import Flutter
import UIKit
import Photos

public class SwiftAdvCameraPlugin: NSObject, FlutterPlugin {
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "adv_camera", binaryMessenger: registrar.messenger())
        let instance = SwiftAdvCameraPlugin.init()
        registrar.addMethodCallDelegate(instance, channel: channel)
        
        let advCameraViewFactory = AdvCameraViewFactory(with: registrar)
        registrar.register(advCameraViewFactory, withId: "plugins.flutter.io/adv_camera")
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch (call.method) {
        case "checkForPermission":
            checkForPermission(result: result)
            break;
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    private func checkForPermission(result: @escaping FlutterResult)-> Void {
        checkForCameraPermission() { (accepted) in
            if accepted{
                self.checkForStoragePermission() { (accepted) in
                    if accepted{
                        result(true)
                    } else {
                        result(false)
                    }
                }
            } else {
                result(false)
            }
        }
        
    }
    
    private func checkForStoragePermission(handleFinish: @escaping (_ isOK:Bool)->())-> Void {
        let storageStatus = PHPhotoLibrary.authorizationStatus()
        
        if storageStatus != PHAuthorizationStatus.authorized {
            PHPhotoLibrary.requestAuthorization { status in
                switch status {
                case .authorized:
                    handleFinish(true)
                    break;
                default:
                    handleFinish(false)
                    break;
                }
            }
        } else {
            handleFinish(true)
        }
    }
    
    private func checkForCameraPermission(handleFinish:@escaping (_ isOK:Bool)->())-> Void {
        let cameraStatus = AVCaptureDevice.authorizationStatus(for: .video)
        if cameraStatus != .authorized {
            AVCaptureDevice.requestAccess(for: AVMediaType.video) { granted in
                if granted {
                    handleFinish(true)
                } else {
                    handleFinish(false)
                }
            }
        } else {
            handleFinish(true)
        }
    }
}
