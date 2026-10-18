package dev.limitshop.economy;

import dev.limitshop.LimitShopPlugin;
import dev.limitshop.model.Settings;
import dev.limitshop.util.Text;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.Collections;
import java.util.List;

/**
 * Exposes LimitShop's custom currency to every Vault-aware plugin.
 */
public final class VaultEconomyProvider implements Economy {

    private final LimitShopPlugin plugin;
    private final InternalEconomy internal;

    public VaultEconomyProvider(LimitShopPlugin plugin, InternalEconomy internal) {
        this.plugin = plugin;
        this.internal = internal;
    }

    private Settings settings() {
        return plugin.settings();
    }

    private OfflinePlayer player(String name) {
        return Bukkit.getOfflinePlayer(name);
    }

    private EconomyResponse failure(String message, OfflinePlayer player) {
        return new EconomyResponse(0, internal.getBalance(player.getUniqueId()),
                EconomyResponse.ResponseType.FAILURE, message);
    }

    private EconomyResponse notImplemented() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED,
                "LimitShop does not support bank accounts.");
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "LimitShop";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 0;
    }

    @Override
    public String format(double amount) {
        return settings().currencySymbol + Text.number(Math.round(amount));
    }

    @Override
    public String currencyNamePlural() {
        return settings().currencyName;
    }

    @Override
    public String currencyNameSingular() {
        return settings().currencySingular;
    }

    @Override
    public boolean hasAccount(String playerName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean hasAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return true;
    }

    @Override
    public double getBalance(String playerName) {
        return internal.getBalance(player(playerName).getUniqueId());
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return internal.getBalance(player.getUniqueId());
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double amount) {
        return internal.getBalance(player(playerName).getUniqueId()) >= Math.round(amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return internal.getBalance(player.getUniqueId()) >= Math.round(amount);
    }

    @Override
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdrawPlayer(player(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        long units = Math.round(amount);
        if (units <= 0) {
            return failure("Amount must be positive.", player);
        }
        if (!internal.withdraw(player.getUniqueId(), units)) {
            return failure("Insufficient funds.", player);
        }
        return new EconomyResponse(units, internal.getBalance(player.getUniqueId()),
                EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return depositPlayer(player(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        long units = Math.round(amount);
        if (units <= 0) {
            return failure("Amount must be positive.", player);
        }
        internal.deposit(player.getUniqueId(), units);
        return new EconomyResponse(units, internal.getBalance(player.getUniqueId()),
                EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return notImplemented();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return notImplemented();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return true;
    }
}
