package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

public class NpcManagerTest {
    @Test
    public void acceptsOnlyStableLowercaseNpcIds() {
        assertTrue(NpcManager.isValidId("tutorial-guide"));
        assertTrue(NpcManager.isValidId("npc-7"));
        assertFalse(NpcManager.isValidId("Tutorial-guide"));
        assertFalse(NpcManager.isValidId("tutorial--guide"));
        assertFalse(NpcManager.isValidId("-guide"));
        assertFalse(NpcManager.isValidId("guide-"));
        assertFalse(NpcManager.isValidId("guide_name"));
    }

    @Test
    public void emptyRuntimeStoreRetainsItsNpcSectionAfterYamlRoundTrip()
            throws InvalidConfigurationException {
        YamlConfiguration saved = new YamlConfiguration();
        saved.set("schema-version", 1);
        saved.createSection("npcs");

        YamlConfiguration loaded = new YamlConfiguration();
        loaded.loadFromString(saved.saveToString());

        assertTrue(loaded.getConfigurationSection("npcs") != null);
        assertTrue(loaded.getConfigurationSection("npcs").getKeys(false).isEmpty());
    }

    @Test
    public void dialogueLookupPrefersMatchingNpcIdThenRole() {
        var exact = new NpcDefinitionLoader.NpcDefinition("trainer-one", "trainer", "Trainer One",
            List.of("Exact dialogue"));
        var fallback = new NpcDefinitionLoader.NpcDefinition("class-trainer", "trainer", "Trainer",
            List.of("Role dialogue"));
        Map<String, NpcDefinitionLoader.NpcDefinition> definitions = new LinkedHashMap<>();
        definitions.put(exact.id(), exact);
        definitions.put(fallback.id(), fallback);

        assertSame(exact, NpcManager.findDialogueDefinition("trainer-one", "trainer", definitions));
        assertSame(exact, NpcManager.findDialogueDefinition("another-trainer", "trainer", definitions));
        assertNull(NpcManager.findDialogueDefinition("story-keeper", "story", definitions));
        assertSame(fallback, NpcManager.findDialogueDefinition("trainer-one", "trainer", Map.of(
            "trainer-one", new NpcDefinitionLoader.NpcDefinition("trainer-one", "story",
                "Wrong role", List.of("Do not use")),
            "class-trainer", fallback)));
    }
}
