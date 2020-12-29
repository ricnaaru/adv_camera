package com.ric.adv_camera;

public interface FragmentLifecycleListener {
    // These methods are the different events and
    // need to pass relevant arguments related to the event triggered
    void onPause();

    // or when data has been loaded
    void onResume();
}
