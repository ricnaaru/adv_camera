library adv_camera;

import 'dart:async';
import 'dart:io';

import 'package:adv_camera/adv_camera_plugin.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

part 'controller.dart';

enum FlashType { auto, on, off, torch }
enum CameraType { front, rear }
enum CameraPreviewRatio { r16_9, r11_9, r4_3, r1 }
enum CameraSessionPreset { low, medium, high, photo }

typedef void CameraCreatedCallback(AdvCameraController controller);
typedef void ImageCapturedCallback(String path);

class AdvCamera extends StatefulWidget {
  final CameraType initialCameraType;
  final CameraPreviewRatio cameraPreviewRatio;
  final CameraSessionPreset cameraSessionPreset;
  final CameraCreatedCallback? onCameraCreated;
  final ImageCapturedCallback? onImageCaptured;
  final FlashType flashType;
  final bool bestPictureSize;
  final String? fileNamePrefix;
  final Color? focusRectColor;
  final int? focusRectSize;
  final bool ignorePermission;
  final String? savePath;
  final int? maxSize;

  const AdvCamera({
    Key? key,
    CameraType initialCameraType = CameraType.rear,
    CameraPreviewRatio cameraPreviewRatio = CameraPreviewRatio.r16_9,
    CameraSessionPreset cameraSessionPreset = CameraSessionPreset.photo,
    FlashType flashType = FlashType.auto,
    bool bestPictureSize = true,
    this.onCameraCreated,
    this.onImageCaptured,
    this.fileNamePrefix,
    this.focusRectColor,
    this.focusRectSize,
    this.ignorePermission = false,
    this.savePath,
    this.maxSize,
  })  : this.initialCameraType = initialCameraType,
        this.cameraPreviewRatio = cameraPreviewRatio,
        this.cameraSessionPreset = cameraSessionPreset,
        this.flashType = flashType,
        this.bestPictureSize = bestPictureSize,
        super(key: key);

  @override
  _AdvCameraState createState() => _AdvCameraState();
}

class _AdvCameraState extends State<AdvCamera> {
  Set<Factory<OneSequenceGestureRecognizer>>? gestureRecognizers;
  late CameraPreviewRatio _cameraPreviewRatio;
  late CameraSessionPreset _cameraSessionPreset;
  late FlashType _flashType;
  bool _hasPermission = false;

  @override
  void initState() {
    super.initState();
    _cameraPreviewRatio = widget.cameraPreviewRatio;
    _cameraSessionPreset = widget.cameraSessionPreset;
    _flashType = widget.flashType;

    if (!widget.ignorePermission) {
      AdvCameraPlugin.checkForPermission().then(setPermission);
    }
  }

  @override
  Widget build(BuildContext context) {
    String previewRatio;
    String sessionPreset;
    String flashType;

    if (!_hasPermission && !widget.ignorePermission)
      return Center(child: CircularProgressIndicator());

    switch (_flashType) {
      case FlashType.on:
        flashType = "on";
        break;
      case FlashType.off:
        flashType = "off";
        break;
      case FlashType.auto:
        flashType = "auto";
        break;
      case FlashType.torch:
        flashType = "torch";
        break;
    }

    switch (_cameraPreviewRatio) {
      case CameraPreviewRatio.r16_9:
        previewRatio = "16:9";
        break;
      case CameraPreviewRatio.r11_9:
        previewRatio = "11:9";
        break;
      case CameraPreviewRatio.r4_3:
        previewRatio = "4:3";
        break;
      case CameraPreviewRatio.r1:
        previewRatio = "1:1";
        break;
    }

    switch (_cameraSessionPreset) {
      case CameraSessionPreset.low:
        sessionPreset = "low";
        break;
      case CameraSessionPreset.medium:
        sessionPreset = "medium";
        break;
      case CameraSessionPreset.high:
        sessionPreset = "high";
        break;
      case CameraSessionPreset.photo:
        sessionPreset = "photo";
        break;
    }

    final Map<String, dynamic> creationParams = <String, dynamic>{
      "initialCameraType":
          widget.initialCameraType == CameraType.rear ? "rear" : "front",
      "flashType": flashType,
      "savePath": widget.savePath,
      "previewRatio": previewRatio,
      "fileNamePrefix": widget.fileNamePrefix ?? "adv_camera",
      "maxSize": widget.maxSize,
      "sessionPreset": sessionPreset,
      "bestPictureSize": widget.bestPictureSize,
      "focusRectColorRed": widget.focusRectColor?.red ?? 12,
      "focusRectColorGreen": widget.focusRectColor?.green ?? 199,
      "focusRectColorBlue": widget.focusRectColor?.blue ?? 12,
      "focusRectSize": widget.focusRectSize ?? 100,
      //for first run on Android (because on each device the default picture size is vary, for example MI 8 Lite's default is the lowest resolution)
    };

    Widget? camera;

    if (defaultTargetPlatform == TargetPlatform.android) {
      camera = AndroidView(
        viewType: 'plugins.flutter.io/adv_camera',
        onPlatformViewCreated: onPlatformViewCreated,
        gestureRecognizers: gestureRecognizers,
        creationParams: creationParams,
        creationParamsCodec: const StandardMessageCodec(),
      );
    } else if (defaultTargetPlatform == TargetPlatform.iOS) {
      camera = UiKitView(
        viewType: 'plugins.flutter.io/adv_camera',
        onPlatformViewCreated: onPlatformViewCreated,
        gestureRecognizers: gestureRecognizers,
        creationParams: creationParams,
        creationParamsCodec: const StandardMessageCodec(),
      );
    }

    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        double greater;
        double widthTemp;
        double heightTemp;
        double width;
        double height;

        if (constraints.maxWidth < constraints.maxHeight) {
          greater = constraints.maxWidth;
        } else {
          greater = constraints.maxHeight;
        }

        switch (_cameraPreviewRatio) {
          case CameraPreviewRatio.r16_9:
            widthTemp = greater;
            heightTemp = greater * 16.0 / 9.0;
            break;
          case CameraPreviewRatio.r11_9:
            widthTemp = greater;
            heightTemp = greater * 11.0 / 9.0;
            break;
          case CameraPreviewRatio.r4_3:
            widthTemp = greater;
            heightTemp = greater * 4.0 / 3.0;
            break;
          case CameraPreviewRatio.r1:
            widthTemp = greater;
            heightTemp = greater;
            break;
        }

        if (Platform.isAndroid) {
          final orientation = MediaQuery.of(context).orientation;

          if (orientation != Orientation.landscape) {
            width = widthTemp;
            height = heightTemp;
          } else {
            if (constraints.maxWidth < constraints.maxHeight) {
              width = widthTemp;
              height = heightTemp;
            } else {
              width = heightTemp;
              height = widthTemp;
            }
          }
        } else {
          width = constraints.maxWidth;
          height = constraints.maxHeight;
        }

        return ClipRect(
          child: OverflowBox(
            maxWidth: width,
            maxHeight: height,
            minHeight: 0,
            minWidth: 0,
            child: camera,
          ),
          clipper: CustomRect(
            right: constraints.maxWidth,
            bottom: constraints.maxHeight,
          ),
        );
      },
    );
  }

  Future<void> onPlatformViewCreated(int id) async {
    final AdvCameraController controller = await AdvCameraController.init(
      id,
      this,
    );

    if (widget.onCameraCreated != null) {
      widget.onCameraCreated!(controller);
    }
  }

  /// @return the greatest common denominator
  int findGcm(int a, int b) {
    return b == 0 ? a : findGcm(b, a % b); // Not bad for one line of code :)
  }

  String asFraction(int a, int b) {
    int gcm = findGcm(a, b);
    return "${(a / gcm)} : ${(b / gcm)}";
  }

  void onImageCaptured(String path) {
    if (widget.onImageCaptured != null) {
      widget.onImageCaptured!(path);
    }
  }

  FutureOr setPermission(bool value) {
    if (this.mounted)
      setState(() {
        _hasPermission = value;
      });
  }
}

class CustomRect extends CustomClipper<Rect> {
  final double right;
  final double bottom;

  CustomRect({required this.right, required this.bottom});

  @override
  Rect getClip(Size size) {
    Rect rect = Rect.fromLTRB(0.0, 0.0, right, bottom);
    return rect;
  }

  @override
  bool shouldReclip(CustomRect oldClipper) {
    return true;
  }
}
