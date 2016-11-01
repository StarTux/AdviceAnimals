package com.winthier.adviceanimals;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

class Animation extends BukkitRunnable {
    @AllArgsConstructor
    static class Frame {
        Location location;

        Map<String, Object> serialize() {
            Map<String, Object> result = new HashMap<>();
            result.put("location", location);
            return result;
        }

        static Frame deserialize(Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Location location = (Location)map.get("location");
            return new Frame(location);
        }

        void apply(Entity e) {
            e.teleport(location);
        }
    }

    String name = null;
    Entity recordee = null;
    final List<Frame> frames = new ArrayList<>();

    @Override public void run() {
        Frame frame = new Frame(recordee.getLocation());
        frames.add(frame);
    }

    static File getFile(String name) {
        File dir = new File(AdviceAnimalsPlugin.getInstance().getDataFolder(), "animations");
        dir.mkdirs();
        return new File(dir, name + ".yml");
    }

    void save(File file) {
        YamlConfiguration config = new YamlConfiguration();
        List<Object> frames = new ArrayList<>();
        config.set("frames", frames);
        for (Frame frame: this.frames) frames.add(frame.serialize());
        try {
            config.save(file);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    void load(File file) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            for (Map<?, ?> map: config.getMapList("frames")) {
                this.frames.add(Frame.deserialize(map));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Animation loadAnimation(String name) {
        File file = getFile(name);
        if (file == null || !file.isFile()) return null;
        Animation result = new Animation();
        result.load(file);
        if (result.frames.isEmpty()) return null;
        result.name = name;
        return result;
    }

    public boolean saveAnimation(String name) {
        File file = getFile(name);
        save(file);
        return true;
    }

    public void apply(Entity e, int frameNo) {
        if (frameNo >= frames.size()) return;
        Frame frame = frames.get(frameNo);
        frame.apply(e);
        if (e instanceof ArmorStand) {
            ArmorStand stand = (ArmorStand)e;
            int motion = frameNo % 20;
            if (motion < 10) {
                int m = motion;
                stand.setRightLegPose(new EulerAngle(1.0 -(double)m / 5.0, 0.0, 0.02));
                stand.setLeftLegPose(new EulerAngle(-1.0 + (double)m / 5.0, 0.0, -0.02));

                stand.setLeftArmPose(new EulerAngle(1.0 -(double)m / 5.0, 0.0, -0.02));
                stand.setRightArmPose(new EulerAngle(-1.0 + (double)m / 5.0, 0.0, 0.02));
            } else {
                int m = motion - 10;
                stand.setLeftLegPose(new EulerAngle(1.0 -(double)m / 5.0, 0.0, -0.02));
                stand.setRightLegPose(new EulerAngle(-1.0 + (double)m / 5.0, 0.0, 0.02));

                stand.setRightArmPose(new EulerAngle(1.0 -(double)m / 5.0, 0.0, 0.02));
                stand.setLeftArmPose(new EulerAngle(-1.0 + (double)m / 5.0, 0.0, -0.02));
            }
        }
    }
}
