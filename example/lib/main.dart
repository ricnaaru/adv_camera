import 'dart:io';

import 'package:adv_camera/adv_camera.dart';
import 'package:flutter/material.dart';

void main() => runApp(MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  List<String> pictureSizes = [];
  String imagePath;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
          appBar: AppBar(
            title: const Text('Plugin example app'),
          ),
          body: SafeArea(
            child: Stack(
              children: [
                Column(
                  children: [
                    Container(
                      color: Colors.purple,
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
                    Container(
                      color: Colors.orange,
                      child: Row(
                        children: [
                          FlatButton(
                            child: Text("1:1"),
                            onPressed: () {
                              cameraController.setPreviewRatio(CameraPreviewRatio.r1);
                              cameraController.setSessionPreset(CameraSessionPreset.low);
                            },
                          ),
                          FlatButton(
                            child: Text("4:3"),
                            onPressed: () {
                              cameraController.setPreviewRatio(CameraPreviewRatio.r4_3);
                              cameraController.setSessionPreset(CameraSessionPreset.medium);
                            },
                          ),
                          FlatButton(
                            child: Text("11:9"),
                            onPressed: () {
                              cameraController.setPreviewRatio(CameraPreviewRatio.r11_9);
                              cameraController.setSessionPreset(CameraSessionPreset.high);
                            },
                          ),
                          FlatButton(
                            child: Text("16:9"),
                            onPressed: () {
                              cameraController.setPreviewRatio(CameraPreviewRatio.r16_9);
                              cameraController.setSessionPreset(CameraSessionPreset.photo);
                            },
                          ),
                        ],
                      ),
                    ),
                    Container(
                      color: Colors.blue,
                      child: SingleChildScrollView(
                        child: Row(
                          children: this.pictureSizes.map((pictureSize) {
                            return FlatButton(
                              child: Text(pictureSize),
                              onPressed: () {
                                print("pictureSize ($pictureSize) => ${pictureSize.indexOf(":")}");
                                print(
                                    "pictureSize ($pictureSize) => ${pictureSize.substring(0, pictureSize.indexOf(":"))}");
                                print(
                                    "pictureSize ($pictureSize) => ${pictureSize.substring(pictureSize.indexOf(":") + 1, pictureSize.length)}");
                                cameraController.setPictureSize(
                                    int.tryParse(
                                        pictureSize.substring(0, pictureSize.indexOf(":"))),
                                    int.tryParse(pictureSize.substring(
                                        pictureSize.indexOf(":") + 1, pictureSize.length)));
                              },
                            );
                          }).toList(),
                        ),
                        scrollDirection: Axis.horizontal,
                      ),
                    ),
                    Expanded(
                        child: Container(
                          child: AdvCamera(
                            onCameraCreated: _onCameraCreated,
                            onImageCaptured: (String path) {
                              setState(() {
                                imagePath = path;
                              });
                            },
                            cameraPreviewRatio: CameraPreviewRatio.r16_9,
                          ),
                        )),
                  ],
                ),
                Positioned(
                  bottom: 16.0,
                  left: 16.0,
                  child: imagePath != null
                      ? Container(width: 100.0, height: 100.0, child: Image.file(File(imagePath)))
                      : Icon(Icons.image),
                )
              ],
            ),
          ),
          floatingActionButton: Column(mainAxisSize: MainAxisSize.min, children: [
            FloatingActionButton(
                child: Icon(Icons.switch_camera),
                onPressed: () {
                  cameraController.switchCamera();
                }),
            FloatingActionButton(
                child: Icon(Icons.camera),
                onPressed: () {
                  print("camera");
                  cameraController.captureImage();
                }),
            FloatingActionButton(
                child: Icon(Icons.camera),
                onPressed: () {
                  setState(() {
                    cameraController.setSavePath("/storage/emulated/0/ricricric");
                  });
                }),
          ])),
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
}
