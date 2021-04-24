package com.winthier.adviceanimals;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class PlayerSession {
    protected final String name;
    protected String selectAnimal = null;
    protected boolean info = false;
    protected String rename = null;
    protected Location teleport = null;
    protected long lastAdvice = 0L;
    protected AdviceAnimal lastAnimal;
    protected int adviceIndex;

    public PlayerSession(final Player player) {
        name = player.getName();
    }

    public Player getPlayer() {
        return Bukkit.getServer().getPlayerExact(name);
    }
}
