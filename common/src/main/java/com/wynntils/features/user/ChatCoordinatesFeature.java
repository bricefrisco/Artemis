/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.features.user;

import com.wynntils.core.features.UserFeature;
import com.wynntils.core.managers.Model;
import com.wynntils.mc.objects.Location;
import com.wynntils.mc.utils.ComponentUtils;
import com.wynntils.mc.utils.LocationUtils;
import com.wynntils.wynn.event.ChatMessageReceivedEvent;
import com.wynntils.wynn.model.CompassModel;
import com.wynntils.wynn.utils.WynnUtils;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class ChatCoordinatesFeature extends UserFeature {
    @Override
    public List<Class<? extends Model>> getModelDependencies() {
        return List.of(CompassModel.class);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChatReceived(ChatMessageReceivedEvent e) {
        if (!WynnUtils.onWorld()) return;

        Component message = e.getMessage();

        e.setMessage(insertCoordinateComponents(message));
    }

    public static Component insertCoordinateComponents(Component message) {
        // no coordinate clickables to insert
        if (!LocationUtils.strictCoordinateMatcher(ComponentUtils.getCoded(message))
                .find()) return message;

        List<MutableComponent> components =
                message.getSiblings().stream().map(Component::copy).collect(Collectors.toList());
        components.add(0, message.plainCopy().withStyle(message.getStyle()));

        MutableComponent temp = new TextComponent("");

        for (Component comp : components) {
            Matcher m = LocationUtils.strictCoordinateMatcher((ComponentUtils.getCoded(comp)));
            if (!m.find()) {
                Component newComponent = comp.copy();
                temp.append(newComponent);
                continue;
            }

            do {
                String text = ComponentUtils.getCoded(comp);
                Style style = comp.getStyle();

                Optional<Location> location = LocationUtils.parseFromString(m.group());
                if (location.isEmpty()) { // couldn't decode, skip
                    comp = comp.copy();
                    continue;
                }

                MutableComponent preText = new TextComponent(text.substring(0, m.start()));
                preText.withStyle(style);
                temp.append(preText);

                // create hover-able text component for the item
                Component compassComponent = createLocationComponent(location.get());
                temp.append(compassComponent);

                comp = new TextComponent(ComponentUtils.getLastPartCodes(ComponentUtils.getCoded(preText))
                                + text.substring(m.end()))
                        .withStyle(style);
                m = LocationUtils.strictCoordinateMatcher(
                        ComponentUtils.getCoded(comp)); // recreate matcher for new substring
            } while (m.find()); // search for multiple items in the same message

            temp.append(comp); // leftover text after item(s)
        }

        return temp;
    }

    private static Component createLocationComponent(Location location) {
        MutableComponent component = new TextComponent(
                        "[%d, %d, %d]".formatted((int) location.x, (int) location.y, (int) location.z))
                .withStyle(ChatFormatting.DARK_AQUA)
                .withStyle(ChatFormatting.UNDERLINE);

        component.withStyle(style -> style.withClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND, "/compass at " + location.x + " " + location.y + " " + location.z)));
        component.withStyle(style -> style.withHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT, new TextComponent("Click here to set your compass to this location"))));

        return component;
    }
}
