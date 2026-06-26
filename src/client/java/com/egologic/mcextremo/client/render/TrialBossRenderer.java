package com.egologic.mcextremo.client.render;

import com.egologic.mcextremo.entity.boss.TrialBossEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TrialBossRenderer extends GeoEntityRenderer<TrialBossEntity> {
    public TrialBossRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new TrialBossModel());
        this.shadowRadius = 0.75f;
    }
}
