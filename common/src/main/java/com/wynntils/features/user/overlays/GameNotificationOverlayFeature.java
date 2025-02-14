/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.features.user.overlays;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wynntils.core.config.Config;
import com.wynntils.core.config.ConfigHolder;
import com.wynntils.core.features.UserFeature;
import com.wynntils.core.features.overlays.Overlay;
import com.wynntils.core.features.overlays.OverlayPosition;
import com.wynntils.core.features.overlays.annotations.OverlayInfo;
import com.wynntils.core.features.overlays.sizes.GuiScaledOverlaySize;
import com.wynntils.core.features.properties.FeatureCategory;
import com.wynntils.core.features.properties.FeatureInfo;
import com.wynntils.core.notifications.MessageContainer;
import com.wynntils.core.notifications.TimedMessageContainer;
import com.wynntils.gui.render.FontRenderer;
import com.wynntils.gui.render.HorizontalAlignment;
import com.wynntils.gui.render.TextRenderSetting;
import com.wynntils.gui.render.TextRenderTask;
import com.wynntils.gui.render.VerticalAlignment;
import com.wynntils.mc.event.RenderEvent;
import com.wynntils.wynn.event.NotificationEvent;
import com.wynntils.wynn.event.WorldStateEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@FeatureInfo(category = FeatureCategory.OVERLAYS)
public class GameNotificationOverlayFeature extends UserFeature {
    public static GameNotificationOverlayFeature INSTANCE;

    private static final List<TimedMessageContainer> messageQueue = new LinkedList<>();

    @OverlayInfo(renderType = RenderEvent.ElementType.GUI)
    public final GameNotificationOverlay gameNotificationOverlay = new GameNotificationOverlay();

    @SubscribeEvent
    public void onWorldStateChange(WorldStateEvent event) {
        messageQueue.clear();
    }

    @SubscribeEvent
    public void onGameNotification(NotificationEvent.Queue event) {
        messageQueue.add(new TimedMessageContainer(
                event.getMessageContainer(), (long) gameNotificationOverlay.messageTimeLimit * 1000));

        if (GameNotificationOverlayFeature.INSTANCE.gameNotificationOverlay.overrideNewMessages
                && messageQueue.size() > GameNotificationOverlayFeature.INSTANCE.gameNotificationOverlay.messageLimit) {
            messageQueue.remove(0);
        }
    }

    @SubscribeEvent
    public void onGameNotification(NotificationEvent.Edit event) {
        MessageContainer newContainer = event.getMessageContainer();
        messageQueue.stream()
                .filter(timedMessageContainer ->
                        timedMessageContainer.getMessageContainer().hashCode() == newContainer.hashCode())
                .findFirst()
                .ifPresent(timedMessageContainer -> timedMessageContainer.update(
                        newContainer, (long) gameNotificationOverlay.messageTimeLimit * 1000));
    }

    public static class GameNotificationOverlay extends Overlay {
        @Config
        public float messageTimeLimit = 10f;

        @Config
        public int messageLimit = 5;

        @Config
        public boolean invertGrowth = true;

        @Config
        public int messageMaxLength = 0;

        @Config
        public FontRenderer.TextShadow textShadow = FontRenderer.TextShadow.OUTLINE;

        @Config
        public boolean overrideNewMessages = true;

        private TextRenderSetting textRenderSetting;

        protected GameNotificationOverlay() {
            super(
                    new OverlayPosition(
                            -20,
                            -5,
                            VerticalAlignment.Top,
                            HorizontalAlignment.Right,
                            OverlayPosition.AnchorSection.BottomRight),
                    new GuiScaledOverlaySize(250, 110));

            updateTextRenderSetting();
        }

        @Override
        public void render(PoseStack poseStack, float partialTicks, Window window) {
            List<TimedMessageContainer> toRender = new ArrayList<>();

            ListIterator<TimedMessageContainer> messages = messageQueue.listIterator(messageQueue.size());
            while (messages.hasPrevious()) {
                TimedMessageContainer message = messages.previous();

                if (message.getRemainingTime() <= 0.0f) {
                    messages.remove(); // remove the message if the time has come
                    continue;
                }

                TextRenderTask messageTask = message.getRenderTask();

                if (messageMaxLength == 0 || messageTask.getText().length() < messageMaxLength) {
                    toRender.add(message);
                } else {
                    TimedMessageContainer first = new TimedMessageContainer(
                            new MessageContainer(messageTask.getText().substring(0, messageMaxLength)),
                            message.getEndTime());
                    TimedMessageContainer second = new TimedMessageContainer(
                            new MessageContainer(messageTask.getText().substring(messageMaxLength)),
                            message.getEndTime());
                    if (this.invertGrowth) {
                        toRender.add(first);
                        toRender.add(second);
                    } else {
                        toRender.add(second);
                        toRender.add(first);
                    }
                }
            }

            if (toRender.isEmpty()) return;

            List<TimedMessageContainer> renderedValues = this.overrideNewMessages
                    ? toRender.subList(0, Math.min(toRender.size(), this.messageLimit))
                    : toRender.subList(Math.max(toRender.size() - this.messageLimit, 0), toRender.size());

            Collections.reverse(renderedValues);

            if (this.invertGrowth) {
                while (renderedValues.size() < messageLimit) {
                    renderedValues.add(0, new TimedMessageContainer(new MessageContainer(""), (long)
                            (this.messageTimeLimit * 1000)));
                }
            }

            FontRenderer.getInstance()
                    .renderTextsWithAlignment(
                            poseStack,
                            this.getRenderX(),
                            this.getRenderY(),
                            renderedValues.stream()
                                    .map(messageContainer -> messageContainer
                                            .getRenderTask()
                                            .setSetting(textRenderSetting.withCustomColor(messageContainer
                                                    .getRenderTask()
                                                    .getSetting()
                                                    .customColor()
                                                    .withAlpha(messageContainer.getRemainingTime() / 1000f))))
                                    .toList(),
                            this.getWidth(),
                            this.getHeight(),
                            this.getRenderHorizontalAlignment(),
                            this.getRenderVerticalAlignment());
        }

        @Override
        public void renderPreview(PoseStack poseStack, float partialTicks, Window window) {
            FontRenderer.getInstance()
                    .renderTextWithAlignment(
                            poseStack,
                            this.getRenderX(),
                            this.getRenderY(),
                            new TextRenderTask("§r§a→ §r§2Player [§r§aWC1/Archer§r§2]", textRenderSetting),
                            this.getWidth(),
                            this.getHeight(),
                            this.getRenderHorizontalAlignment(),
                            this.getRenderVerticalAlignment());
        }

        @Override
        protected void onConfigUpdate(ConfigHolder configHolder) {
            updateTextRenderSetting();
        }

        private void updateTextRenderSetting() {
            textRenderSetting = TextRenderSetting.DEFAULT
                    .withMaxWidth(this.getWidth())
                    .withHorizontalAlignment(this.getRenderHorizontalAlignment())
                    .withVerticalAlignment(this.getRenderVerticalAlignment())
                    .withTextShadow(textShadow);
        }
    }
}
