package org.FrostyFlippper.Util;

import com.google.gson.JsonNull;
import dev.railroadide.core.registry.Registry;
import dev.railroadide.core.settings.DefaultSettingCodecs;
import dev.railroadide.core.settings.Setting;
import dev.railroadide.core.settings.SettingCategory;
import dev.railroadide.core.settings.SettingCodec;
import dev.railroadide.railroadpluginapi.PluginContext;
import dev.railroadide.railroadpluginapi.Registries;
import javafx.scene.control.TextField;
import org.FrostyFlippper.WakatimePlugin;

public class SettingUtil {
    public static Setting<String> apiKeySetting;
    public static Setting<String> proxySetting;
    public static Setting<Boolean> doesShowInStatusBarSetting;
    public static Setting<Boolean> isDebugSetting;

    public static final SettingCodec<String, TextField> API_KEY_CODEC =
            SettingCodec.<String, TextField>builder("wakatime:api_key")
                    .nodeToValue(textField -> {
                        WakatimePlugin.TOKEN_STORE.saveToken(textField.getText(), "WakatimeApiKey");
                        return textField.getText();
                    })
                    .valueToNode((text, textF) -> textF.setText(text))
                    .jsonEncoder(string -> JsonNull.INSTANCE)
                    .jsonDecoder(jsonElement -> "")
                    .createNode(string -> new TextField())
                    .build();

    public static void buildSettings(){

        SettingCategory settingCategory = SettingCategory.builder("wakatime:category")
                .title("wakatime.category.title")
                .noDescription().build();

        apiKeySetting = Setting.builder(String.class, "wakatime:apiKeySetting")
                .treePath("plugins.wakatime")
                .title("wakatime.apiKey.title")
                .description("wakatime.apiKey.description")
                .codec(API_KEY_CODEC)
                .category(settingCategory)
                .defaultValue("")
                .build();

        proxySetting = Setting.builder(String.class, "wakatime:proxy")
                .treePath("plugins.wakatime")
                .title("wakatime.proxy.title")
                .description("wakatime.proxy.description")
                .codec(DefaultSettingCodecs.STRING)
                .category(settingCategory)
                .defaultValue("")
                .build();

        doesShowInStatusBarSetting = Setting.builder(Boolean.class, "wakatime:does_show_in_status_bar")
                .treePath("plugins.wakatime")
                .title("wakatime.does_show_in_status_bar.title")
                .description("wakatime.does_show_in_status_bar.description")
                .codec(DefaultSettingCodecs.BOOLEAN)
                .category(settingCategory)
                .defaultValue(true)
                .build();

        isDebugSetting = Setting.builder(Boolean.class, "wakatime:is_debug")
                .treePath("plugins.wakatime")
                .title("wakatime.is_debug.title")
                .description("wakatime.is_debug.description")
                .codec(DefaultSettingCodecs.BOOLEAN)
                .category(settingCategory)
                .defaultValue(false)
                .build();
    }

    public static void registerSettings(Registry<Setting<?>> settingRegistry, PluginContext context){
        settingRegistry.register(apiKeySetting.getId(), apiKeySetting);
        context.getLogger().info("Setting '" + apiKeySetting.getId() + "' registered.");

        settingRegistry.register(proxySetting.getId(), proxySetting);
        context.getLogger().info("Setting '" + proxySetting.getId() + "' registered.");

        settingRegistry.register(doesShowInStatusBarSetting.getId(), doesShowInStatusBarSetting);
        context.getLogger().info("Setting '" + doesShowInStatusBarSetting.getId() + "' registered.");

        settingRegistry.register(isDebugSetting.getId(), isDebugSetting);
        context.getLogger().info("Setting '" + isDebugSetting.getId() + "' registered.");
    }

    public static void unregisterSettings(PluginContext context){
        Registry<Setting<?>> settingRegistry = Registries.getSettingsRegistry(context);
        try {
            if (SettingUtil.apiKeySetting != null) {
                settingRegistry.unregister(SettingUtil.apiKeySetting.getId());
                context.getLogger().info("Setting '" + SettingUtil.apiKeySetting.getId() + "' unregistered.");
            }

            if (SettingUtil.proxySetting != null) {
                settingRegistry.unregister(SettingUtil.proxySetting.getId());
                context.getLogger().info("Setting '" + SettingUtil.proxySetting.getId() + "' unregistered.");
            }

            if (SettingUtil.doesShowInStatusBarSetting != null) {
                settingRegistry.unregister(SettingUtil.doesShowInStatusBarSetting.getId());
                context.getLogger().info("Setting '" + SettingUtil.doesShowInStatusBarSetting.getId() + "' unregistered.");
            }

            if (SettingUtil.isDebugSetting != null) {
                settingRegistry.unregister(SettingUtil.isDebugSetting.getId());
                context.getLogger().info("Setting '" + SettingUtil.isDebugSetting.getId() + "' unregistered.");
            }
        } catch (Exception exception) {
            context.getLogger().warn("Failed to unregister setting", exception);
        }
    }
}
