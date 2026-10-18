package dev.limitshop.menu;

import dev.limitshop.LimitShopPlugin;
import dev.limitshop.model.Shop;
import org.bukkit.entity.Player;

public final class MenuManager {

    private final LimitShopPlugin plugin;

    public MenuManager(LimitShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void openIndex(Player player) {
        new IndexMenu(plugin, player, 0).open();
    }

    public void openShop(Player player, Shop shop) {
        // Use the live shop object, not an object held by an old menu after a
        // reload or deletion.
        Shop current = plugin.shops().get(shop.id);
        if (current == null) {
            return;
        }
        new ShopMenu(plugin, player, current).open();
    }
}
