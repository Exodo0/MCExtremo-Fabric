package com.egologic.mcextremo.manager;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.skilltree.Skill;
import com.egologic.mcextremo.skilltree.SkillPassiveHandler;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.Heightmap;

import java.util.*;

public class ZombieHordeManager {
    private static final String HORDE_TAG = "mcextremo_horde";
    private static final double LEASH_DISTANCE_SQ = 48.0 * 48.0;

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

        int activeHordeZombies = countActiveHordeZombies();
        globalZombieCount = activeHordeZombies;
        if (activeHordeZombies >= config().maxGlobal) return;

        int radio = Math.min(config().radioMaximo, config().radioActivacion + day);

        Box searchBox = new Box(
            player.getX() - radio, player.getY() - 30, player.getZ() - radio,
            player.getX() + radio, player.getY() + 30, player.getZ() + radio
        );

        List<ZombieEntity> nearby = world.getEntitiesByClass(
            ZombieEntity.class, searchBox,
            z -> z.isAlive()
                && !z.isRemoved()
                && !z.getCommandTags().contains("mcextremo_trial")
                && !z.getCommandTags().contains("mcextremo_event_trial")
                && !z.getCommandTags().contains(HORDE_TAG)
                && z.squaredDistanceTo(player) < radio * radio
        );

        if (nearby.isEmpty()) return;
        nearby.sort(Comparator.comparingDouble(z -> z.squaredDistanceTo(player)));

        int availableSlots = Math.max(0, config().maxGlobal - activeHordeZombies);
        int count = Math.min(Math.min(nearby.size(), hordeSize), availableSlots);
        if (count <= 0) return;
        List<ZombieEntity> hordeZombies = new ArrayList<>(nearby.subList(0, count));

        HordeTier tier = getTier(day);
        for (ZombieEntity z : hordeZombies) {
            mod.getZombieManager().applyScaling(z, day);
            mod.getZombieManager().applyHordeSpeedBoost(z);
            prepareHordeZombie(world, z, player, true);
        }

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
        globalZombieCount = activeHordeZombies + count;
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

            if (event.player.isDead() || !event.player.isAlive()) {
                endHorde(event);
                it.remove();
                continue;
            }

            List<ZombieEntity> alive = getAliveZombies(event);
            int aliveZombies = alive.size();
            globalZombieCount = countActiveHordeZombies();

            if (event.ticksRemaining <= 0) {
                rewardHorde(event, true);
                endHorde(event);
                it.remove();
                continue;
            }

            if (aliveZombies == 0) {
                rewardHorde(event, false);
                endHorde(event);
                it.remove();
                continue;
            }

            float progress = (float) aliveZombies / event.initialZombies;
            event.bossBar.setPercent(progress);
            event.bossBar.setName(Text.literal(
                "\u00A74\u2620 " + getHordeTierName(event.day) + " \u00A77- \u00A7f" + aliveZombies + "/" + event.initialZombies + " zombies"
            ));

            for (ZombieEntity z : alive) {
                prepareHordeZombie(world, z, event.player, false);
                if (z.squaredDistanceTo(event.player) > LEASH_DISTANCE_SQ && world.getTime() % 40L == 0L) {
                    teleportNearPlayer(world, z, event.player);
                    event.player.sendMessage(Text.literal("\u00A7e\u2731 \u00A77Un zombie de la horda fue reposicionado cerca."), true);
                }
            }

            int glowThreshold = SkillPassiveHandler.hasSkill(event.player, Skill.CAZADOR_T3) ? 2 : 1;
            if (aliveZombies <= glowThreshold) {
                for (ZombieEntity z : alive) {
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

    private void endHorde(HordeEvent event) {
        ServerWorld world = (ServerWorld) event.player.getWorld();
        for (UUID uuid : new HashSet<>(event.zombieIds)) {
            if (world.getEntity(uuid) instanceof ZombieEntity zombie) {
                zombie.removeCommandTag(HORDE_TAG);
            }
        }
        event.zombieIds.clear();
        globalZombieCount = countActiveHordeZombies();
        event.bossBar.clearPlayers();
        event.bossBar.setVisible(false);
    }

    private void rewardHorde(HordeEvent event, boolean timeout) {
        int exp = getHordeExpReward(event.day);
        if (timeout) {
            exp = Math.max(1, Math.round(exp * 0.65f));
        }
        if (SkillPassiveHandler.hasSkill(event.player, Skill.CAZADOR_T1)) {
            exp = Math.round(exp * 1.10f);
        }
        event.player.addExperience(exp);
        String tierName = getHordeTierName(event.day);
        mod.getRewardManager().giveHordeRewards(event.player, tierName, event.day);
        if (SkillPassiveHandler.hasSkill(event.player, Skill.CAZADOR_T4)) {
            event.player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 8 * 20, 0, false, true));
        }
        String status = timeout ? "SOBREVIVIDA" : "ELIMINADA";
        event.player.sendMessage(Text.literal("\u00A7a\u2714 \u00A7a\u00A7l\u00A1" + tierName + " " + status + "! \u00A77Recompensa recibida. \u00A7e+" + exp + " EXP"), true);
        hordeCooldowns.put(event.player.getUuid(), config().cooldownSegundos * 20);
    }

    private void prepareHordeZombie(ServerWorld world, ZombieEntity zombie, ServerPlayerEntity player, boolean initialPlacement) {
        zombie.addCommandTag(HORDE_TAG);
        zombie.setPersistent();
        zombie.setTarget(player);
        zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, initialPlacement ? 8 * 20 : 60, 0, false, true), player);
        if (initialPlacement) {
            teleportNearPlayer(world, zombie, player);
        }
    }

    private void teleportNearPlayer(ServerWorld world, ZombieEntity zombie, ServerPlayerEntity player) {
        BlockPos tpPos = findTeleportPos(player);
        zombie.teleport(tpPos.getX() + 0.5, tpPos.getY(), tpPos.getZ() + 0.5);
        zombie.setTarget(player);
        zombie.velocityModified = true;
    }

    private List<ZombieEntity> getAliveZombies(HordeEvent event) {
        ServerWorld world = (ServerWorld) event.player.getWorld();
        List<ZombieEntity> alive = new ArrayList<>();
        Iterator<UUID> iterator = event.zombieIds.iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (world.getEntity(uuid) instanceof ZombieEntity zombie && zombie.isAlive() && !zombie.isRemoved()) {
                alive.add(zombie);
            } else {
                iterator.remove();
            }
        }
        return alive;
    }

    private int countActiveHordeZombies() {
        int count = 0;
        for (HordeEvent event : activeHordes.values()) {
            count += getAliveZombies(event).size();
        }
        return count;
    }

    private net.minecraft.util.math.BlockPos findTeleportPos(ServerPlayerEntity player) {
        net.minecraft.util.math.BlockPos playerPos = player.getBlockPos();
        net.minecraft.world.World world = player.getWorld();
        for (int attempts = 0; attempts < 20; attempts++) {
            int dx = random.nextInt(17) - 8;
            int dz = random.nextInt(17) - 8;
            net.minecraft.util.math.BlockPos candidate = playerPos.add(dx, 0, dz);

            net.minecraft.util.math.BlockPos ground = world.getTopPosition(
                Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, candidate);

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
        final Set<UUID> zombieIds;
        final int initialZombies;
        final int day;
        int ticksRemaining;

        HordeEvent(ServerPlayerEntity player, ServerBossBar bossBar, List<ZombieEntity> zombies, int initialZombies, int duration, int day) {
            this.player = player;
            this.bossBar = bossBar;
            this.zombieIds = new HashSet<>();
            for (ZombieEntity zombie : zombies) {
                this.zombieIds.add(zombie.getUuid());
            }
            this.initialZombies = initialZombies;
            this.ticksRemaining = duration;
            this.day = day;
        }
    }
}
