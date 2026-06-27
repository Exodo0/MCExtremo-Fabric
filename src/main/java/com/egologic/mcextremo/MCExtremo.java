package com.egologic.mcextremo;

import com.egologic.mcextremo.command.ModCommands;
import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.entity.ModEntities;
import com.egologic.mcextremo.item.ModItems;
import com.egologic.mcextremo.listener.DeathListener;
import com.egologic.mcextremo.listener.JoinListener;
import com.egologic.mcextremo.listener.ClientRequirementListener;
import com.egologic.mcextremo.listener.SkillTreeListener;
import com.egologic.mcextremo.listener.ZombieGoalListener;
import com.egologic.mcextremo.manager.*;
import com.egologic.mcextremo.network.SkillTreeNetworking;
import com.egologic.mcextremo.network.VersionNetworking;
import com.egologic.mcextremo.skilltree.SkillPassiveHandler;
import com.egologic.mcextremo.skilltree.SkillTreeManager;
import com.egologic.mcextremo.util.UpdateChecker;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.GeckoLib;

public class MCExtremo implements ModInitializer {
    public static final String MOD_ID = "mcextremo";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static MCExtremo instance;
    private LivesManager livesManager;
    private PvPScheduler pvpScheduler;
    private ScoreboardManager scoreboardManager;
    private HardcoreManager hardcoreManager;
    private ZombieManager zombieManager;
    private DataManager dataManager;
    private ZombieHordeManager zombieHordeManager;
    private SkillTreeManager skillTreeManager;
    private RewardManager rewardManager;
    private ArmorUpgradeManager armorUpgradeManager;
    private ReviveTrialManager reviveTrialManager;
    private EventTrialManager eventTrialManager;
    private DailyMissionManager dailyMissionManager;
    private WorldEventManager worldEventManager;
    private ControlPointManager controlPointManager;
    private UpdateChecker updateChecker;

    @Override
    public void onInitialize() {
        instance = this;

        GeckoLib.initialize();
        ModConfig.register();
        ModEntities.register();
        ModItems.register();

        dataManager = new DataManager(this);
        livesManager = new LivesManager(this);
        pvpScheduler = new PvPScheduler(this);
        scoreboardManager = new ScoreboardManager(this);
        hardcoreManager = new HardcoreManager(this);
        zombieManager = new ZombieManager(this);
        zombieHordeManager = new ZombieHordeManager(this);
        rewardManager = new RewardManager(this);
        armorUpgradeManager = new ArmorUpgradeManager(this);
        reviveTrialManager = new ReviveTrialManager(this);
        eventTrialManager = new EventTrialManager(this);
        dailyMissionManager = new DailyMissionManager(this);
        worldEventManager = new WorldEventManager(this);
        controlPointManager = new ControlPointManager(this);
        updateChecker = new UpdateChecker();

        if (ModConfig.get().skillTree.activado) {
            skillTreeManager = new SkillTreeManager(this);
            skillTreeManager.load();
        }

        livesManager.load();
        dataManager.load();
        rewardManager.registerEvents();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!server.isDedicated()) {
                LOGGER.warn("MCExtremo requiere servidor dedicado. La logica del mod queda desactivada en singleplayer.");
                return;
            }
            dataManager.setServer(server);
            hardcoreManager.apply(server);
            if (ModConfig.get().pvpProgramado.activado) {
                pvpScheduler.start(server);
            }
            updateChecker.checkAsync(false).thenAccept(info -> {
                if (info.updateAvailable()) {
                    LOGGER.warn("MCExtremo v" + info.latestVersion() + " disponible. Descarga: " + info.downloadUrl());
                } else if (!info.failed()) {
                    LOGGER.info("MCExtremo actualizado. Version local: " + info.currentVersion());
                }
            });
            LOGGER.info("MCExtremo habilitado. Vidas: " + livesManager.getDefaultLives());
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!server.isDedicated()) return;
            livesManager.tick(server);
            reviveTrialManager.tick(server);
            eventTrialManager.tick(server);
            zombieManager.tick(server.getOverworld());
            pvpScheduler.tick(server);
            scoreboardManager.tick(server);
            zombieHordeManager.tick(server.getOverworld());
            dailyMissionManager.tick(server);
            worldEventManager.tick(server);
            controlPointManager.tick(server);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            livesManager.save();
            dataManager.save();
            if (skillTreeManager != null) {
                skillTreeManager.save();
            }
            UpdateChecker.shutdown();
        });

        registerPvpCancellation();
        SkillPassiveHandler.register();
        dailyMissionManager.registerEvents();
        SkillTreeNetworking.registerServer();
        VersionNetworking.registerServer();

        ClientRequirementListener.register();
        DeathListener.register();
        JoinListener.register();
        ZombieGoalListener.register();
        SkillTreeListener.register();
        ModCommands.register();

        LOGGER.info("MCExtremo initialized!");
    }

    private void registerPvpCancellation() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, amount) -> {
            var attacker = damageSource.getAttacker();
            var source = damageSource.getSource();
            if (eventTrialManager != null
                && eventTrialManager.isProtectedVeilBoss(entity)
                && (attacker instanceof PlayerEntity || source instanceof net.minecraft.entity.projectile.ProjectileEntity)) {
                return false;
            }
            if (entity.getCommandTags().contains(EventTrialManager.MOB_TAG)
                && ((attacker != null && attacker.getCommandTags().contains(EventTrialManager.MOB_TAG))
                    || (source != null && source.getCommandTags().contains(EventTrialManager.MOB_TAG)))) {
                return false;
            }
            if (entity instanceof ServerPlayerEntity victim) {
                if (attacker instanceof ServerPlayerEntity && !pvpScheduler.isPvpEnabled()) {
                    return false;
                }
            }
            return true;
        });
    }

    public static MCExtremo getInstance() {
        return instance;
    }

    public LivesManager getLivesManager() { return livesManager; }
    public PvPScheduler getPvpScheduler() { return pvpScheduler; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public HardcoreManager getHardcoreManager() { return hardcoreManager; }
    public ZombieManager getZombieManager() { return zombieManager; }
    public DataManager getDataManager() { return dataManager; }
    public ZombieHordeManager getZombieHordeManager() { return zombieHordeManager; }
    public SkillTreeManager getSkillTreeManager() { return skillTreeManager; }
    public RewardManager getRewardManager() { return rewardManager; }
    public ArmorUpgradeManager getArmorUpgradeManager() { return armorUpgradeManager; }
    public ReviveTrialManager getReviveTrialManager() { return reviveTrialManager; }
    public EventTrialManager getEventTrialManager() { return eventTrialManager; }
    public DailyMissionManager getDailyMissionManager() { return dailyMissionManager; }
    public WorldEventManager getWorldEventManager() { return worldEventManager; }
    public ControlPointManager getControlPointManager() { return controlPointManager; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
}
