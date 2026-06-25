package com.egologic.mcextremo.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class TrialCinematicController {
    private static final float CAMERA_STRENGTH = 0.34f;
    private static final float MAX_PITCH = 75.0f;
    private static final int FADE_TICKS = 16;

    private static BossIntro activeIntro;
    private static EventIntro activeEventIntro;
    private static float guidedYaw;
    private static float guidedPitch;

    private record BossIntro(int entityId, Vec3d fallbackPos, int totalTicks, int ticksRemaining, String title) {
        BossIntro tick() {
            return new BossIntro(entityId, fallbackPos, totalTicks, ticksRemaining - 1, title);
        }
    }

    private record EventIntro(Vec3d center, int totalTicks, int ticksRemaining) {
        EventIntro tick() {
            return new EventIntro(center, totalTicks, ticksRemaining - 1);
        }
    }

    private TrialCinematicController() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(TrialCinematicController::tick);
        HudRenderCallback.EVENT.register(TrialCinematicController::renderHud);
    }

    public static void startBossIntro(int entityId, Vec3d fallbackPos, int durationTicks, String title) {
        int ticks = Math.max(1, durationTicks);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            guidedYaw = client.player.getYaw();
            guidedPitch = client.player.getPitch();
        }
        activeIntro = new BossIntro(entityId, fallbackPos, ticks, ticks, title == null || title.isBlank() ? "El Coloso desciende" : title);
    }

    public static void startEventIntro(Vec3d center, int durationTicks) {
        int ticks = Math.max(40, durationTicks);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            guidedYaw = client.player.getYaw();
            guidedPitch = client.player.getPitch();
        }
        activeEventIntro = new EventIntro(center, ticks, ticks);
    }

    public static void stopAll() {
        activeIntro = null;
        activeEventIntro = null;
    }

    private static void tick(MinecraftClient client) {
        if (activeEventIntro != null) {
            if (client.player == null || activeEventIntro.ticksRemaining() <= 0) {
                activeEventIntro = null;
            } else {
                lookAtEventSky(client, activeEventIntro);
                activeEventIntro = activeEventIntro.tick();
            }
        }
        if (activeIntro == null) return;
        if (client.player == null || client.world == null) {
            activeIntro = null;
            return;
        }

        if (activeIntro.ticksRemaining() <= 0) {
            activeIntro = null;
            return;
        }

        Vec3d target = getTarget(client, activeIntro);
        lookAt(client, target, activeIntro);
        activeIntro = activeIntro.tick();
    }

    private static Vec3d getTarget(MinecraftClient client, BossIntro intro) {
        Entity entity = client.world.getEntityById(intro.entityId());
        if (entity != null && entity.isAlive()) {
            return entity.getPos().add(0.0, entity.getHeight() * 0.75, 0.0);
        }
        return intro.fallbackPos();
    }

    private static void lookAt(MinecraftClient client, Vec3d target, BossIntro intro) {
        Vec3d eye = client.player.getEyePos();
        Vec3d delta = target.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.001) return;

        float targetYaw = (float) (MathHelper.atan2(delta.z, delta.x) * 180.0F / Math.PI) - 90.0F;
        float targetPitch = (float) (-(MathHelper.atan2(delta.y, horizontal) * 180.0F / Math.PI));
        targetPitch = MathHelper.clamp(targetPitch, -MAX_PITCH, MAX_PITCH);

        float progress = 1.0f - (intro.ticksRemaining() / (float) intro.totalTicks());
        float strength = CAMERA_STRENGTH + Math.min(0.42f, progress * 0.42f);
        guidedYaw += MathHelper.wrapDegrees(targetYaw - guidedYaw) * strength;
        guidedPitch += (targetPitch - guidedPitch) * strength;
        guidedPitch = MathHelper.clamp(guidedPitch, -MAX_PITCH, MAX_PITCH);

        client.player.prevYaw = guidedYaw;
        client.player.prevPitch = guidedPitch;
        client.player.setYaw(guidedYaw);
        client.player.setPitch(guidedPitch);
        client.player.setHeadYaw(guidedYaw);
        client.player.bodyYaw = guidedYaw;
    }

    private static void lookAtEventSky(MinecraftClient client, EventIntro intro) {
        Vec3d target = intro.center().add(0.0, 10.0, 0.0);
        Vec3d eye = client.player.getEyePos();
        Vec3d delta = target.subtract(eye);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.001) return;

        float targetYaw = (float) (MathHelper.atan2(delta.z, delta.x) * 180.0F / Math.PI) - 90.0F;
        float targetPitch = (float) (-(MathHelper.atan2(delta.y, horizontal) * 180.0F / Math.PI));
        targetPitch = MathHelper.clamp(targetPitch, -34.0f, 8.0f);

        guidedYaw += MathHelper.wrapDegrees(targetYaw - guidedYaw) * 0.45f;
        guidedPitch += (targetPitch - guidedPitch) * 0.38f;
        guidedPitch = MathHelper.clamp(guidedPitch, -34.0f, 8.0f);

        client.player.prevYaw = guidedYaw;
        client.player.prevPitch = guidedPitch;
        client.player.setYaw(guidedYaw);
        client.player.setPitch(guidedPitch);
        client.player.setHeadYaw(guidedYaw);
        client.player.bodyYaw = guidedYaw;
    }

    private static void renderHud(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) return;
        if (activeEventIntro != null) {
            renderEventIntro(context, client, tickDelta);
        }
        if (activeIntro == null) return;

        float alpha = getFadeAlpha(activeIntro, tickDelta);
        int darkAlpha = MathHelper.clamp((int) (alpha * 150.0f), 0, 150);
        int purpleAlpha = MathHelper.clamp((int) (alpha * 58.0f), 0, 58);
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        context.fill(0, 0, width, height, darkAlpha << 24);
        context.fill(0, 0, width, height / 5, (purpleAlpha << 24) | 0x2D0A4D);
        context.fill(0, height - height / 5, width, height, (purpleAlpha << 24) | 0x2D0A4D);

        if (alpha > 0.25f) {
            int textAlpha = MathHelper.clamp((int) (alpha * 255.0f), 0, 255);
            int color = (textAlpha << 24) | 0xD9B5FF;
            context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(activeIntro.title()), width / 2, height / 2 + 34, color);
        }
    }

    private static void renderEventIntro(DrawContext context, MinecraftClient client, float tickDelta) {
        float alpha = getEventFadeAlpha(activeEventIntro, tickDelta);
        int darkAlpha = MathHelper.clamp((int) (alpha * 118.0f), 0, 118);
        int violetAlpha = MathHelper.clamp((int) (alpha * 64.0f), 0, 64);
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        context.fill(0, 0, width, height, darkAlpha << 24);
        context.fill(0, 0, width, height / 6, (violetAlpha << 24) | 0x30104D);
        context.fill(0, height - height / 6, width, height, (violetAlpha << 24) | 0x30104D);

        if (alpha > 0.18f) {
            int textAlpha = MathHelper.clamp((int) (alpha * 255.0f), 0, 255);
            int titleColor = (textAlpha << 24) | 0xF5D67B;
            int subColor = (textAlpha << 24) | 0xD9B5FF;
            context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("Entrando al Event Trial"), width / 2, height / 2 + 22, titleColor);
            context.drawCenteredTextWithShadow(client.textRenderer, Text.literal("La arena te reclama"), width / 2, height / 2 + 36, subColor);
        }
    }

    private static float getFadeAlpha(BossIntro intro, float tickDelta) {
        float elapsed = intro.totalTicks() - intro.ticksRemaining() + tickDelta;
        float fadeIn = MathHelper.clamp(elapsed / FADE_TICKS, 0.0f, 1.0f);
        float fadeOut = MathHelper.clamp((intro.ticksRemaining() - tickDelta) / FADE_TICKS, 0.0f, 1.0f);
        return Math.min(fadeIn, fadeOut);
    }

    private static float getEventFadeAlpha(EventIntro intro, float tickDelta) {
        float elapsed = intro.totalTicks() - intro.ticksRemaining() + tickDelta;
        float fadeIn = MathHelper.clamp(elapsed / FADE_TICKS, 0.0f, 1.0f);
        float fadeOut = MathHelper.clamp((intro.ticksRemaining() - tickDelta) / FADE_TICKS, 0.0f, 1.0f);
        return Math.min(fadeIn, fadeOut);
    }
}
