package com.egologic.mcextremo.entity.boss;

public final class TrialGuardianSpiderAnimations {
    public static final String IDLE = "animation.trial_guardian_spider.idle";
    public static final String WALK = "animation.trial_guardian_spider.walk";
    public static final String ATTACK = "animation.trial_guardian_spider.attack";
    public static final String SUMMON = "animation.trial_guardian_spider.summon";
    public static final String DEATH = "animation.trial_guardian_spider.death";

    private TrialGuardianSpiderAnimations() {
    }

    public static String fromState(TrialGuardianSpiderState state) {
        return switch (state) {
            case WALK -> WALK;
            case ATTACK -> ATTACK;
            case SUMMON -> SUMMON;
            case DEATH -> DEATH;
            case IDLE -> IDLE;
        };
    }
}
