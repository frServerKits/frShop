package dev.limitshop.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Shop {

    public String id;
    public String title;
    public String icon = "BARREL";
    public long createdAt = System.currentTimeMillis();
    public List<ShopItem> items = new ArrayList<>();

    /**
     * -1 means "use the global default", otherwise a percentage between 0 and 95.
     */
    public int offerDiscount = -1;
    public long offerDay = Long.MIN_VALUE;
    public String offerItemId;

    public Shop() {
    }

    public Shop(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public ShopItem findItem(String itemId) {
        if (itemId == null) {
            return null;
        }
        for (ShopItem item : items) {
            if (itemId.equals(item.id)) {
                return item;
            }
        }
        return null;
    }

    public ShopItem findItemByUuid(UUID itemId) {
        return itemId == null ? null : findItem(itemId.toString());
    }

    public int indexOfItem(String itemId) {
        if (itemId == null) {
            return -1;
        }
        for (int i = 0; i < items.size(); i++) {
            if (itemId.equals(items.get(i).id)) {
                return i;
            }
        }
        return -1;
    }

    public ShopItem offerItem() {
        return findItem(offerItemId);
    }

    public int effectiveOfferDiscount(Settings settings) {
        int discount = offerDiscount >= 0 ? offerDiscount : settings.defaultOfferDiscount;
        return Math.max(0, Math.min(95, discount));
    }

    public long effectiveOfferPrice(ShopItem item, Settings settings) {
        return discountedPrice(item.price, effectiveOfferDiscount(settings));
    }

    public static long discountedPrice(long price, int percent) {
        if (price <= 0) {
            return 0;
        }
        int clamped = Math.max(0, Math.min(95, percent));
        long value = (price * (100L - clamped) + 99L) / 100L;
        return Math.max(1L, value);
    }
}
