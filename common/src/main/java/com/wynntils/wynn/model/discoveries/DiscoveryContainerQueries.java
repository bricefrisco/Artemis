/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.wynn.model.discoveries;

import com.wynntils.core.WynntilsMod;
import com.wynntils.mc.utils.ComponentUtils;
import com.wynntils.mc.utils.ItemUtils;
import com.wynntils.mc.utils.McUtils;
import com.wynntils.wynn.model.container.ContainerContent;
import com.wynntils.wynn.model.container.ScriptedContainerQuery;
import com.wynntils.wynn.model.discoveries.objects.DiscoveryInfo;
import com.wynntils.wynn.model.quests.QuestManager;
import com.wynntils.wynn.utils.InventoryUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class DiscoveryContainerQueries {
    private static final int NEXT_PAGE_SLOT = 8;
    private static final int DISCOVERIES_SLOT = 35;
    private static final int SECRET_DISCOVERIES_SLOT = 44;

    private static final int DISCOVERIES_PER_PAGE =
            41; // 6 * 7 items, but - 1 because last item is missing because of Wynn bug

    private static final Pattern DISCOVERY_COUNT_PATTERN =
            Pattern.compile("§6Total Discoveries: §r§e\\[(\\d+)/\\d+\\]");
    private static final Pattern SECRET_DISCOVERY_COUNT_PATTERN =
            Pattern.compile("§bTotal Secret Discoveries: §r§3\\[(\\d+)/\\d+\\]");

    private List<DiscoveryInfo> newDiscoveries;

    public void queryDiscoveries() {
        ScriptedContainerQuery.QueryBuilder queryBuilder = ScriptedContainerQuery.builder("Discovery Count Query")
                .onError(msg -> WynntilsMod.warn("Problem getting discovery count in Quest Book: " + msg))
                .useItemInHotbar(InventoryUtils.QUEST_BOOK_SLOT_NUM)
                .matchTitle(QuestManager.getQuestBookTitle(1))
                .processContainer((c) -> {
                    ItemStack discoveriesItem = c.items().get(DISCOVERIES_SLOT);
                    ItemStack secretDiscoveriesItem = c.items().get(SECRET_DISCOVERIES_SLOT);

                    if (!ComponentUtils.getCoded(discoveriesItem.getHoverName()).equals("§6§lDiscoveries")
                            || !ComponentUtils.getCoded(secretDiscoveriesItem.getHoverName())
                                    .equals("§b§lSecret Discoveries")) {
                        WynntilsMod.error("Returned early because discovery items were not found.");

                        return;
                    }

                    int discoveryCount = -1;
                    for (String line : ItemUtils.getLore(discoveriesItem)) {
                        Matcher matcher = DISCOVERY_COUNT_PATTERN.matcher(line);

                        if (matcher.matches()) {
                            discoveryCount = Integer.parseInt(matcher.group(1));
                            break;
                        }
                    }

                    if (discoveryCount == -1) {
                        WynntilsMod.error("Could not find discovery count in discovery item.");

                        return;
                    }

                    for (String line : ItemUtils.getLore(secretDiscoveriesItem)) {
                        Matcher matcher = SECRET_DISCOVERY_COUNT_PATTERN.matcher(line);

                        if (matcher.matches()) {
                            int secretDiscoveryCount = Integer.parseInt(matcher.group(1));

                            if (secretDiscoveryCount == -1) {
                                WynntilsMod.error("Could not find secret discovery count in secret discovery item.");

                                return;
                            }

                            DiscoveryManager.setDiscoveriesTooltip(ItemUtils.getTooltipLines(discoveriesItem));
                            DiscoveryManager.setSecretDiscoveriesTooltip(
                                    ItemUtils.getTooltipLines(secretDiscoveriesItem));

                            int discoveryPages = discoveryCount / DISCOVERIES_PER_PAGE
                                    + (discoveryCount % DISCOVERIES_PER_PAGE == 0 ? 0 : 1);
                            int secretDiscoveryPages = secretDiscoveryCount / DISCOVERIES_PER_PAGE
                                    + (secretDiscoveryCount % DISCOVERIES_PER_PAGE == 0 ? 0 : 1);
                            buildDiscoveryQuery(discoveryPages, secretDiscoveryPages);
                            break;
                        }
                    }
                });

        queryBuilder.build().executeQuery();
    }

    private void buildDiscoveryQuery(int discoveryPages, int secretDiscoveryPages) {
        ScriptedContainerQuery.QueryBuilder queryBuilder = ScriptedContainerQuery.builder("Discovery Query")
                .onError(msg -> {
                    WynntilsMod.warn("Problem querying discoveries: " + msg);
                    McUtils.sendMessageToClient(
                            new TextComponent("Error updating discoveries.").withStyle(ChatFormatting.RED));
                })
                .useItemInHotbar(InventoryUtils.QUEST_BOOK_SLOT_NUM)
                .matchTitle(QuestManager.getQuestBookTitle(1))
                .processContainer(c -> {})
                .clickOnSlot(DISCOVERIES_SLOT)
                .matchTitle(getDiscoveryPageTitle(1))
                .processContainer(c -> processDiscoveryPage(c, 1, discoveryPages, false));

        for (int i = 2; i <= discoveryPages; i++) {
            final int page = i; // Lambdas need final variables
            queryBuilder
                    .clickOnSlotWithName(NEXT_PAGE_SLOT, Items.GOLDEN_SHOVEL, getNextPageButtonName(page))
                    .matchTitle(getDiscoveryPageTitle(page))
                    .processContainer(c -> processDiscoveryPage(c, page, discoveryPages, false));
        }

        queryBuilder
                .clickOnSlot(SECRET_DISCOVERIES_SLOT)
                .matchTitle(getDiscoveryPageTitle(1))
                .processContainer(c -> processDiscoveryPage(c, 1, secretDiscoveryPages, true));

        for (int i = 2; i <= secretDiscoveryPages; i++) {
            final int page = i; // Lambdas need final variables
            queryBuilder
                    .clickOnSlotWithName(NEXT_PAGE_SLOT, Items.GOLDEN_SHOVEL, getNextPageButtonName(page))
                    .matchTitle(getDiscoveryPageTitle(page))
                    .processContainer(c -> processDiscoveryPage(c, page, secretDiscoveryPages, true));
        }

        queryBuilder.build().executeQuery();
    }

    private void processDiscoveryPage(ContainerContent container, int page, int lastPage, boolean secretDiscovery) {
        if (page == 1) {
            newDiscoveries = new ArrayList<>();
        }

        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 7; col++) {
                int slot = row * 9 + col;

                ItemStack item = container.items().get(slot);
                DiscoveryInfo discoveryInfo = DiscoveryInfo.parseFromItemStack(item);
                if (discoveryInfo == null) continue;

                newDiscoveries.add(discoveryInfo);
            }
        }

        if (page == lastPage) {
            // Last page finished
            if (secretDiscovery) {
                // Secret discoveries finished
                DiscoveryManager.setSecretDiscoveries(newDiscoveries);
            } else {
                // Normal discoveries finished
                DiscoveryManager.setDiscoveries(newDiscoveries);
            }
        }
    }

    public static String getDiscoveryPageTitle(int pageNum) {
        // FIXME: We ignore pageNum, as we do not have a valid way of only querying dynamic amounts of pages
        return "^§0\\[Pg. \\d+\\] §8.*§0 Discoveries$";
    }

    private String getNextPageButtonName(int nextPageNum) {
        return "[§f§lPage " + nextPageNum + "§a >§2>§a>§2>§a>]";
    }
}
