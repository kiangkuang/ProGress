package com.kiangkuang.progress;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback, PlaceSelectionListener, OnMapClickListener, OnMarkerDragListener, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, ResultCallback<Status> {

    private static final String TAG = "MainActivity";
    private static final int DESTINATION = 1;
    private static final int DISTANCE = 2;

    private int mode;
    private GoogleMap map;
    private Marker searchMarker;
    private Marker destinationMarker;
    private Circle distanceCircle;

    private GoogleApiClient googleApiClient;
    private PendingIntent geofencePendingIntent;
    private ArrayList<String> geofenceIdList;
    private NotificationManager notificationManager;
    private MyReceiver geofenceReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        geofenceReceiver = new MyReceiver();
        geofenceReceiver.setMainActivityHandler(this);

        geofenceIdList = new ArrayList<>();
        geofenceIdList.add("ProGress");

        initMap();
        initSearch();

        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }

    @Override
    protected void onStart() {
        googleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Register for the particular broadcast based on ACTION string
        IntentFilter filter = new IntentFilter(GeofenceTransitionsIntentService.ACTION_ARRIVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(geofenceReceiver, filter);
        // or `registerReceiver(geofenceReceiver, filter)` for a normal broadcast
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Unregister the listener when the application is paused
        LocalBroadcastManager.getInstance(this).unregisterReceiver(geofenceReceiver);
        // or `unregisterReceiver(geofenceReceiver)` for a normal broadcast
    }

    @Override
    protected void onStop() {
        googleApiClient.disconnect();
        super.onStop();
    }

    private void initMap() {
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;

        enableMyLocation();
        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setMapToolbarEnabled(false);

        map.setOnMapClickListener(this);
        map.setOnMarkerDragListener(this);

        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {

            Bundle bundle = intent.getParcelableExtra("bundle");
            LatLng destPos = bundle.getParcelable("destination");
            if (destPos != null) {
                destinationMarker = map.addMarker(new MarkerOptions()
                        .position(destPos)
                        .draggable(true)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                        .title("Destination")
                        .snippet("Hold marker to drag to new position")
                        .alpha(0.7f));
            }
            if (intent.getDoubleExtra("radius", -1) != -1) {
                distanceCircle = map.addCircle(new CircleOptions()
                        .center(destinationMarker.getPosition())
                        .radius(intent.getDoubleExtra("radius", -1))
                        .fillColor(Color.argb(50, 0, 0, 255))
                        .strokeColor(Color.argb(100, 0, 0, 255)));
                destinationMarker.setSnippet("Alarm radius: " + (int) distanceCircle.getRadius() + "m");
            }
            LatLng searchPos = bundle.getParcelable("searchPos");
            if (searchPos != null) {
                searchMarker = map.addMarker(new MarkerOptions()
                        .position(searchPos)
                        .title(intent.getStringExtra("searchName"))
                        .snippet(intent.getStringExtra("searchAddress"))
                        .alpha(0.7f));
            }
            mode = DESTINATION * -1;
            setStep(4);
        } else {
            destinationMarker = null;
            distanceCircle = null;
            searchMarker = null;
            mode = DESTINATION;
            setStep(1);

            Toast.makeText(getApplicationContext(), "Tap the map to set destination", Toast.LENGTH_SHORT).show();
        }
    }

    private void enableMyLocation() {
        // Access to the location has been granted to the app.
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        map.setMyLocationEnabled(true);
    }

    private void initSearch() {
        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);
        autocompleteFragment.setOnPlaceSelectedListener(this);
    }

    @Override
    public void onPlaceSelected(Place place) {
        if (searchMarker != null) {
            searchMarker.remove();
        }

        searchMarker = map.addMarker(new MarkerOptions()
                .position(place.getLatLng())
                .title(place.getName().toString())
                .snippet(place.getAddress().toString())
                .alpha(0.7f));

        map.animateCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 15));
    }

    @Override
    public void onError(Status status) {
        Log.i(TAG, "An error occurred: " + status);
    }

    @Override
    public void onMapClick(LatLng latLng) {
        if (mode == DESTINATION) {
            placeDestinationMarker(latLng);
        } else if (mode == DISTANCE) {
            placeDistanceCircle(latLng);
        }
    }

    private void placeDestinationMarker(LatLng latLng) {
        if (destinationMarker == null) {
            destinationMarker = map.addMarker(new MarkerOptions()
                    .position(latLng)
                    .draggable(true)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    .title("Destination")
                    .snippet("Hold marker to drag to new position")
                    .alpha(0.7f));

            // first time after destination placed, auto jump to distance mode
            setDestinationToggle(false);
            setDistanceToggle(true);
            setStep(2);
            Toast.makeText(getApplicationContext(), "Tap the map to set alarm radius", Toast.LENGTH_SHORT).show();
        } else {
            destinationMarker.setPosition(latLng);
            if (distanceCircle != null) {
                distanceCircle.setCenter(latLng);
            }
        }
    }

    private void placeDistanceCircle(LatLng latLng) {
        if (distanceCircle == null) {
            distanceCircle = map.addCircle(new CircleOptions()
                    .center(destinationMarker.getPosition())
                    .radius(Math.max(toRadiusMeters(destinationMarker.getPosition(), latLng), 100))
                    .fillColor(Color.argb(50, 0, 0, 255))
                    .strokeColor(Color.argb(100, 0, 0, 255)));
            setStep(3);
        } else {
            distanceCircle.setRadius(Math.max(toRadiusMeters(destinationMarker.getPosition(), latLng), 100));
        }

        destinationMarker.setSnippet("Alarm radius: " + (int) distanceCircle.getRadius() + "m");
        //Toast.makeText(getApplicationContext(), (int) distanceCircle.getRadius() + "m", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMarkerDragStart(Marker marker) {
        if (distanceCircle != null) {
            distanceCircle.setCenter(marker.getPosition());
            distanceCircle.setFillColor(Color.argb(0, 0, 0, 255));
            distanceCircle.setStrokeColor(Color.argb(50, 0, 0, 255));
        }
        marker.setAlpha(0.5f);
    }

    @Override
    public void onMarkerDrag(Marker marker) {
        if (distanceCircle != null) {
            distanceCircle.setCenter(marker.getPosition());
        }
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        if (distanceCircle != null) {
            distanceCircle.setCenter(marker.getPosition());
            distanceCircle.setFillColor(Color.argb(50, 0, 0, 255));
            distanceCircle.setStrokeColor(Color.argb(100, 0, 0, 255));
        }
        marker.setAlpha(0.7f);
    }

    public void toggledDestination(View view) {
        setDestinationToggle(true);
        setDistanceToggle(false);

        mode = DESTINATION;
    }

    public void toggledDist(View view) {
        setDestinationToggle(false);
        setDistanceToggle(true);

        mode = DISTANCE;
    }

    private void setDestinationToggle(boolean state) {
        ToggleButton destinationToggle = (ToggleButton) findViewById(R.id.destinationToggle);
        destinationToggle.setChecked(state);

        if (state) {
            mode = DESTINATION;
        }
    }

    private void setDistanceToggle(boolean state) {
        ToggleButton distanceToggle = (ToggleButton) findViewById(R.id.distanceToggle);
        distanceToggle.setChecked(state);

        if (state) {
            mode = DISTANCE;
        }
    }

    private void setStep(int step) {
        ToggleButton destinationToggle = (ToggleButton) findViewById(R.id.destinationToggle);
        ToggleButton distanceToggle = (ToggleButton) findViewById(R.id.distanceToggle);
        Button clear = (Button) findViewById(R.id.clear);
        Button start = (Button) findViewById(R.id.start);

        switch (step) {
            case 1: // init
                destinationToggle.setEnabled(true);
                distanceToggle.setEnabled(false);
                clear.setEnabled(false);
                clear.setTextColor(0x55cc0000);
                start.setEnabled(false);
                start.setTextColor(0x55669900);
                start.setText("Start");
                break;
            case 2: // destination done
                destinationToggle.setEnabled(true);
                distanceToggle.setEnabled(true);
                clear.setEnabled(true);
                clear.setTextColor(0xffcc0000);
                start.setEnabled(false);
                start.setTextColor(0x55669900);
                start.setText("Start");
                break;
            case 3: // distance done
                destinationToggle.setEnabled(true);
                distanceToggle.setEnabled(true);
                clear.setEnabled(true);
                clear.setTextColor(0xffcc0000);
                start.setEnabled(true);
                start.setTextColor(0xff669900);
                start.setText("Start");
                break;
            case 4: // started
                destinationToggle.setEnabled(false);
                distanceToggle.setEnabled(false);
                clear.setEnabled(false);
                clear.setTextColor(0x55cc0000);
                start.setEnabled(true);
                start.setTextColor(0xff669900);
                start.setText("Stop");
                break;
        }
    }

    public void clearButton(View view) {
        if (destinationMarker != null) {
            destinationMarker.remove();
            destinationMarker = null;
        }

        if (distanceCircle != null) {
            distanceCircle.remove();
            distanceCircle = null;
        }

        setDestinationToggle(true);
        setDistanceToggle(false);
        setStep(1);
    }

    public void startButton(View view) {
        if (mode > 0) {
            // start
            setStep(4);
            mode *= -1;

            startGeofence();

            sendNotification("ProGress", "Alarm enabled", Notification.PRIORITY_HIGH, true);
//            startService(intent);
        } else {
            // stop
            setStep(3);
            mode *= -1;

            stopGeofence();

            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancelAll();
//            stopService(new Intent(getApplicationContext(), LocationAlarm.class));
        }
    }

    public void startGeofence() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        LocationServices.GeofencingApi.addGeofences(
                googleApiClient,
                getGeofencingRequest(),
                getGeofencePendingIntent()
        ).setResultCallback(this);
    }

    public void stopGeofence() {
        LocationServices.GeofencingApi.removeGeofences(
                googleApiClient,
                // This is the same pending intent that was used in addGeofences().
                geofenceIdList
        ).setResultCallback(this); // Result processed in onResult().
    }

    private GeofencingRequest getGeofencingRequest() {
        Geofence geofence = new Geofence.Builder()
                // Set the request ID of the geofence. This is a string to identify this
                // geofence.
                .setRequestId("ProGress")

                .setCircularRegion(
                        destinationMarker.getPosition().latitude,
                        destinationMarker.getPosition().longitude,
                        (float) distanceCircle.getRadius()
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build();

        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofence(geofence);
        return builder.build();
    }

    private PendingIntent getGeofencePendingIntent() {
        // Reuse the PendingIntent if we already have it.
        if (geofencePendingIntent != null) {
            return geofencePendingIntent;
        }
        Intent intent = new Intent(this, GeofenceTransitionsIntentService.class);
        // We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when
        // calling addGeofences() and removeGeofences().
        geofencePendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        return geofencePendingIntent;
    }

    public static double toRadiusMeters(LatLng center, LatLng radius) {
        float[] result = new float[1];
        Location.distanceBetween(center.latitude, center.longitude, radius.latitude, radius.longitude, result);
        return result[0];
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Location lastLocation = LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
        if (lastLocation != null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), 15));
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onResult(@NonNull Status status) {
        Log.i(TAG, status.toString());
    }

    public void sendNotification(String title, String text, int priority, boolean ongoing) {
        Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        Bundle bundle = new Bundle();
        bundle.putParcelable("destination", destinationMarker.getPosition());
        if (searchMarker != null) {
            bundle.putParcelable("searchPos", searchMarker.getPosition());
            notificationIntent.putExtra("searchName", searchMarker.getTitle());
            notificationIntent.putExtra("searchAddress", searchMarker.getSnippet());
        }
        notificationIntent.putExtra("bundle", bundle);
        notificationIntent.putExtra("radius", distanceCircle.getRadius());

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(MainActivity.class);
        stackBuilder.addNextIntent(notificationIntent);
        PendingIntent notificationPendingIntent = stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);

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

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, builder.build());
    }
}
