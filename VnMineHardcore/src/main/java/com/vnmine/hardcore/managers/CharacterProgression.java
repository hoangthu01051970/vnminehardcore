package com.vnmine.hardcore.managers;

public final class CharacterProgression {
    public static final int DEFAULT_MAX_LEVEL = 30;
    public static final int DEFAULT_BASE_EXPERIENCE = 100;
    public static final int DEFAULT_EXPERIENCE_PER_LEVEL = 50;
    public static final int DEFAULT_SKILL_POINTS_PER_LEVEL = 1;

    private CharacterProgression() {
    }

    public record Rules(int maxLevel, int baseExperience, int experiencePerLevel, int skillPointsPerLevel) {
        public Rules {
            if (maxLevel < 2 || maxLevel > 100) {
                throw new IllegalArgumentException("maxLevel must be between 2 and 100.");
            }
            if (baseExperience < 1 || baseExperience > 1_000_000) {
                throw new IllegalArgumentException("baseExperience must be between 1 and 1000000.");
            }
            if (experiencePerLevel < 0 || experiencePerLevel > 1_000_000) {
                throw new IllegalArgumentException("experiencePerLevel must be between 0 and 1000000.");
            }
            if (skillPointsPerLevel < 0 || skillPointsPerLevel > 10) {
                throw new IllegalArgumentException("skillPointsPerLevel must be between 0 and 10.");
            }
        }

        public static Rules defaults() {
            return new Rules(DEFAULT_MAX_LEVEL, DEFAULT_BASE_EXPERIENCE,
                DEFAULT_EXPERIENCE_PER_LEVEL, DEFAULT_SKILL_POINTS_PER_LEVEL);
        }

        public int experienceRequiredForNextLevel(int currentLevel) {
            if (currentLevel < 1) {
                throw new IllegalArgumentException("currentLevel must be at least 1.");
            }
            if (currentLevel >= maxLevel) {
                return 0;
            }
            return Math.toIntExact((long) baseExperience
                + (long) (currentLevel - 1) * experiencePerLevel);
        }
    }

    public record Progress(int level, int experience, int unspentSkillPoints) {
    }

    public record AwardResult(Progress progress, int levelsGained, int skillPointsGained) {
    }

    public static AwardResult award(int currentLevel, int currentExperience, int currentSkillPoints,
                                    int experienceAward, Rules rules) {
        if (rules == null) {
            throw new IllegalArgumentException("rules cannot be null.");
        }
        if (currentLevel < 1 || currentExperience < 0 || currentSkillPoints < 0 || experienceAward < 0) {
            throw new IllegalArgumentException("Character progression values cannot be negative or below level 1.");
        }

        long experience = (long) currentExperience + experienceAward;
        long skillPoints = currentSkillPoints;
        int level = currentLevel;
        int levelsGained = 0;
        long pointsGained = 0;

        while (level < rules.maxLevel()) {
            int requiredExperience = rules.experienceRequiredForNextLevel(level);
            if (experience < requiredExperience) {
                break;
            }
            experience -= requiredExperience;
            level++;
            levelsGained++;
            pointsGained += rules.skillPointsPerLevel();
            skillPoints += rules.skillPointsPerLevel();
        }

        if (experience > Integer.MAX_VALUE || skillPoints > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Character progression exceeds the supported integer range.");
        }
        return new AwardResult(new Progress(level, (int) experience, (int) skillPoints),
            levelsGained, (int) pointsGained);
    }
}
