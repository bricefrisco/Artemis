/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.core.events;

import com.wynntils.core.WynntilsMod;
import net.minecraftforge.eventbus.EventBus;
import net.minecraftforge.eventbus.api.BusBuilder;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;

public class EventBusWrapper extends EventBus {
    private EventBusWrapper(BusBuilder busBuilder) {
        super(busBuilder);
    }

    public static IEventBus createEventBus() {
        if (WynntilsMod.isDevelopmentEnvironment()) {
            return new EventBusWrapper(BusBuilder.builder());
        } else {
            return BusBuilder.builder().build();
        }
    }

    @Override
    public boolean post(Event event) {
        Class<? extends Event> eventClass = event.getClass();
        EventThread threadAnnotation = eventClass.getDeclaredAnnotation(EventThread.class);
        String threadName = Thread.currentThread().getName();
        if (threadAnnotation == null) {
            // Events without annotation are only allowed on Render thread
            if (!threadName.equals("Render thread")) {
                WynntilsMod.warn(
                        "Handling non-annotated event " + eventClass.getSimpleName() + " on thread " + threadName);
            }
        } else {
            // Make sure annotation matches the actual thread
            boolean threadOk =
                    switch (threadAnnotation.value()) {
                        case RENDER -> threadName.equals("Render thread");
                        case IO -> threadName.startsWith("Netty Client IO #");
                        case WORKER -> threadName.contains("pool");
                        case ANY -> true;
                    };
            if (!threadOk) {
                WynntilsMod.warn("Handling event " + eventClass.getSimpleName() + " annotated as "
                        + threadAnnotation.value() + " on thread " + threadName);
            }
        }

        return super.post(event);
    }
}
