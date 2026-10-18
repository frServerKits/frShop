package dev.limitshop.util;

import org.bukkit.block.ShulkerBox;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;
import java.util.Map;

/**
 * Small deterministic value table used only for debt repossession. Items that a
 * shop buys use the shop's real sell price instead.
 */
public final class ItemValue {

    private ItemValue() {
    }

    public static long estimate(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0L;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof BlockStateMeta blockMeta && blockMeta.getBlockState() instanceof ShulkerBox box) {
            long total = 80L;
            for (ItemStack content : box.getInventory().getContents()) {
                if (content != null && !content.getType().isAir()) {
                    total += estimate(content) * content.getAmount();
                }
            }
            return total;
        }

        String name = stack.getType().name();
        long base = baseValue(name);
        if (base <= 0) {
            return 0L;
        }

        long enchantBonus = 0L;
        for (Map.Entry<Enchantment, Integer> entry : stack.getEnchantments().entrySet()) {
            enchantBonus += (long) entry.getValue() * (base >= 50 ? 15L : 5L);
        }
        if (meta instanceof EnchantmentStorageMeta storage) {
            for (Map.Entry<Enchantment, Integer> entry : storage.getStoredEnchants().entrySet()) {
                enchantBonus += (long) entry.getValue() * 10L;
            }
        }
        return base + enchantBonus;
    }

    private static long baseValue(String name) {
        return switch (name) {
            case "NETHERITE_INGOT" -> 250;
            case "NETHERITE_SCRAP" -> 100;
            case "NETHERITE_BLOCK" -> 2250;
            case "ANCIENT_DEBRIS" -> 100;
            case "DIAMOND" -> 60;
            case "DIAMOND_BLOCK" -> 540;
            case "EMERALD" -> 40;
            case "EMERALD_BLOCK" -> 360;
            case "GOLD_INGOT" -> 20;
            case "GOLD_BLOCK" -> 180;
            case "IRON_INGOT" -> 12;
            case "IRON_BLOCK" -> 108;
            case "COPPER_INGOT" -> 2;
            case "COPPER_BLOCK" -> 18;
            case "AMETHYST_SHARD" -> 4;
            case "LAPIS_LAZULI" -> 2;
            case "REDSTONE" -> 1;
            case "QUARTZ" -> 2;
            case "COAL" -> 1;
            case "CHARCOAL" -> 1;
            case "RAW_IRON" -> 4;
            case "RAW_GOLD" -> 8;
            case "RAW_COPPER" -> 1;
            case "ENCHANTED_GOLDEN_APPLE" -> 300;
            case "GOLDEN_APPLE" -> 30;
            case "GOLDEN_CARROT" -> 5;
            case "ELYTRA" -> 300;
            case "TOTEM_OF_UNDYING" -> 200;
            case "NETHER_STAR" -> 500;
            case "DRAGON_EGG" -> 1000;
            case "TRIDENT" -> 200;
            case "ENCHANTED_BOOK" -> 20;
            case "EXPERIENCE_BOTTLE" -> 3;
            case "ENDER_PEARL" -> 8;
            case "BLAZE_ROD" -> 6;
            case "GHAST_TEAR" -> 10;
            case "NAME_TAG" -> 10;
            case "SADDLE" -> 10;
            case "POTION" -> 5;
            case "SPLASH_POTION" -> 8;
            case "LINGERING_POTION" -> 10;
            default -> suffixValue(name);
        };
    }

    private static long suffixValue(String name) {
        if (name.endsWith("SHULKER_BOX")) {
            return 80;
        }
        if (name.endsWith("_HORSE_ARMOR")) {
            if (name.startsWith("NETHERITE")) {
                return 100;
            }
            if (name.startsWith("DIAMOND")) {
                return 60;
            }
            if (name.startsWith("GOLDEN")) {
                return 30;
            }
            if (name.startsWith("IRON")) {
                return 20;
            }
            return 8;
        }

        String upper = name.toUpperCase(Locale.ROOT);
        if (upper.startsWith("NETHERITE_")) {
            return 200;
        }
        if (upper.startsWith("DIAMOND_")) {
            return 60;
        }
        if (upper.startsWith("IRON_")) {
            return 20;
        }
        if (upper.startsWith("GOLDEN_")) {
            return 25;
        }
        if (upper.startsWith("CHAINMAIL_")) {
            return 15;
        }
        if (upper.startsWith("LEATHER_")) {
            return 8;
        }
        if (upper.startsWith("STONE_")) {
            return 3;
        }
        if (upper.startsWith("WOODEN_")) {
            return 1;
        }

        // Common but still useful goods.
        return switch (upper) {
            case "ENDER_CHEST", "ANVIL", "ENCHANTING_TABLE", "BREWING_STAND" -> 20;
            case "HOPPER", "OBSERVER", "PISTON", "STICKY_PISTON" -> 5;
            case "IRON_DOOR", "IRON_TRAPDOOR" -> 4;
            case "HONEY_BLOCK", "SLIME_BLOCK" -> 6;
            case "SPONGE", "WET_SPONGE" -> 30;
            default -> 0;
        };
    }
}
