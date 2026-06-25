package com.tuservidor.mcextremo.goal;

import com.tuservidor.mcextremo.MCExtremo;
import com.tuservidor.mcextremo.config.ModConfig;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

import java.util.EnumSet;

public class ZombieSightGoal extends Goal {
    private final ZombieEntity zombie;
    private int seeCooldown;

    public ZombieSightGoal(ZombieEntity zombie) {
        this.zombie = zombie;
        this.setControls(EnumSet.of(Control.LOOK));
    }

    @Override
    public boolean canStart() {
        return zombie.getTarget() instanceof ServerPlayerEntity;
    }

    @Override
    public boolean shouldContinue() {
        return zombie.getTarget() instanceof ServerPlayerEntity;
    }

    @Override
    public boolean shouldRunEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        seeCooldown++;
        if (seeCooldown < 40) return;
        seeCooldown = 0;

        if (!(zombie.getTarget() instanceof ServerPlayerEntity player)) return;
        MCExtremo mod = MCExtremo.getInstance();
        if (mod.getReviveTrialManager().isInTrial(player.getUuid())) return;
        if (zombie.hasCustomName() && zombie.getName().getString().contains("Guardian del Vacio")) return;

        ModConfig.Zombies.Horda hordaConfig = ModConfig.get().zombies.horda;
        if (!hordaConfig.activado) return;

        ServerWorld world = (ServerWorld) zombie.getWorld();
        int day = mod.getZombieManager().getDay(world);
        if (!mod.getZombieManager().canUseHordes(day)) return;

        int radio = Math.min(hordaConfig.radioMaximo, hordaConfig.radioActivacion + day);
        if (zombie.squaredDistanceTo(player) > radio * radio) return;

        if (!hasLineOfSight(player)) return;

        if (mod.getZombieHordeManager() != null) {
            mod.getZombieHordeManager().onZombieSeePlayer(zombie, player);
        }
    }

    private boolean hasLineOfSight(net.minecraft.entity.Entity target) {
        Vec3d eyePos = zombie.getEyePos();
        Vec3d targetPos = target.getEyePos();

        BlockHitResult result = zombie.getWorld().raycast(new RaycastContext(
            eyePos, targetPos,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            zombie
        ));

        return result.getType() == HitResult.Type.MISS;
    }
}
