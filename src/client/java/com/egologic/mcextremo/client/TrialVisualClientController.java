package com.egologic.mcextremo.client;

import com.egologic.mcextremo.visual.TrialVisualEvent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class TrialVisualClientController {
    private static final int FADE_TICKS = 14;
    private static ActiveVisual activeVisual;

    private record ActiveVisual(TrialVisualEvent event, Vec3d pos, int totalTicks, int ticksRemaining, String title, String subtitle) {
        ActiveVisual tick() {
            return new ActiveVisual(event, pos, totalTicks, ticksRemaining - 1, title, subtitle);
        }
    }

    private TrialVisualClientController() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(TrialVisualClientController::tick);
        HudRenderCallback.EVENT.register(TrialVisualClientController::renderHud);
    }

    public static void start(TrialVisualEvent event, Vec3d pos, int durationTicks, String title, String subtitle) {
        int ticks = Math.max(1, durationTicks);
        activeVisual = new ActiveVisual(event, pos, ticks, ticks, title == null ? "" : title, subtitle == null ? "" : subtitle);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            switch (event) {
                case START_TRIAL -> client.player.playSound(SoundEvents.BLOCK_END_PORTAL_SPAWN, 0.75f, 0.9f);
                case HORDE_START -> client.player.playSound(SoundEvents.ENTITY_WITHER_SPAWN, 0.6f, 1.15f);
                case BOSS_INTRO -> client.player.playSound(SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.75f);
                case BOSS_PHASE_CHANGE -> client.player.playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 0.8f);
                case BOSS_DEATH -> client.player.playSound(SoundEvents.ENTITY_ENDER_DRAGON_DEATH, 0.8f, 1.0f);
                case TRIAL_COMPLETE -> client.player.playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
                case TRIAL_FAILED -> client.player.playSound(SoundEvents.ENTITY_WITHER_HURT, 0.8f, 0.7f);
            }
        }
    }

    private static void tick(MinecraftClient client) {
        if (activeVisual == null) return;
        if (client.player == null || client.world == null || activeVisual.ticksRemaining() <= 0) {
            activeVisual = null;
            return;
        }
        if (activeVisual.ticksRemaining() % 4 == 0) {
            int count = activeVisual.event() == TrialVisualEvent.HORDE_START ? 10 : 16;
            client.world.addParticle(ParticleTypes.REVERSE_PORTAL, activeVisual.pos().x, activeVisual.pos().y + 1.0, activeVisual.pos().z, 0.0, 0.04, 0.0);
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2.0 * i / count + activeVisual.ticksRemaining() * 0.08;
                double radius = activeVisual.event() == TrialVisualEvent.BOSS_PHASE_CHANGE ? 2.8 : 1.8;
                client.world.addParticle(
                    activeVisual.event() == TrialVisualEvent.HORDE_START ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.DRAGON_BREATH,
                    activeVisual.pos().x + Math.cos(angle) * radius,
                    activeVisual.pos().y + 0.8 + Math.sin(activeVisual.ticksRemaining() * 0.08) * 0.25,
                    activeVisual.pos().z + Math.sin(angle) * radius,
                    0.0, 0.02, 0.0
                );
            }
        }
        activeVisual = activeVisual.tick();
    }

    private static void renderHud(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (activeVisual == null || client.player == null || client.options.hudHidden) return;
        float alpha = getFadeAlpha(activeVisual, tickDelta);
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();
        int darkAlpha = MathHelper.clamp((int) (alpha * 120.0f), 0, 120);
        context.fill(0, 0, width, height, darkAlpha << 24);
        if (!activeVisual.title().isBlank()) {
            int textAlpha = MathHelper.clamp((int) (alpha * 255.0f), 0, 255);
            int titleColor = (textAlpha << 24) | 0xF4D06F;
            int subtitleColor = (textAlpha << 24) | 0xD3B3FF;
            context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(activeVisual.title()), width / 2, height / 2 - 18, titleColor);
            if (!activeVisual.subtitle().isBlank()) {
                context.drawCenteredTextWithShadow(client.textRenderer, Text.literal(activeVisual.subtitle()), width / 2, height / 2, subtitleColor);
            }
        }
    }

    private static float getFadeAlpha(ActiveVisual visual, float tickDelta) {
        float elapsed = visual.totalTicks() - visual.ticksRemaining() + tickDelta;
        float fadeIn = MathHelper.clamp(elapsed / FADE_TICKS, 0.0f, 1.0f);
        float fadeOut = MathHelper.clamp((visual.ticksRemaining() - tickDelta) / FADE_TICKS, 0.0f, 1.0f);
        return Math.min(fadeIn, fadeOut);
    }
}
