package com.winthier.adviceanimals;

import com.cavetale.core.event.player.PluginPlayerEvent;
import com.cavetale.core.event.player.PluginPlayerEvent.Detail;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.plugin.java.JavaPlugin;

public final class AdviceAnimalsPlugin extends JavaPlugin {
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
    // Animation
    private Animation recording = null;
    // Static instance.
    private static AdviceAnimalsPlugin instance;
    private final Set<UUID> ignores = new HashSet<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
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
        result = result.replace("{prefix}", prefix);
        result = result.replace("{message}", message);
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
            int maxHealth = (int) Math.round(entity.getAttribute(Attribute.MAX_HEALTH).getValue());
            sender.sendMessage("Health: " + entity.getHealth() + "/" + maxHealth);
            if (entity instanceof Tameable) {
                Tameable tameable = (Tameable) entity;
                sender.sendMessage("Tamed: " + (tameable.isTamed() ? "Yes" : "No"));
                sender.sendMessage("Owner: " + (tameable.getOwner() != null ? tameable.getOwner().getName() : "None"));
            }
        }
    }

    public boolean checkEntity(Entity entity, Player player) {
        if (player != null && ignores.contains(player.getUniqueId())) return false;
        if (entity == null) return false;
        if (!(entity instanceof LivingEntity)) return false;
        LivingEntity living = (LivingEntity) entity;
        AdviceAnimal animal = getAnimal(living);
        if (animal != null) animal.check(living);
        if (player != null) {
            if (modifyAnimal(player, living)) {
                return true;
            } else if (animal != null) {
                animal.printMessage(player);
                PluginPlayerEvent.Name.INTERACT_NPC.make(this, player)
                    .detail(Detail.NAME, animal.getName())
                    .callEvent();
            }
        }
        return animal != null;
    }

    public boolean checkEntity(Entity entity) {
        return checkEntity(entity, (Player) null);
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
        } else if (session.rename != null) {
            entity.setCustomName(session.rename);
            session.rename = null;
        } else if (session.teleport != null) {
            Location loc = session.teleport;
            session.teleport = null;
            AdviceAnimal animal = getAnimal(entity);
            if (animal == null) {
                player.sendMessage("This entity is not an Advice Animal. Teleporting anyway.");
            } else {
                animal.setLocation(loc);
                player.sendMessage("Entity location udpated.");
            }
            entity.teleport(loc);
        } else {
            return false;
        }
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) return false;
        Player player = null;
        if (sender instanceof Player) player = (Player) sender;
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
        } else if (args.length >= 1 && args[0].equals("rename")) {
            if (player == null) {
                sender.sendMessage("Player expected");
                return true;
            }
            if (args.length >= 2) {
                StringBuilder sb = new StringBuilder(args[1]);
                for (int i = 2; i < args.length; ++i) sb.append(" ").append(args[i]);
                getPlayerSession(player).rename = ChatColor.translateAlternateColorCodes('&', sb.toString());
            } else {
                getPlayerSession(player).rename = "";
            }
            player.sendMessage("Hit the animal you want to rename.");
        } else if ((args.length == 1 || args.length == 2) && args[0].equals("teleport")) {
            if (player == null) {
                sender.sendMessage("Player expected");
                return true;
            }
            if (args.length == 2) {
                AdviceAnimal animal = nameMap.get(args[1]);
                if (animal == null) {
                    player.sendMessage(ChatColor.RED + "Animal not found: " + args[1]);
                    return true;
                }
                animal.setLocation(player.getLocation());
                animal.teleport(player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Animal teleported to you: " + animal.name);
                return true;
            }
            getPlayerSession(player).teleport = player.getLocation();
            player.sendMessage("Hit the animal you want to teleport.");
        } else if (args.length == 1 && args[0].equals("ignore")) {
            if (player == null) {
                sender.sendMessage("Player expected");
                return true;
            }
            boolean val = ignores.remove(player.getUniqueId());
            if (val) {
                player.sendMessage("No longer ignoring advice animals.");
            } else {
                ignores.add(player.getUniqueId());
                player.sendMessage("Ignoring advice animals.");
            }
        } else if (args.length == 2 && args[0].equals("locate")) {
            if (player == null) {
                sender.sendMessage("Player expected");
                return true;
            }
            String key = args[1];
            AdviceAnimal aa = nameMap.get(key);
            if (aa == null) {
                sender.sendMessage("Advice animal not found: " + key);
                return true;
            }
            if (aa.getEntity() != null && aa.getEntity().isValid()) {
                player.teleport(aa.getEntity());
            } else if (aa.getLocation() != null) {
                player.teleport(aa.getLocation());
            } else {
                player.sendMessage("Could not locate advice animal " + aa.getName());
            }
            player.sendMessage("Teleported to advice animal " + aa.getName());
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
        } else if (args.length == 2 || args.length == 3 && args[0].equals("record")) {
            if (player == null) return false;
            String subcmd = args[1].toLowerCase();
            if (subcmd.equals("start")) {
                if (recording != null) {
                    player.sendMessage("Recording already exists!");
                    return true;
                }
                recording = new Animation();
                recording.recordee = player;
                recording.runTaskTimer(this, 1, 1);
                player.sendMessage("Recording started");
            } else if (subcmd.equals("stop")) {
                if (recording == null) {
                    player.sendMessage("Not recording!");
                    return true;
                }
                try {
                    recording.cancel();
                } catch (IllegalStateException ise) { }
                player.sendMessage("Recording stopped");
            } else if (subcmd.equals("cancel")) {
                if (recording == null) {
                    player.sendMessage("Not recording!");
                    return true;
                }
                try {
                    recording.cancel();
                } catch (IllegalStateException ise) { }
                recording = null;
                player.sendMessage("Recording cancelled");
            } else if (subcmd.equals("save") && args.length == 3) {
                if (recording == null) {
                    player.sendMessage("No recording exists!");
                    return true;
                }
                String name = args[2];
                try {
                    recording.cancel();
                } catch (IllegalStateException ise) { }
                recording.saveAnimation(name);
                recording = null;
                player.sendMessage("Recording saved as " + name + ".");
            } else {
                return false;
            }
        }
        return true;
    }

    public static AdviceAnimalsPlugin getInstance() {
        return instance;
    }
}
