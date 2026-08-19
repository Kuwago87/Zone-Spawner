package io.tony.zonespawner.command;

import io.tony.zonespawner.zone.Zone;
import io.tony.zonespawner.zone.ZoneManager;
import io.tony.zonespawner.zone.ZoneRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Handles /zone set|remove|list|info|delete.
 */
public final class ZoneCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("set", "remove", "list", "info", "delete", "maxheight", "floor");

    // A curated shortlist of common ground animals/passive mobs, just to make
    // tab completion pleasant. Any valid EntityType name can still be typed.
    private static final List<String> COMMON_ANIMALS = List.of(
            "COW", "PIG", "SHEEP", "CHICKEN", "RABBIT", "HORSE", "DONKEY", "MULE",
            "LLAMA", "WOLF", "CAT", "FOX", "PANDA", "GOAT", "PARROT", "TURTLE",
            "OCELOT", "BEE", "FROG", "AXOLOTL", "SNIFFER", "ARMADILLO", "CAMEL"
    );

    /** Used when /zone set omits the leash radius argument. */
    private static final double DEFAULT_LEASH_RADIUS = 10.0;

    private final ZoneManager zones;

    public ZoneCommand(ZoneManager zones) {
        this.zones = zones;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> handleSet(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            case "info" -> handleInfo(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "maxheight" -> handleMaxHeight(sender, args);
            case "floor" -> handleFloor(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (args.length != 5 && args.length != 6) {
            sender.sendMessage(Component.text(
                    "Usage: /zone set <zoneName> <animal> <amount> <respawnRateSeconds> [leashRadius]",
                    NamedTextColor.RED));
            return;
        }
        Zone zone = requireZone(sender, args[1]);
        if (zone == null) {
            return;
        }
        EntityType type = parseEntityType(sender, args[2]);
        if (type == null) {
            return;
        }
        int amount;
        int respawnRate;
        double leashRadius;
        try {
            amount = Integer.parseInt(args[3]);
            respawnRate = Integer.parseInt(args[4]);
            leashRadius = args.length == 6 ? Double.parseDouble(args[5]) : DEFAULT_LEASH_RADIUS;
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text(
                    "Amount and respawn rate must be whole numbers, and leash radius a number.",
                    NamedTextColor.RED));
            return;
        }
        if (amount < 0 || respawnRate < 1 || leashRadius < 0) {
            sender.sendMessage(Component.text(
                    "Amount must be >= 0, respawn rate >= 1 second, and leash radius >= 0.", NamedTextColor.RED));
            return;
        }

        zone.setRule(type, new ZoneRule(amount, respawnRate, leashRadius));
        zones.save();
        String leashMsg = leashRadius > 0
                ? "leashed within " + leashRadius + " blocks of where each one spawns"
                : "free to wander (no leash)";
        sender.sendMessage(Component.text(
                "Zone '" + zone.getName() + "': " + type.name() + " capped at " + amount
                        + ", checking every " + respawnRate + "s, " + leashMsg + ".",
                NamedTextColor.GREEN
        ));
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(Component.text("Usage: /zone remove <zoneName> <animal>", NamedTextColor.RED));
            return;
        }
        Zone zone = requireZone(sender, args[1]);
        if (zone == null) {
            return;
        }
        EntityType type = parseEntityType(sender, args[2]);
        if (type == null) {
            return;
        }
        if (zone.removeRule(type) == null) {
            sender.sendMessage(Component.text(
                    "Zone '" + zone.getName() + "' has no rule for " + type.name() + ".", NamedTextColor.RED));
            return;
        }
        zones.save();
        sender.sendMessage(Component.text(
                "Removed " + type.name() + " rule from zone '" + zone.getName() + "'.", NamedTextColor.GREEN));
    }

    private void handleList(CommandSender sender) {
        if (zones.getZones().isEmpty()) {
            sender.sendMessage(Component.text("No zones have been created yet.", NamedTextColor.YELLOW));
            return;
        }
        sender.sendMessage(Component.text("Zones:", NamedTextColor.GOLD));
        for (Zone zone : zones.getZones()) {
            sender.sendMessage(Component.text(
                    " - " + zone.getName() + " (" + zone.getWorldName() + ", "
                            + zone.getRules().size() + " rule(s))",
                    NamedTextColor.GRAY
            ));
        }
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /zone info <zoneName>", NamedTextColor.RED));
            return;
        }
        Zone zone = requireZone(sender, args[1]);
        if (zone == null) {
            return;
        }
        sender.sendMessage(Component.text("Zone '" + zone.getName() + "'", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(" World: " + zone.getWorldName(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                " Corners: (" + zone.getMinX() + ", " + zone.getMinY() + ", " + zone.getMinZ() + ") to ("
                        + zone.getMaxX() + ", " + zone.getMaxY() + ", " + zone.getMaxZ() + ")",
                NamedTextColor.GRAY
        ));
        sender.sendMessage(Component.text(
                " Floor: Y=" + zone.getEffectiveFloor()
                        + (zone.getFloorOverride() != null ? " (manually set)" : " (from lower corner)"),
                NamedTextColor.GRAY
        ));
        sender.sendMessage(Component.text(
                " Max spawn height: " + (zone.getMaxHeightAboveFloor() > 0
                        ? zone.getMaxHeightAboveFloor() + " blocks above floor"
                        : "unlimited"),
                NamedTextColor.GRAY
        ));
        if (zone.getRules().isEmpty()) {
            sender.sendMessage(Component.text(" No animal rules configured.", NamedTextColor.GRAY));
            return;
        }
        sender.sendMessage(Component.text(" Rules:", NamedTextColor.GRAY));
        for (Map.Entry<EntityType, ZoneRule> entry : zone.getRules().entrySet()) {
            ZoneRule rule = entry.getValue();
            String leashText = rule.getLeashRadius() > 0
                    ? "leash " + rule.getLeashRadius() + " blocks"
                    : "no leash";
            sender.sendMessage(Component.text(
                    "  - " + entry.getKey().name() + ": cap " + rule.getAmount()
                            + ", every " + rule.getRespawnRateSeconds() + "s, " + leashText,
                    NamedTextColor.GRAY
            ));
        }
    }

    private void handleMaxHeight(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(Component.text(
                    "Usage: /zone maxheight <zoneName> <blocksAboveFloor|0 for unlimited>", NamedTextColor.RED));
            return;
        }
        Zone zone = requireZone(sender, args[1]);
        if (zone == null) {
            return;
        }
        int blocks;
        try {
            blocks = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Component.text("Height must be a whole number.", NamedTextColor.RED));
            return;
        }
        if (blocks < 0) {
            sender.sendMessage(Component.text("Height must be >= 0 (0 = unlimited).", NamedTextColor.RED));
            return;
        }

        zone.setMaxHeightAboveFloor(blocks);
        zones.save();
        String msg = blocks > 0
                ? "Animals in zone '" + zone.getName() + "' will now only spawn within " + blocks
                        + " blocks above the zone's floor."
                : "Zone '" + zone.getName() + "' now has no spawn height limit.";
        sender.sendMessage(Component.text(msg, NamedTextColor.GREEN));
    }

    private void handleFloor(CommandSender sender, String[] args) {
        if (args.length != 2 && args.length != 3) {
            sender.sendMessage(Component.text(
                    "Usage: /zone floor <zoneName> [y|reset] - omit the value to use your current Y",
                    NamedTextColor.RED));
            return;
        }
        Zone zone = requireZone(sender, args[1]);
        if (zone == null) {
            return;
        }

        if (args.length == 3 && args[2].equalsIgnoreCase("reset")) {
            zone.setFloorOverride(null);
            zones.save();
            sender.sendMessage(Component.text(
                    "Zone '" + zone.getName() + "' floor reset to its lower corner ("
                            + zone.getMinY() + ").",
                    NamedTextColor.GREEN
            ));
            return;
        }

        int floorY;
        if (args.length == 3) {
            try {
                floorY = Integer.parseInt(args[2]);
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text("Y must be a whole number, or 'reset'.", NamedTextColor.RED));
                return;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text(
                        "Console must specify a Y value: /zone floor <zoneName> <y>", NamedTextColor.RED));
                return;
            }
            floorY = player.getLocation().getBlockY();
        }

        zone.setFloorOverride(floorY);
        zones.save();
        sender.sendMessage(Component.text(
                "Zone '" + zone.getName() + "' floor set to Y=" + floorY
                        + ". Pair this with /zone maxheight so the search doesn't reach back up to a roof.",
                NamedTextColor.GREEN
        ));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /zone delete <zoneName>", NamedTextColor.RED));
            return;
        }
        if (zones.deleteZone(args[1])) {
            sender.sendMessage(Component.text("Deleted zone '" + args[1] + "'.", NamedTextColor.GREEN));
        } else {
            sender.sendMessage(Component.text("No zone named '" + args[1] + "' exists.", NamedTextColor.RED));
        }
    }

    private Zone requireZone(CommandSender sender, String name) {
        Zone zone = zones.getZone(name);
        if (zone == null) {
            sender.sendMessage(Component.text("No zone named '" + name + "' exists.", NamedTextColor.RED));
        }
        return zone;
    }

    private EntityType parseEntityType(CommandSender sender, String input) {
        try {
            return EntityType.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            sender.sendMessage(Component.text("Unknown entity type '" + input + "'.", NamedTextColor.RED));
            return null;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("Usage:", NamedTextColor.GOLD));
        sender.sendMessage(Component.text(
                " /zone set <zoneName> <animal> <amount> <respawnRateSeconds> [leashRadius=10]",
                NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /zone remove <zoneName> <animal>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /zone list", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /zone info <zoneName>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(" /zone delete <zoneName>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                " /zone maxheight <zoneName> <blocksAboveFloor|0>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text(
                " /zone floor <zoneName> [y|reset]", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("set") || sub.equals("remove") || sub.equals("info")
                || sub.equals("delete") || sub.equals("maxheight") || sub.equals("floor"))) {
            List<String> names = zones.getZones().stream().map(Zone::getName).collect(Collectors.toList());
            return filter(names, args[1]);
        }
        if (args.length == 3 && (sub.equals("set") || sub.equals("remove"))) {
            return filter(COMMON_ANIMALS, args[2]);
        }
        if (args.length == 3 && sub.equals("floor")) {
            return filter(List.of("reset"), args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String current) {
        String lower = current.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
