/*
 * Copyright © Wynntils 2022.
 * This file is released under AGPLv3. See LICENSE for full license details.
 */
package com.wynntils.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.wynntils.core.WynntilsMod;
import com.wynntils.core.features.Configurable;
import com.wynntils.core.features.Feature;
import com.wynntils.core.features.overlays.Overlay;
import com.wynntils.core.managers.CoreManager;
import com.wynntils.mc.objects.CustomColor;
import com.wynntils.mc.utils.McUtils;
import com.wynntils.utils.FileUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;

public final class ConfigManager extends CoreManager {
    private static final File CONFIGS = WynntilsMod.getModStorageDir("config");
    private static final String FILE_SUFFIX = ".conf.json";
    private static final List<ConfigHolder> CONFIG_HOLDERS = new ArrayList<>();
    private static File userConfig;
    private static final File defaultConfig = new File(CONFIGS, "default" + FILE_SUFFIX);
    private static JsonObject configObject;
    private static Gson gson;

    public static void registerFeature(Feature feature) {
        for (Overlay overlay : feature.getOverlays()) {
            registerConfigOptions(overlay);
        }

        registerConfigOptions(feature);
    }

    private static void registerConfigOptions(Configurable configurable) {
        List<ConfigHolder> configOptions = getConfigOptions(configurable);

        configurable.addConfigOptions(configOptions);
        loadConfigOptions(configOptions, false);
        CONFIG_HOLDERS.addAll(configOptions);
    }

    public static void init() {
        gson = new GsonBuilder()
                .registerTypeAdapter(CustomColor.class, new CustomColor.CustomColorSerializer())
                .setPrettyPrinting()
                .serializeNulls()
                .create();

        loadConfigFile();
    }

    public static void loadConfigFile() {
        // create config directory if necessary
        FileUtils.mkdir(CONFIGS);

        // set up config file based on uuid, load it if it exists
        userConfig = new File(CONFIGS, McUtils.mc().getUser().getUuid() + FILE_SUFFIX);
        if (!userConfig.exists()) {
            FileUtils.createNewFile(userConfig);
            configObject = new JsonObject();
            return;
        }

        try {
            InputStreamReader reader = new InputStreamReader(new FileInputStream(userConfig), StandardCharsets.UTF_8);
            JsonElement fileElement = JsonParser.parseReader(new JsonReader(reader));
            reader.close();
            if (!fileElement.isJsonObject()) {
                // invalid config file

                // Copy old config file to a backup, with a random part in the name to make sure we do not overwrite it
                FileUtils.copyFile(
                        userConfig,
                        new File(
                                CONFIGS,
                                "invalid_" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "_"
                                        + RandomStringUtils.random(5) + "_" + userConfig.getName()));
                FileUtils.deleteFile(userConfig);
                FileUtils.createNewFile(userConfig);
                configObject = new JsonObject();

                return;
            }

            configObject = fileElement.getAsJsonObject();
        } catch (IOException e) {
            WynntilsMod.error("Failed to load user config file!", e);

            configObject = new JsonObject();
        }
    }

    public static void loadAllConfigOptions(boolean resetIfNotFound) {
        loadConfigOptions(CONFIG_HOLDERS, resetIfNotFound);
    }

    public static void loadConfigOptions(List<ConfigHolder> holders, boolean resetIfNotFound) {
        if (configObject == null) {
            WynntilsMod.error("Tried to load configs when configObject is null.");
            return; // nothing to load from
        }

        for (ConfigHolder holder : holders) {
            // option hasn't been saved to config
            if (!configObject.has(holder.getJsonName())) {
                if (resetIfNotFound) {
                    holder.reset();
                }
                continue;
            }

            // read value and update option
            JsonElement holderJson = configObject.get(holder.getJsonName());
            Object value = gson.fromJson(holderJson, holder.getType());
            holder.setValue(value);
        }
    }

    public static void saveConfig() {
        try {
            // create file if necessary
            if (!userConfig.exists()) {
                FileUtils.createNewFile(userConfig);
            }

            // create json object, with entry for each option of each container
            JsonObject holderJson = new JsonObject();
            for (ConfigHolder holder : CONFIG_HOLDERS) {
                if (!holder.valueChanged()) continue; // only save options that have been set by the user
                Object value = holder.getValue();

                JsonElement holderElement = gson.toJsonTree(value);
                holderJson.add(holder.getJsonName(), holderElement);
            }

            // write json to file
            OutputStreamWriter fileWriter =
                    new OutputStreamWriter(new FileOutputStream(userConfig), StandardCharsets.UTF_8);
            gson.toJson(holderJson, fileWriter);
            fileWriter.close();
        } catch (IOException e) {
            WynntilsMod.error("Failed to save user config file!", e);
        }
    }

    public static void saveDefaultConfig() {
        try {
            // create file if necessary
            if (!defaultConfig.exists()) {
                FileUtils.createNewFile(defaultConfig);
            }

            // create json object, with entry for each option of each container
            JsonObject holderJson = new JsonObject();
            for (ConfigHolder holder : CONFIG_HOLDERS) {
                Object value = holder.getValue();

                JsonElement holderElement = gson.toJsonTree(value);
                holderJson.add(holder.getJsonName(), holderElement);
            }

            // write json to file
            OutputStreamWriter fileWriter =
                    new OutputStreamWriter(new FileOutputStream(defaultConfig), StandardCharsets.UTF_8);
            gson.toJson(holderJson, fileWriter);
            fileWriter.close();
            WynntilsMod.info("Default config file created with " + holderJson.size() + " config values.");
        } catch (IOException e) {
            WynntilsMod.error("Failed to save user config file!", e);
        }
    }

    private static Type findFieldTypeOverride(Configurable parent, Field configField) {
        Optional<Field> typeField = Arrays.stream(
                        FieldUtils.getFieldsWithAnnotation(parent.getClass(), TypeOverride.class))
                .filter(field ->
                        field.getType() == Type.class && field.getName().equals(configField.getName() + "Type"))
                .findFirst();

        if (typeField.isPresent()) {
            try {
                return (Type) FieldUtils.readField(typeField.get(), parent, true);
            } catch (IllegalAccessException e) {
                WynntilsMod.error("Unable to get field " + typeField.get().getName(), e);
            }
        }

        return null;
    }

    private static List<ConfigHolder> getConfigOptions(Configurable parent) {
        List<ConfigHolder> options = new ArrayList<>();

        for (Field configField : FieldUtils.getFieldsWithAnnotation(parent.getClass(), Config.class)) {
            Config metadata = configField.getAnnotation(Config.class);

            Type typeOverride = findFieldTypeOverride(parent, configField);

            ConfigHolder configHolder = new ConfigHolder(parent, configField, metadata, typeOverride);
            if (WynntilsMod.isDevelopmentEnvironment()) {
                if (metadata.visible()) {
                    if (configHolder.getDisplayName().startsWith("feature.wynntils.")) {
                        WynntilsMod.error("Config displayName i18n is missing for " + configHolder.getDisplayName());
                        throw new RuntimeException();
                    }
                    if (configHolder.getDescription().startsWith("feature.wynntils.")) {
                        WynntilsMod.error("Config description i18n is missing for " + configHolder.getDescription());
                        throw new RuntimeException();
                    }
                    if (configHolder.getDescription().isEmpty()) {
                        WynntilsMod.error("Config description is empty for " + configHolder.getDisplayName());
                        throw new RuntimeException();
                    }
                }
            }
            options.add(configHolder);
        }
        return options;
    }

    public static Gson getGson() {
        return gson;
    }

    public static List<ConfigHolder> getConfigHolders() {
        return CONFIG_HOLDERS;
    }
}
