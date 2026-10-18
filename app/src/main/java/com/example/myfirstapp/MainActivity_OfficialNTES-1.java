package com.example.myfirstapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.TranslateAnimation;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.DatePicker;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private WebView webView;
    private Button drivingButton;
    private TextView distanceNote;
    private static final int LOCATION_REQUEST = 101;

    private static final String WEBSITE_URL =
            "https://beautiful-liger-ce28f9.netlify.app/";
    private static final String TRUSTED_HOST =
            "beautiful-liger-ce28f9.netlify.app";

    private static final String PREFS = "TravelkitPrefs";
    private static final String LOG_KEY = "city_visit_log";
    private static final String NAME_KEY = "user_name";
    private static final String HOME_ADDRESS_KEY = "home_address";
    private static final String HOME_LAT_KEY = "home_latitude";
    private static final String HOME_LON_KEY = "home_longitude";
    private static final String TRAIN_NO_KEY = "train_number";
    private static final String TRAIN_DATE_KEY = "train_date";
    private static final String TRAIN_STATION_KEY = "train_alert_station";
    private static final String TRAIN_CHANNEL_ID = "travelkit_train_alerts";

    private boolean pendingLogRequest = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHomeScreen();
    }

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasFineLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showHomeScreen() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(24, 16, 24, 24);
        layout.setBackgroundColor(Color.WHITE);

        Button menuButton = new Button(this);
        menuButton.setText("☰  User Details");
        layout.addView(menuButton);
        setFullWidth(menuButton, 10);
        menuButton.setOnClickListener(v -> showUserDetails());

        String name = prefs().getString(NAME_KEY, "");
        TextView title = new TextView(this);
        title.setText(name.isEmpty() ? "Welcome to Travelkit" : "Welcome, " + name + "!");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = 22;
        layout.addView(title, titleParams);

        Button locationButton = new Button(this);
        locationButton.setText("Current Location");
        layout.addView(locationButton);
        setFullWidth(locationButton, 10);
        locationButton.setOnClickListener(v -> {
            if (hasLocationPermission()) {
                openWebsite();
            } else {
                pendingLogRequest = false;
                requestLocationPermission();
            }
        });

        drivingButton = new Button(this);
        drivingButton.setText("Drive to Home");
        layout.addView(drivingButton);
        setFullWidth(drivingButton, 6);
        drivingButton.setOnClickListener(v -> openDrivingDirections());

        distanceNote = new TextView(this);
        String savedAddress = prefs().getString(HOME_ADDRESS_KEY, "");
        distanceNote.setText(savedAddress.isEmpty()
                ? "Set your home in User Details"
                : "Home: " + savedAddress);
        distanceNote.setTextSize(14);
        distanceNote.setTextColor(Color.DKGRAY);
        distanceNote.setGravity(Gravity.CENTER);
        layout.addView(distanceNote);

        Button logButton = new Button(this);
        logButton.setText("Log a City Visit");
        layout.addView(logButton);
        setFullWidth(logButton, 10);
        logButton.setOnClickListener(v -> startCityLogging());

        Button trainButton = new Button(this);
        trainButton.setText("Train Journey Tracker");
        layout.addView(trainButton);
        setFullWidth(trainButton, 10);
        trainButton.setOnClickListener(v -> showTrainTracker());

        Button viewLogButton = new Button(this);
        viewLogButton.setText("View Travel Log");
        layout.addView(viewLogButton);
        setFullWidth(viewLogButton, 0);
        viewLogButton.setOnClickListener(v -> showTravelLog());

        setContentView(layout);

        if (hasLocationPermission() && prefs().contains(HOME_LAT_KEY)
                && prefs().contains(HOME_LON_KEY)) {
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

    private void showUserDetails() {
        Dialog drawer = new Dialog(this);
        drawer.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(24, 24, 24, 24);
        panel.setBackgroundColor(Color.WHITE);

        TextView heading = new TextView(this);
        heading.setText("User Details");
        heading.setTextSize(24);
        heading.setTextColor(Color.BLACK);
        panel.addView(heading);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Your name");
        nameInput.setSingleLine(true);
        nameInput.setText(prefs().getString(NAME_KEY, ""));
        panel.addView(nameInput);

        Button saveName = new Button(this);
        saveName.setText("Save Name");
        panel.addView(saveName);
        saveName.setOnClickListener(v -> {
            prefs().edit().putString(NAME_KEY,
                    nameInput.getText().toString().trim()).apply();
            Toast.makeText(this, "Name saved offline", Toast.LENGTH_SHORT).show();
            drawer.dismiss();
            showHomeScreen();
        });

        TextView homeLabel = new TextView(this);
        String oldAddress = prefs().getString(HOME_ADDRESS_KEY, "");
        homeLabel.setText(oldAddress.isEmpty()
                ? "Home location: Not set"
                : "Saved home: " + oldAddress);
        homeLabel.setTextColor(Color.DKGRAY);
        homeLabel.setPadding(0, 18, 0, 12);
        panel.addView(homeLabel);

        Button chooseHome = new Button(this);
        chooseHome.setText("Pinpoint Home on Map");
        panel.addView(chooseHome);
        chooseHome.setOnClickListener(v -> {
            drawer.dismiss();
            showHomeMapPicker();
        });

        Button close = new Button(this);
        close.setText("Close");
        panel.addView(close);
        close.setOnClickListener(v -> drawer.dismiss());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);
        drawer.setContentView(scroll);
        drawer.show();

        Window window = drawer.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.white);
            window.setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.88),
                    WindowManager.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.START);
            window.setWindowAnimations(android.R.style.Animation_Dialog);
        }
    }

    private void showHomeMapPicker() {
        Dialog mapDialog = new Dialog(this);
        mapDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);

        TextView instructions = new TextView(this);
        instructions.setText("Tap the map at your home's location. Move the pin by tapping another spot, then press Save Home.");
        instructions.setTextColor(Color.BLACK);
        instructions.setPadding(14, 12, 14, 12);
        root.addView(instructions);

        WebView map = new WebView(this);
        map.getSettings().setJavaScriptEnabled(true);
        map.getSettings().setDomStorageEnabled(true);
        map.setWebChromeClient(new WebChromeClient());
        map.addJavascriptInterface(new HomeMapBridge(mapDialog), "TravelkitMap");

        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(map, mapParams);

        Button cancel = new Button(this);
        cancel.setText("Cancel");
        root.addView(cancel);
        cancel.setOnClickListener(v -> mapDialog.dismiss());

        mapDialog.setContentView(root);
        mapDialog.show();

        Window window = mapDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.white);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }

        double initialLat = 11.3991;
        double initialLon = 79.6910;
        if (prefs().contains(HOME_LAT_KEY) && prefs().contains(HOME_LON_KEY)) {
            initialLat = prefs().getFloat(HOME_LAT_KEY, (float) initialLat);
            initialLon = prefs().getFloat(HOME_LON_KEY, (float) initialLon);
        } else {
            Location current = getLastKnownLocation();
            if (current != null) {
                initialLat = current.getLatitude();
                initialLon = current.getLongitude();
            }
        }

        String html = "<!DOCTYPE html><html><head><meta name='viewport' " +
                "content='width=device-width, initial-scale=1.0'>" +
                "<link rel='stylesheet' href='https://unpkg.com/leaflet@1.9.4/dist/leaflet.css'/>" +
                "<style>html,body,#map{height:100%;margin:0;padding:0}" +
                ".save{position:absolute;z-index:999;bottom:18px;left:10%;width:80%;" +
                "padding:14px;background:#1565c0;color:white;border:0;border-radius:8px;font-size:16px}" +
                "</style></head><body><div id='map'></div>" +
                "<button class='save' onclick='savePin()'>Save Home Pin</button>" +
                "<script src='https://unpkg.com/leaflet@1.9.4/dist/leaflet.js'></script>" +
                "<script>" +
                "var map=L.map('map').setView([" + initialLat + "," + initialLon + "],16);" +
                "L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png'," +
                "{maxZoom:19,attribution:'&copy; OpenStreetMap contributors'}).addTo(map);" +
                "var marker=L.marker([" + initialLat + "," + initialLon + "]).addTo(map);" +
                "var selectedLat=" + initialLat + ",selectedLon=" + initialLon + ";" +
                "map.on('click',function(e){selectedLat=e.latlng.lat;selectedLon=e.latlng.lng;" +
                "marker.setLatLng(e.latlng);});" +
                "function savePin(){TravelkitMap.saveHome(selectedLat,selectedLon);}" +
                "</script></body></html>";
        map.loadDataWithBaseURL("https://travelkit.local/", html,
                "text/html", "UTF-8", null);
    }

    private class HomeMapBridge {
        private final Dialog dialog;

        HomeMapBridge(Dialog dialog) {
            this.dialog = dialog;
        }

        @android.webkit.JavascriptInterface
        public void saveHome(double latitude, double longitude) {
            runOnUiThread(() -> {
                String address = String.format(Locale.getDefault(),
                        "Pinned location (%.5f, %.5f)", latitude, longitude);
                prefs().edit()
                        .putFloat(HOME_LAT_KEY, (float) latitude)
                        .putFloat(HOME_LON_KEY, (float) longitude)
                        .putString(HOME_ADDRESS_KEY, address)
                        .apply();
                Toast.makeText(MainActivity.this,
                        "Home location saved offline", Toast.LENGTH_LONG).show();
                dialog.dismiss();
                showHomeScreen();
            });
        }
    }

    private void requestLocationPermission() {
        requestPermissions(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, LOCATION_REQUEST);
    }

    private void updateDistance() {
        if (!hasLocationPermission()) return;
        try {
            Location location = getLastKnownLocation();
            if (location != null) showDistance(location);
            requestFreshLocation(new LocationCallback() {
                @Override
                public void onLocation(Location freshLocation) {
                    showDistance(freshLocation);
                }
                @Override
                public void onError(String message) {
                    if (distanceNote != null && location == null) {
                        distanceNote.setText(message);
                    }
                }
            });
        } catch (Exception e) {
            if (distanceNote != null) distanceNote.setText("Location unavailable");
        }
    }

    private Location getLastKnownLocation() {
        if (!hasLocationPermission()) return null;
        try {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location best = null;
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                best = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                Location network = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                if (network != null && (best == null || network.getTime() > best.getTime())) {
                    best = network;
                }
            }
            return best;
        } catch (Exception e) {
            return null;
        }
    }

    private interface LocationCallback {
        void onLocation(Location location);
        void onError(String message);
    }

    @SuppressWarnings("deprecation")
    private void requestFreshLocation(LocationCallback callback) {
        if (!hasLocationPermission()) {
            callback.onError("Location permission required");
            return;
        }
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        try {
            String provider = hasFineLocationPermission()
                    && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            if (!manager.isProviderEnabled(provider)) {
                callback.onError("Please turn on phone Location");
                return;
            }
            manager.requestSingleUpdate(provider, new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    callback.onLocation(location);
                }
            }, Looper.getMainLooper());
        } catch (SecurityException e) {
            callback.onError("Location permission required");
        } catch (Exception e) {
            callback.onError("Unable to get GPS location");
        }
    }

    private void showDistance(Location current) {
        if (!prefs().contains(HOME_LAT_KEY) || !prefs().contains(HOME_LON_KEY)) {
            runOnUiThread(() -> {
                if (drivingButton != null) drivingButton.setText("Drive to Home");
                if (distanceNote != null) distanceNote.setText("Set your home in User Details");
            });
            return;
        }
        Location destination = new Location("home");
        destination.setLatitude(prefs().getFloat(HOME_LAT_KEY, 0f));
        destination.setLongitude(prefs().getFloat(HOME_LON_KEY, 0f));
        float km = current.distanceTo(destination) / 1000f;
        runOnUiThread(() -> {
            if (drivingButton != null) {
                drivingButton.setText(String.format(Locale.getDefault(),
                        "Drive to Home • %.1f km", km));
            }
            if (distanceNote != null) {
                distanceNote.setText("Home: " +
                        prefs().getString(HOME_ADDRESS_KEY, "Saved home")
                        + "\nApproximate straight-line distance");
            }
        });
    }

    private void openDrivingDirections() {
        if (!prefs().contains(HOME_LAT_KEY) || !prefs().contains(HOME_LON_KEY)) {
            new AlertDialog.Builder(this)
                    .setTitle("Home not set")
                    .setMessage("Open User Details and save your home location using a Google Maps share link first.")
                    .setPositiveButton("Open User Details",
                            (dialog, which) -> showUserDetails())
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        double lat = prefs().getFloat(HOME_LAT_KEY, 0f);
        double lon = prefs().getFloat(HOME_LON_KEY, 0f);
        Uri uri = Uri.parse("https://www.google.com/maps/dir/?api=1"
                + "&destination=" + lat + "," + lon
                + "&travelmode=driving");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Google Maps", Toast.LENGTH_LONG).show();
        }
    }

    private void showTrainTracker() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(22, 16, 22, 16);

        EditText trainNumberInput = new EditText(this);
        trainNumberInput.setHint("Train number (e.g. 12635)");
        trainNumberInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        trainNumberInput.setText(prefs().getString(TRAIN_NO_KEY, ""));
        panel.addView(trainNumberInput);

        Button dateButton = new Button(this);
        String savedDate = prefs().getString(TRAIN_DATE_KEY, "");
        dateButton.setText(savedDate.isEmpty() ? "Choose departure date" : "Departure date: " + savedDate);
        panel.addView(dateButton);

        final String[] chosenDate = {savedDate};
        dateButton.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            try {
                if (!chosenDate[0].isEmpty()) {
                    String[] parts = chosenDate[0].split("-");
                    cal.set(Integer.parseInt(parts[0]),
                            Integer.parseInt(parts[1]) - 1,
                            Integer.parseInt(parts[2]));
                }
            } catch (Exception ignored) { }

            DatePickerDialog picker = new DatePickerDialog(this,
                    (DatePicker view, int year, int month, int day) -> {
                        chosenDate[0] = String.format(Locale.US,
                                "%04d-%02d-%02d", year, month + 1, day);
                        dateButton.setText("Departure date: " + chosenDate[0]);
                    },
                    cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
            picker.show();
        });

        EditText stationInput = new EditText(this);
        stationInput.setHint("Alert station name or code");
        stationInput.setText(prefs().getString(TRAIN_STATION_KEY, ""));
        panel.addView(stationInput);

        TextView note = new TextView(this);
        note.setText(
                "Official NTES enquiry opens separately. Check the train's route there, " +
                "then enter the station you want to be alerted for here. " +
                "This version stores your choices but cannot read live NTES status automatically.");
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, 10, 0, 12);
        panel.addView(note);

        Button save = new Button(this);
        save.setText("Save Journey Details");
        panel.addView(save);
        save.setOnClickListener(v -> {
            String train = trainNumberInput.getText().toString().trim();
            String station = stationInput.getText().toString().trim();
            if (train.isEmpty() || chosenDate[0].isEmpty() || station.isEmpty()) {
                Toast.makeText(this, "Enter train number, departure date and alert station",
                        Toast.LENGTH_LONG).show();
                return;
            }
            prefs().edit()
                    .putString(TRAIN_NO_KEY, train)
                    .putString(TRAIN_DATE_KEY, chosenDate[0])
                    .putString(TRAIN_STATION_KEY, station)
                    .apply();
            Toast.makeText(this, "Journey details saved on this phone",
                    Toast.LENGTH_LONG).show();
            showTrainTrackerSummary(train, chosenDate[0], station);
        });

        Button openNtes = new Button(this);
        openNtes.setText("Open Official NTES");
        panel.addView(openNtes);
        openNtes.setOnClickListener(v -> {
            String train = trainNumberInput.getText().toString().trim();
            String url = "https://enquiry.indianrail.gov.in/";
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e) {
                Toast.makeText(this, "Unable to open official NTES", Toast.LENGTH_LONG).show();
            }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);
        new AlertDialog.Builder(this)
                .setTitle("Train Journey Tracker")
                .setView(scroll)
                .setNegativeButton("Close", null)
                .show();
    }

    private void showTrainTrackerSummary(String train, String date, String station) {
        String summary = "Train number: " + train
                + "\nDeparture date: " + date
                + "\nAlert station: " + station
                + "\n\nYour journey details are saved offline."
                + "\n\nLive status: Open official NTES to check the current running status."
                + "\nAutomatic arrival alert: Not available in this official-only version.";
        new AlertDialog.Builder(this)
                .setTitle("Saved Train Journey")
                .setMessage(summary)
                .setPositiveButton("Open NTES", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://enquiry.indianrail.gov.in/")));
                    } catch (Exception e) {
                        Toast.makeText(this, "Unable to open NTES", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void startCityLogging() {
        if (!hasLocationPermission()) {
            pendingLogRequest = true;
            requestLocationPermission();
            return;
        }
        Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show();
        requestFreshLocation(new LocationCallback() {
            @Override
            public void onLocation(Location location) {
                reverseGeocode(location);
            }
            @Override
            public void onError(String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void reverseGeocode(Location location) {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String urlString = "https://nominatim.openstreetmap.org/reverse"
                        + "?format=jsonv2&lat=" + location.getLatitude()
                        + "&lon=" + location.getLongitude()
                        + "&addressdetails=1";
                connection = (HttpURLConnection) new URL(urlString).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("User-Agent", "TravelkitAndroid/1.0");
                connection.setRequestProperty("Accept", "application/json");

                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    showLookupError();
                    return;
                }

                StringBuilder response = new StringBuilder();
                try (InputStream stream = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }

                JSONObject result = new JSONObject(response.toString());
                JSONObject address = result.optJSONObject("address");
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
                runOnUiThread(() -> confirmAndSaveVisit(detectedCity, location));
            } catch (Exception e) {
                showLookupError();
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return "";
    }

    private void showLookupError() {
        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                "Could not identify the city. Check internet and try again.",
                Toast.LENGTH_LONG).show());
    }

    private void confirmAndSaveVisit(String city, Location location) {
        String message = "Detected city: " + city
                + "\n\nLatitude: " + location.getLatitude()
                + "\nLongitude: " + location.getLongitude()
                + "\n\nSave this visit?";
        new AlertDialog.Builder(this)
                .setTitle("Confirm City Visit")
                .setMessage(message)
                .setPositiveButton("Save Visit", (dialog, which) -> saveVisit(city, location))
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
            prefs().edit().putString(LOG_KEY, logs.toString()).apply();
            Toast.makeText(this, city + " saved to your offline travel log",
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not save visit", Toast.LENGTH_LONG).show();
        }
    }

    private JSONArray getSavedLogs() {
        String saved = prefs().getString(LOG_KEY, "[]");
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
            String date = new SimpleDateFormat("dd MMM yyyy, hh:mm a",
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
            delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setTitle("Delete visit?")
                    .setMessage("Remove " + city + " from your log?")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", (dialog, which) -> deleteVisit(index))
                    .show());
        }

        TextView attribution = new TextView(this);
        attribution.setText("City lookup data © OpenStreetMap contributors");
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
            if (i != index) newLogs.put(oldLogs.optJSONObject(i));
        }
        prefs().edit().putString(LOG_KEY, newLogs.toString()).apply();
        showTravelLog();
    }

    private void openWebsite() {
        if (webView != null) webView.destroy();
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
                Uri uri = Uri.parse(origin);
                boolean trusted = "https".equalsIgnoreCase(uri.getScheme())
                        && TRUSTED_HOST.equalsIgnoreCase(uri.getHost());
                callback.invoke(origin, trusted && hasLocationPermission(), false);
            }
        });
        setContentView(webView);
        webView.loadUrl(WEBSITE_URL);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
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
                Toast.makeText(this, "Please allow location permission.",
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
