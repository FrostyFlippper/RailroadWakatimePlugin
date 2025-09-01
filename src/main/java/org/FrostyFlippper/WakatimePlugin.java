package org.FrostyFlippper;

import com.google.gson.*;
import dev.railroadide.core.gson.GsonLocator;
import dev.railroadide.core.registry.Registry;
import dev.railroadide.core.secure_storage.SecureTokenStore;
import dev.railroadide.core.settings.DefaultSettingCodecs;
import dev.railroadide.core.settings.Setting;
import dev.railroadide.core.settings.SettingCategory;
import dev.railroadide.logger.Logger;
import dev.railroadide.railroadpluginapi.Plugin;
import dev.railroadide.railroadpluginapi.PluginContext;
import dev.railroadide.railroadpluginapi.Registries;
import dev.railroadide.railroadpluginapi.dto.Document;
import dev.railroadide.railroadpluginapi.events.FileEvent;
import dev.railroadide.railroadpluginapi.events.FileModifiedEvent;
import dev.railroadide.railroadpluginapi.services.ApplicationInfoService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WakatimePlugin implements Plugin {
    private static final Gson GSON = GsonLocator.getInstance();
    private static Logger logger;

    private static final SecureTokenStore TOKEN_STORE = new SecureTokenStore("WakatimePlugin");
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);

    private Setting<String> proxySetting;
    private Setting<Boolean> doesShowInStatusBarSetting;
    private Setting<Boolean> isDebugSetting;

    @Override
    public void onEnable(PluginContext context) {
        logger = context.getLogger();

        Registry<Setting<?>> settingRegistry = Registries.getSettingsRegistry(context);
        proxySetting = Setting.builder(String.class, "wakatime:proxy")
                .treePath("plugins.wakatime")
                .title("wakatime.proxy.title")
                .description("wakatime.proxy.description")
                .codec(DefaultSettingCodecs.STRING)
                .category(SettingCategory.builder("wakatime:category")
                        .title("wakatime.category.title")
                        .noDescription().build())
                .defaultValue("")
                .build();

        doesShowInStatusBarSetting = Setting.builder(Boolean.class, "wakatime:does_show_in_status_bar")
                .treePath("plugins.wakatime")
                .title("wakatime.does_show_in_status_bar.title")
                .description("wakatime.does_show_in_status_bar.description")
                .codec(DefaultSettingCodecs.BOOLEAN)
                .category(SettingCategory.builder("wakatime:category")
                        .title("wakatime.category.title")
                        .noDescription().build())
                .defaultValue(true)
                .build();

        isDebugSetting = Setting.builder(Boolean.class, "wakatime:is_debug")
                .treePath("plugins.wakatime")
                .title("wakatime.is_debug.title")
                .description("wakatime.is_debug.description")
                .codec(DefaultSettingCodecs.BOOLEAN)
                .category(SettingCategory.builder("wakatime:category")
                        .title("wakatime.category.title")
                        .noDescription().build())
                .defaultValue(false)
                .build();

        settingRegistry.register(proxySetting.getId(), proxySetting);
        context.getLogger().info("Setting '" + proxySetting.getId() + "' registered.");

        settingRegistry.register(doesShowInStatusBarSetting.getId(), doesShowInStatusBarSetting);
        context.getLogger().info("Setting '" + doesShowInStatusBarSetting.getId() + "' registered.");

        settingRegistry.register(isDebugSetting.getId(), isDebugSetting);
        context.getLogger().info("Setting '" + isDebugSetting.getId() + "' registered.");


        Path wakatimeLocation = getWakatimeLocation();
        logger.debug("Wakatime location set to " + wakatimeLocation.toString());

        try {
            checkMissingPlatformSupport();
        } catch (RuntimeException exception) {
            logger.error("Unsupported platform: {}-{}. Please check the Wakatime documentation for supported platforms.", osname(), architecture(), exception);
            return;
        }

        String latestVersion = getLatestWakatimeVersion();
        if (latestVersion == null) {
            logger.error("Unable to get the latest Wakatime version!");
            return;
        }

        logger.debug("Wakatime CLI latest version: {}", latestVersion);

        String osName = osname();
        logger.debug("OS name: {}", osName);

        String architecture = architecture();
        logger.debug("Architecture: {}", architecture);

        // TODO: filePath might be null
        Path filePath = downloadWakatimeCLI(latestVersion, osName, architecture, wakatimeLocation);
        logger.debug("File path: {}", filePath);

        try {
            FileUtil.unzipFile(filePath, wakatimeLocation);
            Files.delete(filePath);
        } catch (IOException exception) {
            logger.error("Error unzipping Wakatime CLI!", exception);
            return;
        }

        if (!isWindows()) {
            wakatimeLocation.resolve("wakatime-cli-%s-%s".formatted(osName, architecture)).toFile().setExecutable(true);
        }

        Queue<Heartbeat> heartbeatQueue = new ConcurrentLinkedQueue<>();
        addEventListeners(context, heartbeatQueue);

        String pluginVersion = context.getDescriptor().getVersion();
        ApplicationInfoService applicationInfoService = context.getService(ApplicationInfoService.class);
        SCHEDULER.scheduleAtFixedRate(() -> runHeartbeatQueue(heartbeatQueue, applicationInfoService, pluginVersion), 0, 30, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable(PluginContext context) {
        Registry<Setting<?>> settingRegistry = Registries.getSettingsRegistry(context);
        try {
            if (proxySetting != null) {
                settingRegistry.unregister(proxySetting.getId());
                context.getLogger().info("Setting '" + proxySetting.getId() + "' unregistered.");
            }

            if (doesShowInStatusBarSetting != null) {
                settingRegistry.unregister(doesShowInStatusBarSetting.getId());
                context.getLogger().info("Setting '" + doesShowInStatusBarSetting.getId() + "' unregistered.");
            }

            if (isDebugSetting != null) {
                settingRegistry.unregister(isDebugSetting.getId());
                context.getLogger().info("Setting '" + isDebugSetting.getId() + "' unregistered.");
            }
        } catch (Exception exception) {
            context.getLogger().warn("Failed to unregister setting", exception);
        }
    }

    public void addEventListeners(PluginContext context, Queue<Heartbeat> heartbeatQueue) {
        context.getEventBus().subscribe(FileEvent.class, event -> {
            if (event.isActivated()) {
                Document file = event.file();
                logger.debug("File {} activated", file.getPath().toString());

                heartbeatQueue.add(new Heartbeat.Builder()
                        .setEntity(file.getPath().toString())
                        .setLineCount((int) file.getContentAsString().lines().count())
                        // TODO: requires railroad implementation
                        //.setLineNumber()
                        //.setCursorPosition()
                        .setTimestamp(getCurrentTimestamp())
                        .setWrite(true)
                        // TODO: requires railroad implementation
                        .setUnsavedFile(false)
                        //.setProject()
                        //.setLanguage()
                        .setBuilding(false)
                        .build());
            } else if (event.isSaved()) {
                Document file = event.file();
                heartbeatQueue.add(new Heartbeat.Builder()
                        .setEntity(file.getPath().toString())
                        .setLineCount((int) file.getContentAsString().lines().count())
                        // TODO: requires railroad implementation
                        //.setLineNumber()
                        //.setCursorPosition()
                        .setTimestamp(getCurrentTimestamp())
                        .setWrite(true)
                        // TODO: requires railroad implementation
                        .setUnsavedFile(true)
                        //.setProject()
                        //.setLanguage()
                        .setBuilding(false)
                        .build());
            }
        });

        context.getEventBus().subscribe(FileModifiedEvent.class, event -> {
            Document file = event.file();
            heartbeatQueue.add(new Heartbeat.Builder()
                    .setEntity(file.getPath().toString())
                    .setLineCount((int) file.getContentAsString().lines().count())
                    // TODO: requires railroad implementation
                    //.setLineNumber()
                    //.setCursorPosition()
                    .setTimestamp(getCurrentTimestamp())
                    .setWrite(true)
                    // TODO: requires railroad implementation
                    .setUnsavedFile(false)
                    //.setProject()
                    //.setLanguage()
                    .setBuilding(false)
                    .build());
        });
    }

    private static BigDecimal getCurrentTimestamp() {
        return new BigDecimal((System.currentTimeMillis() / 1000.0)).setScale(4, RoundingMode.HALF_UP);
    }

    public void runHeartbeatQueue(Queue<Heartbeat> heartbeatQueue, ApplicationInfoService applicationInfoService, String currentVersion) {
        String retrievedApiKey = TOKEN_STORE.getToken("WakatimeApiKey");

        Heartbeat initialHeartbeat = heartbeatQueue.poll();
        if (initialHeartbeat == null)
            return;

        List<Heartbeat> additionalHeartbeats = new ArrayList<>();
        for (int i = 0; i < heartbeatQueue.size(); i++) {
            Heartbeat heartbeat = heartbeatQueue.poll();
            if (heartbeat == null)
                break;

            additionalHeartbeats.add(heartbeat);
        }

        logger.debug("Found {} additional heartbeats!", additionalHeartbeats.size());

        String[] command = buildCliCommand(initialHeartbeat, retrievedApiKey, additionalHeartbeats, applicationInfoService, currentVersion);

        Process process;
        try {
            process = new ProcessBuilder(command).start();
        } catch (IOException exception) {
            logger.error("Error starting Wakatime CLI process!", exception);
            return;
        }

        var jsonArray = new JsonArray();
        for (Heartbeat heartbeat : additionalHeartbeats) {
            jsonArray.add(GSON.toJsonTree(heartbeat));
        }

        try {
            OutputStream outputStream = process.getOutputStream();
            outputStream.write(GSON.toJson(jsonArray).getBytes(StandardCharsets.UTF_8));

            outputStream.write("\n".getBytes());

            outputStream.flush();
            outputStream.close();
        } catch (IOException exception) {
            logger.error("Error writing to Wakatime CLI process!", exception);
        }
    }

    private String[] buildCliCommand(Heartbeat heartbeat, String apiKey, List<Heartbeat> extraHeartbeats, ApplicationInfoService applicationInfoService, String currentVersion) {
        List<String> cmds = new ArrayList<>();
        cmds.add(getWakatimeCliLocation().toString());

        cmds.add("--plugin");
        String plugin = getPluginString(applicationInfoService, currentVersion);
        cmds.add(plugin);

        cmds.add("--entity");
        cmds.add(heartbeat.getEntity());

        cmds.add("--time");
        cmds.add(heartbeat.getTimestamp().toPlainString());

        if (!apiKey.isEmpty()) {
            cmds.add("--key");
            cmds.add(apiKey);
        }

        if (heartbeat.getLineCount() != null) {
            cmds.add("--lines-in-file");
            cmds.add(heartbeat.getLineCount().toString());
        }

        if (heartbeat.getLineNumber() != null) {
            cmds.add("--lineno");
            cmds.add(heartbeat.getLineNumber().toString());
        }

        if (heartbeat.getCursorPosition() != null) {
            cmds.add("--cursorpos");
            cmds.add(heartbeat.getCursorPosition().toString());
        }

        if (heartbeat.getProject() != null) {
            cmds.add("--alternate-project");
            cmds.add(heartbeat.getProject());
        }

        if (heartbeat.getLanguage() != null) {
            cmds.add("--alternate-language");
            cmds.add(heartbeat.getLanguage());
        }

        if (heartbeat.isWrite()) {
            cmds.add("--write");
        }

        if (heartbeat.isUnsavedFile()) {
            cmds.add("--is-unsaved-entity");
        }

        if (heartbeat.isBuilding()) {
            cmds.add("--category");
            cmds.add("building");
        }

        if(Boolean.TRUE.equals(isDebugSetting.getValue())){
            cmds.add("--verbose");
        }

        String proxy = proxySetting.getValue();
        if (proxy != null) {
            logger.debug("built-in proxy will be used: {}", proxy);
            cmds.add("--proxy");
            cmds.add(proxy);
        }

        if (!extraHeartbeats.isEmpty()) {
            cmds.add("--extra-heartbeats");
        }

        return cmds.toArray(new String[0]);
    }

    private static Path getWakatimeLocation() {
        final String wakatimeHome = System.getenv("WAKATIME_HOME");
        return wakatimeHome == null || wakatimeHome.isBlank() ?
                Path.of(System.getProperty("user.home")).resolve(".wakatime") :
                Path.of(wakatimeHome);
    }

    private static Path getWakatimeCliLocation() {
        String fileName = "wakatime-cli-%s-%s.exe".formatted(osname(), architecture());
        Path resolvedPath = getWakatimeLocation().resolve(fileName);
        logger.debug("Wakatime CLI location set to {}", resolvedPath);
        return resolvedPath;
    }

    private static String getPluginString(ApplicationInfoService infoService, String pluginVersion) {
        return "%s/%s %s-wakatime/%s".formatted(infoService.getName(), infoService.getVersion(), infoService.getName(), pluginVersion);
    }

    private static String getLatestWakatimeVersion() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/wakatime/wakatime-cli/releases/latest"))
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.debug("Received {} status code from GitHub API", response.statusCode());

            JsonObject jsonObject = GSON.fromJson(response.body(), JsonObject.class);
            if (jsonObject.has("tag_name")) {
                JsonElement tagNameElement = jsonObject.get("tag_name");
                if (tagNameElement.isJsonPrimitive()) {
                    JsonPrimitive tagNamePrimitive = tagNameElement.getAsJsonPrimitive();
                    if (tagNamePrimitive.isString()) {
                        logger.debug("getLatestWakatimeVersion returns: " + tagNameElement.getAsString());
                        return tagNameElement.getAsString();
                    }
                }
            }

            logger.debug("Unable to get the latest Wakatime version!");
            return null;
        } catch (IOException | InterruptedException exception) {
            logger.error("Error getting latest Wakatime version!", exception);
            return null;
        }
    }

    private static Path downloadWakatimeCLI(String version, String osName, String architecture, Path path) {
        try {
            String fileName = "wakatime-cli-%s-%s.zip".formatted(osName, architecture);
            String url = "https://github.com/wakatime/wakatime-cli/releases/download/%s/%s".formatted(version, fileName);
            fileName = "wakatime-cli-%s.zip".formatted(version);
            Path filePath = path.resolve(fileName);

            InputStream inputStream = new URI(url).toURL().openStream();
            Files.createDirectories(path);
            Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

            logger.debug("Downloaded Wakatime CLI to {}", filePath);
            return filePath;
        } catch (IOException | URISyntaxException exception) {
            logger.error("Error downloading Wakatime CLI!", exception);
            return null;
        }
    }

    private static void checkMissingPlatformSupport() {
        String osname = osname();
        String arch = architecture();

        String[] validCombinations = {
                "darwin-amd64",
                "darwin-arm64",
                "freebsd-386",
                "freebsd-amd64",
                "freebsd-arm",
                "linux-386",
                "linux-amd64",
                "linux-arm",
                "linux-arm64",
                "netbsd-386",
                "netbsd-amd64",
                "netbsd-arm",
                "openbsd-386",
                "openbsd-amd64",
                "openbsd-arm",
                "openbsd-arm64",
                "windows-386",
                "windows-amd64",
                "windows-arm64",
        };

        if (!Arrays.asList(validCombinations).contains(osname + "-" + arch))
            throw new RuntimeException("OS not supported!");
    }

    public static String osname() {
        if (isWindows())
            return "windows";

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac") || os.contains("darwin"))
            return "darwin";

        if (os.contains("linux"))
            return "linux";

        return os;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    public static String architecture() {
        String arch = System.getProperty("os.arch");
        if (arch.contains("386") || arch.contains("32"))
            return "386";

        if (arch.equals("aarch64"))
            return "arm64";

        if (osname().equals("darwin") && arch.contains("arm"))
            return "arm64";

        if (arch.contains("64"))
            return "amd64";

        return arch;
    }
}