package com.egologic.mcextremo.client.render;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.entity.boss.TrialBossEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class TrialBossModel extends GeoModel<TrialBossEntity> {
    private static final Identifier MODEL = new Identifier(MCExtremo.MOD_ID, "geo/trial_boss.geo.json");
    private static final Identifier TEXTURE = new Identifier(MCExtremo.MOD_ID, "textures/entity/trial_boss.png");
    private static final Identifier ANIMATIONS = new Identifier(MCExtremo.MOD_ID, "animations/trial_boss.animation.json");

    @Override
    public Identifier getModelResource(TrialBossEntity animatable) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(TrialBossEntity animatable) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(TrialBossEntity animatable) {
        return ANIMATIONS;
    }
}
