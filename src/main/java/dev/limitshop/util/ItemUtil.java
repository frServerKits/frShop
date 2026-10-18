package dev.limitshop.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public final class ItemUtil {

    private static final Base64.Encoder ENCODER = Base64.getEncoder();
    private static final Base64.Decoder DECODER = Base64.getDecoder();

    private ItemUtil() {
    }

    public static String encode(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        return ENCODER.encodeToString(stack.serializeAsBytes());
    }

    public static ItemStack decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            return ItemStack.deserializeBytes(DECODER.decode(encoded));
        } catch (RuntimeException ex) {
            Bukkit.getLogger().warning("[LimitShop] Could not deserialize a shop item: " + ex.getMessage());
            return null;
        }
    }

    /**
     * How many of this exact item can still fit into the player's storage slots.
     */
    public static int freeCapacity(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0;
        }
        int maxStack = Math.min(item.getMaxStackSize(), player.getInventory().getMaxStackSize());
        int free = 0;
        for (ItemStack slot : player.getInventory().getStorageContents()) {
            if (slot == null || slot.getType().isAir()) {
                free += maxStack;
            } else if (slot.isSimilar(item)) {
                int slotMax = Math.min(maxStack, slot.getMaxStackSize());
                free += Math.max(0, slotMax - slot.getAmount());
            }
        }
        return free;
    }

    public static void give(Player player, ItemStack item, int amount) {
        if (amount <= 0) {
            return;
        }
        ItemStack give = item.asQuantity(amount);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(give);
        // Capacity is checked before a purchase, so this should never happen.
        // Dropping at the player's feet is better than silently deleting paid items.
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
        }
    }

    public static int removeFromSlot(Player player, int slot, int amount) {
        Inventory inventory = player.getInventory();
        ItemStack current = inventory.getItem(slot);
        if (current == null || current.getType().isAir() || amount <= 0) {
            return 0;
        }
        int removed = Math.min(amount, current.getAmount());
        int left = current.getAmount() - removed;
        if (left <= 0) {
            inventory.setItem(slot, null);
        } else {
            current.setAmount(left);
            inventory.setItem(slot, current);
        }
        return removed;
    }

    public static boolean isAir(ItemStack stack) {
        return stack == null || stack.getType().isAir() || stack.getAmount() <= 0;
    }

    public static Map<Integer, ItemStack> copyStorage(Player player) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        Map<Integer, ItemStack> result = new HashMap<>();
        for (int i = 0; i < contents.length; i++) {
            if (!isAir(contents[i])) {
                result.put(i, contents[i].clone());
            }
        }
        return result;
    }

    public static Material material(String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem()) {
            return fallback;
        }
        return material;
    }
}
