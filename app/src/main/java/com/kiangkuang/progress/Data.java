package com.kiangkuang.progress;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.Marker;

public class Data {
    public static int mode = 1; // destination or distance
    public static int step = 1;
    public static double destLat = 0;
    public static double destLong = 0;
    public static double radius = 0;

    public static void save(Activity activity, int mode, int step, Marker destinationMarker, Circle radiusCircle) {
        Data.mode = mode;
        Data.step = step;
        if (destinationMarker != null) {
            Data.destLat = destinationMarker.getPosition().latitude;
            Data.destLong = destinationMarker.getPosition().longitude;
        } else {
            Data.destLat = Data.destLong = 0;
        }
        if (radiusCircle != null) {
            Data.radius = radiusCircle.getRadius();
        } else {
            Data.radius = 0;
        }

        SharedPreferences sharedPref = activity.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("mode", Data.mode);
        editor.putInt("step", Data.step);
        editor.putFloat("destLat", (float) destLat);
        editor.putFloat("destLong", (float) destLong);
        editor.putFloat("radius", (float) radius);
        editor.apply();
        Log.i("ProGress", "Saved: mode " + Data.mode + ", step " + Data.step + ", lat " + destLat + ", long " + destLong + ", radius " + radius);
    }

    public static void load(Activity activity) {
        SharedPreferences sharedPref = activity.getPreferences(Context.MODE_PRIVATE);
        mode = Math.max(1, sharedPref.getInt("mode", 1));
        step = Math.max(1, sharedPref.getInt("step", 1));
        destLat = (double) sharedPref.getFloat("destLat", 0);
        destLong = (double) sharedPref.getFloat("destLong", 0);
        radius = (double) sharedPref.getFloat("radius", 0);
        Log.i("ProGress", "Loaded: mode " + Data.mode + ", step " + Data.step + ", lat " + destLat + ", long " + destLong + ", radius " + radius);
    }
}
