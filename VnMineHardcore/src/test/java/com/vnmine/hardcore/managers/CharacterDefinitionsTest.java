package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

public class CharacterDefinitionsTest {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    @Test
    public void bundledRaceAndClassDefinitionsAreCompleteAndLinked() throws Exception {
        File racesFile = resourceFile("races.yml");
        File classesFile = resourceFile("player-classes.yml");
        YamlDuplicateKeyValidator.validateNoDuplicateKeys(racesFile);
        YamlDuplicateKeyValidator.validateNoDuplicateKeys(classesFile);

        YamlConfiguration racesConfig = YamlConfiguration.loadConfiguration(racesFile);
        YamlConfiguration classesConfig = YamlConfiguration.loadConfiguration(classesFile);
        YamlConfiguration pluginConfig = YamlConfiguration.loadConfiguration(resourceFile("config.yml"));
        ConfigurationSection races = racesConfig.getConfigurationSection("races");
        ConfigurationSection classes = classesConfig.getConfigurationSection("classes");

        assertTrue(pluginConfig.getBoolean("rpg.character-creation.enabled"));
        assertNotNull(races);
        assertNotNull(classes);
        assertEquals(5, races.getKeys(false).size());
        assertEquals(15, classes.getKeys(false).size());
        Set<String> raceIds = races.getKeys(false);

        for (String raceId : raceIds) {
            assertTrue(ID_PATTERN.matcher(raceId).matches());
            ConfigurationSection race = races.getConfigurationSection(raceId);
            assertNotNull(race);
            assertFalse(race.getString("name", "").isBlank());
            assertFalse(race.getString("description", "").isBlank());
            assertNotNull(Material.matchMaterial(race.getString("icon", "")));
        }

        for (String classId : classes.getKeys(false)) {
            assertTrue(ID_PATTERN.matcher(classId).matches());
            ConfigurationSection definition = classes.getConfigurationSection(classId);
            assertNotNull(definition);
            assertTrue(raceIds.contains(definition.getString("race")));
            assertFalse(definition.getString("name", "").isBlank());
            assertFalse(definition.getString("description", "").isBlank());
            assertNotNull(Material.matchMaterial(definition.getString("icon", "")));
        }
    }

    private File resourceFile(String name) throws Exception {
        var resource = getClass().getClassLoader().getResource(name);
        assertNotNull(resource);
        return Path.of(resource.toURI()).toFile();
    }
}
