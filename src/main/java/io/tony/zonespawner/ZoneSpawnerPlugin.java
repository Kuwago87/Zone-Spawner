package io.tony.zonespawner;

import io.tony.zonespawner.command.ZPosCommand;
import io.tony.zonespawner.command.ZoneCommand;
import io.tony.zonespawner.command.ZoneCreateCommand;
import io.tony.zonespawner.task.ZoneSpawnTask;
import io.tony.zonespawner.zone.SelectionManager;
import io.tony.zonespawner.zone.ZoneManager;
import io.tony.zonespawner.zone.ZoneTagKeys;
import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public final class ZoneSpawnerPlugin extends JavaPlugin {

    /** How often (in ticks) the spawn regulation task runs. 20 ticks = 1 second. */
    private static final long TASK_PERIOD_TICKS = 20L;

    // bStats plugin ID for ZoneSpawner - see https://bstats.org/plugin/bukkit/ZoneSpawner/34218
    private static final int BSTATS_PLUGIN_ID = 34218;

    private ZoneManager zoneManager;
    private SelectionManager selectionManager;
    private ZoneTagKeys tagKeys;
    private Metrics metrics;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        this.tagKeys = ZoneTagKeys.create(this);
        this.zoneManager = new ZoneManager(this);
        this.zoneManager.load();
        this.selectionManager = new SelectionManager();

        ZPosCommand zPosCommand = new ZPosCommand(selectionManager);
        getCommand("zpos1").setExecutor(zPosCommand);
        getCommand("zpos2").setExecutor(zPosCommand);
        getCommand("zpos3").setExecutor(zPosCommand);
        getCommand("zonecreate").setExecutor(new ZoneCreateCommand(selectionManager, zoneManager));

        ZoneCommand zoneCommand = new ZoneCommand(zoneManager);
        getCommand("zone").setExecutor(zoneCommand);
        getCommand("zone").setTabCompleter(zoneCommand);

        getServer().getScheduler().runTaskTimer(
                this,
                new ZoneSpawnTask(this, zoneManager, tagKeys),
                TASK_PERIOD_TICKS,
                TASK_PERIOD_TICKS
        );

        this.metrics = new Metrics(this, BSTATS_PLUGIN_ID);

        getLogger().info("ZoneSpawner enabled.");
    }

    @Override
    public void onDisable() {
        if (zoneManager != null) {
            zoneManager.save();
        }
        getLogger().info("ZoneSpawner disabled.");
    }
}
