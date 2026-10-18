package dev.limitshop.economy;

import dev.limitshop.LimitShopPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;

public final class VaultIntegration implements VaultBridge {

    private final LimitShopPlugin plugin;
    private VaultEconomyProvider provider;

    public VaultIntegration(LimitShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public EconomyService findExternal() {
        ServicesManager services = Bukkit.getServicesManager();
        // Check every registration so our own provider can never hide another
        // economy plugin that happens to be registered with a lower priority.
        for (RegisteredServiceProvider<Economy> registration : services.getRegistrations(Economy.class)) {
            Economy economy = registration.getProvider();
            if (economy == null || economy instanceof VaultEconomyProvider) {
                continue;
            }
            if (!economy.isEnabled()) {
                plugin.getLogger().warning("Vault returned a disabled economy provider: " + economy.getName());
                continue;
            }
            return new VaultEconomyService(economy);
        }
        return null;
    }

    @Override
    public boolean registerInternal(InternalEconomy internal) {
        if (provider != null) {
            return true;
        }
        try {
            provider = new VaultEconomyProvider(plugin, internal);
            Bukkit.getServicesManager().register(Economy.class, provider, plugin, ServicePriority.High);
            plugin.getLogger().info("Registered LimitShop's internal currency with Vault.");
            return true;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Could not register the internal currency with Vault: " + ex.getMessage());
            provider = null;
            return false;
        }
    }

    @Override
    public void unregisterInternal() {
        if (provider == null) {
            return;
        }
        try {
            Bukkit.getServicesManager().unregister(Economy.class, provider);
        } catch (RuntimeException ignored) {
            // The service manager can already be closed during shutdown.
        }
        provider = null;
    }

    @Override
    public boolean internalRegistered() {
        return provider != null;
    }

    @Override
    public String providerName() {
        ServicesManager services = Bukkit.getServicesManager();
        RegisteredServiceProvider<Economy> registration = services.getRegistration(Economy.class);
        if (registration == null || registration.getProvider() == null) {
            return null;
        }
        return registration.getProvider().getName();
    }
}
