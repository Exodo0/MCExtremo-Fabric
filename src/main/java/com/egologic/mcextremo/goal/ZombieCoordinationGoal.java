package com.egologic.mcextremo.goal;

import com.egologic.mcextremo.MCExtremo;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.EnumSet;
import java.util.List;

public class ZombieCoordinationGoal extends Goal {
    private final ZombieEntity zombie;
    private final int range;

    public ZombieCoordinationGoal(ZombieEntity zombie, int range) {
        this.zombie = zombie;
        this.range = range;
        this.setControls(EnumSet.of(Control.TARGET));
    }

    @Override
    public boolean canStart() {
        if (!(zombie.getWorld() instanceof ServerWorld world)) return false;
        MCExtremo mod = MCExtremo.getInstance();
        if (mod == null || mod.getZombieManager() == null) return false;
        int day = mod.getZombieManager().getDay(world);
        if (!mod.getZombieManager().getPhase(day).hasCoordination()) return false;
        if (zombie.getTarget() == null) return false;
        return zombie.getRandom().nextFloat() < 0.01f;
    }

    @Override
    public void start() {
        if (zombie.getTarget() == null) return;

        Box box = new Box(
            zombie.getX() - range, zombie.getY() - range, zombie.getZ() - range,
            zombie.getX() + range, zombie.getY() + range, zombie.getZ() + range
        );

        List<ZombieEntity> nearby = zombie.getWorld().getEntitiesByClass(ZombieEntity.class, box,
            z -> z != zombie && z != zombie.getTarget() && z.getTarget() == null);

        for (ZombieEntity z : nearby) {
            if (zombie.getTarget() instanceof PlayerEntity player) {
                z.setTarget(player);
            }
        }
    }

    @Override
    public boolean shouldContinue() {
        return false;
    }

    @Override
    public void stop() {
    }
}
