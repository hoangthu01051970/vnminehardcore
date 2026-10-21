package com.vnmine.hardcore.managers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class StoryArtifactValidator {
    private StoryArtifactValidator() {
    }

    public static List<String> validateArtifact(String storyId, Map<?, ?> artifact) {
        String path = "story.yml:artifacts." + storyId;
        List<String> issues = new ArrayList<>();

        if (!storyId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            issues.add("Invalid artifact ID at " + path +
                "; use lowercase letters, numbers, and single hyphens between segments.");
        }

        Object itemName = artifact.get("item");
        if (!(itemName instanceof String name) || name.isBlank()) {
            issues.add("Missing or invalid required item name at " + path + ".item.");
        }

        Object loreValue = artifact.get("lore");
        if (!(loreValue instanceof List<?> lore) || lore.isEmpty()
                || lore.stream().anyMatch(line -> !(line instanceof String text) || text.isBlank())) {
            issues.add("Missing or invalid required lore list at " + path +
                ".lore; expected a non-empty list of non-blank strings.");
        }

        if (artifact.containsKey("boss")) {
            Object bossId = artifact.get("boss");
            if (!(bossId instanceof String value) || value.isBlank()) {
                issues.add("Invalid boss ID at " + path + ".boss.");
            }
        }

        return List.copyOf(issues);
    }

    public static Optional<String> validateBossReference(String storyId, String bossId, Set<String> knownBossIds) {
        if (knownBossIds.contains(bossId)) return Optional.empty();
        return Optional.of("Unknown boss ID '" + bossId + "' at story.yml:artifacts." + storyId + ".boss.");
    }
}
