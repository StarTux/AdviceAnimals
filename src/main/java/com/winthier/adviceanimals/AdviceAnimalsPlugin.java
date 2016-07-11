package com.winthier.adviceanimals;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.plugin.java.JavaPlugin;

public class AdviceAnimalsPlugin extends JavaPlugin {
    private final Map<UUID, AdviceAnimal> uuidMap = new HashMap<UUID, AdviceAnimal>();
    private final Map<String, AdviceAnimal> nameMap = new LinkedHashMap<String, AdviceAnimal>();
    private final Map<String, PlayerSession> sessionMap = new HashMap<String, PlayerSession>();
    private AnimalEventListener animalEventListener = new AnimalEventListener(this);
    private long adviceCooldown = 20;
    private String messageFormat;
    private Sounds sounds = new Sounds(this);
    // Tasks to locate animals and make sure they don't wander.
    private CheckAnimalsTask checkAnimalsTask = new CheckAnimalsTask(this);
    private FindAnimalsTask findAnimalsTask = new FindAnimalsTask(this);
    // Static instance.
    private static AdviceAnimalsPlugin instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();
        loadConfiguration();
        animalEventListener.enable();
        checkAnimalsTask.start();
        findAnimalsTask.start();
    }

    @Override
    public void onDisable() {
        saveConfiguration();
        animalEventListener = null;
        checkAnimalsTask.stop();
        findAnimalsTask.stop();
        instance = null;
    }

    public Sounds getSounds() {
        return sounds;
    }

    public long getAdviceCooldown() {
        return adviceCooldown * 50;
    }

    public void loadConfiguration() {
        reloadConfig();
        messageFormat = getConfig().getString("MessageFormat");
        clearAnimals();
        ConfigurationSection animalsSection = getConfig().getConfigurationSection("animals");
        for (String key : animalsSection.getKeys(false)) {
            ConfigurationSection animalSection = animalsSection.getConfigurationSection(key);
            AdviceAnimal animal = new AdviceAnimal(this, key);
            animal.configure(animalSection);
            addAnimal(animal);
        }
        adviceCooldown = getConfig().getLong("Cooldown");
    }

    public void saveConfiguration() {
        ConfigurationSection animalsSection = getConfig().createSection("animals");
        for (String key : nameMap.keySet()) {
            ConfigurationSection animalSection = animalsSection.createSection(key);
            nameMap.get(key).serialize(animalSection);
        }
        saveConfig();
    }

    public void clearAnimals() {
        // Clear all the maps.
        uuidMap.clear();
        nameMap.clear();

        // Forget about pending location checks which
        // might belong to invalid animals.
        if (checkAnimalsTask != null) checkAnimalsTask.flush();
    }

    public void updateUniqueId(UUID oldId, UUID newId, AdviceAnimal animal) {
        if (oldId != null) uuidMap.remove(oldId);
        if (newId != null) uuidMap.put(newId, animal);
    }

    public void addAnimal(AdviceAnimal animal) {
        nameMap.put(animal.getName(), animal);
    }

    public void removeAnimal(AdviceAnimal animal) {
        updateUniqueId(animal.getUniqueId(), null, animal);
        nameMap.remove(animal.getName());
    }

    public String formatMessage(String prefix, String message) {
        String result = messageFormat;
        result = result.replaceAll(Pattern.quote("{prefix}"), Matcher.quoteReplacement(prefix));
        result = result.replaceAll(Pattern.quote("{message}"), Matcher.quoteReplacement(message));
        result = ChatColor.translateAlternateColorCodes('&', result);
        return result;
    }

    /**
     * Get all Advice Animals.
     */
    public Collection<AdviceAnimal> getAnimals() {
        return nameMap.values();
    }

    public AdviceAnimal getAnimal(UUID uuid) {
        return uuidMap.get(uuid);
    }

    public AdviceAnimal getAnimal(String name) {
        return nameMap.get(name);
    }

    public AdviceAnimal getAnimal(LivingEntity entity) {
        return uuidMap.get(entity.getUniqueId());
    }

    public PlayerSession getPlayerSession(Player player) {
        PlayerSession result = sessionMap.get(player.getName());
        if (result == null) {
            result = new PlayerSession(player);
            sessionMap.put(player.getName(), result);
        }
        return result;
    }

    public void printEntityInfo(LivingEntity entity, CommandSender sender) {
        sender.sendMessage("[AdviceAnimals] Entity Information");
        if (entity == null) {
            sender.sendMessage("Not loaded");
        } else {
            if (entity.getCustomName() != null) {
                sender.sendMessage("Custom Name: " + entity.getCustomName());
            }
            sender.sendMessage("Type: " + entity.getType().name().toLowerCase());
            Location loc = entity.getLocation();
            sender.sendMessage("Location: " + loc.getWorld().getName() + ", " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
            sender.sendMessage("Health: " + entity.getHealth() + "/" + entity.getMaxHealth());
            if (entity instanceof Tameable) {
                Tameable tameable = (Tameable)entity;
                sender.sendMessage("Tamed: " + (tameable.isTamed() ? "Yes" : "No"));
                sender.sendMessage("Owner: " + (tameable.getOwner() != null ? tameable.getOwner().getName() : "None"));
            }
        }
    }

    public boolean checkEntity(Entity entity, Player player) {
        if (entity == null) return false;
        if (!(entity instanceof LivingEntity)) return false;
        LivingEntity living = (LivingEntity)entity;
        AdviceAnimal animal = getAnimal(living);
        if (animal != null) animal.check(living);
        if (player != null) {
            if (modifyAnimal(player, living)) {
                return true;
            } else {
                if (animal != null) animal.printMessage(player);
            }
        }
        return animal != null;
    }

    public boolean checkEntity(Entity entity) {
        return checkEntity(entity, (Player)null);
    }

    private boolean modifyAnimal(Player player, LivingEntity entity) {
        PlayerSession session = getPlayerSession(player);
        if (session.selectAnimal != null) {
            AdviceAnimal animal = getAnimal(session.selectAnimal);
            if (animal != null) {
                animal.select(entity);
                player.sendMessage(entity.getType().name().toLowerCase() + " selected as advice animal \"" + animal.getName() + "\"!");
            }
            session.selectAnimal = null;
        } else if (session.info) {
            printEntityInfo(entity, player);
            session.info = false;
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String args[]) {
        Player player = null;
        if (sender instanceof Player) player = (Player)sender;
        if (args.length == 1 && args[0].equals("reload")) {
            reloadConfig();
            loadConfiguration();
            sender.sendMessage("Configuration reloaded.");
        } else if (args.length == 1 && args[0].equals("save")) {
            saveConfiguration();
            sender.sendMessage("Configuration saved to disk.");
        } else if (args.length == 1 && args[0].equals("list")) {
            sender.sendMessage("[AdviceAnimals] Animal List");
            for (String key : nameMap.keySet()) {
                sender.sendMessage("- " + key);
            }
        } else if (args.length == 2 && args[0].equals("info")) {
            AdviceAnimal animal = getAnimal(args[1]);
            if (animal == null) {
                sender.sendMessage("Animal " + args[1] + " not found!");
                return true;
            }
            animal.printInfo(sender);
            LivingEntity entity = animal.getEntity();
            printEntityInfo(entity, sender);
        } else if (args.length == 1 && args[0].equals("info")) {
            if (player == null) {
                sender.sendMessage("Player expected");
                return true;
            }
            getPlayerSession(player).info = true;
            player.sendMessage("Hit the animal you want information about.");
        } else if (args.length == 2 && args[0].equals("select")) {
            if (player == null) {
                sender.sendMessage("Player expected");
                return true;
            }
            AdviceAnimal animal = getAnimal(args[1]);
            if (animal == null) {
                sender.sendMessage("Animal not found: \"" + args[1] + "\".");
                return true;
            }
            getPlayerSession(player).selectAnimal = animal.getName();
            sender.sendMessage("Hit the animal you want to select.");
        } else if (args.length == 2 && args[0].equals("create")) {
            String name = args[1];
            AdviceAnimal animal = getAnimal(name);
            if (animal != null) {
                sender.sendMessage("Animal \"" + name + "\" already exists!");
                return true;
            }
            animal = new AdviceAnimal(this, name);
            nameMap.put(name, animal);
            if (player != null) {
                getPlayerSession(player).selectAnimal = name;
                sender.sendMessage("Created animal \"" + name + "\". Hit an entity to select it.");
            } else {
                sender.sendMessage("Created animal \"" + name + "\".");
            }
        } else if (args.length == 2 && args[0].equals("tasks")) {
            if (args[1].equals("stop")) {
                checkAnimalsTask.stop();
                sender.sendMessage("Tasks stopped. Don't forget to start them again.");
            } else if (args[1].equals("start")) {
                checkAnimalsTask.start();
                sender.sendMessage("Tasks restarted");
            } else {
                sender.sendMessage("Usage: /aa tasks start|stop");
            }
        } else {
            sender.sendMessage("Usage:");
            sender.sendMessage("- /aa list");
            sender.sendMessage("- /aa info");
            sender.sendMessage("- /aa info <name>");
            sender.sendMessage("- /aa setowner <name>");
        }
        return true;
    }

    public static AdviceAnimalsPlugin getInstance() {
        return instance;
    }
}