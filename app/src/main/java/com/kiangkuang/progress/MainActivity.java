package com.kiangkuang.progress;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
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
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
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

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, OnMapClickListener, OnMarkerDragListener, GoogleApiClient.ConnectionCallbacks, ResultCallback<Status>, AdapterView.OnItemSelectedListener {

    private static final String TAG = "MainActivity";
    private static final int DESTINATION = 1;
    private static final int DISTANCE = 2;
    private static final int PLACE_AUTOCOMPLETE_REQUEST_CODE = 1;

    private int mode; // destination or distance
    private int step;
    private GoogleMap map;
    private Marker searchMarker;
    private Marker destinationMarker;
    private Circle radiusCircle;

    private GoogleApiClient googleApiClient;
    private NotificationManager notificationManager;

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

        Toolbar myToolbar = (Toolbar) findViewById(R.id.my_toolbar);
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
                radiusCircle = map.addCircle(new CircleOptions()
                        .center(destinationMarker.getPosition())
                        .radius(intent.getDoubleExtra("radius", 0))
                        .fillColor(Color.argb(50, 0, 0, 255))
                        .strokeColor(Color.argb(100, 0, 0, 255)));
                destinationMarker.setSnippet("Alarm radius: " + (int) radiusCircle.getRadius() + "m");
            }
            LatLng searchPos = bundle.getParcelable("searchPos");
            if (searchPos != null) {
                searchMarker = map.addMarker(new MarkerOptions()
                        .position(searchPos)
                        .title(intent.getStringExtra("searchName"))
                        .snippet(intent.getStringExtra("searchAddress"))
                        .alpha(0.7f));
            }
            setMode(intent.getIntExtra("mode", DESTINATION));
            setStep(intent.getIntExtra("step", 1));
        } else {
            destinationMarker = null;
            radiusCircle = null;
            searchMarker = null;
            setMode(DESTINATION);
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
        if (destinationMarker != null) {
            double lat1 = Math.min(lastLocation.getLatitude(), destinationMarker.getPosition().latitude);
            double lat2 = Math.max(lastLocation.getLatitude(), destinationMarker.getPosition().latitude);
            double lng1 = Math.min(lastLocation.getLongitude(), destinationMarker.getPosition().longitude);
            double lng2 = Math.max(lastLocation.getLongitude(), destinationMarker.getPosition().longitude);
            LatLngBounds bounds = new LatLngBounds(new LatLng(lat1, lng1), new LatLng(lat2, lng2));
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 200));
        } else if (lastLocation != null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), 15));
        }
        if (radiusCircle != null && toRadiusMeters(destinationMarker.getPosition(), new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude())) < radiusCircle.getRadius()) {
            radiusCircle.setFillColor(Color.argb(50, 0, 255, 0));
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
            setMode(DISTANCE);
            setStep(2);
            Toast.makeText(getApplicationContext(), "Tap the map to set alarm radius", Toast.LENGTH_SHORT).show();
        } else {
            destinationMarker.setPosition(latLng);
            if (radiusCircle != null) {
                radiusCircle.setCenter(latLng);
            }
        }
    }

    private void placeDistanceCircle(LatLng latLng) {
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

        int color;
        if (toRadiusMeters(destinationMarker.getPosition(), latLng) < toRadiusMeters(new LatLng(lastLocation.getLatitude(), lastLocation.getLongitude()), destinationMarker.getPosition())) {
            color = Color.argb(50, 0, 0, 255);
        } else {
            color = Color.argb(50, 255, 0, 0);
        }

        if (radiusCircle == null) {
            radiusCircle = map.addCircle(new CircleOptions()
                    .center(destinationMarker.getPosition())
                    .radius(Math.max(toRadiusMeters(destinationMarker.getPosition(), latLng), 100))
                    .fillColor(color)
                    .strokeColor(Color.argb(100, 0, 0, 255)));
            setStep(3);
        } else {
            radiusCircle.setRadius(Math.max(toRadiusMeters(destinationMarker.getPosition(), latLng), 100));
            radiusCircle.setFillColor(color);
        }

        destinationMarker.setSnippet("Alarm radius: " + (int) radiusCircle.getRadius() + "m");
        //Toast.makeText(getApplicationContext(), (int) radiusCircle.getRadius() + "m", Toast.LENGTH_SHORT).show();
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
                getPendingIntent(GeofenceTransitionsIntentService.class, false)
        ).setResultCallback(this);
    }

    private void stopGeofence() {
        ArrayList<String> geofenceIdList;
        geofenceIdList = new ArrayList<>();
        geofenceIdList.add("ProGress");

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
                        (float) radiusCircle.getRadius()
                )
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                .build();

        GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
        builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
        builder.addGeofence(geofence);
        return builder.build();
    }

    private void sendNotification(Class cls, String title, String text, int priority, boolean ongoing) {
        PendingIntent notificationPendingIntent = getPendingIntent(cls, true);

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

    private PendingIntent getPendingIntent(Class cls, boolean activity) {
        Intent notificationIntent = new Intent(this, cls);
        Bundle bundle = new Bundle();
        bundle.putParcelable("destination", destinationMarker.getPosition());
        if (searchMarker != null) {
            bundle.putParcelable("searchPos", searchMarker.getPosition());
            notificationIntent.putExtra("searchName", searchMarker.getTitle());
            notificationIntent.putExtra("searchAddress", searchMarker.getSnippet());
        }
        notificationIntent.putExtra("bundle", bundle);
        notificationIntent.putExtra("radius", radiusCircle.getRadius());
        notificationIntent.putExtra("mode", mode);
        notificationIntent.putExtra("step", step);

        if (activity) {
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
            stackBuilder.addParentStack(MainActivity.class);
            stackBuilder.addNextIntent(notificationIntent);
            return stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            return PendingIntent.getService(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
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
}
