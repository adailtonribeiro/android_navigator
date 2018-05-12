package com.adailtonribeiro.android_navigator;

import android.location.Location;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.annotations.Marker;
import com.mapbox.mapboxsdk.annotations.MarkerOptions;
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerMode;
import com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerPlugin;
import com.mapbox.services.android.location.LostLocationEngine;
import com.mapbox.services.android.navigation.ui.v5.NavigationLauncher;
import com.mapbox.services.android.navigation.ui.v5.NavigationViewOptions;
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.services.android.telemetry.location.LocationEngine;
import com.mapbox.services.android.telemetry.location.LocationEngineListener;
import com.mapbox.services.android.telemetry.location.LocationEnginePriority;
import com.mapbox.services.android.telemetry.permissions.PermissionsListener;
import com.mapbox.services.android.telemetry.permissions.PermissionsManager;
// classes to calculate a route
import com.mapbox.services.android.navigation.ui.v5.route.NavigationMapRoute;
import com.mapbox.services.android.navigation.v5.navigation.NavigationRoute;
import com.mapbox.api.directions.v5.models.DirectionsResponse;
import com.mapbox.api.directions.v5.models.DirectionsRoute;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import java.util.List;

public class MainActivity extends AppCompatActivity implements LocationEngineListener, PermissionsListener {

  private MapView mapView;
  private MapboxMap map;
  private PermissionsManager permissionsManager;
  private LocationLayerPlugin locationPlugin;
  private LocationEngine locationEngine;
  private Location originLocation;
  private Marker destinationMarker;
  private LatLng originCoord;
  private LatLng destinationCoord;
  private Button button;

  // variables for calculating and drawing a route
  private Point originPosition;
  private Point destinationPosition;
  private DirectionsRoute currentRoute;
  private static final String TAG = "DirectionsActivity";
  private NavigationMapRoute navigationMapRoute;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    Mapbox.getInstance(this, getString(R.string.access_token));
    setContentView(R.layout.activity_main);
    mapView = (MapView) findViewById(R.id.mapView);
    mapView.onCreate(savedInstanceState);

    mapView.getMapAsync(new OnMapReadyCallback() {
      @Override
      public void onMapReady(final MapboxMap mapboxMap) {
        map = mapboxMap;
        enableLocationPlugin();

        originCoord = new LatLng(originLocation.getLatitude(), originLocation.getLongitude());
        mapboxMap.addOnMapClickListener(new MapboxMap.OnMapClickListener() {
          @Override
          public void onMapClick(@NonNull LatLng point) {
            if (destinationMarker != null) {
              mapboxMap.removeMarker(destinationMarker);
            }
            destinationCoord = point;
            destinationMarker = mapboxMap.addMarker(new MarkerOptions()
              .position(destinationCoord)
            );

            destinationPosition = Point.fromLngLat(destinationCoord.getLongitude(), destinationCoord.getLatitude());
            originPosition = Point.fromLngLat(originCoord.getLongitude(), originCoord.getLatitude());
            getRoute(originPosition, destinationPosition);

            button.setEnabled(true);
            button.setBackgroundResource(R.color.mapboxBlue);

          };
        });


      };
    });

    button = findViewById(R.id.startButton);
    button.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) {
        Point origin = originPosition;
        Point destination = destinationPosition;
        // Pass in your Amazon Polly pool id for speech synthesis using Amazon Polly
        // Set to null to use the default Android speech synthesizer
        String awsPoolId = null;
        boolean simulateRoute = true;
        NavigationViewOptions options = NavigationViewOptions.builder()
          .origin(origin)
          .destination(destination)
          .awsPoolId(awsPoolId)
          .shouldSimulateRoute(simulateRoute)
          .build();

        // Call this method with Context from within an Activity
        NavigationLauncher.startNavigation(MainActivity.this, options);
      }
    });

  }

  @SuppressWarnings( {"MissingPermission"})
  private void enableLocationPlugin() {
    // Check if permissions are enabled and if not request
    if (PermissionsManager.areLocationPermissionsGranted(this)) {
      // Create an instance of LOST location engine
      initializeLocationEngine();

      locationPlugin = new LocationLayerPlugin(mapView, map, locationEngine);
      locationPlugin.setLocationLayerEnabled(LocationLayerMode.TRACKING);
    } else {
      permissionsManager = new PermissionsManager(this);
      permissionsManager.requestLocationPermissions(this);
    }
  }

  @SuppressWarnings( {"MissingPermission"})
  private void initializeLocationEngine() {
    locationEngine = new LostLocationEngine(MainActivity.this);
    locationEngine.setPriority(LocationEnginePriority.HIGH_ACCURACY);
    locationEngine.activate();

    Location lastLocation = locationEngine.getLastLocation();
    if (lastLocation != null) {
      originLocation = lastLocation;
      setCameraPosition(lastLocation);
    } else {
      locationEngine.addLocationEngineListener(this);
    }
  }

  private void setCameraPosition(Location location) {
    map.animateCamera(CameraUpdateFactory.newLatLngZoom(
      new LatLng(location.getLatitude(), location.getLongitude()), 13));
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  private void getRoute(Point origin, Point destination) {
    NavigationRoute.builder()
      .accessToken(Mapbox.getAccessToken())
      .origin(origin)
      .destination(destination)
      .build()
      .getRoute(new Callback<DirectionsResponse>() {
        @Override
        public void onResponse(Call<DirectionsResponse> call, Response<DirectionsResponse> response) {
          // You can get the generic HTTP info about the response
          Log.d(TAG, "Response code: " + response.code());
          if (response.body() == null) {
            Log.e(TAG, "No routes found, make sure you set the right user and access token.");
            return;
          } else if (response.body().routes().size() < 1) {
            Log.e(TAG, "No routes found");
            return;
          }

          currentRoute = response.body().routes().get(0);

          // Draw the route on the map
          if (navigationMapRoute != null) {
            navigationMapRoute.removeRoute();
          } else {
            navigationMapRoute = new NavigationMapRoute(null, mapView, map, R.style.NavigationMapRoute);
          }
          navigationMapRoute.addRoute(currentRoute);
        }

        @Override
        public void onFailure(Call<DirectionsResponse> call, Throwable throwable) {
          Log.e(TAG, "Error: " + throwable.getMessage());
        }
      });
  }

  @Override
  @SuppressWarnings( {"MissingPermission"})
  protected void onStart() {
    super.onStart();
    if (locationEngine != null) {
      locationEngine.requestLocationUpdates();
    }
    if (locationPlugin != null) {
      locationPlugin.onStart();
    }
    mapView.onStart();
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (locationEngine != null) {
      locationEngine.removeLocationUpdates();
    }
    if (locationPlugin != null) {
      locationPlugin.onStop();
    }
    mapView.onStop();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mapView.onDestroy();
    if (locationEngine != null) {
      locationEngine.deactivate();
    }
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    mapView.onLowMemory();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mapView.onResume();
  }

  @Override
  protected void onPause() {
    super.onPause();
    mapView.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    mapView.onSaveInstanceState(outState);
  }


  @Override
  public void onConnected() {

  }

  @Override
  public void onLocationChanged(Location location) {

  }

  @Override
  public void onExplanationNeeded(List<String> permissionsToExplain) {

  }

  @Override
  public void onPermissionResult(boolean granted) {

  }
}
