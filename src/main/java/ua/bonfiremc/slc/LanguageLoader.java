package ua.bonfiremc.slc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.impl.resource.pack.ModNioPackResources;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.locale.DeprecatedTranslationsInfo;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.PackType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LanguageLoader {
    public static final Path LANGUAGES_DIR = Paths.get("server_languages");
    public static final Path CURRENT_FILE = LANGUAGES_DIR.resolve("_current.txt");

    public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private static final ModContainer MINECRAFT_CONTAINER = FabricLoader.getInstance().getModContainer("minecraft").get();
    private static final Pattern LANGUAGE_REGEX = Pattern.compile("^minecraft/lang/([^/]+)\\.json$");

    public static final LanguageLoader INSTANCE = new LanguageLoader();

    public final List<RemoteLanguageData> remoteLanguages;
    private String currentLanguage = Language.DEFAULT;

    private LanguageLoader() {
        this.remoteLanguages = fetchLanguages();

        try {
            if (!Files.exists(LANGUAGES_DIR)) Files.createDirectories(LANGUAGES_DIR);
            if (!Files.exists(CURRENT_FILE)) {
                Files.writeString(CURRENT_FILE, Language.DEFAULT);
            } else {
                this.currentLanguage = SLC.normalizeLang(Files.readString(CURRENT_FILE).trim());
            }
        } catch (IOException e) {
            SLC.LOGGER.error("Failed to load current language from file", e);
        }
    }

    public void inject(String language) {
        this.currentLanguage = language;

        this.setServerTranslationsAPILangYepIFuckingLoveThoseLongNames();
        Language.inject(this.createLanguage());

        this.updateFile();
    }

    public SLCLanguage createLanguage() {
        List<String> languages = new ArrayList<>();

        languages.add(Language.DEFAULT);

        if (!this.currentLanguage.equals(Language.DEFAULT)) {
            languages.add(this.currentLanguage);
        }

        Map<String, String> translations = new HashMap<>();

        for (String language : languages) {
            translations.putAll(this.getTranslations(language));
        }

        return new SLCLanguage(Map.copyOf(translations));
    }

    public Map<String, String> getTranslations(String language) {
        List<Path> paths = new ArrayList<>();

        if (language.equals(Language.DEFAULT)) {
            paths.add(
                MINECRAFT_CONTAINER
                    .findPath("/assets/minecraft/lang/" + Language.DEFAULT + ".json")
                    .orElseThrow()
            );
        } else {
            RemoteLanguageData remote = this.findRemoteData(language);

            if (remote == null) {
                throw new RuntimeException();
            }

            if (Files.notExists(remote.path) || !remote.calculateDownloadedSHA1().equals(remote.hash)) {
                remote.downloadAndWrite();
            }

            paths.add(remote.path);
        }

        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (mod.getMetadata().getType().equals("builtin")) continue;

            @SuppressWarnings("UnstableApiUsage")
            Map<PackType, Set<String>> map = ModNioPackResources.readNamespaces(mod.getRootPaths(), mod.getMetadata().getId());

            for (String ns : map.get(PackType.CLIENT_RESOURCES)) {
                mod.findPath(PackType.CLIENT_RESOURCES.getDirectory() + "/" + ns + "/lang/" + language + ".json")
                    .filter(Files::isRegularFile)
                    .ifPresent(paths::add);
            }
        }

        Map<String, String> translations = new HashMap<>();

        for (Path path : paths) {
            try (InputStream stream = Files.newInputStream(path)) {
                Language.loadFromJson(stream, translations::put);
            } catch (Exception e) {
                SLC.LOGGER.error("Failed to load from json", e);
            }
        }

        DeprecatedTranslationsInfo deprecatedInfo = DeprecatedTranslationsInfo.loadFromDefaultResource();
        deprecatedInfo.applyToMap(translations);

        return Map.copyOf(translations);
    }

    public RemoteLanguageData findRemoteData(String lang) {
        for (RemoteLanguageData language : this.remoteLanguages) {
            if (language.key.equals(lang)) {
                return language;
            }
        }
        return null;
    }

    public String getCurrentLanguage() {
        return this.currentLanguage;
    }

    public void setServerTranslationsAPILangYepIFuckingLoveThoseLongNames() {
        if (FabricLoader.getInstance().isModLoaded("server_translations_api")) {
            xyz.nucleoid.server.translations.impl.ServerTranslations instance = xyz.nucleoid.server.translations.impl.ServerTranslations.INSTANCE;

            instance.setSystemLanguage(instance.getLanguageDefinition(this.currentLanguage));
        }
    }

    private void updateFile() {
        try {
            Files.writeString(CURRENT_FILE, this.currentLanguage);
        } catch (IOException e) {
            SLC.LOGGER.error("Failed to write current language to file", e);
        }
    }

    private static List<RemoteLanguageData> fetchLanguages() {
        String manifestString = fetchString("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        JsonObject manifest = JsonParser.parseString(manifestString).getAsJsonObject();

        String versionDataUrl = null;

        String minecraftVersion = MINECRAFT_CONTAINER
            .getMetadata()
            .getVersion()
            .getFriendlyString();

        for (JsonElement el : manifest.getAsJsonArray("versions")) {
            JsonObject version = el.getAsJsonObject();

            if (version.get("id").getAsString().equals(minecraftVersion)) {
                versionDataUrl = version.get("url").getAsString();
                break;
            }
        }
        if (versionDataUrl == null) throw new RuntimeException(minecraftVersion + " version is not found");

        String versionDataString = fetchString(versionDataUrl);
        String assetIndexUrl = JsonParser.parseString(versionDataString).getAsJsonObject()
            .getAsJsonObject("assetIndex")
            .get("url")
            .getAsString();

        String assetIndexString = fetchString(assetIndexUrl);
        JsonObject objects = JsonParser.parseString(assetIndexString).getAsJsonObject()
            .getAsJsonObject("objects");

        List<RemoteLanguageData> list = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            Matcher matcher = LANGUAGE_REGEX.matcher(entry.getKey());

            if (matcher.matches()) {
                String language = matcher.group(1);
                String hash = entry.getValue()
                    .getAsJsonObject()
                    .get("hash")
                    .getAsString();

                list.add(new RemoteLanguageData(language, hash));
            }
        }

        return Collections.unmodifiableList(list);
    }

    private static String fetchString(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
            return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            SLC.LOGGER.error("Failed to fetch string from '{}'", url, e);
            return "";
        }
    }
}
