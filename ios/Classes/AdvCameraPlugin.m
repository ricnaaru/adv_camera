#import "AdvCameraPlugin.h"
#import <adv_camera/adv_camera-Swift.h>

@implementation AdvCameraPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftAdvCameraPlugin registerWithRegistrar:registrar];
}
@end
