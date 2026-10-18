package dev.limitshop.service;

import dev.limitshop.economy.EconomyManager;
import dev.limitshop.manager.ShopManager;
import dev.limitshop.model.Settings;
import dev.limitshop.storage.DataStore;
import dev.limitshop.util.ItemUtil;
import dev.limitshop.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class SellService {

    private final DataStore dataStore;
    private final ShopManager shops;
    private final EconomyManager economy;

    public SellService(DataStore dataStore, ShopManager shops, EconomyManager economy) {
        this.dataStore = dataStore;
        this.shops = shops;
        this.economy = economy;
    }

    public SellResult sellHand(Player player) {
        PlayerInventory inventory = player.getInventory();
        int slot = inventory.getHeldItemSlot();
        ItemStack stack = inventory.getItem(slot);
        if (ItemUtil.isAir(stack)) {
            return SellResult.failure("Hold the item you want to sell.");
        }
        long unit = shops.bestSellPrice(stack);
        if (unit <= 0) {
            return SellResult.failure("No shop buys " + Text.itemName(stack) + ".");
        }
        int count = stack.getAmount();
        long gross = unit * count;
        inventory.setItem(slot, null);
        return complete(player, count, gross);
    }

    public SellResult sellAll(Player player) {
        Inventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getStorageContents();
        int count = 0;
        long gross = 0L;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (ItemUtil.isAir(stack)) {
                continue;
            }
            long unit = shops.bestSellPrice(stack);
            if (unit <= 0) {
                continue;
            }
            count += stack.getAmount();
            gross += unit * stack.getAmount();
            inventory.setItem(slot, null);
        }
        if (count <= 0) {
            return SellResult.failure("You have no items that shops buy.");
        }
        return complete(player, count, gross);
    }

    private SellResult complete(Player player, int count, long gross) {
        EconomyManager.IncomeResult income = economy.applyIncome(player.getUniqueId(), gross);
        dataStore.markDirty();
        Settings settings = dataStore.settings();
        Component feedback = Component.text("Sold ", NamedTextColor.GREEN)
                .append(Component.text(count + " item" + (count == 1 ? "" : "s"), NamedTextColor.WHITE))
                .append(Component.text(" for ", NamedTextColor.GREEN))
                .append(Text.money(gross, settings));
        if (income.debtPaid() > 0) {
            feedback = feedback.append(Component.text(". Debt paid: ", NamedTextColor.GRAY))
                    .append(Text.money(income.debtPaid(), settings));
        }
        if (income.deposited() > 0) {
            feedback = feedback.append(Component.text(". Received: ", NamedTextColor.GRAY))
                    .append(Text.money(income.deposited(), settings));
        }
        return new SellResult(true, count, gross, income.debtPaid(), income.deposited(), feedback);
    }

    public record SellResult(boolean success, int itemCount, long gross, long debtPaid,
                             long deposited, Component feedback) {

        public static SellResult failure(String message) {
            return new SellResult(false, 0, 0, 0, 0, Component.text(message, NamedTextColor.RED));
        }
    }
}
