package com.tuservidor.mcextremo.manager;

import com.tuservidor.mcextremo.MCExtremo;
import com.tuservidor.mcextremo.config.ModConfig;
import com.tuservidor.mcextremo.network.TrialCinematicNetworking;
import com.tuservidor.mcextremo.skilltree.SkillPassiveHandler;
import com.tuservidor.mcextremo.util.TextUtil;
import net.minecraft.block.Blocks;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ReviveTrialManager {
    public static final String STATE_ALIVE = "ALIVE";
    public static final String STATE_TRIAL = "REVIVE_TRIAL";
    public static final String STATE_VOLUNTARY_TRIAL = "VOLUNTARY_TRIAL";
    public static final String STATE_RETRY = "REVIVE_RETRY";
    public static final String STATE_ELIMINATED = "ELIMINATED";

    private static final int WAVE_DELAY_TICKS = 4 * 20;
    private static final int BOSS_INTRO_TICKS = 80;

    private final MCExtremo mod;
    private final Map<UUID, Trial> activeTrials = new HashMap<>();
    private final Map<UUID, SavedInventory> savedInventories = new HashMap<>();

    private record Trial(
        UUID uuid,
        ServerBossBar bossBar,
        BlockPos center,
        int attempt,
        int wave,
        int actionCooldown,
        Set<UUID> mobIds,
        boolean victoryDelay,
        UUID bossId,
        int bossMechanicCooldown,
        int lightningCooldown,
        boolean bossEarlyMinionsTriggered,
        boolean bossPhaseTwoTriggered,
        boolean bossFinalMinionsTriggered,
        boolean bossRecovered,
        int bossIntroTicks,
        boolean voluntary
    ) {
        Trial tick() {
            return new Trial(
                uuid, bossBar, center, attempt, wave,
                Math.max(0, actionCooldown - 1), mobIds, victoryDelay,
                bossId, Math.max(0, bossMechanicCooldown - 1),
                Math.max(0, lightningCooldown - 1),
                bossEarlyMinionsTriggered, bossPhaseTwoTriggered, bossFinalMinionsTriggered, bossRecovered,
                Math.max(0, bossIntroTicks - 1), voluntary
            );
        }

        Trial nextWave(int nextWave, Set<UUID> mobs, UUID nextBossId) {
            int introTicks = nextBossId != null ? BOSS_INTRO_TICKS : 0;
            return new Trial(uuid, bossBar, center, attempt, nextWave, WAVE_DELAY_TICKS, mobs, false, nextBossId, 12 * 20, 14 * 20, false, false, false, false, introTicks, voluntary);
        }

        Trial startVictoryDelay(int cooldown) {
            return new Trial(uuid, bossBar, center, attempt, wave, cooldown, new HashSet<>(), true, null, 0, 0, bossEarlyMinionsTriggered, bossPhaseTwoTriggered, bossFinalMinionsTriggered, bossRecovered, 0, voluntary);
        }

        Trial withBossMechanics(UUID currentBossId, int cooldown, int currentLightningCooldown, boolean earlyMinionsTriggered, boolean phaseTwoTriggered, boolean finalMinionsTriggered, boolean recovered) {
            return new Trial(uuid, bossBar, center, attempt, wave, actionCooldown, mobIds, victoryDelay, currentBossId, cooldown, currentLightningCooldown, earlyMinionsTriggered, phaseTwoTriggered, finalMinionsTriggered, recovered, bossIntroTicks, voluntary);
        }

        Trial withBossIntroTicks(int ticks) {
            return new Trial(uuid, bossBar, center, attempt, wave, actionCooldown, mobIds, victoryDelay, bossId, bossMechanicCooldown, lightningCooldown, bossEarlyMinionsTriggered, bossPhaseTwoTriggered, bossFinalMinionsTriggered, bossRecovered, ticks, voluntary);
        }
    }

    private record SavedInventory(List<ItemStack> slots) {
    }

    public ReviveTrialManager(MCExtremo mod) {
        this.mod = mod;
    }

    public boolean isInTrial(UUID uuid) {
        return activeTrials.containsKey(uuid);
    }

    public void startTrial(ServerPlayerEntity player) {
        int attempt = STATE_RETRY.equals(mod.getDataManager().getTrialState(player.getUuid())) ? 2 : 1;
        startTrialAttempt(player, attempt, false);
    }

    public boolean startVoluntaryTrial(ServerPlayerEntity player) {
        ModConfig.ReviveTrial config = ModConfig.get().reviveTrial;
        if (!config.activado || !config.trialVoluntarioActivado) {
            player.sendMessage(TextUtil.literal("&cLa prueba voluntaria esta deshabilitada."), false);
            return false;
        }
        if (isInTrial(player.getUuid())) {
            player.sendMessage(TextUtil.literal("&cYa estas dentro de una prueba."), false);
            return false;
        }
        if (mod.getLivesManager().getVidas(player.getUuid()) <= 0) {
            player.sendMessage(TextUtil.literal("&cNo puedes iniciar una prueba voluntaria estando eliminado."), false);
            return false;
        }
        int maxLives = mod.getLivesManager().getDefaultLives();
        if (mod.getLivesManager().getVidas(player.getUuid()) >= maxLives) {
            player.sendMessage(TextUtil.literal("&eYa tienes el maximo de vidas. No necesitas hacer esta prueba."), false);
            return false;
        }

        long now = System.currentTimeMillis();
        long cooldownUntil = mod.getDataManager().getVoluntaryTrialCooldownUntil(player.getUuid());
        if (cooldownUntil > now) {
            player.sendMessage(TextUtil.literal("&cDebes esperar &e" + formatCooldown(cooldownUntil - now) + " &cpara volver a iniciar la prueba."), false);
            return false;
        }

        long durationMs = config.trialVoluntarioCooldownMinutos * 60_000L;
        mod.getDataManager().setVoluntaryTrialCooldownUntil(player.getUuid(), now + durationMs);
        startTrialAttempt(player, 1, true);
        return true;
    }

    private String formatCooldown(long millis) {
        long totalSeconds = Math.max(1L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes <= 0) {
            return seconds + "s";
        }
        return minutes + "m " + seconds + "s";
    }

    public void tick(MinecraftServer server) {
        if (activeTrials.isEmpty()) return;

        Iterator<Map.Entry<UUID, Trial>> iterator = activeTrials.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Trial> entry = iterator.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            Trial trial = entry.getValue();
            if (player == null) {
                cleanupTrial(server, trial);
                mod.getDataManager().setTrialState(entry.getKey(),
                    trial.voluntary() ? STATE_ALIVE : trial.attempt() < ModConfig.get().reviveTrial.maxIntentos ? STATE_RETRY : STATE_ELIMINATED);
                iterator.remove();
                continue;
            }

            trial = trial.tick();
            keepPlayerInsideArena(player, trial.center());
            removeEndermenInsideArena((ServerWorld) player.getWorld(), trial.center(), ModConfig.get().reviveTrial.radioArena);
            if (player.getWorld().getTime() % 40L == 0L) {
                spawnArenaAmbientEffects((ServerWorld) player.getWorld(), trial.center());
            }
            if (trial.victoryDelay()) {
                updateVictoryDelay(player, trial);
                if (trial.actionCooldown() <= 0) {
                    finishVictory(player, trial);
                    iterator.remove();
                } else {
                    entry.setValue(trial);
                }
                continue;
            }

            if (trial.wave() <= 0) {
                updateBossBar(player, trial, 0);
                if (trial.actionCooldown() <= 0) {
                    Set<UUID> mobs = spawnWave(player, trial.center(), 1);
                    trial = trial.nextWave(1, mobs, null);
                }
                entry.setValue(trial);
                continue;
            }

            if (trial.wave() >= ModConfig.get().reviveTrial.oleadas) {
                trial = tickBossMechanics(player, trial);
            }
            keepTrialMobsInsideArena((ServerWorld) player.getWorld(), trial);

            int alive = countAliveMobs((ServerWorld) player.getWorld(), trial.mobIds());
            if (alive <= 0) {
                if (trial.wave() >= ModConfig.get().reviveTrial.oleadas) {
                    trial = startVictoryDelay(player, trial);
                    entry.setValue(trial);
                } else if (trial.actionCooldown() <= 0) {
                    int nextWave = trial.wave() + 1;
                    spawnWaveClearEffects((ServerWorld) player.getWorld(), trial.center(), trial.wave());
                    Set<UUID> mobs = spawnWave(player, trial.center(), nextWave);
                    UUID bossId = nextWave >= ModConfig.get().reviveTrial.oleadas && !mobs.isEmpty() ? mobs.iterator().next() : null;
                    trial = trial.nextWave(nextWave, mobs, bossId);
                    entry.setValue(trial);
                } else {
                    updateBossBar(player, trial, 0);
                    entry.setValue(trial);
                }
            } else {
                updateBossBar(player, trial, alive);
                entry.setValue(trial);
            }
        }
    }

    public void failTrial(ServerPlayerEntity player) {
        Trial trial = activeTrials.remove(player.getUuid());
        if (trial != null) {
            cleanupTrial(player.getServer(), trial);
        }

        if (trial != null && trial.voluntary()) {
            mod.getDataManager().setTrialState(player.getUuid(), STATE_ALIVE);
            restoreInventory(player);
            mod.getLivesManager().restoreAfterRevive(player);
            player.sendMessage(TextUtil.literal("&cFallaste la prueba voluntaria. &7No pierdes vidas, pero el cooldown se mantiene."), false);
            return;
        }

        int attempt = trial != null ? trial.attempt() : getAttemptFromState(player.getUuid());
        if (attempt < ModConfig.get().reviveTrial.maxIntentos) {
            mod.getDataManager().setTrialState(player.getUuid(), STATE_RETRY);
            player.sendMessage(TextUtil.literal("&cHas fallado la prueba. &eTienes un ultimo intento."), false);
            startTrialAttempt(player, attempt + 1, false);
            return;
        }

        mod.getDataManager().setTrialState(player.getUuid(), STATE_ELIMINATED);
        player.changeGameMode(GameMode.SPECTATOR);
        restoreInventory(player);
        player.sendMessage(TextUtil.literal("&4Has fallado la prueba final. &7Seras expulsado del servidor."), false);
        if (ModConfig.get().reviveTrial.banAlFallar) {
            mod.getLivesManager().scheduleEliminationBan(player);
        }
    }

    public void handleDisconnect(ServerPlayerEntity player) {
        Trial trial = activeTrials.remove(player.getUuid());
        if (trial == null) return;

        cleanupTrial(player.getServer(), trial);
        if (trial.voluntary()) {
            mod.getDataManager().setTrialState(player.getUuid(), STATE_ALIVE);
            restoreInventory(player);
            return;
        }
        if (trial.attempt() < ModConfig.get().reviveTrial.maxIntentos) {
            mod.getDataManager().setTrialState(player.getUuid(), STATE_RETRY);
        } else {
            mod.getDataManager().setTrialState(player.getUuid(), STATE_ELIMINATED);
            restoreInventory(player);
            if (ModConfig.get().reviveTrial.banAlFallar) {
                mod.getLivesManager().scheduleEliminationBan(player);
            }
        }
    }

    public void setArena(ServerPlayerEntity player) {
        ModConfig.ReviveTrial config = ModConfig.get().reviveTrial;
        config.world = player.getWorld().getRegistryKey().getValue().toString();
        config.x = player.getX();
        config.y = player.getY();
        config.z = player.getZ();
        config.yaw = player.getYaw();
        config.pitch = player.getPitch();
        me.shedaniel.autoconfig.AutoConfig.getConfigHolder(ModConfig.class).save();
    }

    public boolean canResumeTrial(UUID uuid) {
        String state = mod.getDataManager().getTrialState(uuid);
        return STATE_RETRY.equals(state) || STATE_TRIAL.equals(state);
    }

    public boolean recoverInterruptedVoluntaryTrial(ServerPlayerEntity player) {
        if (!STATE_VOLUNTARY_TRIAL.equals(mod.getDataManager().getTrialState(player.getUuid()))) {
            return false;
        }
        mod.getDataManager().setTrialState(player.getUuid(), STATE_ALIVE);
        restoreInventory(player);
        mod.getLivesManager().restoreAfterRevive(player);
        player.sendMessage(TextUtil.literal("&eTu prueba voluntaria fue interrumpida. &7Se restauro tu inventario y volviste al spawn."), false);
        return true;
    }

    private void startTrialAttempt(ServerPlayerEntity player, int attempt, boolean voluntary) {
        ModConfig.ReviveTrial config = ModConfig.get().reviveTrial;
        if (!config.activado) {
            if (voluntary) {
                player.sendMessage(TextUtil.literal("&cLa prueba esta deshabilitada."), false);
            } else {
                mod.getLivesManager().scheduleEliminationBan(player);
            }
            return;
        }

        Trial previous = activeTrials.remove(player.getUuid());
        if (previous != null) {
            cleanupTrial(player.getServer(), previous);
        }

        ServerWorld world = getTrialWorld(player.getServer());
        BlockPos center = getIslandCenter(player.getUuid(), config, attempt);
        generateIsland(world, center, config.radioArena);

        mod.getDataManager().setTrialState(player.getUuid(), voluntary ? STATE_VOLUNTARY_TRIAL : STATE_TRIAL);
        player.changeGameMode(GameMode.SURVIVAL);
        player.teleport(world, center.getX() + 0.5, center.getY() + 3.0, center.getZ() + 0.5, config.yaw, config.pitch);
        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.getHungerManager().setSaturationLevel(10.0f);
        saveAndHideInventory(player);
        giveTrialKit(player);

        ServerBossBar bossBar = new ServerBossBar(
            TextUtil.literal("&dPrueba de Revivir"),
            BossBar.Color.PURPLE,
            BossBar.Style.NOTCHED_10
        );
        bossBar.addPlayer(player);
        bossBar.setPercent(1.0f);

        int preparation = Math.max(20, config.preparacionSegundos * 20);
        activeTrials.put(player.getUuid(), new Trial(player.getUuid(), bossBar, center, attempt, 0, preparation, new HashSet<>(), false, null, 0, 0, false, false, false, false, 0, voluntary));
        if (voluntary) {
            player.sendMessage(TextUtil.literal("&5Has iniciado una prueba voluntaria. &eSobrevive para recuperar una vida."), false);
            player.sendMessage(TextUtil.literal("&7Cooldown: &e" + config.trialVoluntarioCooldownMinutos + " minuto(s)&7."), false);
        } else {
            player.sendMessage(TextUtil.literal("&5Has entrado a la isla del End. &eSobrevive 4 oleadas y derrota al Coloso para revivir."), false);
            player.sendMessage(TextUtil.literal("&7Intento &e" + attempt + "&7/" + config.maxIntentos + "&7."), false);
        }
    }

    private Trial startVictoryDelay(ServerPlayerEntity player, Trial trial) {
        discardMobs(getTrialWorld(player.getServer()), trial.mobIds());
        spawnWaveClearEffects((ServerWorld) player.getWorld(), trial.center(), trial.wave());
        spawnVictoryFireworks((ServerWorld) player.getWorld(), trial.center());
        int delay = Math.max(20, ModConfig.get().reviveTrial.segundosAntesDeRevivir * 20);
        trial.bossBar().setPercent(1.0f);
        trial.bossBar().setName(TextUtil.literal("&aFelicidades, superaste la prueba"));
        player.sendMessage(TextUtil.literal("&aFelicidades, superaste la prueba. &ePronto reviviras."), false);
        return trial.startVictoryDelay(delay);
    }

    private void finishVictory(ServerPlayerEntity player, Trial trial) {
        cleanupTrial(player.getServer(), trial);
        mod.getDataManager().setTrialState(player.getUuid(), STATE_ALIVE);
        if (trial.voluntary()) {
            int currentLives = mod.getLivesManager().getVidas(player.getUuid());
            int maxLives = mod.getLivesManager().getDefaultLives();
            int reward = ModConfig.get().reviveTrial.vidasAlGanarVoluntario;
            mod.getLivesManager().setVidas(player.getUuid(), Math.min(maxLives, currentLives + reward));
        } else {
            mod.getLivesManager().setVidas(player.getUuid(), ModConfig.get().reviveTrial.vidasAlGanar);
        }
        restoreInventory(player);
        mod.getLivesManager().restoreAfterRevive(player);
        SkillPassiveHandler.applyTrialVictoryBuff(player);
        if (trial.voluntary()) {
            player.sendMessage(TextUtil.literal("&aGanaste la prueba voluntaria. &eRecuperaste una vida y tu inventario regreso."), false);
        } else {
            player.sendMessage(TextUtil.literal("&aHas revivido y tu inventario original ha regresado."), false);
        }
    }

    private void updateBossBar(ServerPlayerEntity player, Trial trial, int alive) {
        ModConfig.ReviveTrial config = ModConfig.get().reviveTrial;
        if (trial.wave() >= config.oleadas) {
            updateBossHealthBar(player, trial, alive);
            return;
        }
        trial.bossBar().setPercent(Math.min(1.0f, Math.max(0.05f, trial.wave() / (float) config.oleadas)));
        if (trial.wave() <= 0) {
            trial.bossBar().setName(Text.literal(getTrialTitle(trial) + " \u00A77- \u00A7fPreparacion "
                + Math.max(1, trial.actionCooldown() / 20) + "s"));
            player.sendMessage(Text.literal("\u00A7dLa oleada comenzara pronto..."), true);
        } else if (alive > 0) {
            trial.bossBar().setName(Text.literal(getTrialTitle(trial) + " \u00A77- \u00A7fOleada "
                + trial.wave() + "/" + config.oleadas + " \u00A78| \u00A7c" + alive + " enemigos"));
        } else {
            trial.bossBar().setName(Text.literal(getTrialTitle(trial) + " \u00A77- \u00A7fSiguiente oleada "
                + Math.max(1, trial.actionCooldown() / 20) + "s"));
            player.sendMessage(Text.literal("\u00A7ePreparate para la siguiente oleada..."), true);
        }
    }

    private String getTrialTitle(Trial trial) {
        return trial.voluntary() ? "\u00A7dPrueba de Vidas" : "\u00A7dPrueba de Revivir";
    }

    private void updateBossHealthBar(ServerPlayerEntity player, Trial trial, int alive) {
        ZombieEntity boss = getBoss(player.getServer(), trial);
        if (boss != null && boss.isAlive()) {
            float health = Math.max(0.0f, boss.getHealth());
            float maxHealth = Math.max(1.0f, boss.getMaxHealth());
            String phase = trial.bossPhaseTwoTriggered() ? " \u00A74- Fase 2" : "";
            trial.bossBar().setPercent(Math.max(0.03f, Math.min(1.0f, health / maxHealth)));
            trial.bossBar().setName(Text.literal("\u00A75Coloso del Vacio" + phase + " \u00A77| \u00A7c" + Math.round(health) + "\u00A77/\u00A7c" + Math.round(maxHealth) + " HP"));
        } else if (alive > 0) {
            trial.bossBar().setPercent(0.08f);
            trial.bossBar().setName(Text.literal("\u00A75Coloso derrotado \u00A77| \u00A7cRemata " + alive + " esbirro(s)"));
        } else {
            trial.bossBar().setPercent(0.03f);
            trial.bossBar().setName(Text.literal("\u00A7aColoso derrotado"));
        }
    }

    private void updateVictoryDelay(ServerPlayerEntity player, Trial trial) {
        int seconds = Math.max(1, trial.actionCooldown() / 20);
        int total = Math.max(1, ModConfig.get().reviveTrial.segundosAntesDeRevivir * 20);
        trial.bossBar().setPercent(Math.max(0.05f, trial.actionCooldown() / (float) total));
        trial.bossBar().setName(Text.literal("\u00A7aPronto reviviras \u00A77- \u00A7f" + seconds + "s"));
        player.sendMessage(Text.literal("\u00A7aFelicidades, superaste la prueba. \u00A7eReviviras en " + seconds + "s."), true);
    }

    private Set<UUID> spawnWave(ServerPlayerEntity player, BlockPos center, int wave) {
        ServerWorld world = (ServerWorld) player.getWorld();
        int amount = getWaveSize(wave);
        Set<UUID> mobs = new HashSet<>();
        spawnWaveStartEffects(world, center, wave);
        if (wave >= ModConfig.get().reviveTrial.oleadas) {
            giveBossWaveUpgrade(player);
            player.sendMessage(TextUtil.literal("&5Oleada final &7- &dEl Coloso del Vacio ha despertado"), false);
        } else {
            player.sendMessage(TextUtil.literal("&5Oleada &e" + wave + "&5/" + ModConfig.get().reviveTrial.oleadas + " &7- &c" + amount + " enemigos"), false);
        }

        for (int i = 0; i < amount; i++) {
            ZombieEntity zombie = EntityType.ZOMBIE.create(world);
            if (zombie == null) continue;
            boolean finalWave = wave >= ModConfig.get().reviveTrial.oleadas;
            BlockPos spawn = finalWave
                ? center.add(0, 18, 0)
                : findSafeSpawnPos(world, getWaveSpawnPos(center, wave, i), center);
            double angle = Math.atan2(center.getZ() - spawn.getZ(), center.getX() - spawn.getX());
            zombie.refreshPositionAndAngles(
                spawn.getX() + 0.5,
                spawn.getY(),
                spawn.getZ() + 0.5,
                (float) Math.toDegrees(angle) - 90.0f,
                0.0f
            );
            zombie.addCommandTag("mcextremo_trial");
            zombie.setTarget(finalWave ? null : player);
            equipZombie(zombie, wave, i);
            world.spawnEntity(zombie);
            mod.getZombieManager().applyScaling(zombie, Math.max(35, mod.getZombieManager().getDay(world)));
            if (finalWave) {
                equipBossZombie(zombie);
                zombie.setAiDisabled(true);
                zombie.setVelocity(0.0, -0.25, 0.0);
                zombie.velocityModified = true;
                TrialCinematicNetworking.sendBossIntro(player, zombie.getId(), zombie.getPos().add(0.0, zombie.getHeight() * 0.75, 0.0), BOSS_INTRO_TICKS);
            }
            mobs.add(zombie.getUuid());
        }
        return mobs;
    }

    private BlockPos getWaveSpawnPos(BlockPos center, int wave, int index) {
        int radius = Math.max(10, ModConfig.get().reviveTrial.radioArena - 6);
        int diagonal = Math.max(8, radius - 4);
        int[][] anchors = {
            {radius, 0}, {-radius, 0}, {0, radius}, {0, -radius},
            {diagonal, diagonal}, {-diagonal, diagonal}, {diagonal, -diagonal}, {-diagonal, -diagonal}
        };
        int[] anchor = anchors[Math.floorMod(index + wave, anchors.length)];
        int lane = (index / anchors.length) % 3 - 1;
        return center.add(anchor[0] + lane * 2, 2, anchor[1] - lane * 2);
    }

    private BlockPos findSafeSpawnPos(ServerWorld world, BlockPos preferred, BlockPos center) {
        int[][] offsets = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2},
            {2, 2}, {-2, 2}, {2, -2}, {-2, -2},
            {3, 0}, {-3, 0}, {0, 3}, {0, -3}
        };
        for (int[] offset : offsets) {
            BlockPos base = preferred.add(offset[0], 0, offset[1]);
            for (int y = center.getY() + 6; y >= center.getY() - 2; y--) {
                BlockPos feet = new BlockPos(base.getX(), y, base.getZ());
                if (isSpawnSafe(world, feet)) {
                    return feet;
                }
            }
        }
        return center.add(0, 3, 0);
    }

    private boolean isSpawnSafe(ServerWorld world, BlockPos feet) {
        return world.getBlockState(feet).isAir()
            && world.getBlockState(feet.up()).isAir()
            && !world.getBlockState(feet.down()).isAir()
            && !world.getBlockState(feet.down()).isOf(Blocks.BARRIER)
            && !world.getBlockState(feet.down()).isOf(Blocks.END_ROD);
    }

    private int getWaveSize(int wave) {
        ModConfig.ReviveTrial config = ModConfig.get().reviveTrial;
        return switch (wave) {
            case 1 -> config.zombiesOleada1;
            case 2 -> config.zombiesOleada2;
            case 3 -> config.zombiesOleada3;
            case 4 -> config.zombiesOleada4;
            default -> 1;
        };
    }

    private void equipZombie(ZombieEntity zombie, int wave, int index) {
        if (wave >= ModConfig.get().reviveTrial.oleadas) {
            return;
        }
        if (wave >= 2 && index % 2 == 0) {
            zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
        }
        if (wave >= 3) {
            zombie.equipStack(EquipmentSlot.CHEST, new ItemStack(index == 0 ? Items.DIAMOND_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE));
            if (index == 0) {
                zombie.setCustomName(Text.literal("\u00A75Guardian del Vacio"));
                var health = zombie.getAttributeInstance(net.minecraft.entity.attribute.EntityAttributes.GENERIC_MAX_HEALTH);
                if (health != null) {
                    health.setBaseValue(zombie.getMaxHealth() + 20.0);
                    zombie.setHealth(zombie.getMaxHealth());
                }
            }
        }
    }

    private void giveBossWaveUpgrade(ServerPlayerEntity player) {
        SkillPassiveHandler.applyBossStartBuff(player);
        player.equipStack(EquipmentSlot.HEAD, enchantedArmor(Items.DIAMOND_HELMET));
        player.equipStack(EquipmentSlot.CHEST, enchantedArmor(Items.DIAMOND_CHESTPLATE));
        player.equipStack(EquipmentSlot.LEGS, enchantedArmor(Items.DIAMOND_LEGGINGS));
        player.equipStack(EquipmentSlot.FEET, enchantedArmor(Items.DIAMOND_BOOTS));
        player.getInventory().setStack(player.getInventory().selectedSlot, enchantedSword());
        player.getInventory().markDirty();
        player.sendMessage(TextUtil.literal("&bTu equipo se fortalece: &fDiamante Proteccion III &7+ &fFilo II&7."), false);
    }

    private ItemStack enchantedArmor(net.minecraft.item.Item item) {
        ItemStack stack = new ItemStack(item);
        Map<net.minecraft.enchantment.Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantments.PROTECTION, 3);
        EnchantmentHelper.set(enchantments, stack);
        return stack;
    }

    private ItemStack enchantedSword() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        Map<net.minecraft.enchantment.Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantments.SHARPNESS, 2);
        EnchantmentHelper.set(enchantments, stack);
        return stack;
    }

    private void equipBossZombie(ZombieEntity zombie) {
        ModConfig.ReviveTrial config = ModConfig.get().reviveTrial;
        zombie.setCustomName(Text.literal("\u00A75Coloso del Vacio"));
        zombie.setCustomNameVisible(true);
        zombie.setPersistent();
        zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
        zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        zombie.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        zombie.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        zombie.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20 * 60 * 10, 1, false, true));
        zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 60 * 10, 1, false, true));
        zombie.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 60 * 10, 0, false, false));

        var healthAttr = zombie.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.setBaseValue(zombie.getMaxHealth() + config.jefeOleada5VidaExtra);
            zombie.setHealth(zombie.getMaxHealth());
        }
        var damageAttr = zombie.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() + config.jefeOleada5DanoExtra);
        }
        var armorAttr = zombie.getAttributeInstance(EntityAttributes.GENERIC_ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(armorAttr.getBaseValue() + config.jefeOleada5ArmaduraExtra);
        }
        var followAttr = zombie.getAttributeInstance(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (followAttr != null) {
            followAttr.setBaseValue(Math.max(followAttr.getBaseValue(), 48.0));
        }
    }

    private Trial tickBossMechanics(ServerPlayerEntity player, Trial trial) {
        ServerWorld world = (ServerWorld) player.getWorld();
        Trial recoveredTrial = recoverMissingBoss(world, player, trial);
        ZombieEntity boss = getBoss(player.getServer(), recoveredTrial);
        if (boss == null || !boss.isAlive()) {
            return recoveredTrial;
        }

        if (recoveredTrial.bossIntroTicks() > 0 || boss.isAiDisabled()) {
            return tickBossIntro(world, player, boss, recoveredTrial);
        }

        float ratio = boss.getHealth() / Math.max(1.0f, boss.getMaxHealth());
        boolean earlyMinions = recoveredTrial.bossEarlyMinionsTriggered();
        boolean phaseTwo = recoveredTrial.bossPhaseTwoTriggered();
        boolean finalMinions = recoveredTrial.bossFinalMinionsTriggered();
        boolean recovered = recoveredTrial.bossRecovered();
        int cooldown = recoveredTrial.bossMechanicCooldown();
        int lightningCooldown = recoveredTrial.lightningCooldown();

        if (!earlyMinions && ratio <= 0.66f) {
            spawnBossMinions(world, player, recoveredTrial.center(), recoveredTrial.mobIds(), 2);
            boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 8, 0, false, true));
            player.sendMessage(TextUtil.literal("&5El Coloso llama refuerzos del vacio."), false);
            earlyMinions = true;
            cooldown = 12 * 20;
        }

        if (!phaseTwo && ratio <= 0.50f) {
            spawnBossMinions(world, player, recoveredTrial.center(), recoveredTrial.mobIds(), 4);
            healBossForStageTwo(boss);
            empowerBossStageTwo(boss);
            spawnVisualLightning(world, boss.getBlockPos());
            player.sendMessage(TextUtil.literal("&4El Coloso entra en Fase 2 y absorbe energia del vacio."), false);
            phaseTwo = true;
            cooldown = 12 * 20;
            lightningCooldown = 6 * 20;
        }

        if (!finalMinions && ratio <= 0.25f) {
            spawnBossMinions(world, player, recoveredTrial.center(), recoveredTrial.mobIds(), 3);
            boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 10, 1, false, true));
            player.sendMessage(TextUtil.literal("&4El Coloso abre una grieta final."), false);
            finalMinions = true;
            cooldown = 10 * 20;
        }

        double distanceToBoss = boss.squaredDistanceTo(player);
        double distanceToCenter = player.squaredDistanceTo(recoveredTrial.center().getX() + 0.5, recoveredTrial.center().getY() + 2.0, recoveredTrial.center().getZ() + 0.5);
        if (cooldown <= 0 && distanceToBoss > 12.0 * 12.0) {
            boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 5, 1, false, true));
            player.sendMessage(TextUtil.literal("&5El Coloso acelera para alcanzarte."), true);
            cooldown = 12 * 20;
        }

        if (phaseTwo && lightningCooldown <= 0) {
            triggerBossLightning(world, player, boss, recoveredTrial.center());
            lightningCooldown = 14 * 20;
        }

        if (phaseTwo && distanceToCenter > 22.0 * 22.0) {
            double dx = (recoveredTrial.center().getX() + 0.5) - player.getX();
            double dz = (recoveredTrial.center().getZ() + 0.5) - player.getZ();
            double length = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
            player.addVelocity(dx / length * 0.25, 0.12, dz / length * 0.25);
            player.velocityModified = true;
            player.sendMessage(TextUtil.literal("&5La energia del Coloso te empuja al centro."), true);
        }

        return recoveredTrial.withBossMechanics(recoveredTrial.bossId(), cooldown, lightningCooldown, earlyMinions, phaseTwo, finalMinions, recovered);
    }

    private Trial tickBossIntro(ServerWorld world, ServerPlayerEntity player, ZombieEntity boss, Trial trial) {
        if (trial.bossIntroTicks() > 0) {
            boss.setAiDisabled(true);
            boss.setNoGravity(false);
            boss.setTarget(null);
            if (!boss.isOnGround()) {
                boss.setVelocity(0.0, -0.35, 0.0);
                boss.velocityModified = true;
            }
            if (trial.bossIntroTicks() % 5 == 0) {
                world.spawnParticles(ParticleTypes.DRAGON_BREATH, boss.getX(), boss.getY() + 1.2, boss.getZ(), 18, 0.7, 0.7, 0.7, 0.02);
                world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, boss.getX(), boss.getY() + 1.0, boss.getZ(), 10, 0.5, 0.5, 0.5, 0.05);
            }
            if (trial.bossIntroTicks() == BOSS_INTRO_TICKS - 10) {
                player.sendMessage(TextUtil.literal("&5El Coloso del Vacio desciende sobre la arena."), false);
            }
            return trial;
        }

        boss.setAiDisabled(false);
        boss.setTarget(player);
        spawnBossImpactEffects(world, boss.getBlockPos(), trial.center());
        player.sendMessage(TextUtil.literal("&4El Coloso del Vacio ha descendido."), false);
        return trial.withBossIntroTicks(0);
    }

    private void healBossForStageTwo(ZombieEntity boss) {
        float maxHealth = Math.max(1.0f, boss.getMaxHealth());
        boss.setHealth(Math.min(maxHealth, boss.getHealth() + maxHealth * 0.5f));
    }

    private void empowerBossStageTwo(ZombieEntity boss) {
        boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 12, 1, false, true));
        boss.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 60 * 10, 2, false, true));
        var damageAttr = boss.getAttributeInstance(EntityAttributes.GENERIC_ATTACK_DAMAGE);
        if (damageAttr != null) {
            damageAttr.setBaseValue(damageAttr.getBaseValue() + 2.0);
        }
    }

    private void triggerBossLightning(ServerWorld world, ServerPlayerEntity player, ZombieEntity boss, BlockPos center) {
        BlockPos target = findSafeSpawnPos(world, player.getBlockPos(), center);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5, 24, 0.6, 0.9, 0.6, 0.05);
        world.playSound(null, target, SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 0.7f, 1.3f);
        spawnVisualLightning(world, target);
        if (player.squaredDistanceTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= 3.5 * 3.5) {
            player.damage(world.getDamageSources().magic(), 5.0f);
            player.addVelocity(0.0, 0.25, 0.0);
            player.velocityModified = true;
        }
        boss.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 20 * 4, 0, false, true));
    }

    private void spawnVisualLightning(ServerWorld world, BlockPos pos) {
        LightningEntity lightning = EntityType.LIGHTNING_BOLT.create(world);
        if (lightning != null) {
            lightning.refreshPositionAfterTeleport(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            lightning.setCosmetic(true);
            world.spawnEntity(lightning);
        }
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 32, 0.8, 1.2, 0.8, 0.08);
        world.playSound(null, pos, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 0.8f, 1.2f);
    }

    private void spawnWaveStartEffects(ServerWorld world, BlockPos center, int wave) {
        int radius = Math.max(8, ModConfig.get().reviveTrial.radioArena - 8);
        int[][] anchors = {
            {radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}
        };
        int strikes = wave >= ModConfig.get().reviveTrial.oleadas ? anchors.length : Math.min(wave + 1, anchors.length);
        for (int i = 0; i < strikes; i++) {
            int[] anchor = anchors[Math.floorMod(i + wave, anchors.length)];
            spawnVisualLightning(world, center.add(anchor[0], 3, anchor[1]));
        }
        world.spawnParticles(ParticleTypes.DRAGON_BREATH, center.getX() + 0.5, center.getY() + 3.4, center.getZ() + 0.5, 70, 5.5, 0.9, 5.5, 0.03);
        world.playSound(null, center, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 0.45f, 1.25f);
    }

    private void spawnWaveClearEffects(ServerWorld world, BlockPos center, int wave) {
        world.spawnParticles(ParticleTypes.END_ROD, center.getX() + 0.5, center.getY() + 3.0, center.getZ() + 0.5, 80, 6.0, 1.2, 6.0, 0.06);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, center.getX() + 0.5, center.getY() + 3.4, center.getZ() + 0.5, 55, 4.5, 0.8, 4.5, 0.08);
        world.playSound(null, center, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.0f + Math.min(0.4f, wave * 0.08f));
    }

    private void spawnBossImpactEffects(ServerWorld world, BlockPos bossPos, BlockPos center) {
        spawnVisualLightning(world, bossPos);
        int[][] offsets = {{4, 0}, {-4, 0}, {0, 4}, {0, -4}};
        for (int[] offset : offsets) {
            spawnVisualLightning(world, center.add(offset[0], 3, offset[1]));
        }
        world.spawnParticles(ParticleTypes.EXPLOSION, bossPos.getX() + 0.5, bossPos.getY() + 1.0, bossPos.getZ() + 0.5, 4, 0.8, 0.4, 0.8, 0.02);
        world.spawnParticles(ParticleTypes.DRAGON_BREATH, bossPos.getX() + 0.5, bossPos.getY() + 1.0, bossPos.getZ() + 0.5, 90, 2.5, 0.8, 2.5, 0.04);
        world.playSound(null, bossPos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 0.9f, 0.65f);
    }

    private void spawnVictoryFireworks(ServerWorld world, BlockPos center) {
        int[][] offsets = {
            {0, 0}, {5, 0}, {-5, 0}, {0, 5}, {0, -5}, {4, 4}, {-4, -4}
        };
        for (int i = 0; i < offsets.length; i++) {
            int[] offset = offsets[i];
            ItemStack rocket = createVictoryRocket(i);
            FireworkRocketEntity firework = new FireworkRocketEntity(
                world,
                rocket,
                center.getX() + 0.5 + offset[0],
                center.getY() + 5.0,
                center.getZ() + 0.5 + offset[1],
                true
            );
            world.spawnEntity(firework);
        }
        world.playSound(null, center, SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    private ItemStack createVictoryRocket(int index) {
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        NbtCompound fireworks = rocket.getOrCreateSubNbt("Fireworks");
        fireworks.putByte("Flight", (byte) 2);

        int[][] palettes = {
            {0xAA00FF, 0xFFD700, 0x55FF55},
            {0x55FFFF, 0xFF55FF, 0xFFFFFF},
            {0xFF5555, 0xFFFF55, 0x5555FF}
        };
        int[] colors = palettes[Math.floorMod(index, palettes.length)];
        NbtCompound explosion = new NbtCompound();
        explosion.putByte("Type", (byte) (index % 2 == 0 ? 1 : 2));
        explosion.putIntArray("Colors", colors);
        explosion.putBoolean("Flicker", true);
        explosion.putBoolean("Trail", true);

        NbtList explosions = new NbtList();
        explosions.add(explosion);
        fireworks.put("Explosions", explosions);
        return rocket;
    }

    private void spawnArenaAmbientEffects(ServerWorld world, BlockPos center) {
        world.spawnParticles(ParticleTypes.PORTAL, center.getX() + 0.5, center.getY() + 4.0, center.getZ() + 0.5, 22, 3.5, 0.8, 3.5, 0.04);
        world.spawnParticles(ParticleTypes.REVERSE_PORTAL, center.getX() + 0.5, center.getY() + 3.2, center.getZ() + 0.5, 14, 2.2, 0.5, 2.2, 0.03);
        int radius = Math.max(12, ModConfig.get().reviveTrial.radioArena - 10);
        int[][] anchors = {{radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}};
        for (int[] anchor : anchors) {
            BlockPos pos = center.add(anchor[0], 3, anchor[1]);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.5, 0.7, 0.5, 0.03);
        }
    }

    private ZombieEntity getBoss(MinecraftServer server, Trial trial) {
        if (trial.bossId() == null) return null;
        Entity entity = getTrialWorld(server).getEntity(trial.bossId());
        return entity instanceof ZombieEntity zombie ? zombie : null;
    }

    private Trial recoverMissingBoss(ServerWorld world, ServerPlayerEntity player, Trial trial) {
        if (trial.bossId() == null || trial.bossRecovered()) {
            return trial;
        }
        Entity current = world.getEntity(trial.bossId());
        if (current instanceof ZombieEntity) {
            return trial;
        }

        ZombieEntity boss = EntityType.ZOMBIE.create(world);
        if (boss == null) {
            return trial.withBossMechanics(trial.bossId(), trial.bossMechanicCooldown(), trial.lightningCooldown(), trial.bossEarlyMinionsTriggered(), trial.bossPhaseTwoTriggered(), trial.bossFinalMinionsTriggered(), true);
        }
        BlockPos spawn = findSafeSpawnPos(world, trial.center().add(0, 2, 0), trial.center());
        boss.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYaw(), 0.0f);
        boss.addCommandTag("mcextremo_trial");
        boss.setTarget(player);
        world.spawnEntity(boss);
        mod.getZombieManager().applyScaling(boss, Math.max(35, mod.getZombieManager().getDay(world)));
        equipBossZombie(boss);
        trial.mobIds().add(boss.getUuid());
        player.sendMessage(TextUtil.literal("&5El Coloso vuelve al campo de batalla."), false);
        return trial.withBossMechanics(boss.getUuid(), 8 * 20, 8 * 20, trial.bossEarlyMinionsTriggered(), trial.bossPhaseTwoTriggered(), trial.bossFinalMinionsTriggered(), true);
    }

    private void keepTrialMobsInsideArena(ServerWorld world, Trial trial) {
        for (UUID uuid : new HashSet<>(trial.mobIds())) {
            Entity entity = world.getEntity(uuid);
            if (entity == null || !entity.isAlive()) continue;
            if (entity.getY() < trial.center().getY() - 4 || !isInsideArena(entity, trial.center(), ModConfig.get().reviveTrial.radioArena + 4)) {
                teleportMobToArena(world, entity, trial.center());
            }
        }
    }

    private void teleportMobToArena(ServerWorld world, Entity entity, BlockPos center) {
        BlockPos safe = findSafeSpawnPos(world, center.add(0, 2, 0), center);
        entity.refreshPositionAndAngles(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, entity.getYaw(), entity.getPitch());
        entity.setVelocity(0.0, 0.0, 0.0);
        if (entity instanceof ZombieEntity zombie) {
            if (world.getClosestPlayer(zombie, 64.0) instanceof ServerPlayerEntity player) {
                zombie.setTarget(player);
            }
        }
    }

    private void spawnBossMinions(ServerWorld world, ServerPlayerEntity player, BlockPos center, Set<UUID> mobIds, int count) {
        for (int i = 0; i < count; i++) {
            ZombieEntity zombie = EntityType.ZOMBIE.create(world);
            if (zombie == null) continue;
            BlockPos preferred = center.add((i % 2 == 0 ? 10 : -10), 2, (i - 1) * 5);
            BlockPos spawn = findSafeSpawnPos(world, preferred, center);
            spawnVisualLightning(world, spawn);
            double angle = Math.atan2(center.getZ() - spawn.getZ(), center.getX() - spawn.getX());
            zombie.refreshPositionAndAngles(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, (float) Math.toDegrees(angle) - 90.0f, 0.0f);
            zombie.addCommandTag("mcextremo_trial");
            zombie.setTarget(player);
            zombie.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            zombie.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.CHAINMAIL_HELMET));
            world.spawnEntity(zombie);
            mod.getZombieManager().applyScaling(zombie, Math.max(35, mod.getZombieManager().getDay(world)));
            mobIds.add(zombie.getUuid());
        }
    }

    private int countAliveMobs(ServerWorld world, Set<UUID> mobIds) {
        int alive = 0;
        for (UUID uuid : mobIds) {
            Entity entity = world.getEntity(uuid);
            if (entity != null && entity.isAlive()) {
                alive++;
            }
        }
        return alive;
    }

    private void cleanupTrial(MinecraftServer server, Trial trial) {
        trial.bossBar().clearPlayers();
        ServerWorld world = getTrialWorld(server);
        discardMobs(world, trial.mobIds());
        cleanupArenaEntities(world, trial.center(), ModConfig.get().reviveTrial.radioArena);
        if (ModConfig.get().reviveTrial.limpiarIslaAlTerminar) {
            clearIsland(world, trial.center(), ModConfig.get().reviveTrial.radioArena);
        }
    }

    private void cleanupArenaEntities(ServerWorld world, BlockPos center, int radius) {
        Box box = arenaBox(center, radius);
        for (Entity entity : world.getEntitiesByClass(Entity.class, box, entity -> shouldCleanupArenaEntity(entity, center, radius))) {
            entity.discard();
        }
    }

    private boolean shouldCleanupArenaEntity(Entity entity, BlockPos center, int radius) {
        if (entity instanceof ServerPlayerEntity) return false;
        if (!isInsideArena(entity, center, radius + 8)) return false;
        return entity instanceof ItemEntity
            || entity instanceof ExperienceOrbEntity
            || entity instanceof EndermanEntity
            || entity instanceof HostileEntity;
    }

    private void removeEndermenInsideArena(ServerWorld world, BlockPos center, int radius) {
        for (EndermanEntity enderman : world.getEntitiesByClass(EndermanEntity.class, arenaBox(center, radius), enderman -> isInsideArena(enderman, center, radius + 8))) {
            enderman.discard();
        }
    }

    private Box arenaBox(BlockPos center, int radius) {
        int clearRadius = radius + 16;
        return new Box(
            center.getX() - clearRadius, center.getY() - 10, center.getZ() - clearRadius,
            center.getX() + clearRadius, center.getY() + 24, center.getZ() + clearRadius
        );
    }

    private boolean isInsideArena(Entity entity, BlockPos center, int radius) {
        double dx = entity.getX() - (center.getX() + 0.5);
        double dz = entity.getZ() - (center.getZ() + 0.5);
        return dx * dx + dz * dz <= radius * radius
            && entity.getY() >= center.getY() - 10
            && entity.getY() <= center.getY() + 24;
    }

    private void discardMobs(ServerWorld world, Set<UUID> mobIds) {
        for (UUID uuid : mobIds) {
            Entity entity = world.getEntity(uuid);
            if (entity != null) {
                entity.discard();
            }
        }
    }

    private void keepPlayerInsideArena(ServerPlayerEntity player, BlockPos center) {
        double dx = player.getX() - (center.getX() + 0.5);
        double dz = player.getZ() - (center.getZ() + 0.5);
        double max = ModConfig.get().reviveTrial.radioArena + 6.0;
        if (dx * dx + dz * dz <= max * max && player.getY() > center.getY() - 8) return;

        player.teleport((ServerWorld) player.getWorld(), center.getX() + 0.5, center.getY() + 3.0, center.getZ() + 0.5, player.getYaw(), player.getPitch());
        player.sendMessage(TextUtil.literal("&cLa isla no te deja escapar."), true);
    }

    private void giveTrialKit(ServerPlayerEntity player) {
        player.getInventory().insertStack(enchantedTrialSword());
        player.getInventory().insertStack(new ItemStack(Items.SHIELD));
        player.equipStack(EquipmentSlot.HEAD, enchantedTrialArmor(Items.IRON_HELMET));
        player.equipStack(EquipmentSlot.CHEST, enchantedTrialArmor(Items.IRON_CHESTPLATE));
        player.equipStack(EquipmentSlot.LEGS, enchantedTrialArmor(Items.IRON_LEGGINGS));
        player.equipStack(EquipmentSlot.FEET, enchantedTrialArmor(Items.IRON_BOOTS));
        player.getInventory().insertStack(new ItemStack(Items.BREAD, 48));
        player.getInventory().insertStack(new ItemStack(Items.COOKED_BEEF, 32));
        player.getInventory().insertStack(new ItemStack(Items.GOLDEN_APPLE, 16));
        ItemStack healing = PotionUtil.setPotion(new ItemStack(Items.SPLASH_POTION, 8), Potions.HEALING);
        player.getInventory().insertStack(healing);
    }

    private ItemStack enchantedTrialArmor(net.minecraft.item.Item item) {
        ItemStack stack = new ItemStack(item);
        Map<net.minecraft.enchantment.Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantments.PROTECTION, 2);
        enchantments.put(Enchantments.UNBREAKING, 1);
        EnchantmentHelper.set(enchantments, stack);
        return stack;
    }

    private ItemStack enchantedTrialSword() {
        ItemStack stack = new ItemStack(Items.IRON_SWORD);
        Map<net.minecraft.enchantment.Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantments.SHARPNESS, 2);
        enchantments.put(Enchantments.UNBREAKING, 1);
        EnchantmentHelper.set(enchantments, stack);
        return stack;
    }

    private void saveAndHideInventory(ServerPlayerEntity player) {
        savedInventories.computeIfAbsent(player.getUuid(), uuid -> {
            if (mod.getDataManager().hasTrialInventory(uuid)) {
                List<ItemStack> persisted = mod.getDataManager().loadTrialInventory(uuid, player.getInventory().size());
                return new SavedInventory(persisted != null ? persisted : List.of());
            }

            List<ItemStack> slots = new ArrayList<>();
            for (int i = 0; i < player.getInventory().size(); i++) {
                slots.add(player.getInventory().getStack(i).copy());
            }
            mod.getDataManager().saveTrialInventory(uuid, slots);
            return new SavedInventory(slots);
        });
        clearPlayerInventory(player);
    }

    private void restoreInventory(ServerPlayerEntity player) {
        SavedInventory saved = savedInventories.remove(player.getUuid());
        List<ItemStack> persisted = mod.getDataManager().loadTrialInventory(player.getUuid(), player.getInventory().size());
        clearPlayerInventory(player);
        List<ItemStack> slots = saved != null && !saved.slots().isEmpty() ? saved.slots() : persisted;
        if (slots == null) {
            mod.getDataManager().clearTrialInventory(player.getUuid());
            return;
        }

        for (int i = 0; i < player.getInventory().size() && i < slots.size(); i++) {
            player.getInventory().setStack(i, slots.get(i).copy());
        }
        player.getInventory().markDirty();
        mod.getDataManager().clearTrialInventory(player.getUuid());
    }

    private void clearPlayerInventory(ServerPlayerEntity player) {
        for (int i = 0; i < player.getInventory().size(); i++) {
            player.getInventory().setStack(i, ItemStack.EMPTY);
        }
        player.getInventory().markDirty();
    }

    private BlockPos getIslandCenter(UUID uuid, ModConfig.ReviveTrial config, int attempt) {
        int slot = Math.floorMod(uuid.hashCode(), 10_000);
        int gridX = slot % 100;
        int gridZ = slot / 100;
        int spacing = config.distanciaEntreIslas;
        int attemptOffset = (attempt - 1) * spacing / 3;
        int x = (int) Math.round(config.x) + (gridX - 50) * spacing + attemptOffset;
        int z = (int) Math.round(config.z) + (gridZ - 50) * spacing + attemptOffset;
        int y = (int) Math.round(config.y);
        return new BlockPos(x, y, z);
    }

    private void generateIsland(ServerWorld world, BlockPos center, int radius) {
        clearIsland(world, center, radius);
        for (int dx = -radius - 3; dx <= radius + 3; dx++) {
            for (int dz = -radius - 3; dz <= radius + 3; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                double edgeNoise = ((Math.abs(dx * 31 + dz * 17) % 9) - 4) * 0.45;
                if (distance > radius + edgeNoise) continue;

                int thickness = Math.max(2, (int) Math.round(6 - distance / 6.0));
                for (int dy = 0; dy < thickness; dy++) {
                    BlockPos pos = center.add(dx, -dy, dz);
                    world.setBlockState(pos, Blocks.END_STONE.getDefaultState());
                }
                if (distance > radius - 4 && distance < radius + 1) {
                    world.setBlockState(center.add(dx, 1, dz), Blocks.END_STONE_BRICKS.getDefaultState());
                }
            }
        }

        world.setBlockState(center.down(4), Blocks.BEDROCK.getDefaultState());
        buildCentralPlatform(world, center);
        buildVoidAltar(world, center);
        buildFortressRing(world, center, radius);
        buildFortressTowers(world, center, radius);

        buildBrokenBridge(world, center, radius, 1, 0);
        buildBrokenBridge(world, center, radius, -1, 0);
        buildBrokenBridge(world, center, radius, 0, 1);
        buildBrokenBridge(world, center, radius, 0, -1);

        buildSidePlatform(world, center.add(radius - 9, 2, 0), 4);
        buildSidePlatform(world, center.add(-radius + 9, 2, 0), 4);
        buildSidePlatform(world, center.add(0, 2, radius - 9), 4);
        buildSidePlatform(world, center.add(0, 2, -radius + 9), 4);

        buildRuinPillar(world, center.add(radius - 8, 1, radius - 8), 6);
        buildRuinPillar(world, center.add(-radius + 8, 1, radius - 8), 6);
        buildRuinPillar(world, center.add(radius - 8, 1, -radius + 8), 5);
        buildRuinPillar(world, center.add(-radius + 8, 1, -radius + 8), 5);

        buildObsidianArch(world, center.add(0, 1, -radius + 8), true);
        buildObsidianArch(world, center.add(0, 1, radius - 8), true);
        buildObsidianArch(world, center.add(-radius + 8, 1, 0), false);
        buildObsidianArch(world, center.add(radius - 8, 1, 0), false);

        buildHealingCover(world, center.add(radius / 2, 1, radius / 4));
        buildHealingCover(world, center.add(-radius / 2, 1, radius / 4));
        buildHealingCover(world, center.add(radius / 3, 1, -radius / 2));
        buildHealingCover(world, center.add(-radius / 3, 1, -radius / 2));
        buildHealingCover(world, center.add(radius / 4, 1, 0));
        buildHealingCover(world, center.add(-radius / 4, 1, 0));
        buildOpenCover(world, center, radius);
        buildVoidDecorations(world, center, radius);

        buildInnerSafetyWall(world, center, radius);
        buildBarrierWall(world, center, radius);
    }

    private void buildCentralPlatform(ServerWorld world, BlockPos center) {
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (Math.abs(dx) == 5 && Math.abs(dz) == 5) continue;
                world.setBlockState(center.add(dx, 1, dz), Blocks.END_STONE_BRICKS.getDefaultState());
                if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) {
                    world.setBlockState(center.add(dx, 2, dz), Blocks.PURPUR_BLOCK.getDefaultState());
                }
            }
        }
        world.setBlockState(center.add(3, 3, 0), Blocks.END_ROD.getDefaultState());
        world.setBlockState(center.add(-3, 3, 0), Blocks.END_ROD.getDefaultState());
        world.setBlockState(center.add(0, 3, 3), Blocks.END_ROD.getDefaultState());
        world.setBlockState(center.add(0, 3, -3), Blocks.END_ROD.getDefaultState());
    }

    private void buildVoidAltar(ServerWorld world, BlockPos center) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                if (dx == 0 && dz == 0) continue;
                BlockPos pos = center.add(dx, 3, dz);
                world.setBlockState(pos, (Math.abs(dx) + Math.abs(dz) <= 1 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).getDefaultState());
            }
        }
        world.setBlockState(center.add(0, 4, 2), Blocks.END_ROD.getDefaultState());
        world.setBlockState(center.add(0, 4, -2), Blocks.END_ROD.getDefaultState());
        world.setBlockState(center.add(2, 4, 0), Blocks.END_ROD.getDefaultState());
        world.setBlockState(center.add(-2, 4, 0), Blocks.END_ROD.getDefaultState());
    }

    private void buildBrokenBridge(ServerWorld world, BlockPos center, int radius, int dirX, int dirZ) {
        int start = 5;
        int end = radius - 10;
        for (int i = start; i <= end; i++) {
            for (int width = -1; width <= 1; width++) {
                int x = dirX * i + (dirZ == 0 ? 0 : width);
                int z = dirZ * i + (dirX == 0 ? 0 : width);
                BlockPos pos = center.add(x, 2, z);
                world.setBlockState(pos, (i % 6 == 0 && width != 0 ? Blocks.PURPUR_BLOCK : Blocks.END_STONE_BRICKS).getDefaultState());
                if (width != 0 && i % 5 == 0) {
                    world.setBlockState(pos.up(), Blocks.PURPUR_SLAB.getDefaultState());
                }
            }
        }
    }

    private void buildFortressRing(ServerWorld world, BlockPos center, int radius) {
        int inner = radius - 13;
        int outer = radius - 6;
        for (int dx = -outer; dx <= outer; dx++) {
            for (int dz = -outer; dz <= outer; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < inner || distance > outer) continue;
                BlockPos pos = center.add(dx, 2, dz);
                world.setBlockState(pos, (Math.abs(dx + dz) % 5 == 0 ? Blocks.PURPUR_BLOCK : Blocks.END_STONE_BRICKS).getDefaultState());
                if (distance > outer - 1.5 && Math.abs(dx + dz) % 6 == 0) {
                    world.setBlockState(pos.up(), Blocks.OBSIDIAN.getDefaultState());
                }
            }
        }

        buildWideRamp(world, center, radius, 1, 0);
        buildWideRamp(world, center, radius, -1, 0);
        buildWideRamp(world, center, radius, 0, 1);
        buildWideRamp(world, center, radius, 0, -1);
    }

    private void buildFortressTowers(ServerWorld world, BlockPos center, int radius) {
        int offset = radius - 12;
        buildOpenTower(world, center.add(offset, 2, offset), 4);
        buildOpenTower(world, center.add(-offset, 2, offset), 4);
        buildOpenTower(world, center.add(offset, 2, -offset), 4);
        buildOpenTower(world, center.add(-offset, 2, -offset), 4);
    }

    private void buildOpenTower(ServerWorld world, BlockPos base, int halfSize) {
        for (int dx = -halfSize; dx <= halfSize; dx++) {
            for (int dz = -halfSize; dz <= halfSize; dz++) {
                if (Math.abs(dx) == halfSize && Math.abs(dz) == halfSize) continue;
                BlockPos floor = base.add(dx, 0, dz);
                world.setBlockState(floor, Blocks.END_STONE_BRICKS.getDefaultState());
                if ((Math.abs(dx) == halfSize || Math.abs(dz) == halfSize) && Math.abs(dx + dz) % 3 == 0) {
                    world.setBlockState(floor.up(), Blocks.PURPUR_SLAB.getDefaultState());
                }
            }
        }

        int[][] pillars = {
            {-halfSize, -halfSize}, {-halfSize, halfSize}, {halfSize, -halfSize}, {halfSize, halfSize}
        };
        for (int[] pillar : pillars) {
            for (int y = 1; y <= 5; y++) {
                world.setBlockState(base.add(pillar[0], y, pillar[1]), (y == 5 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).getDefaultState());
            }
            world.setBlockState(base.add(pillar[0], 6, pillar[1]), Blocks.END_ROD.getDefaultState());
        }
    }

    private void buildWideRamp(ServerWorld world, BlockPos center, int radius, int dirX, int dirZ) {
        int start = 7;
        int end = radius - 8;
        for (int i = start; i <= end; i++) {
            for (int width = -2; width <= 2; width++) {
                int x = dirX * i + (dirZ == 0 ? 0 : width);
                int z = dirZ * i + (dirX == 0 ? 0 : width);
                world.setBlockState(center.add(x, 2, z), Blocks.END_STONE_BRICKS.getDefaultState());
            }
        }
    }

    private void buildSidePlatform(ServerWorld world, BlockPos base, int halfSize) {
        for (int dx = -halfSize; dx <= halfSize; dx++) {
            for (int dz = -halfSize; dz <= halfSize; dz++) {
                if (Math.abs(dx) == halfSize && Math.abs(dz) == halfSize) continue;
                BlockPos pos = base.add(dx, 0, dz);
                world.setBlockState(pos, Blocks.END_STONE_BRICKS.getDefaultState());
                if ((Math.abs(dx) == halfSize || Math.abs(dz) == halfSize) && Math.abs(dx + dz) % 4 == 0) {
                    world.setBlockState(pos.up(), Blocks.PURPUR_BLOCK.getDefaultState());
                }
            }
        }
        world.setBlockState(base.up(), Blocks.END_ROD.getDefaultState());
    }

    private void buildRuinPillar(ServerWorld world, BlockPos base, int height) {
        for (int y = 0; y < height; y++) {
            world.setBlockState(base.up(y), Blocks.OBSIDIAN.getDefaultState());
            if (y < height - 1) {
                world.setBlockState(base.add(1, y, 0), Blocks.PURPUR_BLOCK.getDefaultState());
                if (y % 2 == 0) {
                    world.setBlockState(base.add(0, y, 1), Blocks.END_STONE_BRICKS.getDefaultState());
                }
            }
        }
        world.setBlockState(base.up(height), Blocks.CRYING_OBSIDIAN.getDefaultState());
        world.setBlockState(base.up(height + 1), Blocks.END_ROD.getDefaultState());
        world.setBlockState(base.add(-1, 0, 0), Blocks.END_STONE_BRICKS.getDefaultState());
        world.setBlockState(base.add(-1, 1, 0), Blocks.END_STONE_BRICKS.getDefaultState());
        world.setBlockState(base.add(-2, 1, 0), Blocks.END_STONE_BRICKS.getDefaultState());
        world.setBlockState(base.add(-2, 2, 0), Blocks.END_STONE_BRICKS.getDefaultState());
    }

    private void buildObsidianArch(ServerWorld world, BlockPos base, boolean eastWest) {
        for (int side = -2; side <= 2; side += 4) {
            for (int y = 0; y <= 4; y++) {
                BlockPos pos = eastWest ? base.add(side, y, 0) : base.add(0, y, side);
                world.setBlockState(pos, Blocks.OBSIDIAN.getDefaultState());
            }
        }
        for (int i = -2; i <= 2; i++) {
            BlockPos pos = eastWest ? base.add(i, 4, 0) : base.add(0, 4, i);
            world.setBlockState(pos, i == 0 ? Blocks.CRYING_OBSIDIAN.getDefaultState() : Blocks.OBSIDIAN.getDefaultState());
        }
        world.setBlockState(base.up(5), Blocks.END_ROD.getDefaultState());
    }

    private void buildHealingCover(ServerWorld world, BlockPos base) {
        world.setBlockState(base, Blocks.PURPUR_BLOCK.getDefaultState());
        world.setBlockState(base.up(), Blocks.PURPUR_SLAB.getDefaultState());
        world.setBlockState(base.east(), Blocks.END_STONE_BRICKS.getDefaultState());
        world.setBlockState(base.west(), Blocks.END_STONE_BRICKS.getDefaultState());
        world.setBlockState(base.north(), Blocks.PURPUR_SLAB.getDefaultState());
        world.setBlockState(base.south(), Blocks.PURPUR_SLAB.getDefaultState());
    }

    private void buildOpenCover(ServerWorld world, BlockPos center, int radius) {
        int[][] anchors = {
            {radius / 2, radius / 2}, {-radius / 2, radius / 2},
            {radius / 2, -radius / 2}, {-radius / 2, -radius / 2},
            {radius / 2, -radius / 4}, {-radius / 2, radius / 4}
        };
        for (int[] anchor : anchors) {
            buildLowBarricade(world, center.add(anchor[0], 2, anchor[1]));
        }
    }

    private void buildLowBarricade(ServerWorld world, BlockPos base) {
        for (int i = -2; i <= 2; i++) {
            BlockPos pos = base.add(i, 0, 0);
            world.setBlockState(pos, Blocks.END_STONE_BRICKS.getDefaultState());
            if (Math.abs(i) % 2 == 0) {
                world.setBlockState(pos.up(), Blocks.PURPUR_SLAB.getDefaultState());
            }
        }
        world.setBlockState(base.add(0, 1, 1), Blocks.PURPUR_SLAB.getDefaultState());
        world.setBlockState(base.add(0, 1, -1), Blocks.PURPUR_SLAB.getDefaultState());
    }

    private void buildVoidDecorations(ServerWorld world, BlockPos center, int radius) {
        int offset = radius - 16;
        buildBeaconSpire(world, center.add(offset, 3, 0));
        buildBeaconSpire(world, center.add(-offset, 3, 0));
        buildBeaconSpire(world, center.add(0, 3, offset));
        buildBeaconSpire(world, center.add(0, 3, -offset));
    }

    private void buildBeaconSpire(ServerWorld world, BlockPos base) {
        world.setBlockState(base, Blocks.OBSIDIAN.getDefaultState());
        world.setBlockState(base.up(), Blocks.CRYING_OBSIDIAN.getDefaultState());
        world.setBlockState(base.up(2), Blocks.END_ROD.getDefaultState());
        world.setBlockState(base.north(), Blocks.PURPUR_SLAB.getDefaultState());
        world.setBlockState(base.south(), Blocks.PURPUR_SLAB.getDefaultState());
        world.setBlockState(base.east(), Blocks.PURPUR_SLAB.getDefaultState());
        world.setBlockState(base.west(), Blocks.PURPUR_SLAB.getDefaultState());
    }

    private void buildBarrierWall(ServerWorld world, BlockPos center, int radius) {
        int barrierRadius = radius + 8;
        for (int dx = -barrierRadius; dx <= barrierRadius; dx++) {
            for (int dz = -barrierRadius; dz <= barrierRadius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < barrierRadius - 1.3 || distance > barrierRadius + 1.3) continue;
                for (int dy = -4; dy <= 20; dy++) {
                    world.setBlockState(center.add(dx, dy, dz), Blocks.BARRIER.getDefaultState());
                }
            }
        }
    }

    private void buildInnerSafetyWall(ServerWorld world, BlockPos center, int radius) {
        int wallRadius = radius - 2;
        for (int dx = -wallRadius; dx <= wallRadius; dx++) {
            for (int dz = -wallRadius; dz <= wallRadius; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < wallRadius - 1.5 || distance > wallRadius + 1.5) continue;
                BlockPos base = center.add(dx, 2, dz);
                world.setBlockState(base, Blocks.OBSIDIAN.getDefaultState());
                world.setBlockState(base.up(), Blocks.END_STONE_BRICKS.getDefaultState());
            }
        }
    }

    private void clearIsland(ServerWorld world, BlockPos center, int radius) {
        int clearRadius = radius + 16;
        for (int dx = -clearRadius; dx <= clearRadius; dx++) {
            for (int dz = -clearRadius; dz <= clearRadius; dz++) {
                for (int dy = -10; dy <= 24; dy++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (!world.getBlockState(pos).isAir()) {
                        world.setBlockState(pos, Blocks.AIR.getDefaultState());
                    }
                }
            }
        }
    }

    private int getAttemptFromState(UUID uuid) {
        return STATE_RETRY.equals(mod.getDataManager().getTrialState(uuid)) ? 2 : 1;
    }

    private ServerWorld getTrialWorld(MinecraftServer server) {
        String configured = ModConfig.get().reviveTrial.world;
        if ("nether".equalsIgnoreCase(configured) || "minecraft:the_nether".equalsIgnoreCase(configured)) {
            ServerWorld nether = server.getWorld(World.NETHER);
            return nether != null ? nether : server.getOverworld();
        }
        if ("end".equalsIgnoreCase(configured) || "minecraft:the_end".equalsIgnoreCase(configured)) {
            ServerWorld end = server.getWorld(World.END);
            return end != null ? end : server.getOverworld();
        }
        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey().getValue().toString().equals(configured)) return world;
        }
        ServerWorld end = server.getWorld(World.END);
        return end != null ? end : server.getOverworld();
    }
}
