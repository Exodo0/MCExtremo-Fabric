package com.egologic.mcextremo.listener;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

public class SkillTreeListener {

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!ModConfig.get().skillTree.activado) return;

            MCExtremo mod = MCExtremo.getInstance();
            ServerPlayerEntity player = handler.getPlayer();

            mod.getSkillTreeManager().applyAllEffects(player);
        });
    }
}
