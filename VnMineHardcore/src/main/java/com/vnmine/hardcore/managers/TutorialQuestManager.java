package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

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

    public void openJournal(Player player) {
        if (!plugin.getConfig().getBoolean("rpg.quest-journal.enabled", true)) {
            player.sendMessage("§cNhật ký nhiệm vụ hiện đang tắt.");
            return;
        }
        if (definition == null) {
            player.sendMessage("§cNhật ký nhiệm vụ hiện chưa khả dụng. Hãy báo quản trị viên.");
            return;
        }

        PlayerProfileDatabaseManager database = plugin.getPlayerProfileDatabaseManager();
        if (database == null) {
            plugin.getLogger().severe("[Quest] Cannot open journal because the profile database is unavailable.");
            player.sendMessage("§cKhông thể tải nhật ký nhiệm vụ. Dữ liệu của bạn vẫn được giữ.");
            return;
        }
        try {
            Optional<PlayerProfileDatabaseManager.PlayerProfile> profile =
                database.getPlayerProfile(player.getUniqueId().toString());
            boolean hasActiveProfile = profile.isPresent() && profile.get().active();
            Optional<PlayerProfileDatabaseManager.QuestProgress> progress = hasActiveProfile
                ? database.getQuestProgress(player.getUniqueId().toString(), definition.id())
                : Optional.empty();
            openJournalInventory(player, hasActiveProfile, progress);
        } catch (java.sql.SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Quest] Failed to open quest journal for " +
                player.getName(), exception);
            player.sendMessage("§cKhông thể tải nhật ký nhiệm vụ. Dữ liệu của bạn vẫn được giữ.");
        }
    }

    @EventHandler
    public void onQuestJournalClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof QuestJournalHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !holder.viewer.equals(player.getUniqueId())
                || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        switch (event.getRawSlot()) {
            case 11 -> {
                if (!holder.hasActiveProfile && plugin.getCharacterSelectionManager() != null) {
                    plugin.getCharacterSelectionManager().openRaceMenu(player);
                }
            }
            case 15 -> {
                initializePlayer(player);
                openJournal(player);
            }
            case 22 -> {
                if (plugin.getMercenaryManager() != null) {
                    plugin.getMercenaryManager().openMainMenu(player);
                }
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuestJournalDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof QuestJournalHolder) {
            event.setCancelled(true);
        }
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

            if (progress.isEmpty()) return;
            if ("completed".equals(progress.get().status())) {
                if (!progress.get().rewardClaimed()
                        && database.claimCompletedQuestExperienceReward(
                            playerId.toString(), definition.id(), definition.reward().amount(), Instant.now().toString())) {
                    plugin.getCharacterSelectionManager().refreshLoadedProfile(player);
                    player.sendMessage("§aPhần thưởng nhiệm vụ đã được lưu: §e+" +
                        definition.reward().amount() + " EXP§a.");
                }
                return;
            }
            if (!"active".equals(progress.get().status())) return;
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

    static List<String> buildJournalLore(
            TutorialQuestDefinitionLoader.TutorialQuestDefinition quest,
            Optional<PlayerProfileDatabaseManager.QuestProgress> progress) {
        List<String> lore = new ArrayList<>();
        lore.add("§7" + quest.description());
        lore.add("");
        if (progress.isEmpty()) {
            lore.add("§eTrạng thái: §7Chưa bắt đầu");
        } else {
            PlayerProfileDatabaseManager.QuestProgress questProgress = progress.get();
            if ("completed".equals(questProgress.status())) {
                lore.add("§eTrạng thái: §aĐã hoàn thành");
                lore.add("§ePhần thưởng: " + (questProgress.rewardClaimed()
                    ? "§aĐã nhận" : "§cChưa nhận"));
                if (questProgress.completedAt() != null) {
                    lore.add("§eHoàn thành lúc: §f" + questProgress.completedAt());
                }
            } else if ("active".equals(questProgress.status())) {
                lore.add("§eTrạng thái: §6Đang thực hiện");
                lore.add("§eCheckpoint hiện tại: §f" + questProgress.currentStep());
            } else {
                lore.add("§eTrạng thái: §cKhông xác định");
            }
        }
        lore.add("");
        lore.add("§eChỉ dẫn: §f" + quest.objective().startMessage());
        lore.add("§eĐiểm đến: §f" + quest.objective().realmId());
        lore.add("§ePhạm vi: §f" + quest.objective().radiusBlocks() + " block");
        lore.add("§ePhần thưởng dự kiến: §b+" + quest.reward().amount() + " EXP nhân vật");
        return List.copyOf(lore);
    }

    private void openJournalInventory(
            Player player,
            boolean hasActiveProfile,
            Optional<PlayerProfileDatabaseManager.QuestProgress> progress) {
        QuestJournalHolder holder = new QuestJournalHolder(player.getUniqueId(), hasActiveProfile);
        Inventory inventory = Bukkit.createInventory(holder, 27, "VnMine • Nhật ký nhiệm vụ");
        holder.inventory = inventory;

        if (!hasActiveProfile) {
            inventory.setItem(13, createJournalItem(Material.PAPER, "§eChưa có nhân vật RPG", List.of(
                "§7Tạo nhân vật trước để bắt đầu nhiệm vụ tutorial.")));
            inventory.setItem(11, createJournalItem(Material.ENDER_EYE, "§dTạo nhân vật", List.of(
                "§7Mở giao diện chọn chủng tộc và class.")));
        } else if (!definition.enabled()) {
            inventory.setItem(13, createJournalItem(Material.BARRIER, "§cQuest tutorial đang tắt", List.of(
                "§7Quản trị viên có thể bật lại trong first-steps.yml.")));
        } else {
            inventory.setItem(13, createJournalItem(Material.WRITABLE_BOOK, "§6" + definition.title(),
                buildJournalLore(definition, progress)));
            boolean rewardClaimed = progress.isPresent()
                && "completed".equals(progress.get().status())
                && progress.get().rewardClaimed();
            if (rewardClaimed) {
                inventory.setItem(15, createJournalItem(Material.ENCHANTED_BOOK, "§aNhiệm vụ đã hoàn thành",
                    List.of("§7Trạng thái và phần thưởng đã được lưu.")));
            } else {
                inventory.setItem(15, createJournalItem(Material.CLOCK, "§eLàm mới tiến trình", List.of(
                    "§7Tải lại checkpoint và trạng thái đã lưu.",
                    "§7Quest đang hoạt động cũng sẽ được đồng bộ.")));
            }
        }

        inventory.setItem(22, createJournalItem(Material.ARROW, "§7Về menu chính", List.of(
            "§7Quay lại hub RPG.")));
        player.openInventory(inventory);
    }

    private ItemStack createJournalItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
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
            boolean completed = plugin.getPlayerProfileDatabaseManager().completeQuestAndAwardExperience(
                playerId.toString(), definition.id(), "completed", definition.reward().amount(),
                Instant.now().toString());
            if (completed) {
                activeQuestSessions.remove(playerId);
                plugin.getCharacterSelectionManager().refreshLoadedProfile(player);
                player.sendMessage("§a§lNhiệm vụ hoàn thành: §f" + definition.title());
                player.sendMessage("§7" + definition.objective().completeMessage());
                player.sendMessage("§ePhần thưởng: +" + definition.reward().amount() + " EXP nhân vật.");
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

    private static final class QuestJournalHolder implements InventoryHolder {
        private final UUID viewer;
        private final boolean hasActiveProfile;
        private Inventory inventory;

        private QuestJournalHolder(UUID viewer, boolean hasActiveProfile) {
            this.viewer = viewer;
            this.hasActiveProfile = hasActiveProfile;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
