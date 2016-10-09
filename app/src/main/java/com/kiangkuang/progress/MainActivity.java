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
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
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
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.pathsense.android.sdk.location.PathsenseLocationProviderApi;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, OnMapClickListener, OnMarkerDragListener, GoogleApiClient.ConnectionCallbacks, ResultCallback<Status>, AdapterView.OnItemSelectedListener {

    private static final String TAG = "MainActivity";
    private static final int DESTINATION = 1;
    private static final int DISTANCE = 2;
    private static final int PLACE_AUTOCOMPLETE_REQUEST_CODE = 1;

    private static final int REQUEST_LOCATION_PERMISSION = 5001;

    private int mode = 1; // destination or distance
    private int step = 1;
    private Marker searchMarker;
    private Marker destinationMarker;
    private Circle radiusCircle;

    private GoogleMap map;
    private GoogleApiClient googleApiClient;
    private NotificationManager notificationManager;
    private PathsenseLocationProviderApi mApi;
    private BroadcastReceiver mMessageReceiver;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_my, menu);
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        Spinner spinner = (Spinner) findViewById(R.id.mapType);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this, R.array.layers_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(this);

        initMap();

        if (googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        mApi = PathsenseLocationProviderApi.getInstance(this);

        mMessageReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                sendNotification(MainActivity.class, "ProGress", "Arrived!", Notification.PRIORITY_MAX, false);
                mApi.removeGeofences();
                setStep(3);
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("geofenceEvent"));
    }

    @Override
    protected void onStart() {
        googleApiClient.connect();
        super.onStart();
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

        Data.load(this);
        double loadedRadius = Data.radius; // placing destination would replace Data.radius, Data.mode, Data.step
        int loadedMode = Data.mode;
        int loadedStep = Data.step;

        if (Data.destLat != 0 && Data.destLong != 0) {
            placeDestinationMarker(new LatLng(Data.destLat, Data.destLong));
        }
        if (loadedRadius != 0) {
            placeDistanceCircle(loadedRadius);
        }

        searchMarker = null;
        setMode(loadedMode);
        setStep(loadedStep);
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

        map.setMyLocationEnabled(true);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Location lastLocation = getLastLocation();
        if (lastLocation == null) {
            Log.d(TAG, "onConnected: lastLocation is null");
            return;
        }

        if (destinationMarker != null) {
            double lat1 = Math.min(lastLocation.getLatitude(), destinationMarker.getPosition().latitude);
            double lat2 = Math.max(lastLocation.getLatitude(), destinationMarker.getPosition().latitude);
            double lng1 = Math.min(lastLocation.getLongitude(), destinationMarker.getPosition().longitude);
            double lng2 = Math.max(lastLocation.getLongitude(), destinationMarker.getPosition().longitude);
            LatLngBounds bounds = new LatLngBounds(new LatLng(lat1, lng1), new LatLng(lat2, lng2));
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 350));
        } else {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), 15));
        }
    }

    @SuppressWarnings("MissingPermission")
    private Location getLastLocation() {
        return LocationServices.FusedLocationApi.getLastLocation(googleApiClient);
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

                        if (step == 1) {
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
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .show();
                        } else {
                            //Never ask again selected, or device policy prohibits the app from having that permission.
                            //So, disable that feature, or fall back to another situation...
                            new AlertDialog.Builder(this)
                                    .setTitle("Permission")
                                    .setMessage("Location access was not granted.\nPlease allow manually in phone settings.")
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
        if (step < 4) {
            if (mode == DESTINATION) {
                placeDestinationMarker(latLng);
            } else if (mode == DISTANCE) {
                placeDistanceCircle(latLng);
            }
        }
        Data.save(this, mode, step, destinationMarker, radiusCircle);
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
        } else {
            destinationMarker.setPosition(latLng);
            if (radiusCircle != null) {
                radiusCircle.setCenter(latLng);
            }
        }

        // first time after destination placed, auto jump to distance mode
        if (Data.step == 1) {
            Toast.makeText(getApplicationContext(), "Tap the map to set alarm radius", Toast.LENGTH_SHORT).show();
            setMode(DISTANCE);
            setStep(2);
        }
    }

    private void placeDistanceCircle(LatLng latLng) {
        placeDistanceCircle(Math.max(toRadiusMeters(destinationMarker.getPosition(), latLng), 50));
    }

    private void placeDistanceCircle(double radius) {
        if (radiusCircle == null) {
            radiusCircle = map.addCircle(new CircleOptions()
                    .center(destinationMarker.getPosition())
                    .radius(Math.max(radius, 100))
                    .fillColor(Color.argb(50, 0, 0, 255))
                    .strokeColor(Color.argb(100, 0, 0, 255)));
        } else {
            radiusCircle.setRadius(Math.max(radius, 100));
        }

        if (Data.step == 2) {
            setStep(3);
        }

        destinationMarker.setSnippet("Alarm radius: " + (int) radiusCircle.getRadius() + "m");
    }

    @Override
    public void onMarkerDragStart(Marker marker) {
        if (radiusCircle != null) {
            radiusCircle.setCenter(marker.getPosition());
            radiusCircle.setFillColor(Color.argb(0, 0, 0, 255));
            radiusCircle.setStrokeColor(Color.argb(50, 0, 0, 255));
        }
        marker.setAlpha(0.5f);
    }

    @Override
    public void onMarkerDrag(Marker marker) {
        if (radiusCircle != null) {
            radiusCircle.setCenter(marker.getPosition());
        }
    }

    @Override
    public void onMarkerDragEnd(Marker marker) {
        if (radiusCircle != null) {
            radiusCircle.setCenter(marker.getPosition());
            radiusCircle.setFillColor(Color.argb(50, 0, 0, 255));
            radiusCircle.setStrokeColor(Color.argb(100, 0, 0, 255));
        }
        marker.setAlpha(0.7f);
        Data.save(this, mode, step, destinationMarker, radiusCircle);
    }

    public void toggledButton(View view) {
        ToggleButton toggleButton = (ToggleButton) view;
        if (toggleButton.getText().toString().equals(getString(R.string.setDestination))) {
            setMode(DESTINATION);
        } else if (toggleButton.getText().toString().equals(getString(R.string.setDistance))) {
            setMode(DISTANCE);
        }
    }

    private void setMode(int mode) {
        ToggleButton destinationToggle = (ToggleButton) findViewById(R.id.destinationToggle);
        ToggleButton distanceToggle = (ToggleButton) findViewById(R.id.distanceToggle);

        if (mode == DESTINATION) {
            destinationToggle.setChecked(true);
            distanceToggle.setChecked(false);

        } else if (mode == DISTANCE) {
            destinationToggle.setChecked(false);
            distanceToggle.setChecked(true);
        }

        this.mode = mode;
        Data.save(this, mode, step, destinationMarker, radiusCircle);
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
                start.setText(R.string.start);
                break;
            case 2: // destination done
                destinationToggle.setEnabled(true);
                distanceToggle.setEnabled(true);
                clear.setEnabled(true);
                clear.setTextColor(0xffcc0000);
                start.setEnabled(false);
                start.setTextColor(0x55669900);
                start.setText(R.string.start);
                break;
            case 3: // distance done
                destinationToggle.setEnabled(true);
                distanceToggle.setEnabled(true);
                clear.setEnabled(true);
                clear.setTextColor(0xffcc0000);
                start.setEnabled(true);
                start.setTextColor(0xff669900);
                start.setText(R.string.start);
                break;
            case 4: // started
                destinationToggle.setEnabled(false);
                distanceToggle.setEnabled(false);
                clear.setEnabled(false);
                clear.setTextColor(0x55cc0000);
                start.setEnabled(true);
                start.setTextColor(0xff669900);
                start.setText(R.string.stop);
                break;
        }
        this.step = step;
        Data.save(this, mode, step, destinationMarker, radiusCircle);
    }

    public void clearButton(View view) {
        if (destinationMarker != null) {
            destinationMarker.remove();
            destinationMarker = null;
        }

        if (radiusCircle != null) {
            radiusCircle.remove();
            radiusCircle = null;
        }

        setMode(DESTINATION);
        setStep(1);

        Toast.makeText(getApplicationContext(), "Tap the map to set destination", Toast.LENGTH_SHORT).show();
    }

    public void startButton(View view) {
        if (step != 4) {
            // start
            setStep(4);

            startGeofence();

            sendNotification(MainActivity.class, "ProGress", "Alarm enabled", Notification.PRIORITY_HIGH, true);
        } else {
            // stop
            setStep(3);

            stopGeofence();

            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancelAll();
        }
    }

    private void startGeofence() {
        mApi.addGeofence("MYGEOFENCE", destinationMarker.getPosition().latitude, destinationMarker.getPosition().longitude, (int) radiusCircle.getRadius(), PathsenseGeofenceGeofenceEventReceiver.class);
    }

    private void stopGeofence() {
        mApi.removeGeofences();
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
            builder.setVibrate(new long[]{0, 1000, 1000, 1000, 1000, 1000});
            builder.setSound(Settings.System.DEFAULT_ALARM_ALERT_URI);
        }

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(0, builder.build());
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);

                if (searchMarker != null) {
                    searchMarker.remove();
                }

                searchMarker = map.addMarker(new MarkerOptions()
                        .position(place.getLatLng())
                        .title(place.getName().toString())
                        .snippet(place.getAddress().toString())
                        .alpha(0.7f));

                map.animateCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), 15));
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                Toast.makeText(MainActivity.this, "Search error", Toast.LENGTH_SHORT).show();
                Log.i(TAG, status.getStatusMessage());
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
        if (map == null) return;

        Spinner spinner = (Spinner) findViewById(R.id.mapType);
        String layerName = ((String) spinner.getSelectedItem());
        if (layerName.equals(getString(R.string.normal))) {
            map.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        } else if (layerName.equals(getString(R.string.hybrid))) {
            map.setMapType(GoogleMap.MAP_TYPE_HYBRID);
        } else if (layerName.equals(getString(R.string.satellite))) {
            map.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
        } else if (layerName.equals(getString(R.string.terrain))) {
            map.setMapType(GoogleMap.MAP_TYPE_TERRAIN);
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
    public void onResult(@NonNull Status status) {
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

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();

        if (step == 4) {
            setStep(3);
        }

        super.onDestroy();
    }
}
