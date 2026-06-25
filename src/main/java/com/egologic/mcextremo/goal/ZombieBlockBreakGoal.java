package com.egologic.mcextremo.goal;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.util.ZombiePathingHelper;
import com.egologic.mcextremo.util.ZombieWorldRules;
import net.minecraft.block.Block;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class ZombieBlockBreakGoal extends Goal {
    private final ZombieEntity zombie;
    private final int breakTime;
    private BlockPos targetBlock;
    private int progress;

    public ZombieBlockBreakGoal(ZombieEntity zombie, int breakTime) {
        this.zombie = zombie;
        this.breakTime = breakTime;
        this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
    }

    @Override
    public boolean canStart() {
        if (!(zombie.getWorld() instanceof ServerWorld world)) return false;
        int day = MCExtremo.getInstance().getZombieManager().getDay(world);
        if (!MCExtremo.getInstance().getZombieManager().canBreakBlocks(day)) return false;

        if (zombie.getTarget() == null) return false;
        if (zombie.distanceTo(zombie.getTarget()) > 20) return false;
        if (zombie.distanceTo(zombie.getTarget()) < 2.0) return false;

        if (ZombiePathingHelper.hasLineOfSight(zombie, zombie.getTarget())) return false;

        List<BlockPos> blocking = findBlockingBlocks();
        if (blocking.isEmpty()) return false;

        this.targetBlock = blocking.get(0);
        return true;
    }

    @Override
    public boolean shouldContinue() {
        if (this.targetBlock == null) return false;
        if (zombie.getWorld().getBlockState(this.targetBlock).isAir()) return false;
        if (zombie.getTarget() == null) return false;
        if (ZombiePathingHelper.hasLineOfSight(zombie, zombie.getTarget())) return false;
        return zombie.distanceTo(zombie.getTarget()) <= 20;
    }

    @Override
    public void start() {
        this.progress = 0;
    }

    @Override
    public void stop() {
        if (this.targetBlock != null) {
            zombie.getWorld().setBlockBreakingInfo(zombie.getId(), this.targetBlock, 0);
        }
        this.targetBlock = null;
        this.progress = 0;
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (this.targetBlock == null) return;

        zombie.getLookControl().lookAt(
            targetBlock.getX() + 0.5,
            targetBlock.getY() + 0.5,
            targetBlock.getZ() + 0.5
        );

        double dist = zombie.squaredDistanceTo(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5);
        if (dist > 16) {
            zombie.getNavigation().startMovingTo(targetBlock.getX() + 0.5, targetBlock.getY(), targetBlock.getZ() + 0.5, 1.0);
        } else {
            zombie.getNavigation().stop();
        }

        this.progress++;

        int stage = Math.min((this.progress * 10) / this.breakTime, 9);
        zombie.getWorld().setBlockBreakingInfo(zombie.getId(), this.targetBlock, stage);

        if (this.progress >= this.breakTime) {
            breakBlock();
        }
    }

    private void breakBlock() {
        if (this.targetBlock == null) return;
        if (!ZombieWorldRules.canBreak(zombie.getWorld(), this.targetBlock)) return;

        zombie.getWorld().removeBlock(this.targetBlock, false);
        this.targetBlock = null;
        this.progress = 0;
    }

    private List<BlockPos> findBlockingBlocks() {
        List<BlockPos> blocking = new ArrayList<>();
        World world = zombie.getWorld();

        Vec3d eyePos = zombie.getEyePos();
        Vec3d targetPos = zombie.getTarget().getEyePos();

        Vec3d direction = targetPos.subtract(eyePos).normalize();
        double distance = eyePos.distanceTo(targetPos);

        for (double d = 0.5; d < distance; d += 0.5) {
            Vec3d checkVec = eyePos.add(direction.multiply(d));
            BlockPos checkPos = new BlockPos((int) Math.floor(checkVec.x), (int) Math.floor(checkVec.y), (int) Math.floor(checkVec.z));

            var state = world.getBlockState(checkPos);
            if (state.isAir()) continue;

            if (!ZombieWorldRules.canBreak(world, checkPos)) continue;

            double mobDist = zombie.squaredDistanceTo(checkPos.getX() + 0.5, checkPos.getY(), checkPos.getZ() + 0.5);
            if (mobDist <= 25) {
                blocking.add(checkPos);
            }
        }

        blocking.sort((a, b) -> {
            double distA = zombie.squaredDistanceTo(a.getX() + 0.5, a.getY(), a.getZ() + 0.5);
            double distB = zombie.squaredDistanceTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
            return Double.compare(distA, distB);
        });

        return blocking;
    }
}
