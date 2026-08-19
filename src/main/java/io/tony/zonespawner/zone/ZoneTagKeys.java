package io.tony.zonespawner.zone;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The PersistentDataContainer keys this plugin stamps onto every mob it
 * spawns: which zone owns it, and the exact spot it was spawned at (used
 * to enforce each rule's leash radius).
 */
public record ZoneTagKeys(NamespacedKey zone, NamespacedKey spawnX, NamespacedKey spawnY, NamespacedKey spawnZ) {

    public static ZoneTagKeys create(JavaPlugin plugin) {
        return new ZoneTagKeys(
                new NamespacedKey(plugin, "zone"),
                new NamespacedKey(plugin, "spawn-x"),
                new NamespacedKey(plugin, "spawn-y"),
                new NamespacedKey(plugin, "spawn-z")
        );
    }
}
