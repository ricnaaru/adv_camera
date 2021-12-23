package com.ric.adv_camera;

import android.Manifest;
import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.List;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.platform.PlatformViewRegistry;

/**
 * AdvCameraPlugin
 */
public class AdvCameraPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
    private ActivityPluginBinding activityPluginBinding;
    private FlutterPluginBinding flutterPluginBinding;

    @SuppressWarnings("deprecation")
    public static void registerWith(
            final io.flutter.plugin.common.PluginRegistry.Registrar registrar) {
        register(registrar.activity(), registrar.platformViewRegistry(), registrar.messenger(), null);
    }

    private static void register(Activity activity, PlatformViewRegistry platformViewRegistry, BinaryMessenger binaryMessenger, @Nullable AdvCameraPlugin advCameraPlugin) {
        if (activity == null) {
            // When a background flutter view tries to register the plugin, the registrar has no activity.
            // We stop the registration process as this plugin is foreground only.
            return;
        }

        platformViewRegistry.registerViewFactory(
                "plugins.flutter.io/adv_camera", new AdvCameraFactory(activity, binaryMessenger));

        final MethodChannel channel = new MethodChannel(binaryMessenger, "adv_camera");
        if (advCameraPlugin == null)
            channel.setMethodCallHandler(new AdvCameraPlugin());
        else
            channel.setMethodCallHandler(advCameraPlugin);
    }

    @Override
    public void onMethodCall(MethodCall call, @Nullable final Result result) {
        if (call.method.equals("checkForPermission")) {
            checkForPermission(result);
        } else {
            result.notImplemented();
        }
    }

    private void checkForPermission(final MethodChannel.Result result) {
        Dexter.withActivity(activityPluginBinding.getActivity())
                .withPermissions(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        result.success(report.areAllPermissionsGranted());
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                })
                .check();
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding binding) {
        flutterPluginBinding = binding;

        if (activityPluginBinding == null) {
            return;
        }

        register(activityPluginBinding.getActivity(), binding.getPlatformViewRegistry(), binding.getBinaryMessenger(), this);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {

    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activityPluginBinding = binding;

        if (flutterPluginBinding == null) {
            return;
        }

        register(binding.getActivity(), flutterPluginBinding.getPlatformViewRegistry(), flutterPluginBinding.getBinaryMessenger(), this);
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activityPluginBinding = binding;
    }

    @Override
    public void onDetachedFromActivity() {

    }
}