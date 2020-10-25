package com.winthier.adviceanimals;

import com.winthier.adviceanimals.AdviceAnimalsPlugin;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public final class Sounds {
    private final AdviceAnimalsPlugin plugin;
    private Random random = new Random(System.currentTimeMillis());

    public Sounds(AdviceAnimalsPlugin plugin) {
        this.plugin = plugin;
    }

    public void playSounds(final Player player, final Location loc, final String soundName, final float volume, final float median, final float variance, final int amount, long period) {
        new BukkitRunnable() {
            private int count = 0;

            @Override
            public void run() {
                try {
                    Sound sound = null;
                    try {
                        sound = Sound.valueOf(soundName.toUpperCase());
                    } catch (IllegalArgumentException iae) {
                        // do nothing
                    }
                    if (sound == null) {
                        player.playSound(loc, soundName, volume, median + random.nextFloat() * variance - random.nextFloat() * variance);
                    } else {
                        loc.getWorld().playSound(loc, sound, SoundCategory.MASTER, volume, median + random.nextFloat() * variance - random.nextFloat() * variance);
                    }
                    if (++count >= amount) {
                        cancel();
                    }
                } catch (Exception e) {
                    // do nothing
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, period);
    }
}
