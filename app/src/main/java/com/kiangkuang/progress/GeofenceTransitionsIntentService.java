package com.kiangkuang.progress;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingEvent;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;

/**
 * Listener for geofence transition changes.
 *
 * Receives geofence transition events from Location Services in the form of an Intent containing
 * the transition type and geofence id(s) that triggered the transition. Creates a notification
 * as the output.
 */
public class GeofenceTransitionsIntentService extends IntentService implements GoogleApiClient.ConnectionCallbacks, ResultCallback<Status> {

    protected static final String TAG = "GeofenceTransitionsIS";

    private GoogleApiClient googleApiClient;
    private Intent intent;

    public GeofenceTransitionsIntentService() {
        // Use the TAG to name the worker thread.
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        System.out.println("aaa");

        this.intent = intent;
        GeofencingEvent geofencingEvent = GeofencingEvent.fromIntent(intent);
        if (geofencingEvent.hasError()) {
            Log.e(TAG, "error");
            return;
        }

        // Get the transition type.
        int geofenceTransition = geofencingEvent.getGeofenceTransition();

        // Test that the reported transition was of interest.
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            arrived();
        } else {
            // Log the error.
            Log.e(TAG, "Geofence transition invalid type: " + geofenceTransition);
        }
    }

    private void arrived() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addApi(LocationServices.API)
                .build();
        googleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        ArrayList<String> geofenceIdList = new ArrayList<>();
        geofenceIdList.add("ProGress");
        LocationServices.GeofencingApi.removeGeofences(googleApiClient, geofenceIdList)
                .setResultCallback(this);
        sendNotification("ProGress", "Arrived!", Notification.PRIORITY_MAX, false);
    }

    public void sendNotification(String title, String text, int priority, boolean ongoing) {
        PendingIntent notificationPendingIntent = getPendingIntent();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.drawable.notification_icon)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(notificationPendingIntent)
                .setPriority(priority);

        if (ongoing) {
            builder.setOngoing(true);
        } else {
            builder.setAutoCancel(true);
            builder.setVibrate(new long[]{0, 1000, 1000, 1000, 1000, 1000});
            builder.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI);
        }

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, builder.build());
    }

    private PendingIntent getPendingIntent() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.putExtra("bundle", intent.getBundleExtra("bundle"));
        notificationIntent.putExtra("radius", intent.getDoubleExtra("radius", 0));
        notificationIntent.putExtra("mode", intent.getIntExtra("mode", 1));
        notificationIntent.putExtra("step", intent.getIntExtra("step", 1));
        notificationIntent.putExtra("searchName", intent.getStringExtra("searchName"));
        notificationIntent.putExtra("searchAddress", intent.getStringExtra("searchAddress"));

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(notificationIntent);
        return stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onResult(@NonNull Status status) {
        Log.i(TAG, "IS: " + status.toString());
    }
}