package io.tony.zonespawner.task;

import io.tony.zonespawner.zone.Zone;
import io.tony.zonespawner.zone.ZoneManager;
import io.tony.zonespawner.zone.ZoneRule;
import io.tony.zonespawner.zone.ZoneTagKeys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;

/**
 * Runs periodically (see ZoneSpawnerPlugin#onEnable). For every zone and
 * every species rule configured on it, checks whether the zone is under
 * its cap and, if the rule's respawn window has elapsed, spawns one more
 * of that species inside the zone.
 *
 * Population is counted by ownership, not location: every mob this plugin
 * spawns is tagged (via PersistentDataContainer) with the name of the zone
 * that owns it, plus the exact spot it spawned at. The cap tracks how many
 * living, tagged mobs exist for that zone anywhere in the world - not how
 * many are currently standing inside the zone's borders - so a cow that
 * wanders off still counts against the cap. Untagged wild animals are
 * never counted either way.
 *
 * Each rule can also set a leash radius: on the same pass, any tagged mob
 * that has drifted further than its rule's radius from its own spawn point
 * gets pulled back to the edge of that radius, so managed animals stay
 * roughly where they were placed instead of wandering indefinitely.
 *
 * One practical limit: entities in unloaded chunks aren't visible to the
 * server at all, so a mob that wanders far enough to unload its chunk will
 * briefly stop counting/leashing until that chunk loads again.
 */
public final class ZoneSpawnTask implements Runnable {

    /** Don't spam the console: only log a given zone+species placement failure this often. */
    private static final long PLACEMENT_WARNING_COOLDOWN_MS = 5 * 60 * 1000L;

    private final JavaPlugin plugin;
    private final ZoneManager zoneManager;
    private final ZoneTagKeys tagKeys;
    private final Random random = new Random();
    private final Map<String, Long> lastPlacementWarning = new HashMap<>();

    public ZoneSpawnTask(JavaPlugin plugin, ZoneManager zoneManager, ZoneTagKeys tagKeys) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
        this.tagKeys = tagKeys;
    }

    @Override
    public void run() {
        Map<World, List<Zone>> zonesByWorld = new HashMap<>();
        for (Zone zone : zoneManager.getZones()) {
            if (zone.getRules().isEmpty()) {
                continue;
            }
            World world = Bukkit.getWorld(zone.getWorldName());
            if (world == null) {
                continue;
            }
            zonesByWorld.computeIfAbsent(world, w -> new ArrayList<>()).add(zone);
        }

        for (Map.Entry<World, List<Zone>> worldEntry : zonesByWorld.entrySet()) {
            World world = worldEntry.getKey();
            // One pass over this world's loaded entities: count each zone's managed
            // population by tag, and enforce leash radii along the way.
            Map<String, Map<EntityType, Integer>> counts = scanWorld(world);

            for (Zone zone : worldEntry.getValue()) {
                Map<EntityType, Integer> zoneCounts = counts.getOrDefault(zone.getName(), Map.of());
                for (Map.Entry<EntityType, ZoneRule> ruleEntry : zone.getRules().entrySet()) {
                    int currentCount = zoneCounts.getOrDefault(ruleEntry.getKey(), 0);
                    processRule(zone, world, ruleEntry.getKey(), ruleEntry.getValue(), currentCount);
                }
            }
        }
    }

    private Map<String, Map<EntityType, Integer>> scanWorld(World world) {
        Map<String, Map<EntityType, Integer>> counts = new HashMap<>();
        for (Entity entity : world.getEntities()) {
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            String zoneName = pdc.get(tagKeys.zone(), PersistentDataType.STRING);
            if (zoneName == null) {
                continue;
            }
            counts.computeIfAbsent(zoneName, k -> new EnumMap<>(EntityType.class))
                    .merge(entity.getType(), 1, Integer::sum);

            Zone zone = zoneManager.getZone(zoneName);
            if (zone == null) {
                continue;
            }
            ZoneRule rule = zone.getRule(entity.getType());
            if (rule != null && rule.getLeashRadius() > 0) {
                enforceLeash(entity, pdc, rule.getLeashRadius(), zone);
            }
        }
        return counts;
    }

    private void enforceLeash(Entity entity, PersistentDataContainer pdc, double radius, Zone zone) {
        Double spawnX = pdc.get(tagKeys.spawnX(), PersistentDataType.DOUBLE);
        Double spawnY = pdc.get(tagKeys.spawnY(), PersistentDataType.DOUBLE);
        Double spawnZ = pdc.get(tagKeys.spawnZ(), PersistentDataType.DOUBLE);
        if (spawnX == null || spawnY == null || spawnZ == null) {
            // Spawned before the leash feature existed (or by something else) - nothing to leash to.
            return;
        }

        Location current = entity.getLocation();
        double dx = current.getX() - spawnX;
        double dz = current.getZ() - spawnZ;
        double horizontalDistSq = dx * dx + dz * dz;
        if (horizontalDistSq <= radius * radius) {
            return;
        }

        double dist = Math.sqrt(horizontalDistSq);
        double ratio = radius / dist;
        double newX = spawnX + dx * ratio;
        double newZ = spawnZ + dz * ratio;

        World world = entity.getWorld();
        int blockX = (int) Math.floor(newX);
        int blockZ = (int) Math.floor(newZ);
        // Bounded to the zone's own floor/height limit - NOT World#getHighestBlockYAt, which
        // would return the world's topmost block in that column (e.g. a roof above the zone).
        Integer groundY = zone.findGroundYNear(world, blockX, blockZ);
        if (groundY == null) {
            return;
        }

        Location target = new Location(world, newX, groundY + 1, newZ, current.getYaw(), current.getPitch());
        entity.teleport(target);
        entity.setVelocity(new Vector(0, 0, 0));
        entity.setFallDistance(0f);
    }

    private void processRule(Zone zone, World world, EntityType type, ZoneRule rule, int currentCount) {
        if (!zone.tryConsumeSpawnWindow(type, rule.getRespawnRateSeconds())) {
            return;
        }
        if (rule.getAmount() <= 0) {
            return;
        }
        if (currentCount >= rule.getAmount()) {
            return;
        }

        Location spawnLoc = zone.findRandomSurfaceLocation(world, random);
        if (spawnLoc == null) {
            warnNoPlacement(zone, type);
            return;
        }

        try {
            Entity entity = world.spawnEntity(spawnLoc, type);
            if (!entity.isValid()) {
                // Another plugin cancelled the CreatureSpawnEvent (e.g. a region
                // protection or peaceful-territory plugin). Nothing actually
                // appeared in the world, so don't tag/count a dead reference -
                // that would make this zone think it's under-populated forever
                // while silently burning its respawn window every pass.
                warnBlockedSpawn(zone, type);
                return;
            }
            PersistentDataContainer pdc = entity.getPersistentDataContainer();
            pdc.set(tagKeys.zone(), PersistentDataType.STRING, zone.getName());
            pdc.set(tagKeys.spawnX(), PersistentDataType.DOUBLE, spawnLoc.getX());
            pdc.set(tagKeys.spawnY(), PersistentDataType.DOUBLE, spawnLoc.getY());
            pdc.set(tagKeys.spawnZ(), PersistentDataType.DOUBLE, spawnLoc.getZ());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not spawn " + type + " in zone '" + zone.getName() + "': " + ex.getMessage());
        }
    }

    private void warnNoPlacement(Zone zone, EntityType type) {
        String key = zone.getName() + ":" + type.name();
        long now = System.currentTimeMillis();
        Long last = lastPlacementWarning.get(key);
        if (last != null && now - last < PLACEMENT_WARNING_COOLDOWN_MS) {
            return;
        }
        lastPlacementWarning.put(key, now);
        plugin.getLogger().warning("Zone '" + zone.getName() + "' couldn't find a spot to spawn "
                + type.name() + " - no solid ground with 2 clear blocks above was found in or below "
                + "the zone's selection, or its chunks aren't loaded. If this keeps happening, check "
                + "the zone's corners with /zone info " + zone.getName() + ".");
    }

    private void warnBlockedSpawn(Zone zone, EntityType type) {
        String key = "blocked:" + zone.getName() + ":" + type.name();
        long now = System.currentTimeMillis();
        Long last = lastPlacementWarning.get(key);
        if (last != null && now - last < PLACEMENT_WARNING_COOLDOWN_MS) {
            return;
        }
        lastPlacementWarning.put(key, now);
        plugin.getLogger().warning("Zone '" + zone.getName() + "' tried to spawn " + type.name()
                + " but another plugin cancelled it. If this keeps happening, check for a region "
                + "protection, peaceful-territory, or mob-limit plugin covering this zone and make "
                + "sure it allows CUSTOM-reason spawns from " + plugin.getName() + ".");
    }
}
