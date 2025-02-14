/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.wynn.event;

import com.wynntils.core.notifications.MessageContainer;
import net.minecraftforge.eventbus.api.Event;

public class NotificationEvent extends Event {
    private final MessageContainer messageContainer;

    private NotificationEvent(MessageContainer messageContainer) {
        this.messageContainer = messageContainer;
    }

    public MessageContainer getMessageContainer() {
        return messageContainer;
    }

    public static class Queue extends NotificationEvent {
        public Queue(MessageContainer messageContainer) {
            super(messageContainer);
        }
    }

    public static class Edit extends NotificationEvent {
        public Edit(MessageContainer messageContainer) {
            super(messageContainer);
        }
    }
}
