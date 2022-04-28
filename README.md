
# Advanced Camera  
  
This is our custom Camera that enabling you to tap focus, zoom, flashlight.
  
*Note*: This plugin use [android.hardware.Camera](https://developer.android.com/guide/topics/media/camera), I have tried to migrate it to [android.hardware.Camera2]([https://developer.android.com/reference/android/hardware/camera2/package-summary](https://developer.android.com/reference/android/hardware/camera2/package-summary)) in development branch, but with lack of understandings I found out so many inconsistency and find it kinda waste of time since Android is developing their new camera [CameraX]([https://developer.android.com/training/camerax](https://developer.android.com/training/camerax)). So until CameraX has released its stable version, I think I will still use this.

There's still so much feature that I haven't include such as video recording, auto white balance, etc.

And please note that Flutter have their own [camera]([https://pub.dev/packages/camera#-changelog-tab-](https://pub.dev/packages/camera#-changelog-tab-)) plugin, but they haven't include focus and flashlight feature there.
  
## Installation  
  
First, add `adv_camera` as a [dependency in your pubspec.yaml file](https://flutter.io/using-packages/).  
  
### iOS  
  
Add two rows to the `ios/Runner/Info.plist`:  
  
* one with the key `Privacy - Camera Usage Description` and a usage description.  
* and one with the key `Privacy - Microphone Usage Description` and a usage description.  
  
Or in text format add the key:  
  
```xml  
<key>NSCameraUsageDescription</key>  
<string>Can I use the camera please?</string>  
<key>NSMicrophoneUsageDescription</key>  
<string>Can I use the mic please?</string>  
```  
  
### Android  
  
For Android's permission, you have to configure it yourself (using Dexter, etc.), or you can manually [turn on its permission at setting]([https://support.google.com/googleplay/answer/6270602?hl=en](https://support.google.com/googleplay/answer/6270602?hl=en)).

This plugin is made to support my other plugin [adv_image_picker](https://pub.dev/packages/adv_image_picker#-readme-tab-), you can see that its permission is handled there.  
  
## Example  
You can find the full example, [here]([https://github.com/ricnaaru/adv_camera/tree/master/example](https://github.com/ricnaaru/adv_camera/tree/master/example))

## Future developments  
- AndroidX camera integration
- Video integration for both Android and IOS
- More testing and bug fixing

## Support
This repository isn't maintained that well, but I will try to keep it well-maintained as much as possible. Please consider support me..
[![Buy me a coffee](https://www.buymeacoffee.com/assets/img/custom_images/white_img.png)](https://www.buymeacoffee.com/rthayeb)

<br>
