package com.egologic.mcextremo.goal;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.util.ZombiePathingHelper;
import com.egologic.mcextremo.util.ZombieWorldRules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ZombieBuildGoal extends Goal {
    private enum BuildState {
        EVALUATE,
        MOVE_TO_BUILD_POS,
        PLACE_STEP,
        REPATH,
        ABORT
    }

    private record BuildStep(BlockPos placePos, BlockPos standPos) {
    }

    private final ZombieEntity zombie;
    private final int maxBlocks;

    private BuildState state = BuildState.EVALUATE;
    private List<BuildStep> plan = List.of();
    private int currentStep;
    private int blocksPlaced;
    private int buildCooldown;
    private int failedAttempts;
    private int abortCooldown;
    private int repathCooldown;
    private int stuckTicks;
    private int activeDay;
    private Vec3d lastProgressPos = Vec3d.ZERO;

    public ZombieBuildGoal(ZombieEntity zombie, int maxBlocks) {
        this.zombie = zombie;
        this.maxBlocks = maxBlocks;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (abortCooldown > 0) {
            abortCooldown--;
            return false;
        }

        if (!(zombie.getWorld() instanceof ServerWorld world)) return false;
        ModConfig.Zombies.ConstruirBloques config = config();
        if (!config.activado) return false;

        activeDay = MCExtremo.getInstance().getZombieManager().getDay(world);
        if (!MCExtremo.getInstance().getZombieManager().canBuild(activeDay)) return false;

        LivingEntity target = zombie.getTarget();
        if (!isValidTarget(target)) return false;

        double heightDiff = target.getY() - zombie.getY();
        if (heightDiff < 1.5 || heightDiff > config.alturaMaxima + 2) return false;

        double horizontal = ZombiePathingHelper.horizontalDistanceSquared(zombie, target);
        if (horizontal < config.distanciaMinima * config.distanciaMinima) return false;
        int maxDistance = Math.max(config.distanciaMaxima, 16);
        if (horizontal > maxDistance * maxDistance) return false;

        plan = buildStaircasePath(target);
        return !plan.isEmpty();
    }

    @Override
    public boolean shouldContinue() {
        LivingEntity target = zombie.getTarget();
        if (!isValidTarget(target)) return false;
        if (state == BuildState.ABORT) return false;
        if (blocksPlaced >= maxBlocks) return false;
        if (plan.isEmpty() || currentStep >= plan.size()) return false;

        ModConfig.Zombies.ConstruirBloques config = config();
        double heightDiff = target.getY() - zombie.getY();
        if (heightDiff < 1.0 || heightDiff > config.alturaMaxima + 3) return false;

        double horizontal = ZombiePathingHelper.horizontalDistanceSquared(zombie, target);
        return horizontal <= config.radioBusqueda * config.radioBusqueda;
    }

    @Override
    public void start() {
        state = BuildState.EVALUATE;
        currentStep = 0;
        blocksPlaced = 0;
        buildCooldown = 0;
        repathCooldown = 0;
        failedAttempts = 0;
        stuckTicks = 0;
        activeDay = currentDay();
        lastProgressPos = zombie.getPos();
    }

    @Override
    public void stop() {
        plan = List.of();
        currentStep = 0;
        blocksPlaced = 0;
        buildCooldown = 0;
        repathCooldown = 0;
        stuckTicks = 0;
        zombie.getNavigation().stop();
        if (state != BuildState.ABORT) {
            state = BuildState.EVALUATE;
        }
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        LivingEntity target = zombie.getTarget();
        if (!isValidTarget(target)) {
            abort();
            return;
        }

        switch (state) {
            case EVALUATE -> rebuildPlan(target);
            case REPATH -> {
                if (repathCooldown > 0) {
                    repathCooldown--;
                    return;
                }
                rebuildPlan(target);
            }
            case MOVE_TO_BUILD_POS -> moveToCurrentStep();
            case PLACE_STEP -> placeCurrentStep();
            case ABORT -> {
            }
        }
    }

    private void rebuildPlan(LivingEntity target) {
        plan = buildStaircasePath(target);
        currentStep = 0;
        buildCooldown = 0;
        stuckTicks = 0;
        lastProgressPos = zombie.getPos();

        if (plan.isEmpty()) {
            registerFailure();
            return;
        }

        state = BuildState.MOVE_TO_BUILD_POS;
    }

    private void moveToCurrentStep() {
        if (currentStep >= plan.size()) {
            state = BuildState.REPATH;
            return;
        }

        BuildStep step = plan.get(currentStep);
        World world = zombie.getWorld();

        if (!world.getBlockState(step.placePos()).isAir()) {
            currentStep++;
            state = BuildState.REPATH;
            repathCooldown = getRepathDelayTicks();
            return;
        }

        trackStuck();
        if (state == BuildState.ABORT || state == BuildState.REPATH) return;

        zombie.getLookControl().lookAt(
            step.placePos().getX() + 0.5,
            step.placePos().getY() + 0.5,
            step.placePos().getZ() + 0.5
        );

        double dist = zombie.squaredDistanceTo(
            step.placePos().getX() + 0.5,
            step.placePos().getY(),
            step.placePos().getZ() + 0.5
        );

        if (dist > getPlaceRangeSquared()) {
            zombie.getNavigation().startMovingTo(
                step.placePos().getX() + 0.5,
                step.placePos().getY(),
                step.placePos().getZ() + 0.5,
                1.15
            );
            return;
        }

        zombie.getNavigation().stop();
        state = BuildState.PLACE_STEP;
    }

    private void placeCurrentStep() {
        if (currentStep >= plan.size()) {
            state = BuildState.REPATH;
            return;
        }

        buildCooldown++;
        if (buildCooldown < getBuildDelayTicks()) return;
        buildCooldown = 0;

        BuildStep step = plan.get(currentStep);
        if (!ZombieWorldRules.canPlace(zombie.getWorld(), step.placePos())) {
            currentStep++;
            registerFailure();
            return;
        }

        zombie.getWorld().setBlockState(step.placePos(), ZombieWorldRules.getBuildBlock());
        blocksPlaced++;
        currentStep++;
        failedAttempts = 0;

        zombie.getNavigation().startMovingTo(
            step.standPos().getX() + 0.5,
            step.standPos().getY(),
            step.standPos().getZ() + 0.5,
            1.15
        );

        state = BuildState.REPATH;
        repathCooldown = getRepathDelayTicks();
    }

    private void trackStuck() {
        double moved = zombie.getPos().squaredDistanceTo(lastProgressPos);
        if (moved > 0.04) {
            lastProgressPos = zombie.getPos();
            stuckTicks = 0;
            return;
        }

        stuckTicks++;
        if (stuckTicks < config().ticksAtascado) return;

        stuckTicks = 0;
        registerFailure();
    }

    private void registerFailure() {
        failedAttempts++;
        if (failedAttempts >= config().maxIntentosAtascado) {
            abort();
        } else {
            state = BuildState.REPATH;
            repathCooldown = getRepathDelayTicks();
        }
    }

    private void abort() {
        state = BuildState.ABORT;
        abortCooldown = Math.max(20, config().cooldownFracasoTicks / 2);
        zombie.getNavigation().stop();
    }

    private List<BuildStep> buildStaircasePath(LivingEntity target) {
        World world = zombie.getWorld();
        BlockPos origin = zombie.getBlockPos();
        Vec3d toTarget = new Vec3d(target.getX() - zombie.getX(), 0, target.getZ() - zombie.getZ());
        if (toTarget.lengthSquared() < 0.001) return List.of();

        Direction direction = Direction.getFacing(toTarget.x, 0, toTarget.z);
        ModConfig.Zombies.ConstruirBloques config = config();
        int heightNeeded = Math.max(1, (int) Math.ceil(target.getY() - zombie.getY()));
        int maxSteps = Math.min(Math.min(heightNeeded + 2, maxBlocks), config.alturaMaxima + 2);

        List<BuildStep> steps = new ArrayList<>();
        Set<BlockPos> planned = new HashSet<>();

        for (int step = 0; step < maxSteps && steps.size() < maxBlocks; step++) {
            BlockPos placePos = origin.add(
                direction.getOffsetX() * (step + 1),
                Math.min(step, config.alturaMaxima),
                direction.getOffsetZ() * (step + 1)
            );

            BlockPos support = placePos.down();
            if (world.getBlockState(support).isAir()
                && steps.size() < maxBlocks
                && canPlaceConsideringPlan(world, support, planned)) {
                steps.add(new BuildStep(support, support.up()));
                planned.add(support);
            }

            if (canPlaceConsideringPlan(world, placePos, planned)) {
                steps.add(new BuildStep(placePos, placePos.up()));
                planned.add(placePos);
            }
        }

        return steps;
    }

    private boolean canPlaceConsideringPlan(World world, BlockPos pos, Set<BlockPos> planned) {
        if (!world.getBlockState(pos).isAir()) return false;
        if (!world.getBlockState(pos.up()).isAir()) return false;
        if (pos.equals(zombie.getBlockPos())) return false;

        ModConfig.Zombies.ConstruirBloques config = config();
        if (!config.colocarSoloSiHaySoporte) return true;

        BlockPos below = pos.down();
        return planned.contains(below) || world.getBlockState(below).isSolidBlock(world, below);
    }

    private boolean isValidTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved();
    }

    private int getBuildDelayTicks() {
        int configured = config().pausaEntreBloquesTicks;
        if (activeDay >= 100) return Math.max(2, configured - 8);
        if (activeDay >= 71) return Math.max(3, configured - 6);
        if (activeDay >= 46) return Math.max(4, configured - 4);
        return configured;
    }

    private int getRepathDelayTicks() {
        if (activeDay >= 100) return 4;
        if (activeDay >= 71) return 6;
        return 8;
    }

    private double getPlaceRangeSquared() {
        if (activeDay >= 100) return 25.0;
        if (activeDay >= 71) return 20.25;
        return 16.0;
    }

    private int currentDay() {
        if (!(zombie.getWorld() instanceof ServerWorld world)) return activeDay;
        MCExtremo mod = MCExtremo.getInstance();
        if (mod == null || mod.getZombieManager() == null) return activeDay;
        return mod.getZombieManager().getDay(world);
    }

    private ModConfig.Zombies.ConstruirBloques config() {
        return ModConfig.get().zombies.construirBloques;
    }
}
