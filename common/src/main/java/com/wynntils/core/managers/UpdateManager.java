/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.core.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wynntils.core.WynntilsMod;
import com.wynntils.core.webapi.WebManager;
import com.wynntils.utils.FileUtils;
import com.wynntils.utils.MD5Verification;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class UpdateManager extends CoreManager {
    private static final String LAST_BUILD_CHECK_PATH = "https://athena.wynntils.com/version/latest/ce";
    private static final String WYNTILLS_UPDATE_FOLDER = "updates";
    private static final String WYNNTILS_UPDATE_FILE_NAME = "wynntils-update.jar";

    public static void init() {}

    public static CompletableFuture<String> getLatestBuild() {
        CompletableFuture<String> future = new CompletableFuture<>();

        try {
            URLConnection st = WebManager.generateURLRequest(LAST_BUILD_CHECK_PATH);
            InputStreamReader stInputReader = new InputStreamReader(st.getInputStream(), StandardCharsets.UTF_8);
            JsonObject jsonObject = JsonParser.parseReader(stInputReader).getAsJsonObject();

            String version = jsonObject.getAsJsonPrimitive("version").getAsString();
            future.complete(version);

            return future;
        } catch (IOException e) {
            WynntilsMod.error("Exception while trying to fetch update.", e);
            future.complete(null);
            return future;
        }
    }

    public static CompletableFuture<UpdateResult> tryUpdate() {
        CompletableFuture<UpdateResult> future = new CompletableFuture<>();

        try {
            File updateFile = getUpdateFile();
            if (updateFile.exists()) {
                future.complete(UpdateResult.UPDATE_PENDING);
                return future;
            }

            URLConnection st = WebManager.generateURLRequest(LAST_BUILD_CHECK_PATH);
            InputStreamReader stInputReader = new InputStreamReader(st.getInputStream(), StandardCharsets.UTF_8);
            JsonObject jsonObject = JsonParser.parseReader(stInputReader).getAsJsonObject();

            String latestMd5 = jsonObject.getAsJsonPrimitive("md5").getAsString();

            String currentMd5 = getCurrentMd5();
            if (Objects.equals(currentMd5, latestMd5)) {
                future.complete(UpdateResult.ALREADY_ON_LATEST);
                return future;
            }

            if (latestMd5 == null) {
                future.complete(UpdateResult.ERROR);
                return future;
            }

            String latestDownload = jsonObject.getAsJsonPrimitive("url").getAsString();

            tryFetchNewUpdate(latestDownload, future);

            return future;
        } catch (IOException e) {
            WynntilsMod.error("Exception while trying to load new update.", e);
            future.complete(UpdateResult.ERROR);
            return future;
        }
    }

    private static String getCurrentMd5() {
        MD5Verification verification = new MD5Verification(WynntilsMod.getModJar());
        return verification.getMd5();
    }

    private static File getUpdateFile() {
        File updatesDir =
                new File(WynntilsMod.getModStorageDir(WYNTILLS_UPDATE_FOLDER).toURI());
        FileUtils.mkdir(updatesDir);
        return new File(updatesDir, WYNNTILS_UPDATE_FILE_NAME);
    }

    private static void tryFetchNewUpdate(String latestUrl, CompletableFuture<UpdateResult> future) {
        File oldJar = WynntilsMod.getModJar();
        File newJar = getUpdateFile();

        try {
            URL downloadUrl = new URL(latestUrl);
            InputStream in = downloadUrl.openStream();

            FileUtils.createNewFile(newJar);

            Files.copy(in, newJar.toPath(), StandardCopyOption.REPLACE_EXISTING);

            future.complete(UpdateResult.SUCCESSFUL);

            WynntilsMod.info("Successfully downloaded Wynntils update!");

            addShutdownHook(oldJar, newJar);
        } catch (IOException exception) {
            newJar.delete();
            future.complete(UpdateResult.ERROR);
            WynntilsMod.error("Exception when trying to download update!", exception);
        }
    }

    private static void addShutdownHook(File oldJar, File newJar) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (oldJar == null || !oldJar.exists() || oldJar.isDirectory()) {
                    WynntilsMod.warn("Mod jar file not found or incorrect.");
                    return;
                }

                FileUtils.copyFile(newJar, oldJar);
                newJar.delete();

                WynntilsMod.info("Successfully applied update!");
            } catch (IOException e) {
                WynntilsMod.error("Cannot apply update!", e);
            }
        }));
    }

    public enum UpdateResult {
        SUCCESSFUL,
        ALREADY_ON_LATEST,
        UPDATE_PENDING,
        ERROR
    }
}
