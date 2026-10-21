package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StoryChapterLoaderTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void loadsChaptersInConfiguredOrderWithStableIds() throws IOException {
        File file = writeConfig("""
            schema-version: 1
            chapters:
              chapter-second:
                order: 2
                title: Second
                summary: Second chapter
              chapter-first:
                order: 1
                title: First
                summary: First chapter
            """);

        var chapters = StoryChapterLoader.load(file, Logger.getAnonymousLogger());

        assertEquals(2, chapters.size());
        assertEquals("chapter-first", chapters.keySet().iterator().next());
        assertEquals("First chapter", chapters.get("chapter-first").summary());
        assertTrue(chapters.containsKey("chapter-second"));
    }

    @Test
    public void skipsInvalidIdsAndDuplicateOrders() throws IOException {
        File file = writeConfig("""
            schema-version: 1
            chapters:
              chapter-first:
                order: 1
                title: First
                summary: Kept
              chapter-duplicate:
                order: 1
                title: Duplicate
                summary: Must be skipped
              invalid_ID:
                order: 2
                title: Invalid ID
                summary: Must be skipped
            """);

        var chapters = StoryChapterLoader.load(file, Logger.getAnonymousLogger());

        assertEquals(1, chapters.size());
        assertTrue(chapters.containsKey("chapter-first"));
    }

    @Test
    public void rejectsUnsupportedSchemaVersion() throws IOException {
        File file = writeConfig("""
            schema-version: 2
            chapters:
              chapter-first:
                order: 1
                title: First
                summary: First chapter
            """);

        assertTrue(StoryChapterLoader.load(file, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void skipsChaptersWithMissingTextOrInvalidOrder() throws IOException {
        File file = writeConfig("""
            schema-version: 1
            chapters:
              chapter-no-title:
                order: 1
                summary: Missing title
              chapter-invalid-order:
                order: 0
                title: Invalid order
                summary: Order must be positive
            """);

        assertTrue(StoryChapterLoader.load(file, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void loadsBundledChaptersWithStableIds() throws Exception {
        var resource = getClass().getClassLoader().getResource("story/chapters.yml");

        assertTrue(resource != null);
        var chapters = StoryChapterLoader.load(Path.of(resource.toURI()).toFile(), Logger.getAnonymousLogger());

        assertEquals(9, chapters.size());
        assertTrue(chapters.containsKey("chapter-01"));
        assertTrue(chapters.containsKey("chapter-09"));
    }

    private File writeConfig(String content) throws IOException {
        File file = temporaryFolder.newFile("chapters.yml");
        Files.writeString(file.toPath(), content);
        return file;
    }
}
