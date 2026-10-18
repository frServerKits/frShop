package dev.limitshop.listener;

import dev.limitshop.LimitShopPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerListener implements Listener {

    private final LimitShopPlugin plugin;

    public PlayerListener(LimitShopPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.economy().handleJoin(event.getPlayer());
        plugin.debt().handleJoin(event.getPlayer());
    }
}
