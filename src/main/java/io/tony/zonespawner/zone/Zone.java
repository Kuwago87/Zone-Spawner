package io.tony.zonespawner.zone;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.EntityType;

/**
 * A named cuboid region in a specific world, together with the set of
 * per-species population rules that apply inside it.
 */
public final class Zone {

    private static final int MAX_PLACEMENT_ATTEMPTS = 12;

    private final String name;
    private final String worldName;

    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    private final Map<EntityType, ZoneRule> rules = new LinkedHashMap<>();

    // Not persisted: last time (epoch millis) a spawn attempt was made per species.
    private final Map<EntityType, Long> lastSpawnAttempt = new ConcurrentHashMap<>();

    /** Default cap for newly-created zones - keeps them safe out of the box. 0 = unlimited. */
    public static final int DEFAULT_MAX_HEIGHT_ABOVE_FLOOR = 10;

    // 0 = unlimited (search the whole zone, minY..maxY). When set, the ground
    // search only looks up to floor() + this value, so animals can't spawn on
    // top of tall trees, mountains, or floating builds that happen to fall
    // inside the zone's footprint but well above its actual floor. Defaults
    // to DEFAULT_MAX_HEIGHT_ABOVE_FLOOR for new zones; zones loaded from an
    // older zones.yml without this field explicitly default to 0 instead, so
    // existing setups don't change behavior without the owner asking for it.
    private int maxHeightAboveFloor = DEFAULT_MAX_HEIGHT_ABOVE_FLOOR;

    // null = not set, fall back to minY (the zone's own lower corner) as the
    // floor reference. Set explicitly via /zone floor when the zone's corners
    // don't actually sit at the real ground - e.g. both corners were marked
    // from up on a roof, so minY reflects the ceiling rather than the floor.
    private Integer floorOverride;

    public Zone(String name, String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.name = name;
        this.worldName = worldName;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public long getBlockVolume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getName().equals(worldName)) {
            return false;
        }
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        return x >= minX && x <= maxX + 1
                && y >= minY && y <= maxY + 1
                && z >= minZ && z <= maxZ + 1;
    }

    public Map<EntityType, ZoneRule> getRules() {
        return rules;
    }

    public ZoneRule getRule(EntityType type) {
        return rules.get(type);
    }

    public void setRule(EntityType type, ZoneRule rule) {
        rules.put(type, rule);
    }

    public ZoneRule removeRule(EntityType type) {
        lastSpawnAttempt.remove(type);
        return rules.remove(type);
    }

    public int getMaxHeightAboveFloor() {
        return maxHeightAboveFloor;
    }

    public void setMaxHeightAboveFloor(int maxHeightAboveFloor) {
        this.maxHeightAboveFloor = maxHeightAboveFloor;
    }

    public Integer getFloorOverride() {
        return floorOverride;
    }

    public void setFloorOverride(Integer floorOverride) {
        this.floorOverride = floorOverride;
    }

    /** The Y this zone treats as its floor: the explicit override if set, otherwise minY. */
    public int getEffectiveFloor() {
        return floorOverride != null ? floorOverride : minY;
    }

    /**
     * Returns true if enough time has passed (per the rule's respawn rate)
     * since the last spawn attempt for this species, and records the attempt.
     */
    public boolean tryConsumeSpawnWindow(EntityType type, int respawnRateSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = respawnRateSeconds * 1000L;
        Long last = lastSpawnAttempt.get(type);
        if (last != null && now - last < windowMillis) {
            return false;
        }
        lastSpawnAttempt.put(type, now);
        return true;
    }

    /**
     * How far below the zone's floor to keep searching for solid ground.
     * Zones are very often marked out at a single height (you stand at
     * roughly the same Y for both /zpos1 and /zpos2), which makes the floor
     * reference the *air* block you were standing in rather than the solid
     * ground beneath your feet - so we need to look a bit lower to actually
     * find that ground.
     */
    private static final int GROUND_SEARCH_BUFFER = 16;

    /**
     * Attempts to find a safe surface location inside this zone to spawn a
     * ground-dwelling mob. Returns null if no valid spot was found after a
     * handful of random tries (e.g. the zone is mostly air/liquid/unloaded).
     */
    public Location findRandomSurfaceLocation(World world, Random random) {
        int searchFloor = searchFloor(world);
        int searchCeiling = searchCeiling();

        for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS; attempt++) {
            int x = minX + random.nextInt(maxX - minX + 1);
            int z = minZ + random.nextInt(maxZ - minZ + 1);

            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }

            Integer groundY = findGroundY(world, x, z, searchCeiling, searchFloor);
            if (groundY != null) {
                return new Location(world, x + 0.5, groundY + 1, z + 0.5);
            }
        }
        return null;
    }

    /**
     * Finds safe ground at a specific column, respecting this zone's floor
     * and max spawn height - used to correct a mob's position when leashing
     * it back in. Deliberately NOT the same as World#getHighestBlockYAt,
     * which returns the world's topmost block in that column regardless of
     * this zone's bounds - under a roofed structure that's the roof, not the
     * zone's actual floor. Returns null if no valid ground was found within
     * the zone's height range (e.g. the column is unloaded or has no floor
     * at all in range).
     */
    public Integer findGroundYNear(World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return null;
        }
        return findGroundY(world, x, z, searchCeiling(), searchFloor(world));
    }

    private int searchFloor(World world) {
        return Math.max(getEffectiveFloor() - GROUND_SEARCH_BUFFER, world.getMinHeight());
    }

    private int searchCeiling() {
        int floor = getEffectiveFloor();
        return maxHeightAboveFloor > 0 ? Math.min(maxY, floor + maxHeightAboveFloor) : maxY;
    }

    private Integer findGroundY(World world, int x, int z, int searchCeiling, int searchFloor) {
        for (int y = searchCeiling; y >= searchFloor; y--) {
            Block ground = world.getBlockAt(x, y, z);
            if (!isValidGround(ground.getType())) {
                continue;
            }
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);
            if (feet.isPassable() && head.isPassable()) {
                return y;
            }
        }
        return null;
    }

    /**
     * Solid but not real terrain - leaves are the big one, since Bukkit
     * reports them as solid, which used to let animals spawn up in tree
     * canopies instead of on the ground below them.
     */
    private boolean isValidGround(Material material) {
        if (!material.isSolid()) {
            return false;
        }
        return !Tag.LEAVES.isTagged(material);
    }
}
