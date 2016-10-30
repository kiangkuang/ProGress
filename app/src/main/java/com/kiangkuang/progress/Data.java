package com.kiangkuang.progress;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.Log;

import com.google.android.gms.location.places.Place;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class Data {
    public Mode mode; // destination or distance
    public Status status; // 1 to 4
    public Marker destination;
    public Circle distance;
    public Marker search;
    private Activity mActivity;
    private GoogleMap mMap;

    public Data(Activity activity, GoogleMap map) {
        mActivity = activity;
        mMap = map;

        mode = Mode.DESTINATION;
        status = Status.NONE;
        destination = null;
        distance = null;
        search = null;
    }

    public Data setMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    public Data setStatus(Status status) {
        this.status = status;
        return this;
    }

    public Data setDestination(LatLng latLng) {
        if (destination == null) {
            destination = mMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .draggable(true)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    .title("Destination")
                    .snippet("Hold marker to drag to new position")
                    .alpha(0.7f));
        } else {
            destination.setPosition(latLng);
            if (distance != null) {
                distance.setCenter(latLng);
            }
        }
        return this;
    }

    public Data setSearch(Place place) {

        if (search != null) {
            search.remove();
        }
        search = mMap.addMarker(new MarkerOptions()
                .position(place.getLatLng())
                .title(place.getName().toString())
                .snippet(place.getAddress().toString())
                .alpha(0.7f));

        return this;
    }

    public Data setDistance(double radius) {
        if (distance == null) {
            distance = mMap.addCircle(new CircleOptions()
                    .center(destination.getPosition())
                    .radius(Math.max(radius, 50))
                    .fillColor(Color.argb(50, 0, 0, 255))
                    .strokeColor(Color.argb(100, 0, 0, 255)));
        } else {
            distance.setRadius(Math.max(radius, 50));
        }
        return this;
    }

    public void save() {
        SharedPreferences sharedPref = mActivity.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putInt("mode", mode.value);
        editor.putLong("status", status.getId());
        editor.putFloat("destLat", (float) (destination == null ? 0 : destination.getPosition().latitude));
        editor.putFloat("destLong", (float) (destination == null ? 0 : destination.getPosition().longitude));
        editor.putFloat("distance", (float) (distance == null ? 0 : distance.getRadius()));
        editor.apply();
        Log.i("ProGress", "Saved: mode " + mode + ", status " + status + ", dest " + (destination == null ? 0 : destination.getPosition().toString()) + ", dist " + (distance == null ? 0 : distance.getRadius()));
    }

    public void load() {
        SharedPreferences sharedPref = mActivity.getPreferences(Context.MODE_PRIVATE);
        mode = Mode.valueOf(sharedPref.getInt("mode", 0));
        status = Status.fromId(sharedPref.getLong("status", 0));

        double destLat = (double) sharedPref.getFloat("destLat", 0);
        double destLong = (double) sharedPref.getFloat("destLong", 0);
        if (destLat != 0 || destLong != 0) {
            setDestination(new LatLng(destLat, destLong));
        }

        double radius = (double) sharedPref.getFloat("distance", 0);
        if (destination != null && radius != 0) {
            setDistance(radius);
        }

        Log.i("ProGress", "Loaded: mode " + mode + ", status " + status + ", dest " + (destination == null ? 0 : destination.getPosition().toString()) + ", dist " + (distance == null ? 0 : distance.getRadius()));
    }
}


