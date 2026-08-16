package ua.bonfiremc.slc;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class RemoteLanguageData {
    public final String key;
    public final String hash;

    public final Path path;

    public RemoteLanguageData(String key, String hash) {
        this.key = key;
        this.hash = hash;

        this.path = LanguageLoader.LANGUAGES_DIR.resolve(this.key + ".json");
    }

    public void downloadAndWrite() {
        String subHash = this.hash.substring(0, 2);
        String url = "https://resources.download.minecraft.net/" + subHash + "/" + hash;

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url))
            .GET()
            .build();

        try {
            HttpResponse<Path> response = LanguageLoader.HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofFile(this.path));

            if (response.statusCode() != 200) {
                throw new RuntimeException("File loading error: HTTP " + response.statusCode());
            }
        } catch (Exception e) {
            SLC.LOGGER.error("Failed to download asset '{}'", this.hash, e);
        }
    }

    public String calculateDownloadedSHA1() {
        MessageDigest digest;

        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            SLC.LOGGER.error("Bruh, how?", e);
            return "";
        }

        try (InputStream is = Files.newInputStream(this.path)) {
            byte[] buffer = new byte[8192];
            int read;

            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException e) {
            SLC.LOGGER.error("Failed to calculate SHA1", e);
            return "";
        }

        StringBuilder hexString = new StringBuilder();

        for (byte b : digest.digest()) {
            hexString.append(String.format("%02x", b));
        }

        return hexString.toString();
    }
}
