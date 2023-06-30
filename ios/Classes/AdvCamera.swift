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

public class AdvCameraView : NSObject, FlutterPlatformView, AVCaptureVideoDataOutputSampleBufferDelegate {
    var captureSession: AVCaptureSession!
    var previewView: BoundsObservableView
    var _channel: FlutterMethodChannel
    var stillImageOutput: AVCaptureStillImageOutput!
    let minimumZoom: CGFloat = 1.0
    let maximumZoom: CGFloat = 3.0
    var lastZoomFactor: CGFloat = 1.0
    var camera: AVCaptureDevice?
    var fileNamePrefix: String = ""
    var maxSize: Int? = nil
    var orientationLast = UIInterfaceOrientation(rawValue: 0)!
    var motionManager: CMMotionManager?
    var focusRectColor: UIColor?
    var focusRectSize: CGFloat?
    var cameraOn: Bool = true
    var currentCameraType: CameraType = .back
    var currentFlashType: FlashType = .off

    var focusSquare: CameraFocusSquare?

    var videoPreviewLayer: AVCaptureVideoPreviewLayer!

    public func view() -> UIView {
        return previewView
    }
    
    // Camera types (front and back)
    enum CameraType {
        case front
        case back
    }

    // Flash types (auto, off, on)
    enum FlashType {
        case auto
        case off
        case on
        case torch
    }


    init(_ frame: CGRect, viewId: Int64, args: Any?, with registrar: FlutterPluginRegistrar) {
        
        previewView = BoundsObservableView(frame: frame)
        
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
                self.setSessionPreset(.photo)
            } else if (sessionPreset == "high") {
                self.setSessionPreset(.high)
            } else if (sessionPreset == "medium") {
                self.setSessionPreset(.medium)
            } else if (sessionPreset == "low") {
                self.setSessionPreset(.low)
            }

            let initialCameraType = (dict["initialCameraType"] as? String)!
            self.currentCameraType = initialCameraType == "front" ? CameraType.front : CameraType.back

            let flashType = (dict["flashType"] as? String)!
            self.currentFlashType = flashType == "auto" ? FlashType.auto :  flashType == "on" ? FlashType.on :  flashType == "torch" ? FlashType.torch : FlashType.off
            if (flashType == "auto") {
                self.setFlashType(.auto)
            } else if (flashType == "off") {
                self.setFlashType(.off)
            } else if (flashType == "on") {
                self.setFlashType(.on)
            } else if (flashType == "torch") {
                self.setFlashType(.torch)
            }

            let maxSize = (dict["maxSize"] as? Int)
            self.maxSize = maxSize
        }
        
        let tap = UITapGestureRecognizer(target: self, action: #selector(self.handleTap(_:)))
        let pinchRecognizer = UIPinchGestureRecognizer(target: self, action:#selector(self.pinch(_:)))
        self.previewView.addGestureRecognizer(pinchRecognizer)
        self.previewView.addGestureRecognizer(tap)
        self.previewView.backgroundColor = UIColor.black.withAlphaComponent(0.3)
        
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
        
        // Set up capture session
        captureSession = AVCaptureSession()
        // Set up camera preview layer
        videoPreviewLayer = AVCaptureVideoPreviewLayer(session: captureSession!)
        videoPreviewLayer?.videoGravity = AVLayerVideoGravity.resizeAspectFill
        
        // Start the camera
        startCamera()

        _channel.setMethodCallHandler { call, result in
            if call.method == "waitForCamera" {
                result(nil)
            } else if call.method == "captureImage" {
                self.captureImage()
                result(nil)
            } else if call.method == "setPreviewRatio" {
                result(nil)
            } else if call.method == "getFlashType" {
                var flashTypes = [String]()
                
                if let camera = self.camera {
                    if camera.isFlashAvailable {
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
                }

                result(flashTypes)
            } else if call.method == "dispose" {
                NotificationCenter.default.removeObserver(self, name:
                                                        UIApplication.willEnterForegroundNotification, object: nil)

                NotificationCenter.default.removeObserver(self,name:  Notification.Name("UIDeviceOrientationDidChangeNotification"), object: nil)

                result(nil)
            } else if call.method == "turnOff" {
                self.pauseCamera()

                result(nil)
            } else if call.method == "turnOn" {
                self.resumeCamera()

                result(nil)
            } else if call.method == "switchCamera" {
                self.switchCamera()

                result(nil)
            } else if call.method == "setFlashType" {
                if let dict = call.arguments as? [String: Any] {
                    if let flashType = (dict["flashType"] as? String) {
                        if (flashType == "auto") {
                            self.setFlashType(.auto)
                        } else if (flashType == "off") {
                            self.setFlashType(.off)
                        } else if (flashType == "on") {
                            self.setFlashType(.on)
                        } else if (flashType == "torch") {
                            self.setFlashType(.torch)
                        }
                    }

                }

                result(nil)
            } else if call.method == "setSessionPreset" {
                if let dict = call.arguments as? [String: Any] {
                    if let sessionPreset = (dict["sessionPreset"] as? String) {
                        if (sessionPreset == "low") {
                            self.setSessionPreset(.low)
                        } else if (sessionPreset == "medium") {
                            self.setSessionPreset(.medium)
                        } else if (sessionPreset == "high") {
                            self.setSessionPreset(.high)
                        } else if (sessionPreset == "photo") {
                            self.setSessionPreset(.photo)
                        }
                    }
                }

                result(nil)
            } else if call.method == "setFocus" {
                if let dict = call.arguments as? [String: Any] {
                    let x = (dict["x"] as? CGFloat)
                    let y = (dict["y"] as? CGFloat)

                    if y != nil && x != nil {
                        self.setFocus(touchPoint: CGPoint.init(x: x!, y: y!))
                    }
//                    if let sessionPreset = (dict["x"] as? String) {
//                        if (sessionPreset == "low") {
//                            self.sessionPreset = .low
//                        } else if (sessionPreset == "medium") {
//                            self.sessionPreset = .medium
//                        } else if (sessionPreset == "high") {
//                            self.sessionPreset = .high
//                        } else if (sessionPreset == "photo") {
//                            self.sessionPreset = .photo
//                        }
//                    }
                }

                result(nil)
            }
        }
    }

    @objc func onResume() {
        resumeCamera()
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
        let touchPoint:CGPoint = sender!.location(in: sender!.view)

        setFocus(touchPoint: touchPoint)
    }

    func setFocus(touchPoint: CGPoint!) {
        let devicePoint: CGPoint = (self.videoPreviewLayer).captureDevicePointConverted(fromLayerPoint: touchPoint)
        if let device = self.camera {
            if let fsquare = self.focusSquare {
                fsquare.updatePoint(touchPoint)
            } else {
                self.focusSquare = CameraFocusSquare(touchPoint: touchPoint, borderColor: self.focusRectColor!, borderWidth: self.focusRectSize!
                )
                self.previewView.addSubview(self.focusSquare!)
                self.focusSquare?.setNeedsDisplay()
            }

            self.focusSquare?.animateFocusingAction()

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
    
    func startCamera() {
        //this 500ms delay is necessary because without this, the screen will be grey for the first time
        // somehow the first time running is faster than the getView from FlutterNativeView function
        let seconds = 0.5
        DispatchQueue.main.asyncAfter(deadline: .now() + seconds) {
            self.videoPreviewLayer?.frame = self.previewView.bounds
            self.previewView.layer.insertSublayer(self.videoPreviewLayer, at: 0)
            
            guard let captureSession = self.captureSession else { return }
            
            // Configure input device (camera)
            guard let videoCaptureDevice = self.getCameraDevice() else { return }
            guard let videoInput = try? AVCaptureDeviceInput(device: videoCaptureDevice) else { return }
            captureSession.addInput(videoInput)
            self.camera = videoCaptureDevice

            // Configure output (sample buffer delegate)
            let videoOutput = AVCaptureVideoDataOutput()
            videoOutput.setSampleBufferDelegate(self, queue: DispatchQueue(label: "cameraQueue"))
            
            captureSession.addOutput(videoOutput)
            
            // Add still image output
            if captureSession.canAddOutput(self.stillImageOutput) {
                captureSession.addOutput(self.stillImageOutput)
            }
            
            let orientation: UIInterfaceOrientation = UIApplication.shared.keyWindow?.rootViewController?.preferredInterfaceOrientationForPresentation ?? UIInterfaceOrientation.portrait

            if orientation == UIInterfaceOrientation.landscapeLeft {
                self.videoPreviewLayer.connection?.videoOrientation = .landscapeLeft
            } else if orientation == UIInterfaceOrientation.landscapeRight {
                self.videoPreviewLayer.connection?.videoOrientation = .landscapeRight
            } else if orientation == UIInterfaceOrientation.portrait {
                self.videoPreviewLayer.connection?.videoOrientation = .portrait
            } else if orientation == UIInterfaceOrientation.portraitUpsideDown {
                self.videoPreviewLayer.connection?.videoOrientation = .portraitUpsideDown
            }

            self.previewView.previousOrientation = orientation
            self.previewView.videoPreviewLayer = self.videoPreviewLayer
            self.previewView.layer.addSublayer(self.videoPreviewLayer)
            
            DispatchQueue.global(qos: .userInitiated).async { //[weak self] in
                // Start capture session
                captureSession.startRunning()
            }
        }
    }

    
    // Helper method to get the appropriate camera device based on the currentCameraType
    private func getCameraDevice(for cameraType: CameraType? = nil) -> AVCaptureDevice? {
        let position: AVCaptureDevice.Position
        if let cameraType = cameraType {
            position = (cameraType == .front) ? .front : .back
        } else {
            position = (currentCameraType == .front) ? .front : .back
        }
        return AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position)
    }
    
    // Switch between front and back camera
    func switchCamera() {
        guard let captureSession = captureSession else { return }
        // Get current input device
        guard let currentInput = captureSession.inputs.first as? AVCaptureDeviceInput else { return }
        // Determine new camera type
        let newCameraType: CameraType = (currentInput.device.position == .front) ? .back : .front
        // Get new input device
        guard let newVideoCaptureDevice = getCameraDevice(for: newCameraType) else { return }
        guard let newVideoInput = try? AVCaptureDeviceInput(device: newVideoCaptureDevice) else { return }
        // Remove current input and add new input
        captureSession.beginConfiguration()
        captureSession.removeInput(currentInput)
        captureSession.addInput(newVideoInput)
        
        // Add still image output
        if captureSession.canAddOutput(stillImageOutput) {
            captureSession.addOutput(stillImageOutput)
        }
        
        captureSession.commitConfiguration()
        self.camera = newVideoCaptureDevice
        currentCameraType = newCameraType
    }

    // Set flash type (auto, off, on)
    func setFlashType(_ flashType: FlashType) {
        guard let captureSession = captureSession else { return }
        guard let videoCaptureDevice = getCameraDevice() else { return }
        
        // Configure flash mode based on flash type
        do {
            try videoCaptureDevice.lockForConfiguration()
            
            switch flashType {
            case .auto:
                if videoCaptureDevice.isFlashModeSupported(.auto) {
                    if (videoCaptureDevice.isTorchAvailable) {
                        videoCaptureDevice.torchMode = .off
                    }
                    videoCaptureDevice.flashMode = .auto
                } else {
                    videoCaptureDevice.flashMode = .off
                }
            case .off:
                if videoCaptureDevice.isFlashModeSupported(.off) {
                    if (videoCaptureDevice.isTorchAvailable) {
                        videoCaptureDevice.torchMode = .off
                    }
                    videoCaptureDevice.flashMode = .off
                }
            case .on:
                if videoCaptureDevice.isFlashModeSupported(.on) {
                    if (videoCaptureDevice.isTorchAvailable) {
                        videoCaptureDevice.torchMode = .off
                    }
                    videoCaptureDevice.flashMode = .on
                } else {
                    videoCaptureDevice.flashMode = .off
                }
            case .torch:
                if videoCaptureDevice.isTorchModeSupported(.on) {
                    videoCaptureDevice.torchMode = .on
                }
            }
            
            videoCaptureDevice.unlockForConfiguration()
            currentFlashType = flashType
        } catch {
            print("Failed to set flash mode: \(error)")
        }
    }

    // Pause the camera
    func pauseCamera() {
        captureSession?.stopRunning()
    }

    // Resume the camera
    func resumeCamera() {
        captureSession?.startRunning()
    }
    
    // Capture image
    func captureImage() {
        guard let connection = getImageOutputConnection() else { return }
        getImageOutput()?.captureStillImageAsynchronously(from: connection) { buffer, error in
            if let error = error {
                print("Failed to capture image: \(error)")
                return
            }
            
            if let imageDataSampleBuffer = buffer {
                let imageData = AVCaptureStillImageOutput.jpegStillImageNSDataRepresentation(imageDataSampleBuffer)
                let imageTemp: UIImage = UIImage(data: imageData!)!
                let result: Bool = self.saveImage(image: imageTemp)
                
                if (!result) {
                    print("Image save error!")
                }
            } else {
                print("Image save error!")
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
    
    func resizeImage(image: UIImage?) -> UIImage? {
        if (self.maxSize == nil) {
            return image
        }
        
        guard let image = image else { return nil }

        let size = calculateResizedImageSize(image: image, maxSize: self.maxSize!)
        UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
        image.draw(in: CGRect(origin: .zero, size: size))
        let resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return resizedImage
    }

    func calculateResizedImageSize(image: UIImage, maxSize: Int) -> CGSize {
        let width = image.size.width
        let height = image.size.height

        var targetWidth = width
        var targetHeight = height

        if width > height && width > CGFloat(maxSize) {
            targetWidth = CGFloat(maxSize)
            targetHeight = height * targetWidth / width
        } else if height > width && height > CGFloat(maxSize) {
            targetHeight = CGFloat(maxSize)
            targetWidth = width * targetHeight / height
        }

        return CGSize(width: targetWidth, height: targetHeight)
    }

    // Set session preset
    func setSessionPreset(_ preset: AVCaptureSession.Preset) {
        captureSession?.beginConfiguration()
        captureSession?.sessionPreset = preset
        captureSession?.commitConfiguration()
    }

    // Initialize camera with parameters: session preset, initial camera type (front or back), flash type
    func initializeCamera(sessionPreset: AVCaptureSession.Preset, initialCameraType: CameraType, flashType: FlashType) {
        setSessionPreset(sessionPreset)
        currentCameraType = initialCameraType
        currentFlashType = flashType
        startCamera()
    }
    
    // Helper method to get the AVCaptureConnection for image capture
    private func getImageOutputConnection() -> AVCaptureConnection? {
        guard let imageOutput = getImageOutput() else { return nil }
        for connection in imageOutput.connections {
            for port in connection.inputPorts {
                if port.mediaType == .video {
                    return connection
                }
            }
        }
        return nil
    }

    // Helper method to get the AVCaptureStillImageOutput instance
    private func getImageOutput() -> AVCaptureStillImageOutput? {
        return captureSession?.outputs.first(where: { $0 is AVCaptureStillImageOutput }) as? AVCaptureStillImageOutput
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
