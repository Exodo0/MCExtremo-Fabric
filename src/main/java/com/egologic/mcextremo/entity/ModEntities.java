package com.egologic.mcextremo.entity;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.entity.boss.TrialBossEntity;
import com.egologic.mcextremo.entity.boss.TrialGuardianSpiderEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.mob.SpiderEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<TrialBossEntity> TRIAL_BOSS = Registry.register(
        Registries.ENTITY_TYPE,
        new Identifier(MCExtremo.MOD_ID, "trial_boss"),
        FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, TrialBossEntity::new)
            .dimensions(EntityDimensions.fixed(0.9f, 2.35f))
            .trackRangeBlocks(96)
            .trackedUpdateRate(3)
            .forceTrackedVelocityUpdates(true)
            .build()
    );

    public static final EntityType<TrialGuardianSpiderEntity> TRIAL_GUARDIAN_SPIDER = Registry.register(
        Registries.ENTITY_TYPE,
        new Identifier(MCExtremo.MOD_ID, "trial_guardian_spider"),
        FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, TrialGuardianSpiderEntity::new)
            .dimensions(EntityDimensions.fixed(1.6f, 0.9f))
            .trackRangeBlocks(96)
            .trackedUpdateRate(3)
            .forceTrackedVelocityUpdates(true)
            .build()
    );

    private ModEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(TRIAL_BOSS, ZombieEntity.createZombieAttributes());
        FabricDefaultAttributeRegistry.register(TRIAL_GUARDIAN_SPIDER, SpiderEntity.createSpiderAttributes());
        MCExtremo.LOGGER.info("MCExtremo entities registered");
    }
}
