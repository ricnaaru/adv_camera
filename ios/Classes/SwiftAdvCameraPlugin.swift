import Flutter
import UIKit

public class SwiftAdvCameraPlugin: NSObject, FlutterPlugin {
    public static func register(with registrar: FlutterPluginRegistrar) {
        let advCameraViewFactory = AdvCameraViewFactory(with: registrar)
        registrar.register(advCameraViewFactory, withId: "plugins.flutter.io/adv_camera")
  }
}
