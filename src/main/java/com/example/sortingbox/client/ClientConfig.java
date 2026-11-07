package com.example.sortingbox.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Client-side display settings, editable from the Mod Menu config screen.
 */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("sortingbox").resolve("client.json");

    /** Keep label names visible without holding the overlay key. */
    public static boolean alwaysShowNames = true;

    /** Where label text is drawn: "above" the block or "front" of it (towards the player). */
    public static String labelPlacement = "above";

    /** Text size written into the automatically placed category sign (mishanguc default is 8.0). */
    public static float signTextSize = 8.0f;

    /** Use OcelotSignMod wall signs instead of floating labels when available. */
    public static boolean signIntegration = true;

    private ClientConfig() {
    }

    public static void load() {
        if (!Files.exists(FILE)) {
            save();
            return;
        }
        try {
            String text = Files.readString(FILE, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (root.has("alwaysShowNames")) {
                alwaysShowNames = root.get("alwaysShowNames").getAsBoolean();
            }
            if (root.has("signIntegration")) {
                signIntegration = root.get("signIntegration").getAsBoolean();
            }
            if (root.has("signTextSize")) {
                signTextSize = root.get("signTextSize").getAsFloat();
            }
            if (root.has("labelPlacement")) {
                String value = root.get("labelPlacement").getAsString();
                if ("above".equals(value) || "front".equals(value)) {
                    labelPlacement = value;
                }
            }
        } catch (Exception exception) {
            save();
        }
    }

    public static void save() {
        JsonObject root = new JsonObject();
        root.addProperty("alwaysShowNames", alwaysShowNames);
        root.addProperty("labelPlacement", labelPlacement);
        root.addProperty("signTextSize", signTextSize);
        root.addProperty("signIntegration", signIntegration);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            // ignore
        }
    }
}