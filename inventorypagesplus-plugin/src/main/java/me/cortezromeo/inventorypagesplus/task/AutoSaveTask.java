package me.cortezromeo.inventorypagesplus.task;

import me.cortezromeo.inventorypagesplus.InventoryPagesPlus;
import me.cortezromeo.inventorypagesplus.manager.DebugManager;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public class AutoSaveTask implements Runnable {

    private final BukkitTask task;

    public AutoSaveTask(int time) {
        this.task = Bukkit.getScheduler().runTaskTimerAsynchronously(InventoryPagesPlus.plugin, this, 20L * time, 20L * time);
    }

    public BukkitTask getBukkitTask() {
        return task;
    }

    public int getTaskID() {
        return task.getTaskId();
    }

    @Override
    public void run() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            DebugManager.debug("AUTO SAVE", "The database is not saved because there is no players.");
            return;
        }

        InventoryPagesPlus.getDatabaseManager().updateAndSaveAllInventoriesToDatabase();
        DebugManager.debug("AUTO SAVE", "Saved " + Bukkit.getOnlinePlayers().size() + " player database.");
    }

    public void cancel() {
        task.cancel();
    }

}
