package org.FrostyFlippper;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.railroadide.core.secure_storage.SecureTokenStore;
import dev.railroadide.railroadpluginapi.Plugin;
import dev.railroadide.railroadpluginapi.PluginContext;
import dev.railroadide.railroadpluginapi.events.FileEvent;
import dev.railroadide.railroadpluginapi.events.FileModifiedEvent;
import dev.railroadide.railroadpluginapi.services.ApplicationInfoService;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WakatimePlugin implements Plugin {
    private SecureTokenStore tokenStore;

    @Override
    public void onEnable(PluginContext context) {
        // 1. get wakatime location (first check WAKATIME_HOME environment variable), if not set then user home/.wakatime/
        Path wakatimeLocation = getWakatimeLocation();
        System.out.println(wakatimeLocation);

        ApplicationInfoService applicationInfoService = context.getService(ApplicationInfoService.class);

        // 2. then run checkMissingPlatformSupport, if it throws an exception, log and return
        try {
            checkMissingPlatformSupport();
        } catch (RuntimeException e) {
            throw new RuntimeException("Unsupported platform: " + osname() + "-" + architecture() + ". Please check the Wakatime documentation for supported platforms.", e);
        }

        // 3. find the latest cli version (https://api.github.com/repos/wakatime/wakatime-cli/releases/latest) (also add to-do for checking alpha)
        String latestVersion = getLatestWakatimeVersion();

        String osName = osname();
        String architecture = architecture();

        // 4. download the cli (https://github.com/wakatime/wakatime-cli/releases/download/THE_VERSION/wakatime-cli-OS_NAME-ARCHITECTURE.zip)
        // 5. download it to wakatime_location/wakatime-cli.zip (add to-do for proxy support)
        Path filePath = downloadWakatimeCLI(latestVersion, osName, architecture, wakatimeLocation);

        try {
            // 6. unzip the zip file
            FileUtil.unzipFile(filePath, wakatimeLocation);

            // 7. delete the old wakatime-cli.zip and the old unzipped version (if it exists)
            Files.delete(filePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 8. if not on windows, set the file to be executable
        if (!isWindows()) {
            wakatimeLocation.resolve("wakatime-cli-%s-%s".formatted(osName, architecture)).toFile().setExecutable(true);
        }

        // 9. add event listeners for file activation, saving and modifying
        context.getEventBus().subscribe(FileEvent.class, event -> {
            if (event.isActivated()) {

            } else if (event.isSaved()) {

            }
        });

        context.getEventBus().subscribe(FileModifiedEvent.class, event -> {

        });

        // 10. setup a queue of "heartbeat"s (https://github.com/wakatime/jetbrains-wakatime/blob/bf0e28dd706963eca2add7a793fb89d965302024/src/com/wakatime/intellij/plugin/Heartbeat.java#L13-L24)
        Queue<Heartbeat> heartbeatQueue = new ConcurrentLinkedQueue<>();

        // 11. have a thread that runs every 30 seconds
        String pluginVersion = context.getDescriptor().getVersion();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> runHeartbeat(heartbeatQueue, applicationInfoService, pluginVersion), 0, 30, TimeUnit.SECONDS);


        // 21. do this for each of the events
        //      1. check if app is focused (KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null)
        //      2. add a heartbeat to the heartbeat queue
    }


    public void runHeartbeat(Queue<Heartbeat> heartbeatQueue, ApplicationInfoService applicationInfoService, String currentVersion) {
        // [Start Thread]
        // 12. get api key from SecureTokenStore
        String retrievedApiKey = tokenStore.getToken("WakatimeApiKey");

        // 13. poll the queue to see if there's any heartbeats (if not, exit the thread)
        Heartbeat initialHeartbeat = heartbeatQueue.poll();
        if (initialHeartbeat == null)
            return;

        // 14. poll over the queue to find any additional heartbeats (if the heartbeat is null, stop)
        List<Heartbeat> additionalHeartbeats = new ArrayList<>();
        for (int i = 0; i < heartbeatQueue.size(); i++) {
            Heartbeat heartbeat = heartbeatQueue.poll();
            if (heartbeat == null)
                break;

            additionalHeartbeats.add(heartbeat);
        }

        // 15. create a command string for running the cli and adding the necessary arguments (https://github.com/wakatime/jetbrains-wakatime/blob/bf0e28dd706963eca2add7a793fb89d965302024/src/com/wakatime/intellij/plugin/WakaTime.java#L445-L504)
        String[] command = buildCliCommand(initialHeartbeat, retrievedApiKey, additionalHeartbeats, applicationInfoService, currentVersion);

        // 16. execute the command
        ProcessBuilder processBuilder = new ProcessBuilder(command);

        // 17. turn the extra heartbeats into a json string (array), containing all the heartbeat's data in each object


        // 18. write the json string to the command's output stream


        // 19. write a newline character


        // 20. flush and close the stream


        // [End Thread]
    }

    private static String[] buildCliCommand(Heartbeat heartbeat, String apiKey, List<Heartbeat> extraHeartbeats, ApplicationInfoService applicationInfoService, String currentVersion) {
        List<String> cmds = new ArrayList<>();
        cmds.add(getWakatimeCliLocation().toString());
        cmds.add("--plugin");
        String plugin = getPluginString(applicationInfoService, currentVersion);
        cmds.add(plugin);
        cmds.add("--entity");
        cmds.add(heartbeat.entity);
        cmds.add("--time");
        cmds.add(heartbeat.timestamp.toPlainString());
        if (!apiKey.isEmpty()) {
            cmds.add("--key");
            cmds.add(apiKey);
        }
        if (heartbeat.lineCount != null) {
            cmds.add("--lines-in-file");
            cmds.add(heartbeat.lineCount.toString());
        }
        if (heartbeat.lineNumber != null) {
            cmds.add("--lineno");
            cmds.add(heartbeat.lineNumber.toString());
        }
        if (heartbeat.cursorPosition != null) {
            cmds.add("--cursorpos");
            cmds.add(heartbeat.cursorPosition.toString());
        }
        if (heartbeat.project != null) {
            cmds.add("--alternate-project");
            cmds.add(heartbeat.project);
        }
        if (heartbeat.language != null) {
            cmds.add("--alternate-language");
            cmds.add(heartbeat.language);
        }
        if (heartbeat.isWrite)
            cmds.add("--write");
        if (heartbeat.isUnsavedFile)
            cmds.add("--is-unsaved-entity");
        if (heartbeat.isBuilding) {
            cmds.add("--category");
            cmds.add("building");
        }
        if (WakaTime.METRICS)
            cmds.add("--metrics");

        String proxy = getBuiltinProxy();
        if (proxy != null) {
            WakaTime.log.info("built-in proxy will be used: " + proxy);
            cmds.add("--proxy");
            cmds.add(proxy);
        }

        if (!extraHeartbeats.isEmpty())
            cmds.add("--extra-heartbeats");
        return cmds.toArray(new String[0]);
    }

    @Override
    public void onDisable(PluginContext context) {

    }

    private static Path getWakatimeLocation() {
        final String wakatimeHome = System.getenv("WAKATIME_HOME");
        if (wakatimeHome == null || wakatimeHome.isBlank()) {
            return Path.of(System.getProperty("user.home")).resolve(".wakatime");
        } else {
            return Path.of(wakatimeHome);
        }
    }

    private static Path getWakatimeCliLocation() {
        String fileName = "wakatime-cli-%s-%s.exe".formatted(osname(), architecture());
        return getWakatimeLocation().resolve(fileName);
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
            System.out.println(response.statusCode());
            System.out.println(response.body());
            JsonObject jsonObject = new Gson().fromJson(response.body(), JsonObject.class);

            if (jsonObject.has("tag_name")) {
                JsonElement tagNameElement = jsonObject.get("tag_name");
                if (tagNameElement.isJsonPrimitive()) {
                    JsonPrimitive tagNamePrimitive = tagNameElement.getAsJsonPrimitive();
                    if (tagNamePrimitive.isString()) {
                        return tagNameElement.getAsString();
                    }
                }
            }

            return null;

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
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

            return filePath;
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
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
        if (isWindows()) return "windows";
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) return "darwin";
        if (os.contains("linux")) return "linux";
        return os;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    public static String architecture() {
        String arch = System.getProperty("os.arch");
        if (arch.contains("386") || arch.contains("32")) return "386";
        if (arch.equals("aarch64")) return "arm64";
        if (osname().equals("darwin") && arch.contains("arm")) return "arm64";
        if (arch.contains("64")) return "amd64";
        return arch;
    }
}