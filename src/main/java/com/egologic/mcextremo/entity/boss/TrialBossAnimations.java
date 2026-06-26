package com.egologic.mcextremo.entity.boss;

public final class TrialBossAnimations {
    public static final String IDLE = "animation.trial_boss.idle";
    public static final String WALK = "animation.trial_boss.walk";
    public static final String RUN = "animation.trial_boss.run";
    public static final String SPAWN_INTRO = "animation.trial_boss.spawn_intro";
    public static final String ROAR = "animation.trial_boss.roar";
    public static final String BASIC_ATTACK = "animation.trial_boss.basic_attack";
    public static final String SPECIAL_ATTACK = "animation.trial_boss.special_attack";
    public static final String SUMMON_MINIONS = "animation.trial_boss.summon_minions";
    public static final String PHASE_TRANSITION = "animation.trial_boss.phase_transition";
    public static final String STUNNED = "animation.trial_boss.stunned";
    public static final String DEATH = "animation.trial_boss.death";

    private TrialBossAnimations() {
    }

    public static String fromState(TrialBossState state) {
        return switch (state) {
            case SPAWNING -> SPAWN_INTRO;
            case ROARING -> ROAR;
            case ATTACKING -> BASIC_ATTACK;
            case SPECIAL_ATTACKING -> SPECIAL_ATTACK;
            case SUMMONING -> SUMMON_MINIONS;
            case PHASE_TRANSITION -> PHASE_TRANSITION;
            case STUNNED -> STUNNED;
            case DYING -> DEATH;
            case CHASING -> WALK;
            case IDLE -> IDLE;
        };
    }
}
