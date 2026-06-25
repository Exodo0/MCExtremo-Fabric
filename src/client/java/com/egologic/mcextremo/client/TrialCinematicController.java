package com.egologic.mcextremo.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class TrialCinematicController {
    private static final int FADE_TICKS = 16;

    private static BossIntro activeIntro;
    private static EventIntro activeEventIntro;

    private record BossIntro(int entityId, Vec3d fallbackPos, int totalTicks, int ticksRemaining, String title) {
        BossIntro tick() {
            return new BossIntro(entityId, fallbackPos, totalTicks, ticksRemaining - 1, title);
        }
    }

    private record EventIntro(Vec3d center, int totalTicks, int ticksRemaining, String title, String subtitle) {
        EventIntro tick() {
            return new EventIntro(center, totalTicks, ticksRemaining - 1, title, subtitle);
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
        activeIntro = new BossIntro(entityId, fallbackPos, ticks, ticks, title == null || title.isBlank() ? "El Coloso desciende" : title);
    }

    public static void startEventIntro(Vec3d center, int durationTicks, String title, String subtitle) {
        int ticks = Math.max(40, durationTicks);
        String safeTitle = title == null || title.isBlank() ? "Entrando al Event Trial" : title;
        String safeSubtitle = subtitle == null || subtitle.isBlank() ? "La arena te reclama" : subtitle;
        activeEventIntro = new EventIntro(center, ticks, ticks, safeTitle, safeSubtitle);
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

        activeIntro = activeIntro.tick();
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
            context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(activeEventIntro.title()), width / 2, height / 2 + 22, titleColor);
            context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(activeEventIntro.subtitle()), width / 2, height / 2 + 36, subColor);
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
