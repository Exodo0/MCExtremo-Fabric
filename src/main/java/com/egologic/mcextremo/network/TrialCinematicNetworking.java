package com.egologic.mcextremo.network;

import com.egologic.mcextremo.MCExtremo;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

public final class TrialCinematicNetworking {
    public static final Identifier BOSS_INTRO = new Identifier(MCExtremo.MOD_ID, "trial_boss_intro");
    public static final Identifier EVENT_INTRO = new Identifier(MCExtremo.MOD_ID, "event_trial_intro");
    public static final Identifier STOP = new Identifier(MCExtremo.MOD_ID, "trial_cinematic_stop");

    private TrialCinematicNetworking() {
    }

    public static void sendBossIntro(ServerPlayerEntity player, int entityId, Vec3d fallbackPos, int durationTicks, String title) {
        if (!ServerPlayNetworking.canSend(player, BOSS_INTRO)) return;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        buf.writeDouble(fallbackPos.x);
        buf.writeDouble(fallbackPos.y);
        buf.writeDouble(fallbackPos.z);
        buf.writeInt(durationTicks);
        buf.writeString(title);
        ServerPlayNetworking.send(player, BOSS_INTRO, buf);
    }

    public static void sendEventIntro(ServerPlayerEntity player, Vec3d center, int durationTicks) {
        if (!ServerPlayNetworking.canSend(player, EVENT_INTRO)) return;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeDouble(center.x);
        buf.writeDouble(center.y);
        buf.writeDouble(center.z);
        buf.writeInt(durationTicks);
        ServerPlayNetworking.send(player, EVENT_INTRO, buf);
    }

    public static void sendStop(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, STOP)) return;
        ServerPlayNetworking.send(player, STOP, PacketByteBufs.empty());
    }
}
