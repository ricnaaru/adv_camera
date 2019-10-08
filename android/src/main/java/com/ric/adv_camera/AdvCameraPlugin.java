package com.ric.adv_camera;

import android.content.Context;
import android.database.Cursor;
import android.hardware.Camera;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/**
 * AdvCameraPlugin
 */
public class AdvCameraPlugin {
    public static void registerWith(Registrar registrar) {
        if (registrar.activity() == null) {
            // When a background flutter view tries to register the plugin, the registrar has no activity.
            // We stop the registration process as this plugin is foreground only.
            return;
        }

        registrar
                .platformViewRegistry()
                .registerViewFactory(
                        "plugins.flutter.io/adv_camera", new AdvCameraFactory(registrar));
    }
}
