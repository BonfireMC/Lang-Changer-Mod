package org.example;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class langmanager {
    private static final Path DIR = Paths.get("serverlanguages");
    public static final Path CURRENTFILE = DIR.resolve("currentlang.txt");
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public static String getCurrentLanguage() {
        try {
            if (!Files.exists(DIR)) Files.createDirectories(DIR);
            if (!Files.exists(CURRENTFILE)) {
                setCurrentLanguage("uk_ua");
                return "uk_ua";
            }
            return Files.readString(CURRENTFILE).trim();
        } catch (IOException e) {
            return "uk_ua";
        }
    }

    public static void setCurrentLanguage(String lang) throws IOException {
        if (!Files.exists(DIR)) Files.createDirectories(DIR);
        Files.writeString(CURRENTFILE, lang);
    }

    public static Language loadAndCreateLanguage(String langCode) {
        Map<String, String> translations = new HashMap<>();

        try (InputStream is = Language.class.getResourceAsStream("/assets/minecraft/lang/en_us.json")) {
            if (is != null) {
                Language.loadFromJson(is, translations::put);
            }
        } catch (Exception e) {
            langchanger.LOGGER.info("Cannot load english (usa).");
        }

        if (!langCode.equals("en_us")) {
            try {
                Path langFile = DIR.resolve(langCode + ".json");
                String remoteHash = fetchRemoteHash(langCode);

                if (!Files.exists(langFile) || !calculateSHA1(langFile).equals(remoteHash)) {
                    langchanger.LOGGER.info("Uploading lang file of \"" + langCode + "\"...");
                    downloadAsset(remoteHash, langFile);
                }
                try (InputStream is = Files.newInputStream(langFile)) {
                    Language.loadFromJson(is, translations::put);
                }
            } catch (Exception e) {
                langchanger.LOGGER.info("Cannot load \"" + langCode + "\". English (usa) is still in charge.");
                e.printStackTrace();
            }
        }

        final Map<String, String> finalMap = Map.copyOf(translations);
        return new Language() {
            @Override
            public @NonNull String getOrDefault(@NonNull String key, @NonNull String fallback) {
                return finalMap.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(@NonNull String key) {
                return finalMap.containsKey(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public @NonNull FormattedCharSequence getVisualOrder(@NonNull FormattedText text) {
                return sink -> text.visit(
                        (style, string) -> StringDecomposer.iterateFormatted(string, style, sink) ? Optional.empty() : FormattedText.STOP_ITERATION, Style.EMPTY
                ).isPresent();
            }
        };
    }

    private static String fetchRemoteHash(String langCode) throws Exception {
        String mcVersion = FabricLoader.getInstance().getModContainer("minecraft").get().getMetadata().getVersion().getFriendlyString();
        String manifestJson = fetchString("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        JsonObject manifest = JsonParser.parseString(manifestJson).getAsJsonObject();

        String versionJsonUrl = null;
        for (JsonElement el : manifest.getAsJsonArray("versions")) {
            JsonObject version = el.getAsJsonObject();
            if (version.get("id").getAsString().equals(mcVersion)) {
                versionJsonUrl = version.get("url").getAsString();
                break;
            }
        }
        if (versionJsonUrl == null) throw new RuntimeException(mcVersion + "version is not found");

        String versionData = fetchString(versionJsonUrl);
        String assetIndexUrl = JsonParser.parseString(versionData).getAsJsonObject()
                .getAsJsonObject("assetIndex").get("url").getAsString();
        String assetIndexData = fetchString(assetIndexUrl);
        JsonObject objects = JsonParser.parseString(assetIndexData).getAsJsonObject().getAsJsonObject("objects");
        String assetPath = "minecraft/lang/" + langCode + ".json";

        if (!objects.has(assetPath)) throw new RuntimeException("Language \"" + langCode + "\" is not found in asset index.");
        return objects.getAsJsonObject(assetPath).get("hash").getAsString();
    }

    private static void downloadAsset(String hash, Path destination) throws Exception {
        String subHash = hash.substring(0, 2);
        String url = "https://resources.download.minecraft.net/" + subHash + "/" + hash;
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<Path> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(destination));

        if (response.statusCode() != 200) throw new RuntimeException("File loading error: HTTP " + response.statusCode());
    }

    private static String fetchString(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private static String calculateSHA1(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hexString = new StringBuilder();
        for (byte b : digest.digest()) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}