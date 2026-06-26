package com.egologic.mcextremo.client.render;

import com.egologic.mcextremo.entity.boss.TrialGuardianSpiderEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TrialGuardianSpiderRenderer extends GeoEntityRenderer<TrialGuardianSpiderEntity> {
    public TrialGuardianSpiderRenderer(EntityRendererFactory.Context renderManager) {
        super(renderManager, new TrialGuardianSpiderModel());
        this.shadowRadius = 0.8f;
    }
}
