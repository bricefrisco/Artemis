/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.gui.screens.overlays.lists;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wynntils.core.features.overlays.Overlay;
import com.wynntils.core.features.overlays.OverlayManager;
import com.wynntils.gui.render.FontRenderer;
import com.wynntils.gui.render.RenderUtils;
import com.wynntils.gui.render.Texture;
import com.wynntils.gui.screens.overlays.OverlaySelectionScreen;
import com.wynntils.gui.screens.overlays.lists.entries.OverlayEntry;
import com.wynntils.mc.utils.McUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;

public class OverlayList extends ContainerObjectSelectionList<OverlayEntry> {
    private static final int ITEM_HEIGHT = 25;
    private static final int ROW_WIDTH = 161;

    private static final List<Component> HELP_TOOLTIP_LINES = List.of(
            new TextComponent("Left click on the overlay to edit it."),
            new TextComponent("Right click on the overlay to disable/enable it."));

    private static final List<Component> DISABLED_PARENT_TOOLTIP_LINES = List.of(
            new TextComponent("This overlay's parent feature is disabled.").withStyle(ChatFormatting.RED),
            new TextComponent("Enable the feature to edit this overlay.").withStyle(ChatFormatting.RED));

    public OverlayList(OverlaySelectionScreen screen) {
        super(
                McUtils.mc(),
                screen.width,
                screen.height,
                screen.height / 10 + 15,
                screen.height / 10 + Texture.OVERLAY_SELECTION_GUI.height() - 15,
                ITEM_HEIGHT);

        List<Overlay> overlays =
                OverlayManager.getOverlays().stream().sorted(Overlay::compareTo).toList();

        for (Overlay overlay : overlays) {
            this.addEntry(new OverlayEntry(overlay));
        }

        this.setRenderBackground(false);
        this.setRenderTopAndBottom(false);
    }

    @Override
    public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
        super.render(poseStack, mouseX, mouseY, partialTick);

        OverlayEntry hovered = this.getHovered();

        if (hovered != null) {
            if (!hovered.getOverlay().isParentEnabled()) {
                List<Component> helpModified = new ArrayList<>(DISABLED_PARENT_TOOLTIP_LINES);
                helpModified.add(new TextComponent(""));
                helpModified.add(new TextComponent("Feature: "
                        + OverlayManager.getOverlayParent(hovered.getOverlay()).getTranslatedName()));

                RenderUtils.drawTooltipAt(
                        poseStack,
                        mouseX,
                        mouseY,
                        100,
                        helpModified,
                        FontRenderer.getInstance().getFont(),
                        false);
            } else {
                RenderUtils.drawTooltipAt(
                        poseStack,
                        mouseX,
                        mouseY,
                        100,
                        HELP_TOOLTIP_LINES,
                        FontRenderer.getInstance().getFont(),
                        false);
            }
        }
    }

    @Override
    protected void renderList(PoseStack poseStack, int x, int y, int mouseX, int mouseY, float partialTick) {
        int itemCount = this.getItemCount();

        int renderedCount = 0;

        this.hovered = null;

        for (int i = 0; i < itemCount; i++) {
            int top = this.y0 + 1 + renderedCount * this.itemHeight + this.headerHeight;
            int bottom = top + this.itemHeight;
            if (getRowTop(i) < this.y0 || bottom > this.y1) continue;

            OverlayEntry entry = this.getEntry(i);

            if (top + 1 <= mouseY
                    && top + 1 + this.itemHeight >= mouseY
                    && this.getRowLeft() <= mouseX
                    && this.getRowLeft() + this.getRowWidth() >= mouseX) {
                this.hovered = entry;
            }

            entry.render(
                    poseStack,
                    i,
                    top + 1,
                    this.getRowLeft(),
                    this.getRowWidth(),
                    this.itemHeight,
                    mouseX,
                    mouseY,
                    Objects.equals(this.getHovered(), entry),
                    partialTick);

            renderedCount++;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        this.updateScrollingState(mouseX, mouseY, button);

        return this.hovered != null && this.hovered.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected int getScrollbarPosition() {
        return super.getScrollbarPosition() - 43;
    }

    @Override
    protected int getRowTop(int index) {
        return this.y0 - (int) this.getScrollAmount() + index * this.itemHeight + this.headerHeight + 1;
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    @Override
    public int getRowLeft() {
        return this.x0 + this.width / 2 - this.getRowWidth() / 2 - 10;
    }

    public static int getItemHeight() {
        return ITEM_HEIGHT;
    }
}
