package dev.limitshop.manager;

import dev.limitshop.LimitShopPlugin;
import dev.limitshop.model.Shop;
import dev.limitshop.model.ShopItem;
import dev.limitshop.storage.DataStore;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class ShopManager {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]{1,32}");

    private final LimitShopPlugin plugin;
    private final DataStore dataStore;

    public ShopManager(LimitShopPlugin plugin, DataStore dataStore) {
        this.plugin = plugin;
        this.dataStore = dataStore;
    }

    public List<Shop> shops() {
        List<Shop> result = new ArrayList<>(dataStore.data().shops.values());
        result.sort(Comparator.comparingLong(shop -> shop.createdAt));
        return result;
    }

    public Shop get(String id) {
        return id == null ? null : dataStore.data().shops.get(id.toLowerCase(Locale.ROOT));
    }

    public Shop require(String id) {
        return get(id);
    }

    public boolean exists(String id) {
        return id != null && dataStore.data().shops.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public String validateId(String id) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            return "Shop ids must be 1-32 characters using a-z, 0-9, '_' or '-'.";
        }
        return null;
    }

    public Shop create(String id, String title) {
        String clean = id.toLowerCase(Locale.ROOT);
        Shop shop = new Shop(clean, title);
        dataStore.data().shops.put(clean, shop);
        dataStore.markDirty();
        return shop;
    }

    public void delete(Shop shop) {
        dataStore.data().shops.remove(shop.id);
        dataStore.markDirty();
    }

    public ShopItem addItem(Shop shop, ItemStack stack, long price, long stock, long maxStock) {
        ShopItem item = new ShopItem(stack.asOne(), price, stock, maxStock);
        shop.items.add(item);
        dataStore.markDirty();
        return item;
    }

    public boolean removeItem(Shop shop, int oneBasedIndex) {
        int index = oneBasedIndex - 1;
        if (index < 0 || index >= shop.items.size()) {
            return false;
        }
        ShopItem removed = shop.items.remove(index);
        if (removed.id.equals(shop.offerItemId)) {
            shop.offerItemId = null;
            shop.offerDay = Long.MIN_VALUE;
        }
        dataStore.markDirty();
        return true;
    }

    public void setPrice(ShopItem item, long price) {
        item.price = price;
        dataStore.markDirty();
    }

    public void setStock(ShopItem item, long stock) {
        item.stock = stock;
        if (stock >= 0 && item.maxStock >= 0 && item.maxStock < stock) {
            item.maxStock = stock;
        }
        dataStore.markDirty();
    }

    public void setMaxStock(ShopItem item, long maxStock) {
        item.maxStock = maxStock;
        if (maxStock >= 0 && !item.unlimited() && item.stock > maxStock) {
            item.stock = maxStock;
        }
        dataStore.markDirty();
    }

    public void setTitle(Shop shop, String title) {
        shop.title = title;
        dataStore.markDirty();
    }

    public void setIcon(Shop shop, Material material) {
        shop.icon = material.name();
        dataStore.markDirty();
    }

    /**
     * Refills finite items. A negative amount means "fill to maxStock".
     *
     * @return number of item entries that changed
     */
    public int restock(Shop shop, long amount) {
        int changed = 0;
        for (ShopItem item : shop.items) {
            if (item.unlimited()) {
                continue;
            }
            long old = item.stock;
            if (amount < 0) {
                if (item.maxStock >= 0) {
                    item.stock = item.maxStock;
                }
            } else if (item.maxStock >= 0) {
                item.stock = Math.min(item.maxStock, item.stock + amount);
            } else {
                item.stock = item.stock + amount;
            }
            if (item.stock != old) {
                changed++;
            }
        }
        if (changed > 0) {
            dataStore.markDirty();
        }
        return changed;
    }

    public ShopItem itemAt(Shop shop, int oneBasedIndex) {
        int index = oneBasedIndex - 1;
        if (index < 0 || index >= shop.items.size()) {
            return null;
        }
        return shop.items.get(index);
    }

    /**
     * Best price another plugin's economy would pay for this stack. Used by
     * /limitshop sell and by the debt collector.
     */
    public long bestSellPrice(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return 0L;
        }
        long best = 0L;
        for (Shop shop : shops()) {
            for (ShopItem entry : shop.items) {
                if (!matches(entry, stack)) {
                    continue;
                }
                long price = sellPrice(entry);
                if (price > best) {
                    best = price;
                }
            }
        }
        return best;
    }

    public long sellPrice(ShopItem item) {
        int percent = Math.max(0, Math.min(100, dataStore.settings().sellPercent));
        if (item.price <= 0 || percent <= 0) {
            return 0L;
        }
        return Math.max(1L, item.price * percent / 100L);
    }

    public boolean matches(ShopItem entry, ItemStack actual) {
        if (entry == null || actual == null || actual.getType().isAir()) {
            return false;
        }
        ItemStack template = entry.stack();
        if (template == null) {
            return false;
        }
        if (template.isSimilar(actual)) {
            return true;
        }
        // Plain material entries should still match damaged/tweaked variants.
        return template.getType() == actual.getType() && !template.hasItemMeta();
    }

    public boolean hasAnyItems(Shop shop) {
        return shop != null && !shop.items.isEmpty();
    }

    public LimitShopPlugin plugin() {
        return plugin;
    }
}
