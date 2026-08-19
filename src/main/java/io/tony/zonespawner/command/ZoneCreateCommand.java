package io.tony.zonespawner.command;

import io.tony.zonespawner.zone.SelectionManager;
import io.tony.zonespawner.zone.Zone;
import io.tony.zonespawner.zone.ZoneManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class ZoneCreateCommand implements CommandExecutor {

    private final SelectionManager selections;
    private final ZoneManager zones;

    public ZoneCreateCommand(SelectionManager selections, ZoneManager zones) {
        this.selections = selections;
        this.zones = zones;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can create zones.", NamedTextColor.RED));
            return true;
        }
        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /zonecreate <name>", NamedTextColor.RED));
            return true;
        }

        String name = args[0];
        if (zones.zoneExists(name)) {
            player.sendMessage(Component.text("A zone named '" + name + "' already exists.", NamedTextColor.RED));
            return true;
        }

        Location pos1 = selections.getPos1(player.getUniqueId());
        Location pos2 = selections.getPos2(player.getUniqueId());
        if (pos1 == null || pos2 == null) {
            player.sendMessage(Component.text(
                    "Set both corners first with /zpos1 and /zpos2.", NamedTextColor.RED));
            return true;
        }
        if (pos1.getWorld() == null || pos2.getWorld() == null
                || !pos1.getWorld().equals(pos2.getWorld())) {
            player.sendMessage(Component.text(
                    "Both corners must be in the same world.", NamedTextColor.RED));
            return true;
        }

        Location pos3 = selections.getPos3(player.getUniqueId());
        if (pos3 != null && !pos3.getWorld().equals(pos1.getWorld())) {
            player.sendMessage(Component.text(
                    "Position 3 must be in the same world as the corners.", NamedTextColor.RED));
            return true;
        }
        Integer floorOverride = pos3 != null ? pos3.getBlockY() : null;

        try {
            Zone zone = zones.createZone(name, pos1.getWorld(), pos1, pos2, floorOverride);
            selections.clear(player.getUniqueId());
            String floorMsg = floorOverride != null
                    ? " Floor set to Y=" + floorOverride + " from /zpos3."
                    : "";
            player.sendMessage(Component.text(
                    "Created zone '" + zone.getName() + "' (" + zone.getBlockVolume() + " blocks)."
                            + floorMsg + " Spawns are capped to "
                            + zone.getMaxHeightAboveFloor() + " blocks above the floor by default - "
                            + "use /zone maxheight to change that. "
                            + "Use /zone set " + zone.getName() + " <animal> <amount> <respawnRate> to configure it.",
                    NamedTextColor.GREEN
            ));
        } catch (IllegalArgumentException ex) {
            player.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
        }
        return true;
    }
}
