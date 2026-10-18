package dev.limitshop.economy;

import dev.limitshop.LimitShopPlugin;
import dev.limitshop.manager.AccountStore;
import dev.limitshop.model.Account;
import dev.limitshop.model.Settings;
import dev.limitshop.storage.DataStore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

public final class EconomyManager {

    private final LimitShopPlugin plugin;
    private final DataStore dataStore;
    private final AccountStore accounts;
    private final InternalEconomy internal;
    private VaultBridge vault;
    private EconomyService service;

    public EconomyManager(LimitShopPlugin plugin, DataStore dataStore, AccountStore accounts) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.accounts = accounts;
        this.internal = new InternalEconomy(accounts);
        this.service = internal;
    }

    public void setup() {
        ensureVault();
        configure(true);
    }

    /**
     * Vault normally loads first because of softdepend, but this also lets an
     * admin hook Vault up later with /limitshop economy if load order changed.
     */
    private void ensureVault() {
        if (vault != null || Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }
        try {
            // Only load the Vault-referencing classes when Vault is actually installed.
            Class.forName("net.milkbowl.vault.economy.Economy");
            vault = new VaultIntegration(plugin);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Vault was found, but its API is unavailable: " + throwable.getMessage());
            vault = null;
        }
    }

    public void configure(boolean log) {
        ensureVault();
        String requested = normalizeMode(dataStore.settings().vaultMode);
        if (vault == null || "internal".equals(requested)) {
            useInternal(log);
            return;
        }

        // Make sure Vault does not return our own provider while we look for another one.
        vault.unregisterInternal();

        if ("external".equals(requested)) {
            EconomyService external = findExternal();
            if (external != null) {
                useExternal(external, log);
            } else {
                if (log) {
                    plugin.getLogger().warning("Vault mode is 'external', but no Vault economy was found. Falling back to the internal currency.");
                }
                useInternal(false);
            }
            return;
        }

        // auto
        EconomyService external = findExternal();
        if (external != null) {
            useExternal(external, log);
        } else {
            useInternal(log);
        }
    }

    private EconomyService findExternal() {
        if (vault == null) {
            return null;
        }
        try {
            return vault.findExternal();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("Could not read the Vault economy provider: " + throwable.getMessage());
            return null;
        }
    }

    private void useInternal(boolean log) {
        service = internal;
        if (vault != null) {
            vault.registerInternal(internal);
        }
        if (log) {
            if (vault != null) {
                plugin.getLogger().info("Economy: internal '" + dataStore.settings().currencyName + "' currency, exposed to Vault.");
            } else {
                plugin.getLogger().info("Economy: internal '" + dataStore.settings().currencyName + "' currency (Vault not installed).");
            }
        }
    }

    private void useExternal(EconomyService external, boolean log) {
        if (vault != null) {
            vault.unregisterInternal();
        }
        service = external;
        if (log) {
            plugin.getLogger().info("Economy: using Vault provider '" + external.name() + "'.");
        }
    }

    public static String normalizeMode(String mode) {
        if (mode == null) {
            return "internal";
        }
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "auto", "external", "internal" -> mode.toLowerCase(Locale.ROOT);
            default -> "internal";
        };
    }

    public void handleJoin(Player player) {
        Account account = accounts.getOrCreate(player.getUniqueId());
        if (account.initialized) {
            return;
        }
        account.initialized = true;
        if (service.internal()) {
            account.balance = Math.max(0L, dataStore.settings().startingBalance);
        }
        accounts.markDirty();
    }

    public long getBalance(UUID uuid) {
        return service.getBalance(uuid);
    }

    public boolean withdraw(UUID uuid, long amount) {
        return service.withdraw(uuid, amount);
    }

    public void deposit(UUID uuid, long amount) {
        service.deposit(uuid, amount);
    }

    public void setBalance(UUID uuid, long amount) {
        service.setBalance(uuid, amount);
    }

    /**
     * All income first repairs debt, then the remainder is deposited into the
     * wallet. This makes /limitshop sell a natural way out of debt.
     */
    public IncomeResult applyIncome(UUID uuid, long amount) {
        if (amount <= 0) {
            return new IncomeResult(0, 0);
        }
        Account account = accounts.getOrCreate(uuid);
        long toDebt = Math.min(account.debt, amount);
        if (toDebt > 0) {
            accounts.reduceDebt(uuid, toDebt);
        }
        long remaining = amount - toDebt;
        if (remaining > 0) {
            service.deposit(uuid, remaining);
        }
        return new IncomeResult(toDebt, remaining);
    }

    public long availableCredit(UUID uuid) {
        Settings settings = dataStore.settings();
        if (!settings.debtEnabled) {
            return 0L;
        }
        Account account = accounts.getOrCreate(uuid);
        return Math.max(0L, settings.debtLimit - account.debt);
    }

    public boolean isInternal() {
        return service.internal();
    }

    public String serviceName() {
        return service.name();
    }

    public String effectiveMode() {
        return service.internal() ? "internal" : "external";
    }

    public boolean vaultAvailable() {
        return vault != null;
    }

    public String vaultProviderName() {
        return vault == null ? null : vault.providerName();
    }

    public void shutdown() {
        if (vault != null) {
            vault.unregisterInternal();
        }
    }

    public record IncomeResult(long debtPaid, long deposited) {
    }
}
