package com.kiangkuang.progress;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.android.gms.common.api.Status;
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

public class MainActivity extends FragmentActivity implements OnMapReadyCallback, PlaceSelectionListener, OnMapClickListener, OnMarkerDragListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final String TAG = "MainActivity";
    private static final int DESTINATION = 1;
    private static final int DISTANCE = 2;
    private int mode;

    private GoogleMap map;
    private Marker searchMarker;
    private Marker destinationMarker;
    private Circle distanceCircle;

    private boolean mPermissionDenied = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_maps);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        initSearch();

        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            mode = DESTINATION * -1;
            setStep(4);
        } else {
            mode = DESTINATION;
            setStep(1);
            searchMarker = null;
            destinationMarker = null;
            distanceCircle = null;
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;
        LatLng pos = enableMyLocation();

        if (pos != null) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 14));
        }

        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setMapToolbarEnabled(false);

        map.setOnMapClickListener(this);
        map.setOnMarkerDragListener(this);

        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getParcelableExtra("bundle");
            LatLng destLatLng = bundle.getParcelable("destination");
            destinationMarker = map.addMarker(new MarkerOptions()
                    .position(destLatLng)
                    .draggable(true)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    .title("Destination")
                    .snippet("Hold marker to drag to new position")
                    .alpha(0.7f));
            distanceCircle = map.addCircle(new CircleOptions()
                    .center(destinationMarker.getPosition())
                    .radius(intent.getDoubleExtra("radius", -1))
                    .fillColor(Color.argb(50, 0, 0, 255))
                    .strokeColor(Color.argb(100, 0, 0, 255)));
        } else {
            Toast.makeText(getApplicationContext(), "Tap the map to set destination", Toast.LENGTH_SHORT).show();
        }
    }

    private void initSearch() {
        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment) getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);
        autocompleteFragment.setOnPlaceSelectedListener(this);
    }

    private LatLng enableMyLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // Permission to access the location is missing.
            PermissionUtils.requestPermission(this, LOCATION_PERMISSION_REQUEST_CODE, Manifest.permission.ACCESS_FINE_LOCATION, true);
        } else if (map != null) {
            // Access to the location has been granted to the app.
            map.setMyLocationEnabled(true);

            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            return new LatLng(location.getLatitude(), location.getLongitude());
        }
        return null;
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
                    .radius(toRadiusMeters(destinationMarker.getPosition(), latLng))
                    .fillColor(Color.argb(50, 0, 0, 255))
                    .strokeColor(Color.argb(100, 0, 0, 255)));
            setStep(3);
        } else {
            distanceCircle.setRadius(toRadiusMeters(destinationMarker.getPosition(), latLng));
        }
        Toast.makeText(getApplicationContext(), (int) toRadiusMeters(destinationMarker.getPosition(), latLng) + "m", Toast.LENGTH_SHORT).show();
    }

    public void clear(View view) {
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

    public void start(View view) {
        if (mode > 0) {
            // start
            setStep(4);
            mode *= -1;

            Intent intent = new Intent(getApplicationContext(), LocationAlarm.class);
            Bundle bundle = new Bundle();
            bundle.putParcelable("destination", destinationMarker.getPosition());
            intent.putExtra("bundle", bundle);
            intent.putExtra("radius", distanceCircle.getRadius());
            startService(intent);
        } else {
            // stop
            setStep(3);
            mode *= -1;

            stopService(new Intent(getApplicationContext(), LocationAlarm.class));
        }
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

    public static double toRadiusMeters(LatLng center, LatLng radius) {
        float[] result = new float[1];
        Location.distanceBetween(center.latitude, center.longitude, radius.latitude, radius.longitude, result);
        return result[0];
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return;
        }

        if (PermissionUtils.isPermissionGranted(permissions, grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation();
        } else {
            // Display the missing permission error dialog when the fragments resume.
            mPermissionDenied = true;
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();
        if (mPermissionDenied) {
            // Permission was not granted, display error dialog.
            showMissingPermissionError();
            mPermissionDenied = false;
        }
    }

    /**
     * Displays a dialog with error message explaining that the location permission is missing.
     */
    private void showMissingPermissionError() {
        PermissionUtils.PermissionDeniedDialog
                .newInstance(true).show(getSupportFragmentManager(), "dialog");
    }
}
