package io.tony.zonespawner.zone;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Owns the set of configured zones and handles loading/saving them to
 * zones.yml in the plugin's data folder.
 */
public final class ZoneManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Zone> zones = new LinkedHashMap<>();

    public ZoneManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "zones.yml");
    }

    public Zone createZone(String name, World world, Location corner1, Location corner2) {
        return createZone(name, world, corner1, corner2, null);
    }

    /**
     * @param floorOverride optional - the Y to treat as this zone's floor
     *                       (from /zpos3), independent of the corners' own
     *                       Y values. Pass null to just use the lower corner.
     */
    public Zone createZone(String name, World world, Location corner1, Location corner2, Integer floorOverride) {
        String key = key(name);
        if (zones.containsKey(key)) {
            throw new IllegalArgumentException("A zone named '" + name + "' already exists.");
        }
        Zone zone = new Zone(
                name,
                world.getName(),
                corner1.getBlockX(), corner1.getBlockY(), corner1.getBlockZ(),
                corner2.getBlockX(), corner2.getBlockY(), corner2.getBlockZ()
        );
        zone.setFloorOverride(floorOverride);
        zones.put(key, zone);
        save();
        return zone;
    }

    public boolean deleteZone(String name) {
        boolean removed = zones.remove(key(name)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public Zone getZone(String name) {
        return zones.get(key(name));
    }

    public boolean zoneExists(String name) {
        return zones.containsKey(key(name));
    }

    public Collection<Zone> getZones() {
        return zones.values();
    }

    private String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public void load() {
        zones.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection zonesSection = yaml.getConfigurationSection("zones");
        if (zonesSection == null) {
            return;
        }
        for (String name : zonesSection.getKeys(false)) {
            ConfigurationSection zs = zonesSection.getConfigurationSection(name);
            if (zs == null) {
                continue;
            }
            try {
                String world = zs.getString("world");
                Zone zone = new Zone(
                        name,
                        world,
                        zs.getInt("minX"), zs.getInt("minY"), zs.getInt("minZ"),
                        zs.getInt("maxX"), zs.getInt("maxY"), zs.getInt("maxZ")
                );
                zone.setMaxHeightAboveFloor(zs.getInt("maxHeightAboveFloor", 0));
                if (zs.isSet("floorOverride")) {
                    zone.setFloorOverride(zs.getInt("floorOverride"));
                }
                ConfigurationSection rulesSection = zs.getConfigurationSection("rules");
                if (rulesSection != null) {
                    for (String typeName : rulesSection.getKeys(false)) {
                        ConfigurationSection rs = rulesSection.getConfigurationSection(typeName);
                        if (rs == null) {
                            continue;
                        }
                        try {
                            EntityType type = EntityType.valueOf(typeName);
                            int amount = rs.getInt("amount");
                            int respawnRate = rs.getInt("respawnRate");
                            // Absent for zones/rules saved before the leash feature existed -
                            // default to 0 (unlimited wandering) so old behavior doesn't change on load.
                            double leashRadius = rs.getDouble("leashRadius", 0);
                            zone.setRule(type, new ZoneRule(amount, respawnRate, leashRadius));
                        } catch (IllegalArgumentException ex) {
                            plugin.getLogger().warning("Skipping unknown entity type '" + typeName
                                    + "' in zone '" + name + "'.");
                        }
                    }
                }
                zones.put(key(name), zone);
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to load zone '" + name + "'", ex);
            }
        }
        plugin.getLogger().info("Loaded " + zones.size() + " zone(s).");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection zonesSection = yaml.createSection("zones");
        for (Zone zone : zones.values()) {
            ConfigurationSection zs = zonesSection.createSection(zone.getName());
            zs.set("world", zone.getWorldName());
            zs.set("minX", zone.getMinX());
            zs.set("minY", zone.getMinY());
            zs.set("minZ", zone.getMinZ());
            zs.set("maxX", zone.getMaxX());
            zs.set("maxY", zone.getMaxY());
            zs.set("maxZ", zone.getMaxZ());
            zs.set("maxHeightAboveFloor", zone.getMaxHeightAboveFloor());
            if (zone.getFloorOverride() != null) {
                zs.set("floorOverride", zone.getFloorOverride());
            }
            ConfigurationSection rulesSection = zs.createSection("rules");
            for (Map.Entry<EntityType, ZoneRule> entry : zone.getRules().entrySet()) {
                ConfigurationSection rs = rulesSection.createSection(entry.getKey().name());
                rs.set("amount", entry.getValue().getAmount());
                rs.set("respawnRate", entry.getValue().getRespawnRateSeconds());
                rs.set("leashRadius", entry.getValue().getLeashRadius());
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save zones.yml", ex);
        }
    }

    public List<Zone> getZonesContaining(Location location) {
        List<Zone> result = new ArrayList<>();
        for (Zone zone : zones.values()) {
            if (zone.contains(location)) {
                result.add(zone);
            }
        }
        return result;
    }
}
