package dev.limitshop.model;

/**
 * Everything LimitShop persists lives in one JSON document. There is deliberately
 * no hand-edited configuration file: shops and settings are managed in game.
 */
public final class ShopData {

    public int version = 1;
    public Settings settings = new Settings();
    public java.util.Map<String, Shop> shops = new java.util.LinkedHashMap<>();
    public java.util.Map<String, Account> accounts = new java.util.HashMap<>();
}
