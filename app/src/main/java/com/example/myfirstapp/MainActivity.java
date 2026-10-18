package com.example.myfirstapp;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private WebView webView;
    private Button drivingButton;
    private TextView distanceNote;
    private LocationManager locationManager;

    private static final int LOCATION_REQUEST = 101;

    private static final double CHIDAMBARAM_LAT = 11.3991;
    private static final double CHIDAMBARAM_LON = 79.6910;

    private static final String WEBSITE_URL =
            "https://beautiful-liger-ce28f9.netlify.app/";

    private static final String TRUSTED_HOST =
            "beautiful-liger-ce28f9.netlify.app";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHomeScreen();
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showHomeScreen() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(24, 24, 24, 24);
        layout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Welcome to Travelkit");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = 32;
        layout.addView(title, titleParams);

        Button locationButton = new Button(this);
        locationButton.setText("Current Location");
        layout.addView(locationButton);

        LinearLayout.LayoutParams buttonParams =
                (LinearLayout.LayoutParams)
                        locationButton.getLayoutParams();
        buttonParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        buttonParams.bottomMargin = 16;
        locationButton.setLayoutParams(buttonParams);

        locationButton.setOnClickListener(v -> {
            if (hasLocationPermission()) {
                openWebsite();
            } else {
                requestPermissions(
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        LOCATION_REQUEST);
            }
        });

        drivingButton = new Button(this);
        drivingButton.setText("Drive to Chidambaram");
        layout.addView(drivingButton);

        LinearLayout.LayoutParams driveParams =
                (LinearLayout.LayoutParams)
                        drivingButton.getLayoutParams();
        driveParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        drivingButton.setLayoutParams(driveParams);

        drivingButton.setOnClickListener(v -> openDrivingDirections());

        distanceNote = new TextView(this);
        distanceNote.setText("Approximate distance: locating...");
        distanceNote.setTextSize(14);
        distanceNote.setTextColor(Color.DKGRAY);
        distanceNote.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams noteParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = 12;
        layout.addView(distanceNote, noteParams);

        setContentView(layout);

        if (hasLocationPermission()) {
            updateDistance();
        } else {
            distanceNote.setText(
                    "Allow location to calculate distance");
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_REQUEST);
        }
    }

    private void updateDistance() {
        if (!hasLocationPermission()) {
            distanceNote.setText("Location permission required");
            return;
        }

        locationManager =
                (LocationManager) getSystemService(LOCATION_SERVICE);

        try {
            Location lastLocation = null;

            if (locationManager.isProviderEnabled(
                    LocationManager.GPS_PROVIDER)) {
                lastLocation = locationManager.getLastKnownLocation(
                        LocationManager.GPS_PROVIDER);
            }

            if (lastLocation == null
                    && locationManager.isProviderEnabled(
                    LocationManager.NETWORK_PROVIDER)) {
                lastLocation = locationManager.getLastKnownLocation(
                        LocationManager.NETWORK_PROVIDER);
            }

            if (lastLocation != null) {
                showDistance(lastLocation);
            } else {
                distanceNote.setText(
                        "Finding GPS location...");
            }

            String provider =
                    hasFineLocationPermission()
                    && locationManager.isProviderEnabled(
                    LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER
                    : LocationManager.NETWORK_PROVIDER;

            if (locationManager.isProviderEnabled(provider)) {
                locationManager.requestSingleUpdate(
                        provider,
                        new LocationListener() {
                            @Override
                            public void onLocationChanged(
                                    Location location) {
                                showDistance(location);
                            }
                        },
                        Looper.getMainLooper());
            } else if (lastLocation == null) {
                distanceNote.setText(
                        "Please turn on phone Location");
            }

        } catch (SecurityException e) {
            distanceNote.setText("Location permission required");
        } catch (Exception e) {
            distanceNote.setText(
                    "Unable to get location. Try again.");
        }
    }

    private boolean hasFineLocationPermission() {
        return checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showDistance(Location currentLocation) {
        Location destination = new Location("destination");
        destination.setLatitude(CHIDAMBARAM_LAT);
        destination.setLongitude(CHIDAMBARAM_LON);

        float distanceKm =
                currentLocation.distanceTo(destination) / 1000f;

        String distanceText = String.format(
                Locale.getDefault(),
                "%.1f km away",
                distanceKm);

        runOnUiThread(() -> {
            if (drivingButton != null) {
                drivingButton.setText(
                        "Drive to Chidambaram • " + distanceText);
            }
            if (distanceNote != null) {
                distanceNote.setText(
                        "Approximate straight-line distance");
            }
        });
    }

    private void openDrivingDirections() {
        String mapsUrl =
                "https://www.google.com/maps/dir/?api=1"
                + "&destination=Chidambaram%2C"
                + "Tamil%20Nadu%2C%20India"
                + "&travelmode=driving";

        Intent intent = new Intent(
                Intent.ACTION_VIEW,
                Uri.parse(mapsUrl));

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(
                    this,
                    "Unable to open Maps. Please try again.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void openWebsite() {
        if (webView != null) {
            webView.destroy();
        }

        webView = new WebView(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);

        webView.setWebViewClient(new WebViewClient());

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin,
                    GeolocationPermissions.Callback callback) {

                Uri uri = Uri.parse(origin);

                boolean trustedWebsite =
                        "https".equalsIgnoreCase(uri.getScheme())
                        && TRUSTED_HOST.equalsIgnoreCase(uri.getHost());

                callback.invoke(
                        origin,
                        trustedWebsite && hasLocationPermission(),
                        false);
            }
        });

        setContentView(webView);
        webView.loadUrl(WEBSITE_URL);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {

        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);

        if (requestCode == LOCATION_REQUEST) {
            if (hasLocationPermission()) {
                if (webView == null) {
                    updateDistance();
                }
            } else {
                Toast.makeText(
                        this,
                        "Please allow location permission.",
                        Toast.LENGTH_LONG).show();
                if (distanceNote != null) {
                    distanceNote.setText(
                            "Location permission required");
                }
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else if (webView != null) {
            webView.destroy();
            webView = null;
            showHomeScreen();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(
                        (LocationListener) null);
            } catch (Exception ignored) {
                // No active listener to remove here.
            }
        }

        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}
