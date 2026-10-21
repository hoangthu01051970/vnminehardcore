package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public final class SpawnProtectionManager implements Listener {
    public record Protection(int id, String world, int centerX, int centerZ, int radius) {}

    public enum RemoveResult {
        REMOVED,
        NOT_FOUND,
        SAVE_FAILED
    }

    private final VnMineHardcore plugin;
    private final File dataFile;
    private final Map<Integer, Protection> protections = new LinkedHashMap<>();
    private int nextId = 1;

    public SpawnProtectionManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "spawns-protection.yml");
        load();
    }

    public Integer addProtection(Location center, int radius) {
        if (nextId <= 0 || nextId == Integer.MAX_VALUE) {
            plugin.getLogger().warning("[SpawnProtection] No more protection IDs are available.");
            return null;
        }

        int id = nextId++;
        Protection protection = new Protection(
            id, center.getWorld().getName(), center.getBlockX(), center.getBlockZ(), radius
        );
        protections.put(id, protection);
        if (!save()) {
            protections.remove(id);
            nextId--;
            return null;
        }
        return id;
    }

    public RemoveResult removeProtection(int id) {
        Protection removed = protections.remove(id);
        if (removed == null) return RemoveResult.NOT_FOUND;
        if (!save()) {
            protections.put(id, removed);
            return RemoveResult.SAVE_FAILED;
        }
        return RemoveResult.REMOVED;
    }

    public List<Protection> getProtections() {
        return protections.values().stream()
            .sorted(Comparator.comparingInt(Protection::id))
            .toList();
    }

    public List<Integer> getProtectionIds() {
        return getProtections().stream().map(Protection::id).toList();
    }

    public boolean isProtected(Location location) {
        if (location.getWorld() == null) return false;
        if (plugin.getRpgWorldManager() != null && plugin.getRpgWorldManager().isInSafeZone(location)) return true;
        for (Protection protection : protections.values()) {
            if (!protection.world().equalsIgnoreCase(location.getWorld().getName())) continue;
            double dx = location.getBlockX() - (double) protection.centerX();
            double dz = location.getBlockZ() - (double) protection.centerZ();
            if (dx * dx + dz * dz <= (double) protection.radius() * protection.radius()) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isProtected(event.getBlock().getLocation()) || canBypass(event.getPlayer())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cBạn không thể phá block trong khu vực spawn được bảo vệ.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isProtected(event.getBlockPlaced().getLocation()) || canBypass(event.getPlayer())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage("§cBạn không thể đặt block trong khu vực spawn được bảo vệ.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> isProtected(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBurn(BlockBurnEvent event) {
        if (isProtected(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        if (isProtected(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isProtected(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockFlow(BlockFromToEvent event) {
        if (isProtected(event.getToBlock().getLocation())) event.setCancelled(true);
    }

    private boolean canBypass(Player player) {
        return plugin.getPermissionManager().isProtectionBypass(player);
    }

    private void load() {
        if (!dataFile.isFile()) return;

        YamlConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection entries = data.getConfigurationSection("protections");
        if (entries != null) {
            for (String key : entries.getKeys(false)) {
                String path = "protections." + key;
                int id;
                try {
                    id = Integer.parseInt(key);
                } catch (NumberFormatException exception) {
                    plugin.getLogger().warning("[SpawnProtection] Skipping invalid protection ID '" + key + "'.");
                    continue;
                }

                String world = data.getString(path + ".world");
                if (id < 1 || world == null || world.isBlank()
                        || !data.isInt(path + ".center-x")
                        || !data.isInt(path + ".center-z")
                        || !data.isInt(path + ".radius")
                        || data.getInt(path + ".radius") < 1) {
                    plugin.getLogger().warning("[SpawnProtection] Skipping invalid protection entry '" + key + "'.");
                    continue;
                }

                protections.put(id, new Protection(
                    id, world, data.getInt(path + ".center-x"),
                    data.getInt(path + ".center-z"), data.getInt(path + ".radius")
                ));
            }
        }

        int greatestId = protections.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        int minimumNextId = greatestId == Integer.MAX_VALUE ? Integer.MAX_VALUE : greatestId + 1;
        nextId = Math.max(data.getInt("next-id", 1), minimumNextId);
        if (nextId < 1) nextId = minimumNextId;
        plugin.getLogger().info("[SpawnProtection] Loaded " + protections.size() + " protected area(s).");
    }

    private boolean save() {
        YamlConfiguration data = new YamlConfiguration();
        data.set("next-id", nextId);
        for (Protection protection : protections.values()) {
            String path = "protections." + protection.id();
            data.set(path + ".world", protection.world());
            data.set(path + ".center-x", protection.centerX());
            data.set(path + ".center-z", protection.centerZ());
            data.set(path + ".radius", protection.radius());
        }

        if (!plugin.getDataFolder().isDirectory() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("[SpawnProtection] Could not create plugin data directory.");
            return false;
        }
        try {
            data.save(dataFile);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING,
                "[SpawnProtection] Could not save " + dataFile.getName() + ".", exception);
            return false;
        }
    }
}
