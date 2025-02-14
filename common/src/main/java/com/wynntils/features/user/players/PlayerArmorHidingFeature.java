/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.features.user.players;

import com.wynntils.core.config.Config;
import com.wynntils.core.features.UserFeature;
import com.wynntils.core.features.properties.FeatureCategory;
import com.wynntils.core.features.properties.FeatureInfo;
import com.wynntils.core.features.properties.StartDisabled;
import com.wynntils.mc.event.PlayerArmorRenderEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.eventbus.api.SubscribeEvent;

@StartDisabled
@FeatureInfo(category = FeatureCategory.PLAYERS)
public class PlayerArmorHidingFeature extends UserFeature {
    @Config
    public boolean hideHelmets = true;

    @Config
    public boolean hideChestplates = true;

    @Config
    public boolean hideLeggings = true;

    @Config
    public boolean hideBoots = true;

    @Config
    public boolean showCosmetics = true;

    @SubscribeEvent
    public void onPlayerArmorRender(PlayerArmorRenderEvent event) {
        switch (event.getSlot()) {
            case HEAD -> {
                if (!hideHelmets) return;
                if (!showCosmetics) { // helmet is hidden regardless, no extra logic needed
                    event.setCanceled(true);
                    return;
                }

                // only cancel if helmet item isn't cosmetic - all helmet skins use a pickaxe texture
                ItemStack headItem = event.getPlayer().getItemBySlot(event.getSlot());
                if (headItem.getItem() != Items.DIAMOND_PICKAXE) event.setCanceled(true);
                return;
            }
            case CHEST -> {
                if (hideChestplates) event.setCanceled(true);
                return;
            }
            case LEGS -> {
                if (hideLeggings) event.setCanceled(true);
                return;
            }
            case FEET -> {
                if (hideBoots) event.setCanceled(true);
                return;
            }
        }
    }
}
