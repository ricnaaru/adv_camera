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
