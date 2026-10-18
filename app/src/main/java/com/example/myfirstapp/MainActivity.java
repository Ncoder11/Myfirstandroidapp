
package com.example.myfirstapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
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

    private static final String PREFS = "TravelkitPrefs";
    private static final String LOG_KEY = "city_visit_log";

    private boolean pendingLogRequest = false;

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

    private boolean hasFineLocationPermission() {
        return checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION)
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
        titleParams.bottomMargin = 24;
        layout.addView(title, titleParams);

        Button locationButton = new Button(this);
        locationButton.setText("Current Location");
        layout.addView(locationButton);
        setFullWidth(locationButton, 12);

        locationButton.setOnClickListener(v -> {
            if (hasLocationPermission()) {
                openWebsite();
            } else {
                pendingLogRequest = false;
                requestLocationPermission();
            }
        });

        drivingButton = new Button(this);
        drivingButton.setText("Drive to Chidambaram");
        layout.addView(drivingButton);
        setFullWidth(drivingButton, 12);
        drivingButton.setOnClickListener(v -> openDrivingDirections());

        distanceNote = new TextView(this);
        distanceNote.setText("Approximate distance");
        distanceNote.setTextSize(14);
        distanceNote.setTextColor(Color.DKGRAY);
        distanceNote.setGravity(Gravity.CENTER);
        layout.addView(distanceNote);

        Button logButton = new Button(this);
        logButton.setText("Log a City Visit");
        layout.addView(logButton);
        setFullWidth(logButton, 12);
        logButton.setOnClickListener(v -> startCityLogging());

        Button viewLogButton = new Button(this);
        viewLogButton.setText("View Travel Log");
        layout.addView(viewLogButton);
        setFullWidth(viewLogButton, 0);
        viewLogButton.setOnClickListener(v -> showTravelLog());

        setContentView(layout);

        if (hasLocationPermission()) {
            updateDistance();
        }
    }

    private void setFullWidth(Button button, int bottomMargin) {
        LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) button.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.bottomMargin = bottomMargin;
        button.setLayoutParams(params);
    }

    private void requestLocationPermission() {
        requestPermissions(
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_REQUEST);
    }

    private void updateDistance() {
        if (!hasLocationPermission()) return;

        locationManager =
                (LocationManager) getSystemService(LOCATION_SERVICE);

        try {
            Location location = getLastKnownLocation();

            if (location != null) {
                showDistance(location);
            }

            requestFreshLocation(new LocationCallback() {
                @Override
                public void onLocation(Location freshLocation) {
                    showDistance(freshLocation);
                }

                @Override
                public void onError(String message) {
                    if (location == null && distanceNote != null) {
                        distanceNote.setText(message);
                    }
                }
            });
        } catch (Exception e) {
            distanceNote.setText("Location unavailable");
        }
    }

    private Location getLastKnownLocation() {
        if (!hasLocationPermission()) return null;

        try {
            LocationManager manager =
                    (LocationManager) getSystemService(LOCATION_SERVICE);

            Location best = null;

            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                best = manager.getLastKnownLocation(
                        LocationManager.GPS_PROVIDER);
            }

            if (manager.isProviderEnabled(
                    LocationManager.NETWORK_PROVIDER)) {
                Location network = manager.getLastKnownLocation(
                        LocationManager.NETWORK_PROVIDER);

                if (network != null &&
                        (best == null ||
                        network.getTime() > best.getTime())) {
                    best = network;
                }
            }

            return best;
        } catch (SecurityException e) {
            return null;
        }
    }

    private interface LocationCallback {
        void onLocation(Location location);
        void onError(String message);
    }

    private void requestFreshLocation(LocationCallback callback) {
        if (!hasLocationPermission()) {
            callback.onError("Location permission required");
            return;
        }

        LocationManager manager =
                (LocationManager) getSystemService(LOCATION_SERVICE);

        try {
            String provider = hasFineLocationPermission()
                    && manager.isProviderEnabled(
                            LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER
                    : LocationManager.NETWORK_PROVIDER;

            if (!manager.isProviderEnabled(provider)) {
                callback.onError("Please turn on phone Location");
                return;
            }

            manager.requestSingleUpdate(
                    provider,
                    new LocationListener() {
                        @Override
                        public void onLocationChanged(Location location) {
                            callback.onLocation(location);
                        }
                    },
                    Looper.getMainLooper());

        } catch (SecurityException e) {
            callback.onError("Location permission required");
        } catch (Exception e) {
            callback.onError("Unable to get GPS location");
        }
    }

    private void showDistance(Location current) {
        Location destination = new Location("destination");
        destination.setLatitude(CHIDAMBARAM_LAT);
        destination.setLongitude(CHIDAMBARAM_LON);

        float km = current.distanceTo(destination) / 1000f;

        runOnUiThread(() -> {
            if (drivingButton != null) {
                drivingButton.setText(String.format(
                        Locale.getDefault(),
                        "Drive to Chidambaram • %.1f km",
                        km));
            }
            if (distanceNote != null) {
                distanceNote.setText(
                        "Approximate straight-line distance");
            }
        });
    }

    private void openDrivingDirections() {
        String url =
                "https://www.google.com/maps/dir/?api=1"
                + "&destination=Chidambaram%2C"
                + "Tamil%20Nadu%2C%20India"
                + "&travelmode=driving";

        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private void startCityLogging() {
        if (!hasLocationPermission()) {
            pendingLogRequest = true;
            requestLocationPermission();
            return;
        }

        Toast.makeText(this, "Getting your location...",
                Toast.LENGTH_SHORT).show();

        requestFreshLocation(new LocationCallback() {
            @Override
            public void onLocation(Location location) {
                reverseGeocode(location);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, message,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void reverseGeocode(Location location) {
        new Thread(() -> {
            HttpURLConnection connection = null;

            try {
                String urlString =
                        "https://nominatim.openstreetmap.org/reverse"
                        + "?format=jsonv2"
                        + "&lat=" + location.getLatitude()
                        + "&lon=" + location.getLongitude()
                        + "&addressdetails=1";

                URL url = new URL(urlString);
                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);

                connection.setRequestProperty(
                        "User-Agent",
                        "TravelkitAndroid/1.0");

                connection.setRequestProperty(
                        "Accept",
                        "application/json");

                int status = connection.getResponseCode();

                if (status != HttpURLConnection.HTTP_OK) {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,
                                    "City lookup failed. Check internet and try again.",
                                    Toast.LENGTH_LONG).show());
                    return;
                }

                StringBuilder response = new StringBuilder();

                try (InputStream stream =
                             connection.getInputStream();
                     BufferedReader reader =
                             new BufferedReader(
                                     new InputStreamReader(stream))) {

                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                JSONObject result =
                        new JSONObject(response.toString());

                JSONObject address =
                        result.optJSONObject("address");

                if (address == null) {
                    showLookupError();
                    return;
                }

                String city = firstNonEmpty(
                        address.optString("city", ""),
                        address.optString("town", ""),
                        address.optString("village", ""),
                        address.optString("municipality", ""),
                        address.optString("county", ""),
                        address.optString("state", ""));

                if (city.isEmpty()) {
                    showLookupError();
                    return;
                }

                String detectedCity = city;

                runOnUiThread(() ->
                        confirmAndSaveVisit(detectedCity, location));

            } catch (Exception e) {
                showLookupError();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }).start();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    private void showLookupError() {
        runOnUiThread(() ->
                Toast.makeText(MainActivity.this,
                        "Could not identify the city. Check internet and try again.",
                        Toast.LENGTH_LONG).show());
    }

    private void confirmAndSaveVisit(
            String city, Location location) {

        String message = "Detected city: " + city
                + "\n\nLatitude: " + location.getLatitude()
                + "\nLongitude: " + location.getLongitude()
                + "\n\nSave this visit?";

        new AlertDialog.Builder(this)
                .setTitle("Confirm City Visit")
                .setMessage(message)
                .setPositiveButton("Save Visit",
                        (dialog, which) ->
                                saveVisit(city, location))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveVisit(String city, Location location) {
        try {
            JSONArray logs = getSavedLogs();

            JSONObject entry = new JSONObject();
            entry.put("city", city);
            entry.put("time", System.currentTimeMillis());
            entry.put("latitude", location.getLatitude());
            entry.put("longitude", location.getLongitude());

            logs.put(entry);

            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putString(LOG_KEY, logs.toString())
                    .apply();

            Toast.makeText(this,
                    city + " saved to your offline travel log",
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(this, "Could not save visit",
                    Toast.LENGTH_LONG).show();
        }
    }

    private JSONArray getSavedLogs() {
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(LOG_KEY, "[]");

        try {
            return new JSONArray(saved);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void showTravelLog() {
        JSONArray logs = getSavedLogs();

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(24, 16, 24, 16);

        if (logs.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("No city visits saved yet.");
            empty.setTextSize(18);
            list.addView(empty);
        }

        for (int i = logs.length() - 1; i >= 0; i--) {
            JSONObject entry = logs.optJSONObject(i);
            if (entry == null) continue;

            String city = entry.optString("city", "Unknown city");
            long time = entry.optLong("time", 0);

            String date = new SimpleDateFormat(
                    "dd MMM yyyy, hh:mm a",
                    Locale.getDefault()).format(new Date(time));

            TextView row = new TextView(this);
            row.setText(city + "\n" + date);
            row.setTextSize(17);
            row.setTextColor(Color.BLACK);
            row.setPadding(8, 16, 8, 16);
            list.addView(row);

            Button delete = new Button(this);
            delete.setText("Delete this visit");
            list.addView(delete);

            final int index = i;
            delete.setOnClickListener(v ->
                    new AlertDialog.Builder(this)
                            .setTitle("Delete visit?")
                            .setMessage("Remove " + city + " from your log?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete",
                                    (dialog, which) ->
                                            deleteVisit(index))
                            .show());
        }

        TextView attribution = new TextView(this);
        attribution.setText(
                "City lookup data © OpenStreetMap contributors");
        attribution.setTextSize(12);
        attribution.setTextColor(Color.DKGRAY);
        attribution.setPadding(8, 16, 8, 8);
        list.addView(attribution);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(list);

        new AlertDialog.Builder(this)
                .setTitle("Your Offline Travel Log")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show();
    }

    private void deleteVisit(int index) {
        JSONArray oldLogs = getSavedLogs();
        JSONArray newLogs = new JSONArray();

        for (int i = 0; i < oldLogs.length(); i++) {
            if (i != index) {
                newLogs.put(oldLogs.optJSONObject(i));
            }
        }

        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putString(LOG_KEY, newLogs.toString())
                .apply();

        showTravelLog();
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

                boolean trusted =
                        "https".equalsIgnoreCase(uri.getScheme())
                        && TRUSTED_HOST.equalsIgnoreCase(uri.getHost());

                callback.invoke(
                        origin,
                        trusted && hasLocationPermission(),
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
                if (pendingLogRequest) {
                    pendingLogRequest = false;
                    startCityLogging();
                } else if (webView == null) {
                    updateDistance();
                } else {
                    openWebsite();
                }
            } else {
                Toast.makeText(this,
                        "Please allow location permission.",
                        Toast.LENGTH_LONG).show();
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
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
