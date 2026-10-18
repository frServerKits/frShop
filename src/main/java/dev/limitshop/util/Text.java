package dev.limitshop.util;

import dev.limitshop.model.Settings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class Text {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private Text() {
    }

    public static Component color(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return LEGACY.deserialize(input);
    }

    public static Component prefix() {
        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("LimitShop", NamedTextColor.GOLD))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY));
    }

    public static Component prefixed(Component message) {
        return prefix().append(message);
    }

    public static void send(CommandSender sender, Component message) {
        sender.sendMessage(prefixed(message));
    }

    public static void raw(CommandSender sender, Component message) {
        sender.sendMessage(message);
    }

    public static void success(CommandSender sender, Component message) {
        send(sender, Component.text().color(NamedTextColor.GREEN).append(message).build());
    }

    public static void error(CommandSender sender, Component message) {
        send(sender, Component.text().color(NamedTextColor.RED).append(message).build());
    }

    public static void info(CommandSender sender, Component message) {
        send(sender, Component.text().color(NamedTextColor.GRAY).append(message).build());
    }

    public static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    public static String itemName(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return "Air";
        }
        return PLAIN.serialize(stack.effectiveName());
    }

    public static String number(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    public static Component money(long amount, Settings settings) {
        return Component.text(settings.currencySymbol + number(amount), NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, false);
    }

    public static Component moneyWithName(long amount, Settings settings) {
        String name = amount == 1 ? settings.currencySingular : settings.currencyName;
        return Component.text(settings.currencySymbol + number(amount) + " " + name, NamedTextColor.GOLD)
                .decoration(TextDecoration.BOLD, false);
    }
}
