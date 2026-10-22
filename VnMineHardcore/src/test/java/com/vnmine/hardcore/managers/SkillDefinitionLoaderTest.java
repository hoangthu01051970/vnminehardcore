package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import java.util.Set;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SkillDefinitionLoaderTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void bundledSkillDefinitionsAndBooksAreLinked() throws Exception {
        var registry = loadBundled();

        assertEquals(9, registry.skills().size());
        assertEquals(9, registry.books().size());
        assertEquals("swordsman", registry.skills().get("blade-flare").classId());
        assertFalse(registry.skills().get("blade-flare").passive());
        assertEquals(6, registry.skills().get("blade-flare").cooldownSeconds());
        assertEquals(12, registry.skills().get("blade-flare").manaCost());
        assertTrue(registry.skills().get("battle-focus").passive());
        assertEquals("blade-flare", registry.books().get("blade-flare-manual").skillId());
        assertEquals("blade-flare-manual", registry.bookBySkill().get("blade-flare"));
    }

    @Test
    public void bookCannotReferenceAnUnknownSkill() throws Exception {
        File skills = copyResource("skills/skill-definitions.yml", "skills.yml");
        File books = copyResource("skills/skill-books.yml", "books.yml");
        replace(books, "skill-id: blade-flare", "skill-id: missing-skill");

        assertTrue(SkillDefinitionLoader.load(skills, books, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void duplicateYamlKeysRejectTheRegistry() throws Exception {
        File skills = copyResource("skills/skill-definitions.yml", "skills.yml");
        File books = copyResource("skills/skill-books.yml", "books.yml");
        Files.writeString(skills.toPath(), Files.readString(skills.toPath())
            + "\nskills:\n  overwritten: {}\n");

        assertTrue(SkillDefinitionLoader.load(skills, books, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void skillConstraintsRejectInvalidLevelAndRank() throws Exception {
        File skills = copyResource("skills/skill-definitions.yml", "skills.yml");
        File books = copyResource("skills/skill-books.yml", "books.yml");
        replace(skills, "required-level: 1", "required-level: 31");

        assertFalse(SkillDefinitionLoader.load(skills, books, Logger.getAnonymousLogger()).isPresent());
    }

    @Test
    public void skillConstraintsRejectInvalidCastTypeAndCooldown() throws Exception {
        File skills = copyResource("skills/skill-definitions.yml", "skills.yml");
        File books = copyResource("skills/skill-books.yml", "books.yml");
        replace(skills, "cast-type: active", "cast-type: instant");

        assertTrue(SkillDefinitionLoader.load(skills, books, Logger.getAnonymousLogger()).isEmpty());

        skills = copyResource("skills/skill-definitions.yml", "invalid-cooldown.yml");
        replace(skills, "cooldown-seconds: 6", "cooldown-seconds: 121");
        assertTrue(SkillDefinitionLoader.load(skills, books, Logger.getAnonymousLogger()).isEmpty());

        skills = copyResource("skills/skill-definitions.yml", "invalid-mana-cost.yml");
        replace(skills, "mana-cost: 12", "mana-cost: -1");
        assertTrue(SkillDefinitionLoader.load(skills, books, Logger.getAnonymousLogger()).isEmpty());
    }

    @Test
    public void existingDefinitionsWithoutCastFieldsReceiveCompatibleDefaults() throws Exception {
        File skills = copyResource("skills/skill-definitions.yml", "legacy-skills.yml");
        File books = copyResource("skills/skill-books.yml", "legacy-books.yml");
        String content = Files.readString(skills.toPath()).replaceAll(
            "(?m)^    (?:cast-type: (?:active|passive)|cooldown-seconds: \\d+|mana-cost: \\d+)\\R", "");
        Files.writeString(skills.toPath(), content);

        var registry = SkillDefinitionLoader.load(skills, books, Logger.getAnonymousLogger()).orElseThrow();

        assertFalse(registry.skills().get("blade-flare").passive());
        assertEquals(6, registry.skills().get("blade-flare").cooldownSeconds());
        assertEquals(12, registry.skills().get("blade-flare").manaCost());
        assertTrue(registry.skills().get("battle-focus").passive());
        assertEquals(0, registry.skills().get("battle-focus").cooldownSeconds());
        assertEquals(0, registry.skills().get("battle-focus").manaCost());
    }

    @Test
    public void unknownClassIdRejectsTheRegistry() throws Exception {
        File skills = copyResource("skills/skill-definitions.yml", "skills.yml");
        File books = copyResource("skills/skill-books.yml", "books.yml");

        assertTrue(SkillDefinitionLoader.load(skills, books, Set.of("guardian", "mage"),
            Logger.getAnonymousLogger()).isEmpty());
    }

    private SkillDefinitionLoader.SkillRegistry loadBundled() throws Exception {
        return SkillDefinitionLoader.load(
            resourceFile("skills/skill-definitions.yml"),
            resourceFile("skills/skill-books.yml"),
            Set.of("swordsman", "guardian", "mage"),
            Logger.getAnonymousLogger()).orElseThrow();
    }

    private File resourceFile(String name) throws Exception {
        var resource = getClass().getClassLoader().getResource(name);
        if (resource == null) throw new IllegalStateException("Bundled skill resource is missing: " + name);
        return Path.of(resource.toURI()).toFile();
    }

    private File copyResource(String resourceName, String targetName) throws Exception {
        File target = temporaryFolder.newFile(targetName);
        try (var input = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (input == null) throw new IllegalStateException("Bundled skill resource is missing: " + resourceName);
            Files.copy(input, target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private void replace(File file, String oldValue, String newValue) throws Exception {
        Files.writeString(file.toPath(), Files.readString(file.toPath()).replace(oldValue, newValue));
    }
}
