package me.cortezromeo.inventorypagesplus.manager;

import me.cortezromeo.inventorypagesplus.Settings;
import me.cortezromeo.inventorypagesplus.task.AutoSaveTask;

public class AutoSaveManager {

    public static boolean autoSaveEnabled = false;
    private static AutoSaveTask autoSaveTask;

    public static void startAutoSave(int time) {
        if (Settings.AUTO_SAVE_ENABLED && autoSaveEnabled && autoSaveTask != null)
            return;

        autoSaveTask = new AutoSaveTask(time);
        autoSaveEnabled = true;
    }

    public static void stopAutoSave() {
        if (!autoSaveEnabled && autoSaveTask == null)
            return;

        autoSaveTask.cancel();
        autoSaveEnabled = false;
    }

    public static void reloadTimeAutoSave() {
        if (!isAutoSaveEnabled())
            return;

        stopAutoSave();
        startAutoSave(Settings.AUTO_SAVE_SECONDS);
    }

    public static boolean isAutoSaveEnabled() {
        return autoSaveEnabled;
    }

    public static void setAutoSaveEnabled(boolean b) {
        autoSaveEnabled = b;
    }

}
