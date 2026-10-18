package dev.limitshop.model;

/**
 * Server-wide settings. Every value is editable in game with
 * {@code /limitshop settings <key> <value>}.
 */
public final class Settings {

    public String currencyName = "Shards";
    public String currencySingular = "Shard";
    public String currencySymbol = "\u2726";

    public long startingBalance = 100L;

    public boolean debtEnabled = true;
    public long debtLimit = 500L;
    public int debtGraceSeconds = 300;
    public int debtCollectSeconds = 60;
    public long debtMinItemValue = 2L;

    public int sellPercent = 50;
    public int defaultOfferDiscount = 30;

    /**
     * internal = LimitShop's own currency (also exposed to Vault),
     * external = use whichever economy Vault already has,
     * auto     = external when available, internal otherwise.
     */
    public String vaultMode = "internal";
}
