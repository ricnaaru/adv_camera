package com.ric.adv_camera;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.StandardMessageCodec;
import io.flutter.plugin.platform.PlatformView;
import io.flutter.plugin.platform.PlatformViewFactory;

public class AdvCameraFactory extends PlatformViewFactory {
    private final Activity activity;
    private final BinaryMessenger binaryMessenger;

    AdvCameraFactory(Activity activity, BinaryMessenger binaryMessenger) {
        super(StandardMessageCodec.INSTANCE);
        this.activity = activity;
        this.binaryMessenger = binaryMessenger;
    }

    @Override
    public PlatformView create(Context context, int id, Object args) {
        return new AdvCamera(id, context, activity, binaryMessenger, args);
    }
}