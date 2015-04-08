package com.winthier.adviceanimals;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;

public class PlayerSession {
        public final String name;
        public String selectAnimal = null;
        public boolean info = false;
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
