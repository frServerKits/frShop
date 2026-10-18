package dev.limitshop.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import dev.limitshop.LimitShopPlugin;
import dev.limitshop.model.Settings;
import dev.limitshop.model.ShopData;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;

/**
 * Loads and saves one JSON document. Saves are debounced and the JSON snapshot
 * is taken on the main thread, so the asynchronous disk write only touches an
 * immutable string.
 */
public final class DataStore {

    private final LimitShopPlugin plugin;
    private final Path file;
    private final Path tempFile;
    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private final Object writeLock = new Object();

    private ShopData data = new ShopData();
    private boolean dirty;
    private boolean saveScheduled;
    private boolean stopping;
    private long generation;
    private BukkitTask saveTask;

    public DataStore(LimitShopPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("data.json");
        this.tempFile = plugin.getDataFolder().toPath().resolve("data.json.tmp");
    }

    public void load() {
        stopping = false;
        generation++;
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            if (!Files.exists(file)) {
                data = new ShopData();
                writeSync(gson.toJson(data), generation);
                return;
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            ShopData loaded = gson.fromJson(json, ShopData.class);
            if (loaded == null) {
                throw new JsonParseException("data.json is empty");
            }
            data = normalize(loaded);
        } catch (Exception ex) {
            plugin.getLogger().severe("Could not read data.json: " + ex.getMessage());
            backupBrokenFile();
            data = new ShopData();
            writeSync(gson.toJson(data), generation);
        }
    }

    public void reload() {
        flushSync();
        stopping = false;
        load();
    }

    private ShopData normalize(ShopData loaded) {
        if (loaded.settings == null) {
            loaded.settings = new Settings();
        }
        if (loaded.shops == null) {
            loaded.shops = new java.util.LinkedHashMap<>();
        }
        if (loaded.accounts == null) {
            loaded.accounts = new java.util.HashMap<>();
        }
        loaded.shops.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        for (var entry : loaded.shops.entrySet()) {
            entry.getValue().id = entry.getKey();
            if (entry.getValue().items == null) {
                entry.getValue().items = new java.util.ArrayList<>();
            }
            entry.getValue().items.removeIf(item -> item == null || item.item == null);
        }
        return loaded;
    }

    private void backupBrokenFile() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            Path backup = file.resolveSibling("data.json.corrupt-" + Instant.now().toEpochMilli());
            Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
            plugin.getLogger().warning("Broken data.json was moved to " + backup.getFileName());
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not back up data.json: " + ex.getMessage());
        }
    }

    public ShopData data() {
        return data;
    }

    public Settings settings() {
        return data.settings;
    }

    /**
     * Called on the main thread after a mutation.
     */
    public void markDirty() {
        dirty = true;
        if (saveScheduled || stopping || !plugin.isEnabled()) {
            return;
        }
        saveScheduled = true;
        long expectedGeneration = generation;
        saveTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            saveScheduled = false;
            if (!dirty || stopping || expectedGeneration != generation) {
                return;
            }
            dirty = false;
            String snapshot = gson.toJson(data);
            if (!plugin.isEnabled()) {
                writeSync(snapshot, expectedGeneration);
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (stopping || expectedGeneration != generation) {
                    return;
                }
                writeSync(snapshot, expectedGeneration);
            });
        }, 20L);
    }

    /**
     * Writes the latest state synchronously. Called on shutdown and before a reload.
     */
    public void flushSync() {
        stopping = true;
        dirty = false;
        generation++;
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        saveScheduled = false;
        writeSync(gson.toJson(data), generation);
    }

    private void writeSync(String json, long expectedGeneration) {
        synchronized (writeLock) {
            // A reload or shutdown invalidated this snapshot while it was
            // queued; never write stale data over newer data.
            if (expectedGeneration != generation) {
                return;
            }
            try {
                Files.createDirectories(plugin.getDataFolder().toPath());
                Files.writeString(tempFile, json, StandardCharsets.UTF_8);
                try {
                    Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                plugin.getLogger().severe("Could not save data.json: " + ex.getMessage());
            }
        }
    }
}
