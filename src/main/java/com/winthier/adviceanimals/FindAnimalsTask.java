package com.winthier.adviceanimals;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;

public final class FindAnimalsTask {
    private final static long INTERVAL = 20L;
    private final AdviceAnimalsPlugin plugin;
    Iterator<Entity> entities = null;
    private BukkitRunnable task;

    public FindAnimalsTask(AdviceAnimalsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = new BukkitRunnable() {
            public void run() {
                iter();
            }
        };
        task.runTaskTimer(plugin, INTERVAL, INTERVAL);
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
        entities = null;
    }
    
    public void iter() {
        try {
            if (entities == null || !entities.hasNext()) {
                List<Entity> entityList = new ArrayList<>();
                for (World world: plugin.getServer().getWorlds()) {
                    entityList.addAll(world.getEntities());
                }
                this.entities = entityList.iterator();
            } else {
                for (int i = 0; i < 100; ++i) {
                    if (!entities.hasNext()) return;
                    Entity entity = entities.next();
                    if (!entity.isValid()) continue;
                    plugin.checkEntity(entity);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            stop();
        }
    }
}
