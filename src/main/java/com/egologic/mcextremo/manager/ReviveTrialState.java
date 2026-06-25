package com.egologic.mcextremo.manager;

import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

record Trial(

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
    UUID bossIntroCamera,
    boolean voluntary
) {
    private static final int REVIVE_WAVE_DELAY_TICKS = 4 * 20;
    private static final int REVIVE_BOSS_INTRO_TICKS = 80;
    Trial tick() {
        return new Trial(
            uuid, bossBar, center, attempt, wave,
            Math.max(0, actionCooldown - 1), mobIds, victoryDelay,
            bossId, Math.max(0, bossMechanicCooldown - 1),
            Math.max(0, lightningCooldown - 1),
            bossEarlyMinionsTriggered, bossPhaseTwoTriggered, bossFinalMinionsTriggered, bossRecovered,
            Math.max(0, bossIntroTicks - 1), bossIntroCamera, voluntary
        );
    }

    Trial nextWave(int nextWave, Set<UUID> mobs, UUID nextBossId) {
        int introTicks = nextBossId != null ? REVIVE_BOSS_INTRO_TICKS : 0;
        return new Trial(uuid, bossBar, center, attempt, nextWave, REVIVE_WAVE_DELAY_TICKS, mobs, false, nextBossId, 12 * 20, 14 * 20, false, false, false, false, introTicks, null, voluntary);
    }

    Trial startVictoryDelay(int cooldown) {
        return new Trial(uuid, bossBar, center, attempt, wave, cooldown, new HashSet<>(), true, null, 0, 0, bossEarlyMinionsTriggered, bossPhaseTwoTriggered, bossFinalMinionsTriggered, bossRecovered, 0, null, voluntary);
    }

    Trial withBossMechanics(UUID currentBossId, int cooldown, int currentLightningCooldown, boolean earlyMinionsTriggered, boolean phaseTwoTriggered, boolean finalMinionsTriggered, boolean recovered) {
        return new Trial(uuid, bossBar, center, attempt, wave, actionCooldown, mobIds, victoryDelay, currentBossId, cooldown, currentLightningCooldown, earlyMinionsTriggered, phaseTwoTriggered, finalMinionsTriggered, recovered, bossIntroTicks, bossIntroCamera, voluntary);
    }

    Trial withBossIntroTicks(int ticks) {
        return new Trial(uuid, bossBar, center, attempt, wave, actionCooldown, mobIds, victoryDelay, bossId, bossMechanicCooldown, lightningCooldown, bossEarlyMinionsTriggered, bossPhaseTwoTriggered, bossFinalMinionsTriggered, bossRecovered, ticks, bossIntroCamera, voluntary);
    }

    Trial withBossCamera(UUID cameraId) {
        return new Trial(uuid, bossBar, center, attempt, wave, actionCooldown, mobIds, victoryDelay, bossId, bossMechanicCooldown, lightningCooldown, bossEarlyMinionsTriggered, bossPhaseTwoTriggered, bossFinalMinionsTriggered, bossRecovered, bossIntroTicks, cameraId, voluntary);
    }
}

record SavedInventory(List<ItemStack> slots) {
}

record WaveSpawnResult(Set<UUID> mobs, UUID bossCameraId) {
}
