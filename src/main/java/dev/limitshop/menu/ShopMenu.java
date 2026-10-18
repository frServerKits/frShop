package dev.limitshop.menu;

import dev.limitshop.LimitShopPlugin;
import dev.limitshop.model.Account;
import dev.limitshop.model.Settings;
import dev.limitshop.model.Shop;
import dev.limitshop.model.ShopItem;
import dev.limitshop.service.OfferService;
import dev.limitshop.service.PurchaseService;
import dev.limitshop.util.ItemUtil;
import dev.limitshop.util.Text;
import dev.limitshop.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class ShopMenu implements InventoryHolder {

    public static final int OFFER_SLOT = 4;
    public static final int BALANCE_SLOT = 45;
    public static final int DEBT_SLOT = 46;
    public static final int PREVIOUS_SLOT = 47;
    public static final int PAGE_SLOT = 48;
    public static final int NEXT_SLOT = 49;
    public static final int CLOSE_SLOT = 53;
    public static final int ITEM_START = 9;
    public static final int ITEM_END = 44;
    public static final int PAGE_SIZE = ITEM_END - ITEM_START + 1;

    private final LimitShopPlugin plugin;
    private final Player player;
    private final Shop shop;
    private final Inventory inventory;
    private final Map<Integer, ShopItem> slotItems = new HashMap<>();
    private ShopItem offerItem;
    private int page;

    public ShopMenu(LimitShopPlugin plugin, Player player, Shop shop) {
        this.plugin = plugin;
        this.player = player;
        this.shop = shop;
        this.inventory = Bukkit.createInventory(this, 54, Text.color(shop.title));
        refresh();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Shop shop() {
        return shop;
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void refresh() {
        inventory.clear();
        slotItems.clear();

        for (int slot = 0; slot < 9; slot++) {
            if (slot != OFFER_SLOT) {
                inventory.setItem(slot, border());
            }
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, border());
        }

        offerItem = plugin.offers().ensureOffer(shop);
        inventory.setItem(OFFER_SLOT, offerIcon());

        List<ShopItem> visible = new ArrayList<>();
        for (ShopItem item : shop.items) {
            if (item.stack() == null) {
                continue;
            }
            if (offerItem != null && item.id != null && item.id.equals(offerItem.id)) {
                continue;
            }
            visible.add(item);
        }

        int pages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));

        int start = page * PAGE_SIZE;
        for (int index = 0; index < PAGE_SIZE; index++) {
            int listIndex = start + index;
            if (listIndex >= visible.size()) {
                break;
            }
            ShopItem item = visible.get(listIndex);
            int slot = ITEM_START + index;
            inventory.setItem(slot, display(item, false));
            slotItems.put(slot, item);
        }

        inventory.setItem(BALANCE_SLOT, balanceIcon());
        inventory.setItem(DEBT_SLOT, debtIcon());
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, button(Material.ARROW, "Previous page", NamedTextColor.WHITE));
        }
        inventory.setItem(PAGE_SLOT, button(Material.PAPER,
                "Page " + (page + 1) + "/" + pages, NamedTextColor.GRAY));
        if (page + 1 < pages) {
            inventory.setItem(NEXT_SLOT, button(Material.ARROW, "Next page", NamedTextColor.WHITE));
        }
        inventory.setItem(CLOSE_SLOT, button(Material.BARRIER, "Close", NamedTextColor.RED));
    }

    public void handleClick(Player clicker, int slot, ClickType clickType) {
        if (!clicker.equals(player)) {
            return;
        }
        // The shop may have been deleted or replaced by /limitshop reload.
        if (plugin.shops().get(shop.id) != shop) {
            clicker.closeInventory();
            return;
        }
        if (slot == CLOSE_SLOT) {
            clicker.closeInventory();
            return;
        }
        if (slot == PREVIOUS_SLOT && clickType != ClickType.SHIFT_LEFT && clickType != ClickType.SHIFT_RIGHT) {
            if (page > 0) {
                page--;
                refresh();
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            // Only advance when there is another page; refresh() clamps invalid pages.
            int before = page;
            page++;
            refresh();
            if (page == before) {
                // No next page: stay put.
                return;
            }
            return;
        }

        boolean shift = clickType == ClickType.SHIFT_LEFT || clickType == ClickType.SHIFT_RIGHT;
        ShopItem item = null;
        boolean isOffer = false;
        if (slot == OFFER_SLOT) {
            item = offerItem;
            isOffer = item != null;
        } else {
            item = slotItems.get(slot);
        }
        if (item == null) {
            return;
        }

        PurchaseService.PurchaseResult result = plugin.purchases().buy(clicker, shop, item, shift, isOffer);
        clicker.sendActionBar(result.feedback());
        if (result.success()) {
            clicker.playSound(clicker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.2f);
        } else {
            clicker.playSound(clicker.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        }
        refresh();
    }

    private ItemStack offerIcon() {
        Settings settings = plugin.settings();
        if (offerItem == null) {
            return button(Material.CLOCK, "Offer of the day", NamedTextColor.GRAY,
                    Component.text("No items are available today.", NamedTextColor.DARK_GRAY));
        }
        int discount = plugin.offers().discount(shop);
        long price = plugin.offers().unitPrice(shop, offerItem);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("OFFER OF THE DAY  -" + discount + "%", NamedTextColor.GOLD, TextDecoration.BOLD));
        lore.add(Component.text("Price: ", NamedTextColor.GRAY)
                .append(Text.money(price, settings))
                .append(Component.text("  was ", NamedTextColor.DARK_GRAY))
                .append(Text.money(offerItem.price, settings).decoration(TextDecoration.STRIKETHROUGH, true)));
        lore.add(stockLine(offerItem));
        lore.add(Component.empty());
        if (offerItem.soldOut()) {
            lore.add(Component.text("SOLD OUT", NamedTextColor.RED, TextDecoration.BOLD));
        } else {
            lore.add(clickHint(offerItem));
        }
        return display(offerItem.stack(), lore, !offerItem.soldOut());
    }

    private ItemStack display(ShopItem item, boolean offer) {
        Settings settings = plugin.settings();
        long price = offer ? plugin.offers().unitPrice(shop, item) : item.price;
        List<Component> lore = new ArrayList<>();
        if (offer) {
            lore.add(Component.text("OFFER OF THE DAY", NamedTextColor.GOLD, TextDecoration.BOLD));
        }
        lore.add(Component.text("Price: ", NamedTextColor.GRAY)
                .append(Text.money(price, settings)));
        lore.add(stockLine(item));
        lore.add(Component.empty());
        if (item.soldOut()) {
            lore.add(Component.text("SOLD OUT", NamedTextColor.RED, TextDecoration.BOLD));
        } else {
            lore.add(clickHint(item));
        }
        return display(item.stack(), lore, false);
    }

    private Component stockLine(ShopItem item) {
        if (item.unlimited()) {
            return Component.text("Stock: ", NamedTextColor.GRAY)
                    .append(Component.text("Unlimited", NamedTextColor.GREEN));
        }
        if (item.stock <= 0) {
            return Component.text("Stock: ", NamedTextColor.GRAY)
                    .append(Component.text("Sold out", NamedTextColor.RED));
        }
        return Component.text("Stock: ", NamedTextColor.GRAY)
                .append(Component.text(Text.number(item.stock), NamedTextColor.YELLOW));
    }

    private Component clickHint(ShopItem item) {
        int max = Math.min(64, Math.max(1, item.stack() == null ? 1 : item.stack().getMaxStackSize()));
        if (max <= 1) {
            return Component.text("Click to buy 1", NamedTextColor.DARK_GRAY);
        }
        return Component.text("Click: buy 1  |  Shift+Click: buy " + max, NamedTextColor.DARK_GRAY);
    }

    private ItemStack balanceIcon() {
        Settings settings = plugin.settings();
        long balance = plugin.economy().getBalance(player.getUniqueId());
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Balance: ", NamedTextColor.GRAY)
                .append(Text.moneyWithName(balance, settings)));
        lore.add(Component.text("Currency: " + settings.currencyName, NamedTextColor.DARK_GRAY));
        return button(Material.SUNFLOWER, "Your wallet", NamedTextColor.GOLD, lore.toArray(Component[]::new));
    }

    private ItemStack debtIcon() {
        Account account = plugin.accounts().getOrCreate(player.getUniqueId());
        Settings settings = plugin.settings();
        if (account.debt <= 0) {
            return button(Material.LIME_DYE, "No debt", NamedTextColor.GREEN,
                    Component.text("You are all paid up.", NamedTextColor.DARK_GRAY));
        }
        long now = System.currentTimeMillis();
        long graceEnd = account.debtSince + TimeUnit.SECONDS.toMillis(Math.max(0, settings.debtGraceSeconds));
        String when;
        if (now < graceEnd) {
            when = "Collector starts in " + TimeUtil.duration(graceEnd - now);
        } else if (account.lastCollect > now) {
            when = "Next item in " + TimeUtil.duration(account.lastCollect - now);
        } else {
            when = "Collector is active now";
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Debt: ", NamedTextColor.GRAY).append(Text.money(account.debt, settings)));
        lore.add(Component.text(when, NamedTextColor.RED));
        lore.add(Component.text("One valuable item is taken at a time.", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Pay with /limitshop pay", NamedTextColor.DARK_GRAY));
        return button(Material.REDSTONE, "Debt", NamedTextColor.RED, lore.toArray(Component[]::new));
    }

    private ItemStack border() {
        return button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
    }

    private ItemStack button(Material material, String name, NamedTextColor color, Component... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            if (lore.length > 0) {
                List<Component> lines = new ArrayList<>();
                for (Component line : lore) {
                    lines.add(line.decoration(TextDecoration.ITALIC, false));
                }
                meta.lore(lines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack display(ItemStack base, List<Component> lore, boolean glow) {
        ItemStack stack = base.asOne();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            List<Component> clean = new ArrayList<>();
            for (Component line : lore) {
                clean.add(line.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(clean);
            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
