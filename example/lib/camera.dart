import 'dart:io';

import 'package:adv_camera/adv_camera.dart';
import 'package:flutter/material.dart';

class CameraApp extends StatefulWidget {
  final String id;

  const CameraApp({Key? key, required this.id}) : super(key: key);

  @override
  _CameraAppState createState() => _CameraAppState();
}

class _CameraAppState extends State<CameraApp> {
  List<String> pictureSizes = <String>[];
  String? imagePath;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('AdvCamera Example'),
      ),
      body: SafeArea(
        child: Stack(
          children: [
            Column(
              children: [
                Padding(
                  padding: EdgeInsets.symmetric(vertical: 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      buildFlashSettings(context),
                      Container(
                        margin: EdgeInsets.symmetric(
                          horizontal: 16,
                        ).copyWith(bottom: 16),
                        height: 1,
                        width: double.infinity,
                        color: Colors.grey,
                      ),
                      buildRatioSettings(context),
                      if (this.pictureSizes.isNotEmpty)
                        Container(
                          margin: EdgeInsets.symmetric(
                            horizontal: 16,
                          ).copyWith(bottom: 16),
                          height: 1,
                          width: double.infinity,
                          color: Colors.grey,
                        ),
                      if (this.pictureSizes.isNotEmpty)
                        buildImageOutputSettings(context),
                    ],
                  ),
                ),
                Expanded(
                  child: AdvCamera(
                    initialCameraType: CameraType.rear,
                    onCameraCreated: _onCameraCreated,
                    onImageCaptured: (String path) {
                      if (this.mounted)
                        setState(() {
                          imagePath = path;
                        });
                    },
                    cameraPreviewRatio: CameraPreviewRatio.r16_9,
                    focusRectColor: Colors.purple,
                    focusRectSize: 200,
                  ),
                ),
              ],
            ),
            Positioned(
              bottom: 16.0,
              left: 16.0,
              child: imagePath != null
                  ? Container(
                      width: 100.0,
                      height: 100.0,
                      child: Image.file(File(imagePath!)),
                    )
                  : Icon(Icons.image),
            )
          ],
        ),
      ),
      floatingActionButton: Column(
        crossAxisAlignment: CrossAxisAlignment.end,
        mainAxisSize: MainAxisSize.min,
        children: [
          FloatingActionButton(
            heroTag: "switch",
            child: Icon(Icons.switch_camera),
            onPressed: () async {
              await cameraController!.switchCamera();
            },
          ),
          Container(height: 16.0),
          FloatingActionButton(
            heroTag: "capture",
            child: Icon(Icons.camera),
            onPressed: () {
              cameraController!.captureImage();
            },
          ),
        ],
      ),
    );
  }

  AdvCameraController? cameraController;

  _onCameraCreated(AdvCameraController controller) {
    this.cameraController = controller;

    this.cameraController!.getPictureSizes().then((pictureSizes) {
      setState(() {
        this.pictureSizes = pictureSizes ?? <String>[];
      });
    });
  }

  Widget buildFlashSettings(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: EdgeInsets.only(left: 16),
          child: Text("Flash Setting"),
        ),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Container(
            child: Row(
              children: [
                TextButton(
                  child: Text("Auto"),
                  onPressed: () {
                    cameraController!.setFlashType(FlashType.auto);
                  },
                ),
                TextButton(
                  child: Text("On"),
                  onPressed: () {
                    cameraController!.setFlashType(FlashType.on);
                  },
                ),
                TextButton(
                  child: Text("Off"),
                  onPressed: () {
                    cameraController!.setFlashType(FlashType.off);
                  },
                ),
                TextButton(
                  child: Text("Torch"),
                  onPressed: () {
                    cameraController!.setFlashType(FlashType.torch);
                  },
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget buildRatioSettings(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: EdgeInsets.only(left: 16),
          child: Text("Ratio Setting"),
        ),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Container(
            child: Row(
              children: [
                TextButton(
                  child: Text(Platform.isAndroid ? "1:1" : "Low"),
                  onPressed: () {
                    cameraController!.setPreviewRatio(CameraPreviewRatio.r1);
                    cameraController!.setSessionPreset(CameraSessionPreset.low);
                  },
                ),
                TextButton(
                  child: Text(Platform.isAndroid ? "4:3" : "Medium"),
                  onPressed: () {
                    cameraController!.setPreviewRatio(CameraPreviewRatio.r4_3);
                    cameraController!
                        .setSessionPreset(CameraSessionPreset.medium);
                  },
                ),
                TextButton(
                  child: Text(Platform.isAndroid ? "11:9" : "High"),
                  onPressed: () {
                    cameraController!.setPreviewRatio(CameraPreviewRatio.r11_9);
                    cameraController!
                        .setSessionPreset(CameraSessionPreset.high);
                  },
                ),
                TextButton(
                  child: Text(Platform.isAndroid ? "16:9" : "Best"),
                  onPressed: () {
                    cameraController!.setPreviewRatio(CameraPreviewRatio.r16_9);
                    cameraController!
                        .setSessionPreset(CameraSessionPreset.photo);
                  },
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget buildImageOutputSettings(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: EdgeInsets.only(left: 16),
          child: Text("Image Output Setting"),
        ),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: this.pictureSizes.map((pictureSize) {
              return TextButton(
                child: Text(pictureSize),
                onPressed: () {
                  cameraController!.setPictureSize(
                      int.tryParse(pictureSize.substring(
                              0, pictureSize.indexOf(":"))) ??
                          0,
                      int.tryParse(pictureSize.substring(
                              pictureSize.indexOf(":") + 1,
                              pictureSize.length)) ??
                          0);
                },
              );
            }).toList(),
          ),
        ),
      ],
    );
  }
}
