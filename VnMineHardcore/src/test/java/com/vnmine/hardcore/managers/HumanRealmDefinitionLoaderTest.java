package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.logging.Logger;
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
        assertTrue(definition.spawn().useWorldSpawn());
        assertTrue(definition.safeZone().enabled());
        assertEquals(32, definition.safeZone().radius());
    }

    @Test
    public void invalidSafeZoneRadiusRejectsDefinition() throws Exception {
        File file = copyDefinition("invalid-radius.yml");
        replace(file, "radius: 32", "radius: 0");

        assertTrue(HumanRealmDefinitionLoader.load(file, Logger.getAnonymousLogger()).isEmpty());
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

    private File resourceFile() throws Exception {
        var resource = getClass().getClassLoader().getResource("worlds/human-realm.yml");
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
