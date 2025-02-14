/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.gui.widgets;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wynntils.gui.render.FontRenderer;
import com.wynntils.gui.render.HorizontalAlignment;
import com.wynntils.gui.render.RenderUtils;
import com.wynntils.gui.render.Texture;
import com.wynntils.gui.render.VerticalAlignment;
import com.wynntils.gui.screens.CharacterSelectorScreen;
import com.wynntils.mc.objects.CommonColors;
import com.wynntils.wynn.model.CharacterSelectionManager;
import com.wynntils.wynn.objects.ClassInfo;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.TextComponent;

public class ClassInfoButton extends AbstractButton {
    private final ClassInfo classInfo;
    private final CharacterSelectorScreen characterSelectorScreen;

    public ClassInfoButton(
            int x, int y, int width, int height, ClassInfo classInfo, CharacterSelectorScreen characterSelectorScreen) {
        super(x, y, width, height, new TextComponent("Class Info Button"));
        this.classInfo = classInfo;
        this.characterSelectorScreen = characterSelectorScreen;
    }

    @Override
    public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        RenderUtils.drawTexturedRect(
                poseStack,
                Texture.CHARACTER_BUTTON.resource(),
                this.x,
                this.y,
                0,
                this.width,
                this.height,
                0,
                this.isHovered || characterSelectorScreen.getSelected() == this
                        ? Texture.CHARACTER_BUTTON.height() / 2
                        : 0,
                Texture.CHARACTER_BUTTON.width(),
                Texture.CHARACTER_BUTTON.height() / 2,
                Texture.CHARACTER_BUTTON.width(),
                Texture.CHARACTER_BUTTON.height());

        float itemScale = this.height * 0.03f;
        RenderUtils.renderGuiItem(
                classInfo.itemStack(),
                (int) (this.x + this.width * 0.038f * itemScale),
                (int) (this.y + this.height * 0.12f * itemScale),
                itemScale);

        poseStack.pushPose();
        poseStack.translate(this.x + this.width * 0.25f, this.y + this.height * 0.16f, 0f);
        float scale = this.height * 0.032f;
        poseStack.scale(scale, scale, 0f);

        FontRenderer.getInstance()
                .renderText(
                        poseStack,
                        classInfo.name(),
                        0,
                        0,
                        CommonColors.BLACK,
                        HorizontalAlignment.Left,
                        VerticalAlignment.Top,
                        FontRenderer.TextShadow.NONE);
        FontRenderer.getInstance()
                .renderText(
                        poseStack,
                        "Level " + classInfo.level(),
                        0,
                        10f,
                        CommonColors.BLACK,
                        HorizontalAlignment.Left,
                        VerticalAlignment.Top,
                        FontRenderer.TextShadow.NONE);

        poseStack.popPose();

        RenderUtils.drawProgressBar(
                poseStack,
                Texture.XP_BAR,
                this.x + 5,
                this.y + this.height * 0.8f,
                this.x + 5 + this.width * 0.9f,
                this.y + this.height * 0.9f,
                0,
                0,
                Texture.XP_BAR.width(),
                Texture.XP_BAR.height(),
                classInfo.xp() / 100f);
    }

    @Override
    public void onPress() {
        if (characterSelectorScreen.getSelected() == this) {
            CharacterSelectionManager.playWithCharacter(classInfo.slot());
        }
    }

    @Override
    public void updateNarration(NarrationElementOutput narrationElementOutput) {}

    public ClassInfo getClassInfo() {
        return classInfo;
    }
}
