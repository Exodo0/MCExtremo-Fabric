package com.egologic.mcextremo.listener;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.util.TextUtil;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class DeathListener {

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) return true;

            MCExtremo mod = MCExtremo.getInstance();
            if (mod.getEventTrialManager().handleDeath(player)) {
                return false;
            }
            if (mod.getReviveTrialManager().isInTrial(player.getUuid())) {
                mod.getReviveTrialManager().failTrial(player);
                return false;
            }

            int vidas = mod.getLivesManager().quitarVida(player);

            ModConfig config = ModConfig.get();

            String nombre = player.getName().getString();

            if (vidas > 0) {
                String msg = config.mensajes.perdidaVida
                    .replace("{jugador}", nombre)
                    .replace("{vidas}", String.valueOf(vidas));
                broadcastMessage(mod, msg);
            }

            if (vidas <= 0) {
                String msg = config.mensajes.sinVidas.replace("{jugador}", nombre);
                broadcastMessage(mod, msg);

                mod.getLivesManager().eliminar(player);
                return false;
            }

            return true;
        });
    }

    private static void broadcastMessage(MCExtremo mod, String message) {
        Text text = TextUtil.literal(message);
        if (mod.getDataManager().getServer() != null) {
            for (var player : mod.getDataManager().getServer().getPlayerManager().getPlayerList()) {
                player.sendMessage(text, false);
            }
        }
    }
}
