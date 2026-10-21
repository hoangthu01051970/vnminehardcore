package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

public class YamlConfigMergerTest {
    @Test
    public void addsMissingDefaultsWithoutOverwritingServerValues() throws InvalidConfigurationException {
        YamlConfiguration target = new YamlConfiguration();
        target.loadFromString("""
            title: Server title
            custom:
              retained: server value
              overwritten-by-default: server value
            list:
              - server entry
            expects-section: custom scalar
            """);
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString("""
            title: Default title
            custom:
              retained: default value
              overwritten-by-default: default value
              newly-added: default value
            list:
              - default entry
            expects-section:
              nested: default value
            new-section:
              new-key: default value
            """);

        boolean changed = YamlConfigMerger.mergeMissingKeys(
            target, defaults, Logger.getAnonymousLogger());

        assertTrue(changed);
        assertEquals("Server title", target.getString("title"));
        assertEquals("server value", target.getString("custom.retained"));
        assertEquals("server value", target.getString("custom.overwritten-by-default"));
        assertEquals("default value", target.getString("custom.newly-added"));
        assertEquals(java.util.List.of("server entry"), target.getStringList("list"));
        assertEquals("custom scalar", target.getString("expects-section"));
        assertEquals("default value", target.getString("new-section.new-key"));
    }

    @Test
    public void leavesConfigurationUntouchedWhenNoDefaultsAreMissing() throws InvalidConfigurationException {
        YamlConfiguration target = new YamlConfiguration();
        target.loadFromString("""
            existing: server value
            nested:
              configured: server value
            """);
        YamlConfiguration defaults = new YamlConfiguration();
        defaults.loadFromString("""
            existing: default value
            nested:
              configured: default value
            """);

        boolean changed = YamlConfigMerger.mergeMissingKeys(
            target, defaults, Logger.getAnonymousLogger());

        assertFalse(changed);
        assertEquals("server value", target.getString("existing"));
        assertEquals("server value", target.getString("nested.configured"));
    }
}
