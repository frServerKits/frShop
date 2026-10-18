package dev.limitshop.service;

import dev.limitshop.model.Shop;
import dev.limitshop.model.ShopItem;
import dev.limitshop.storage.DataStore;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class OfferService {

    private final DataStore dataStore;

    public OfferService(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    /**
     * Rotates the offer when the calendar day changes and repairs a deleted
     * offer item. The chosen item is persisted, so everyone sees the same deal.
     */
    public ShopItem ensureOffer(Shop shop) {
        long today = LocalDate.now().toEpochDay();
        boolean changed = false;
        ShopItem current = shop.offerItem();

        if (shop.offerDay != today) {
            shop.offerDay = today;
            shop.offerItemId = choose(shop, today, null);
            changed = true;
            current = shop.offerItem();
        }

        if (current == null || current.stack() == null) {
            String replacement = choose(shop, today, shop.offerItemId);
            if (!java.util.Objects.equals(replacement, shop.offerItemId)) {
                shop.offerItemId = replacement;
                changed = true;
            }
            current = shop.offerItem();
        }

        if (changed) {
            dataStore.markDirty();
        }
        return current;
    }

    public ShopItem reroll(Shop shop) {
        long today = LocalDate.now().toEpochDay();
        shop.offerDay = today;
        shop.offerItemId = choose(shop, today + 1, shop.offerItemId);
        dataStore.markDirty();
        return shop.offerItem();
    }

    public int discount(Shop shop) {
        return shop.effectiveOfferDiscount(dataStore.settings());
    }

    public long unitPrice(Shop shop, ShopItem item) {
        return Shop.discountedPrice(item.price, discount(shop));
    }

    public boolean isOffer(Shop shop, ShopItem item) {
        return shop != null && item != null && item.id != null && item.id.equals(shop.offerItemId);
    }

    private String choose(Shop shop, long day, String excludeId) {
        List<ShopItem> available = new ArrayList<>();
        List<ShopItem> all = new ArrayList<>();
        for (ShopItem item : shop.items) {
            if (item.id == null || item.stack() == null) {
                continue;
            }
            all.add(item);
            if (item.available()) {
                available.add(item);
            }
        }
        List<ShopItem> pool = available.isEmpty() ? new ArrayList<>(all) : available;
        if (excludeId != null) {
            pool.removeIf(item -> item.id.equals(excludeId));
            if (pool.isEmpty()) {
                // A one-item shop still has an offer; reroll simply keeps it.
                pool = available.isEmpty() ? all : available;
            }
        }
        if (pool.isEmpty()) {
            return null;
        }
        long seed = shop.id == null ? day : shop.id.hashCode() * 1_000_003L + day;
        int index = Math.floorMod(new Random(seed).nextInt(), pool.size());
        return pool.get(index).id;
    }
}
