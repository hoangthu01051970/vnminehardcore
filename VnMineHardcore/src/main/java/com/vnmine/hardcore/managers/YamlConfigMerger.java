package com.vnmine.hardcore.managers;

import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;

public final class YamlConfigMerger {
    private YamlConfigMerger() {
    }

    public static boolean mergeMissingKeys(
            ConfigurationSection target, ConfigurationSection defaults, Logger logger) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            ConfigurationSection defaultChild = defaults.getConfigurationSection(key);
            if (defaultChild != null) {
                ConfigurationSection targetChild = target.getConfigurationSection(key);
                if (targetChild == null) {
                    if (target.contains(key)) {
                        logger.warning("[Config] Preserving non-section custom value at " + key +
                            " while merging defaults.");
                        continue;
                    }
                    targetChild = target.createSection(key);
                    changed = true;
                }
                changed |= mergeMissingKeys(targetChild, defaultChild, logger);
            } else if (!target.contains(key)) {
                target.set(key, defaults.get(key));
                changed = true;
            }
        }
        return changed;
    }
}
