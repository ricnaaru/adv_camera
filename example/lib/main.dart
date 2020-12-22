import 'dart:io';

import 'package:adv_camera/adv_camera.dart';
import 'package:adv_camera_example/camera.dart';
import 'package:flutter/material.dart';

void main() {
  String id = DateTime.now().toIso8601String();
  runApp(MaterialApp(home: MyApp(id: id)));
}

class MyApp extends StatefulWidget {
  final String id;

  const MyApp({Key key, this.id}) : super(key: key);

  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Home'),
      ),
      body: Center(child: Text('Press Floating Button to access camera')),
      floatingActionButton: FloatingActionButton(
        heroTag: "test3",
        child: Icon(Icons.camera),
        onPressed: () {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (BuildContext context) {
                String id = DateTime.now().toIso8601String();
                return CameraApp(id: id);
              },
            ),
          );
        },
      ),
    );
  }
}
