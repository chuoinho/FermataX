package me.aap.fermata.location;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Looper;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.*;
import java.util.function.Consumer;

abstract class GoogleFusedSource {
 interface Factory {
  GoogleFusedSource create(Context context, Looper looper, Consumer<Location> sink);
 }

 static final Factory DEFAULT = (context, looper, sink) -> {
  if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    != ConnectionResult.SUCCESS) return null;
  FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
  return new GoogleFusedSource() {
   final LocationCallback callback = new LocationCallback() {
    @Override public void onLocationResult(LocationResult result) {
     for (Location location : result.getLocations()) sink.accept(location);
    }
   };
   @SuppressLint("MissingPermission")
   @Override public void start() {
    client.requestLocationUpdates(new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
      .setMinUpdateIntervalMillis(1000).setMinUpdateDistanceMeters(0).build(), callback, looper)
      .addOnFailureListener(error -> LocationHub.googleRegistrationFailed(error));
   }
   @Override public void stop() { client.removeLocationUpdates(callback); }
  };
 };

 abstract void start();
 abstract void stop();
}
