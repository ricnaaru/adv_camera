package com.ric.adv_camera;

import android.content.Context;

import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.StandardMessageCodec;
import io.flutter.plugin.platform.PlatformView;
import io.flutter.plugin.platform.PlatformViewFactory;

public class AdvCameraFactory extends PlatformViewFactory {
    private final PluginRegistry.Registrar mPluginRegistrar;

    AdvCameraFactory(PluginRegistry.Registrar registrar) {
        super(StandardMessageCodec.INSTANCE);
        mPluginRegistrar = registrar;
    }

    @Override
    public PlatformView create(Context context, int id, Object args) {
        return new AdvCamera(id, context, mPluginRegistrar, args);
    }
}
