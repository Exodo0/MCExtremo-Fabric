package com.egologic.mcextremo.entity.boss;

import com.egologic.mcextremo.config.ModConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class TrialBossEntity extends ZombieEntity implements GeoEntity {
    private static final TrackedData<Integer> BOSS_STATE = DataTracker.registerData(TrialBossEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> FORCED_ANIMATION_TICKS = DataTracker.registerData(TrialBossEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public TrialBossEntity(EntityType<? extends ZombieEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        this.goalSelector.add(2, new ZombieAttackGoal(this, 1.0, false));
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(BOSS_STATE, TrialBossState.IDLE.ordinal());
        this.dataTracker.startTracking(FORCED_ANIMATION_TICKS, 0);
    }

    @Override
    public void tick() {
        super.tick();
        int ticks = this.dataTracker.get(FORCED_ANIMATION_TICKS);
        if (ticks > 0) {
            this.dataTracker.set(FORCED_ANIMATION_TICKS, ticks - 1);
            if (ticks == 1 && getBossState() != TrialBossState.DYING) {
                setBossState(hasTarget() ? TrialBossState.CHASING : TrialBossState.IDLE);
            }
        } else if (getBossState() != TrialBossState.DYING && getBossState() != TrialBossState.SPAWNING) {
            setBossState(hasTarget() ? TrialBossState.CHASING : TrialBossState.IDLE);
        }
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        playBossState(TrialBossState.DYING, ModConfig.get().visuals.bossDeathDurationTicks);
        super.onDeath(damageSource);
    }

    @Override
    protected void updatePostDeath() {
        this.deathTime++;
        if (this.deathTime >= ModConfig.get().visuals.bossDeathDurationTicks && !this.getWorld().isClient()) {
            this.getWorld().sendEntityStatus(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    public TrialBossState getBossState() {
        int index = this.dataTracker.get(BOSS_STATE);
        TrialBossState[] values = TrialBossState.values();
        return index >= 0 && index < values.length ? values[index] : TrialBossState.IDLE;
    }

    public void setBossState(TrialBossState state) {
        this.dataTracker.set(BOSS_STATE, state.ordinal());
    }

    public void playBossState(TrialBossState state, int durationTicks) {
        setBossState(state);
        this.dataTracker.set(FORCED_ANIMATION_TICKS, Math.max(1, durationTicks));
    }

    private boolean hasTarget() {
        return getTarget() != null && getTarget().isAlive();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "trial_boss_controller", 4, state -> {
            TrialBossState bossState = getBossState();
            String animation = TrialBossAnimations.fromState(bossState);
            if (bossState == TrialBossState.CHASING) {
                animation = isSprinting() || getVelocity().horizontalLengthSquared() > 0.075 ? TrialBossAnimations.RUN : TrialBossAnimations.WALK;
            } else if (bossState == TrialBossState.IDLE && state.isMoving()) {
                animation = TrialBossAnimations.WALK;
            }
            state.getController().setAnimation(RawAnimation.begin().thenLoop(animation));
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }
}
