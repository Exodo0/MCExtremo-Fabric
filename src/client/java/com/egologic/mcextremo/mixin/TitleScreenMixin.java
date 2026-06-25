package com.egologic.mcextremo.mixin;

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
    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "initWidgetsNormal", at = @At("TAIL"), require = 0)
    private void mcextremo$hideSingleplayerButton(int y, int spacingY, CallbackInfo ci) {
        for (Element child : this.children()) {
            if (child instanceof ButtonWidget button && isSingleplayerButton(button)) {
                button.active = false;
                button.visible = false;
            }
        }
    }

    private boolean isSingleplayerButton(ButtonWidget button) {
        return button.getMessage().getContent() instanceof TranslatableTextContent content
            && "menu.singleplayer".equals(content.getKey());
    }
}
