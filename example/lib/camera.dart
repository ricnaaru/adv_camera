import 'dart:io';

import 'package:adv_camera/adv_camera.dart';
import 'package:flutter/material.dart';

class CameraApp extends StatefulWidget {
  final String id;

  const CameraApp({Key key, this.id}) : super(key: key);

  @override
  _CameraAppState createState() => _CameraAppState();
}

class _CameraAppState extends State<CameraApp> {
  List<String> pictureSizes = [];
  String imagePath;
  double _width = 204;
  double _height = 250;

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
                      // buildFlashSettings(context),
                      // Container(
                      //   margin: EdgeInsets.symmetric(
                      //     horizontal: 16,
                      //   ).copyWith(bottom: 16),
                      //   height: 1,
                      //   width: double.infinity,
                      //   color: Colors.grey,
                      // ),
                      // buildRatioSettings(context),
                      // buildRatioSettings(context),
                      buildSizeSettings(context),
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
                  child: Stack(
                    children: [
                  Center(
                  child: Container(
                        height: _height + 16,
                        width: _width + 16,
                        color: Colors.green,
                  ),
                  ),
                      Center(
                        child: Container(
                          height: _height,
                          width: _width,
                          child: AdvCamera(
                            initialCameraType: CameraType.front,
                            onCameraCreated: _onCameraCreated,
                            onImageCaptured: (String path) {
                              if (this.mounted)
                                setState(() {
                                  imagePath = path;
                                });
                            },
                            cameraPreviewRatio: CameraPreviewRatio.r16_9,
                          ),
                        ),
                      ),
                    ],
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
                      child: Image.file(File(imagePath)),
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
            heroTag: "test1",
            child: Icon(Icons.switch_camera),
            onPressed: () async {
              await cameraController.switchCamera();
              List<FlashType> types = await cameraController.getFlashType();
            },
          ),
          Container(height: 16.0),
          FloatingActionButton(
            heroTag: "test2",
            child: Icon(Icons.camera),
            onPressed: () {
              cameraController.captureImage();
            },
          ),
        ],
      ),
    );
  }

  AdvCameraController cameraController;

  _onCameraCreated(AdvCameraController controller) {
    this.cameraController = controller;

    this.cameraController.getPictureSizes().then((pictureSizes) {
      setState(() {
        this.pictureSizes = pictureSizes;
      });
    });
  }

  _onTapUp(TapUpDetails details) {
    var x = details.globalPosition.dx;
    var y = details.globalPosition.dy;
    // or user the local position method to get the offset
    print(details.localPosition);
    print("tap up " + x.toString() + ", " + y.toString());
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
                FlatButton(
                  child: Text("Auto"),
                  onPressed: () {
                    cameraController.setFlashType(FlashType.auto);
                  },
                ),
                FlatButton(
                  child: Text("On"),
                  onPressed: () {
                    cameraController.setFlashType(FlashType.on);
                  },
                ),
                FlatButton(
                  child: Text("Off"),
                  onPressed: () {
                    cameraController.setFlashType(FlashType.off);
                  },
                ),
                FlatButton(
                  child: Text("Torch"),
                  onPressed: () {
                    cameraController.setFlashType(FlashType.torch);
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
                FlatButton(
                  child: Text(Platform.isAndroid ? "1:1" : "Low"),
                  onPressed: () {
                    cameraController.setPreviewRatio(CameraPreviewRatio.r1);
                    cameraController.setSessionPreset(CameraSessionPreset.low);
                  },
                ),
                FlatButton(
                  child: Text(Platform.isAndroid ? "4:3" : "Medium"),
                  onPressed: () {
                    cameraController.setPreviewRatio(CameraPreviewRatio.r4_3);
                    cameraController
                        .setSessionPreset(CameraSessionPreset.medium);
                  },
                ),
                FlatButton(
                  child: Text(Platform.isAndroid ? "11:9" : "High"),
                  onPressed: () {
                    cameraController.setPreviewRatio(CameraPreviewRatio.r11_9);
                    cameraController.setSessionPreset(CameraSessionPreset.high);
                  },
                ),
                FlatButton(
                  child: Text(Platform.isAndroid ? "16:9" : "Best"),
                  onPressed: () {
                    cameraController.setPreviewRatio(CameraPreviewRatio.r16_9);
                    cameraController
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

  Widget buildSizeSettings(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: EdgeInsets.only(left: 16),
          child: Text("Size Setting"),
        ),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Container(
            child: Row(
              children: [
                FlatButton(
                  color: Colors.green,
                  child: Text("250:204"),
                  onPressed: () {
                    setState(() {
                      _width = 204;
                      _height = 250;
                    });
                  },
                ),
                FlatButton(
                  color: Colors.green,
                  child: Text("250:205"),
                  onPressed: () {
                    setState(() {
                      _width = 205;
                      _height = 250;
                    });
                  },
                ),
                FlatButton(
                  color: Colors.green,
                  child: Text("250:140"),
                  onPressed: () {
                    setState(() {
                      _width = 140;
                      _height = 250;
                    });
                  },
                ),
                FlatButton(
                  color: Colors.green,
                  child: Text("250:141"),
                  onPressed: () {
                    setState(() {
                      _width = 141;
                      _height = 250;
                    });
                  },
                ),
                FlatButton(
                  color: Colors.green,
                  child: Text("250:187"),
                  onPressed: () {
                    setState(() {
                      _width = 187;
                      _height = 250;
                    });
                  },
                ),
                FlatButton(
                  color: Colors.green,
                  child: Text("250:188"),
                  onPressed: () {
                    setState(() {
                      _width = 188;
                      _height = 250;
                    });
                  },
                ),
                FlatButton(
                  color: Colors.red,
                  child: Text("250:204"),
                  onPressed: () {
                    setState(() {
                      _height = 204;
                      _width = 250;
                    });
                  },
                ),
                FlatButton(
                  color: Colors.red,
                  child: Text("250:205"),
                  onPressed: () {
                    setState(() {
                      _height = 205;
                      _width = 250;
                    });
                  },
                ),
                FlatButton(
                  color: Colors.red,
                  child: Text("250:140"),
                  onPressed: () {
                    setState(() {
                      _height = 140;
                      _width = 250;
                    });
                  },
                ),
                FlatButton(
                  color: Colors.red,
                  child: Text("250:141"),
                  onPressed: () {
                    setState(() {
                      _height= 141;
                      _width  = 250;
                    });
                  },
                ),
                FlatButton(
                  color: Colors.red,
                  child: Text("250:187"),
                  onPressed: () {
                    setState(() {
                      _height = 187;
                      _width = 250;
                    });
                  },
                ),
                FlatButton(
                  color: Colors.red,
                  child: Text("250:188"),
                  onPressed: () {
                    setState(() {
                      _height = 188;
                      _width = 250;
                    });
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
              return FlatButton(
                child: Text(pictureSize),
                onPressed: () {
                  cameraController.setPictureSize(
                      int.tryParse(
                          pictureSize.substring(0, pictureSize.indexOf(":"))),
                      int.tryParse(pictureSize.substring(
                          pictureSize.indexOf(":") + 1, pictureSize.length)));
                },
              );
            }).toList(),
          ),
        ),
      ],
    );
  }
}
