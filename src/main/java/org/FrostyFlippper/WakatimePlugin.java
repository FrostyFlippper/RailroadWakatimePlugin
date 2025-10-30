package org.FrostyFlippper;

import com.google.gson.*;
import dev.railroadide.core.gson.GsonLocator;
import dev.railroadide.core.localization.LocalizationService;
import dev.railroadide.core.localization.LocalizationServiceLocator;
import dev.railroadide.core.registry.Registry;
import dev.railroadide.core.secure_storage.SecureTokenStore;
import dev.railroadide.core.settings.Setting;
import dev.railroadide.core.ui.localized.LocalizedButton;
import dev.railroadide.core.ui.localized.LocalizedLabel;
import dev.railroadide.core.ui.localized.LocalizedText;
import dev.railroadide.core.ui.localized.LocalizedTextField;
import dev.railroadide.logger.Logger;
import dev.railroadide.railroadpluginapi.Plugin;
import dev.railroadide.railroadpluginapi.PluginContext;
import dev.railroadide.railroadpluginapi.Registries;
import dev.railroadide.railroadpluginapi.dto.Document;
import dev.railroadide.railroadpluginapi.event.EventBus;
import dev.railroadide.railroadpluginapi.events.FileEvent;
import dev.railroadide.railroadpluginapi.events.FileModifiedEvent;
import dev.railroadide.railroadpluginapi.events.ProjectEvent;
import dev.railroadide.railroadpluginapi.services.ApplicationInfoService;
import dev.railroadide.railroadpluginapi.services.DocumentEditorStateService;
import dev.railroadide.railroadpluginapi.services.IDEStateService;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import lombok.Getter;
import org.FrostyFlippper.Util.FileUtil;
import org.FrostyFlippper.Util.PlatformUtil;
import org.FrostyFlippper.Util.SettingUtil;
import org.FrostyFlippper.Util.WakatimeUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;

public class WakatimePlugin implements Plugin {
    public static final Gson GSON = GsonLocator.getInstance();

    @Getter
    private static Logger logger;

    public static final SecureTokenStore TOKEN_STORE = new SecureTokenStore("WakatimePlugin");
    private static final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(1);

    @Override
    public void onEnable(PluginContext context) {
        logger = context.getLogger();

        String latestVersion = WakatimeUtil.getLatestWakatimeVersion();
        String architecture = PlatformUtil.architecture();
        String osName = PlatformUtil.osname();

        Registry<Setting<?>> settingRegistry = Registries.getSettingsRegistry(context);

        SettingUtil.buildSettings();
        SettingUtil.registerSettings(settingRegistry, context);

        Path wakatimeLocation = WakatimeUtil.getWakatimeLocation();
        logger.debug("Wakatime location set to {}", wakatimeLocation.toString());

        try {
            PlatformUtil.checkMissingPlatformSupport();
        } catch (RuntimeException exception) {
            logger.error("Unsupported platform: {}-{}. Please check the Wakatime documentation for supported platforms.", osName, architecture, exception);
            return;
        }

        if (latestVersion == null) {
            logger.error("Unable to get the latest Wakatime version!");
            return;
        }

        logger.debug("Wakatime CLI latest version: {}", latestVersion);

        Path filePath = WakatimeUtil.downloadWakatimeCLI(latestVersion, osName, architecture, wakatimeLocation);
        if (filePath == null)
            return;

        try {
            FileUtil.unzipFile(filePath, wakatimeLocation);
            Files.delete(filePath);
        } catch (IOException exception) {
            logger.error("Error unzipping Wakatime CLI!", exception);
            return;
        }

        if (!PlatformUtil.isWindows()) {
            if(!wakatimeLocation.resolve("wakatime-cli-%s-%s".formatted(osName, architecture)).toFile().setExecutable(true)){
                logger.error("Wakatime file at {} could not be executed!", WakatimeUtil.getWakatimeLocation());
                return;
            }
        }

        DocumentEditorStateService editorStateService = context.getService(DocumentEditorStateService.class);
        ApplicationInfoService applicationInfoService = context.getService(ApplicationInfoService.class);
        IDEStateService ideStateService = context.getService(IDEStateService.class);

        Queue<Heartbeat> heartbeatQueue = new ConcurrentLinkedQueue<>();

        String pluginVersion = context.getDescriptor().getVersion();

        EventBus eventBus = context.getEventBus();
        eventBus.subscribe(ProjectEvent.class,
                event -> handleProjectStateChange(context, event, heartbeatQueue, applicationInfoService, pluginVersion));
        eventBus.subscribe(FileEvent.class, event -> {
            if (event.isActivatedEvent()) {
                handleFileActivated(editorStateService, ideStateService, heartbeatQueue, event);

            } else if (event.isSavedEvent()) {
                handleFileSaved(editorStateService, ideStateService, heartbeatQueue, event);
            }
        });

        eventBus.subscribe(FileModifiedEvent.class,
                event -> handleFileModified(event, editorStateService, ideStateService, heartbeatQueue));
    }

    private void handleProjectStateChange(PluginContext context, ProjectEvent event, Queue<Heartbeat> heartbeatQueue, ApplicationInfoService applicationInfoService, String pluginVersion) {
        if (event.isOpened()) {
            while(true) {
                try {
                    if(TOKEN_STORE.getToken("WakatimeApiKey").isEmpty()) {
                        displayPopup(context);
                    } else {
                        break;
                    }
                } catch (RuntimeException ignored) {
                    displayPopup(context);
                }
            }

            SCHEDULER.scheduleAtFixedRate(
                    () -> runHeartbeatQueue(heartbeatQueue, applicationInfoService, pluginVersion), 0, 30, TimeUnit.SECONDS);
        }

        if(event.isClosed()) {
            try {
                if(!SCHEDULER.awaitTermination(5, TimeUnit.SECONDS)){
                    SCHEDULER.shutdownNow();
                }
            } catch (InterruptedException ignored) {
                SCHEDULER.shutdownNow();
            }
        }
    }

    private void displayPopup(PluginContext context){
        SCHEDULER.schedule(() -> Platform.runLater(() -> {
            LocalizationService localizationService = LocalizationServiceLocator.getInstance();

            var label = new LocalizedLabel("wakatime.dialog.info.content");
            var apiKeyField = new LocalizedTextField("wakatime.dialog.info.textfieldprompt");
            var localizedButton = new LocalizedButton("wakatime.dialog.popupconfirmation");

            var hyperlink = new Hyperlink(localizationService.get("wakatime.dialog.textflow.here"));

            HostServices hostServices = context.getService(HostServices.class);
            hyperlink.setOnAction($ -> hostServices.showDocument("https://wakatime.com/api-key"));

            var textFlow = new TextFlow(new LocalizedText("wakatime.dialog.textflow.click"),
                    new Text(" "),
                    hyperlink,
                    new Text(" "),
                    new LocalizedText("wakatime.dialog.textflow.togetyourapikey"));

            textFlow.getChildren().forEach(node -> {
                if (node instanceof Text text) {
                    text.getStyleClass().add("text-flow-text");
                }

                if(node instanceof Hyperlink link) {
                    link.getStyleClass().add("text-hyperlink");
                }
            });

            var vBox = new VBox(label, apiKeyField, localizedButton, textFlow);
            vBox.setPadding(new Insets(10));
            vBox.setSpacing(10);

            var scene = new Scene(vBox);

            scene.getStylesheets().add(getClass().getResource("/assets/wakatime-plugin/styles/popup.css").toExternalForm());

            var stage = new Stage();
            stage.setTitle(localizationService.get("wakatime.popup.info.title"));
            stage.setScene(scene);

            localizedButton.setOnAction(event -> {
                if(apiKeyField.getText().isEmpty())
                    return;

                TOKEN_STORE.saveToken(apiKeyField.getText(), "WakatimeApiKey");
                stage.close();
            });

            stage.showAndWait();
        }), 1, TimeUnit.SECONDS);
    }

    @Override
    public void onDisable(PluginContext context) {
        SettingUtil.unregisterSettings(context);
    }

    private static void handleFileActivated(DocumentEditorStateService editorStateService, IDEStateService ideStateService, Queue<Heartbeat> heartbeatQueue, FileEvent event) {
        Document file = event.file();
        logger.debug("File {} activated", file.getPath().toString());

        heartbeatQueue.add(new Heartbeat.Builder()
                .setEntity(file.getPath().toString())
                .setLineCount((int) file.getContentAsString().lines().count())
                .setLineNumber(editorStateService.getCursors().getLast().line())
                .setCursorPosition(editorStateService.getCursors().getLast().column())
                .setTimestamp(getCurrentTimestamp())
                .setWrite(false)
                .setUnsavedFile(file.isDirty())
                .setProject(ideStateService.getCurrentProject().getAlias())
                .setLanguage(file.getLanguageId())
                .setBuilding(false)
                .build());

        logger.debug("Added file is activated heartbeat to the queue:");
    }

    private static void handleFileSaved(DocumentEditorStateService editorStateService, IDEStateService ideStateService, Queue<Heartbeat> heartbeatQueue, FileEvent event) {
        Document file = event.file();
        heartbeatQueue.add(new Heartbeat.Builder()
                .setEntity(file.getPath().toString())
                .setLineCount((int) file.getContentAsString().lines().count())
                .setLineNumber(editorStateService.getCursors().getLast().line())
                .setCursorPosition(editorStateService.getCursors().getLast().column())
                .setTimestamp(getCurrentTimestamp())
                .setWrite(true)
                .setUnsavedFile(false)
                .setProject(ideStateService.getCurrentProject().getAlias())
                .setLanguage(file.getLanguageId())
                .setBuilding(false)
                .build());

        logger.debug("Added file saved heartbeat to queue:");
    }

    private static void handleFileModified(FileModifiedEvent event, DocumentEditorStateService editorStateService, IDEStateService ideStateService, Queue<Heartbeat> heartbeatQueue){
        Document file = event.file();
        heartbeatQueue.add(new Heartbeat.Builder()
                .setEntity(file.getPath().toString())
                .setLineCount((int) file.getContentAsString().lines().count())
                .setLineNumber(editorStateService.getCursors().getLast().line() + 1)
                .setCursorPosition(editorStateService.getCursors().getLast().column())
                .setTimestamp(getCurrentTimestamp())
                .setWrite(true)
                .setUnsavedFile(file.isDirty())
                .setProject(ideStateService.getCurrentProject().getAlias())
                .setLanguage(file.getLanguageId())
                .setBuilding(false)
                .build());

        logger.debug("Added file modified heartbeat to queue:");
    }

    private static BigDecimal getCurrentTimestamp() {
        return new BigDecimal((System.currentTimeMillis() / 1000.0)).setScale(4, RoundingMode.HALF_UP);
    }

    private static String getApiKey(){
        try {
            return TOKEN_STORE.getToken("WakatimeApiKey");
        } catch (IllegalArgumentException exception) {
            logger.warn("API key is not present!", exception);
            return null;
        } catch (RuntimeException exception) {
            logger.error("An error occurred while retrieving the api key!", exception);
            return null;
        }
    }

    public void runHeartbeatQueue(Queue<Heartbeat> heartbeatQueue, ApplicationInfoService applicationInfoService, String currentVersion) {
        String retrievedApiKey = getApiKey();

        if(retrievedApiKey == null)
            return;

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
        cmds.add(WakatimeUtil.getWakatimeCliLocation().toString());

        cmds.add("--plugin");
        String plugin = WakatimeUtil.getPluginString(applicationInfoService, currentVersion);
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

        if (heartbeat.getIsWrite()) {
            cmds.add("--write");
        }

        if (heartbeat.getIsUnsavedFile()) {
            cmds.add("--is-unsaved-entity");
        }

        if (heartbeat.getIsBuilding()) {
            cmds.add("--category");
            cmds.add("building");
        }

        if (Boolean.TRUE.equals(SettingUtil.isDebugSetting.getValue())) {
            cmds.add("--verbose");
        }

        String proxy = SettingUtil.proxySetting.getValue();
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
}