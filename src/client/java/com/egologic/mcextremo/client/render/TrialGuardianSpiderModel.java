package com.egologic.mcextremo.client.render;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.entity.boss.TrialGuardianSpiderEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class TrialGuardianSpiderModel extends GeoModel<TrialGuardianSpiderEntity> {
    private static final Identifier MODEL = new Identifier(MCExtremo.MOD_ID, "geo/trial_guardian_spider.geo.json");
    private static final Identifier TEXTURE = new Identifier(MCExtremo.MOD_ID, "textures/entity/trial_guardian_spider.png");
    private static final Identifier ANIMATIONS = new Identifier(MCExtremo.MOD_ID, "animations/trial_guardian_spider.animation.json");

    @Override
    public Identifier getModelResource(TrialGuardianSpiderEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(TrialGuardianSpiderEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(TrialGuardianSpiderEntity animatable) {
        return ANIMATIONS;
    }
}
