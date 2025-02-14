/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.wynn.model.quests;

import com.wynntils.core.WynntilsMod;
import com.wynntils.core.managers.CoreManager;
import com.wynntils.core.webapi.request.Request;
import com.wynntils.core.webapi.request.RequestBuilder;
import com.wynntils.core.webapi.request.RequestHandler;
import com.wynntils.mc.objects.Location;
import com.wynntils.mc.utils.McUtils;
import com.wynntils.utils.Utils;
import com.wynntils.utils.WebUtils;
import com.wynntils.wynn.event.QuestBookReloadedEvent;
import com.wynntils.wynn.event.TrackedQuestUpdateEvent;
import com.wynntils.wynn.event.WorldStateEvent;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class QuestManager extends CoreManager {
    private static final String WIKI_BASE_URL = "https://wynncraft.fandom.com/wiki/";
    private static final String WIKI_QUEST_PAGE_QUERY =
            "https://wynncraft.fandom.com/index.php?title=Special:CargoExport&format=json&tables=Quests&fields=Quests._pageTitle&where=Quests.name=";

    public static final QuestScoreboardHandler SCOREBOARD_HANDLER = new QuestScoreboardHandler();
    private static final QuestContainerQueries CONTAINER_QUERIES = new QuestContainerQueries();
    private static final DialogueHistoryQueries DIALOGUE_HISTORY_QUERIES = new DialogueHistoryQueries();

    private static List<QuestInfo> quests = List.of();
    private static List<QuestInfo> miniQuests = List.of();
    private static List<List<String>> dialogueHistory = List.of();
    private static QuestInfo currentQuest = null;

    public static void init() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onWorldStateChanged(WorldStateEvent e) {
        quests = List.of();
        dialogueHistory = List.of();
    }

    public static void rescanQuestBook(boolean includeQuests, boolean includeMiniQuests) {
        WynntilsMod.info("Requesting rescan of Quest Book");
        if (includeQuests) {
            CONTAINER_QUERIES.queryQuestBook();
        }
        if (includeMiniQuests) {
            CONTAINER_QUERIES.queryMiniQuests();
        }
    }

    public static void rescanDialogueHistory() {
        DIALOGUE_HISTORY_QUERIES.scanDialogueHistory();
    }

    public static List<QuestInfo> getQuests(QuestSortOrder sortOrder) {
        return sortQuestInfoList(sortOrder, quests);
    }

    public static List<QuestInfo> getMiniQuests(QuestSortOrder sortOrder) {
        return sortQuestInfoList(sortOrder, miniQuests);
    }

    private static List<QuestInfo> sortQuestInfoList(QuestSortOrder sortOrder, List<QuestInfo> questList) {
        // All quests are always sorted by status (available then unavailable), and then
        // the given sort order, and finally a third way if the given sort order is equal.
        return switch (sortOrder) {
            case LEVEL -> questList.stream()
                    .sorted(Comparator.comparing(QuestInfo::getStatus)
                            .thenComparing(QuestInfo::getSortLevel)
                            .thenComparing(QuestInfo::getName))
                    .toList();
            case DISTANCE -> questList.stream()
                    .sorted(Comparator.comparing(QuestInfo::getStatus)
                            .thenComparing(new LocationComparator())
                            .thenComparing(QuestInfo::getName))
                    .toList();
            case ALPHABETIC -> questList.stream()
                    .sorted(Comparator.comparing(QuestInfo::getStatus)
                            .thenComparing(QuestInfo::getName)
                            .thenComparing(QuestInfo::getSortLevel))
                    .toList();
        };
    }

    public static Optional<QuestInfo> getQuestFromName(String name) {
        return quests.stream().filter(quest -> quest.getName().equals(name)).findFirst();
    }

    public static List<List<String>> getDialogueHistory() {
        return dialogueHistory;
    }

    public static void toggleTracking(QuestInfo questInfo) {
        CONTAINER_QUERIES.toggleTracking(questInfo);
    }

    public static void openQuestOnWiki(QuestInfo questInfo) {
        if (questInfo.isMiniQuest()) {
            String type = questInfo.getName().split(" ")[0];

            String wikiName = "Quests#" + type + "ing_Posts"; // Don't encode #

            Utils.openUrl(WIKI_BASE_URL + wikiName);
            return;
        }

        String url = WIKI_QUEST_PAGE_QUERY + WebUtils.encodeForCargoQuery(questInfo.getName());
        Request req = new RequestBuilder(url, "WikiQuestQuery")
                .handleJsonArray(jsonOutput -> {
                    String pageTitle = jsonOutput
                            .get(0)
                            .getAsJsonObject()
                            .get("_pageTitle")
                            .getAsString();
                    Utils.openUrl(WIKI_BASE_URL + WebUtils.encodeForWikiTitle(pageTitle));
                    return true;
                })
                .build();

        RequestHandler handler = new RequestHandler();

        handler.addAndDispatch(req, true);
    }

    public static QuestInfo getCurrentQuest() {
        return currentQuest;
    }

    public static Location getCurrentQuestLocation() {
        QuestInfo questInfo = QuestManager.getCurrentQuest();

        if (questInfo == null) return null;

        Optional<Location> location = questInfo.getNextLocation();

        if (location.isEmpty()) return null;

        return location.get();
    }

    protected static void setCurrentQuest(QuestInfo questInfo) {
        currentQuest = questInfo;
        WynntilsMod.postEvent(new TrackedQuestUpdateEvent(currentQuest));
    }

    protected static void setQuests(List<QuestInfo> newQuests) {
        quests = newQuests;
        WynntilsMod.postEvent(new QuestBookReloadedEvent.QuestsReloaded());
    }

    protected static void setMiniQuests(List<QuestInfo> newMiniQuests) {
        miniQuests = newMiniQuests;
        WynntilsMod.postEvent(new QuestBookReloadedEvent.MiniQuestsReloaded());
    }

    protected static void setDialogueHistory(List<List<String>> newDialogueHistory) {
        dialogueHistory = newDialogueHistory;
        WynntilsMod.postEvent(new QuestBookReloadedEvent.DialogueHistoryReloaded());
    }

    /** Shared between the container query classes */
    public static String getQuestBookTitle(int pageNum) {
        return "^§0\\[Pg. " + pageNum + "\\] §8.*§0 Quests$";
    }

    private static class LocationComparator implements Comparator<QuestInfo> {
        private static final Vec3 PLAYER_LOCATION = McUtils.player().position();

        private double getDistance(Optional<Location> loc) {
            // Quests with no location always counts as closest
            if (loc.isEmpty()) return 0f;

            Location location = loc.get();
            return PLAYER_LOCATION.distanceToSqr(location.toVec3());
        }

        @Override
        public int compare(QuestInfo quest1, QuestInfo quest2) {
            Optional<Location> loc1 = quest1.getNextLocation();
            Optional<Location> loc2 = quest2.getNextLocation();
            return (int) (getDistance(loc1) - getDistance(loc2));
        }
    }
}
