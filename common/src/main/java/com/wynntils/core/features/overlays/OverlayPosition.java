/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.core.features.overlays;

import com.wynntils.gui.render.HorizontalAlignment;
import com.wynntils.gui.render.VerticalAlignment;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.world.phys.Vec2;

public class OverlayPosition {
    private static final Pattern POSITION_PATTERN = Pattern.compile(
            "OverlayPosition\\{verticalOffset=(.+),horizontalOffset=(.+),verticalAlignment=(.+),horizontalAlignment=(.+),anchorSection=(.+)}");

    private final float verticalOffset;
    private final float horizontalOffset;

    private final VerticalAlignment verticalAlignment;
    private final HorizontalAlignment horizontalAlignment;

    private final AnchorSection anchorSection;

    public OverlayPosition(
            float verticalOffset,
            float horizontalOffset,
            VerticalAlignment verticalAlignment,
            HorizontalAlignment horizontalAlignment,
            AnchorSection anchorSection) {
        this.verticalAlignment = verticalAlignment;
        this.horizontalAlignment = horizontalAlignment;
        this.anchorSection = anchorSection;
        this.verticalOffset = verticalOffset;
        this.horizontalOffset = horizontalOffset;
    }

    public OverlayPosition(String string) {
        Matcher matcher = POSITION_PATTERN.matcher(string.replaceAll(" ", ""));

        if (!matcher.matches()) {
            throw new RuntimeException("Failed to parse OverlayPosition");
        }

        try {
            this.verticalOffset = Float.parseFloat(matcher.group(1));
            this.horizontalOffset = Float.parseFloat(matcher.group(2));
            this.verticalAlignment = VerticalAlignment.valueOf(matcher.group(3));
            this.horizontalAlignment = HorizontalAlignment.valueOf(matcher.group(4));
            this.anchorSection = AnchorSection.valueOf(matcher.group(5));
        } catch (IllegalArgumentException exception) {
            throw new RuntimeException("Failed to parse OverlayPosition", exception);
        }
    }

    public AnchorSection getAnchorSection() {
        return anchorSection;
    }

    public HorizontalAlignment getHorizontalAlignment() {
        return horizontalAlignment;
    }

    public float getHorizontalOffset() {
        return horizontalOffset;
    }

    public VerticalAlignment getVerticalAlignment() {
        return verticalAlignment;
    }

    public float getVerticalOffset() {
        return verticalOffset;
    }

    @Override
    public String toString() {
        return "OverlayPosition{" + "verticalOffset="
                + verticalOffset + ", horizontalOffset="
                + horizontalOffset + ", verticalAlignment="
                + verticalAlignment + ", horizontalAlignment="
                + horizontalAlignment + ", anchorSection="
                + anchorSection + '}';
    }

    public static OverlayPosition getBestPositionFor(
            Overlay overlay, float oldRenderX, float oldRenderY, float offsetX, float offsetY) {
        Vec2 referencePoint = new Vec2(
                overlay.getRenderX() + overlay.getWidth() / 2f, overlay.getRenderY() + overlay.getHeight() / 2f);

        // 1. Get the best section (section with the center point of overlay)
        AnchorSection section = Arrays.stream(AnchorSection.values())
                .filter(anchorSection ->
                        OverlayManager.getSection(anchorSection).overlaps(referencePoint.x, referencePoint.y))
                .findAny()
                .orElse(AnchorSection.Middle);

        SectionCoordinates sectionCoordinates = OverlayManager.getSection(section);

        // 2. Calculate the best alignment inside the section
        HorizontalAlignment horizontalAlignment = HorizontalAlignment.Right;

        float horizontalChunkWidth = (sectionCoordinates.x2() - sectionCoordinates.x1()) / 3f;
        if (referencePoint.x < sectionCoordinates.x1() + horizontalChunkWidth) {
            horizontalAlignment = HorizontalAlignment.Left;
        } else if (referencePoint.x < sectionCoordinates.x1() + horizontalChunkWidth * 2) {
            horizontalAlignment = HorizontalAlignment.Center;
        }

        VerticalAlignment verticalAlignment = VerticalAlignment.Bottom;

        float verticalChunkHeight = (sectionCoordinates.y2() - sectionCoordinates.y1()) / 3f;
        if (referencePoint.y < sectionCoordinates.y1() + verticalChunkHeight) {
            verticalAlignment = VerticalAlignment.Top;
        } else if (referencePoint.y < sectionCoordinates.y1() + verticalChunkHeight * 2) {
            verticalAlignment = VerticalAlignment.Middle;
        }

        // 3. Calculate render positions for new alignment
        OverlayPosition newOverlayPositionTemp =
                new OverlayPosition(0, 0, verticalAlignment, horizontalAlignment, section);

        float renderX = overlay.getRenderX(newOverlayPositionTemp);
        float renderY = overlay.getRenderY(newOverlayPositionTemp);

        // 4. Calculate the alignment offsets to match the current render position, but factor in argument offsets
        return new OverlayPosition(
                oldRenderY - renderY + offsetY,
                oldRenderX - renderX + offsetX,
                verticalAlignment,
                horizontalAlignment,
                section);
    }

    public enum AnchorSection {
        TopLeft(0),
        TopMiddle(1),
        TopRight(2),
        MiddleLeft(3),
        Middle(4),
        MiddleRight(5),
        BottomLeft(6),
        BottomMiddle(7),
        BottomRight(8);

        private final int index;

        AnchorSection(int index) {
            this.index = index;
        }

        public int getIndex() {
            return index;
        }
    }
}
