# ZoneSpawner (Animal/ Mob Control)

A Paper plugin that lets you draw rectangular zones and auto-regulate a
population cap per mob species inside each one - the classic "keep a farm
stocked with exactly 20 cows, no more, no less" tool, but not limited to
passive animals: any valid Minecraft 'EntityType' can be assigned to a zone.

## Requirements

- Paper (or a Paper fork) for Minecraft / Paper API **26.2**

## Quick start

1. Stand at one corner of the area you want to manage and run '/zpos1'.
2. Stand at the opposite corner and run '/zpos2'.
3. (Optional) Stand at actual ground level and run '/zpos3' if '/zpos1'/
   '/zpos2' were marked somewhere else (e.g. up on scaffolding) - this tells
   the zone where its floor really is, independent of the corner heights.
4. '/zonecreate <name>' - creates the zone from your current selection.
5. '/zone set <name> <entityType> <amount> <respawnRateSeconds> [leashRadius]'
   - configure a population rule, e.g.:

   '''
   /zone set farm COW 20 30
   /zone set farm SHEEP 10 30 8
   '''

   Every 'respawnRateSeconds', the zone checks each configured species: if
   the count is under 'amount', it spawns one more (on solid ground, with 2
   clear blocks above, within the zone's floor/height limits). 'leashRadius'
   (default 10 blocks) pulls any of *this zone's own* mobs back if they
   wander further than that from where they spawned - 0 means no leash.

## Commands

| Command | Description |
|---|---|
| '/zpos1' | Mark corner 1 of your zone selection at your current position. |
| '/zpos2' | Mark corner 2 of your zone selection at your current position. |
| '/zpos3' | Optional - mark your current position's Y as the zone's floor height. |
| '/zonecreate <name>' | Create a zone from your current '/zpos1'/'/zpos2' (and optional '/zpos3') selection. |
| '/zone set <name> <entityType> <amount> <respawnRate> [leashRadius]' | Add/update a population rule for that species in the zone. |
| '/zone remove <name> <entityType>' | Remove a species' rule from the zone. |
| '/zone list' | List all zones. |
| '/zone info <name>' | Show a zone's world, corners, floor, height limit, and rules. |
| '/zone delete <name>' | Delete a zone entirely. |
| '/zone maxheight <name> <blocksAboveFloor\|0>' | Cap how high above the floor spawns are allowed (0 = unlimited). |
| '/zone floor <name> [y\|reset]' | Manually set (or reset) the zone's floor Y, independent of its corners. |

'<entityType>' accepts any [Bukkit 'EntityType'](https://jd.papermc.io/paper/1.21/org/bukkit/entity/EntityType.html)
name ('COW', 'ZOMBIE', 'PHANTOM', ...) - tab-complete only *suggests* common
passive animals, it isn't a whitelist.

## Permissions

| Node | Default | Grants |
|---|---|---|
| 'zonespawner.admin' | op | All of the above: creating, configuring, and deleting zones. |

## How population tracking works

Every mob this plugin spawns is tagged via 'PersistentDataContainer' with the
name of the zone that owns it, plus its exact spawn location. A zone's count
is "how many living, tagged mobs exist anywhere in the world for this zone" -
not how many are currently standing inside its borders - so a cow that
wanders off still counts against the cap, and it's what the leash radius
pulls back. Untagged wild animals (already on the server, or spawned some
other way) are never counted either way. Mobs in unloaded chunks are
invisible to the server entirely, so one that wanders far enough to unload
its own chunk will briefly stop counting/leashing until that chunk loads
again.

## Playing well with other plugins

Zone spawns go through 'World#spawnEntity(...)', which fires the normal
'CreatureSpawnEvent' with reason 'CUSTOM' - so any region-protection,
mob-limit, or "no monsters here" plugin on your server gets a chance to
cancel them, exactly like it would for a plugin-spawned mob from anywhere
else. If another plugin does cancel a zone's spawn attempt, ZoneSpawner
detects it (the returned entity is no longer valid) and skips tagging it
rather than silently believing the zone is populated - it logs a
rate-limited warning instead so you can see which zone/species is being
blocked and by what.

If you're running **FactionsMobGuard** alongside this plugin: its default
config already exempts 'CUSTOM'-reason spawns, so hostile mobs configured in
a ZoneSpawner zone will spawn normally even inside peaceful faction
territory, while ambient/natural hostile spawns nearby are still blocked as
intended. See FactionsMobGuard's own README for details.

## Data

Zones persist in 'plugins/ZoneSpawner/zones.yml', including their rules,
floor/height settings, and corners, so they survive restarts.
