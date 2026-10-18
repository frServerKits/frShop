package dev.limitshop.service;

import dev.limitshop.LimitShopPlugin;
import dev.limitshop.economy.EconomyManager;
import dev.limitshop.manager.AccountStore;
import dev.limitshop.model.Settings;
import dev.limitshop.model.Shop;
import dev.limitshop.model.ShopItem;
import dev.limitshop.storage.DataStore;
import dev.limitshop.util.ItemUtil;
import dev.limitshop.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class PurchaseService {

    private final LimitShopPlugin plugin;
    private final DataStore dataStore;
    private final AccountStore accounts;
    private final EconomyManager economy;
    private final OfferService offers;

    public PurchaseService(LimitShopPlugin plugin, DataStore dataStore,
                           AccountStore accounts, EconomyManager economy, OfferService offers) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.accounts = accounts;
        this.economy = economy;
        this.offers = offers;
    }

    public PurchaseResult buy(Player player, Shop shop, ShopItem item, boolean shiftClick, boolean offer) {
        if (item == null || shop == null) {
            return PurchaseResult.failure("That item is no longer available.", NamedTextColor.RED);
        }
        ItemStack template = item.stack();
        if (template == null) {
            return PurchaseResult.failure("That item is broken and cannot be bought.", NamedTextColor.RED);
        }

        int desired = shiftClick ? Math.max(1, Math.min(64, template.getMaxStackSize())) : 1;
        long stockAvailable = item.unlimited() ? desired : Math.max(0L, Math.min(desired, item.stock));
        int capacity = ItemUtil.freeCapacity(player, template);
        int amount = (int) Math.min(stockAvailable, capacity);

        if (amount <= 0) {
            if (stockAvailable <= 0) {
                return PurchaseResult.failure("Sold out.", NamedTextColor.RED);
            }
            return PurchaseResult.failure("Your inventory is full.", NamedTextColor.RED);
        }

        long unitPrice = offer ? offers.unitPrice(shop, item) : item.price;
        long total;
        try {
            total = Math.multiplyExact(unitPrice, amount);
        } catch (ArithmeticException ex) {
            return PurchaseResult.failure("That price is too large.", NamedTextColor.RED);
        }

        Settings settings = dataStore.settings();
        long balance = economy.getBalance(player.getUniqueId());
        long creditRoom = economy.availableCredit(player.getUniqueId());
        if (total > safeAdd(balance, creditRoom)) {
            if (creditRoom <= 0) {
                return PurchaseResult.failure("Not enough money, and your credit limit is reached.", NamedTextColor.RED);
            }
            Component message = Component.text("Not enough money. ", NamedTextColor.RED)
                    .append(Component.text("You can still borrow ", NamedTextColor.GRAY))
                    .append(Text.money(creditRoom, settings))
                    .append(Component.text(".", NamedTextColor.GRAY));
            return PurchaseResult.failure(message);
        }

        long fromBalance = Math.min(balance, total);
        long onCredit = total - fromBalance;
        if (onCredit > 0 && !settings.debtEnabled) {
            return PurchaseResult.failure("You do not have enough money.", NamedTextColor.RED);
        }

        if (!economy.withdraw(player.getUniqueId(), fromBalance)) {
            return PurchaseResult.failure("The payment could not be completed.", NamedTextColor.RED);
        }

        boolean stockChanged = !item.unlimited();
        try {
            if (onCredit > 0) {
                accounts.addDebt(player.getUniqueId(), onCredit);
            }
            if (stockChanged) {
                item.stock -= amount;
            }
            item.sold += amount;
            ItemUtil.give(player, template, amount);
            dataStore.markDirty();
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Purchase rollback for " + player.getName() + ": " + ex.getMessage());
            if (onCredit > 0) {
                accounts.reduceDebt(player.getUniqueId(), onCredit);
            }
            if (stockChanged) {
                item.stock += amount;
            }
            item.sold = Math.max(0L, item.sold - amount);
            if (fromBalance > 0) {
                economy.deposit(player.getUniqueId(), fromBalance);
            }
            return PurchaseResult.failure("The purchase failed and your payment was returned.", NamedTextColor.RED);
        }

        Component feedback = Component.text("Bought ", NamedTextColor.GREEN)
                .append(Component.text(amount + "x " + Text.itemName(template), NamedTextColor.WHITE))
                .append(Component.text(" for ", NamedTextColor.GREEN))
                .append(Text.money(total, settings));
        if (onCredit > 0) {
            feedback = feedback.append(Component.text(" (", NamedTextColor.GRAY))
                    .append(Text.money(onCredit, settings))
                    .append(Component.text(" on credit)", NamedTextColor.GRAY));
        }
        if (amount < desired) {
            feedback = feedback.append(Component.text(" [limited]", NamedTextColor.DARK_GRAY));
        }
        if (offer) {
            feedback = feedback.append(Component.text(" OFFER", NamedTextColor.GOLD));
        }
        return new PurchaseResult(true, amount, total, onCredit, feedback);
    }

    private static long safeAdd(long left, long right) {
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    public record PurchaseResult(boolean success, int amount, long total, long credit, Component feedback) {

        public static PurchaseResult failure(String message, NamedTextColor color) {
            return new PurchaseResult(false, 0, 0, 0, Component.text(message, color));
        }

        public static PurchaseResult failure(Component message) {
            return new PurchaseResult(false, 0, 0, 0, message);
        }
    }
}
