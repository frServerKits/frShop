package dev.limitshop.listener;

import dev.limitshop.menu.IndexMenu;
import dev.limitshop.menu.ShopMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof ShopMenu) && !(holder instanceof IndexMenu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !clicked.equals(event.getInventory())) {
            return;
        }
        if (holder instanceof ShopMenu menu) {
            menu.handleClick(player, event.getSlot(), event.getClick());
        } else if (holder instanceof IndexMenu menu) {
            menu.handleClick(player, event.getSlot(), event.getClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof ShopMenu || holder instanceof IndexMenu) {
            event.setCancelled(true);
        }
    }
}
