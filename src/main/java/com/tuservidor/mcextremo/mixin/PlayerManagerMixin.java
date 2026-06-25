package com.tuservidor.mcextremo.mixin;

import com.mojang.authlib.GameProfile;
import com.tuservidor.mcextremo.MCExtremo;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.SocketAddress;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Inject(method = "checkCanJoin", at = @At("HEAD"), cancellable = true)
    private void mcextremo$checkCanJoin(SocketAddress address, GameProfile profile, CallbackInfoReturnable<Text> cir) {
        MCExtremo mod = MCExtremo.getInstance();
        if (mod == null || mod.getLivesManager() == null || profile == null || profile.getId() == null) {
            return;
        }

        if (mod.getLivesManager().isEliminated(profile.getId())
            && !mod.getReviveTrialManager().canResumeTrial(profile.getId())) {
            cir.setReturnValue(mod.getLivesManager().getEliminationText(profile.getName()));
        }
    }
}
