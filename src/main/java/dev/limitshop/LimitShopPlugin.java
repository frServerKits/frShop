package dev.limitshop;

import dev.limitshop.command.LimitShopCommand;
import dev.limitshop.economy.EconomyManager;
import dev.limitshop.listener.MenuListener;
import dev.limitshop.listener.PlayerListener;
import dev.limitshop.manager.AccountStore;
import dev.limitshop.manager.ShopManager;
import dev.limitshop.menu.MenuManager;
import dev.limitshop.model.Settings;
import dev.limitshop.service.DebtService;
import dev.limitshop.service.OfferService;
import dev.limitshop.service.PurchaseService;
import dev.limitshop.service.SellService;
import dev.limitshop.storage.DataStore;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class LimitShopPlugin extends JavaPlugin {

    private DataStore dataStore;
    private ShopManager shops;
    private AccountStore accounts;
    private EconomyManager economy;
    private OfferService offers;
    private PurchaseService purchases;
    private SellService sells;
    private DebtService debt;
    private MenuManager menus;

    @Override
    public void onEnable() {
        this.dataStore = new DataStore(this);
        this.dataStore.load();
        this.shops = new ShopManager(this, dataStore);
        this.accounts = new AccountStore(dataStore);
        this.economy = new EconomyManager(this, dataStore, accounts);
        this.offers = new OfferService(dataStore);
        this.purchases = new PurchaseService(this, dataStore, accounts, economy, offers);
        this.sells = new SellService(dataStore, shops, economy);
        this.debt = new DebtService(this, dataStore, accounts, shops, economy);
        this.menus = new MenuManager(this);

        this.economy.setup();

        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        PluginCommand command = getCommand("limitshop");
        if (command == null) {
            getLogger().severe("Command 'limitshop' is missing from plugin.yml!");
        } else {
            LimitShopCommand executor = new LimitShopCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        this.debt.start();

        getLogger().info("LimitShop enabled with " + shops.shops().size() + " shop(s).");
    }

    @Override
    public void onDisable() {
        if (debt != null) {
            debt.stop();
        }
        if (economy != null) {
            economy.shutdown();
        }
        if (dataStore != null) {
            dataStore.flushSync();
        }
    }

    public void reloadAll() {
        dataStore.reload();
        economy.configure(true);
        debt.start();
    }

    public Settings settings() {
        return dataStore.settings();
    }

    public DataStore dataStore() {
        return dataStore;
    }

    public ShopManager shops() {
        return shops;
    }

    public AccountStore accounts() {
        return accounts;
    }

    public EconomyManager economy() {
        return economy;
    }

    public OfferService offers() {
        return offers;
    }

    public PurchaseService purchases() {
        return purchases;
    }

    public SellService sells() {
        return sells;
    }

    public DebtService debt() {
        return debt;
    }

    public MenuManager menus() {
        return menus;
    }
}
