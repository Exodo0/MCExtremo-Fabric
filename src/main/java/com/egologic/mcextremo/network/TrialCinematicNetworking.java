package com.egologic.mcextremo.network;

import com.egologic.mcextremo.MCExtremo;
import com.egologic.mcextremo.config.ModConfig;
import com.egologic.mcextremo.entity.boss.TrialBossAnimations;
import com.egologic.mcextremo.visual.TrialVisualEvent;
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
    public static final Identifier BOSS_ANIMATION = new Identifier(MCExtremo.MOD_ID, "boss_animation");
    public static final Identifier VISUAL_EVENT = new Identifier(MCExtremo.MOD_ID, "trial_visual_event");

    private TrialCinematicNetworking() {
    }

    public static void sendBossIntro(ServerPlayerEntity player, int entityId, Vec3d fallbackPos, int durationTicks, String title) {
        if (!ModConfig.get().visuals.enableBossIntroAnimations || !ServerPlayNetworking.canSend(player, BOSS_INTRO)) return;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        buf.writeDouble(fallbackPos.x);
        buf.writeDouble(fallbackPos.y);
        buf.writeDouble(fallbackPos.z);
        buf.writeInt(durationTicks);
        buf.writeString(title);
        ServerPlayNetworking.send(player, BOSS_INTRO, buf);
    }

    public static void sendEventIntro(ServerPlayerEntity player, Vec3d center, int durationTicks, String title, String subtitle) {
        if (!ModConfig.get().visuals.enableTrialScreenEffects || !ServerPlayNetworking.canSend(player, EVENT_INTRO)) return;

        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeDouble(center.x);
        buf.writeDouble(center.y);
        buf.writeDouble(center.z);
        buf.writeInt(durationTicks);
        buf.writeString(title);
        buf.writeString(subtitle);
        ServerPlayNetworking.send(player, EVENT_INTRO, buf);
    }

    public static void sendStop(ServerPlayerEntity player) {
        if (!ServerPlayNetworking.canSend(player, STOP)) return;
        ServerPlayNetworking.send(player, STOP, PacketByteBufs.empty());
    }

    public static void sendBossAnimation(ServerPlayerEntity player, int entityId, String animation, int durationTicks) {
        if (!ServerPlayNetworking.canSend(player, BOSS_ANIMATION)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(entityId);
        buf.writeString(animation == null || animation.isBlank() ? TrialBossAnimations.IDLE : animation);
        buf.writeInt(Math.max(1, durationTicks));
        ServerPlayNetworking.send(player, BOSS_ANIMATION, buf);
    }

    public static void sendVisualEvent(ServerPlayerEntity player, TrialVisualEvent event, Vec3d pos, int durationTicks, String title, String subtitle) {
        ModConfig.Visuals visuals = ModConfig.get().visuals;
        if (!visuals.enableTrialScreenEffects && event != TrialVisualEvent.HORDE_START) return;
        if (event == TrialVisualEvent.HORDE_START && !visuals.enableHordeVisualEffects) return;
        if (!ServerPlayNetworking.canSend(player, VISUAL_EVENT)) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeEnumConstant(event);
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeInt(Math.max(1, durationTicks));
        buf.writeString(title == null ? "" : title);
        buf.writeString(subtitle == null ? "" : subtitle);
        ServerPlayNetworking.send(player, VISUAL_EVENT, buf);
    }
}
