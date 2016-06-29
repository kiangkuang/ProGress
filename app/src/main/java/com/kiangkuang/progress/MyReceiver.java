package com.kiangkuang.progress;

import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class MyReceiver extends BroadcastReceiver {

    MainActivity main = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        main.stopGeofence();
        main.sendNotification("ProGress", "Arrived!", Notification.PRIORITY_MAX, false);
    }

    void setMainActivityHandler(MainActivity main) {
        this.main = main;
    }
}
