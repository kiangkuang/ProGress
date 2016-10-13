package com.kiangkuang.progress;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
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
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMarkerDragListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.pathsense.android.sdk.location.PathsenseLocationProviderApi;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, OnMapClickListener, OnMarkerDragListener, GoogleApiClient.ConnectionCallbacks, ResultCallback<com.google.android.gms.common.api.Status>, AdapterView.OnItemSelectedListener {
    private static final String TAG = "MainActivity";
    private static final String GEOFENCE_NAME = "ProgressGeofence";
    private static final int REQUEST_LOCATION_PERMISSION = 5001;
    private static final int PLACE_AUTOCOMPLETE_REQUEST_CODE = 5002;

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private NotificationManager mNotificationManager;
    private PathsenseLocationProviderApi mPathsenseApi;
    private BroadcastReceiver mMessageReceiver;

    private Data data;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_activity_map_menu, menu);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        Toolbar toolbar = (Toolbar) findViewById(R.id.main_activity_toolbar);
        setSupportActionBar(toolbar);

        Spinner spinner = (Spinner) findViewById(R.id.mapType);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.layers_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        initMap();

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        mPathsenseApi = PathsenseLocationProviderApi.getInstance(this);

        mMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                sendNotification(MainActivity.class, "ProGress", "Arrived!", Notification.PRIORITY_MAX, false);
                mPathsenseApi.removeGeofences();
                updateUiStatus(Status.READY);
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter(GeofenceEventReceiver.GEOFENCE_INTENT));
    }

    @Override
    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    @Override
    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    private void initMap() {
        // Obtain the SupportMapFragment and get notified when the mMap is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap gMap) {
        mMap = gMap;
        enableMyLocation();
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setMapToolbarEnabled(false);

        mMap.setOnMapClickListener(this);
        mMap.setOnMarkerDragListener(this);

        data = new Data(this, mMap);
        data.load();

        updateUiMode(data.mode);
        updateUiStatus(data.status);
    }

    private void enableMyLocation() {
        // Access to the location has been granted to the app.
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // ask for permission
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQUEST_LOCATION_PERMISSION);

            return;
        }

        mMap.setMyLocationEnabled(true);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Location lastLocation = getLastLocation();
        if (lastLocation == null) {
            Log.d(TAG, "onConnected: lastLocation is null");
            return;
        }

        if (data.destination != null) {
            double lat1 = Math.min(lastLocation.getLatitude(), data.destination.getPosition().latitude);
            double lat2 = Math.max(lastLocation.getLatitude(), data.destination.getPosition().latitude);
            double lng1 = Math.min(lastLocation.getLongitude(), data.destination.getPosition().longitude);
            double lng2 = Math.max(lastLocation.getLongitude(), data.destination.getPosition().longitude);
            LatLngBounds bounds = new LatLngBounds(new LatLng(lat1, lng1), new LatLng(lat2, lng2));
            mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 350));
        } else {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), 15));
        }
    }

    @SuppressWarnings("MissingPermission")
    private Location getLastLocation() {
        return LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION_PERMISSION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // resume function call previously interrupted by permission request
                    enableMyLocation();
                }

                if (grantResults.length > 0) {
                    if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        // resume function call previously interrupted by permission request
                        enableMyLocation();
                        onConnected(null);

                        if (data.status == Status.NONE) {
                            Toast.makeText(getApplicationContext(), "Tap the map to set destination", Toast.LENGTH_SHORT).show();
                        }
                    } else if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                        // Should we show an explanation?
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                            //Show permission explanation dialog...
                            new AlertDialog.Builder(this)
                                    .setTitle("Permission")
                                    .setMessage("Location access is required for app to work.")
                                    .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                            }, REQUEST_LOCATION_PERMISSION);
                                        }
                                    })
                                    .setNegativeButton("Quit", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            MainActivity.this.finish();
                                            System.exit(0);
                                        }
                                    })
                                    .setIcon(android.R.drawable.ic_menu_mylocation)
                                    .show();
                        } else {
                            //Never ask again selected, or device policy prohibits the app from having that permission.
                            //So, disable that feature, or fall back to another situation...
                            new AlertDialog.Builder(this)
                                    .setTitle("Permission")
                                    .setMessage("Location access was not granted.\nPlease enable permission manually in phone Settings > Apps > ProGress.")
                                    .setNegativeButton("Quit", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            MainActivity.this.finish();
                                            System.exit(0);
                                        }
                                    })
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .show();
                        }
                    }
                }
                break;
            }
        }
    }

    @Override
    public void onMapClick(LatLng latLng) {
        if (data.status != Status.STARTED) {
            if (data.mode == Mode.DESTINATION) {
                placeDestinationMarker(latLng);
            } else if (data.mode == Mode.DISTANCE) {
                placeDistanceCircle(latLng);
            }
        }
        data.save();
    }

    private void placeDestinationMarker(LatLng latLng) {
        data.setDestination(latLng);

        // first time after destination placed, auto jump to distance mode
        if (data.status == Status.NONE) {
            Toast.makeText(getApplicationContext(), "Tap the mMap to set alarm distance", Toast.LENGTH_SHORT).show();

            updateUiMode(Mode.DISTANCE);
            updateUiStatus(Status.HALF);
        }
    }

    private void placeDistanceCircle(LatLng latLng) {
        placeDistanceCircle(toRadiusMeters(data.destination.getPosition(), latLng));
    }

    private void placeDistanceCircle(double radius) {
        data.setDistance(radius);

        if (data.status == Status.HALF) {
            updateUiStatus(Status.READY);
        }

        data.destination.setSnippet("Alarm distance: " + (int) data.distance.getRadius() + "m");
    }

    @Override
    public void onMarkerDragStart(Marker marker) {
        if (data.distance != null) {
            data.distance.setCenter(marker.getPosition());
            data.distance.setFillColor(Color.argb(0, 0, 0, 255));
            data.distance.setStrokeColor(Color.argb(50, 0, 0, 255));
        }
        marker.setAlpha(0.5f);
    }

    @Override
    public void onMarkerDrag(Marker marker) {
        if (data.distance != null) {
            data.distance.setCenter(marker.getPosition());
        }
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        if (data.distance != null) {
            data.distance.setCenter(marker.getPosition());
            data.distance.setFillColor(Color.argb(50, 0, 0, 255));
            data.distance.setStrokeColor(Color.argb(100, 0, 0, 255));
        }
        marker.setAlpha(0.7f);
        data.save();
    }

    private void updateUiMode(Mode mode) {
        ToggleButton destinationToggle = (ToggleButton) findViewById(R.id.destinationToggle);
        ToggleButton distanceToggle = (ToggleButton) findViewById(R.id.distanceToggle);

        if (mode == Mode.DESTINATION) {
            destinationToggle.setChecked(true);
            distanceToggle.setChecked(false);

        } else if (mode == Mode.DISTANCE) {
            destinationToggle.setChecked(false);
            distanceToggle.setChecked(true);
        }

        data.setMode(mode).save();
    }

    private void updateUiStatus(Status status) {
        ToggleButton destinationToggle = (ToggleButton) findViewById(R.id.destinationToggle);
        ToggleButton distanceToggle = (ToggleButton) findViewById(R.id.distanceToggle);
        Button clear = (Button) findViewById(R.id.clear);
        Button start = (Button) findViewById(R.id.start);

        destinationToggle.setEnabled(status != Status.STARTED);
        distanceToggle.setEnabled(status == Status.HALF || status == Status.READY);
        clear.setEnabled(status == Status.HALF || status == Status.READY);
        clear.setTextColor(status == Status.HALF || status == Status.READY ? 0xffcc0000 : 0x55cc0000);
        start.setEnabled(status == Status.READY || status == Status.STARTED);
        start.setTextColor(status == Status.READY || status == Status.STARTED ? 0xff669900 : 0x55669900);
        start.setText(status == Status.STARTED ? R.string.stop : R.string.start);

        if (data.destination != null) data.destination.setDraggable(status != Status.STARTED);

        data.setStatus(status).save();
    }

    public void toggledButton(View view) {
        ToggleButton toggleButton = (ToggleButton) view;
        if (toggleButton.getText().toString().equals(getString(R.string.setDestination))) {
            data.setMode(Mode.DESTINATION);
        } else if (toggleButton.getText().toString().equals(getString(R.string.setDistance))) {
            data.setMode(Mode.DISTANCE);
        }

        updateUiMode(data.mode);
    }

    public void clearButton(View view) {
        if (data.destination != null) {
            data.destination.remove();
            data.destination = null;
        }

        if (data.distance != null) {
            data.distance.remove();
            data.distance = null;
        }

        updateUiMode(Mode.DESTINATION);
        updateUiStatus(Status.NONE);

        Toast.makeText(getApplicationContext(), "Tap the mMap to set destination", Toast.LENGTH_SHORT).show();
    }

    public void startButton(View view) {
        if (data.status != Status.STARTED) {
            // start
            updateUiStatus(Status.STARTED);

            startGeofence();

            sendNotification(MainActivity.class, "ProGress", "Alarm enabled", Notification.PRIORITY_HIGH, true);
        } else {
            // stop
            updateUiStatus(Status.READY);

            stopGeofence();

            mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.cancelAll();
        }
    }

    private void startGeofence() {
        mPathsenseApi.addGeofence(GEOFENCE_NAME, data.destination.getPosition().latitude, data.destination.getPosition().longitude, (int) data.distance.getRadius(), GeofenceEventReceiver.class);
    }

    private void stopGeofence() {
        mPathsenseApi.removeGeofences();
    }

    private void sendNotification(Class cls, String title, String text, int priority, boolean ongoing) {
        PendingIntent notificationPendingIntent = getPendingIntent(cls);

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
            builder.setVibrate(new long[]{0, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000});
            builder.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI);
        }

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(0, builder.build());
    }

    private PendingIntent getPendingIntent(Class cls) {
        Intent notificationIntent = new Intent(this, cls);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        notificationIntent.putExtra("notification", true);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        return PendingIntent.getActivity(this, 0, notificationIntent, 0);
    }

    public void onClickSearch(MenuItem item) {
        try {
            Intent intent = new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY).build(this);
            startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE);
        } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
            Toast.makeText(MainActivity.this, "Google Play Services not available.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, intent);

                data.setSearch(place);

                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 15));
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                com.google.android.gms.common.api.Status status = PlaceAutocomplete.getStatus(this, intent);
                Toast.makeText(MainActivity.this, "Search error", Toast.LENGTH_SHORT).show();
                Log.i(TAG, status.getStatusMessage());
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if (mMap == null) return;

        Spinner spinner = (Spinner) findViewById(R.id.mapType);
        String layerName = ((String) spinner.getSelectedItem());
        if (layerName.equals(getString(R.string.normal))) {
            mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        } else if (layerName.equals(getString(R.string.hybrid))) {
            mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        } else if (layerName.equals(getString(R.string.satellite))) {
            mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        } else if (layerName.equals(getString(R.string.terrain))) {
            mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
        } else {
            Log.i("LDA", "Error setting layer with name " + layerName);
        }
    }

    public double toRadiusMeters(LatLng center, LatLng radius) {
        float[] result = new float[1];
        Location.distanceBetween(center.latitude, center.longitude, radius.latitude, radius.longitude, result);
        return result[0];
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Connection suspended");
    }

    @Override
    public void onResult(@NonNull com.google.android.gms.common.api.Status status) {
        Log.i(TAG, "MA: " + status.toString());
    }

    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {
    }

    public void onClickSettings(MenuItem item) {
        Toast.makeText(MainActivity.this, "SETTINGS HERE", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        // Unregister since the activity is about to be closed.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);

        stopGeofence();

        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancelAll();

        if (data.status == Status.STARTED) {
            updateUiStatus(Status.READY);
        }

        super.onDestroy();
    }
}
