package com.tuservidor.mcextremo.util;

import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

public final class ZombiePathingHelper {
    private ZombiePathingHelper() {
    }

    public static boolean hasLineOfSight(Entity from, Entity target) {
        Vec3d eyePos = from.getEyePos();
        Vec3d targetPos = target.getEyePos();

        BlockHitResult result = from.getWorld().raycast(new RaycastContext(
            eyePos,
            targetPos,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            from
        ));

        return result.getType() == HitResult.Type.MISS;
    }

    public static double horizontalDistanceSquared(Entity from, Entity target) {
        double dx = from.getX() - target.getX();
        double dz = from.getZ() - target.getZ();
        return dx * dx + dz * dz;
    }
}
