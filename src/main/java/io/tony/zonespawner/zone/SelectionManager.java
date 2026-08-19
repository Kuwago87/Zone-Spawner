package io.tony.zonespawner.zone;

import org.bukkit.Location;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks the in-progress /zpos1, /zpos2 and /zpos3 selection each player is
 * making, ahead of running /zonecreate. Purely in-memory - selections don't
 * need to survive a restart.
 *
 * pos1/pos2 mark the two corners of the zone's cuboid, same as always.
 * pos3 is optional and only used for its Y coordinate: stand at the real
 * ground level (even if pos1/pos2 were marked somewhere else, like up on a
 * roof) and run /zpos3 there to tell /zonecreate where the floor actually is.
 */
public final class SelectionManager {

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final Map<UUID, Location> pos3 = new HashMap<>();

    public void setPos1(UUID player, Location location) {
        pos1.put(player, location);
    }

    public void setPos2(UUID player, Location location) {
        pos2.put(player, location);
    }

    public void setPos3(UUID player, Location location) {
        pos3.put(player, location);
    }

    public Location getPos1(UUID player) {
        return pos1.get(player);
    }

    public Location getPos2(UUID player) {
        return pos2.get(player);
    }

    public Location getPos3(UUID player) {
        return pos3.get(player);
    }

    public void clear(UUID player) {
        pos1.remove(player);
        pos2.remove(player);
        pos3.remove(player);
    }
}
