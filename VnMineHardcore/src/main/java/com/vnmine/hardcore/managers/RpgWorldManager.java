package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.util.Optional;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.projectiles.ProjectileSource;

public final class RpgWorldManager implements Listener {
    private final VnMineHardcore plugin;
    private final File definitionFile;
    private HumanRealmDefinitionLoader.HumanRealmDefinition definition;

    public RpgWorldManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        File worldsFolder = new File(plugin.getDataFolder(), "worlds");
        if (!worldsFolder.isDirectory() && !worldsFolder.mkdirs()) {
            plugin.getLogger().severe("[World] Could not create the worlds configuration directory.");
            definitionFile = new File(worldsFolder, "human-realm.yml");
            return;
        }

        definitionFile = new File(worldsFolder, "human-realm.yml");
        if (!definitionFile.isFile()) {
            try {
                plugin.saveResource("worlds/human-realm.yml", false);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().log(Level.SEVERE, "[World] Bundled human-realm.yml resource is missing.", exception);
                return;
            }
        }
        reload();
    }

    public boolean reload() {
        Optional<HumanRealmDefinitionLoader.HumanRealmDefinition> loaded =
            HumanRealmDefinitionLoader.load(definitionFile, plugin.getLogger());
        if (loaded.isEmpty()) return false;

        definition = loaded.get();
        logWorldAvailability();
        return true;
    }

    public Optional<HumanRealmDefinitionLoader.HumanRealmDefinition> getDefinition() {
        return Optional.ofNullable(definition);
    }

    public Optional<ResolvedSpawn> resolveHumanRealmSpawn() {
        if (definition == null) return Optional.empty();

        World realmWorld = Bukkit.getWorld(definition.worldName());
        if (realmWorld != null) {
            return createRealmSpawn(realmWorld, false);
        }

        String fallbackName = definition.fallbackWorldName();
        if (fallbackName.equalsIgnoreCase(definition.worldName())) return Optional.empty();
        World fallbackWorld = Bukkit.getWorld(fallbackName);
        if (fallbackWorld == null) return Optional.empty();
        Location fallbackSpawn = fallbackWorld.getSpawnLocation();
        if (!isValidLocation(fallbackSpawn)) return Optional.empty();
        return Optional.of(new ResolvedSpawn(fallbackSpawn.clone(), true));
    }

    public boolean isInSafeZone(Location location) {
        if (location == null || location.getWorld() == null || definition == null
                || !definition.safeZone().enabled()) {
            return false;
        }
        Optional<ResolvedSpawn> resolvedSpawn = resolveHumanRealmSpawn();
        if (resolvedSpawn.isEmpty()) return false;

        Location center = resolvedSpawn.get().location();
        if (location.getWorld() != center.getWorld()) return false;
        return isWithinRadius(center.getX(), center.getZ(), location.getX(), location.getZ(),
            definition.safeZone().radius());
    }

    static boolean isWithinRadius(double centerX, double centerZ, double x, double z, int radius) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSafeZonePlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isInSafeZone(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSafeZonePlayerAttack(EntityDamageByEntityEvent event) {
        Player attacker = findPlayerAttacker(event.getDamager());
        if (attacker != null && isInSafeZone(attacker.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSafeZoneProjectileLaunch(ProjectileLaunchEvent event) {
        ProjectileSource shooter = event.getEntity().getShooter();
        if (shooter instanceof Player player && isInSafeZone(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSafeZoneHostileSpawn(CreatureSpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Monster && isInSafeZone(entity.getLocation())) {
            event.setCancelled(true);
        }
    }

    private Optional<ResolvedSpawn> createRealmSpawn(World world, boolean fallback) {
        HumanRealmDefinitionLoader.Spawn spawn = definition.spawn();
        Location location = spawn.useWorldSpawn()
            ? world.getSpawnLocation()
            : new Location(world, spawn.x(), spawn.y(), spawn.z(), spawn.yaw(), spawn.pitch());
        location.setYaw(spawn.yaw());
        location.setPitch(spawn.pitch());
        if (!isValidLocation(location)) return Optional.empty();
        return Optional.of(new ResolvedSpawn(location.clone(), fallback));
    }

    private boolean isValidLocation(Location location) {
        World world = location.getWorld();
        return world != null
            && Double.isFinite(location.getX())
            && Double.isFinite(location.getY())
            && Double.isFinite(location.getZ())
            && location.getY() >= world.getMinHeight()
            && location.getY() < world.getMaxHeight()
            && world.getWorldBorder().isInside(location);
    }

    private Player findPlayerAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) return player;
        }
        return null;
    }

    private void logWorldAvailability() {
        if (Bukkit.getWorld(definition.worldName()) != null) {
            plugin.getLogger().info("[World] Human realm '" + definition.id() + "' uses loaded world '" +
                definition.worldName() + "'.");
            if (resolveHumanRealmSpawn().isEmpty()) {
                plugin.getLogger().severe("[World] Human realm spawn is outside the loaded world's valid bounds.");
            }
            return;
        }

        plugin.getLogger().warning("[World] Human realm world '" + definition.worldName() + "' is not loaded.");
        if (!definition.fallbackWorldName().equalsIgnoreCase(definition.worldName())
                && Bukkit.getWorld(definition.fallbackWorldName()) != null
                && resolveHumanRealmSpawn().isPresent()) {
            plugin.getLogger().warning("[World] Using fallback world '" +
                definition.fallbackWorldName() + "' spawn; no world will be created automatically.");
        } else {
            plugin.getLogger().severe("[World] Neither the human realm nor a valid fallback world is available.");
        }
    }

    public record ResolvedSpawn(Location location, boolean fallbackUsed) {
    }
}
