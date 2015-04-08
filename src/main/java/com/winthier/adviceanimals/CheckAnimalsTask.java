package com.winthier.adviceanimals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import org.bukkit.scheduler.BukkitRunnable;

public class CheckAnimalsTask {
    private final AdviceAnimalsPlugin plugin;
    Iterator<AdviceAnimal> animals;
    private BukkitRunnable task;

    public CheckAnimalsTask(AdviceAnimalsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = new BukkitRunnable() {
            public void run() {
                iter();
            }
        };
        task.runTaskTimer(plugin, 20L, 20L);
    }

    public void stop() {
        flush();
        if (task == null) return;
        try {
            task.cancel();
        } catch (IllegalStateException ise) {
            ise.printStackTrace();
        }
        task = null;
    }

    public void flush() {
        animals = null;
    }

    public void iter() {
        try {
            for (AdviceAnimal animal : plugin.getAnimals()) {
                animal.check();
            }
        } catch (Throwable t) {
            t.printStackTrace();
            stop();
        }
    }
}
