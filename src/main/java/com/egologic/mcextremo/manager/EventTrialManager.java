package com.egologic.mcextremo.manager;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.network.TrialCinematicNetworking;
import com.egologic.mcextremo.util.TextUtil;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.EvokerFangsEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.StrayEntity;
import net.minecraft.entity.mob.WitherSkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class EventTrialManager {
    public static final String STATE_EVENT_TRIAL = "EVENT_TRIAL";
    public static final String MOB_TAG = "mcextremo_event_trial";

    private static final int WAVE_DELAY_TICKS = 5 * 20;

    private final MCExtremo mod;
    private EventTrial activeEvent;

    private final EventTrialArenaBuilder arenaBuilder = new EventTrialArenaBuilder();
    private final EventTrialCinematicController cinematicController = new EventTrialCinematicController();

    public EventTrialManager(MCExtremo mod) {
        this.mod = mod;
    }

    public boolean isActive() {
        return activeEvent != null;
    }

    public boolean isParticipant(UUID uuid) {
        return activeEvent != null && activeEvent.participants.contains(uuid);
    }

    public String getStatus() {
        if (activeEvent == null) return "Sin evento activo.";
        return "Oleada " + activeEvent.wave + "/" + ModConfig.get().eventTrial.oleadas
            + " | Jugadores " + activeEvent.participants.size()
            + " | Mobs " + activeEvent.mobs.size()
            + " | Tokens " + activeEvent.tokens;
    }

    public boolean startEvent(ServerPlayerEntity admin) {
        MinecraftServer server = admin.getServer();
        if (activeEvent != null) {
            admin.sendMessage(TextUtil.literal("&cYa hay un event trial activo."), false);
            return false;
        }
        ModConfig.EventTrial config = ModConfig.get().eventTrial;
        if (!config.activado) {
            admin.sendMessage(TextUtil.literal("&cEl event trial esta deshabilitado."), false);
            return false;
        }

        List<ServerPlayerEntity> players = collectParticipants(server);
        if (players.isEmpty()) {
            admin.sendMessage(TextUtil.literal("&cNo hay jugadores vivos disponibles para el evento."), false);
            return false;
        }

        ServerWorld world = server.getWorld(World.END);
        if (world == null) world = server.getOverworld();

        BlockPos center = getEventCenter(config);

        EventTrial event = new EventTrial();
        event.center = center;
        event.initialPlayers = players.size();
        event.tokens = players.size() * config.tokensPorJugador;
        event.wave = 0;
        event.actionCooldown = 0;
        event.introTicks = config.introActivada ? config.introDuracionTicks : 0;
        event.phase = EventTrialPhase.GENERATING;
        event.arenaBuild = new EventArenaBuildTask(center, config.radioArena);
        event.bossCooldown = 12 * 20;
        event.fangsCooldown = 10 * 20;
        event.fireCooldown = 8 * 20;
        event.comboCooldown = 16 * 20;
        event.bossBar = new ServerBossBar(
            TextUtil.literal("&5Event Trial &7- Generando arena"),
            BossBar.Color.PURPLE,
            BossBar.Style.NOTCHED_20
        );
        event.bossBar.setPercent(1.0f);
        for (ServerPlayerEntity player : players) {
            event.pendingPlayers.add(player.getUuid());
            event.bossBar.addPlayer(player);
        }
        activeEvent = event;

        broadcast(server, "&5&lEVENT TRIAL &7- &eGenerando arena para " + players.size() + " jugador(es).");
        return true;
    }

    public void stopEvent(MinecraftServer server, String reason) {
        if (activeEvent == null) return;
        finishFailure(server, reason);
    }

    public void tick(MinecraftServer server) {
        if (activeEvent == null) return;

        EventTrial event = activeEvent;
        ServerWorld world = server.getWorld(World.END);
        if (world == null) world = server.getOverworld();

        if (event.phase == EventTrialPhase.GENERATING) {
            tickArenaGeneration(server, world, event);
            return;
        }

        event.participants.removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
        if (event.participants.isEmpty()) {
            cleanupEvent(server, event);
            activeEvent = null;
            return;
        }

        for (UUID uuid : new HashSet<>(event.participants)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;
            keepPlayerInside(player, world, event.center);
            if (player.getWorld() != world) {
                teleportToArena(player, world, event.center);
            }
        }

        if (event.phase == EventTrialPhase.INTRO) {
            cinematicController.tickIntro(server, world, event);
            return;
        }

        cleanupEndermen(world, event.center, ModConfig.get().eventTrial.radioArena);
        keepMobsInside(world, event);
        if (world.getTime() % 40L == 0L) {
            spawnAmbientEffects(world, event.center);
        }

        if (event.victoryDelay) {
            updateVictoryDelay(server, event);
            if (--event.actionCooldown <= 0) {
                finishVictory(server);
            }
            return;
        }

        if (event.actionCooldown > 0) {
            event.actionCooldown--;
            updateBossBar(server, event, countAliveMobs(world, event), null);
            return;
        }

        int alive = countAliveMobs(world, event);
        if (event.bossDeathTick >= 0) {
            tickBossDeath(server, world, event);
            updateBossBar(server, event, 0, null);
            return;
        }
        if (alive <= 0) {
            if (event.wave >= ModConfig.get().eventTrial.oleadas) {
                beginBossDeath(world, event);
            } else {
                if (event.wave > 0) {
                    spawnWaveClearEffects(world, event.center);
                }
                event.wave++;
                spawnWave(world, event);
                event.actionCooldown = WAVE_DELAY_TICKS;
            }
        } else {
            markLastAliveMobs(world, event, alive);
            if (event.wave >= ModConfig.get().eventTrial.oleadas) {
                tickBoss(server, world, event);
            }
            updateBossBar(server, event, alive, getBoss(world, event));
        }
    }

    public boolean handleDeath(ServerPlayerEntity player) {
        if (!isParticipant(player.getUuid()) || activeEvent == null) return false;

        EventTrial event = activeEvent;
        if (event.phase != EventTrialPhase.INTRO) {
            event.tokens = Math.max(0, event.tokens - 1);
        }
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(10.0f);
        if (event.phase == EventTrialPhase.INTRO) {
            ServerWorld world = getEventWorld(player.getServer());
            cinematicController.prepareIntroViewer(player, world, event.center, ModConfig.get().eventTrial);
            Entity camera = event.cameraEntityId == null ? null : world.getEntity(event.cameraEntityId);
            if (camera != null) {
                player.setCameraEntity(camera);
            }
        } else {
            teleportToArena(player, getEventWorld(player.getServer()), event.center);
        }
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 20 * 5, 1, false, true));
        player.sendMessage(TextUtil.literal(event.phase == EventTrialPhase.INTRO
            ? "&eEntrada estabilizada. &7No pierdes tokens durante la intro."
            : "&cCaida evitada. &7Tokens grupales restantes: &e" + event.tokens), false);

        if (event.phase != EventTrialPhase.INTRO && event.tokens <= 0) {
            finishFailure(player.getServer(), "&cEl grupo se quedo sin tokens. Event trial fallido.");
        }
        return true;
    }

    public void handleDisconnect(ServerPlayerEntity player) {
        if (activeEvent == null || !activeEvent.participants.remove(player.getUuid())) return;
        activeEvent.bossBar.removePlayer(player);
        cinematicController.resetIntroPlayerState(player);
        TrialCinematicNetworking.sendStop(player);
        player.sendMessage(Text.empty(), true);
        cinematicController.discardIntroAvatar(getEventWorld(player.getServer()), activeEvent, player.getUuid());
        restoreInventory(player);
        mod.getDataManager().setTrialState(player.getUuid(), ReviveTrialManager.STATE_ALIVE);
    }

    public boolean recoverInterruptedEvent(ServerPlayerEntity player) {
        if (!STATE_EVENT_TRIAL.equals(mod.getDataManager().getTrialState(player.getUuid()))) {
            return false;
        }
        restoreInventory(player);
        TrialCinematicNetworking.sendStop(player);
        player.sendMessage(Text.empty(), true);
        mod.getDataManager().setTrialState(player.getUuid(), ReviveTrialManager.STATE_ALIVE);
        mod.getLivesManager().restoreAfterRevive(player);
        player.sendMessage(TextUtil.literal("&eTu event trial fue interrumpido. &7Se restauro tu inventario."), false);
        return true;
    }

    private List<ServerPlayerEntity> collectParticipants(MinecraftServer server) {
        List<ServerPlayerEntity> players = new ArrayList<>();
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (mod.getLivesManager().getVidas(player.getUuid()) <= 0) continue;
            if (mod.getReviveTrialManager().isInTrial(player.getUuid())) continue;
            GameMode mode = player.interactionManager.getGameMode();
            if (mode == GameMode.SPECTATOR || mode == GameMode.CREATIVE) continue;
            players.add(player);
        }
        return players;
    }

    private void tickArenaGeneration(MinecraftServer server, ServerWorld world, EventTrial event) {
        event.pendingPlayers.removeIf(uuid -> server.getPlayerManager().getPlayer(uuid) == null);
        if (event.pendingPlayers.isEmpty()) {
            cleanupEvent(server, event);
            activeEvent = null;
            return;
        }

        boolean complete = arenaBuilder.processArenaBuild(world, event.arenaBuild, 520);
        arenaBuilder.updateGenerationBar(event);
        if (!complete) return;

        List<ServerPlayerEntity> players = new ArrayList<>();
        for (UUID uuid : event.pendingPlayers) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) players.add(player);
        }
        if (players.isEmpty()) {
            cleanupEvent(server, event);
            activeEvent = null;
            return;
        }

        ModConfig.EventTrial config = ModConfig.get().eventTrial;
        event.initialPlayers = players.size();
        event.tokens = players.size() * config.tokensPorJugador;
        event.actionCooldown = Math.max(20, config.preparacionSegundos * 20);
        event.introTicks = config.introActivada ? config.introDuracionTicks : 0;
        event.phase = config.introActivada ? EventTrialPhase.INTRO : EventTrialPhase.PREPARATION;
        event.pendingPlayers.clear();
        event.bossBar.setName(TextUtil.literal(config.introActivada ? "&5Event Trial &7- Entrada" : "&5Event Trial &7- Preparacion"));
        event.bossBar.setPercent(1.0f);

        ArmorStandEntity camera = config.introActivada ? cinematicController.spawnCentralCamera(world, event.center) : null;
        if (camera != null) {
            event.cameraEntityId = camera.getUuid();
        }

        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity player = players.get(i);
            BlockPos landing = cinematicController.getLandingPosition(world, event.center, i, players.size());
            event.participants.add(player.getUuid());
            event.landingPositions.put(player.getUuid(), landing);
            saveAndHideInventory(player);
            giveEventKit(player);
            mod.getDataManager().setTrialState(player.getUuid(), STATE_EVENT_TRIAL);
            player.changeGameMode(GameMode.SURVIVAL);
            if (config.introActivada) {
                cinematicController.prepareIntroViewer(player, world, event.center, config);
                if (camera != null) {
                    player.setCameraEntity(camera);
                }
                cinematicController.createIntroAvatar(world, event, player, landing, config);
                TrialCinematicNetworking.sendEventIntro(player, Vec3d.ofCenter(event.center), config.introDuracionTicks,
                    "Entrando al Event Trial", "La arena te reclama");
            } else {
                cinematicController.teleportToLanding(player, world, landing);
            }
        }

        broadcast(server, "&5&lEVENT TRIAL &7- &e" + players.size() + " jugador(es) entraron al evento.");
    }



































    private void spawnWave(ServerWorld world, EventTrial event) {
        discardMobs(world, event.mobs);
        event.mobs.clear();
        int amount = getWaveSize(event.wave, event.initialPlayers);
        int cap = ModConfig.get().eventTrial.maxMobsActivos;
        amount = Math.min(amount, cap);
        spawnWaveEffects(world, event.center, event.wave);

        if (event.wave >= ModConfig.get().eventTrial.oleadas) {
            ZombieEntity boss = EntityType.ZOMBIE.create(world);
            if (boss != null) {
                BlockPos spawn = findArenaSpawn(world, event.center.add(0, 4, 0), event.center);
                boss.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0f, 0.0f);
                boss.addCommandTag(MOB_TAG);
                boss.setCustomName(Text.literal("\u00A75Coloso del Evento"));
                boss.setCustomNameVisible(true);
                boss.setPersistent();
                boss.equipStack(EquipmentSlot.MAINHAND, enchantedItem(Items.NETHERITE_AXE, Enchantments.SHARPNESS, 3));
                boss.equipStack(EquipmentSlot.HEAD, enchantedItem(Items.NETHERITE_HELMET, Enchantments.PROTECTION, 3));
                boss.equipStack(EquipmentSlot.CHEST, enchantedItem(Items.NETHERITE_CHESTPLATE, Enchantments.PROTECTION, 3));
                boss.equipStack(EquipmentSlot.LEGS, enchantedItem(Items.NETHERITE_LEGGINGS, Enchantments.PROTECTION, 3));
                boss.equipStack(EquipmentSlot.FEET, enchantedItem(Items.NETHERITE_BOOTS, Enchantments.PROTECTION, 3));
                world.spawnEntity(boss);
                setAttr(boss, EntityAttributes.GENERIC_MAX_HEALTH, 260.0 + event.initialPlayers * 65.0);
                setAttr(boss, EntityAttributes.GENERIC_ATTACK_DAMAGE, 9.0 + event.initialPlayers * 0.75);
                setAttr(boss, EntityAttributes.GENERIC_ARMOR, 12.0);
                boss.setHealth(boss.getMaxHealth());
                boss.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 60 * 10, 1, false, true));
                event.bossId = boss.getUuid();
                event.mobs.add(boss.getUuid());
                broadcast(world.getServer(), "&5El Coloso del Evento ha aparecido.");
            }
            return;
        }

        for (int i = 0; i < amount; i++) {
            MobEntity mob = createWaveMob(world, event.wave, i);
            if (mob == null) continue;
            BlockPos spawn = findArenaSpawn(world, getSpawnAnchor(event.center, event.wave, i), event.center);
            double angle = Math.atan2(event.center.getZ() - spawn.getZ(), event.center.getX() - spawn.getX());
            mob.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, (float) Math.toDegrees(angle) - 90.0f, 0.0f);
            mob.addCommandTag(MOB_TAG);
            equipWaveMob(mob, event.wave, i);
            world.spawnEntity(mob);
            if (mob instanceof ZombieEntity zombie) {
                mod.getZombieManager().applyScaling(zombie, Math.max(35, mod.getZombieManager().getDay(world)));
            }
            boostEventMobStats(mob, event.wave, false);
            targetNearestParticipant(mob);
            event.mobs.add(mob.getUuid());
        }
        broadcast(world.getServer(), "&5Oleada &e" + event.wave + "&5/" + ModConfig.get().eventTrial.oleadas + " &7- &c" + amount + " enemigos.");
    }

    private void tickBoss(MinecraftServer server, ServerWorld world, EventTrial event) {
        ZombieEntity boss = getBoss(world, event);
        if (boss == null || !boss.isAlive()) return;
        if (event.bossPhaseThree) {
            tickVeilPhase(server, world, event, boss);
            return;
        }

        targetNearestParticipant(boss);
        spawnBossAura(world, event, boss);

        float ratio = boss.getHealth() / Math.max(1.0f, boss.getMaxHealth());
        if (!event.bossPhaseTwo && ratio <= 0.50f) {
            event.bossPhaseTwo = true;
            boss.setHealth(Math.min(boss.getMaxHealth(), boss.getHealth() + boss.getMaxHealth() * 0.50f));
            boss.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 60 * 5, 1, false, true));
            boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 20, 1, false, true));
            spawnLightning(world, boss.getBlockPos());
            spawnBossPhaseBurst(world, boss);
            broadcast(server, "&4El Coloso entra en Fase 2.");
        }
        if (event.bossPhaseTwo && !event.bossPhaseFour && ratio <= 0.25f) {
            startVeilPhase(server, world, event, boss);
            return;
        }

        if (--event.bossCooldown <= 0) {
            spawnBossMinions(world, event, event.bossPhaseFour ? 3 : event.bossPhaseTwo ? 4 : 3);
            event.bossCooldown = event.bossPhaseFour ? 12 * 20 : event.bossPhaseTwo ? 13 * 20 : 16 * 20;
        }
        if (--event.fangsCooldown <= 0) {
            castFangs(world, event, event.bossPhaseTwo);
            event.fangsCooldown = event.bossPhaseFour ? 12 * 20 : event.bossPhaseTwo ? 15 * 20 : 18 * 20;
        }
        if (--event.fireCooldown <= 0) {
            castFireCone(world, event, boss, event.bossPhaseTwo);
            event.fireCooldown = event.bossPhaseFour ? 10 * 20 : event.bossPhaseTwo ? 12 * 20 : 15 * 20;
        }
        if (--event.comboCooldown <= 0) {
            executeBossCombo(server, world, event, boss);
            event.comboCooldown = event.bossPhaseFour ? 14 * 20 : event.bossPhaseTwo ? 16 * 20 : 20 * 20;
        }
        if (event.bossPhaseFour) {
            tickSkullBurst(world, event, boss);
        }
    }

    private void startVeilPhase(MinecraftServer server, ServerWorld world, EventTrial event, ZombieEntity boss) {
        event.bossPhaseThree = true;
        event.veilTransitionTick = 0;
        event.veilTransitionDone = false;
        event.furyTransitionActive = false;
        event.furyTransitionTick = 0;
        event.furyLanding = null;
        event.veilRegenCooldown = 3 * 20;
        event.skullCooldown = 8 * 20;
        event.skullBurstDelay = 0;
        event.skullsInBurst = 0;
        boss.setAiDisabled(true);
        boss.setTarget(null);
        boss.setVelocity(0.0, 0.0, 0.0);
        boss.velocityModified = true;
        broadcast(server, "&5El Coloso se detiene...");
    }

    private void tickVeilPhase(MinecraftServer server, ServerWorld world, EventTrial event, ZombieEntity boss) {
        boss.setTarget(null);
        if (!event.veilTransitionDone) {
            tickVeilTransition(server, world, event, boss);
            return;
        }
        if (event.furyTransitionActive) {
            tickFuryTransition(server, world, event, boss);
            return;
        }
        boss.setVelocity(0.0, 0.0, 0.0);
        boss.velocityModified = true;
        spawnVeilAura(world, event, boss, 6.0, true);

        if (--event.veilRegenCooldown <= 0) {
            float heal = boss.getMaxHealth() * 0.06f;
            boss.setHealth(Math.min(boss.getMaxHealth(), boss.getHealth() + heal));
            event.veilRegenCooldown = 3 * 20;
            broadcast(server, "&5El Velo regenera al Coloso. &7Derroten al Guardian.");
        }

        Entity guardian = event.veilGuardianId == null ? null : world.getEntity(event.veilGuardianId);
        if (!(guardian instanceof SpiderEntity spider) || !spider.isAlive()) {
            startFuryPhase(server, world, event, boss);
        } else {
            targetNearestParticipant(spider);
            if (world.getTime() % 200L == 0L) {
                spider.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 60 * 5, 0, false, true));
            }
        }
    }

    private void startFuryPhase(MinecraftServer server, ServerWorld world, EventTrial event, ZombieEntity boss) {
        event.veilGuardianId = null;
        event.furyTransitionActive = true;
        event.furyTransitionTick = 0;
        event.furyLanding = findArenaSpawn(world, event.center.add(0, 3, 0), event.center);
        boss.setNoGravity(false);
        boss.setVelocity(0.0, -0.18, 0.0);
        boss.velocityModified = true;
        broadcast(server, "&4El Velo se rompe. El Coloso cae.");
    }

    private void completeFuryPhase(MinecraftServer server, ServerWorld world, EventTrial event, ZombieEntity boss) {
        event.bossPhaseThree = false;
        event.bossPhaseFour = true;
        event.furyTransitionActive = false;
        BlockPos landing = event.furyLanding != null ? event.furyLanding : findArenaSpawn(world, event.center.add(0, 3, 0), event.center);
        boss.teleport(landing.getX() + 0.5, landing.getY(), landing.getZ() + 0.5);
        boss.setInvulnerable(false);
        boss.setNoGravity(false);
        boss.setAiDisabled(false);
        boss.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 60 * 10, 1, false, true));
        boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 60 * 10, 0, false, true));
        event.bossCooldown = 9 * 20;
        event.fangsCooldown = 8 * 20;
        event.fireCooldown = 7 * 20;
        event.comboCooldown = 10 * 20;
        event.skullCooldown = 7 * 20;
        event.skullBurstDelay = 0;
        event.skullsInBurst = 0;
        spawnRegearChests(world, event);
        spawnBossImpact(world, landing);
        targetNearestParticipant(boss);
        broadcast(server, "&4&lLA FURIA &7- &cEl Coloso cae al suelo. Re-equipense y terminen la pelea.");
    }

    private void tickVeilTransition(MinecraftServer server, ServerWorld world, EventTrial event, ZombieEntity boss) {
        int tick = event.veilTransitionTick++;
        if (tick == 0) {
            boss.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0, false, true));
            castStormRing(world, boss.getBlockPos(), 3, 3.0);
            world.spawnParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 1.2, boss.getZ(), 6, 1.5, 0.6, 1.5, 0.03);
            world.spawnParticles(ParticleTypes.DRAGON_BREATH, boss.getX(), boss.getY() + 1.0, boss.getZ(), 220, 4.0, 1.2, 4.0, 0.08);
            world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.8f, 0.55f);
            discardNonBossMobsWithLightning(world, event);
        }
        if (tick == 1) {
            world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 1.1f, 0.55f);
        }
        if (tick >= 1 && tick <= 40) {
            double targetY = event.center.getY() + 8.0;
            if (boss.getY() < targetY - 0.15) {
                boss.setVelocity(0.0, 0.18, 0.0);
            } else {
                boss.setVelocity(0.0, 0.0, 0.0);
            }
            boss.velocityModified = true;
            spawnAscensionTrail(world, event, boss);
            if (tick == 20) {
                BlockPos ground = findArenaSpawn(world, event.center.add(0, 3, 0), event.center).down();
                castStormRing(world, ground, 4, 2.4);
                world.playSound(null, ground, SoundEvents.ENTITY_ENDER_DRAGON_AMBIENT, SoundCategory.HOSTILE, 1.2f, 0.7f);
            }
        }
        if (tick == 41) {
            boss.setVelocity(0.0, 0.0, 0.0);
            boss.velocityModified = true;
            boss.setInvulnerable(true);
            boss.setNoGravity(true);
            world.playSound(null, event.center, SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 0.75f, 0.65f);
        }
        if (tick >= 41 && tick <= 70) {
            boss.setVelocity(0.0, 0.0, 0.0);
            boss.velocityModified = true;
            spawnVeilConvergence(world, event);
            if (tick == 55) {
                spawnLightning(world, findArenaSpawn(world, event.center.add(0, 3, 0), event.center));
            }
            if (tick == 60) {
                world.playSound(null, event.center, SoundEvents.ENTITY_SPIDER_AMBIENT, SoundCategory.HOSTILE, 1.2f, 0.5f);
            }
        }
        if (tick == 71) {
            spawnVeilGuardian(server, world, event);
            event.veilTransitionDone = true;
            event.veilRegenCooldown = 3 * 20;
            broadcast(server, "&5&lEL VELO &7- &dDerroten al Guardi\u00E1n del Velo.");
        }
    }

    private void tickFuryTransition(MinecraftServer server, ServerWorld world, EventTrial event, ZombieEntity boss) {
        event.furyTransitionTick++;
        if (event.furyLanding == null) {
            event.furyLanding = findArenaSpawn(world, event.center.add(0, 3, 0), event.center);
        }
        if (event.furyTransitionTick <= 20 && boss.getY() > event.furyLanding.getY() + 0.25) {
            boss.setVelocity(0.0, -0.18, 0.0);
            boss.velocityModified = true;
            spawnDescentTrail(world, boss);
            return;
        }
        boss.setVelocity(0.0, 0.0, 0.0);
        boss.velocityModified = true;
        completeFuryPhase(server, world, event, boss);
    }

    private void spawnVeilGuardian(MinecraftServer server, ServerWorld world, EventTrial event) {
        SpiderEntity guardian = EntityType.SPIDER.create(world);
        if (guardian == null) return;
        BlockPos spawn = findArenaSpawn(world, event.center.add(0, 3, 0), event.center);
        guardian.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0f, 0.0f);
        guardian.addCommandTag(MOB_TAG);
        guardian.setCustomName(Text.literal("\u00A75Guardi\u00E1n del Velo"));
        guardian.setCustomNameVisible(true);
        world.spawnEntity(guardian);
        setAttr(guardian, EntityAttributes.GENERIC_MAX_HEALTH, 200.0 + event.initialPlayers * 25.0);
        setAttr(guardian, EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0 + event.initialPlayers * 0.75);
        setAttr(guardian, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.33);
        setAttr(guardian, EntityAttributes.GENERIC_ARMOR, 9.0);
        guardian.setHealth(guardian.getMaxHealth());
        guardian.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 60 * 5, 0, false, true));
        guardian.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 60 * 5, 0, false, true));
        guardian.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 60 * 5, 0, false, true));
        targetNearestParticipant(guardian);
        event.veilGuardianId = guardian.getUuid();
        event.mobs.add(guardian.getUuid());
        world.spawnParticles(ParticleTypes.EXPLOSION, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, 2, 0.7, 0.25, 0.7, 0.02);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, 90, 1.1, 0.9, 1.1, 0.08);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, spawn.getX() + 0.5, spawn.getY() + 1.0, spawn.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        spawnLightning(world, spawn);
        world.playSound(null, spawn, SoundEvents.ENTITY_SPIDER_HURT, SoundCategory.HOSTILE, 1.3f, 0.55f);
        world.playSound(null, spawn, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.1f, 0.6f);
    }

    private void castStormRing(ServerWorld world, BlockPos center, int count, double radius) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / Math.max(1, count);
            BlockPos strike = center.add((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius));
            spawnLightning(world, strike);
        }
    }

    private void discardNonBossMobsWithLightning(ServerWorld world, EventTrial event) {
        for (UUID uuid : new HashSet<>(event.mobs)) {
            if (uuid.equals(event.bossId)) continue;
            Entity entity = world.getEntity(uuid);
            if (entity != null) {
                spawnLightning(world, entity.getBlockPos());
                world.spawnParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + 0.8, entity.getZ(), 20, 0.5, 0.4, 0.5, 0.04);
                entity.discard();
            }
            event.mobs.remove(uuid);
        }
    }

    private void spawnAscensionTrail(ServerWorld world, EventTrial event, ZombieEntity boss) {
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 0.6, boss.getZ(), 24, 0.8, 0.5, 0.8, 0.04);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(), boss.getY() - 0.2, boss.getZ(), 18, 0.45, 0.7, 0.45, -0.015);
        for (int i = 0; i < 16; i++) {
            double angle = event.auraTicks * 0.28 + Math.PI * 2.0 * i / 16.0;
            double y = boss.getY() - 0.5 + (i % 6) * 0.45;
            double radius = 1.5 + (i % 3) * 0.25;
            world.spawnParticles(ParticleTypes.WITCH,
                boss.getX() + Math.cos(angle) * radius, y, boss.getZ() + Math.sin(angle) * radius,
                1, 0.03, 0.08, 0.03, 0.02);
        }
        event.auraTicks++;
    }

    private void spawnDescentTrail(ServerWorld world, ZombieEntity boss) {
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, boss.getX(), boss.getY() + 1.3, boss.getZ(), 18, 0.65, 0.5, 0.65, 0.04);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(), boss.getY() + boss.getHeight(), boss.getZ(), 28, 0.55, 0.6, 0.55, 0.03);
        world.spawnParticles(ParticleTypes.SMOKE, boss.getX(), boss.getY() + 0.2, boss.getZ(), 10, 0.35, 0.2, 0.35, 0.02);
    }

    private void spawnVeilConvergence(ServerWorld world, EventTrial event) {
        int points = 12;
        double radius = Math.max(12.0, ModConfig.get().eventTrial.radioArena * 0.72);
        Vec3d center = Vec3d.ofCenter(event.center).add(0.0, 1.2, 0.0);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2.0 * i / points + event.veilTransitionTick * 0.035;
            Vec3d from = center.add(Math.cos(angle) * radius, 0.4 + (i % 4) * 0.25, Math.sin(angle) * radius);
            Vec3d velocity = center.subtract(from).normalize().multiply(0.18);
            world.spawnParticles(ParticleTypes.WITCH, from.x, from.y, from.z, 1, velocity.x, velocity.y, velocity.z, 0.08);
            world.spawnParticles(ParticleTypes.PORTAL, from.x, from.y + 0.1, from.z, 1, velocity.x, velocity.y, velocity.z, 0.12);
        }
    }

    public boolean isProtectedVeilBoss(Entity entity) {
        return activeEvent != null
            && activeEvent.bossPhaseThree
            && activeEvent.bossId != null
            && entity.getUuid().equals(activeEvent.bossId);
    }

    private void discardNonBossMobs(ServerWorld world, EventTrial event) {
        for (UUID uuid : new HashSet<>(event.mobs)) {
            if (uuid.equals(event.bossId)) continue;
            Entity entity = world.getEntity(uuid);
            if (entity != null) entity.discard();
            event.mobs.remove(uuid);
        }
    }

    private void spawnBossImpact(ServerWorld world, BlockPos pos) {
        world.spawnParticles(ParticleTypes.EXPLOSION, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 1.2, 0.35, 1.2, 0.04);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5, 120, 3.0, 0.2, 3.0, 0.05);
        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2f, 0.65f);
        spawnLightning(world, pos);
    }

    private void spawnRegearChests(ServerWorld world, EventTrial event) {
        if (event.regearChestsSpawned) return;
        event.regearChestsSpawned = true;
        int count = Math.min(4, Math.max(1, event.participants.size()));
        int attempts = 0;
        for (int i = 0; i < count && attempts < 48; attempts++) {
            double angle = world.random.nextDouble() * Math.PI * 2.0;
            int distance = 9 + world.random.nextInt(Math.max(4, ModConfig.get().eventTrial.radioArena / 2));
            BlockPos preferred = event.center.add((int) Math.round(Math.cos(angle) * distance), 3, (int) Math.round(Math.sin(angle) * distance));
            BlockPos spawn = findArenaSpawn(world, preferred, event.center);
            BlockPos chestPos = spawn.down();
            if (!isChestPositionSeparated(chestPos, event.regearChests)) continue;
            world.setBlockState(chestPos, Blocks.CHEST.getDefaultState());
            if (world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest) {
                fillRegearChest(chest, world);
                event.regearChests.add(chestPos);
                spawnRegearChestMarker(world, event, chestPos);
                world.spawnParticles(ParticleTypes.END_ROD, chestPos.getX() + 0.5, chestPos.getY() + 1.2, chestPos.getZ() + 0.5, 32, 0.55, 0.55, 0.55, 0.05);
                world.playSound(null, chestPos, SoundEvents.BLOCK_ENDER_CHEST_OPEN, SoundCategory.BLOCKS, 1.0f, 1.1f);
                i++;
            }
        }
    }

    private void spawnRegearChestMarker(ServerWorld world, EventTrial event, BlockPos chestPos) {
        ArmorStandEntity marker = new ArmorStandEntity(world, chestPos.getX() + 0.5, chestPos.getY() + 1.25, chestPos.getZ() + 0.5);
        marker.setInvisible(true);
        marker.setNoGravity(true);
        marker.setInvulnerable(true);
        marker.setCustomName(Text.literal("\u00A7aRe-equipamiento"));
        marker.setCustomNameVisible(false);
        cinematicController.setArmorStandMarker(marker);
        marker.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 20 * 60 * 10, 0, false, false));
        marker.addCommandTag("mcextremo_regear_marker");
        world.spawnEntity(marker);
        event.regearChestMarkers.add(marker.getUuid());
    }

    private boolean isChestPositionSeparated(BlockPos pos, Set<BlockPos> existing) {
        for (BlockPos other : existing) {
            if (other.getSquaredDistance(pos) < 49.0) return false;
        }
        return true;
    }

    private void fillRegearChest(ChestBlockEntity chest, ServerWorld world) {
        chest.setStack(0, enchantedItem(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 3));
        chest.setStack(1, enchantedItem(Items.BOW, Enchantments.POWER, 3));
        chest.setStack(2, new ItemStack(Items.ARROW, 32));
        chest.setStack(3, new ItemStack(Items.GOLDEN_APPLE, 8));
        chest.setStack(4, new ItemStack(Items.COOKED_BEEF, 24));
        chest.setStack(5, enchantedItem(Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 2));
        chest.setStack(6, enchantedItem(Items.DIAMOND_LEGGINGS, Enchantments.PROTECTION, 2));
        if (world.random.nextBoolean()) {
            chest.setStack(8, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
    }

    private void spawnBossMinions(ServerWorld world, EventTrial event, int count) {
        int active = countAliveMobs(world, event);
        int bossCap = Math.min(ModConfig.get().eventTrial.maxMobsActivos,
            Math.max(5, event.initialPlayers * (event.bossPhaseFour ? 2 : 3) + (event.bossPhaseFour ? 4 : 5)));
        int allowed = Math.max(0, bossCap - active);
        int amount = Math.min(count, allowed);
        for (int i = 0; i < amount && event.mobs.size() < ModConfig.get().eventTrial.maxMobsActivos; i++) {
            MobEntity mob = createBossMinion(world, event.bossPhaseTwo, i);
            if (mob == null) continue;
            BlockPos spawn = findArenaSpawn(world, getSpawnAnchor(event.center, event.wave, i + 20), event.center);
            spawnLightning(world, spawn);
            mob.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0.0f, 0.0f);
            mob.addCommandTag(MOB_TAG);
            equipBossMinion(mob, event.bossPhaseTwo, i);
            world.spawnEntity(mob);
            boostEventMobStats(mob, event.bossPhaseFour ? 4 : event.bossPhaseTwo ? 5 : 3, true);
            targetNearestParticipant(mob);
            event.mobs.add(mob.getUuid());
        }
    }

    private void spawnBossAura(ServerWorld world, EventTrial event, ZombieEntity boss) {
        event.auraTicks++;
        if (event.bossPhaseFour) {
            spawnBossSphere(world, boss, 2.0, ParticleTypes.FLAME, 5, 8, event.auraTicks * 0.20);
            spawnBossSphere(world, boss, 1.1, ParticleTypes.SOUL_FIRE_FLAME, 3, 5, -(event.auraTicks * 0.16));
            if (world.getTime() % 10L == 0L) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    boss.getX(), boss.getY() + boss.getHeight() * 0.55, boss.getZ(),
                    20, 1.0, 0.8, 1.0, 0.08);
            }
            spawnBossGroundEffect(world, boss);
            if (world.getTime() % 2L == 0L) {
                spawnBossDrip(world, boss, 2.0);
            }
            if (world.getTime() % 50L == 0L) {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 0.7f, 0.5f);
            }
            if (world.getTime() % 120L == 0L) {
                LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
                if (lightning != null) {
                    lightning.setCosmetic(true);
                    lightning.refreshPositionAfterTeleport(boss.getX(), boss.getY() + boss.getHeight() + 0.5, boss.getZ());
                    world.spawnEntity(lightning);
                }
            }
            return;
        }

        if (event.bossPhaseTwo) {
            spawnBossSphere(world, boss, 2.4, ParticleTypes.SOUL_FIRE_FLAME, 5, 8, event.auraTicks * 0.14);
            spawnBossSphere(world, boss, 1.2, ParticleTypes.PORTAL, 3, 5, -(event.auraTicks * 0.10));
            if (world.getTime() % 3L == 0L) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    boss.getX(), boss.getY() + boss.getHeight() * 0.55, boss.getZ(),
                    8, 0.8, 0.7, 0.8, 0.05);
            }
            spawnBossGroundEffect(world, boss);
            if (world.getTime() % 2L == 0L) {
                spawnBossDrip(world, boss, 2.4);
            }
            if (world.getTime() % 60L == 0L) {
                world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 0.7f, 0.65f);
            }
            return;
        }

        spawnBossSphere(world, boss, 1.8, ParticleTypes.DRAGON_BREATH, 4, 6, event.auraTicks * 0.08);
        spawnBossGroundEffect(world, boss);
        if (world.getTime() % 2L == 0L) {
            spawnBossDrip(world, boss, 1.8);
        }
        if (world.getTime() % 80L == 0L) {
            world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.HOSTILE, 0.7f, 0.85f);
        }
    }

    private void spawnBossSphere(ServerWorld world, ZombieEntity boss, double radius, ParticleEffect particle, int bands, int pointsPerBand, double rotationOffset) {
        int safeBands = Math.max(1, bands);
        int safePoints = Math.max(1, Math.min(pointsPerBand, 80 / safeBands));
        double centerY = boss.getY() + boss.getHeight() * 0.55;
        for (int band = 0; band < safeBands; band++) {
            double phi = safeBands == 1 ? Math.PI * 0.5 : Math.PI * band / (safeBands - 1);
            double sin = Math.sin(phi);
            double cos = Math.cos(phi);
            for (int point = 0; point < safePoints; point++) {
                double theta = Math.PI * 2.0 * point / safePoints;
                double x = boss.getX() + radius * sin * Math.cos(theta + rotationOffset);
                double y = centerY + radius * cos;
                double z = boss.getZ() + radius * sin * Math.sin(theta + rotationOffset);
                world.spawnParticles(particle, x, y, z, 1, 0.04, 0.04, 0.04, 0.01);
            }
        }
    }

    private void spawnBossGroundEffect(ServerWorld world, ZombieEntity boss) {
        double baseY = boss.getY() + 0.1;
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2.0 * i / 8.0;
            double x = boss.getX() + Math.cos(angle) * 3.0;
            double z = boss.getZ() + Math.sin(angle) * 3.0;
            Vec3d velocity = new Vec3d(boss.getX() - x, 0.0, boss.getZ() - z).normalize().multiply(0.06);
            world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, baseY, z, 1, velocity.x, 0.0, velocity.z, 0.06);
        }
    }

    private void spawnBossDrip(ServerWorld world, ZombieEntity boss, double radius) {
        double centerY = boss.getY() + boss.getHeight() * 0.55;
        ParticleEffect particle = activeEvent != null && activeEvent.bossPhaseFour ? ParticleTypes.DRIPPING_LAVA : ParticleTypes.DRIPPING_OBSIDIAN_TEAR;
        world.spawnParticles(particle, boss.getX(), centerY + radius, boss.getZ(), 5, 0.15, 0.05, 0.15, -0.04);
    }

    private void spawnBossPhaseBurst(ServerWorld world, ZombieEntity boss) {
        world.spawnParticles(ParticleTypes.EXPLOSION, boss.getX(), boss.getY() + 1.2, boss.getZ(), 3, 0.8, 0.4, 0.8, 0.02);
        world.spawnParticles(ParticleTypes.DRAGON_BREATH, boss.getX(), boss.getY() + 1.0, boss.getZ(), 160, 3.5, 1.2, 3.5, 0.06);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, boss.getX(), boss.getY() + 0.5, boss.getZ(), 90, 2.6, 0.5, 2.6, 0.04);
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 1.3f, 0.65f);
    }

    private void spawnVeilAura(ServerWorld world, EventTrial event, ZombieEntity boss, double radius, boolean blocking) {
        event.auraTicks++;
        double pulseRadius = radius + Math.sin(event.auraTicks * 0.10) * 0.5;
        spawnBossSphere(world, boss, pulseRadius, ParticleTypes.REVERSE_PORTAL, 8, 10, event.auraTicks * 0.06);
        spawnBossSphere(world, boss, Math.min(2.0, Math.max(1.2, radius * 0.34)), ParticleTypes.SOUL_FIRE_FLAME, 4, 6, event.auraTicks * 0.12);
        world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
            boss.getX(), boss.getY() + 0.5, boss.getZ(),
            12, 1.5, 0.15, 1.5, -0.08);
        if (world.getTime() % 100L == 0L) {
            world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 0.5f, 0.7f);
        }
        if (blocking) {
            applyBlockingAura(world, event, boss, radius);
        }
    }

    private void beginBossDeath(ServerWorld world, EventTrial event) {
        if (event.bossDeathTick >= 0) return;
        event.bossDeathTick = 0;
        event.dyingBossId = event.bossId;
        ZombieEntity boss = getBoss(world, event);
        if (boss != null) {
            boss.setInvulnerable(true);
            boss.setAiDisabled(true);
            boss.setVelocity(0.0, 0.0, 0.0);
            boss.velocityModified = true;
        }
    }

    private void tickBossDeath(MinecraftServer server, ServerWorld world, EventTrial event) {
        Entity entity = event.dyingBossId == null ? null : world.getEntity(event.dyingBossId);
        Vec3d pos = entity != null ? entity.getPos() : Vec3d.ofCenter(event.center).add(0.0, 2.0, 0.0);
        int tick = event.bossDeathTick++;

        if (tick == 0) {
            if (entity instanceof ZombieEntity boss) {
                boss.setInvulnerable(true);
                boss.setAiDisabled(true);
                boss.setVelocity(0.0, 0.0, 0.0);
                boss.velocityModified = true;
            }
            for (int i = 0; i < 3; i++) {
                world.spawnParticles(ParticleTypes.EXPLOSION,
                    pos.x + (world.random.nextDouble() - 0.5) * 3.0,
                    pos.y + 1.0 + world.random.nextDouble(),
                    pos.z + (world.random.nextDouble() - 0.5) * 3.0,
                    1, 0.2, 0.2, 0.2, 0.02);
            }
            world.spawnParticles(ParticleTypes.DRAGON_BREATH, pos.x, pos.y + 1.0, pos.z, 300, 5.0, 2.0, 5.0, 0.08);
            world.playSound(null, BlockPos.ofFloored(pos), SoundEvents.ENTITY_ENDER_DRAGON_DEATH, SoundCategory.HOSTILE, 2.0f, 1.0f);
        }

        if (tick >= 1 && tick <= 40) {
            double radius = tick * 0.4;
            for (int i = 0; i < 16; i++) {
                double angle = Math.PI * 2.0 * i / 16.0;
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x + Math.cos(angle) * radius, event.center.getY() + 1.2, pos.z + Math.sin(angle) * radius,
                    1, 0.05, 0.03, 0.05, 0.02);
            }
        }

        if (tick >= 40) {
            if (entity != null) entity.discard();
            event.bossDeathTick = -1;
            event.dyingBossId = null;
            startVictoryDelay(server, event);
        }
    }

    private void applyBlockingAura(ServerWorld world, EventTrial event, ZombieEntity boss, double radius) {
        double radiusSq = radius * radius;
        for (UUID uuid : event.participants) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player == null || player.getWorld() != world || player.isCreative() || player.isSpectator()) continue;
            double distanceSq = player.squaredDistanceTo(boss);
            if (distanceSq > radiusSq) continue;
            Vec3d push = player.getPos().subtract(boss.getPos());
            if (push.lengthSquared() < 0.01) {
                push = new Vec3d(1.0, 0.0, 0.0);
            }
            Vec3d velocity = push.normalize().multiply(1.45).add(0.0, 0.42, 0.0);
            player.setVelocity(velocity);
            player.velocityModified = true;
            if (world.getTime() % 10L == 0L) {
                player.damage(world.getDamageSources().magic(), 4.0f);
            }
        }
    }

    private void executeBossCombo(MinecraftServer server, ServerWorld world, EventTrial event, ZombieEntity boss) {
        if (castAntiCampCombo(world, event, boss)) {
            broadcast(server, "&5El Coloso castiga a quienes intentan escapar.");
            return;
        }
        float ratio = boss.getHealth() / Math.max(1.0f, boss.getMaxHealth());
        if (event.bossPhaseTwo && !event.bossPhaseFour && ratio <= 0.25f) {
            castFinalCombo(world, event, boss);
            broadcast(server, "&4El Coloso desata su ultimo combo.");
            return;
        }
        int roll = world.random.nextInt(event.bossPhaseTwo ? 4 : 3);
        if (roll == 0) {
            castPressureCombo(world, event, boss);
        } else if (roll == 1) {
            castFireCombo(world, event, boss);
        } else {
            castFangWave(world, event, boss, event.bossPhaseTwo ? 3 : 2);
        }
    }

    private void castPressureCombo(ServerWorld world, EventTrial event, ZombieEntity boss) {
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 1.1f, 0.75f);
        world.spawnParticles(ParticleTypes.SONIC_BOOM, boss.getX(), boss.getY() + 1.2, boss.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        for (UUID uuid : event.participants) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player == null || player.getWorld() != world) continue;
            BlockPos strike = findGround(world, player.getBlockPos(), event.center).up();
            spawnLightning(world, strike);
            castFangLine(world, boss, boss.getPos(), player.getPos(), event.bossPhaseTwo ? 10 : 7);
        }
        spawnBossMinions(world, event, event.bossPhaseTwo ? 3 : 2);
    }

    private void castFireCombo(ServerWorld world, EventTrial event, ZombieEntity boss) {
        castFireCone(world, event, boss, event.bossPhaseTwo);
        castFireRing(world, event, boss, event.bossPhaseTwo ? 7.0 : 5.0);
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0f, 0.7f);
    }

    private void castFinalCombo(ServerWorld world, EventTrial event, ZombieEntity boss) {
        castFireRing(world, event, boss, 8.0);
        castFangWave(world, event, boss, 3);
        spawnBossMinions(world, event, 3);
        boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 8, 1, false, true));
        boss.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 8, 1, false, true));
    }

    private void tickSkullBurst(ServerWorld world, EventTrial event, ZombieEntity boss) {
        if (event.skullsInBurst > 0) {
            if (--event.skullBurstDelay <= 0) {
                fireWitherSkull(world, event, boss);
                event.skullsInBurst--;
                event.skullBurstDelay = 2;
            }
            return;
        }
        if (--event.skullCooldown <= 0) {
            event.skullsInBurst = 2;
            event.skullBurstDelay = 0;
            event.skullCooldown = 12 * 20;
        }
    }

    private void fireWitherSkull(ServerWorld world, EventTrial event, ZombieEntity boss) {
        ServerPlayerEntity target = findNearestParticipant(world, event, boss.getPos());
        if (target == null) return;
        WitherSkullEntity skull = EntityType.WITHER_SKULL.create(world);
        if (skull == null) return;
        Vec3d start = boss.getPos().add(0.0, boss.getHeight() * 0.72, 0.0);
        Vec3d aim = target.getEyePos().subtract(start);
        if (aim.lengthSquared() < 0.01) return;
        double spread = (world.random.nextDouble() - 0.5) * 0.18;
        Vec3d direction = aim.normalize()
            .add((world.random.nextDouble() - 0.5) * 0.08, spread, (world.random.nextDouble() - 0.5) * 0.08)
            .normalize();
        skull.refreshPositionAndAngles(start.x, start.y, start.z, boss.getYaw(), boss.getPitch());
        skull.setOwner(boss);
        skull.setCharged(false);
        skull.setVelocity(direction.multiply(1.15));
        skull.addCommandTag(MOB_TAG);
        world.spawnEntity(skull);
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.HOSTILE, 0.9f, 1.2f);
    }

    private boolean castAntiCampCombo(ServerWorld world, EventTrial event, ZombieEntity boss) {
        boolean cast = false;
        double maxDistance = ModConfig.get().eventTrial.radioArena * 0.68;
        for (UUID uuid : event.participants) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player == null || player.getWorld() != world) continue;
            double dx = player.getX() - (event.center.getX() + 0.5);
            double dz = player.getZ() - (event.center.getZ() + 0.5);
            boolean campingHigh = player.getY() > event.center.getY() + 9;
            boolean campingFar = dx * dx + dz * dz > maxDistance * maxDistance;
            if (!campingHigh && !campingFar) continue;
            cast = true;
            BlockPos ground = findGround(world, player.getBlockPos(), event.center).up();
            spawnLightning(world, ground);
            for (int i = 0; i < 10; i++) {
                double angle = Math.PI * 2.0 * i / 10.0;
                BlockPos pos = ground.add((int) Math.round(Math.cos(angle) * 2.8), 0, (int) Math.round(Math.sin(angle) * 2.8));
                BlockPos safe = findGround(world, pos, event.center);
                world.spawnEntity(new EvokerFangsEntity(world, safe.getX() + 0.5, safe.getY() + 0.05, safe.getZ() + 0.5, (float) angle, 8, boss));
            }
        }
        return cast;
    }

    private void castFangWave(ServerWorld world, EventTrial event, ZombieEntity boss, int rings) {
        for (int ring = 1; ring <= rings; ring++) {
            int points = 10 + ring * 4;
            double radius = 3.0 + ring * 2.4;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0 * i / points + ring * 0.18;
                BlockPos pos = boss.getBlockPos().add((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius));
                BlockPos safe = findGround(world, pos, event.center);
                world.spawnParticles(ParticleTypes.SCULK_SOUL, safe.getX() + 0.5, safe.getY() + 0.1, safe.getZ() + 0.5, 6, 0.25, 0.04, 0.25, 0.02);
                world.spawnEntity(new EvokerFangsEntity(world, safe.getX() + 0.5, safe.getY() + 0.05, safe.getZ() + 0.5, (float) angle, ring * 5, boss));
            }
        }
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 1.0f, 0.8f);
    }

    private void castFangLine(ServerWorld world, ZombieEntity boss, Vec3d from, Vec3d to, int steps) {
        Vec3d delta = to.subtract(from);
        double length = Math.max(0.001, Math.sqrt(delta.x * delta.x + delta.z * delta.z));
        Vec3d dir = new Vec3d(delta.x / length, 0.0, delta.z / length);
        float yaw = (float) Math.atan2(dir.z, dir.x);
        for (int i = 2; i <= steps; i++) {
            Vec3d point = from.add(dir.multiply(i * 1.65));
            BlockPos safe = findGround(world, BlockPos.ofFloored(point), boss.getBlockPos());
            world.spawnParticles(ParticleTypes.SCULK_SOUL, safe.getX() + 0.5, safe.getY() + 0.1, safe.getZ() + 0.5, 4, 0.22, 0.04, 0.22, 0.02);
            world.spawnEntity(new EvokerFangsEntity(world, safe.getX() + 0.5, safe.getY() + 0.05, safe.getZ() + 0.5, yaw, i * 2, boss));
        }
    }

    private void castFireCone(ServerWorld world, EventTrial event, ZombieEntity boss, boolean phaseTwo) {
        ServerPlayerEntity target = findNearestParticipant(world, event, boss.getPos());
        Vec3d direction = target == null ? boss.getRotationVec(1.0f) : target.getPos().subtract(boss.getPos()).normalize();
        double range = phaseTwo ? 16.0 : 12.0;
        for (int step = 2; step <= range; step++) {
            double width = 0.35 + step * 0.22;
            Vec3d center = boss.getPos().add(direction.multiply(step));
            for (int side = -2; side <= 2; side++) {
                Vec3d sideVec = new Vec3d(-direction.z, 0.0, direction.x).multiply(side * width);
                Vec3d pos = center.add(sideVec);
                world.spawnParticles(ParticleTypes.FLAME, pos.x, boss.getY() + 0.35, pos.z, 3, 0.15, 0.08, 0.15, 0.02);
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, boss.getY() + 0.55, pos.z, phaseTwo ? 2 : 1, 0.1, 0.08, 0.1, 0.02);
            }
        }
        for (UUID uuid : event.participants) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player == null || player.getWorld() != world) continue;
            Vec3d toPlayer = player.getPos().subtract(boss.getPos());
            double horizontal = Math.sqrt(toPlayer.x * toPlayer.x + toPlayer.z * toPlayer.z);
            if (horizontal > range || horizontal < 0.1) continue;
            Vec3d normalized = new Vec3d(toPlayer.x / horizontal, 0.0, toPlayer.z / horizontal);
            if (normalized.dotProduct(new Vec3d(direction.x, 0.0, direction.z).normalize()) < 0.58) continue;
            player.setOnFireFor(phaseTwo ? 4 : 2);
            player.damage(world.getDamageSources().onFire(), phaseTwo ? 5.0f : 3.0f);
            player.addVelocity(normalized.x * 0.35, 0.12, normalized.z * 0.35);
            player.velocityModified = true;
        }
        world.playSound(null, boss.getBlockPos(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0f, phaseTwo ? 0.55f : 0.75f);
    }

    private void castFireRing(ServerWorld world, EventTrial event, ZombieEntity boss, double radius) {
        for (int i = 0; i < 48; i++) {
            double angle = Math.PI * 2.0 * i / 48.0;
            double x = boss.getX() + Math.cos(angle) * radius;
            double z = boss.getZ() + Math.sin(angle) * radius;
            world.spawnParticles(ParticleTypes.FLAME, x, boss.getY() + 0.25, z, 3, 0.08, 0.05, 0.08, 0.02);
            if (i % 3 == 0) {
                world.spawnParticles(ParticleTypes.LAVA, x, boss.getY() + 0.2, z, 1, 0.05, 0.02, 0.05, 0.01);
            }
        }
        for (UUID uuid : event.participants) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player == null || player.getWorld() != world) continue;
            double distance = Math.sqrt(player.squaredDistanceTo(boss));
            if (distance < radius - 1.6 || distance > radius + 1.8) continue;
            player.damage(world.getDamageSources().inFire(), 4.0f);
            Vec3d push = player.getPos().subtract(boss.getPos()).normalize();
            player.addVelocity(push.x * 0.45, 0.16, push.z * 0.45);
            player.velocityModified = true;
        }
        world.playSound(null, boss.getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 1.0f, 0.7f);
    }

    private void castFangs(ServerWorld world, EventTrial event, boolean phaseTwo) {
        int rings = phaseTwo ? 2 : 1;
        for (UUID uuid : event.participants) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player == null || player.getWorld() != world) continue;
            for (int ring = 0; ring < rings; ring++) {
                double radius = 2.5 + ring * 2.2;
                int points = ring == 0 ? 6 : 10;
                for (int i = 0; i < points; i++) {
                    double angle = (Math.PI * 2.0 / points) * i + world.random.nextDouble() * 0.2;
                    BlockPos pos = player.getBlockPos().add((int) Math.round(Math.cos(angle) * radius), 0, (int) Math.round(Math.sin(angle) * radius));
                    BlockPos safe = findGround(world, pos, event.center);
                    world.spawnParticles(ParticleTypes.SCULK_SOUL, safe.getX() + 0.5, safe.getY() + 0.1, safe.getZ() + 0.5, 8, 0.25, 0.05, 0.25, 0.02);
                    EvokerFangsEntity fangs = new EvokerFangsEntity(world, safe.getX() + 0.5, safe.getY() + 0.05, safe.getZ() + 0.5, (float) angle, 20, getBoss(world, event));
                    world.spawnEntity(fangs);
                }
            }
        }
        world.playSound(null, event.center, SoundEvents.ENTITY_EVOKER_PREPARE_ATTACK, SoundCategory.HOSTILE, 1.0f, 0.8f);
    }

    private void startVictoryDelay(MinecraftServer server, EventTrial event) {
        event.victoryDelay = true;
        event.actionCooldown = Math.max(20, ModConfig.get().eventTrial.segundosAntesDeSalir * 20);
        spawnVictoryFireworks(getEventWorld(server), event.center);
        broadcast(server, "&aFelicidades, superaron el event trial. &eVolveran pronto al spawn.");
    }

    private void finishVictory(MinecraftServer server) {
        EventTrial event = activeEvent;
        if (event == null) return;
        for (UUID uuid : new HashSet<>(event.participants)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;
            int current = mod.getLivesManager().getVidas(uuid);
            int max = mod.getLivesManager().getDefaultLives();
            mod.getLivesManager().setVidas(uuid, Math.min(max, current + ModConfig.get().eventTrial.vidasAlGanar));
            restoreAndReturn(player);
            mod.getRewardManager().giveEventTrialRewards(player, event.initialPlayers);
            player.sendMessage(TextUtil.literal("&aEvent trial completado. &eRecompensa recibida."), false);
        }
        cleanupEvent(server, event);
        activeEvent = null;
    }

    private void finishFailure(MinecraftServer server, String reason) {
        EventTrial event = activeEvent;
        if (event == null) return;
        broadcast(server, reason);
        for (UUID uuid : new HashSet<>(event.participants)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                restoreAndReturn(player);
            }
        }
        cleanupEvent(server, event);
        activeEvent = null;
    }

    private void restoreAndReturn(ServerPlayerEntity player) {
        cinematicController.resetIntroPlayerState(player);
        TrialCinematicNetworking.sendStop(player);
        player.sendMessage(Text.empty(), true);
        restoreInventory(player);
        mod.getDataManager().setTrialState(player.getUuid(), ReviveTrialManager.STATE_ALIVE);
        mod.getLivesManager().restoreAfterRevive(player);
    }

    private void updateBossBar(MinecraftServer server, EventTrial event, int alive, ZombieEntity boss) {
        if (boss != null && boss.isAlive()) {
            float percent = Math.max(0.03f, Math.min(1.0f, boss.getHealth() / Math.max(1.0f, boss.getMaxHealth())));
            event.bossBar.setPercent(percent);
            event.bossBar.setName(Text.literal("\u00A75Event Trial \u00A77| \u00A7dColoso "
                + Math.round(boss.getHealth()) + "/" + Math.round(boss.getMaxHealth()) + " HP \u00A78| \u00A7eTokens " + event.tokens));
            return;
        }
        event.bossBar.setPercent(Math.max(0.05f, event.wave / (float) ModConfig.get().eventTrial.oleadas));
        event.bossBar.setName(Text.literal("\u00A75Event Trial \u00A77| \u00A7fOleada "
            + event.wave + "/" + ModConfig.get().eventTrial.oleadas + " \u00A78| \u00A7c" + alive + " mobs \u00A78| \u00A7eTokens " + event.tokens));
    }

    private void updateVictoryDelay(MinecraftServer server, EventTrial event) {
        int total = Math.max(1, ModConfig.get().eventTrial.segundosAntesDeSalir * 20);
        event.bossBar.setPercent(Math.max(0.05f, event.actionCooldown / (float) total));
        event.bossBar.setName(Text.literal("\u00A7aVictoria \u00A77- \u00A7fSalida en " + Math.max(1, event.actionCooldown / 20) + "s"));
    }

    private void saveAndHideInventory(ServerPlayerEntity player) {
        List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < player.getInventory().size(); i++) {
            slots.add(player.getInventory().getStack(i).copy());
            player.getInventory().setStack(i, ItemStack.EMPTY);
        }
        player.getInventory().markDirty();
        mod.getDataManager().saveTrialInventory(player.getUuid(), slots);
    }

    private void restoreInventory(ServerPlayerEntity player) {
        List<ItemStack> slots = mod.getDataManager().loadTrialInventory(player.getUuid(), player.getInventory().size());
        for (int i = 0; i < player.getInventory().size(); i++) {
            player.getInventory().setStack(i, ItemStack.EMPTY);
        }
        if (slots != null) {
            for (int i = 0; i < player.getInventory().size() && i < slots.size(); i++) {
                player.getInventory().setStack(i, slots.get(i).copy());
            }
        }
        player.getInventory().markDirty();
        mod.getDataManager().clearTrialInventory(player.getUuid());
    }

    private void giveEventKit(ServerPlayerEntity player) {
        player.equipStack(EquipmentSlot.HEAD, enchantedItem(Items.DIAMOND_HELMET, Enchantments.PROTECTION, 2));
        player.equipStack(EquipmentSlot.CHEST, enchantedItem(Items.DIAMOND_CHESTPLATE, Enchantments.PROTECTION, 2));
        player.equipStack(EquipmentSlot.LEGS, enchantedItem(Items.DIAMOND_LEGGINGS, Enchantments.PROTECTION, 2));
        player.equipStack(EquipmentSlot.FEET, enchantedItem(Items.DIAMOND_BOOTS, Enchantments.PROTECTION, 2));
        player.getInventory().insertStack(enchantedItem(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 3));
        player.getInventory().insertStack(enchantedItem(Items.BOW, Enchantments.POWER, 2));
        player.getInventory().insertStack(new ItemStack(Items.ARROW, 64));
        player.getInventory().insertStack(new ItemStack(Items.SHIELD));
        player.getInventory().insertStack(new ItemStack(Items.BREAD, 64));
        player.getInventory().insertStack(new ItemStack(Items.COOKED_BEEF, 32));
        player.getInventory().insertStack(new ItemStack(Items.GOLDEN_APPLE, 24));
        player.getInventory().insertStack(PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION, 12), Potions.HEALING));
        player.getInventory().insertStack(PotionUtil.setPotion(new ItemStack(Items.POTION, 4), Potions.REGENERATION));
        player.getInventory().markDirty();
    }

    private ItemStack enchantedItem(net.minecraft.item.Item item, net.minecraft.enchantment.Enchantment enchantment, int level) {
        ItemStack stack = new ItemStack(item);
        Map<net.minecraft.enchantment.Enchantment, Integer> enchantments = new java.util.HashMap<>();
        enchantments.put(enchantment, level);
        enchantments.put(Enchantments.UNBREAKING, 2);
        EnchantmentHelper.set(enchantments, stack);
        return stack;
    }

    private MobEntity createWaveMob(ServerWorld world, int wave, int index) {
        int roll = Math.floorMod(index * 31 + wave * 17 + world.random.nextInt(100), 100);
        if (wave <= 1) {
            if (roll < 22) return EntityType.SPIDER.create(world);
            if (roll < 36) return EntityType.HUSK.create(world);
            return EntityType.ZOMBIE.create(world);
        }
        if (wave == 2) {
            if (roll < 18) return EntityType.SKELETON.create(world);
            if (roll < 34) return EntityType.SPIDER.create(world);
            if (roll < 48) return EntityType.HUSK.create(world);
            return EntityType.ZOMBIE.create(world);
        }
        if (wave == 3) {
            if (roll < 18) return EntityType.STRAY.create(world);
            if (roll < 34) return EntityType.CAVE_SPIDER.create(world);
            if (roll < 50) return EntityType.SKELETON.create(world);
            if (roll < 68) return EntityType.HUSK.create(world);
            return EntityType.ZOMBIE.create(world);
        }
        if (wave == 4) {
            if (roll < 10) return EntityType.WITHER_SKELETON.create(world);
            if (roll < 26) return EntityType.STRAY.create(world);
            if (roll < 42) return EntityType.CAVE_SPIDER.create(world);
            if (roll < 62) return EntityType.SKELETON.create(world);
            if (roll < 78) return EntityType.HUSK.create(world);
            return EntityType.ZOMBIE.create(world);
        }
        if (roll < 14) return EntityType.WITHER_SKELETON.create(world);
        if (roll < 30) return EntityType.STRAY.create(world);
        if (roll < 45) return EntityType.CAVE_SPIDER.create(world);
        if (roll < 64) return EntityType.SKELETON.create(world);
        if (roll < 82) return EntityType.HUSK.create(world);
        return EntityType.ZOMBIE.create(world);
    }

    private MobEntity createBossMinion(ServerWorld world, boolean phaseTwo, int index) {
        int roll = Math.floorMod(index * 37 + world.random.nextInt(100), 100);
        if (phaseTwo) {
            if (roll < 20) return EntityType.WITHER_SKELETON.create(world);
            if (roll < 42) return EntityType.STRAY.create(world);
            if (roll < 58) return EntityType.CAVE_SPIDER.create(world);
            if (roll < 76) return EntityType.HUSK.create(world);
            return EntityType.ZOMBIE.create(world);
        }
        if (roll < 18) return EntityType.SKELETON.create(world);
        if (roll < 34) return EntityType.SPIDER.create(world);
        if (roll < 52) return EntityType.HUSK.create(world);
        return EntityType.ZOMBIE.create(world);
    }

    private void equipWaveMob(MobEntity mob, int wave, int index) {
        if (mob instanceof HuskEntity husk) {
            husk.equipStack(EquipmentSlot.MAINHAND, new ItemStack(wave >= 4 ? Items.IRON_AXE : Items.IRON_SWORD));
            husk.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            setAttr(husk, EntityAttributes.GENERIC_MAX_HEALTH, husk.getMaxHealth() + wave * 2.0);
            husk.setHealth(husk.getMaxHealth());
            return;
        }
        if (mob instanceof ZombieEntity zombie) {
            equipWaveZombie(zombie, wave, index);
            return;
        }
        if (mob instanceof SkeletonEntity || mob instanceof StrayEntity) {
            mob.equipStack(EquipmentSlot.MAINHAND, enchantedItem(Items.BOW, Enchantments.POWER, Math.max(1, Math.min(3, wave / 2 + 1))));
            if (wave >= 3) mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            return;
        }
        if (mob instanceof WitherSkeletonEntity witherSkeleton) {
            witherSkeleton.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
            setAttr(witherSkeleton, EntityAttributes.GENERIC_MAX_HEALTH, 24.0 + wave * 3.0);
            witherSkeleton.setHealth(witherSkeleton.getMaxHealth());
            return;
        }
        if (mob instanceof SpiderEntity || mob instanceof CaveSpiderEntity) {
            setAttr(mob, EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28 + wave * 0.01);
        }
    }

    private void equipBossMinion(MobEntity mob, boolean phaseTwo, int index) {
        if (mob instanceof HuskEntity husk) {
            husk.equipStack(EquipmentSlot.MAINHAND, new ItemStack(phaseTwo ? Items.DIAMOND_AXE : Items.IRON_AXE));
            husk.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            if (phaseTwo) husk.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            return;
        }
        if (mob instanceof ZombieEntity zombie) {
            zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(phaseTwo ? Items.DIAMOND_SWORD : Items.IRON_SWORD));
            zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            if (phaseTwo && index % 2 == 0) zombie.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            return;
        }
        if (mob instanceof SkeletonEntity || mob instanceof StrayEntity) {
            mob.equipStack(EquipmentSlot.MAINHAND, enchantedItem(Items.BOW, Enchantments.POWER, phaseTwo ? 3 : 2));
            mob.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            return;
        }
        if (mob instanceof WitherSkeletonEntity witherSkeleton) {
            witherSkeleton.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            setAttr(witherSkeleton, EntityAttributes.GENERIC_MAX_HEALTH, phaseTwo ? 34.0 : 26.0);
            witherSkeleton.setHealth(witherSkeleton.getMaxHealth());
        }
    }

    private void boostEventMobStats(MobEntity mob, int wave, boolean bossMinion) {
        double healthBonus = (bossMinion ? 7.0 : 5.0) + wave * (bossMinion ? 2.5 : 3.0);
        double damageBonus = 0.8 + wave * (bossMinion ? 0.45 : 0.55);

        var health = mob.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(Math.min(90.0, health.getBaseValue() + healthBonus));
            mob.setHealth(mob.getMaxHealth());
        }
        var damage = mob.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (damage != null) {
            damage.setBaseValue(damage.getBaseValue() + damageBonus);
        }
        var armor = mob.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (armor != null && wave >= 3) {
            armor.setBaseValue(Math.min(bossMinion ? 8.0 : 12.0, armor.getBaseValue() + wave * (bossMinion ? 0.45 : 0.8)));
        }
        if (wave >= 3) {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 8, bossMinion ? 1 : 0, false, true));
        }
        if (wave >= 5 || bossMinion) {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 10, 0, false, true));
        }
    }

    private void equipWaveZombie(ZombieEntity zombie, int wave, int index) {
        if (wave >= 2) zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        if (wave >= 3) zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        if (wave >= 4) zombie.equipStack(EquipmentSlot.CHEST, new ItemStack(index % 5 == 0 ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
        if (wave >= 5 && index % 7 == 0) {
            zombie.setCustomName(Text.literal("\u00A75Guardian del Evento"));
            setAttr(zombie, EntityAttributes.GENERIC_MAX_HEALTH, zombie.getMaxHealth() + 24.0);
            zombie.setHealth(zombie.getMaxHealth());
        }
    }

    private int getWaveSize(int wave, int players) {
        return switch (wave) {
            case 1 -> 8 + players * 3;
            case 2 -> 11 + players * 4;
            case 3 -> 14 + players * 5;
            case 4 -> 17 + players * 5;
            case 5 -> 20 + players * 6;
            default -> 1;
        };
    }







































    private void cleanupEvent(MinecraftServer server, EventTrial event) {
        ServerWorld world = getEventWorld(server);
        event.bossBar.clearPlayers();
        event.bossBar.setVisible(false);
        for (UUID uuid : new HashSet<>(event.participants)) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                cinematicController.resetIntroPlayerState(player);
                TrialCinematicNetworking.sendStop(player);
                player.sendMessage(Text.empty(), true);
            }
        }
        cinematicController.discardIntroActors(world, event);
        cinematicController.discardIntroCamera(world, event);
        cleanupRegearChests(world, event);
        discardMobs(world, event.mobs);
        Box box = new Box(event.center).expand(ModConfig.get().eventTrial.radioArena + 20, 40, ModConfig.get().eventTrial.radioArena + 20);
        for (Entity entity : world.getEntitiesByClass(Entity.class, box, entity -> !(entity instanceof ServerPlayerEntity))) {
            if (entity instanceof HostileEntity || entity instanceof EndermanEntity || entity instanceof ExperienceOrbEntity || entity instanceof ItemEntity) {
                entity.discard();
            }
        }
        if (ModConfig.get().eventTrial.limpiarArenaAlTerminar) {
            arenaBuilder.clearArena(world, event.center, ModConfig.get().eventTrial.radioArena);
        }
    }

    private void cleanupRegearChests(ServerWorld world, EventTrial event) {
        for (BlockPos pos : event.regearChests) {
            if (world.getBlockState(pos).isOf(Blocks.CHEST)) {
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
            }
        }
        event.regearChests.clear();
        for (UUID uuid : event.regearChestMarkers) {
            Entity marker = world.getEntity(uuid);
            if (marker != null) marker.discard();
        }
        event.regearChestMarkers.clear();
    }

    private BlockPos getEventCenter(ModConfig.EventTrial config) {
        long slot = System.currentTimeMillis() / 1000L;
        int offset = (int) (Math.floorMod(slot, 1000) * config.distanciaEntreArenas);
        return new BlockPos((int) Math.round(config.x) + offset, (int) Math.round(config.y), (int) Math.round(config.z));
    }

    private ServerWorld getEventWorld(MinecraftServer server) {
        ServerWorld world = server.getWorld(World.END);
        return world != null ? world : server.getOverworld();
    }







    private void teleportToArena(ServerPlayerEntity player, ServerWorld world, BlockPos center) {
        player.teleport(world, center.getX() + 0.5, center.getY() + 5.0, center.getZ() + 0.5, player.getYaw(), player.getPitch());
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(20.0f);
    }

    private void keepPlayerInside(ServerPlayerEntity player, ServerWorld world, BlockPos center) {
        double max = ModConfig.get().eventTrial.radioArena + 4.0;
        double dx = player.getX() - (center.getX() + 0.5);
        double dz = player.getZ() - (center.getZ() + 0.5);
        if (dx * dx + dz * dz <= max * max && player.getY() > center.getY() - 8) return;
        teleportToArena(player, world, center);
        player.sendMessage(TextUtil.literal("&cLa arena del evento no te deja escapar."), true);
    }

    private BlockPos getSpawnAnchor(BlockPos center, int wave, int index) {
        int radius = ModConfig.get().eventTrial.radioArena - 12;
        int[][] anchors = {{radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}, {radius / 2, radius / 2}, {-radius / 2, radius / 2}, {radius / 2, -radius / 2}, {-radius / 2, -radius / 2}};
        int[] anchor = anchors[Math.floorMod(index + wave, anchors.length)];
        int lane = (index / anchors.length) % 5 - 2;
        return center.add(anchor[0] + lane * 3, 4, anchor[1] - lane * 3);
    }

    private BlockPos findSafeSpawn(ServerWorld world, BlockPos preferred, BlockPos center) {
        for (int r = 0; r <= 8; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos ground = findGround(world, preferred.add(dx, 0, dz), center);
                    if (isSpawnSafe(world, ground.up())) return ground.up();
                }
            }
        }
        return center.add(0, 5, 0);
    }

    private BlockPos findArenaSpawn(ServerWorld world, BlockPos preferred, BlockPos center) {
        int arenaFeetY = center.getY() + 3;
        for (int r = 0; r <= 16; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos feet = new BlockPos(preferred.getX() + dx, arenaFeetY, preferred.getZ() + dz);
                    if (isArenaFloorSpawnSafe(world, feet, center)) return feet;
                }
            }
        }
        for (int r = 4; r <= 24; r += 4) {
            for (int i = 0; i < 16; i++) {
                double angle = Math.PI * 2.0 * i / 16.0;
                BlockPos feet = new BlockPos(
                    center.getX() + (int) Math.round(Math.cos(angle) * r),
                    arenaFeetY,
                    center.getZ() + (int) Math.round(Math.sin(angle) * r)
                );
                if (isArenaFloorSpawnSafe(world, feet, center)) return feet;
            }
        }
        return center.add(0, 3, 0);
    }

    private boolean isArenaFloorSpawnSafe(ServerWorld world, BlockPos feet, BlockPos center) {
        double max = ModConfig.get().eventTrial.radioArena - 8.0;
        double dx = feet.getX() - center.getX();
        double dz = feet.getZ() - center.getZ();
        if (dx * dx + dz * dz > max * max) return false;
        if (feet.getY() < center.getY() + 2 || feet.getY() > center.getY() + 4) return false;
        return world.getBlockState(feet).isAir()
            && world.getBlockState(feet.up()).isAir()
            && world.getBlockState(feet.down()).isSolidBlock(world, feet.down())
            && !world.getBlockState(feet.down()).isOf(Blocks.BARRIER)
            && !world.getBlockState(feet.down()).isOf(Blocks.LADDER)
            && world.getBlockState(feet.up(2)).isAir();
    }

    private BlockPos findGround(ServerWorld world, BlockPos pos, BlockPos center) {
        for (int y = center.getY() + 20; y >= center.getY() - 8; y--) {
            BlockPos ground = new BlockPos(pos.getX(), y, pos.getZ());
            if (!world.getBlockState(ground).isAir() && !world.getBlockState(ground).isOf(Blocks.BARRIER)) {
                return ground;
            }
        }
        return center;
    }

    private boolean isSpawnSafe(ServerWorld world, BlockPos feet) {
        return world.getBlockState(feet).isAir()
            && world.getBlockState(feet.up()).isAir()
            && !world.getBlockState(feet.down()).isAir()
            && !world.getBlockState(feet.down()).isOf(Blocks.BARRIER);
    }

    private void keepMobsInside(ServerWorld world, EventTrial event) {
        for (UUID uuid : new HashSet<>(event.mobs)) {
            Entity entity = world.getEntity(uuid);
            if (entity == null || !entity.isAlive()) continue;
            if (entity instanceof MobEntity mob && !(mob.getTarget() instanceof ServerPlayerEntity)) {
                targetNearestParticipant(mob);
            }
            double dx = entity.getX() - (event.center.getX() + 0.5);
            double dz = entity.getZ() - (event.center.getZ() + 0.5);
            double max = ModConfig.get().eventTrial.radioArena + 6.0;
            if (dx * dx + dz * dz <= max * max && entity.getY() > event.center.getY() - 8) continue;
            BlockPos spawn = findArenaSpawn(world, event.center.add(0, 3, 0), event.center);
            entity.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, entity.getYaw(), entity.getPitch());
            entity.setVelocity(0.0, 0.0, 0.0);
        }
    }

    private int countAliveMobs(ServerWorld world, EventTrial event) {
        int alive = 0;
        event.mobs.removeIf(uuid -> {
            Entity entity = world.getEntity(uuid);
            return entity == null || !entity.isAlive();
        });
        for (UUID uuid : event.mobs) {
            Entity entity = world.getEntity(uuid);
            if (entity != null && entity.isAlive()) alive++;
        }
        return alive;
    }

    private void markLastAliveMobs(ServerWorld world, EventTrial event, int alive) {
        if (alive > 2 || event.wave >= ModConfig.get().eventTrial.oleadas) return;
        for (UUID uuid : event.mobs) {
            Entity entity = world.getEntity(uuid);
            if (!(entity instanceof MobEntity mob) || !mob.isAlive()) continue;
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 80, 0, false, true));
            if (world.getTime() % 10L == 0L) {
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK,
                    mob.getX(), mob.getY() + mob.getHeight() + 0.35, mob.getZ(),
                    12, 0.35, 0.25, 0.35, 0.04);
                world.spawnParticles(ParticleTypes.END_ROD,
                    mob.getX(), mob.getY() + mob.getHeight() * 0.6, mob.getZ(),
                    8, 0.25, 0.35, 0.25, 0.02);
            }
        }
        if (world.getTime() % 40L == 0L) {
            for (UUID uuid : event.participants) {
                ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
                if (player != null) {
                    player.sendMessage(Text.literal("\u00A7eUltimo enemigo marcado."), true);
                }
            }
        }
    }

    private ZombieEntity getBoss(ServerWorld world, EventTrial event) {
        if (event.bossId == null) return null;
        Entity entity = world.getEntity(event.bossId);
        return entity instanceof ZombieEntity zombie ? zombie : null;
    }

    private ServerPlayerEntity findNearestParticipant(ServerWorld world, EventTrial event, Vec3d pos) {
        ServerPlayerEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (UUID uuid : event.participants) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player == null || player.getWorld() != world) continue;
            double distance = player.getPos().squaredDistanceTo(pos);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private void targetNearestParticipant(MobEntity mob) {
        if (activeEvent == null) return;
        ServerPlayerEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (UUID uuid : activeEvent.participants) {
            ServerPlayerEntity player = mob.getServer().getPlayerManager().getPlayer(uuid);
            if (player == null || player.getWorld() != mob.getWorld()) continue;
            double distance = mob.squaredDistanceTo(player);
            if (distance < best) {
                best = distance;
                nearest = player;
            }
        }
        if (nearest != null) mob.setTarget(nearest);
    }

    private void discardMobs(ServerWorld world, Set<UUID> mobs) {
        for (UUID uuid : mobs) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) entity.discard();
        }
    }

    private void cleanupEndermen(ServerWorld world, BlockPos center, int radius) {
        Box box = new Box(center).expand(radius + 12, 28, radius + 12);
        for (EndermanEntity enderman : world.getEntitiesByClass(EndermanEntity.class, box, entity -> true)) {
            enderman.discard();
        }
    }

    private void spawnWaveEffects(ServerWorld world, BlockPos center, int wave) {
        int radius = ModConfig.get().eventTrial.radioArena - 14;
        int[][] points = {{radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}, {radius / 2, radius / 2}, {-radius / 2, -radius / 2}};
        for (int[] point : points) {
            spawnLightning(world, center.add(point[0], 4, point[1]));
        }
        spawnLightning(world, center.up(4));
        world.spawnParticles(ParticleTypes.SONIC_BOOM, center.getX() + 0.5, center.getY() + 4.0, center.getZ() + 0.5, 1, 0.0, 0.0, 0.0, 0.0);
        for (int i = 0; i < 16; i++) {
            double angle = Math.PI * 2.0 * i / 16.0;
            double x = center.getX() + 0.5 + Math.cos(angle) * 8.0;
            double z = center.getZ() + 0.5 + Math.sin(angle) * 8.0;
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, x, center.getY() + 2.2, z, 2, 0.06, 0.06, 0.06, 0.04);
        }
        world.spawnParticles(ParticleTypes.DRAGON_BREATH, center.getX() + 0.5, center.getY() + 5.0, center.getZ() + 0.5, 120, 8.0, 1.2, 8.0, 0.04);
        world.playSound(null, center, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.6f, 1.1f);
        world.playSound(null, center, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 1.0f, 0.8f);
        sendEventActionbar(world.getServer(), "&5Oleada &d" + wave);
    }

    private void spawnWaveClearEffects(ServerWorld world, BlockPos center) {
        world.spawnParticles(ParticleTypes.FIREWORK, center.getX() + 0.5, center.getY() + 3.0, center.getZ() + 0.5, 60, 4.0, 1.8, 4.0, 0.12);
        world.playSound(null, center, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.HOSTILE, 1.2f, 1.0f);
        world.playSound(null, center, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.HOSTILE, 0.8f, 1.2f);
        sendEventActionbar(world.getServer(), "&aOleada completada");
    }

    private void sendEventActionbar(MinecraftServer server, String message) {
        if (activeEvent == null || server == null) return;
        for (UUID uuid : activeEvent.participants) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player != null) {
                player.sendMessage(TextUtil.literal(message), true);
            }
        }
    }

    private void spawnAmbientEffects(ServerWorld world, BlockPos center) {
        world.spawnParticles(ParticleTypes.PORTAL, center.getX() + 0.5, center.getY() + 6.0, center.getZ() + 0.5, 36, 6.0, 1.0, 6.0, 0.04);
        int radius = Math.min(ModConfig.get().eventTrial.radioArena - 26, 50);
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * 2.0 * i / 6.0 + world.getTime() * 0.01;
            double x = center.getX() + 0.5 + Math.cos(angle) * radius;
            double z = center.getZ() + 0.5 + Math.sin(angle) * radius;
            world.spawnParticles(ParticleTypes.REVERSE_PORTAL, x, center.getY() + 4.0, z, 8, 0.4, 0.5, 0.4, 0.02);
            if (i % 2 == 0) {
                world.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, center.getY() + 3.2, z, 3, 0.18, 0.15, 0.18, 0.01);
            }
        }
    }

    private void spawnLightning(ServerWorld world, BlockPos pos) {
        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
        if (lightning != null) {
            lightning.refreshPositionAfterTeleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            lightning.setCosmetic(true);
            world.spawnEntity(lightning);
        }
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 36, 0.8, 1.2, 0.8, 0.08);
    }

    private void spawnVictoryFireworks(ServerWorld world, BlockPos center) {
        for (int i = 0; i < 10; i++) {
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            NbtCompound fireworks = rocket.getOrCreateSubNbt("Fireworks");
            fireworks.putByte("Flight", (byte) 2);
            NbtCompound explosion = new NbtCompound();
            explosion.putByte("Type", (byte) (i % 2 == 0 ? 1 : 2));
            explosion.putIntArray("Colors", new int[]{0xAA00FF, 0xFFD700, 0x55FFFF});
            explosion.putBoolean("Flicker", true);
            explosion.putBoolean("Trail", true);
            NbtList explosions = new NbtList();
            explosions.add(explosion);
            fireworks.put("Explosions", explosions);
            double angle = Math.PI * 2.0 * i / 10.0;
            world.spawnEntity(new FireworkRocketEntity(world, rocket, center.getX() + 0.5 + Math.cos(angle) * 8.0, center.getY() + 6.0, center.getZ() + 0.5 + Math.sin(angle) * 8.0, true));
        }
    }

    private void setAttr(net.minecraft.entity.LivingEntity entity, net.minecraft.entity.attribute.EntityAttribute attribute, double value) {
        var instance = entity.getAttributeInstance(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private void broadcast(MinecraftServer server, String message) {
        Text text = TextUtil.literal(message);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(text, false);
        }
    }
}
