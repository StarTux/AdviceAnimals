package com.winthier.adviceanimals;

import java.util.Iterator;
import org.bukkit.scheduler.BukkitRunnable;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class CheckAnimalsTask {
    private final AdviceAnimalsPlugin plugin;
    Iterator<AdviceAnimal> animals;
    private BukkitRunnable task;

    public void start() {
        stop();
        task = new BukkitRunnable() {
            public void run() {
                iter();
            }
        };
        task.runTaskTimer(plugin, 1, 1);
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
