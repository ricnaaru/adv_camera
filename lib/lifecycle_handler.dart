import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

class LifecycleEventHandler extends WidgetsBindingObserver {
  final AsyncCallback onResumed;
  final AsyncCallback onInactive;
  final AsyncCallback onPaused;
  final AsyncCallback onDetached;

  LifecycleEventHandler({
    this.onResumed,
    this.onInactive,
    this.onPaused,
    this.onDetached,
  });

  @override
  Future<Null> didChangeAppLifecycleState(AppLifecycleState state) async {
    switch (state) {
      case AppLifecycleState.resumed:
        if (onResumed != null)
          await onResumed();
        break;
      case AppLifecycleState.inactive:
        if (onInactive != null)
        await onInactive();
        break;
      case AppLifecycleState.paused:
        if (onPaused != null)
        await onPaused();
        break;
      case AppLifecycleState.detached:
        if (onDetached != null)
        await onDetached();
        break;
    }
  }
}
