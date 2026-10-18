package dev.limitshop.menu;

import dev.limitshop.LimitShopPlugin;
import dev.limitshop.model.Shop;
import dev.limitshop.util.ItemUtil;
import dev.limitshop.util.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class IndexMenu implements InventoryHolder {

    public static final int PREVIOUS_SLOT = 45;
    public static final int PAGE_SLOT = 48;
    public static final int NEXT_SLOT = 49;
    public static final int CLOSE_SLOT = 53;
    public static final int PAGE_SIZE = 45;

    private final LimitShopPlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final Map<Integer, Shop> slotShops = new HashMap<>();
    private int page;

    public IndexMenu(LimitShopPlugin plugin, Player player, int page) {
        this.plugin = plugin;
        this.player = player;
        this.page = Math.max(0, page);
        this.inventory = Bukkit.createInventory(this, 54, Component.text("Shops", NamedTextColor.DARK_GRAY));
        refresh();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void refresh() {
        inventory.clear();
        slotShops.clear();

        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, glass());
        }

        List<Shop> shops = plugin.shops().shops();
        int pages = Math.max(1, (shops.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));

        if (shops.isEmpty()) {
            inventory.setItem(22, button(Material.BARRIER, "No shops yet", NamedTextColor.RED,
                    Component.text("Create one in game:", NamedTextColor.GRAY),
                    Component.text("/limitshop create <id> [title]", NamedTextColor.WHITE),
                    Component.text("Then hold an item and run:", NamedTextColor.GRAY),
                    Component.text("/limitshop add <id> <price> [stock]", NamedTextColor.WHITE)));
        } else {
            int start = page * PAGE_SIZE;
            for (int index = 0; index < PAGE_SIZE; index++) {
                int listIndex = start + index;
                if (listIndex >= shops.size()) {
                    break;
                }
                Shop shop = shops.get(listIndex);
                inventory.setItem(index, shopIcon(shop));
                slotShops.put(index, shop);
            }
        }

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
        if (slot == CLOSE_SLOT) {
            clicker.closeInventory();
            return;
        }
        if (slot == PREVIOUS_SLOT) {
            if (page > 0) {
                page--;
                refresh();
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            }
            return;
        }
        if (slot == NEXT_SLOT) {
            int before = page;
            page++;
            refresh();
            if (page != before) {
                clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            }
            return;
        }
        Shop shop = slotShops.get(slot);
        if (shop != null) {
            plugin.menus().openShop(clicker, shop);
            clicker.playSound(clicker.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
        }
    }

    private ItemStack shopIcon(Shop shop) {
        Material material = ItemUtil.material(shop.icon, Material.BARREL);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(Text.number(shop.items.size()) + " item" + (shop.items.size() == 1 ? "" : "s"), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(Component.text("Click to open", NamedTextColor.GREEN));
        return button(material, shop.title, NamedTextColor.WHITE, lore.toArray(Component[]::new));
    }

    private ItemStack glass() {
        return button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY);
    }

    private ItemStack button(Material material, String name, NamedTextColor color, Component... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.color(name).colorIfAbsent(color).decoration(TextDecoration.ITALIC, false));
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
}
