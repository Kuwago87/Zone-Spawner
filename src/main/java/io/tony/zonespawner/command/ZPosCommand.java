package io.tony.zonespawner.command;

import io.tony.zonespawner.zone.SelectionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /zpos1, /zpos2 and /zpos3 - which slot it fills is determined by
 * the command label that was actually typed.
 *
 * /zpos1 and /zpos2 mark the two corners of the zone. /zpos3 is optional
 * and only its Y coordinate matters: stand at the real ground level (even
 * if /zpos1 and /zpos2 were marked somewhere else, like up on a roof) and
 * run /zpos3 there so /zonecreate knows where the floor actually is.
 */
public final class ZPosCommand implements CommandExecutor {

    private final SelectionManager selections;

    public ZPosCommand(SelectionManager selections) {
        this.selections = selections;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can select zone points.", NamedTextColor.RED));
            return true;
        }

        Location loc = player.getLocation();
        String name = command.getName().toLowerCase();
        String description;
        switch (name) {
            case "zpos1" -> {
                selections.setPos1(player.getUniqueId(), loc);
                description = "Position 1";
            }
            case "zpos2" -> {
                selections.setPos2(player.getUniqueId(), loc);
                description = "Position 2";
            }
            case "zpos3" -> {
                selections.setPos3(player.getUniqueId(), loc);
                description = "Position 3 (floor height)";
            }
            default -> {
                return true;
            }
        }

        player.sendMessage(Component.text(
                description + " set to ("
                        + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ").",
                NamedTextColor.GREEN
        ));
        return true;
    }
}
