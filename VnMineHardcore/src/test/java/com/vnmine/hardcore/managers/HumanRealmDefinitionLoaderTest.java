package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HumanRealmDefinitionLoaderTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void bundledHumanRealmDefinitionIsValid() throws Exception {
        File file = resourceFile();
        var definition = HumanRealmDefinitionLoader.load(file, Logger.getAnonymousLogger()).orElseThrow();

        assertEquals("human-realm", definition.id());
        assertEquals("Nhân giới", definition.displayName());
        assertEquals("world", definition.worldName());
        assertEquals("world", definition.fallbackWorldName());
        assertEquals("default", definition.access().type());
        assertTrue(MercenaryManager.isRealmAccessAllowed(definition.access(), false, false));
        assertTrue(definition.spawn().useWorldSpawn());
        assertTrue(definition.safeZone().enabled());
        assertEquals(32, definition.safeZone().radius());
    }

    @Test
    public void bundledYeuRealmUsesCompletedFirstStepsAccessCondition() throws Exception {
        var definition = HumanRealmDefinitionLoader.load(
            resourceFile("worlds/yeu-realm.yml"), Logger.getAnonymousLogger()).orElseThrow();

        assertEquals("yeu-realm", definition.id());
        assertEquals("Yêu giới", definition.displayName());
        assertEquals("yeu_realm", definition.worldName());
        assertEquals("world", definition.fallbackWorldName());
        assertEquals("completed-quest", definition.access().type());
        assertEquals("first-steps", definition.access().questId());
        assertFalse(definition.safeZone().enabled());

        assertFalse(MercenaryManager.isRealmAccessAllowed(definition.access(), false, true));
        assertFalse(MercenaryManager.isRealmAccessAllowed(definition.access(), true, false));
        assertTrue(MercenaryManager.isRealmAccessAllowed(definition.access(), true, true));
        var lockedLore = MercenaryManager.buildRealmMapLore(definition, false, false, false, false, false);
        var eligibleLore = MercenaryManager.buildRealmMapLore(definition, true, false, true, true, true);
        var unlockedLore = MercenaryManager.buildRealmMapLore(definition, true, true, true, true, true);
        assertTrue(lockedLore.contains("§cTrạng thái: §fĐang khóa"));
        assertTrue(lockedLore.contains("§7Điều kiện: §fHoàn thành nhiệm vụ first-steps"));
        assertTrue(lockedLore.contains("§cSpawn: world chưa được nạp hoặc không hợp lệ"));
        assertTrue(eligibleLore.contains("§eTrạng thái: §fĐã đủ điều kiện"));
        assertTrue(unlockedLore.contains("§aTrạng thái: §fĐã mở"));
        assertTrue(unlockedLore.contains("§eSpawn: đang dùng world dự phòng"));
    }

    @Test
    public void invalidSafeZoneRadiusRejectsDefinition() throws Exception {
        File file = copyDefinition("invalid-radius.yml");
        replace(file, "radius: 32", "radius: 0");

        assertTrue(HumanRealmDefinitionLoader.load(file, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void unsupportedRealmAccessTypeRejectsDefinition() throws Exception {
        File file = copyDefinition("invalid-access.yml");
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(file);
        ConfigurationSection access = configuration.getConfigurationSection("access");
        if (access == null) access = configuration.createSection("access");
        access.set("type", "unknown");
        configuration.save(file);

        assertTrue(HumanRealmDefinitionLoader.load(file, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void legacyRealmDefinitionWithoutAccessDefaultsToOpen() throws Exception {
        File file = copyDefinition("legacy-realm.yml");
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.load(file);
        configuration.set("access", null);
        configuration.save(file);

        var definition = HumanRealmDefinitionLoader.load(file, Logger.getAnonymousLogger()).orElseThrow();

        assertEquals("default", definition.access().type());
        assertEquals(null, definition.access().questId());
    }

    @Test
    public void customSpawnRequiresAllCoordinates() throws Exception {
        File file = copyDefinition("missing-coordinate.yml");
        replace(file, "use-world-spawn: true", "use-world-spawn: false");

        assertTrue(HumanRealmDefinitionLoader.load(file, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void duplicateKeysRejectDefinition() throws Exception {
        File file = copyDefinition("duplicate-key.yml");
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write("\nworld-name: duplicated\n");
        }

        assertTrue(HumanRealmDefinitionLoader.load(file, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void safeZoneUsesCircularHorizontalBoundary() {
        assertTrue(RpgWorldManager.isWithinRadius(0, 0, 32, 0, 32));
        assertFalse(RpgWorldManager.isWithinRadius(0, 0, 32, 1, 32));
    }

    @Test
    public void humanRealmMapLoreShowsDefaultUnlockAndWorldFallbackState() throws Exception {
        var definition = HumanRealmDefinitionLoader.load(resourceFile(), Logger.getAnonymousLogger())
            .orElseThrow();

        var lore = MercenaryManager.buildRealmMapLore(definition, true, true, false, true, false);

        assertTrue(lore.contains("§aTrạng thái: §fĐã mở"));
        assertTrue(lore.contains("§7Điều kiện: §fĐại lục khởi đầu"));
        assertTrue(lore.contains("§7ID: §f" + definition.id()));
        assertTrue(lore.contains("§7World: §f" + definition.worldName()));
        assertTrue(lore.contains("§cSpawn: world chưa được nạp hoặc không hợp lệ"));
        assertTrue(lore.contains("§eTạo nhân vật để lưu tiến trình cá nhân"));
    }

    private File resourceFile() throws Exception {
        return resourceFile("worlds/human-realm.yml");
    }

    private File resourceFile(String name) throws Exception {
        var resource = getClass().getClassLoader().getResource(name);
        if (resource == null) throw new IllegalStateException("Bundled human realm definition is missing.");
        return Path.of(resource.toURI()).toFile();
    }

    private File copyDefinition(String name) throws Exception {
        File copy = temporaryFolder.newFile(name);
        try (var input = getClass().getClassLoader().getResourceAsStream("worlds/human-realm.yml")) {
            if (input == null) throw new IllegalStateException("Bundled human realm definition is missing.");
            java.nio.file.Files.copy(input, copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return copy;
    }

    private void replace(File file, String oldValue, String newValue) throws Exception {
        String contents = java.nio.file.Files.readString(file.toPath());
        java.nio.file.Files.writeString(file.toPath(), contents.replace(oldValue, newValue));
    }
}
