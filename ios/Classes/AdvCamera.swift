//
//  AdvCamera.swift
//  adv_camera
//
//  Created by Richardo GVT on 30/09/19.
//
import Flutter
import Photos
import AVFoundation
import CoreMotion

extension FlutterViewController {
    
}

public class AdvCameraView : NSObject, FlutterPlatformView {
    var captureSession: AVCaptureSession!
    var previewView: BoundsObservableView
    var _channel: FlutterMethodChannel
    var stillImageOutput: AVCaptureStillImageOutput!
    var camera: AVCaptureDevice?
    let minimumZoom: CGFloat = 1.0
    let maximumZoom: CGFloat = 3.0
    var lastZoomFactor: CGFloat = 1.0
    var sessionPreset: AVCaptureSession.Preset = .photo
    var flashType: AVCaptureDevice.FlashMode = .auto
    var torchType: AVCaptureDevice.TorchMode = .off
    var fileNamePrefix: String = ""
    var maxSize: Int? = nil
    var orientationLast = UIInterfaceOrientation(rawValue: 0)!
    var motionManager: CMMotionManager?
    var focusRectColor: UIColor?
    var focusRectSize: CGFloat?
    
    var focusSquare: CameraFocusSquare?
    
    var videoPreviewLayer: AVCaptureVideoPreviewLayer!
    
    public func view() -> UIView {
        return previewView
    }
    
    init(_ frame: CGRect, viewId: Int64, args: Any?, with registrar: FlutterPluginRegistrar) {
        self.previewView = BoundsObservableView(frame: frame)
        
        captureSession = AVCaptureSession()
        _channel = FlutterMethodChannel(name: "plugins.flutter.io/adv_camera/\(viewId)", binaryMessenger: registrar.messenger())
        stillImageOutput = AVCaptureStillImageOutput()
        
        super.init()
        
        NotificationCenter.default.addObserver(self, selector: #selector(onResume), name:
                                                UIApplication.willEnterForegroundNotification, object: nil)
        
        NotificationCenter.default.addObserver(self, selector: #selector(orientationChanged), name:  Notification.Name("UIDeviceOrientationDidChangeNotification"), object: nil)
        
        if let dict = args as? [String: Any] {
            let focusRectColorRed = (dict["focusRectColorRed"] as? CGFloat)
            let focusRectColorGreen = (dict["focusRectColorGreen"] as? CGFloat)
            let focusRectColorBlue = (dict["focusRectColorBlue"] as? CGFloat)
            let focusRectSize = (dict["focusRectSize"] as? CGFloat)
            self.focusRectSize = focusRectSize
            let red: CGFloat! = (focusRectColorRed ?? 12) / 255
            let green: CGFloat! = (focusRectColorGreen ?? 199) / 255
            let blue: CGFloat! = (focusRectColorBlue ?? 12) / 255
            
            self.focusRectColor = UIColor.init(red: red, green: green, blue: blue, alpha: 255)
            
            let fileNamePrefix: String = (dict["fileNamePrefix"] as? String)!
            self.fileNamePrefix = fileNamePrefix
            
            let sessionPreset: String = (dict["sessionPreset"] as? String)!
            if (sessionPreset == "photo") {
                self.sessionPreset = .photo
            } else if (sessionPreset == "high") {
                self.sessionPreset = .high
            } else if (sessionPreset == "medium") {
                self.sessionPreset = .medium
            } else if (sessionPreset == "low") {
                self.sessionPreset = .low
            }
            
            let initialCameraType = (dict["initialCameraType"] as? String)!
            
            let videoDevices = AVCaptureDevice.devices(for: AVMediaType.video)
            for device in videoDevices {
                if (initialCameraType == "front") {
                    if device.position == AVCaptureDevice.Position.front {
                        self.camera = device
                        break
                    }
                } else if (initialCameraType == "rear") {
                    if device.position == AVCaptureDevice.Position.back {
                        self.camera = device
                        break
                    }
                }
            }
            
            let flashType = (dict["flashType"] as? String)!
            if (flashType == "auto") {
                if let camera = self.camera {
                    if (camera.isFlashModeSupported(.auto)) {
                        self.torchType = .off
                        self.flashType = .auto
                    } else {
                        self.torchType = .off
                        self.flashType = .off
                    }
                }
            } else if (flashType == "off") {
                self.torchType = .off
                self.flashType = .off
            } else if (flashType == "on") {
                if (self.camera!.isFlashModeSupported(.on)) {
                    self.torchType = .off
                    self.flashType = .on
                } else {
                    self.torchType = .off
                    self.flashType = .off
                }
            } else if (flashType == "torch") {
                if (self.camera!.isTorchModeSupported(.on)) {
                    self.flashType = .off
                    self.torchType = .on
                } else {
                    self.torchType = .off
                    self.flashType = .on
                }
            }
            
            let maxSize = (dict["maxSize"] as? Int)
            self.maxSize = maxSize
            
            if let camera = self.camera {
                if (camera.hasTorch) {
                    do {
                        try camera.lockForConfiguration()
                    } catch {
                        print("Could not lock camera")
                    }
                    
                    camera.flashMode = self.flashType
                    camera.torchMode = self.torchType
                }
                
                // unlock your device
                camera.unlockForConfiguration()
            }
        }
        
        handle()
        
        motionManager = CMMotionManager()
        motionManager?.accelerometerUpdateInterval = 0.2
        motionManager?.gyroUpdateInterval = 0.2
        motionManager?.startAccelerometerUpdates(to: (OperationQueue.current)!, withHandler: {
            (accelerometerData, error) -> Void in
            if error == nil {
                self.outputAccelertionData((accelerometerData?.acceleration)!)
            } else {
                print("\(error!)")
            }
        })
        
        _channel.setMethodCallHandler { call, result in
            if call.method == "waitForCamera" {
                result(nil)
            } else if call.method == "captureImage" {
                if let dict = call.arguments as? [String: Any] {
                    if let maxSize = (dict["maxSize"] as? Int) {
                        self.maxSize = maxSize
                    }
                }
                self.saveToCamera()
                result(nil)
            } else if call.method == "setPreviewRatio" {
                result(nil)
            } else if call.method == "getFlashType" {
                var flashTypes = [String]()
                
                if let camera = self.camera {
                    if camera.isFlashModeSupported(.auto) {
                        flashTypes.append("auto")
                    }
                    if camera.isFlashModeSupported(.on) {
                        flashTypes.append("on")
                    }
                    if camera.isFlashModeSupported(.off) {
                        flashTypes.append("off")
                    }
                    
                    if camera.isTorchModeSupported(.on) {
                        flashTypes.append("torch")
                    }
                }
                
                result(flashTypes)
            } else if call.method == "turnOff" {
                self.captureSession.stopRunning()
                
                result(nil)
            } else if call.method == "switchCamera" {
                let videoDevices = AVCaptureDevice.devices(for: AVMediaType.video)
                for device in videoDevices {
                    if (self.camera?.position == .front) {
                        if device.position == AVCaptureDevice.Position.back {
                            self.camera = device
                            break
                        }
                    } else if (self.camera?.position == .back) {
                        if device.position == AVCaptureDevice.Position.front {
                            self.camera = device
                            break
                        }
                    }
                }
                
                self.handle()
                
                result(nil)
            } else if call.method == "setFlashType" {
                if let dict = call.arguments as? [String: Any] {
                    if let flashType = (dict["flashType"] as? String) {
                        if (flashType == "auto") {
                            if (self.camera!.isFlashModeSupported(.auto)) {
                                self.torchType = .off
                                self.flashType = .auto
                            } else {
                                self.torchType = .off
                                self.flashType = .off
                            }
                        } else if (flashType == "off") {
                            self.torchType = .off
                            self.flashType = .off
                        } else if (flashType == "on") {
                            if (self.camera!.isFlashModeSupported(.on)) {
                                self.torchType = .off
                                self.flashType = .on
                            } else {
                                self.torchType = .off
                                self.flashType = .off
                            }
                        } else if (flashType == "torch") {
                            if (self.camera!.isTorchModeSupported(.on)) {
                                self.flashType = .off
                                self.torchType = .on
                            } else {
                                self.torchType = .off
                                self.flashType = .on
                            }
                        }
                    }
                    
                    if (self.camera!.hasTorch) {
                        do {
                            try self.camera!.lockForConfiguration()
                        } catch {
                            print("Could not lock camera")
                        }
                        
                        self.camera!.flashMode = self.flashType
                        self.camera!.torchMode = self.torchType
                    }
                    
                    // unlock your device
                    self.camera!.unlockForConfiguration()
                    
                }
                
                result(nil)
            } else if call.method == "setSessionPreset" {
                if let dict = call.arguments as? [String: Any] {
                    if let sessionPreset = (dict["sessionPreset"] as? String) {
                        if (sessionPreset == "low") {
                            self.sessionPreset = .low
                        } else if (sessionPreset == "medium") {
                            self.sessionPreset = .medium
                        } else if (sessionPreset == "high") {
                            self.sessionPreset = .high
                        } else if (sessionPreset == "photo") {
                            self.sessionPreset = .photo
                        }
                    }
                }
                
                self.handle()
                
                result(nil)
            }
        }
        
        let tap = UITapGestureRecognizer(target: self, action: #selector(self.handleTap(_:)))
        let pinchRecognizer = UIPinchGestureRecognizer(target: self, action:#selector(self.pinch(_:)))
        self.previewView.addGestureRecognizer(pinchRecognizer)
        self.previewView.addGestureRecognizer(tap)
        self.previewView.backgroundColor = UIColor.black.withAlphaComponent(0.3)
    }
    
    @objc func onResume() {
        setupLivePreview()
    }
    
    func outputAccelertionData(_ acceleration: CMAcceleration) {
        var orientationNew: UIInterfaceOrientation
        
        if acceleration.x >= 0.75 {
            orientationNew = .landscapeLeft
        } else if acceleration.x <= -0.75 {
            orientationNew = .landscapeRight
        } else if acceleration.y <= -0.75 {
            orientationNew = .portrait
        } else if acceleration.y >= 0.75 {
            orientationNew = .portraitUpsideDown
        } else {
            // Consider same as last time
            return
        }
        
        if orientationNew == orientationLast {
            return
        }
        
        orientationLast = orientationNew
    }
    
    @objc func orientationChanged() {
        let orientation: UIInterfaceOrientation = UIApplication.shared.keyWindow?.rootViewController?.preferredInterfaceOrientationForPresentation ?? UIInterfaceOrientation.portrait
        
        if orientation == UIInterfaceOrientation.landscapeLeft {
            if (self.previewView.previousOrientation == UIInterfaceOrientation.landscapeRight) {
                self.previewView.setNeedsLayout()
            }
        } else if orientation == UIInterfaceOrientation.landscapeRight {
            if (self.previewView.previousOrientation == UIInterfaceOrientation.landscapeLeft) {
                self.previewView.setNeedsLayout()
            }
        }
    }
    
    @objc func handleTap(_ sender: UITapGestureRecognizer? = nil) {
        let devicePoint: CGPoint = (self.videoPreviewLayer).captureDevicePointConverted(fromLayerPoint: sender!.location(in: sender!.view))
        if let device = self.camera {
            if let sender = sender {
                let touchPoint:CGPoint = sender.location(in: self.previewView)
                if let fsquare = self.focusSquare {
                    fsquare.updatePoint(touchPoint)
                } else {
                    self.focusSquare = CameraFocusSquare(touchPoint: touchPoint, borderColor: self.focusRectColor!, borderWidth: self.focusRectSize!
                    )
                    self.previewView.addSubview(self.focusSquare!)
                    self.focusSquare?.setNeedsDisplay()
                }
                
                self.focusSquare?.animateFocusingAction()
            }
            
            do {
                if (device.isFocusPointOfInterestSupported){
                    try device.lockForConfiguration()
                    
                    device.focusPointOfInterest = devicePoint
                    
                    device.focusMode = .autoFocus
                    device.unlockForConfiguration()
                }
            } catch {
                // just ignore
            }
        }
    }
    
    @objc func pinch(_ pinch: UIPinchGestureRecognizer) {
        guard let device = self.camera else { return }
        
        // Return zoom value between the minimum and maximum zoom values
        func minMaxZoom(_ factor: CGFloat) -> CGFloat {
            return min(min(max(factor, minimumZoom), maximumZoom), device.activeFormat.videoMaxZoomFactor)
        }
        
        func update(scale factor: CGFloat) {
            do {
                try device.lockForConfiguration()
                defer { device.unlockForConfiguration() }
                device.videoZoomFactor = factor
            } catch {
                print("\(error.localizedDescription)")
            }
        }
        
        let newScaleFactor = minMaxZoom(pinch.scale * lastZoomFactor)
        
        switch pinch.state {
        case .began: fallthrough
        case .changed: update(scale: newScaleFactor)
        case .ended:
            lastZoomFactor = minMaxZoom(newScaleFactor)
            update(scale: lastZoomFactor)
        default: break
        }
    }
    
    private func handle() {
        captureSession = AVCaptureSession()
        stillImageOutput = AVCaptureStillImageOutput()
        captureSession.sessionPreset = sessionPreset
        do {
            if let camera = self.camera {
                let input = try AVCaptureDeviceInput(device: camera)
                
                if captureSession.canAddInput(input) && captureSession.canAddOutput(stillImageOutput) {
                    captureSession.addInput(input)
                    captureSession.addOutput(stillImageOutput)
                    setupLivePreview()
                }
            }
        } catch let error  {
            print("Error Unable to initialize back camera:  \(error.localizedDescription)")
        }
    }
    
    func setupLivePreview() {
        videoPreviewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        
        videoPreviewLayer.videoGravity = .resizeAspectFill
        let orientation: UIInterfaceOrientation = UIApplication.shared.keyWindow?.rootViewController?.preferredInterfaceOrientationForPresentation ?? UIInterfaceOrientation.portrait
        
        if orientation == UIInterfaceOrientation.landscapeLeft {
            videoPreviewLayer.connection?.videoOrientation = .landscapeLeft
        } else if orientation == UIInterfaceOrientation.landscapeRight {
            videoPreviewLayer.connection?.videoOrientation = .landscapeRight
        } else if orientation == UIInterfaceOrientation.portrait {
            videoPreviewLayer.connection?.videoOrientation = .portrait
        } else if orientation == UIInterfaceOrientation.portraitUpsideDown {
            videoPreviewLayer.connection?.videoOrientation = .portraitUpsideDown
        }
        
        previewView.previousOrientation = orientation
        previewView.videoPreviewLayer = videoPreviewLayer
        previewView.layer.addSublayer(videoPreviewLayer)
        
        DispatchQueue.global(qos: .userInitiated).async { //[weak self] in
            self.captureSession.startRunning()
            
            //this 500ms delay is necessary because without this, the screen will be grey for the first time
            // somehow the first time running is faster than the getView from FlutterNativeView function
            let seconds = 0.5
            DispatchQueue.main.asyncAfter(deadline: .now() + seconds) {
                self.videoPreviewLayer.frame = self.previewView.bounds
                
                if (self.camera!.hasTorch) {
                    do {
                        try self.camera!.lockForConfiguration()
                    } catch {
                        print("Could not lock camera")
                    }
                    
                    self.camera!.flashMode = self.flashType
                    self.camera!.torchMode = self.torchType
                }
                
                // unlock your device
                self.camera!.unlockForConfiguration()
            }
        }
    }
    
    func rotateImage(image: UIImage) -> UIImage? {
        if self.camera?.position == .front {
            if self.orientationLast == UIInterfaceOrientation.landscapeRight {
                return UIImage(cgImage: image.cgImage!, scale: 1.0, orientation: .downMirrored)
            } else if self.orientationLast == UIInterfaceOrientation.landscapeLeft {
                return UIImage(cgImage: image.cgImage!, scale: 1.0, orientation: .upMirrored)
            } else if self.orientationLast == UIInterfaceOrientation.portrait {
                return UIImage(cgImage: image.cgImage!, scale: 1.0, orientation: .leftMirrored)
            } else if self.orientationLast == UIInterfaceOrientation.portraitUpsideDown {
                return UIImage(cgImage: image.cgImage!, scale: 1.0, orientation: .rightMirrored)
            }
        } else if self.camera?.position == .back {
            if self.orientationLast == UIInterfaceOrientation.landscapeRight {
                return UIImage(cgImage: image.cgImage!, scale: 1.0, orientation: .up)
            } else if self.orientationLast == UIInterfaceOrientation.landscapeLeft {
                return UIImage(cgImage: image.cgImage!, scale: 1.0, orientation: .down)
            } else if self.orientationLast == UIInterfaceOrientation.portrait {
                return UIImage(cgImage: image.cgImage!, scale: 1.0, orientation: .right)
            } else if self.orientationLast == UIInterfaceOrientation.portraitUpsideDown {
                return UIImage(cgImage: image.cgImage!, scale: 1.0, orientation: .left)
            }
        }
        
        return image
    }
    
    func saveToCamera() {
        if let videoConnection = stillImageOutput.connection(with: AVMediaType.video) {
            stillImageOutput.captureStillImageAsynchronously(from: videoConnection) {
                (imageDataSampleBuffer, error) -> Void in
                self.captureSession.stopRunning()
                if let imageDataSampleBuffer = imageDataSampleBuffer {
                    let imageData = AVCaptureStillImageOutput.jpegStillImageNSDataRepresentation(imageDataSampleBuffer)
                    let imageTemp: UIImage = UIImage(data: imageData!)!
                    let result: Bool = self.saveImage(image: imageTemp)
                    
                    if (!result) {
                        print("Image save error!")
                    }
                } else {
                    print("Image save error!")
                }
                self.captureSession.startRunning()
            }
        }
    }
    
    func saveImage(image: UIImage) -> Bool {
        autoreleasepool {
            let rotatedImage = rotateImage(image: image)!
            let newImage = resizeImage(image: rotatedImage)!
            guard let data = newImage.jpegData(compressionQuality: 1) ?? newImage.pngData() else {
                return false
            }
            guard let directory = try? FileManager.default.url(for: .documentDirectory, in: .userDomainMask, appropriateFor: nil, create: false) as NSURL else {
                return false
            }
            
            let timestamp = NSDate().timeIntervalSince1970
            let myTimeInterval = TimeInterval(timestamp)
            let time = NSDate(timeIntervalSince1970: TimeInterval(myTimeInterval))
            let dateFormatterGet = DateFormatter()
            dateFormatterGet.dateFormat = "yyyyMMdd_HHmmss"
            
            let fileURL = directory.appendingPathComponent("\(self.fileNamePrefix)_\(dateFormatterGet.string(from: time as Date)).jpg")
            if !FileManager.default.fileExists(atPath: fileURL!.path) {
                FileManager.default.createFile(atPath: fileURL!.path, contents: data, attributes: nil)
            } else {
                do {
                    try data.write(to: fileURL!)
                } catch {
                    print(error.localizedDescription)
                }
            }
            
            var dict: [String: String] = [String:String]()
            
            dict["path"] = fileURL?.path
            _channel.invokeMethod("onImageCaptured", arguments: dict)
            return true
        }
    }
    
    func resizeImage(image: UIImage) -> UIImage? {
        if (self.maxSize == nil) {
            return image
        }
        let scale = CGFloat(self.maxSize!) / image.size.width
        let newWidth = image.size.width * scale
        let newHeight = image.size.height * scale
        UIGraphicsBeginImageContext(CGSize(width: newWidth, height: newHeight))
        image.draw(in: CGRect(x: 0, y: 0, width: newWidth, height: newHeight))
        let newImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        
        return newImage
    }
}

public protocol ViewBoundsObserving: class {
    // Notifies the delegate that view's `bounds` has changed.
    // Use `view.bounds` to access current bounds
    func boundsDidChange(_ view: BoundsObservableView, from previousBounds: CGRect);
}

/// You can observe bounds change with this view subclass via `ViewBoundsObserving` delegate.
public class BoundsObservableView: UIView {
    
    public weak var boundsDelegate: ViewBoundsObserving?
    
    private var previousBounds: CGRect = .zero
    var previousOrientation: UIInterfaceOrientation?
    var videoPreviewLayer: AVCaptureVideoPreviewLayer!
    
    public override func layoutSubviews() {
        let orientation: UIInterfaceOrientation = UIApplication.shared.keyWindow?.rootViewController?.preferredInterfaceOrientationForPresentation ?? UIInterfaceOrientation.portrait
        
        if (orientation != previousOrientation) {
            boundsDelegate?.boundsDidChange(self, from: previousBounds)
            previousBounds = bounds
            if let connection = videoPreviewLayer?.connection {
                if orientation == UIInterfaceOrientation.landscapeLeft {
                    connection.videoOrientation = .landscapeLeft
                } else if orientation == UIInterfaceOrientation.landscapeRight {
                    connection.videoOrientation = .landscapeRight
                } else if orientation == UIInterfaceOrientation.portrait {
                    connection.videoOrientation = .portrait
                } else if orientation == UIInterfaceOrientation.portraitUpsideDown {
                    connection.videoOrientation = .portraitUpsideDown
                }
            }
            
            if let leyer = self.videoPreviewLayer {
                DispatchQueue.main.async {
                    leyer.frame = self.bounds//.offsetBy(dx: 0, dy: -20)
                }
                
            }
        }
        
        previousOrientation = orientation
        
        // UIView's implementation will layout subviews for me using Auto Resizing mask or Auto Layout constraints.
        super.layoutSubviews()
    }
}

public class AdvCameraViewFactory : NSObject, FlutterPlatformViewFactory {
    var _registrar: FlutterPluginRegistrar
    
    init(with registrar: FlutterPluginRegistrar) {
        _registrar = registrar
    }
    
    public func create(withFrame frame: CGRect, viewIdentifier viewId: Int64, arguments args: Any?) -> FlutterPlatformView {
        return AdvCameraView(frame, viewId: viewId, args: args, with: _registrar)
    }
    
    public func createArgsCodec() -> FlutterMessageCodec & NSObjectProtocol {
        return FlutterStandardMessageCodec.sharedInstance()
    }
}
