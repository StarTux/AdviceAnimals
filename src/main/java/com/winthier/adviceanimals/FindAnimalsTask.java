package com.winthier.adviceanimals;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class FindAnimalsTask {
    private final static long INTERVAL = 20L;
    private final static double RADIUS = 64.0;
    private final AdviceAnimalsPlugin plugin;
    Iterator<UUID> players = null;
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
        players = null;
    }
    
    public void iter() {
        try {
            if (players == null || !players.hasNext()) {
                List<UUID> uuids = new ArrayList<>();
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    uuids.add(player.getUniqueId());
                }
                this.players = uuids.iterator();
            } else {
                UUID uuid = players.next();
                Player player = plugin.getServer().getPlayer(uuid);
                if (player == null) return;
                for (Entity entity : player.getNearbyEntities(RADIUS, RADIUS, RADIUS)) {
                    plugin.checkEntity(entity);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
            stop();
        }
    }
}
