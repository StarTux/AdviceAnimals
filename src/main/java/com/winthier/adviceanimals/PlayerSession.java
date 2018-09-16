package com.winthier.adviceanimals;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class PlayerSession {
    public final String name;
    public String selectAnimal = null;
    public boolean info = false;
    public String rename = null;
    public Location teleport = null;
    public long lastAdvice = 0L;
    public AdviceAnimal lastAnimal;
    public int adviceIndex;

    public PlayerSession(Player player) {
        name = player.getName();
    }

    public Player getPlayer() {
        return Bukkit.getServer().getPlayerExact(name);
    }
}
