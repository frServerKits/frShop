package dev.limitshop.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.UUID;

/**
 * Used when the server already has another Vault economy and the admin chooses
 * external/auto mode.
 */
public final class VaultEconomyService implements EconomyService {

    private final Economy economy;

    public VaultEconomyService(Economy economy) {
        this.economy = economy;
    }

    @Override
    public long getBalance(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return Math.max(0L, (long) Math.floor(economy.getBalance(player)));
    }

    @Override
    public boolean withdraw(UUID uuid, long amount) {
        if (amount <= 0) {
            return true;
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (economy.getBalance(player) < amount) {
            return false;
        }
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Override
    public void deposit(UUID uuid, long amount) {
        if (amount <= 0) {
            return;
        }
        economy.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
    }

    @Override
    public void setBalance(UUID uuid, long amount) {
        long target = Math.max(0L, amount);
        long current = getBalance(uuid);
        if (current > target) {
            economy.withdrawPlayer(Bukkit.getOfflinePlayer(uuid), current - target);
        } else if (current < target) {
            economy.depositPlayer(Bukkit.getOfflinePlayer(uuid), target - current);
        }
    }

    @Override
    public boolean internal() {
        return false;
    }

    @Override
    public String name() {
        return economy.getName();
    }
}
