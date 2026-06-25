package com.tuservidor.mcextremo.manager;

import com.tuservidor.mcextremo.MCExtremo;
import com.tuservidor.mcextremo.config.ModConfig;
import com.tuservidor.mcextremo.skilltree.Skill;
import com.tuservidor.mcextremo.skilltree.SkillPassiveHandler;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import java.util.*;

public class ZombieHordeManager {
    private final MCExtremo mod;
    private final Map<UUID, HordeEvent> activeHordes = new HashMap<>();
    private final Map<UUID, Integer> hordeCooldowns = new HashMap<>();
    private final Random random = new Random();
    private int globalZombieCount = 0;

    public ZombieHordeManager(MCExtremo mod) {
        this.mod = mod;
    }

    private ModConfig.Zombies.Horda config() {
        return ModConfig.get().zombies.horda;
    }

    private record HordeTier(
        String name, BossBar.Color color,
        int minZombies, int maxZombies, int duration, int exp
    ) {}

    private HordeTier getTier(int day) {
        ModConfig.Zombies.Horda c = config();
        if (day < c.diaInicio) return null;
        if (day < 15)  return new HordeTier("\u00A7eMini Horda",     BossBar.Color.YELLOW, c.miniMin,  c.miniMax,  c.miniDuracion,  c.expMini);
        if (day < 35)  return new HordeTier("\u00A76Horda",          BossBar.Color.RED,     c.hordaMin, c.hordaMax, c.hordaDuracion, c.expHorda);
        if (day < 65)  return new HordeTier("\u00A7cHorda Grande",   BossBar.Color.RED,     c.grandeMin, c.grandeMax, c.grandeDuracion, c.expGrande);
        if (day < 100) return new HordeTier("\u00A75Horda Masiva",   BossBar.Color.PURPLE,  c.masivaMin, c.masivaMax, c.masivaDuracion, c.expMasiva);
        return                new HordeTier("\u00A74\u00A7lHorda Extrema", BossBar.Color.PURPLE, c.extremaMin, c.extremaMax, c.extremaDuracion, c.expExtrema);
    }

    public int getHordeSize(int day) {
        HordeTier tier = getTier(day);
        if (tier == null) return 0;
        return tier.minZombies + random.nextInt(tier.maxZombies - tier.minZombies + 1);
    }

    public String getHordeTierName(int day) {
        HordeTier tier = getTier(day);
        return tier != null ? tier.name : null;
    }

    public int getHordeDuration(int day) {
        HordeTier tier = getTier(day);
        return tier != null ? tier.duration : 0;
    }

    public int getHordeExpReward(int day) {
        HordeTier tier = getTier(day);
        if (tier == null) return 0;
        return tier.exp + (day / 10);
    }

    public void onZombieSeePlayer(ZombieEntity zombie, ServerPlayerEntity player) {
        if (!config().activado) return;
        if (mod.getReviveTrialManager().isInTrial(player.getUuid())) return;
        if (activeHordes.containsKey(player.getUuid())) return;

        Integer cooldown = hordeCooldowns.get(player.getUuid());
        if (cooldown != null && cooldown > 0) return;

        ServerWorld world = (ServerWorld) zombie.getWorld();
        int day = mod.getZombieManager().getDay(world);

        if (!mod.getZombieManager().canUseHordes(day)) return;

        int hordeSize = getHordeSize(day);
        if (hordeSize <= 0) return;

        if (globalZombieCount >= config().maxGlobal) return;

        int radio = Math.min(config().radioMaximo, config().radioActivacion + day);

        Box searchBox = new Box(
            player.getX() - radio, player.getY() - 30, player.getZ() - radio,
            player.getX() + radio, player.getY() + 30, player.getZ() + radio
        );

        List<ZombieEntity> nearby = world.getEntitiesByClass(
            ZombieEntity.class, searchBox,
            z -> z.isAlive() && !z.isRemoved() && z.squaredDistanceTo(player) < radio * radio
        );

        if (nearby.isEmpty()) return;

        int count = Math.min(nearby.size(), hordeSize);
        List<ZombieEntity> hordeZombies = new ArrayList<>(nearby.subList(0, count));

        HordeTier tier = getTier(day);
        for (ZombieEntity z : hordeZombies) {
            mod.getZombieManager().applyScaling(z, day);
            mod.getZombieManager().applyHordeSpeedBoost(z);
            if (z.getTarget() == null) {
                z.setTarget(player);
            }
        }

        globalZombieCount += count;

        ServerBossBar bossBar = new ServerBossBar(
            Text.literal("\u00A74\u2620 " + tier.name() + " \u00A77- \u00A7f" + count + " zombies"),
            tier.color(),
            BossBar.Style.NOTCHED_10
        );
        bossBar.setPercent(1.0f);
        bossBar.addPlayer(player);

        int duration = tier.duration() * 20;
        HordeEvent event = new HordeEvent(player, bossBar, hordeZombies, count, duration, day);
        activeHordes.put(player.getUuid(), event);
        hordeCooldowns.put(player.getUuid(), 0);

        player.sendMessage(Text.literal("\u00A74\u2620 \u00A7c\u00A7l" + tier.name() + " ACTIVADA! \u00A77" + count + " zombies se dirigen hacia ti."), true);
    }

    public void tick(ServerWorld world) {
        Iterator<Map.Entry<UUID, Integer>> cdIt = hordeCooldowns.entrySet().iterator();
        while (cdIt.hasNext()) {
            Map.Entry<UUID, Integer> entry = cdIt.next();
            if (entry.getValue() > 0) {
                entry.setValue(entry.getValue() - 1);
            }
            if (entry.getValue() <= 0 && !activeHordes.containsKey(entry.getKey())) {
                cdIt.remove();
            }
        }

        Iterator<Map.Entry<UUID, HordeEvent>> it = activeHordes.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<UUID, HordeEvent> entry = it.next();
            HordeEvent event = entry.getValue();

            event.ticksRemaining--;

            if (event.ticksRemaining <= 0 || event.player.isDead() || !event.player.isAlive()) {
                endHorde(event);
                it.remove();
                continue;
            }

            int aliveZombies = 0;
            for (ZombieEntity z : event.zombies) {
                if (z.isAlive() && !z.isRemoved()) {
                    aliveZombies++;
                }
            }

            globalZombieCount = Math.max(0, globalZombieCount - (event.totalZombies - aliveZombies));
            event.totalZombies = aliveZombies;

            if (aliveZombies == 0) {
                int exp = getHordeExpReward(event.day);
                if (SkillPassiveHandler.hasSkill(event.player, Skill.CAZADOR_T1)) {
                    exp = Math.round(exp * 1.10f);
                }
                event.player.addExperience(exp);
                String tierName = getHordeTierName(event.day);
                mod.getRewardManager().giveHordeRewards(event.player, tierName, event.day);
                if (SkillPassiveHandler.hasSkill(event.player, Skill.CAZADOR_T4)) {
                    event.player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 8 * 20, 0, false, true));
                }
                event.player.sendMessage(Text.literal("\u00A7a\u2714 \u00A7a\u00A7l\u00A1" + tierName + " ELIMINADA! \u00A77Sobreviviste. \u00A7e+" + exp + " EXP"), true);
                hordeCooldowns.put(event.player.getUuid(), config().cooldownSegundos * 20);
                endHorde(event);
                it.remove();
                continue;
            }

            float progress = (float) aliveZombies / event.initialZombies;
            event.bossBar.setPercent(progress);
            event.bossBar.setName(Text.literal(
                "\u00A74\u2620 " + getHordeTierName(event.day) + " \u00A77- \u00A7f" + aliveZombies + "/" + event.initialZombies + " zombies"
            ));

            for (ZombieEntity z : event.zombies) {
                if (z.isAlive() && !z.isRemoved() && z.getTarget() == null) {
                    z.setTarget(event.player);
                }
            }

            int glowThreshold = SkillPassiveHandler.hasSkill(event.player, Skill.CAZADOR_T3) ? 2 : 1;
            if (aliveZombies <= glowThreshold) {
                for (ZombieEntity z : event.zombies) {
                    if (z.isAlive() && !z.isRemoved()) {
                        z.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                            net.minecraft.entity.effect.StatusEffects.GLOWING, 60, 0, false, true), event.player);

                        double dist = z.squaredDistanceTo(event.player);
                        if (dist > 400.0) {
                            net.minecraft.util.math.BlockPos tpPos = findTeleportPos(event.player);
                            z.teleport(tpPos.getX() + 0.5, tpPos.getY(), tpPos.getZ() + 0.5);
                            event.player.sendMessage(Text.literal("\u00A7e\u2731 \u00A77Ultimo zombie teletransportado cerca!"), true);
                        }
                    }
                }
            }
        }
    }

    private void endHorde(HordeEvent event) {
        int aliveCount = 0;
        for (ZombieEntity z : event.zombies) {
            if (z.isAlive() && !z.isRemoved()) aliveCount++;
        }
        globalZombieCount = Math.max(0, globalZombieCount - aliveCount);
        event.bossBar.clearPlayers();
        event.bossBar.setVisible(false);
    }

    private net.minecraft.util.math.BlockPos findTeleportPos(ServerPlayerEntity player) {
        net.minecraft.util.math.BlockPos playerPos = player.getBlockPos();
        net.minecraft.world.World world = player.getWorld();
        for (int attempts = 0; attempts < 20; attempts++) {
            int dx = random.nextInt(11) - 5;
            int dz = random.nextInt(11) - 5;
            net.minecraft.util.math.BlockPos candidate = playerPos.add(dx, 0, dz);

            net.minecraft.util.math.BlockPos ground = world.getTopPosition(
                net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, candidate);

            if (world.getBlockState(ground.down()).isSolidBlock(world, ground.down()) &&
                world.getBlockState(ground).isAir() &&
                world.getBlockState(ground.up()).isAir()) {
                return ground;
            }
        }
        return playerPos;
    }

    public void removePlayer(ServerPlayerEntity player) {
        HordeEvent event = activeHordes.remove(player.getUuid());
        if (event != null) {
            endHorde(event);
        }
        hordeCooldowns.remove(player.getUuid());
    }

    private static class HordeEvent {
        final ServerPlayerEntity player;
        final ServerBossBar bossBar;
        final List<ZombieEntity> zombies;
        final int initialZombies;
        final int day;
        int totalZombies;
        int ticksRemaining;

        HordeEvent(ServerPlayerEntity player, ServerBossBar bossBar, List<ZombieEntity> zombies, int initialZombies, int duration, int day) {
            this.player = player;
            this.bossBar = bossBar;
            this.zombies = new ArrayList<>(zombies);
            this.initialZombies = initialZombies;
            this.totalZombies = initialZombies;
            this.ticksRemaining = duration;
            this.day = day;
        }
    }
}
