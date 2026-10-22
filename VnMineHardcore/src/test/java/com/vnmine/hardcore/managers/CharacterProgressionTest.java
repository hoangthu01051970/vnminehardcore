package com.vnmine.hardcore.managers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class CharacterProgressionTest {
    @Test
    public void awardsMultipleLevelsAndCarriesExcessExperience() {
        CharacterProgression.AwardResult result = CharacterProgression.award(
            1, 0, 0, 375, CharacterProgression.Rules.defaults());

        assertEquals(new CharacterProgression.Progress(3, 125, 2), result.progress());
        assertEquals(2, result.levelsGained());
        assertEquals(2, result.skillPointsGained());
    }

    @Test
    public void retainsExperienceAtMaximumLevelWithoutAwardingMorePoints() {
        CharacterProgression.Rules rules = new CharacterProgression.Rules(2, 100, 50, 1);

        CharacterProgression.AwardResult result = CharacterProgression.award(2, 20, 3, 500, rules);

        assertEquals(new CharacterProgression.Progress(2, 520, 3), result.progress());
        assertEquals(0, result.levelsGained());
        assertEquals(0, result.skillPointsGained());
    }

    @Test
    public void rejectsInvalidProgressionInputs() {
        assertThrows(IllegalArgumentException.class, () ->
            CharacterProgression.award(0, 0, 0, 0, CharacterProgression.Rules.defaults()));
        assertThrows(IllegalArgumentException.class, () ->
            CharacterProgression.award(1, -1, 0, 0, CharacterProgression.Rules.defaults()));
        assertThrows(IllegalArgumentException.class, () ->
            CharacterProgression.award(1, 0, 0, -1, CharacterProgression.Rules.defaults()));
        assertThrows(IllegalArgumentException.class, () ->
            CharacterProgression.award(30, Integer.MAX_VALUE, 0, 1, CharacterProgression.Rules.defaults()));
        assertThrows(IllegalArgumentException.class, () ->
            CharacterProgression.award(1, 0, Integer.MAX_VALUE, 100, CharacterProgression.Rules.defaults()));
    }
}
