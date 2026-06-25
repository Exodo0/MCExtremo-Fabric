package com.tuservidor.mcextremo.mixin;

import com.tuservidor.mcextremo.MCExtremo;
import com.tuservidor.mcextremo.config.ModConfig;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void mcextremo$onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayerEntity player = (ServerPlayerEntity) (Object) this;
        MCExtremo mod = MCExtremo.getInstance();

        if (mod == null || mod.getPvpScheduler() == null) return;

        if (source.getAttacker() instanceof ServerPlayerEntity) {
            if (!mod.getPvpScheduler().isPvpEnabled()) {
                cir.setReturnValue(false);
            }
        }
    }
}
