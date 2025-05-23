package com.winthier.adviceanimals;

import com.destroystokyo.paper.entity.ai.Goal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Breedable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.EulerAngle;

@Getter
public final class AdviceAnimal {
    public final AdviceAnimalsPlugin plugin;
    public final String name;
    private UUID uuid = null;
    private List<Object> messages;
    private Location location = null;
    private boolean teleporting;
    private boolean randomize;
    private double health = 0.0;
    private LivingEntity cachedEntity = null;
    private double horizontalDistance = 0.0;
    private double verticalDistance = 0.0;
    private String prefix = "Animal";
    private boolean slow = false;
    private boolean removeGoals = false;
    // sound effect
    private String soundName;
    private int soundAmount;
    private long soundPeriod;
    private float soundVolume;
    private float soundMedian;
    private float soundVariance;
    // randomizer
    private static final Random RANDOM = new Random(System.currentTimeMillis());
    // animation
    private String animationName;
    private Animation animation = null;
    private int animationFrame = 0;
    private int ticks;
    private int motion;

    public AdviceAnimal(final AdviceAnimalsPlugin plugin, final String name) {
        this.plugin = plugin;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    public String formatMessage(String message) {
        return plugin.formatMessage(prefix, message);
    }

    public UUID getUniqueId() {
        return uuid;
    }

    public void setUniqueId(UUID newUuid) {
        plugin.updateUniqueId(this.uuid, newUuid, this);
        this.uuid = newUuid;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }

    public void select(LivingEntity entity) {
        uuid = entity.getUniqueId();
        cachedEntity = entity;
        setupEntity(entity);
        setLocation(entity.getLocation().clone());
        if (entity instanceof Breedable) {
            Breedable breedable = (Breedable) entity;
            breedable.setAgeLock(true);
        }
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
        health = entity.getHealth();
    }

    /**
     * Configure thie Advice Animal. It is assumed that this
     * is called right after the constructor, so no values
     * should be set other than the name.
     */
    @SuppressWarnings("unchecked")
    public void configure(ConfigurationSection section) {
        // Entity UUID
        String tmp;
        tmp = section.getString("UUID");
        if (tmp != null) {
            setUniqueId(UUID.fromString(tmp));
        }
        // Entity Location
        tmp = section.getString("Location");
        if (tmp != null) {
            Location loc = parseLocation(tmp);
            if (loc == null) plugin.getLogger().warning("Failed to parse location: " + tmp);
            if (loc != null) this.location = loc;
        } else {
            plugin.getLogger().warning("Animal " + name + " does not set location");
        }
        prefix = section.getString("Prefix", "Animal");
        messages = (List<Object>) section.getList("messages");
        // legacy
        if (this.messages == null && section.isList("Messages")) {
            plugin.getLogger().warning(name + ": LEGACY");
            this.messages = (List<Object>) section.getList("Messages");
        }
        randomize = section.getBoolean("Randomize", false);
        health = section.getDouble("Health", 0.0);
        slow = section.getBoolean("Slow", false);
        removeGoals = section.getBoolean("RemoveGoals", false);
        horizontalDistance = section.getDouble("HorizontalDistance", 0.0);
        verticalDistance = section.getDouble("VerticalDistance", 0.0);
        ConfigurationSection soundSection = section.getConfigurationSection("sound");
        if (soundSection != null) {
            soundName = soundSection.getString("Name", "ENTITY_CAT_AMBIENT");
            soundAmount = soundSection.getInt("Amount", 5);
            soundPeriod = soundSection.getLong("Period", 4);
            soundVolume = (float) soundSection.getDouble("Volume", 1.0);
            soundMedian = (float) soundSection.getDouble("Median", 1.0);
            soundVariance = (float) soundSection.getDouble("Variance", 0.25);
        }
        animationName = section.getString("Animation");
    }

    public void serialize(ConfigurationSection section) {
        if (uuid != null) section.set("UUID", uuid.toString());
        if (this.location != null) section.set("Location", serializeLocation(this.location));
        section.set("Prefix", prefix);
        section.set("messages", messages);
        section.set("Randomize", randomize);
        section.set("Health", health);
        section.set("Slow", slow);
        section.set("RemoveGoals", removeGoals);
        section.set("HorizontalDistance", horizontalDistance);
        section.set("VerticalDistance", verticalDistance);
        if (soundName != null) {
            ConfigurationSection soundSection = section.createSection("sound");
            soundSection.set("Name", soundName);
            soundSection.set("Amount", soundAmount);
            soundSection.set("Period", soundPeriod);
            soundSection.set("Volume", soundVolume);
            soundSection.set("Median", soundMedian);
            soundSection.set("Variance", soundVariance);
        }
        if (animationName != null) section.set("Animation", animationName);
    }

    public LivingEntity getEntity() {
        if (cachedEntity != null) {
            if (!cachedEntity.isDead()) {
                return cachedEntity;
            } else {
                cachedEntity = null;
            }
        } else if (uuid != null) {
            Entity e = Bukkit.getEntity(uuid);
            if (e instanceof LivingEntity) {
                cachedEntity = (LivingEntity) e;
                setupEntity(cachedEntity);
                Location loc = e.getLocation();
                plugin.getLogger().info(String.format("Discovered animal %s at %s %d,%d,%d ",
                                                      name, loc.getWorld().getName(),
                                                      loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
                return cachedEntity;
            }
        }
        return null;
    }

    /**
     * Modify a newly selected or discovered entity for the first time.
     */
    private void setupEntity(LivingEntity entity) {
        if (slow) {
            entity.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.0);
        }
        if (removeGoals && entity instanceof Mob) {
            Mob mob = (Mob) entity;
            for (Goal<Mob> goal : Bukkit.getMobGoals().getAllGoals(mob)) {
                if (!goal.getKey().getNamespacedKey().getKey().equals("look_at_player")) {
                    Bukkit.getMobGoals().removeGoal(mob, goal);
                }
            }
        }
        entity.setCollidable(false);
    }

    private Location parseLocation(String str) {
        String[] tokens = str.split(",");
        if (tokens.length != 6) {
            plugin.getLogger().warning("Expected 6 tokens, got " + tokens.length);
            return null;
        }
        World world = Bukkit.getServer().getWorld(tokens[0]);
        if (world == null) {
            plugin.getLogger().warning("World not found: " + tokens[0]);
            return null;
        }
        double x;
        double y;
        double z;
        float yaw;
        float pitch;
        try {
            x = Double.parseDouble(tokens[1]);
            y = Double.parseDouble(tokens[2]);
            z = Double.parseDouble(tokens[3]);
            yaw = Float.parseFloat(tokens[4]);
            pitch = Float.parseFloat(tokens[5]);
        } catch (NumberFormatException nfe) {
            plugin.getLogger().warning("Number Format Exception: " + nfe.getMessage());
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    private static String serializeLocation(Location loc) {
        return String.format("%s,%f,%f,%f,%f,%f", loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    public void printInfo(CommandSender sender) {
        sender.sendMessage("[AdviceAnimals] Info for animal " + name);
        if (uuid == null) {
            sender.sendMessage("UUID: N/A");
        } else {
            sender.sendMessage("UUID: " + uuid.toString());
        }
        if (this.location == null) {
            sender.sendMessage("Location: N/A");
        } else {
            sender.sendMessage("Location: " + location.getWorld().getName()
                               + ", " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        }
        sender.sendMessage("HorizontalDistance: " + horizontalDistance);
        sender.sendMessage("VerticalDistance: " + verticalDistance);
    }

    public Object dealMessage(PlayerSession session) {
        int iter = 0;
        if (session.lastAnimal == this) {
            iter = session.adviceIndex;
        } else {
            session.lastAnimal = this;
            session.adviceIndex = 0;
        }
        if (messages == null || messages.isEmpty()) return null;
        Object result = null;
        if (randomize) {
            result = messages.get(RANDOM.nextInt(messages.size()));
        } else {
            result = messages.get(Math.min(iter, messages.size() - 1));
            iter += 1;
            if (iter >= messages.size()) iter = 0;
            session.adviceIndex = iter;
        }
        return result;
    }

    private void legacy(Player player, String message) {
        for (String line : message.split("\\\\n|\\n")) {
            line = line.replaceAll(Pattern.quote("{player}"), player.getName());
            if (line.startsWith("/CMD ")) {
                final String cmd = line.substring(5);
                plugin.getServer().dispatchCommand(player, cmd);
            } else if (line.startsWith("/CCMD")) {
                final String cmd = line.substring(6);
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
            } else {
                player.sendMessage(plugin.formatMessage(prefix, line));
            }
        }
    }

    private String format(String string, Player player) {
        string = string.replace("{player}", player.getName());
        string = string.replace("{adviceanimal}", name);
        return string;
    }

    private List<String> getStringList(ConfigurationSection config, String key) {
        if (config.isList(key)) return config.getStringList(key);
        List<String> result = new ArrayList<>(1);
        if (config.isSet(key)) result.add(config.getString(key));
        return result;
    }

    private void useMessage(Player player, Object message) {
        if (!(message instanceof Map)) return;
        ConfigurationSection config = new MemoryConfiguration().createSection("tmp", (Map) message);
        for (String string : getStringList(config, "Text")) {
            string = format(string, player);
            player.sendMessage(plugin.formatMessage(prefix, string));
            Msg.sendActionBar(player, "&f" + string);
        }
        for (String string : getStringList(config, "Chat")) {
            string = format(string, player);
            player.chat(string);
        }
        for (String string : getStringList(config, "Command")) {
            string = format(string, player);
            plugin.getServer().dispatchCommand(player, string);
        }
        for (String string : getStringList(config, "ConsoleCommand")) {
            string = format(string, player);
            plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), string);
        }
        if (config.isList("raw")) {
            for (Object obj : config.getList("raw")) {
                String json = Msg.toJsonString(obj);
                String cmd = "tellraw " + player.getName() + " " + json;
                plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), cmd);
            }
        }
    }

    public void printMessage(Player player) {
        PlayerSession session = plugin.getPlayerSession(player);
        // check timer
        if (System.currentTimeMillis() - session.lastAdvice < plugin.getAdviceCooldown()) return;
        Object message = dealMessage(session);
        if (message == null) return;
        if (message instanceof String) {
            legacy(player, (String) message);
        } else {
            useMessage(player, message);
        }
        if (soundName != null) {
            plugin.getSounds().playSounds(player, cachedEntity.getLocation(), this);
        }
        session.lastAdvice = System.currentTimeMillis();
    }

    public void check() {
        // Stack Overflow
        if (teleporting) return;
        final LivingEntity entity = getEntity();
        if (entity == null) return;
        check(entity);
    }

    public void check(LivingEntity entity) {
        // Stack Overflow
        if (teleporting) return;
        Location entityLocation = entity.getLocation();
        // Animation stuff
        if (animation == null && animationName != null) {
            try {
                animation = Animation.loadAnimation(animationName);
            } catch (Exception e) {
                e.printStackTrace();
                animation = new Animation();
            }
        }
        if (animation == null || animation.frames.isEmpty()) {
            if (this.location == null) {
                setLocation(entityLocation.clone());
                return;
            }
            if (isTooFar(entityLocation)) {
                plugin.getLogger().info("Teleporting " + name);
                teleport(location);
            }
            if (name != null && entity instanceof ArmorStand) {
                switch (name) {
                case "PocketMob":
                case "EasterTokenShop":
                case "EasterTokens":
                case "EasterEggs": {
                    ArmorStand stand = (ArmorStand) entity;
                    if (motion < 20) {
                        int m = motion;
                        stand.setRightLegPose(new EulerAngle(1.0 - (double) m / 10.0, 0.0, 0.02));
                        stand.setLeftLegPose(new EulerAngle(-1.0 + (double) m / 10.0, 0.0, -0.02));

                        stand.setLeftArmPose(new EulerAngle(1.0 - (double) m / 10.0, 0.0, -0.02));
                        stand.setRightArmPose(new EulerAngle(-1.0 + (double) m / 10.0, 0.0, 0.02));

                        stand.setHeadPose(new EulerAngle(0.25 - (double) m / 40.0, 0.0, 0.0));
                    } else {
                        int m = motion - 20;
                        stand.setLeftLegPose(new EulerAngle(1.0 - (double) m / 10.0, 0.0, -0.02));
                        stand.setRightLegPose(new EulerAngle(-1.0 + (double) m / 10.0, 0.0, 0.02));

                        stand.setRightArmPose(new EulerAngle(1.0 - (double) m / 10.0, 0.0, 0.02));
                        stand.setLeftArmPose(new EulerAngle(-1.0 + (double) m / 10.0, 0.0, -0.02));

                        stand.setHeadPose(new EulerAngle(-0.25 + (double) m / 40.0, 0.0, 0.0));
                    }
                    motion = motion + 1;
                    if (motion >= 40) motion = 0;
                }
                default: break;
                }
            }
        } else {
            if (animationFrame >= animation.frames.size()) animationFrame = 0;
            animation.apply(entity, animationFrame);
            animationFrame += 1;
        }
        if (health > 0.0 && entity.getHealth() != health) {
            entity.setHealth(health);
        }
    }

    public void teleport(Location toLocation) {
        LivingEntity entity = getEntity();
        if (entity == null || entity.isDead()) return;
        teleporting = true;
        entity.teleport(toLocation);
        teleporting = false;
    }

    public boolean isTooFar(Location toLocation) {
        if (location == null) return false;
        if (!toLocation.getWorld().equals(location.getWorld())) return true;
        final double dx = toLocation.getX() - location.getX();
        final double dy = toLocation.getY() - location.getY();
        final double dz = toLocation.getZ() - location.getZ();
        return dx * dx + dz * dz > horizontalDistance * horizontalDistance
            || Math.abs(dy) > verticalDistance;
    }
}
