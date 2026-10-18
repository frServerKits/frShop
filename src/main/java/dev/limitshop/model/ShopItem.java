package dev.limitshop.model;

import dev.limitshop.util.ItemUtil;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class ShopItem {

    public String id = UUID.randomUUID().toString();

    /**
     * ItemStack serialized with Paper's NBT byte format and Base64 encoded.
     */
    public String item;

    public long price;

    /**
     * -1 means unlimited.
     */
    public long stock = -1L;

    /**
     * Maximum stock used by /limitshop restock. -1 means no maximum.
     */
    public long maxStock = -1L;

    public long sold = 0L;

    public ShopItem() {
    }

    public ShopItem(ItemStack stack, long price, long stock, long maxStock) {
        this.item = ItemUtil.encode(stack);
        this.price = price;
        this.stock = stock;
        this.maxStock = maxStock;
    }

    public ItemStack stack() {
        return ItemUtil.decode(item);
    }

    public boolean unlimited() {
        return stock < 0;
    }

    public boolean soldOut() {
        return !unlimited() && stock <= 0;
    }

    public boolean available() {
        return !soldOut();
    }
}
