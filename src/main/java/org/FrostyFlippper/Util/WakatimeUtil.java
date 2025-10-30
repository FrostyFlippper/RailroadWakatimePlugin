package org.FrostyFlippper.Util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.railroadide.railroadpluginapi.services.ApplicationInfoService;
import org.FrostyFlippper.WakatimePlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class WakatimeUtil {
    public static String getLatestWakatimeVersion() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/wakatime/wakatime-cli/releases/latest"))
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            WakatimePlugin.getLogger().debug("Received {} status code from GitHub API", response.statusCode());

            JsonObject jsonObject = WakatimePlugin.GSON.fromJson(response.body(), JsonObject.class);
            if (jsonObject.has("tag_name")) {
                JsonElement tagNameElement = jsonObject.get("tag_name");
                if (tagNameElement.isJsonPrimitive()) {
                    JsonPrimitive tagNamePrimitive = tagNameElement.getAsJsonPrimitive();
                    if (tagNamePrimitive.isString()) {

                        WakatimePlugin.getLogger().debug("getLatestWakatimeVersion returns: " + tagNameElement.getAsString());
                        return tagNameElement.getAsString();
                    }
                }
            }

            WakatimePlugin.getLogger().debug("Unable to get the latest Wakatime version!");
            return null;
        } catch (IOException | InterruptedException exception) {
            WakatimePlugin.getLogger().error("Error getting latest Wakatime version!", exception);
            return null;
        }
    }

    public static Path downloadWakatimeCLI(String version, String osName, String architecture, Path path) {
        try {
            String fileName = "wakatime-cli-%s-%s.zip".formatted(osName, architecture);
            String url = "https://github.com/wakatime/wakatime-cli/releases/download/%s/%s".formatted(version, fileName);
            fileName = "wakatime-cli-%s.zip".formatted(version);
            Path filePath = path.resolve(fileName);

            InputStream inputStream = new URI(url).toURL().openStream();
            Files.createDirectories(path);
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

            WakatimePlugin.getLogger().debug("Downloaded Wakatime CLI to {}", filePath);
            return filePath;
        } catch (IOException | URISyntaxException exception) {
            WakatimePlugin.getLogger().error("Error downloading Wakatime CLI!", exception);
            return null;
        }
    }

    public static Path getWakatimeLocation() {
        final String wakatimeHome = System.getenv("WAKATIME_HOME");
        return wakatimeHome == null || wakatimeHome.isBlank() ?
                Path.of(System.getProperty("user.home")).resolve(".wakatime") :
                Path.of(wakatimeHome);
    }

    public static Path getWakatimeCliLocation() {
        String fileName = "wakatime-cli-%s-%s.exe".formatted(PlatformUtil.osname(), PlatformUtil.architecture());
        Path resolvedPath = getWakatimeLocation().resolve(fileName);
        WakatimePlugin.getLogger().debug("Wakatime CLI location set to {}", resolvedPath);
        return resolvedPath;
    }

    public static String getPluginString(ApplicationInfoService infoService, String pluginVersion) {
        return "%s/%s %s-wakatime/%s".formatted(infoService.getName(), infoService.getVersion(), infoService.getName(), pluginVersion);
    }
}
