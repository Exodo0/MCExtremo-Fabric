package com.egologic.mcextremo.mixin;

import net.minecraft.entity.attribute.AttributeContainer;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.ZombieEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ZombieEntity.class)
public class ZombieAttributesMixin {

    @Inject(method = "createZombieAttributes", at = @At("RETURN"))
    private static void mcextremo$modifyAttributes(CallbackInfoReturnable<DefaultAttributeContainer.Builder> cir) {
        DefaultAttributeContainer.Builder builder = cir.getReturnValue();

        builder.add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0D);
        builder.add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0D);
        builder.add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23D);
        builder.add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0D);
        builder.add(EntityAttributes.GENERIC_ARMOR, 0.0D);
    }
}
