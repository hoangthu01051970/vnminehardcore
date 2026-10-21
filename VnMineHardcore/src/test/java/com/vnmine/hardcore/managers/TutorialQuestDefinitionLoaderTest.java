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

public class TutorialQuestDefinitionLoaderTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void bundledTutorialQuestUsesSupportedExplorationObjective() throws Exception {
        var definition = TutorialQuestDefinitionLoader.load(resourceFile(), Logger.getAnonymousLogger())
            .orElseThrow();

        assertEquals("first-steps", definition.id());
        assertTrue(definition.enabled());
        assertEquals("reach-human-realm-spawn", definition.objective().id());
        assertEquals("visit-realm-spawn", definition.objective().type());
        assertEquals("human-realm", definition.objective().realmId());
        assertEquals(8, definition.objective().radiusBlocks());
    }

    @Test
    public void unsupportedObjectiveTypeRejectsTheWholeQuest() throws Exception {
        File file = copyDefinition("unsupported-type.yml");
        replace(file, "type: visit-realm-spawn", "type: kill-monster");

        assertTrue(TutorialQuestDefinitionLoader.load(file, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void duplicateKeysRejectTheQuest() throws Exception {
        File file = copyDefinition("duplicate-key.yml");
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write("\nquest:\n  id: overwritten\n");
        }

        assertTrue(TutorialQuestDefinitionLoader.load(file, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void objectiveRadiusHasAnExplicitBound() throws Exception {
        File file = copyDefinition("invalid-radius.yml");
        replace(file, "radius-blocks: 8", "radius-blocks: 65");

        assertTrue(TutorialQuestDefinitionLoader.load(file, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void objectiveUsesCircularHorizontalBoundary() {
        assertTrue(TutorialQuestManager.isWithinObjectiveRadius(0, 0, 8, 0, 8));
        assertFalse(TutorialQuestManager.isWithinObjectiveRadius(0, 0, 8, 1, 8));
    }

    private File resourceFile() throws Exception {
        var resource = getClass().getClassLoader().getResource("quests/tutorial/first-steps.yml");
        if (resource == null) throw new IllegalStateException("Bundled tutorial quest is missing.");
        return Path.of(resource.toURI()).toFile();
    }

    private File copyDefinition(String name) throws Exception {
        File copy = temporaryFolder.newFile(name);
        try (var input = getClass().getClassLoader().getResourceAsStream("quests/tutorial/first-steps.yml")) {
            if (input == null) throw new IllegalStateException("Bundled tutorial quest is missing.");
            java.nio.file.Files.copy(input, copy.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return copy;
    }

    private void replace(File file, String oldValue, String newValue) throws Exception {
        String contents = java.nio.file.Files.readString(file.toPath());
        java.nio.file.Files.writeString(file.toPath(), contents.replace(oldValue, newValue));
    }
}
