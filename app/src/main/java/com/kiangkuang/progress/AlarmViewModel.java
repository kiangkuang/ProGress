package com.kiangkuang.progress;

import android.graphics.Color;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.location.places.Place;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class AlarmViewModel implements Parcelable {

  private Mode mode = Mode.NONE;
  private Status status = Status.NONE;
  private Double latitude = null;
  private Double longitude = null;
  private Double radius = null;

  public AlarmViewModel(@NonNull Mode mode, @NonNull Status status) {
    this.mode = mode;
    this.status = status;
  }

  public Double getLatitude() {
    return latitude;
  }

  public void setLatitude(Double latitude) {
    this.latitude = latitude;
  }

  public Double getLongitude() {
    return longitude;
  }

  public void setLongitude(Double longitude) {
    this.longitude = longitude;
  }

  public Mode getMode() {
    return mode;
  }

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  public Double getRadius() {
    return radius;
  }

  public void setRadius(Double radius) {
    this.radius = radius;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public MarkerOptions getMarkerOptions(@NonNull Place place){
    LatLng latLng = place.getLatLng();
    return getMarkerOptions(latLng.latitude, latLng.longitude, place.getName(), place.getAddress(), false);
  }

  public MarkerOptions getMarkerOptions() {
    if (latitude == null || longitude == null) {
      throw new IllegalStateException("Latitude/longitude not set");
    }

    return getMarkerOptions(latitude, longitude, "Destination", "Hold marker to drag to new position", true);
  }

  private MarkerOptions getMarkerOptions(@Nullable  Double latitude, @Nullable Double longitude,
                                         @Nullable CharSequence title, @Nullable CharSequence description, boolean isDraggable) {
    if (latitude == null || longitude == null) {
      throw new IllegalArgumentException("Latitude/longitude invalid");
    }

    MarkerOptions options = new MarkerOptions();
    options.position(new LatLng(latitude, longitude));
    options.draggable(isDraggable);
    options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
    String titleString;
    if(title != null && (titleString = title.toString().trim()).length() > 0){
      options.title(titleString);
    }
    String descriptionString;
    if(description != null && (descriptionString = description.toString().trim()).length() > 0) {
      options.snippet(descriptionString);
    }
    options.alpha(0.7f);
    return options;
  }

  public CircleOptions getCircleOptions() {
    if (latitude == null || longitude == null) {
      throw new IllegalStateException("Latitude/longitude not set");
    }

    if (radius == null) {
      throw new IllegalStateException("Radius not set");
    }

    return new CircleOptions()
      .center(new LatLng(latitude, longitude))
      .radius(Math.max(radius, 50))
      .fillColor(Color.argb(50, 0, 0, 255))
      .strokeColor(Color.argb(100, 0, 0, 255));
  }

  public void onRestoreInstanceState(@Nullable Parcelable parcelable) {
    if (parcelable != null && parcelable instanceof AlarmViewModel) {
      AlarmViewModel alarmViewModel = (AlarmViewModel) parcelable;
      this.mode = alarmViewModel.mode;
      this.status = alarmViewModel.status;
      this.latitude = alarmViewModel.latitude;
      this.longitude = alarmViewModel.longitude;
      this.radius = alarmViewModel.radius;
    }
  }

  public Parcelable onSaveInstanceState() {
    return this;
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeInt(this.mode == null ? -1 : this.mode.ordinal());
    dest.writeInt(this.status == null ? -1 : this.status.ordinal());
    dest.writeValue(this.latitude);
    dest.writeValue(this.longitude);
    dest.writeValue(this.radius);
  }

  protected AlarmViewModel(Parcel in) {
    int tmpMode = in.readInt();
    this.mode = tmpMode == -1 ? null : Mode.values()[tmpMode];
    int tmpStatus = in.readInt();
    this.status = tmpStatus == -1 ? null : Status.values()[tmpStatus];
    this.latitude = (Double) in.readValue(Double.class.getClassLoader());
    this.longitude = (Double) in.readValue(Double.class.getClassLoader());
    this.radius = (Double) in.readValue(Double.class.getClassLoader());
  }

  public static final Creator<AlarmViewModel> CREATOR = new Creator<AlarmViewModel>() {
    @Override
    public AlarmViewModel createFromParcel(Parcel source) {
      return new AlarmViewModel(source);
    }

    @Override
    public AlarmViewModel[] newArray(int size) {
      return new AlarmViewModel[size];
    }
  };
}


