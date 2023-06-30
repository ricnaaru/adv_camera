import 'package:flutter/widgets.dart';

mixin VisibilityAwareStateMixin<S extends StatefulWidget> on State<S> {
  bool? _wasVisible;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final wasVisible = _wasVisible;
    final isVisible = TickerMode.of(context);
    _wasVisible = isVisible;
    if (wasVisible != null && wasVisible != isVisible) {
      didChangeVisibility(isVisible);
    }
  }

  @mustCallSuper
  void didChangeVisibility(bool isVisible) {}
}
