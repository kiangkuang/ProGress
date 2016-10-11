package com.kiangkuang.progress;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.pathsense.android.sdk.location.PathsenseGeofenceEvent;
import com.pathsense.android.sdk.location.PathsenseGeofenceEventReceiver;

public class PathsenseGeofenceGeofenceEventReceiver extends PathsenseGeofenceEventReceiver {
    static final String TAG = "EventReceiver";

    @Override
    protected void onGeofenceEvent(Context context, PathsenseGeofenceEvent geofenceEvent) {
        Log.i(TAG, "geofence = " + geofenceEvent.getGeofenceId() + ", " + geofenceEvent.getLatitude() + ", " + geofenceEvent.getLongitude() + ", " + geofenceEvent.getRadius());
        //
        if (geofenceEvent.isIngress()) {
            Location location = geofenceEvent.getLocation();
            Log.i(TAG, "geofenceIngress = " + location.getTime() + ", " + location.getProvider() + ", " + location.getLatitude() + ", " + location.getLongitude() + ", " + location.getAltitude() + ", " + location.getSpeed() + ", " + location.getBearing() + ", " + location.getAccuracy());
            // broadcast event
            Intent geofenceEventIntent = new Intent("geofenceEvent");
            geofenceEventIntent.putExtra("geofenceEvent", geofenceEvent);
            LocalBroadcastManager.getInstance(context).sendBroadcast(geofenceEventIntent);
        }
    }
}
