package org.FrostyFlippper;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.railroadide.railroadpluginapi.Plugin;
import dev.railroadide.railroadpluginapi.PluginContext;
import dev.railroadide.railroadpluginapi.events.FileEvent;
import dev.railroadide.railroadpluginapi.events.FileModifiedEvent;

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
import java.util.Arrays;
import java.util.Queue;

public class WakatimePlugin implements Plugin {

    @Override
    public void onEnable(PluginContext context) {
        // 1. get wakatime location (first check WAKATIME_HOME environment variable), if not set then user home/.wakatime/
        Path wakatimeLocation = getWakatimeLocation();
        System.out.println(wakatimeLocation);
        // 2. then run checkMissingPlatformSupport, if it throws an exception, log and return
        try{
            checkMissingPlatformSupport();
        } catch (RuntimeException e){
            throw new RuntimeException("Unsupported platform: " + osname() + "-" + architecture() + ". Please check the Wakatime documentation for supported platforms.", e);
        }
        // 3. find the latest cli version (https://api.github.com/repos/wakatime/wakatime-cli/releases/latest) (also add to-do for checking alpha)
        String latestVersion = getLatestWakatimeVersion();
        String osName = osname();
        String architecture = architecture();
        // 4. download the cli (https://github.com/wakatime/wakatime-cli/releases/download/THE_VERSION/wakatime-cli-OS_NAME-ARCHITECTURE.zip)
        // 5. download it to wakatime_location/wakatime-cli.zip (add to-do for proxy support)
        Path filePath = downloadWakatimeCLI(latestVersion, osName, architecture, wakatimeLocation);
        // 6. unzip the zip file
        // 7. delete the old wakatime-cli.zip and the old unzipped version (if it exists)
        try{
            FileUtil.unzipFile(filePath, wakatimeLocation);
            Files.delete(filePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 8. if not on windows, set the file to be executable
        if(!isWindows()) {
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
        Queue<Heart>
        // 11. have a thread that runs every 30 seconds
        // [Start Thread]
        // 12. get api key from wakatime config
        // 13. poll the queue to see if theres any heartbeats (if not, exit the thread)
        // 14. loop over the queue to find any additional heartbeats
        // 15. create a command string for running the cli and adding the necessary arguments (https://github.com/wakatime/jetbrains-wakatime/blob/bf0e28dd706963eca2add7a793fb89d965302024/src/com/wakatime/intellij/plugin/WakaTime.java#L445-L504)
        // 16. execute the command
        // 17. turn the extra heartbeats into a json string (array), containing all the heartbeat's data in each object
        // 18. write the json string to the command's output stream
        // 19. write a newline character
        // 20. flush and close the stream

        // 21. do this for each of the events
        //      1. check if app is focused (KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != null)
        //      2. add a heartbeat to the heartbeat queue
    }

    @Override
    public void onDisable(PluginContext context) {

    }

    private static Path getWakatimeLocation(){
        final String wakatimeHome = System.getenv("WAKATIME_HOME");
        if(wakatimeHome == null || wakatimeHome.isBlank()){
            return Path.of(System.getProperty("user.home")).resolve(".wakatime");
        } else {
            return Path.of(wakatimeHome);
        }
    }

    private static String getLatestWakatimeVersion(){
        try(HttpClient client = HttpClient.newHttpClient()){
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/wakatime/wakatime-cli/releases/latest"))
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(response.statusCode());
            System.out.println(response.body());
            JsonObject jsonObject = new Gson().fromJson(response.body(), JsonObject.class);

            if(jsonObject.has("tag_name")){
                JsonElement tagNameElement = jsonObject.get("tag_name");
                if (tagNameElement.isJsonPrimitive()) {
                    JsonPrimitive tagNamePrimitive = tagNameElement.getAsJsonPrimitive();
                    if (tagNamePrimitive.isString()) {
                        return tagNameElement.getAsString();
                    }
                }
            }

            return null;

        } catch (IOException | InterruptedException e){
            throw new RuntimeException(e);
        }
    }

    private static Path downloadWakatimeCLI(String version, String osName, String architecture, Path path){
        try{
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