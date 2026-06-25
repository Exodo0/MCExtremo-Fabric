package com.egologic.mcextremo.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    private static final Text SERVER_ONLY_NOTICE = Text.literal("MCExtremo: solo servidores dedicados");

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mcextremo$hideSingleplayerButton(CallbackInfo ci) {
        for (Element child : this.children()) {
            if (child instanceof ButtonWidget button && isSingleplayerButton(button)) {
                button.active = false;
                button.visible = false;
            }
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mcextremo$renderServerOnlyNotice(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int y = Math.max(78, this.height / 4 + 72);
        context.drawCenteredTextWithShadow(this.textRenderer, SERVER_ONLY_NOTICE, this.width / 2, y, 0xFFAA55);
    }

    private boolean isSingleplayerButton(ButtonWidget button) {
        return button.getMessage().getContent() instanceof TranslatableTextContent content
            && "menu.singleplayer".equals(content.getKey());
    }
}
