package com.winthier.adviceanimals;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class Sounds {
    private final AdviceAnimalsPlugin plugin;
    private Random random = new Random(System.currentTimeMillis());

    private static Sound parseSound(String soundName) {
        try {
            return Sound.valueOf(soundName.toUpperCase());
        } catch (IllegalArgumentException iae) {
            return null;
        }
    }

    public void playSounds(final Player player, final Location loc, final AdviceAnimal adviceAnimal) {
        final String soundName = adviceAnimal.getSoundName();
        if (soundName == null) return;
        final Sound sound = parseSound(soundName);
        final float volume = adviceAnimal.getSoundVolume();
        final float median = adviceAnimal.getSoundMedian();
        final float variance = adviceAnimal.getSoundVariance();
        final int amount = adviceAnimal.getSoundAmount();
        final long period = adviceAnimal.getSoundPeriod();
        new BukkitRunnable() {
            private int count = 0;

            @Override
            public void run() {
                try {
                    float pitch = median + random.nextFloat() * variance - random.nextFloat() * variance;
                    if (sound == null) {
                        player.playSound(loc, soundName, volume, pitch);
                    } else {
                        loc.getWorld().playSound(loc, sound, SoundCategory.MASTER, volume, pitch);
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
