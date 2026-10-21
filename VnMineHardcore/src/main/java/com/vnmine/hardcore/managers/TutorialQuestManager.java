package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TutorialQuestManager implements Listener {
    private final VnMineHardcore plugin;
    private final File definitionFile;
    private final Map<UUID, Boolean> activeQuestSessions = new HashMap<>();
    private TutorialQuestDefinitionLoader.TutorialQuestDefinition definition;

    public TutorialQuestManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        File questsFolder = new File(plugin.getDataFolder(), "quests/tutorial");
        if (!questsFolder.isDirectory() && !questsFolder.mkdirs()) {
            plugin.getLogger().severe("[Quest] Could not create the tutorial quest configuration directory.");
            definitionFile = new File(questsFolder, "first-steps.yml");
            return;
        }

        definitionFile = new File(questsFolder, "first-steps.yml");
        if (!definitionFile.isFile()) {
            try {
                plugin.saveResource("quests/tutorial/first-steps.yml", false);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().log(Level.SEVERE, "[Quest] Bundled first-steps.yml resource is missing.", exception);
                return;
            }
        }
        reload();
    }

    public boolean reload() {
        Optional<TutorialQuestDefinitionLoader.TutorialQuestDefinition> loaded =
            TutorialQuestDefinitionLoader.load(definitionFile, plugin.getLogger());
        if (loaded.isEmpty()) return false;

        definition = loaded.get();
        for (Player player : Bukkit.getOnlinePlayers()) {
            initializePlayer(player, false);
        }
        return true;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        initializePlayer(event.getPlayer(), true);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeQuestSessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || isSameBlock(from, to)) return;

        UUID playerId = event.getPlayer().getUniqueId();
        Boolean wasInside = activeQuestSessions.get(playerId);
        if (wasInside == null) return;

        boolean isInside = isAtObjective(to);
        activeQuestSessions.put(playerId, isInside);
        if (isInside && !wasInside) {
            completeObjective(event.getPlayer());
        }
    }

    public void initializePlayer(Player player) {
        initializePlayer(player, true);
    }

    private void initializePlayer(Player player, boolean showReminder) {
        if (definition == null) return;
        if (!definition.enabled()) {
            activeQuestSessions.remove(player.getUniqueId());
            return;
        }
        UUID playerId = player.getUniqueId();
        activeQuestSessions.remove(playerId);
        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        if (database == null) {
            plugin.getLogger().severe("[Quest] Cannot initialize tutorial progress because the profile database is unavailable.");
            player.sendMessage("§cKhông thể tải nhiệm vụ hướng dẫn lúc này. Tiến trình của bạn vẫn được giữ.");
            return;
        }

        try {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> profile =
                database.getPlayerProfile(playerId.toString());
            if (profile.isEmpty() || !profile.get().active()) return;

            Optional<PlayerProfileDatabaseManager.QuestProgress> progress =
                database.getQuestProgress(playerId.toString(), definition.id());
            boolean startedNow = false;
            if (progress.isEmpty()) {
                String startedAt = Instant.now().toString();
                if (database.startQuestProgress(playerId.toString(), definition.id(),
                        definition.objective().id(), startedAt)) {
                    startedNow = true;
                    progress = Optional.of(new PlayerProfileDatabaseManager.QuestProgress(
                        "active", definition.objective().id(), startedAt, null, false));
                } else {
                    progress = database.getQuestProgress(playerId.toString(), definition.id());
                }
            }

            if (progress.isEmpty() || !"active".equals(progress.get().status())) return;
            if (!definition.objective().id().equals(progress.get().currentStep())) {
                plugin.getLogger().warning("[Quest] Active tutorial progress has an unknown objective ID for " +
                    player.getName() + "; saved progress was preserved.");
                return;
            }
            if (startedNow || showReminder) {
                player.sendMessage("§6§l" + definition.title());
                player.sendMessage("§7" + definition.description());
                player.sendMessage("§e" + definition.objective().startMessage());
            }

            boolean alreadyAtTarget = isAtObjective(player.getLocation());
            activeQuestSessions.put(playerId, alreadyAtTarget);
            if (alreadyAtTarget) completeObjective(player);
        } catch (java.sql.SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Quest] Failed to initialize tutorial progress for " +
                player.getName(), exception);
            player.sendMessage("§cKhông thể lưu/tải tiến trình nhiệm vụ. Dữ liệu của bạn vẫn được giữ.");
        }
    }

    static boolean isWithinObjectiveRadius(double centerX, double centerZ, double x, double z, int radius) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    private boolean isAtObjective(Location location) {
        if (definition == null || location.getWorld() == null
                || !definition.objective().realmId().equals("human-realm")) {
            return false;
        }
        Optional<RpgWorldManager.ResolvedSpawn> target =
            plugin.getRpgWorldManager().resolveHumanRealmSpawn();
        if (target.isEmpty()) return false;

        Location center = target.get().location();
        return location.getWorld() == center.getWorld()
            && isWithinObjectiveRadius(center.getX(), center.getZ(), location.getX(), location.getZ(),
                definition.objective().radiusBlocks());
    }

    private void completeObjective(Player player) {
        if (definition == null) return;
        UUID playerId = player.getUniqueId();
        try {
            boolean completed = plugin.getPlayerProfileDatabaseManager().completeQuest(
                playerId.toString(), definition.id(), "completed", Instant.now().toString());
            if (completed) {
                activeQuestSessions.remove(playerId);
                player.sendMessage("§a§lNhiệm vụ hoàn thành: §f" + definition.title());
                player.sendMessage("§7" + definition.objective().completeMessage());
                return;
            }

            Optional<PlayerProfileDatabaseManager.QuestProgress> progress =
                plugin.getPlayerProfileDatabaseManager().getQuestProgress(playerId.toString(), definition.id());
            if (progress.isPresent() && "completed".equals(progress.get().status())) {
                activeQuestSessions.remove(playerId);
                return;
            }
            plugin.getLogger().warning("[Quest] Could not complete tutorial objective for " +
                player.getName() + "; the active progress remains available.");
            activeQuestSessions.put(playerId, true);
            player.sendMessage("§cChưa thể lưu tiến trình nhiệm vụ. Hãy rời khu vực rồi quay lại để thử lại.");
        } catch (java.sql.SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Quest] Failed to complete tutorial objective for " +
                player.getName(), exception);
            activeQuestSessions.put(playerId, true);
            player.sendMessage("§cChưa thể lưu tiến trình nhiệm vụ. Hãy rời khu vực rồi quay lại để thử lại.");
        }
    }

    private boolean isSameBlock(Location first, Location second) {
        return first.getWorld() == second.getWorld()
            && first.getBlockX() == second.getBlockX()
            && first.getBlockY() == second.getBlockY()
            && first.getBlockZ() == second.getBlockZ();
    }
}
