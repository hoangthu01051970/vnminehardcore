package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
