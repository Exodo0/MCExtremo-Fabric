package com.egologic.mcextremo.manager;

import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

enum EventTrialPhase {
    GENERATING,
    INTRO,
    PREPARATION,
    COMBAT,
    VICTORY
}

class EventTrial {
    ServerBossBar bossBar;
    BlockPos center;
    EventArenaBuildTask arenaBuild;
    Set<UUID> pendingPlayers = new HashSet<>();
    Set<UUID> participants = new HashSet<>();
    Set<UUID> mobs = new HashSet<>();
    Map<UUID, BlockPos> landingPositions = new HashMap<>();
    Map<UUID, UUID> introAvatars = new HashMap<>();
    Set<UUID> landedPlayers = new HashSet<>();
    Set<UUID> impactPrimedPlayers = new HashSet<>();
    UUID cameraEntityId;
    EventTrialPhase phase = EventTrialPhase.PREPARATION;
    int initialPlayers;
    int wave;
    int actionCooldown;
    int introTicks;
    int tokens;
    boolean victoryDelay;
    UUID bossId;
    int bossMinionConsumes;
    int bossCooldown;
    int fangsCooldown;
    int fireCooldown;
    int comboCooldown;
    int auraTicks;
    boolean bossPhaseTwo;
    boolean bossPhaseThree;
    boolean bossPhaseFour;
    int veilTransitionTick;
    boolean veilTransitionDone;
    boolean furyTransitionActive;
    int furyTransitionTick;
    BlockPos furyLanding;
    UUID veilGuardianId;
    int veilRegenCooldown;
    int skullCooldown;
    int skullBurstDelay;
    int skullsInBurst;
    Map<UUID, Long> bossSkulls = new HashMap<>();
    int bossDeathTick = -1;
    UUID dyingBossId;
    boolean regearChestsSpawned;
    Set<BlockPos> regearChests = new HashSet<>();
    Set<UUID> regearChestMarkers = new HashSet<>();
}
