## 3.2.0

* Upgrade to flutter 3.32.0

## 3.1.0

* Rework on ios swift code
* Attach route aware mixin now, so the widget will turn off the camera itself if not needed
* Upgrade to flutter latest

## 3.0.4

* Fix camera being active after unused on IOS

## 3.0.3+4

* Fix switch camera not properly pause the current active camera

## 3.0.3+3

* Minor fix on IOS

## 3.0.3+2

* Minor fix on IOS

## 3.0.3

* Up ios deployment target to 11

## 3.0.2

* Permission fixes for Android 33

## 3.0.1

* Support for Android 33

## 3.0.0

* Support for Flutter 3

## 2.0.3+2

- More script tidy-up

## 2.0.3+2

- More script tidy-up

## 2.0.3

- Migrate Android V2 embedding 

## 2.0.2

- Fix android error for Q above context.getExternalFilesDir(null) replaces Environment.getExternalStorageState() 

## 2.0.1+1

- Fix minor bug on turnOn method

## 2.0.1

- Fix Android torchlight
- Add turnoff Camera feature (so the flashlight can be used if the camera is turned off)

## 2.0.0+1-nullsafety

- Add SavePath and MaxSize parameter on constructor

## 2.0.0-nullsafety

- Migrate to Null Safety
- Add Ignore Permission for Camera

## 1.3.1+1

- Fix error when null is passed as focusRectColor
- Tidy ups

## 1.3.1

- Add checkPermissionAtStartup flag at AdvCamera, to prevent checkPermission at startup

## 1.3.0

- Add manual focus on controller.setFocus, to programmatically set the focus of camera
- Provide rectangle size and color for AdvCamera initialization
- Draw rectangle whenever camera gets focused

## 1.2.0

- Do some tidy ups

## 1.1.3+1

- Fix switch camera without result in Android

## 1.1.3

- Fix error when changing into camera without flash (android)
- add getFlashType

## 1.1.2+3

- fix ios no camera case (for emulator)

## 1.1.2+2

- turn off camera for android only happen in native

## 1.1.2+1

- remove onResume on IOS, because apparently the camera will start right away when it's resumed

## 1.1.2

- fix that the camera is not closed after disposing the widget

## 1.1.1+1

- clean up and check if the camera is null before stop preview on paused

## 1.1.1

- freeze ios camera after taking picture

## 1.1.0

- Add permission on both Android and IOS
- fixing the grey screen when first loading with IOS
- fixing the set parameter failed on Android

## 1.0.0+1

- Add fileNamePrefix as initial parameter on AdvCamera

## 1.0.0

- Fixing inconsistency on some device with its result rotated 90 degree more, hopefully this works for every device

## 0.0.6

- Add bestPictureSize parameter for initial run on Android, because each device's default picture size may vary, example on MI 8 Lite, its default picture size is the lowest

## 0.0.5

- Fix ios wrong rotation after resized

## 0.0.4

- Add maxSize argument to resize captured image

## 0.0.3

- Fix ios bug when tap to focus
- Fix android capture image to return success

## 0.0.2+2

- Migrate to Swift 4.2

## 0.0.2+1

- Migrate to AndroidX

## 0.0.2

- Some bug fixes
- Clean up unnecessary codes

## 0.0.1

Initial release with these features
- tap to focus
- zoom (although this might not be as smooth, but hopefully I can maintain this plugin often enough to improve it)
- flashlight
