package com.tuservidor.mcextremo.listener;

import com.tuservidor.mcextremo.MCExtremo;
import com.tuservidor.mcextremo.config.ModConfig;
import com.tuservidor.mcextremo.goal.ZombieBlockBreakGoal;
import com.tuservidor.mcextremo.goal.ZombieBuildGoal;
import com.tuservidor.mcextremo.goal.ZombieCoordinationGoal;
import com.tuservidor.mcextremo.goal.ZombieSightGoal;
import com.tuservidor.mcextremo.util.DifficultyPhase;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.ai.goal.BreakDoorGoal;
import net.minecraft.entity.ai.goal.GoalSelector;
import net.minecraft.entity.ai.goal.LongDoorInteractGoal;
import net.minecraft.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.Difficulty;

public class ZombieGoalListener {

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ZombieEntity zombie) {
                if (zombie.getCommandTags().contains("mcextremo_trial")) return;
                if (zombie.getCommandTags().contains("mcextremo_event_trial")) return;
                MCExtremo mod = MCExtremo.getInstance();
                if (mod != null && mod.getZombieManager() != null) {
                    ServerWorld serverWorld = (ServerWorld) world;
                    int day = mod.getZombieManager().getDay(serverWorld);
                    mod.getZombieManager().applyScaling(zombie, day);
                }
                addGoals(zombie);
            }
        });
    }

    private static void addGoals(ZombieEntity zombie) {
        ModConfig config = ModConfig.get();
        if (config == null) return;
        ModConfig.Zombies zombies = config.zombies;
        if (zombies == null) return;
        MCExtremo mod = MCExtremo.getInstance();
        int day = 0;
        if (mod != null && mod.getZombieManager() != null && zombie.getWorld() instanceof ServerWorld world) {
            day = mod.getZombieManager().getDay(world);
        }
        DifficultyPhase phase = DifficultyPhase.fromDay(day);

        MobEntity mob = (MobEntity) zombie;
        GoalSelector goals = mob.goalSelector;

        if (phase.hasDoors() && zombies.inteligencia.romperPuertas) {
            goals.add(1, new BreakDoorGoal(zombie, difficulty ->
                difficulty == Difficulty.HARD || difficulty == Difficulty.NORMAL));
        }

        if (phase.hasDoors() && zombies.inteligencia.abrirPuertas) {
            goals.add(2, new LongDoorInteractGoal(zombie, true));
        }

        if (zombies.inteligencia.saltarAlJugador) {
            goals.add(3, new ZombieAttackGoal(zombie, 1.2D, false));
        }

        if (phase.hasCoordination() && zombies.inteligencia.coordinarAtaque) {
            goals.add(1, new ZombieCoordinationGoal(zombie, zombies.inteligencia.rangoCoordinacion));
        }

        if (zombies.construirBloques.activado) {
            goals.add(1, new ZombieBuildGoal(zombie, zombies.construirBloques.alturaMaxima));
        }

        if (zombies.romperBloques.activado) {
            int intelligence = mod != null && mod.getZombieManager() != null
                ? mod.getZombieManager().getIntelligenceLevel(day) : 0;
            int breakTime = mod != null && mod.getZombieManager() != null
                ? Math.min(zombies.romperBloques.tiempoBase, mod.getZombieManager().getBreakTimeForLevel(intelligence))
                : zombies.romperBloques.tiempoBase;
            goals.add(2, new ZombieBlockBreakGoal(zombie, breakTime));
        }

        if (zombies.horda.activado) {
            goals.add(3, new ZombieSightGoal(zombie));
        }
    }
}
