package com.winthier.adviceanimals;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.EulerAngle;
import org.json.simple.JSONValue;

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
    // sound effect
    private String soundName;
    private int soundAmount;
    private long soundPeriod;
    private float soundVolume;
    private float soundMedian, soundVariance;
    // randomizer
    private static final Random random = new Random(System.currentTimeMillis());
    // animation
    private String animationName;
    private Animation animation = null;
    private int animationFrame = 0;
    private int ticks;
    private int motion;

    public AdviceAnimal(AdviceAnimalsPlugin plugin, String name) {
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

    public void setUniqueId(UUID uuid) {
        plugin.updateUniqueId(this.uuid, uuid, this);
        this.uuid = uuid;
    }

    public void setCachedEntity(LivingEntity entity) {
        this.cachedEntity = entity;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }

    public boolean isTeleporting() {
        return teleporting;
    }

    public void select(LivingEntity entity) {
        uuid = entity.getUniqueId();
        cachedEntity = entity;
        setLocation(entity.getLocation().clone());
        // if (entity instanceof Tameable) {
        //     Tameable tameable = (Tameable)entity;
        //     if (tameable.isTamed()) {
        //         FakeTamer tamer = new FakeTamer();
        //         tameable.setOwner(tamer);
        //     }
        // }
        if (entity instanceof Ageable) {
            Ageable ageable = (Ageable)entity;
            ageable.setAgeLock(true);
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
        if ((tmp = section.getString("UUID")) != null) {
            setUniqueId(UUID.fromString(tmp));
        }
        // Entity Location
        if ((tmp = section.getString("Location")) != null) {
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
        if (uuid == null) return null;
        if (cachedEntity != null) {
            if (cachedEntity.getUniqueId().equals(uuid) && cachedEntity.isValid()) {
                return cachedEntity;
            } else {
                cachedEntity = null;
            }
        }
        return null;
    }

    private Location parseLocation(String str) {
        String tokens[] = str.split(",");
        if (tokens.length != 6) {
            plugin.getLogger().warning("Expected 6 tokens, got " + tokens.length);
            return null;
        }
        World world = Bukkit.getServer().getWorld(tokens[0]);
        if (world == null) {
            plugin.getLogger().warning("World not found: " + tokens[0]);
            return null;
        }
        double x, y, z;
        float yaw, pitch;
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
            sender.sendMessage("Location: " + location.getWorld().getName() + ", " + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
        }
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
            result = messages.get(random.nextInt(messages.size()));
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
        ConfigurationSection config = new MemoryConfiguration().createSection("tmp", (Map)message);
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
                String json = JSONValue.toJSONString(obj);
                //System.out.println(name + ": Sending json: " + json);
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
            legacy(player, (String)message);
        } else {
            useMessage(player, message);
        }
        if (soundName != null) {
            plugin.getSounds().playSounds(player, cachedEntity.getLocation(), soundName, soundVolume, soundMedian, soundVariance, soundAmount, soundPeriod);
        }
        session.lastAdvice = System.currentTimeMillis();
    }

    public void check() {
        final LivingEntity entity = getEntity();
        if (entity == null) return;
        check(entity);
    }

    public void check(LivingEntity entity) {
        Location entityLocation = entity.getLocation();
        if (entity != cachedEntity) {
            plugin.getLogger().info(String.format("Discovered animal %s at %s %d,%d,%d ", name, entityLocation.getWorld().getName(), entityLocation.getBlockX(), entityLocation.getBlockY(), entityLocation.getBlockZ()));
            cachedEntity = entity;
        }
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
            double dx = entityLocation.getX() - location.getX();
            double dy = entityLocation.getY() - location.getY();
            double dz = entityLocation.getZ() - location.getZ();
            if (!entityLocation.getWorld().equals(location.getWorld()) || dx * dx + dz * dz > horizontalDistance * horizontalDistance || Math.abs(dy) > verticalDistance) {
                plugin.getLogger().info("Teleporting " + name);
                teleporting = true;
                entity.teleport(location);
                teleporting = false;
            }
            if (entity instanceof ArmorStand) {
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
        } else {
            if (animationFrame >= animation.frames.size()) animationFrame = 0;
            animation.apply(entity, animationFrame);
            animationFrame += 1;
        }
        if (slow) {
            entity.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.0);
        }
        if (health > 0.0 && entity.getHealth() != health) {
            entity.setHealth(health);
        }
    }
}
