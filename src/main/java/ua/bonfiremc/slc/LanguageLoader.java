package ua.bonfiremc.slc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.impl.resource.ServerLanguageUtil;
import net.fabricmc.fabric.impl.resource.pack.ModNioPackResources;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.locale.DeprecatedTranslationsInfo;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.server.packs.PackType;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LanguageLoader {
    public static final Path DIR = Paths.get("server_languages");
    public static final Path CURRENT_FILE = DIR.resolve("_current.txt");

    public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private static final ModContainer MINECRAFT_CONTAINER = FabricLoader.getInstance().getModContainer("minecraft").get();
    private static final Pattern LANGUAGE_REGEX = Pattern.compile("^minecraft/lang/([^/]+)\\.json$");

    public final List<ServerLanguage> availableLanguages;

    private ServerLanguage currentLanguage = null;

    public static final LanguageLoader INSTANCE = new LanguageLoader();

    private LanguageLoader() {
        this.availableLanguages = fetchLanguages();

        try {
            if (!Files.exists(DIR)) Files.createDirectories(DIR);
            if (!Files.exists(CURRENT_FILE)) {
                Files.writeString(CURRENT_FILE, "en_us");
            } else {
                String languageKey = SLC.normalizeLang(Files.readString(CURRENT_FILE).trim());

                if (!languageKey.equals("en_us")) {
                    for (ServerLanguage language : this.availableLanguages) {
                        if (language.key.equals(languageKey)) {
                            this.currentLanguage = language;
                            break;
                        }
                    }
                }
            }
        } catch (IOException e) {
            SLC.LOGGER.error("Failed to load current language from file", e);
        }
    }

    public void inject(ServerLanguage language) {
        this.currentLanguage = language;

        this.writeToFile(language == null ? "en_us" : language.key);

        Language.inject(this.createLanguage());
    }

    public Language createLanguage() {
        if (this.currentLanguage == null) {
            Path path = MINECRAFT_CONTAINER.findPath("/assets/minecraft/lang/en_us.json").orElseThrow();

            try (InputStream stream = Files.newInputStream(path)) {
                return streamToLanguage(stream);
            } catch (Exception e) {
                SLC.LOGGER.error("Failed to load default language", e);
            }
        } else {
            if (Files.notExists(this.currentLanguage.path) || !this.currentLanguage.calculateSHA1().equals(this.currentLanguage.hash)) {
                this.currentLanguage.download();
            }

            try (InputStream stream = Files.newInputStream(this.currentLanguage.path)) {
                return streamToLanguage(stream);
            } catch (Exception e) {
                SLC.LOGGER.error("Failed to load server language", e);
            }
        }
        throw new RuntimeException("Failed to create language");
    }

    public ServerLanguage findLanguage(String lang) {
        for (ServerLanguage language : this.availableLanguages) {
            if (language.key.equals(lang)) {
                return language;
            }
        }
        return null;
    }

    public ServerLanguage getCurrentLanguage() {
        return this.currentLanguage;
    }

    private void writeToFile(String content) {
        try {
            Files.writeString(CURRENT_FILE, content);
        } catch (IOException e) {
            SLC.LOGGER.error("Failed to write current language to file", e);
        }
    }

    private static Language streamToLanguage(InputStream stream) {
        Map<String, String> translations = new HashMap<>();

        Language.loadFromJson(stream, translations::put);

        DeprecatedTranslationsInfo deprecatedInfo = DeprecatedTranslationsInfo.loadFromDefaultResource();
        deprecatedInfo.applyToMap(translations);

        for (Path path : getModLanguageFiles()) {
            try (InputStream is = Files.newInputStream(path)) {
                Language.loadFromJson(is, translations::put);
            } catch (Exception e) {
                SLC.LOGGER.error("Failed to load mod translations", e);
            }
        }

        Map<String, String> storage = Map.copyOf(translations);

        return new Language() {
            public @NonNull String getOrDefault(@NonNull String elementId, @NonNull String defaultValue) {
                return storage.getOrDefault(elementId, defaultValue);
            }

            public boolean has(@NonNull String elementId) {
                return storage.containsKey(elementId);
            }

            public boolean isDefaultRightToLeft() {
                return false;
            }

            public @NonNull FormattedCharSequence getVisualOrder(@NonNull FormattedText logicalOrderText) {
                return (output) -> logicalOrderText.visit((style, contents) -> StringDecomposer.iterateFormatted(contents, style, output) ? Optional.empty() : FormattedText.STOP_ITERATION, Style.EMPTY).isPresent();
            }
        };
    }

    private static Collection<Path> getModLanguageFiles() {
        Set<Path> paths = new LinkedHashSet<>();

        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (mod.getMetadata().getType().equals("builtin")) continue;

            @SuppressWarnings("UnstableApiUsage")
            Map<PackType, Set<String>> map = ModNioPackResources.readNamespaces(mod.getRootPaths(), mod.getMetadata().getId());

            for (String ns : map.get(PackType.CLIENT_RESOURCES)) {
                mod.findPath(PackType.CLIENT_RESOURCES.getDirectory() + "/" + ns + "/lang/" + Language.DEFAULT + ".json")
                    .filter(Files::isRegularFile)
                    .ifPresent(paths::add);

                if (LanguageLoader.INSTANCE.currentLanguage != null) {
                    mod.findPath(PackType.CLIENT_RESOURCES.getDirectory() + "/" + ns + "/lang/" + LanguageLoader.INSTANCE.currentLanguage.key + ".json")
                        .filter(Files::isRegularFile)
                        .ifPresent(paths::add);
                }
            }
        }

        return Collections.unmodifiableCollection(paths);
    }

    private static List<ServerLanguage> fetchLanguages() {
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

        List<ServerLanguage> list = new ArrayList<>();

        for (Map.Entry<String, JsonElement> entry : objects.entrySet()) {
            Matcher matcher = LANGUAGE_REGEX.matcher(entry.getKey());

            if (matcher.matches()) {
                String language = matcher.group(1);
                String hash = entry.getValue()
                    .getAsJsonObject()
                    .get("hash")
                    .getAsString();

                list.add(new ServerLanguage(language, hash));
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
