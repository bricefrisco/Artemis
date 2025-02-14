/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.features.user;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wynntils.core.WynntilsMod;
import com.wynntils.core.features.UserFeature;
import com.wynntils.core.features.properties.FeatureInfo;
import com.wynntils.core.features.properties.FeatureInfo.Stability;
import com.wynntils.core.features.properties.RegisterKeyBind;
import com.wynntils.core.keybinds.KeyBind;
import com.wynntils.gui.render.FontRenderer;
import com.wynntils.gui.render.RenderUtils;
import com.wynntils.mc.event.ItemTooltipRenderEvent;
import com.wynntils.mc.utils.McUtils;
import com.wynntils.wynn.item.GearItemStack;
import com.wynntils.wynn.model.ChatItemModel;
import com.wynntils.wynn.utils.WynnItemUtils;
import com.wynntils.wynn.utils.WynnUtils;
import java.awt.HeadlessException;
import java.awt.image.BufferedImage;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

@FeatureInfo(stability = Stability.INVARIABLE)
public class ItemScreenshotFeature extends UserFeature {
    @RegisterKeyBind
    private final KeyBind itemScreenshotKeyBind =
            new KeyBind("Screenshot Item", GLFW.GLFW_KEY_F4, true, null, this::onInventoryPress);

    private Slot screenshotSlot = null;

    private void onInventoryPress(Slot hoveredSlot) {
        screenshotSlot = hoveredSlot;
    }

    @SubscribeEvent
    public void render(ItemTooltipRenderEvent.Pre e) {
        if (!WynnUtils.onWorld()) return;
        if (screenshotSlot == null || !screenshotSlot.hasItem()) return;

        Screen screen = McUtils.mc().screen;
        if (!(screen instanceof AbstractContainerScreen<?>)) return;

        // has to be called during a render period
        takeScreenshot(screen, screenshotSlot);
        screenshotSlot = null;
    }

    private static void takeScreenshot(Screen screen, Slot hoveredSlot) {
        ItemStack stack = hoveredSlot.getItem();
        List<Component> tooltip = stack.getTooltipLines(null, TooltipFlag.Default.NORMAL);
        WynnItemUtils.removeLoreTooltipLines(tooltip);

        Font font = FontRenderer.getInstance().getFont();

        // width calculation
        int width = 0;
        for (Component c : tooltip) {
            int w = font.width(c.getString());
            if (w > width) {
                width = w;
            }
        }
        width += 8;

        // height calculation
        int height = 16;
        if (tooltip.size() > 1) {
            height += 2 + (tooltip.size() - 1) * 10;
        }

        // calculate tooltip size to fit to framebuffer
        float scaleh = (float) screen.height / height;
        float scalew = (float) screen.width / width;

        // draw tooltip to framebuffer, create image
        McUtils.mc().getMainRenderTarget().unbindWrite();

        PoseStack poseStack = new PoseStack();
        RenderTarget fb = new MainTarget(width * 2, height * 2);
        fb.setClearColor(1f, 1f, 1f, 0f);
        fb.createBuffers(width * 2, height * 2, false);
        fb.bindWrite(false);
        poseStack.pushPose();
        poseStack.scale(scalew, scaleh, 1);
        RenderUtils.drawTooltip(poseStack, tooltip, font, true);
        poseStack.popPose();
        fb.unbindWrite();
        McUtils.mc().getMainRenderTarget().bindWrite(true);

        BufferedImage bi = RenderUtils.createScreenshot(fb);
        try {
            RenderUtils.copyImageToClipboard(bi);
            McUtils.sendMessageToClient(
                    new TranslatableComponent("feature.wynntils.itemScreenshot.message", stack.getHoverName())
                            .withStyle(ChatFormatting.GREEN));
        } catch (HeadlessException ex) {
            WynntilsMod.error("Failed to copy image to clipboard", ex);
            McUtils.sendMessageToClient(
                    new TranslatableComponent("feature.wynntils.itemScreenshot.error", stack.getHoverName())
                            .withStyle(ChatFormatting.RED));
        }

        // chat item prompt
        if (stack instanceof GearItemStack gearItem) {
            String encoded = ChatItemModel.encodeItem(gearItem);

            McUtils.sendMessageToClient(new TranslatableComponent("feature.wynntils.itemScreenshot.chatItemMessage")
                    .withStyle(ChatFormatting.DARK_GREEN)
                    .withStyle(ChatFormatting.UNDERLINE)
                    .withStyle(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, encoded)))
                    .withStyle(s -> s.withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            new TranslatableComponent("feature.wynntils.itemScreenshot.chatItemTooltip")
                                    .withStyle(ChatFormatting.DARK_AQUA)))));
        }
    }
}
