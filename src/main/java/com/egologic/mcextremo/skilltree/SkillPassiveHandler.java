package com.egologic.mcextremo.skilltree;

import com.egologic.mcextremo.MCExtremo;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class SkillPassiveHandler {
    private static final Map<UUID, Map<Skill, Integer>> cooldowns = new HashMap<>();

    private SkillPassiveHandler() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tickCooldowns());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            cooldowns.remove(handler.getPlayer().getUuid()));

        ServerEntityCombatEvents.AFTER_KILLED_OTHER_ENTITY.register((world, entity, killedEntity) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return;
            if (!(killedEntity instanceof HostileEntity)) return;

            if (hasSkill(player, Skill.SUPERVIVENCIA_T1) && consumeCooldown(player, Skill.SUPERVIVENCIA_T1, 10 * 20)) {
                int food = Math.min(20, player.getHungerManager().getFoodLevel() + 1);
                player.getHungerManager().setFoodLevel(food);
            }
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;

            if (amount >= 6.0f
                && hasSkill(player, Skill.SUPERVIVENCIA_T2)
                && consumeCooldown(player, Skill.SUPERVIVENCIA_T2, 35 * 20)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 5 * 20, 0, false, true));
            }

            if (source.isOf(DamageTypes.FALL)
                && player.getHealth() <= player.getMaxHealth() * 0.5f
                && hasSkill(player, Skill.SUPERVIVENCIA_T3)
                && consumeCooldown(player, Skill.SUPERVIVENCIA_T3, 60 * 20)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 6 * 20, 0, false, true));
            }

            if (player.getHealth() <= player.getMaxHealth() * 0.3f
                && hasSkill(player, Skill.SUPERVIVENCIA_T4)
                && consumeCooldown(player, Skill.SUPERVIVENCIA_T4, 90 * 20)) {
                player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 10 * 20, 0, false, true));
            }
            return true;
        });
    }

    public static boolean hasSkill(ServerPlayerEntity player, Skill skill) {
        MCExtremo mod = MCExtremo.getInstance();
        return mod != null
            && mod.getSkillTreeManager() != null
            && mod.getSkillTreeManager().hasSkill(player.getUuid(), skill);
    }

    public static void applyBossStartBuff(ServerPlayerEntity player) {
        if (hasSkill(player, Skill.CAZADOR_T5)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 20, 0, false, true));
        }
    }

    public static void applyTrialVictoryBuff(ServerPlayerEntity player) {
        if (hasSkill(player, Skill.SUPERVIVENCIA_T5)) {
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 30 * 20, 0, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 12 * 20, 0, false, true));
        }
    }

    private static boolean consumeCooldown(ServerPlayerEntity player, Skill skill, int ticks) {
        Map<Skill, Integer> playerCooldowns = cooldowns.computeIfAbsent(player.getUuid(), uuid -> new HashMap<>());
        if (playerCooldowns.getOrDefault(skill, 0) > 0) return false;
        playerCooldowns.put(skill, ticks);
        return true;
    }

    private static void tickCooldowns() {
        Iterator<Map.Entry<UUID, Map<Skill, Integer>>> playerIterator = cooldowns.entrySet().iterator();
        while (playerIterator.hasNext()) {
            Map<Skill, Integer> playerCooldowns = playerIterator.next().getValue();
            playerCooldowns.replaceAll((skill, ticks) -> Math.max(0, ticks - 1));
            playerCooldowns.entrySet().removeIf(entry -> entry.getValue() <= 0);
            if (playerCooldowns.isEmpty()) {
                playerIterator.remove();
            }
        }
    }
}
