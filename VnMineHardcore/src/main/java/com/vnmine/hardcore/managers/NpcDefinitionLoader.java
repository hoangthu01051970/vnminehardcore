package com.vnmine.hardcore.managers;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.yaml.snakeyaml.error.YAMLException;

public final class NpcDefinitionLoader {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final int MAX_DEFINITIONS = 100;
    private static final int MAX_DIALOGUE_LINES = 20;
    private static final Set<String> SUPPORTED_TYPES = Set.of(
        "tutorial", "quest-giver", "trainer", "merchant", "story", "gatekeeper", "healer");
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    private NpcDefinitionLoader() {
    }

    public static Set<String> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    public static Optional<Map<String, NpcDefinition>> load(File file, Logger logger) {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            YamlDuplicateKeyValidator.validateNoDuplicateKeys(file);
            configuration.load(file);
        } catch (IOException | InvalidConfigurationException | YAMLException exception) {
            logger.log(Level.SEVERE, "[NPC] Could not load " + file.getName() + ".", exception);
            return Optional.empty();
        }

        Object rawSchemaVersion = configuration.get("schema-version");
        int schemaVersion = rawSchemaVersion instanceof Number number ? number.intValue() : -1;
        if (!(rawSchemaVersion instanceof Number number)
                || number.doubleValue() != schemaVersion
                || schemaVersion != SUPPORTED_SCHEMA_VERSION) {
            logger.warning("[NPC] Unsupported or missing schema-version in " + file.getName()
                + "; expected " + SUPPORTED_SCHEMA_VERSION + ".");
            return Optional.empty();
        }

        ConfigurationSection definitionsSection = configuration.getConfigurationSection("npc-definitions");
        if (definitionsSection == null) {
            logger.warning("[NPC] Missing 'npc-definitions' section in " + file.getName() + ".");
            return Optional.empty();
        }
        if (definitionsSection.getKeys(false).size() > MAX_DEFINITIONS) {
            logger.warning("[NPC] " + file.getName() + " exceeds the limit of "
                + MAX_DEFINITIONS + " NPC definitions.");
            return Optional.empty();
        }

        Map<String, NpcDefinition> definitions = new LinkedHashMap<>();
        boolean valid = true;
        for (String id : definitionsSection.getKeys(false)) {
            String path = file.getName() + ":npc-definitions." + id;
            ConfigurationSection section = definitionsSection.getConfigurationSection(id);
            if (section == null) {
                logger.warning("[NPC] Expected a section at " + path + ".");
                valid = false;
                continue;
            }
            boolean definitionValid = true;
            if (!ID_PATTERN.matcher(id).matches()) {
                logger.warning("[NPC] Invalid NPC definition ID at " + path
                    + "; use lowercase letters, numbers, and single hyphens between segments.");
                definitionValid = false;
            }

            String type = readRequiredText(section, "type", path, logger);
            if (type == null) definitionValid = false;
            if (type != null && !SUPPORTED_TYPES.contains(type)) {
                logger.warning("[NPC] Unsupported NPC type '" + type + "' at " + path
                    + ".type; supported types: " + String.join(", ", SUPPORTED_TYPES) + ".");
                definitionValid = false;
            }
            String displayName = readRequiredText(section, "display-name", path, logger);
            if (displayName == null) definitionValid = false;
            if (displayName != null && displayName.length() > 64) {
                logger.warning("[NPC] display-name at " + path + " must be at most 64 characters.");
                definitionValid = false;
            }
            List<String> dialogue = readDialogue(section, path, logger);
            if (dialogue == null) definitionValid = false;

            if (definitionValid) {
                definitions.put(id, new NpcDefinition(id, type, displayName, dialogue));
            } else {
                valid = false;
            }
        }
        if (!valid) return Optional.empty();
        logger.info("[NPC] Loaded " + definitions.size() + " NPC definitions from " + file.getName() + ".");
        return Optional.of(Collections.unmodifiableMap(definitions));
    }

    private static String readRequiredText(ConfigurationSection section, String key, String path, Logger logger) {
        Object value = section.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            logger.warning("[NPC] Missing or invalid text at " + path + "." + key + ".");
            return null;
        }
        return text.trim();
    }

    private static List<String> readDialogue(ConfigurationSection section, String path, Logger logger) {
        Object value = section.get("dialogue");
        if (!(value instanceof List<?> lines) || lines.isEmpty() || lines.size() > MAX_DIALOGUE_LINES) {
            logger.warning("[NPC] dialogue at " + path + " must contain 1 to "
                + MAX_DIALOGUE_LINES + " non-empty lines.");
            return null;
        }
        List<String> dialogue = new java.util.ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            Object line = lines.get(index);
            if (!(line instanceof String text) || text.isBlank() || text.length() > 256) {
                logger.warning("[NPC] Invalid dialogue line " + (index + 1) + " at " + path
                    + "; lines must contain 1 to 256 characters.");
                return null;
            }
            dialogue.add(text.trim());
        }
        return List.copyOf(dialogue);
    }

    public record NpcDefinition(String id, String type, String displayName, List<String> dialogue) {
        public NpcDefinition {
            dialogue = List.copyOf(dialogue);
        }
    }
}
