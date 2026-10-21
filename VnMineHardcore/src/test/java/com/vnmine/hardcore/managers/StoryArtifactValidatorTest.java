package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class StoryArtifactValidatorTest {
    @Test
    public void acceptsValidArtifactWithoutOptionalBossMetadata() {
        Map<String, Object> artifact = Map.of(
            "item", "Lôi Tâm Hủy Diệt",
            "lore", List.of("Dòng lore")
        );

        assertTrue(StoryArtifactValidator.validateArtifact("creeper-relic", artifact).isEmpty());
    }

    @Test
    public void reportsInvalidIdAndMissingRequiredFieldsWithPaths() {
        Map<String, Object> artifact = Map.of("lore", List.of());

        List<String> issues = StoryArtifactValidator.validateArtifact("Bad_ID", artifact);

        assertEquals(3, issues.size());
        assertTrue(issues.stream().anyMatch(issue -> issue.contains("story.yml:artifacts.Bad_ID")));
        assertTrue(issues.stream().anyMatch(issue -> issue.endsWith(".item.")));
        assertTrue(issues.stream().anyMatch(issue -> issue.contains(".lore;")));
    }

    @Test
    public void reportsUnknownBossReference() {
        var issue = StoryArtifactValidator.validateBossReference(
            "creeper-relic", "missing_boss", Set.of("creeper_boss"));

        assertTrue(issue.isPresent());
        assertTrue(issue.get().contains("story.yml:artifacts.creeper-relic.boss"));
        assertTrue(issue.get().contains("missing_boss"));
    }

    @Test
    public void acceptsKnownBossReference() {
        assertTrue(StoryArtifactValidator.validateBossReference(
            "creeper-relic", "creeper_boss", Set.of("creeper_boss")).isEmpty());
    }

    @Test
    public void reportsInvalidOptionalBossMetadata() {
        Map<String, Object> artifact = Map.of(
            "item", "Lôi Tâm Hủy Diệt",
            "lore", List.of("Dòng lore"),
            "boss", 42
        );

        List<String> issues = StoryArtifactValidator.validateArtifact("creeper-relic", artifact);

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).endsWith(".boss."));
    }
}
