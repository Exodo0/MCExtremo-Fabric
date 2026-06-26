package com.egologic.mcextremo.entity.boss;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class TrialGuardianSpiderEntity extends SpiderEntity implements GeoEntity {
    private static final TrackedData<Integer> GUARDIAN_STATE = DataTracker.registerData(TrialGuardianSpiderEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> FORCED_ANIMATION_TICKS = DataTracker.registerData(TrialGuardianSpiderEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public TrialGuardianSpiderEntity(EntityType<? extends SpiderEntity> entityType, World world) {
        super(entityType, world);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(GUARDIAN_STATE, TrialGuardianSpiderState.IDLE.ordinal());
        this.dataTracker.startTracking(FORCED_ANIMATION_TICKS, 0);
    }

    @Override
    public void tick() {
        super.tick();
        int ticks = this.dataTracker.get(FORCED_ANIMATION_TICKS);
        if (ticks > 0) {
            this.dataTracker.set(FORCED_ANIMATION_TICKS, ticks - 1);
            if (ticks == 1 && getGuardianState() != TrialGuardianSpiderState.DEATH) {
                setGuardianState(getVelocity().horizontalLengthSquared() > 0.004 ? TrialGuardianSpiderState.WALK : TrialGuardianSpiderState.IDLE);
            }
        } else if (getGuardianState() != TrialGuardianSpiderState.DEATH) {
            setGuardianState(getVelocity().horizontalLengthSquared() > 0.004 ? TrialGuardianSpiderState.WALK : TrialGuardianSpiderState.IDLE);
        }
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        playGuardianState(TrialGuardianSpiderState.DEATH, 30);
        super.onDeath(damageSource);
    }

    public TrialGuardianSpiderState getGuardianState() {
        int index = this.dataTracker.get(GUARDIAN_STATE);
        TrialGuardianSpiderState[] values = TrialGuardianSpiderState.values();
        return index >= 0 && index < values.length ? values[index] : TrialGuardianSpiderState.IDLE;
    }

    public void setGuardianState(TrialGuardianSpiderState state) {
        this.dataTracker.set(GUARDIAN_STATE, state.ordinal());
    }

    public void playGuardianState(TrialGuardianSpiderState state, int durationTicks) {
        setGuardianState(state);
        this.dataTracker.set(FORCED_ANIMATION_TICKS, Math.max(1, durationTicks));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "trial_guardian_spider_controller", 4, state -> {
            TrialGuardianSpiderState guardianState = getGuardianState();
            String animation = TrialGuardianSpiderAnimations.fromState(guardianState);
            if (guardianState == TrialGuardianSpiderState.IDLE && state.isMoving()) {
                animation = TrialGuardianSpiderAnimations.WALK;
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
