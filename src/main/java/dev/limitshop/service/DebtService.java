package dev.limitshop.service;

import dev.limitshop.LimitShopPlugin;
import dev.limitshop.economy.EconomyManager;
import dev.limitshop.manager.AccountStore;
import dev.limitshop.manager.ShopManager;
import dev.limitshop.model.Account;
import dev.limitshop.model.Settings;
import dev.limitshop.storage.DataStore;
import dev.limitshop.util.ItemUtil;
import dev.limitshop.util.ItemValue;
import dev.limitshop.util.Text;
import dev.limitshop.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

/**
 * If debt is not paid, valuable inventory items are repossessed one at a time.
 * The player always gets a warning and a grace period first.
 */
public final class DebtService {

    private final LimitShopPlugin plugin;
    private final DataStore dataStore;
    private final AccountStore accounts;
    private final ShopManager shops;
    private final EconomyManager economy;
    private BukkitTask task;

    public DebtService(LimitShopPlugin plugin, DataStore dataStore, AccountStore accounts,
                       ShopManager shops, EconomyManager economy) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.accounts = accounts;
        this.shops = shops;
        this.economy = economy;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void handleJoin(Player player) {
        Account account = accounts.getOrCreate(player.getUniqueId());
        if (account.debt <= 0) {
            return;
        }
        Settings settings = dataStore.settings();
        long now = System.currentTimeMillis();
        long interval = intervalMillis(settings);
        long grace = TimeUnit.SECONDS.toMillis(Math.max(0, settings.debtGraceSeconds));

        if (account.debtSince <= 0) {
            account.debtSince = now;
            account.lastCollect = now + grace;
            accounts.markDirty();
        }

        // Debt that came due while the player was offline is collected once on
        // their return; logging out is not a way to escape the collector.
        if (now >= account.debtSince + grace && account.lastCollect > 0 && now >= account.lastCollect) {
            collectOne(player, account, settings);
            if (account.debt <= 0) {
                accounts.markDirty();
                return;
            }
            account.lastCollect = now + interval;
            accounts.markDirty();
        } else if (account.lastCollect < now + interval) {
            account.lastCollect = now + interval;
            accounts.markDirty();
        }

        Text.send(player, Component.text("You are in debt: ", NamedTextColor.RED)
                .append(Text.money(account.debt, settings))
                .append(Component.text(". The collector will take one valuable item in ", NamedTextColor.GRAY))
                .append(Component.text(TimeUtil.duration(account.lastCollect - now), NamedTextColor.YELLOW))
                .append(Component.text(". Pay with /limitshop pay or sell items with /limitshop sell.", NamedTextColor.GRAY)));
    }

    private void tick() {
        Settings settings = dataStore.settings();
        if (!settings.debtEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        long interval = intervalMillis(settings);
        long grace = TimeUnit.SECONDS.toMillis(Math.max(0, settings.debtGraceSeconds));

        for (Player player : Bukkit.getOnlinePlayers()) {
            Account account = accounts.peek(player.getUniqueId());
            if (account == null || account.debt <= 0) {
                continue;
            }
            if (account.debtSince <= 0) {
                account.debtSince = now;
                account.lastCollect = now + grace;
                accounts.markDirty();
            }
            if (now < account.debtSince + grace) {
                continue;
            }
            if (account.lastCollect <= 0) {
                account.lastCollect = now + interval;
                accounts.markDirty();
                continue;
            }
            if (now < account.lastCollect) {
                continue;
            }

            try {
                collectOne(player, account, settings);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Debt collection failed for " + player.getName() + ": " + ex.getMessage());
            } finally {
                if (account.debt > 0) {
                    account.lastCollect = now + interval;
                }
                accounts.markDirty();
            }
        }
    }

    public boolean forceCollect(Player player) {
        Account account = accounts.getOrCreate(player.getUniqueId());
        if (account.debt <= 0) {
            return false;
        }
        Settings settings = dataStore.settings();
        collectOne(player, account, settings);
        if (account.debt > 0) {
            account.lastCollect = System.currentTimeMillis() + intervalMillis(settings);
        }
        accounts.markDirty();
        return true;
    }

    private void collectOne(Player player, Account account, Settings settings) {
        // Solvent players pay from their wallet before losing items.
        long balance = economy.getBalance(player.getUniqueId());
        if (balance > 0) {
            long payment = Math.min(balance, account.debt);
            if (payment > 0 && economy.withdraw(player.getUniqueId(), payment)) {
                accounts.reduceDebt(player.getUniqueId(), payment);
                Text.send(player, Component.text("The debt collector took ", NamedTextColor.RED)
                        .append(Text.money(payment, settings))
                        .append(Component.text(" from your wallet. ", NamedTextColor.GRAY))
                        .append(account.debt <= 0
                                ? Component.text("Debt cleared.", NamedTextColor.GREEN)
                                : Component.text("Debt left: ", NamedTextColor.RED).append(Text.money(account.debt, settings))));
                if (account.debt <= 0) {
                    return;
                }
            }
        }

        Candidate best = findBest(player, 0, 35, account, settings);
        if (best == null) {
            // Equipped armour and the offhand are the last resort.
            best = findBest(player, 36, 40, account, settings);
        }

        if (best == null) {
            Text.send(player, Component.text("The debt collector found no valuable items. ", NamedTextColor.RED)
                    .append(Component.text("Debt: ", NamedTextColor.GRAY))
                    .append(Text.money(account.debt, settings)));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            return;
        }

        ItemStack taken = best.stack().asQuantity(best.take());
        String name = Text.itemName(taken);
        ItemUtil.removeFromSlot(player, best.slot(), best.take());
        long worth = best.unit() * best.take();
        long paid = Math.min(account.debt, worth);
        accounts.reduceDebt(player.getUniqueId(), paid);

        long remaining = account.debt;
        Component message = Component.text("Debt collector took ", NamedTextColor.RED)
                .append(Component.text(best.take() + "x " + name, NamedTextColor.WHITE))
                .append(Component.text(" (worth ", NamedTextColor.GRAY))
                .append(Text.money(worth, settings))
                .append(Component.text("). ", NamedTextColor.GRAY))
                .append(Component.text(remaining <= 0 ? "Debt cleared." : "Debt left: ", remaining <= 0 ? NamedTextColor.GREEN : NamedTextColor.RED))
                .append(remaining <= 0 ? Component.empty() : Text.money(remaining, settings));
        Text.send(player, message);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.7f);
    }

    private Candidate findBest(Player player, int startSlot, int endSlot, Account account, Settings settings) {
        Candidate best = null;
        for (int slot = startSlot; slot <= endSlot; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (ItemUtil.isAir(stack)) {
                continue;
            }
            long unit = shops.bestSellPrice(stack);
            if (unit <= 0) {
                unit = ItemValue.estimate(stack);
            }
            if (unit < Math.max(0L, settings.debtMinItemValue)) {
                continue;
            }
            long stackValue = unit * stack.getAmount();
            if (best == null || stackValue > best.stackValue()) {
                long needed = Math.max(1L, (account.debt + unit - 1L) / unit);
                int take = (int) Math.min(stack.getAmount(), needed);
                best = new Candidate(slot, unit, stackValue, take, stack.clone());
            }
        }
        return best;
    }

    private record Candidate(int slot, long unit, long stackValue, int take, ItemStack stack) {
    }

    private long intervalMillis(Settings settings) {
        return TimeUnit.SECONDS.toMillis(Math.max(1, settings.debtCollectSeconds));
    }
}
