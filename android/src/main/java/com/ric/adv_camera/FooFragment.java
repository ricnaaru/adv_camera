package com.ric.adv_camera;

import android.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class FooFragment extends Fragment {
    FragmentLifecycleListener listener;
    private View view;

    // The onCreateView method is called when Fragment should create its View object hierarchy,
    // either dynamically or via XML layout inflation.
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Defines the xml file for the fragment
        if (view != null) {
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null)
                parent.removeView(view);
        }
        try {
            view = inflater.inflate(R.layout.fragment_foo, container, false);
        } catch (InflateException e) {
            /* map is already there, just return view as it is */
            Log.d("ricric", "map is already there, just return view as it is");
        }
        return view;
    }

    // This event is triggered soon after onCreateView().
    // Any view setup should occur here.  E.g., view lookups and attaching view listeners.
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Setup any handles to view objects here
        // EditText etFoo = (EditText) view.findViewById(R.id.etFoo);
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d("tag", "onPause() called");
        if (listener != null)
            listener.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d("tag", "onResume() called");
        if (listener != null)
            listener.onResume();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        Log.d("ricric", "onDestroyView");
    }
}