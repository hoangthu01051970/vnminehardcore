package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.yaml.snakeyaml.constructor.DuplicateKeyException;

public class YamlDuplicateKeyValidatorTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsDuplicateArtifactIdsBeforeConfigurationCanOverwriteThem() throws IOException {
        File file = writeConfig("""
            artifacts:
              relic-one:
                item: First
              relic-one:
                item: Duplicate
            """);

        try {
            YamlDuplicateKeyValidator.validateNoDuplicateKeys(file);
            fail("Expected duplicate YAML key to be rejected.");
        } catch (DuplicateKeyException expected) {
            assertTrue(expected.getMessage().contains("relic-one"));
        }
    }

    @Test
    public void permitsSameKeyInSeparateSections() throws IOException {
        File file = writeConfig("""
            first:
              shared: one
            second:
              shared: two
            """);

        YamlDuplicateKeyValidator.validateNoDuplicateKeys(file);
    }

    @Test
    public void chapterLoaderRejectsDuplicateChapterIds() throws IOException {
        File file = writeConfig("""
            schema-version: 1
            chapters:
              chapter-one:
                order: 1
                title: First
                summary: First summary
              chapter-one:
                order: 2
                title: Duplicate
                summary: Duplicate summary
            """);

        assertTrue(StoryChapterLoader.load(file, Logger.getAnonymousLogger()).isEmpty());
    }

    private File writeConfig(String content) throws IOException {
        File file = temporaryFolder.newFile("duplicate.yml");
        Files.writeString(file.toPath(), content);
        return file;
    }
}
