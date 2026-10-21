package com.vnmine.hardcore.managers;

import com.vnmine.hardcore.VnMineHardcore;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.yaml.snakeyaml.error.YAMLException;

public final class CharacterSelectionManager implements Listener {
    private static final Pattern DEFINITION_ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final int[] MENU_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final VnMineHardcore plugin;
    private final Map<String, RaceDefinition> races = new LinkedHashMap<>();
    private final Map<String, List<ClassDefinition>> classesByRace = new HashMap<>();
    private boolean definitionsAvailable;

    public CharacterSelectionManager(VnMineHardcore plugin) {
        this.plugin = plugin;
        try {
            loadDefinitions();
            definitionsAvailable = true;
        } catch (IllegalStateException exception) {
            races.clear();
            classesByRace.clear();
            plugin.getLogger().log(Level.SEVERE,
                "[Character] Character creation is disabled because its race/class configuration is invalid.",
                exception);
        }
    }

    public void openRaceMenu(Player player) {
        if (!isCreationAvailable(player)) return;
        if (hasExistingProfile(player)) return;
        CharacterMenuHolder holder = new CharacterMenuHolder(player.getUniqueId(), "race", null, null, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Chọn chủng tộc");
        holder.inventory = inventory;
        int index = 0;
        for (RaceDefinition race : races.values()) {
            if (index >= MENU_SLOTS.length) break;
            int slot = MENU_SLOTS[index++];
            holder.choices.put(slot, race.id());
            inventory.setItem(slot, createRaceItem(race));
        }
        inventory.setItem(49, createActionItem(Material.BARRIER, "§cĐóng", List.of("§7Chưa có thay đổi nào được lưu.")));
        player.openInventory(inventory);
    }

    public void openClassMenu(Player player, String raceId) {
        if (!isCreationAvailable(player)) return;
        if (hasExistingProfile(player)) return;
        RaceDefinition race = races.get(raceId);
        if (race == null) {
            player.sendMessage("§cChủng tộc không hợp lệ.");
            return;
        }

        CharacterMenuHolder holder = new CharacterMenuHolder(
            player.getUniqueId(), "class", raceId, null, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 54, "VnMine • Chọn class cho " + race.name());
        holder.inventory = inventory;
        List<ClassDefinition> classList = classesByRace.getOrDefault(raceId, Collections.emptyList());
        for (int index = 0; index < classList.size() && index < MENU_SLOTS.length; index++) {
            ClassDefinition klass = classList.get(index);
            int slot = MENU_SLOTS[index];
            holder.choices.put(slot, klass.id());
            inventory.setItem(slot, createClassItem(race, klass));
        }
        inventory.setItem(49, createActionItem(Material.ARROW, "§7Chọn lại chủng tộc",
            List.of("§7Quay lại màn hình chủng tộc.")));
        player.openInventory(inventory);
    }

    private void openConfirmationMenu(Player player, String raceId, String classId) {
        if (!isCreationAvailable(player)) return;
        RaceDefinition race = races.get(raceId);
        ClassDefinition selectedClass = findClass(raceId, classId);
        if (race == null || selectedClass == null) {
            player.sendMessage("§cKhông tìm thấy lựa chọn race/class hợp lệ.");
            return;
        }
        CharacterMenuHolder holder = new CharacterMenuHolder(
            player.getUniqueId(), "confirm", raceId, classId, new HashMap<>());
        Inventory inventory = Bukkit.createInventory(holder, 27, "VnMine • Xác nhận nhân vật");
        holder.inventory = inventory;
        inventory.setItem(11, createActionItem(Material.LIME_CONCRETE, "§aXác nhận tạo nhân vật", List.of(
            "§7Chủng tộc: §6" + race.name(),
            "§7Class: §b" + selectedClass.name(),
            "§eMỗi tài khoản chỉ tạo được một nhân vật.")));
        inventory.setItem(13, createActionItem(Material.BOOK, "§fXem lại lựa chọn", List.of(
            "§7" + race.description(),
            "§7" + selectedClass.description())));
        inventory.setItem(15, createActionItem(Material.RED_CONCRETE, "§cHủy", List.of(
            "§7Quay lại chọn class.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onCharacterMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CharacterMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!holder.viewer.equals(player.getUniqueId())) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if ("confirm".equals(holder.type)) {
            if (slot == 11) {
                finalizeSelection(player, holder.raceId, holder.classId);
            } else if (slot == 15) {
                openClassMenu(player, holder.raceId);
            }
            return;
        }

        if (slot == 49) {
            if ("class".equals(holder.type)) {
                openRaceMenu(player);
            } else {
                player.closeInventory();
            }
            return;
        }

        String choice = holder.choices.get(slot);
        if (choice == null) {
            return;
        }

        if ("race".equals(holder.type)) {
            openClassMenu(player, choice);
            return;
        }

        if ("class".equals(holder.type)) {
            openConfirmationMenu(player, holder.raceId, choice);
        }
    }

    public void finalizeSelection(Player player, String raceId, String classId) {
        if (!isCreationAvailable(player)) return;
        RaceDefinition race = races.get(raceId);
        ClassDefinition selectedClass = findClass(raceId, classId);
        if (race == null || selectedClass == null) {
            player.sendMessage("§cBạn chưa chọn đầy đủ chủng tộc và class hợp lệ.");
            return;
        }
        if (plugin.getPlayerProfileDatabaseManager() == null) {
            player.sendMessage("§cHệ thống hồ sơ chưa sẵn sàng.");
            return;
        }
        try {
            String now = Instant.now().toString();
            boolean created = plugin.getPlayerProfileDatabaseManager().createPlayerProfile(
                player.getUniqueId().toString(),
                player.getName(),
                raceId,
                classId,
                1,
                0,
                null,
                now
            );
            if (created) {
                player.sendMessage("§aĐã tạo nhân vật: §6" + race.name() + " §7- §b" + selectedClass.name());
                player.closeInventory();
            } else {
                player.sendMessage("§cTài khoản của bạn đã có nhân vật. Không có dữ liệu nào bị ghi đè.");
                player.closeInventory();
            }
        } catch (SQLException exception) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[Profile] Failed to save character selection for " + player.getName(), exception);
            player.sendMessage("§cKhông thể lưu hồ sơ nhân vật. Vui lòng thử lại sau.");
        }
    }

    private boolean isCreationAvailable(Player player) {
        if (!plugin.getConfig().getBoolean("rpg.character-creation.enabled", true)) {
            player.sendMessage("§cTính năng tạo nhân vật RPG hiện đang tắt.");
            return false;
        }
        if (!definitionsAvailable) {
            player.sendMessage("§cTính năng tạo nhân vật tạm thời không khả dụng. Hãy báo quản trị viên.");
            return false;
        }
        return true;
    }

    private boolean hasExistingProfile(Player player) {
        if (plugin.getPlayerProfileDatabaseManager() == null) {
            player.sendMessage("§cHệ thống hồ sơ chưa sẵn sàng.");
            return true;
        }
        try {
            if (plugin.getPlayerProfileDatabaseManager().hasPlayerProfile(player.getUniqueId().toString())) {
                player.sendMessage("§cTài khoản của bạn đã có nhân vật. Chức năng nhiều nhân vật chưa được hỗ trợ.");
                return true;
            }
            return false;
        } catch (SQLException exception) {
            plugin.getLogger().log(Level.SEVERE, "[Profile] Failed to check existing profile for " +
                player.getName(), exception);
            player.sendMessage("§cKhông thể kiểm tra hồ sơ nhân vật. Vui lòng thử lại sau.");
            return true;
        }
    }

    private void loadDefinitions() {
        File raceFile = loadBundledConfig("races.yml");
        File classFile = loadBundledConfig("player-classes.yml");
        YamlConfiguration raceConfig = loadStrictConfig(raceFile);
        YamlConfiguration classConfig = loadStrictConfig(classFile);
        ConfigurationSection raceSection = raceConfig.getConfigurationSection("races");
        ConfigurationSection classSection = classConfig.getConfigurationSection("classes");
        if (raceSection == null || classSection == null) {
            throw new IllegalStateException("Both races.yml:races and player-classes.yml:classes are required.");
        }

        for (String id : raceSection.getKeys(false)) {
            ConfigurationSection section = raceSection.getConfigurationSection(id);
            if (section == null || !DEFINITION_ID_PATTERN.matcher(id).matches()) {
                plugin.getLogger().warning("[Character] Invalid race definition at races.yml:races." + id + ".");
                continue;
            }
            String name = readText(section, "name", "races.yml:races." + id);
            String description = readText(section, "description", "races.yml:races." + id);
            Material icon = readMaterial(section, "icon", "races.yml:races." + id);
            if (name != null && description != null && icon != null) {
                races.put(id, new RaceDefinition(id, name, icon, description));
                classesByRace.put(id, new java.util.ArrayList<>());
            }
        }

        for (String id : classSection.getKeys(false)) {
            ConfigurationSection section = classSection.getConfigurationSection(id);
            if (section == null || !DEFINITION_ID_PATTERN.matcher(id).matches()) {
                plugin.getLogger().warning("[Character] Invalid class definition at player-classes.yml:classes." + id + ".");
                continue;
            }
            String path = "player-classes.yml:classes." + id;
            String raceId = readText(section, "race", path);
            String name = readText(section, "name", path);
            String description = readText(section, "description", path);
            Material icon = readMaterial(section, "icon", path);
            if (raceId == null || name == null || description == null || icon == null) continue;
            List<ClassDefinition> classList = classesByRace.get(raceId);
            if (classList == null) {
                plugin.getLogger().warning("[Character] Unknown race '" + raceId + "' at " + path + ".race.");
                continue;
            }
            classList.add(new ClassDefinition(raceId, id, name, icon, description));
        }

        races.entrySet().removeIf(entry -> {
            List<ClassDefinition> classList = classesByRace.get(entry.getKey());
            if (classList != null && !classList.isEmpty()) return false;
            plugin.getLogger().warning("[Character] Skipping race '" + entry.getKey() + "' because it has no valid classes.");
            classesByRace.remove(entry.getKey());
            return true;
        });
        if (races.isEmpty()) {
            throw new IllegalStateException("No valid race/class definitions were loaded.");
        }
        for (Map.Entry<String, List<ClassDefinition>> entry : classesByRace.entrySet()) {
            if (entry.getValue().size() > MENU_SLOTS.length) {
                throw new IllegalStateException("Too many classes for race '" + entry.getKey() +
                    "'; maximum supported in the selection menu is " + MENU_SLOTS.length + ".");
            }
        }
        if (races.size() > MENU_SLOTS.length) {
            throw new IllegalStateException("Too many race definitions; maximum supported in the selection menu is " +
                MENU_SLOTS.length + ".");
        }
    }

    private File loadBundledConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return file;
    }

    private YamlConfiguration loadStrictConfig(File file) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            YamlDuplicateKeyValidator.validateNoDuplicateKeys(file);
            configuration.load(file);
            return configuration;
        } catch (IOException | InvalidConfigurationException | YAMLException exception) {
            throw new IllegalStateException("Cannot load character definitions from " + file.getName() + ".", exception);
        }
    }

    private String readText(ConfigurationSection section, String key, String path) {
        Object value = section.get(key);
        if (value instanceof String text && !text.isBlank()) return text;
        plugin.getLogger().warning("[Character] Missing or invalid text at " + path + "." + key + ".");
        return null;
    }

    private Material readMaterial(ConfigurationSection section, String key, String path) {
        String value = readText(section, key, path);
        Material material = value == null ? null : Material.matchMaterial(value);
        if (material == null || !material.isItem()) {
            plugin.getLogger().warning("[Character] Missing or invalid Material at " + path + "." + key + ".");
            return null;
        }
        return material;
    }

    private ClassDefinition findClass(String raceId, String classId) {
        return classesByRace.getOrDefault(raceId, Collections.emptyList()).stream()
            .filter(klass -> klass.id().equals(classId))
            .findFirst()
            .orElse(null);
    }

    private ItemStack createRaceItem(RaceDefinition race) {
        return createSelectionItem(race.material(), "§6" + race.name(), List.of(
            "§7" + race.description(),
            "§8ID: " + race.id(),
            "§aNhấn để chọn chủng tộc này."));
    }

    private ItemStack createClassItem(RaceDefinition race, ClassDefinition klass) {
        return createSelectionItem(klass.material(), "§b" + klass.name(), List.of(
            "§7" + klass.description(),
            "§8" + race.name() + " • " + klass.id(),
            "§aNhấn để xem trước và xác nhận."));
    }

    private ItemStack createActionItem(Material material, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSelectionItem(Material material, String title, List<String> lore) {
        return createActionItem(material, title, lore);
    }

    public record RaceDefinition(String id, String name, Material material, String description) {
    }

    public record ClassDefinition(String raceId, String id, String name, Material material, String description) {
    }

    private static final class CharacterMenuHolder implements InventoryHolder {
        private final UUID viewer;
        private final String type;
        private final String raceId;
        private final String classId;
        private final Map<Integer, String> choices;
        private Inventory inventory;

        private CharacterMenuHolder(UUID viewer, String type, String raceId, String classId,
                                    Map<Integer, String> choices) {
            this.viewer = viewer;
            this.type = type;
            this.raceId = raceId;
            this.classId = classId;
            this.choices = choices;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
