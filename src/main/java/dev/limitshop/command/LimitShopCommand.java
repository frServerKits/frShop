package dev.limitshop.command;

import dev.limitshop.LimitShopPlugin;
import dev.limitshop.economy.EconomyManager;
import dev.limitshop.manager.ShopManager;
import dev.limitshop.model.Account;
import dev.limitshop.model.Settings;
import dev.limitshop.model.Shop;
import dev.limitshop.model.ShopItem;
import dev.limitshop.service.DebtService;
import dev.limitshop.service.SellService;
import dev.limitshop.util.ItemUtil;
import dev.limitshop.util.Text;
import dev.limitshop.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class LimitShopCommand implements CommandExecutor, TabCompleter {

    private static final long MAX_PRICE = 1_000_000_000_000L;
    private static final long MAX_STOCK = 1_000_000_000L;

    private static final Set<String> RESERVED = Set.of(
            "help", "version", "open", "summon", "create", "delete", "list", "shops",
            "add", "remove", "items", "price", "stock", "maxstock", "restock", "title",
            "icon", "offer", "balance", "pay", "debt", "sell", "credit", "setbalance",
            "setdebt", "collect", "settings", "economy", "reload", "shop"
    );

    private static final List<String> PLAYER_COMMANDS = List.of(
            "help", "list", "open", "balance", "debt", "pay", "sell", "version"
    );

    private static final List<String> ADMIN_COMMANDS = List.of(
            "help", "version", "open", "summon", "create", "delete", "list", "shops",
            "add", "remove", "items", "price", "stock", "maxstock", "restock", "title",
            "icon", "offer", "balance", "pay", "debt", "sell", "credit", "setbalance",
            "setdebt", "collect", "settings", "economy", "reload"
    );

    private static final List<String> SETTING_KEYS = List.of(
            "currency-name", "currency-singular", "currency-symbol", "starting-balance",
            "debt-enabled", "debt-limit", "debt-grace-seconds", "debt-collect-seconds",
            "debt-min-item-value", "sell-percent", "offer-discount", "vault-mode"
    );

    private final LimitShopPlugin plugin;

    public LimitShopCommand(LimitShopPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                help(sender);
                return true;
            }
            if (!use(sender)) {
                return true;
            }
            plugin.menus().openIndex(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "help" -> {
                help(sender);
                yield true;
            }
            case "version" -> {
                version(sender);
                yield true;
            }
            case "open" -> {
                open(sender, args);
                yield true;
            }
            case "summon" -> {
                summon(sender, args);
                yield true;
            }
            case "create" -> {
                create(sender, args);
                yield true;
            }
            case "delete" -> {
                delete(sender, args);
                yield true;
            }
            case "list", "shops" -> {
                list(sender);
                yield true;
            }
            case "add" -> {
                add(sender, args);
                yield true;
            }
            case "remove" -> {
                remove(sender, args);
                yield true;
            }
            case "items" -> {
                items(sender, args);
                yield true;
            }
            case "price" -> {
                itemValue(sender, args, "price");
                yield true;
            }
            case "stock" -> {
                itemValue(sender, args, "stock");
                yield true;
            }
            case "maxstock" -> {
                itemValue(sender, args, "maxstock");
                yield true;
            }
            case "restock" -> {
                restock(sender, args);
                yield true;
            }
            case "title" -> {
                title(sender, args);
                yield true;
            }
            case "icon" -> {
                icon(sender, args);
                yield true;
            }
            case "offer" -> {
                offer(sender, args);
                yield true;
            }
            case "balance" -> {
                balance(sender, args);
                yield true;
            }
            case "pay" -> {
                pay(sender, args);
                yield true;
            }
            case "debt" -> {
                debt(sender, args);
                yield true;
            }
            case "sell" -> {
                sell(sender, args);
                yield true;
            }
            case "credit" -> {
                credit(sender, args);
                yield true;
            }
            case "setbalance" -> {
                setBalance(sender, args);
                yield true;
            }
            case "setdebt" -> {
                setDebt(sender, args);
                yield true;
            }
            case "collect" -> {
                collect(sender, args);
                yield true;
            }
            case "settings" -> {
                settings(sender, args);
                yield true;
            }
            case "economy" -> {
                economy(sender, args);
                yield true;
            }
            case "reload" -> {
                reload(sender);
                yield true;
            }
            default -> directOpen(sender, sub, args);
        };
    }

    private boolean directOpen(CommandSender sender, String id, String[] args) {
        if (args.length != 1) {
            Text.error(sender, Component.text("Unknown subcommand. Use /limitshop help."));
            return true;
        }
        Shop shop = plugin.shops().get(id);
        if (shop == null) {
            Text.error(sender, Component.text("Unknown shop '" + id + "'. Use /limitshop list."));
            return true;
        }
        if (!use(sender)) {
            return true;
        }
        if (!(sender instanceof Player player)) {
            Text.error(sender, Component.text("Players only. Use /limitshop summon <shop> <player> from console."));
            return true;
        }
        plugin.menus().openShop(player, shop);
        return true;
    }

    private void open(CommandSender sender, String[] args) {
        if (!use(sender)) {
            return;
        }
        if (!(sender instanceof Player player)) {
            Text.error(sender, Component.text("Players only. Use /limitshop summon <shop> <player>."));
            return;
        }
        if (args.length != 2) {
            Text.error(sender, Component.text("Usage: /limitshop open <shop>"));
            return;
        }
        Shop shop = shop(sender, args[1]);
        if (shop != null) {
            plugin.menus().openShop(player, shop);
        }
    }

    private void summon(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2 || args.length > 3) {
            Text.error(sender, Component.text("Usage: /limitshop summon <shop> [player]"));
            return;
        }
        Shop shop = shop(sender, args[1]);
        if (shop == null) {
            return;
        }
        String targetName = args.length == 3 ? args[2] : (sender instanceof Player player ? player.getName() : null);
        if (targetName == null) {
            Text.error(sender, Component.text("Console must specify a player."));
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            Text.error(sender, Component.text("Player '" + targetName + "' is not online."));
            return;
        }
        plugin.menus().openShop(target, shop);
        Text.success(sender, Component.text("Opened shop '" + shop.id + "' for " + target.getName() + "."));
    }

    private void create(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2) {
            Text.error(sender, Component.text("Usage: /limitshop create <id> [title...]"));
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        String error = plugin.shops().validateId(id);
        if (error != null) {
            Text.error(sender, Component.text(error));
            return;
        }
        if (RESERVED.contains(id)) {
            Text.error(sender, Component.text("'" + id + "' is a command name and cannot be used as a shop id."));
            return;
        }
        if (plugin.shops().exists(id)) {
            Text.error(sender, Component.text("A shop with id '" + id + "' already exists."));
            return;
        }
        String title = args.length > 2 ? join(args, 2) : capitalize(id);
        if (title.length() > 64) {
            Text.error(sender, Component.text("The title must be at most 64 characters."));
            return;
        }
        Shop shop = plugin.shops().create(id, title);
        Text.success(sender, Component.text("Created shop '" + shop.id + "'."));
        Text.info(sender, Component.text("Hold an item and run: "));
        sender.sendMessage(Component.text()
                .append(Text.prefix())
                .append(Component.text("/limitshop add " + id + " <price> [stock] [maxStock]", NamedTextColor.WHITE)
                        .clickEvent(ClickEvent.suggestCommand("/limitshop add " + id + " ")))
                .build());
        Text.info(sender, Component.text("Then open it with /limitshop " + id + " or /limitshop summon " + id + "."));
    }

    private void delete(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2) {
            Text.error(sender, Component.text("Usage: /limitshop delete <shop> confirm"));
            return;
        }
        Shop shop = shop(sender, args[1]);
        if (shop == null) {
            return;
        }
        if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
            Text.error(sender, Component.text("This deletes the shop and its stock. Run: /limitshop delete " + shop.id + " confirm"));
            return;
        }
        plugin.shops().delete(shop);
        Text.success(sender, Component.text("Deleted shop '" + shop.id + "'."));
    }

    private void list(CommandSender sender) {
        if (!use(sender)) {
            return;
        }
        List<Shop> shops = plugin.shops().shops();
        if (shops.isEmpty()) {
            Text.info(sender, Component.text("No shops yet."));
            return;
        }
        Text.info(sender, Component.text("Shops (" + shops.size() + "):"));
        for (Shop shop : shops) {
            Component line = Component.text("  " + shop.id + " ", NamedTextColor.YELLOW)
                    .append(Text.color(shop.title))
                    .append(Component.text(" (" + shop.items.size() + " items)", NamedTextColor.DARK_GRAY))
                    .clickEvent(ClickEvent.runCommand("/limitshop open " + shop.id))
                    .hoverEvent(HoverEvent.showText(Component.text("Open " + shop.id, NamedTextColor.GREEN)));
            sender.sendMessage(Text.prefixed(line));
        }
    }

    private void add(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length < 3 || args.length > 5) {
            Text.error(sender, Component.text("Usage (hold an item): /limitshop add <shop> <price> [stock] [maxStock]"));
            return;
        }
        Shop shop = shop(sender, args[1]);
        if (shop == null) {
            return;
        }
        Long price = parseLong(sender, args[2], 1L, MAX_PRICE, "price");
        if (price == null) {
            return;
        }
        long stock = -1L;
        if (args.length >= 4) {
            Long parsed = parseLong(sender, args[3], -1L, MAX_STOCK, "stock");
            if (parsed == null) {
                return;
            }
            stock = parsed;
        }
        long maxStock = stock >= 0 ? stock : -1L;
        if (args.length >= 5) {
            Long parsed = parseLong(sender, args[4], -1L, MAX_STOCK, "maxStock");
            if (parsed == null) {
                return;
            }
            maxStock = parsed;
        }
        if (maxStock >= 0 && stock > maxStock) {
            maxStock = stock;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (ItemUtil.isAir(held)) {
            Text.error(sender, Component.text("Hold the item you want to add to the shop."));
            return;
        }
        ShopItem item = plugin.shops().addItem(shop, held, price, stock, maxStock);
        Text.success(sender, Component.text("Added " + Text.itemName(held) + " to '" + shop.id + "' as item #"
                + shop.items.size() + " for " + Text.plain(Text.money(price, plugin.settings())) + "."));
        if (!item.unlimited()) {
            Text.info(sender, Component.text("Stock: " + Text.number(stock) + " (max " + Text.number(maxStock)
                    + "). Restock with /limitshop restock " + shop.id + " [amount]."));
        } else {
            Text.info(sender, Component.text("Stock: unlimited. Use /limitshop stock " + shop.id + " "
                    + shop.items.size() + " <amount> to limit it."));
        }
        Text.info(sender, Component.text("Set the daily offer: /limitshop offer " + shop.id + " " + plugin.settings().defaultOfferDiscount));
    }

    private void remove(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length != 3) {
            Text.error(sender, Component.text("Usage: /limitshop remove <shop> <item#>"));
            return;
        }
        Shop shop = shop(sender, args[1]);
        if (shop == null) {
            return;
        }
        Integer index = parseInt(sender, args[2], 1, Integer.MAX_VALUE, "item number");
        if (index == null) {
            return;
        }
        ShopItem item = plugin.shops().itemAt(shop, index);
        if (item == null) {
            Text.error(sender, Component.text("There is no item #" + index + " in '" + shop.id + "'."));
            return;
        }
        String name = item.stack() == null ? "broken item" : Text.itemName(item.stack());
        plugin.shops().removeItem(shop, index);
        Text.success(sender, Component.text("Removed #" + index + " (" + name + ") from '" + shop.id + "'."));
    }

    private void items(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length != 2) {
            Text.error(sender, Component.text("Usage: /limitshop items <shop>"));
            return;
        }
        Shop shop = shop(sender, args[1]);
        if (shop == null) {
            return;
        }
        if (shop.items.isEmpty()) {
            Text.info(sender, Component.text("Shop '" + shop.id + "' has no items. Hold an item and use /limitshop add."));
            return;
        }
        Settings settings = plugin.settings();
        Text.info(sender, Component.text("Items in '" + shop.id + "' (" + shop.items.size() + "):"));
        for (int i = 0; i < shop.items.size(); i++) {
            ShopItem item = shop.items.get(i);
            String name = item.stack() == null ? "BROKEN ITEM" : Text.itemName(item.stack());
            String stock = item.unlimited() ? "unlimited" : (Text.number(item.stock) + "/"
                    + (item.maxStock < 0 ? "?" : Text.number(item.maxStock)));
            Component line = Component.text("  #" + (i + 1) + " ", NamedTextColor.YELLOW)
                    .append(Component.text(name, NamedTextColor.WHITE))
                    .append(Component.text("  price ", NamedTextColor.DARK_GRAY))
                    .append(Text.money(item.price, settings))
                    .append(Component.text("  stock " + stock + "  sold " + Text.number(item.sold), NamedTextColor.DARK_GRAY))
                    .clickEvent(ClickEvent.suggestCommand("/limitshop price " + shop.id + " " + (i + 1) + " "))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to edit this item's price", NamedTextColor.GREEN)));
            sender.sendMessage(Text.prefixed(line));
        }
    }

    private void itemValue(CommandSender sender, String[] args, String type) {
        if (!admin(sender)) {
            return;
        }
        if (args.length != 4) {
            Text.error(sender, Component.text("Usage: /limitshop " + type + " <shop> <item#> <value>"));
            return;
        }
        Shop shop = shop(sender, args[1]);
        if (shop == null) {
            return;
        }
        Integer index = parseInt(sender, args[2], 1, Integer.MAX_VALUE, "item number");
        if (index == null) {
            return;
        }
        ShopItem item = plugin.shops().itemAt(shop, index);
        if (item == null) {
            Text.error(sender, Component.text("There is no item #" + index + " in '" + shop.id + "'."));
            return;
        }
        switch (type) {
            case "price" -> {
                Long value = parseLong(sender, args[3], 1L, MAX_PRICE, "price");
                if (value == null) {
                    return;
                }
                plugin.shops().setPrice(item, value);
                Text.success(sender, Component.text("Item #" + index + " now costs " + Text.plain(Text.money(value, plugin.settings())) + "."));
            }
            case "stock" -> {
                Long value = parseLong(sender, args[3], -1L, MAX_STOCK, "stock");
                if (value == null) {
                    return;
                }
                plugin.shops().setStock(item, value);
                Text.success(sender, Component.text("Item #" + index + " stock is now "
                        + (value < 0 ? "unlimited" : Text.number(value)) + "."));
            }
            case "maxstock" -> {
                Long value = parseLong(sender, args[3], -1L, MAX_STOCK, "maxStock");
                if (value == null) {
                    return;
                }
                plugin.shops().setMaxStock(item, value);
                Text.success(sender, Component.text("Item #" + index + " max stock is now "
                        + (value < 0 ? "unlimited" : Text.number(value)) + "."));
            }
            default -> Text.error(sender, Component.text("Unknown value type."));
        }
    }

    private void restock(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 2 || args.length > 3) {
            Text.error(sender, Component.text("Usage: /limitshop restock <shop> [amount]"));
            return;
        }
        Shop shop = shop(sender, args[1]);
        if (shop == null) {
            return;
        }
        long amount = -1L;
        if (args.length == 3) {
            Long parsed = parseLong(sender, args[2], 0L, MAX_STOCK, "amount");
            if (parsed == null) {
                return;
            }
            amount = parsed;
        }
        int changed = plugin.shops().restock(shop, amount);
        if (changed == 0) {
            Text.info(sender, Component.text("No finite-stock items needed restocking in '" + shop.id + "'."));
        } else {
            Text.success(sender, Component.text("Restocked " + changed + " item(s) in '" + shop.id + "'."));
        }
    }

    private void title(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length < 3) {
            Text.error(sender, Component.text("Usage: /limitshop title <shop> <title...>"));
            return;
        }
        Shop shop = shop(sender, args[1]);
        if (shop == null) {
            return;
        }
        String title = join(args, 2);
        if (title.length() > 64) {
            Text.error(sender, Component.text("The title must be at most 64 characters."));
            return;
        }
        plugin.shops().setTitle(shop, title);
        Text.success(sender, Component.text("Shop title updated. Colors use & codes."));
    }

    private void icon(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length != 2) {
            Text.error(sender, Component.text("Usage (hold an item): /limitshop icon <shop>"));
            return;
        }
        Shop shop = shop(sender, args[1]);
        if (shop == null) {
            return;
        }
        Material material = player.getInventory().getItemInMainHand().getType();
        if (material.isAir() || !material.isItem()) {
            Text.error(sender, Component.text("Hold the item whose icon you want to use."));
            return;
        }
        plugin.shops().setIcon(shop, material);
        Text.success(sender, Component.text("Icon for '" + shop.id + "' set to " + material.name() + "."));
    }

    private void offer(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length != 3) {
            Text.error(sender, Component.text("Usage: /limitshop offer <shop> <percent|reroll|reset>"));
            return;
        }
        Shop shop = shop(sender, args[1]);
        if (shop == null) {
            return;
        }
        if (shop.items.isEmpty()) {
            Text.error(sender, Component.text("Add items to the shop before creating an offer."));
            return;
        }
        if (args[2].equalsIgnoreCase("reroll")) {
            ShopItem item = plugin.offers().reroll(shop);
            if (item == null || item.stack() == null) {
                Text.error(sender, Component.text("Could not choose an offer item."));
            } else {
                Text.success(sender, Component.text("Today's offer is now " + Text.itemName(item.stack())
                        + " at -" + plugin.offers().discount(shop) + "%."));
            }
            return;
        }
        if (args[2].equalsIgnoreCase("reset")) {
            shop.offerDiscount = -1;
            plugin.dataStore().markDirty();
            plugin.offers().ensureOffer(shop);
            Text.success(sender, Component.text("Offer discount reset to the global default ("
                    + plugin.settings().defaultOfferDiscount + "%)."));
            return;
        }
        Integer percent = parseInt(sender, args[2], 0, 95, "percent");
        if (percent == null) {
            return;
        }
        shop.offerDiscount = percent;
        plugin.dataStore().markDirty();
        plugin.offers().ensureOffer(shop);
        Text.success(sender, Component.text("Offer discount for '" + shop.id + "' set to -" + percent + "%."));
    }

    private void balance(CommandSender sender, String[] args) {
        if (!use(sender)) {
            return;
        }
        if (args.length > 2) {
            Text.error(sender, Component.text("Usage: /limitshop balance [player]"));
            return;
        }
        Target target;
        if (args.length == 2) {
            if (!admin(sender)) {
                return;
            }
            target = target(sender, args[1]);
        } else {
            Player self = player(sender);
            if (self == null) {
                return;
            }
            target = new Target(self.getUniqueId(), self.getName());
        }
        if (target == null) {
            return;
        }
        Settings settings = plugin.settings();
        Account account = plugin.accounts().getOrCreate(target.uuid());
        Text.send(sender, Component.text(target.name() + "'s balance: ", NamedTextColor.GRAY)
                .append(Text.moneyWithName(plugin.economy().getBalance(target.uuid()), settings)));
        if (account.debt > 0) {
            Text.send(sender, Component.text("Debt: ", NamedTextColor.RED).append(Text.money(account.debt, settings)));
        }
    }

    private void pay(CommandSender sender, String[] args) {
        if (!use(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length != 2) {
            Text.error(sender, Component.text("Usage: /limitshop pay <amount>"));
            return;
        }
        Long amount = parseLong(sender, args[1], 1L, Long.MAX_VALUE, "amount");
        if (amount == null) {
            return;
        }
        Account account = plugin.accounts().getOrCreate(player.getUniqueId());
        if (account.debt <= 0) {
            Text.info(sender, Component.text("You have no debt."));
            return;
        }
        long balance = plugin.economy().getBalance(player.getUniqueId());
        long payment = Math.min(Math.min(amount, account.debt), balance);
        if (payment <= 0) {
            Text.error(sender, Component.text("You have no money to pay with. Sell items with /limitshop sell."));
            return;
        }
        if (!plugin.economy().withdraw(player.getUniqueId(), payment)) {
            Text.error(sender, Component.text("The payment could not be completed."));
            return;
        }
        plugin.accounts().reduceDebt(player.getUniqueId(), payment);
        Text.success(sender, Component.text("Paid " + Text.plain(Text.money(payment, plugin.settings()))
                + ". Debt left: " + Text.plain(Text.money(account.debt, plugin.settings())) + "."));
        if (account.debt <= 0) {
            Text.success(sender, Component.text("Your debt is cleared. The collector has stopped."));
        }
    }

    private void debt(CommandSender sender, String[] args) {
        if (!use(sender)) {
            return;
        }
        if (args.length > 2) {
            Text.error(sender, Component.text("Usage: /limitshop debt [player]"));
            return;
        }
        Target target;
        if (args.length == 2) {
            if (!admin(sender)) {
                return;
            }
            target = target(sender, args[1]);
        } else {
            Player self = player(sender);
            if (self == null) {
                return;
            }
            target = new Target(self.getUniqueId(), self.getName());
        }
        if (target == null) {
            return;
        }
        Account account = plugin.accounts().getOrCreate(target.uuid());
        Settings settings = plugin.settings();
        if (account.debt <= 0) {
            Text.send(sender, Component.text(target.name() + " has no debt.", NamedTextColor.GREEN));
            return;
        }
        long now = System.currentTimeMillis();
        long graceEnd = account.debtSince + TimeUnit.SECONDS.toMillis(Math.max(0, settings.debtGraceSeconds));
        String status;
        if (now < graceEnd) {
            status = "Grace period: " + TimeUtil.duration(graceEnd - now) + " left";
        } else if (account.lastCollect > now) {
            status = "Next item: in " + TimeUtil.duration(account.lastCollect - now);
        } else {
            status = "Repossession is due now";
        }
        Text.send(sender, Component.text(target.name() + "'s debt: ", NamedTextColor.RED)
                .append(Text.money(account.debt, settings)));
        Text.info(sender, Component.text(status + ". " + "One valuable item is taken per interval."));
        Text.info(sender, Component.text("Pay with /limitshop pay <amount> or sell with /limitshop sell."));
    }

    private void sell(CommandSender sender, String[] args) {
        if (!use(sender)) {
            return;
        }
        Player player = player(sender);
        if (player == null) {
            return;
        }
        if (args.length > 2 || (args.length == 2 && !args[1].equalsIgnoreCase("all"))) {
            Text.error(sender, Component.text("Usage: /limitshop sell [all]"));
            return;
        }
        SellService.SellResult result = args.length == 2
                ? plugin.sells().sellAll(player)
                : plugin.sells().sellHand(player);
        Text.send(player, result.feedback());
        if (result.success()) {
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.1f);
        }
    }

    private void credit(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length != 3) {
            Text.error(sender, Component.text("Usage: /limitshop credit <player> <amount>"));
            return;
        }
        Target target = target(sender, args[1]);
        if (target == null) {
            return;
        }
        Long amount = parseLong(sender, args[2], 1L, MAX_PRICE, "amount");
        if (amount == null) {
            return;
        }
        plugin.economy().deposit(target.uuid(), amount);
        Text.success(sender, Component.text("Gave " + Text.plain(Text.money(amount, plugin.settings()))
                + " to " + target.name() + "."));
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null) {
            Text.success(online, Component.text("You received " + Text.plain(Text.money(amount, plugin.settings())) + "."));
        }
    }

    private void setBalance(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length != 3) {
            Text.error(sender, Component.text("Usage: /limitshop setbalance <player> <amount>"));
            return;
        }
        Target target = target(sender, args[1]);
        if (target == null) {
            return;
        }
        Long amount = parseLong(sender, args[2], 0L, MAX_PRICE, "amount");
        if (amount == null) {
            return;
        }
        plugin.economy().setBalance(target.uuid(), amount);
        Text.success(sender, Component.text("Set " + target.name() + "'s balance to "
                + Text.plain(Text.money(amount, plugin.settings())) + "."));
    }

    private void setDebt(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length != 3) {
            Text.error(sender, Component.text("Usage: /limitshop setdebt <player> <amount>"));
            return;
        }
        Target target = target(sender, args[1]);
        if (target == null) {
            return;
        }
        Long amount = parseLong(sender, args[2], 0L, MAX_PRICE, "amount");
        if (amount == null) {
            return;
        }
        plugin.accounts().setDebt(target.uuid(), amount);
        Text.success(sender, Component.text("Set " + target.name() + "'s debt to "
                + Text.plain(Text.money(amount, plugin.settings())) + "."));
    }

    private void collect(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length != 2) {
            Text.error(sender, Component.text("Usage: /limitshop collect <player>"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            Text.error(sender, Component.text("That player is not online."));
            return;
        }
        boolean collected = plugin.debt().forceCollect(target);
        if (collected) {
            Text.success(sender, Component.text("Debt collection triggered for " + target.getName() + "."));
        } else {
            Text.info(sender, Component.text(target.getName() + " has no debt."));
        }
    }

    private void settings(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length == 1) {
            showSettings(sender);
            return;
        }
        String key = args[1].toLowerCase(Locale.ROOT);
        if (!SETTING_KEYS.contains(key)) {
            Text.error(sender, Component.text("Unknown setting '" + key + "'. Keys: " + String.join(", ", SETTING_KEYS)));
            return;
        }
        if (args.length < 3) {
            Text.error(sender, Component.text("Usage: /limitshop settings " + key + " <value>"));
            return;
        }
        String value = join(args, 2);
        Settings settings = plugin.settings();
        switch (key) {
            case "currency-name" -> {
                if (value.isBlank() || value.length() > 32) {
                    Text.error(sender, Component.text("Currency name must be 1-32 characters."));
                    return;
                }
                settings.currencyName = value;
                Text.success(sender, Component.text("Currency name set to '" + value + "'."));
            }
            case "currency-singular" -> {
                if (value.isBlank() || value.length() > 32) {
                    Text.error(sender, Component.text("Singular currency name must be 1-32 characters."));
                    return;
                }
                settings.currencySingular = value;
                Text.success(sender, Component.text("Singular currency name set to '" + value + "'."));
            }
            case "currency-symbol" -> {
                if (value.isBlank() || value.length() > 8) {
                    Text.error(sender, Component.text("Currency symbol must be 1-8 characters."));
                    return;
                }
                settings.currencySymbol = value;
                Text.success(sender, Component.text("Currency symbol set to '" + value + "'."));
            }
            case "starting-balance" -> {
                Long parsed = parseLong(sender, value, 0L, MAX_PRICE, "starting balance");
                if (parsed == null) {
                    return;
                }
                settings.startingBalance = parsed;
                Text.success(sender, Component.text("Starting balance set to " + Text.plain(Text.money(parsed, settings)) + " for new players."));
            }
            case "debt-enabled" -> {
                Boolean parsed = parseBoolean(sender, value, "debt-enabled");
                if (parsed == null) {
                    return;
                }
                settings.debtEnabled = parsed;
                Text.success(sender, Component.text("Debt is now " + (settings.debtEnabled ? "enabled" : "disabled") + "."));
            }
            case "debt-limit" -> {
                Long parsed = parseLong(sender, value, 0L, MAX_PRICE, "debt limit");
                if (parsed == null) {
                    return;
                }
                settings.debtLimit = parsed;
                Text.success(sender, Component.text("Debt limit set to " + Text.plain(Text.money(parsed, settings)) + "."));
            }
            case "debt-grace-seconds" -> {
                Integer parsed = parseInt(sender, value, 0, 31_536_000, "grace seconds");
                if (parsed == null) {
                    return;
                }
                settings.debtGraceSeconds = parsed;
                Text.success(sender, Component.text("Debt grace period set to " + TimeUtil.durationSeconds(parsed) + "."));
            }
            case "debt-collect-seconds" -> {
                Integer parsed = parseInt(sender, value, 1, 31_536_000, "collect seconds");
                if (parsed == null) {
                    return;
                }
                settings.debtCollectSeconds = parsed;
                Text.success(sender, Component.text("Repossession interval set to " + TimeUtil.durationSeconds(parsed) + "."));
            }
            case "debt-min-item-value" -> {
                Long parsed = parseLong(sender, value, 0L, MAX_PRICE, "minimum item value");
                if (parsed == null) {
                    return;
                }
                settings.debtMinItemValue = parsed;
                Text.success(sender, Component.text("Collector minimum item value set to " + Text.plain(Text.money(parsed, settings)) + "."));
            }
            case "sell-percent" -> {
                Integer parsed = parseInt(sender, value, 0, 100, "sell percent");
                if (parsed == null) {
                    return;
                }
                settings.sellPercent = parsed;
                Text.success(sender, Component.text("Shop buy-back rate set to " + parsed + "% of the sale price."));
            }
            case "offer-discount" -> {
                Integer parsed = parseInt(sender, value, 0, 95, "offer discount");
                if (parsed == null) {
                    return;
                }
                settings.defaultOfferDiscount = parsed;
                Text.success(sender, Component.text("Default daily offer discount set to " + parsed + "%."));
            }
            case "vault-mode" -> {
                String raw = value.toLowerCase(Locale.ROOT);
                if (!raw.equals("auto") && !raw.equals("internal") && !raw.equals("external")) {
                    Text.error(sender, Component.text("Vault mode must be auto, internal or external."));
                    return;
                }
                settings.vaultMode = EconomyManager.normalizeMode(raw);
                plugin.economy().configure(true);
                Text.success(sender, Component.text("Vault mode set to " + settings.vaultMode
                        + " (effective: " + plugin.economy().effectiveMode() + ")."));
                Text.info(sender, Component.text("Internal and external balances are separate; switching does not transfer money."));
            }
            default -> {
                Text.error(sender, Component.text("Unknown setting."));
                return;
            }
        }
        plugin.dataStore().markDirty();
    }

    private void showSettings(CommandSender sender) {
        Settings settings = plugin.settings();
        Text.info(sender, Component.text("LimitShop settings (change with /limitshop settings <key> <value>):"));
        sendSetting(sender, "currency-name", settings.currencyName);
        sendSetting(sender, "currency-singular", settings.currencySingular);
        sendSetting(sender, "currency-symbol", settings.currencySymbol);
        sendSetting(sender, "starting-balance", Text.number(settings.startingBalance));
        sendSetting(sender, "debt-enabled", String.valueOf(settings.debtEnabled));
        sendSetting(sender, "debt-limit", Text.number(settings.debtLimit));
        sendSetting(sender, "debt-grace-seconds", String.valueOf(settings.debtGraceSeconds));
        sendSetting(sender, "debt-collect-seconds", String.valueOf(settings.debtCollectSeconds));
        sendSetting(sender, "debt-min-item-value", Text.number(settings.debtMinItemValue));
        sendSetting(sender, "sell-percent", String.valueOf(settings.sellPercent));
        sendSetting(sender, "offer-discount", String.valueOf(settings.defaultOfferDiscount));
        sendSetting(sender, "vault-mode", settings.vaultMode + " (effective: " + plugin.economy().effectiveMode() + ")");
    }

    private void sendSetting(CommandSender sender, String key, String value) {
        Component line = Component.text("  " + key + ": ", NamedTextColor.GRAY)
                .append(Component.text(value, NamedTextColor.WHITE))
                .clickEvent(ClickEvent.suggestCommand("/limitshop settings " + key + " "))
                .hoverEvent(HoverEvent.showText(Component.text("Click to edit", NamedTextColor.GREEN)));
        sender.sendMessage(Text.prefixed(line));
    }

    private void economy(CommandSender sender, String[] args) {
        if (!admin(sender)) {
            return;
        }
        if (args.length != 2) {
            Text.error(sender, Component.text("Usage: /limitshop economy <auto|internal|external>"));
            return;
        }
        String raw = args[1].toLowerCase(Locale.ROOT);
        if (!raw.equals("auto") && !raw.equals("internal") && !raw.equals("external")) {
            Text.error(sender, Component.text("Mode must be auto, internal or external."));
            return;
        }
        plugin.settings().vaultMode = raw;
        plugin.dataStore().markDirty();
        plugin.economy().configure(true);
        Text.success(sender, Component.text("Economy mode set to " + raw + " (effective: " + plugin.economy().effectiveMode()
                + ", provider: " + plugin.economy().serviceName() + ")."));
        Text.info(sender, Component.text("Internal and external balances are separate; switching does not transfer money."));
    }

    private void reload(CommandSender sender) {
        if (!admin(sender)) {
            return;
        }
        plugin.reloadAll();
        Text.success(sender, Component.text("Reloaded data.json and the economy hook."));
    }

    private void version(CommandSender sender) {
        Text.send(sender, Component.text("LimitShop " + plugin.getPluginMeta().getVersion()
                + " for Paper 26.3", NamedTextColor.GOLD));
    }

    private void help(CommandSender sender) {
        Text.raw(sender, Component.text("LimitShop", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(" - instant-click shops", NamedTextColor.GRAY)));
        sendHelp(sender, "/limitshop", "Open the shop list");
        sendHelp(sender, "/limitshop <shop>", "Open a shop directly");
        sendHelp(sender, "/limitshop sell [all]", "Sell the held item, or all matching items");
        sendHelp(sender, "/limitshop balance [player]", "Check a balance");
        sendHelp(sender, "/limitshop debt [player]", "Check debt and repossession timing");
        sendHelp(sender, "/limitshop pay <amount>", "Pay off debt");
        sendHelp(sender, "/limitshop list", "List shops");
        if (!sender.hasPermission("limitshop.admin")) {
            return;
        }
        Text.raw(sender, Component.text("Admin", NamedTextColor.GOLD, TextDecoration.BOLD));
        sendHelp(sender, "/limitshop create <id> [title...]", "Create a new shop");
        sendHelp(sender, "/limitshop add <shop> <price> [stock] [maxStock]", "Add the held item to a shop");
        sendHelp(sender, "/limitshop remove <shop> <item#>", "Remove an item");
        sendHelp(sender, "/limitshop items <shop>", "List items and their numbers");
        sendHelp(sender, "/limitshop price|stock|maxstock <shop> <item#> <value>", "Edit an item");
        sendHelp(sender, "/limitshop restock <shop> [amount]", "Refill finite stock");
        sendHelp(sender, "/limitshop offer <shop> <percent|reroll|reset>", "Set today's offer discount");
        sendHelp(sender, "/limitshop title|icon <shop>", "Change the shop title or icon");
        sendHelp(sender, "/limitshop summon <shop> [player]", "Open a shop for someone");
        sendHelp(sender, "/limitshop delete <shop> confirm", "Delete a shop");
        sendHelp(sender, "/limitshop settings [key] [value]", "All server settings (no config file)");
        sendHelp(sender, "/limitshop economy <auto|internal|external>", "Choose the Vault economy mode");
        sendHelp(sender, "/limitshop credit|setbalance|setdebt <player> <amount>", "Manage wallets and debt");
        sendHelp(sender, "/limitshop collect <player>", "Trigger repossession once");
        sendHelp(sender, "/limitshop reload", "Reload data.json");
    }

    private void sendHelp(CommandSender sender, String command, String description) {
        Component line = Component.text("  " + command + " ", NamedTextColor.YELLOW)
                .append(Component.text("- " + description, NamedTextColor.GRAY))
                .clickEvent(ClickEvent.suggestCommand(command.contains("<") ? command.substring(0, command.indexOf('<')) : command))
                .hoverEvent(HoverEvent.showText(Component.text("Click to put this command in chat", NamedTextColor.GREEN)));
        sender.sendMessage(Text.prefixed(line));
    }

    private boolean use(CommandSender sender) {
        if (sender.hasPermission("limitshop.use") || sender.hasPermission("limitshop.admin")) {
            return true;
        }
        Text.error(sender, Component.text("You do not have permission to use shops."));
        return false;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("limitshop.admin")) {
            return true;
        }
        Text.error(sender, Component.text("You do not have permission to manage shops."));
        return false;
    }

    private Player player(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        Text.error(sender, Component.text("Players only."));
        return null;
    }

    private Shop shop(CommandSender sender, String id) {
        Shop shop = plugin.shops().get(id);
        if (shop == null) {
            Text.error(sender, Component.text("No shop with id '" + id + "'. Use /limitshop list."));
        }
        return shop;
    }

    private Target target(CommandSender sender, String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return new Target(online.getUniqueId(), online.getName());
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (!offline.hasPlayedBefore() && !offline.isOnline()) {
            Text.error(sender, Component.text("Player '" + name + "' has never joined this server."));
            return null;
        }
        String resolved = offline.getName() == null ? name : offline.getName();
        return new Target(offline.getUniqueId(), resolved);
    }

    private Long parseLong(CommandSender sender, String raw, long min, long max, String label) {
        try {
            long value = Long.parseLong(raw);
            if (value < min || value > max) {
                Text.error(sender, Component.text("The " + label + " must be between " + min + " and " + max + "."));
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            Text.error(sender, Component.text("'" + raw + "' is not a valid " + label + "."));
            return null;
        }
    }

    private Integer parseInt(CommandSender sender, String raw, int min, int max, String label) {
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                Text.error(sender, Component.text("The " + label + " must be between " + min + " and " + max + "."));
                return null;
            }
            return value;
        } catch (NumberFormatException ex) {
            Text.error(sender, Component.text("'" + raw + "' is not a valid " + label + "."));
            return null;
        }
    }

    private Boolean parseBoolean(CommandSender sender, String raw, String label) {
        if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("on") || raw.equalsIgnoreCase("yes")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false") || raw.equalsIgnoreCase("off") || raw.equalsIgnoreCase("no")) {
            return false;
        }
        Text.error(sender, Component.text("The " + label + " value must be true or false."));
        return null;
    }

    private String join(String[] args, int start) {
        return String.join(" ", Arrays.copyOfRange(args, start, args.length));
    }

    private String capitalize(String id) {
        if (id.isEmpty()) {
            return id;
        }
        return Character.toUpperCase(id.charAt(0)) + id.substring(1);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        boolean isAdmin = sender.hasPermission("limitshop.admin");
        if (args.length == 1) {
            addMatches(result, isAdmin ? ADMIN_COMMANDS : PLAYER_COMMANDS, args[0]);
            for (Shop shop : plugin.shops().shops()) {
                if (!RESERVED.contains(shop.id)) {
                    addMatch(result, shop.id, args[0]);
                }
            }
            return result;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "open", "summon", "delete", "add", "remove", "items", "price", "stock",
                        "maxstock", "restock", "title", "icon", "offer" -> {
                    for (Shop shop : plugin.shops().shops()) {
                        addMatch(result, shop.id, args[1]);
                    }
                }
                case "balance", "debt", "credit", "setbalance", "setdebt", "collect" -> {
                    if (isAdmin) {
                        addPlayers(result, args[1]);
                    }
                }
                case "settings" -> addMatches(result, SETTING_KEYS, args[1]);
                case "economy" -> addMatches(result, List.of("auto", "internal", "external"), args[1]);
                case "sell" -> addMatch(result, "all", args[1]);
                default -> {
                }
            }
            return result;
        }

        if (args.length == 3) {
            switch (sub) {
                case "price", "stock", "maxstock", "remove" -> {
                    Shop shop = plugin.shops().get(args[1]);
                    if (shop != null) {
                        for (int i = 1; i <= Math.min(shop.items.size(), 200); i++) {
                            addMatch(result, String.valueOf(i), args[2]);
                        }
                    }
                }
                case "offer" -> addMatches(result, List.of("30", "reroll", "reset"), args[2]);
                case "settings" -> addSettingValues(result, args[1], args[2]);
                case "summon" -> {
                    if (isAdmin) {
                        addPlayers(result, args[2]);
                    }
                }
                case "credit", "setbalance", "setdebt" -> addMatch(result, "100", args[2]);
                case "restock" -> addMatch(result, "100", args[2]);
                case "add" -> addMatch(result, "10", args[2]);
                default -> {
                }
            }
            return result;
        }

        if (args.length == 4) {
            if (sub.equals("add")) {
                addMatch(result, "100", args[3]);
            } else if (sub.equals("price") || sub.equals("stock") || sub.equals("maxstock")) {
                addMatch(result, "10", args[3]);
            }
        } else if (args.length == 5 && sub.equals("add")) {
            addMatch(result, "1000", args[4]);
        }
        return result;
    }

    private void addSettingValues(List<String> result, String key, String prefix) {
        switch (key.toLowerCase(Locale.ROOT)) {
            case "debt-enabled" -> addMatches(result, List.of("true", "false"), prefix);
            case "vault-mode" -> addMatches(result, List.of("auto", "internal", "external"), prefix);
            case "currency-symbol" -> addMatches(result, List.of("\u2726", "$", "coins"), prefix);
            case "sell-percent" -> addMatches(result, List.of("25", "50", "75"), prefix);
            case "offer-discount" -> addMatches(result, List.of("10", "25", "30", "50"), prefix);
            default -> addMatch(result, "10", prefix);
        }
    }

    private void addPlayers(List<String> result, String prefix) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            addMatch(result, player.getName(), prefix);
        }
    }

    private void addMatches(List<String> result, List<String> values, String prefix) {
        for (String value : values) {
            addMatch(result, value, prefix);
        }
    }

    private void addMatch(List<String> result, String value, String prefix) {
        if (value != null && value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))
                && !result.contains(value)) {
            result.add(value);
        }
    }

    private record Target(UUID uuid, String name) {
    }
}
