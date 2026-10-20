package com.example.myfirstapp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.ClipData;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.MediaStore;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.content.res.Configuration;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Image;
import android.media.ImageReader;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.os.Handler;
import android.os.HandlerThread;
import android.location.Address;
import android.location.Geocoder;
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
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.Toast;
import android.view.MotionEvent;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.Locale;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private WebView webView;
    private Button drivingButton;
    private TextView distanceNote;
    private TextView homeWeatherText;
    private TextView weatherTemperatureText;
    private TextView weatherIconText;
    private static final int LOCATION_REQUEST = 101;
    private static final int GPS_CAMERA_REQUEST = 202;
    private Uri gpsCameraPhotoUri;
    private Location gpsCameraLocation;
    private String gpsCameraPlace = "Place unavailable";
    private String gpsCameraDistrict = "District unavailable";
    private String gpsCameraPin = "PIN unavailable";
    private int gpsCameraFacing = 0; // 0 = rear, 1 = front (best-effort camera-app hint)
    private String gpsCameraAspect = "4:3";

    // In-app Camera2 preview and live location overlay
    private TextureView gpsPreview;
    private Size gpsPreviewSize;
    private TextView gpsLiveOverlay;
    private CameraDevice gpsCameraDevice;
    private CameraCaptureSession gpsCameraSession;
    private ImageReader gpsImageReader;
    private HandlerThread gpsCameraThread;
    private Handler gpsCameraHandler;
    private Handler gpsUiHandler = new Handler(Looper.getMainLooper());
    private LocationManager gpsLiveLocationManager;
    private boolean gpsCameraScreenOpen = false;
    private boolean gpsHasFirstFix = false;
    private long gpsLastAddressLookup = 0L;
    private final Runnable gpsLocationRefreshRunnable = new Runnable() {
        @Override public void run() {
            if (!gpsCameraScreenOpen) return;
            refreshGpsCameraLocation();
            gpsUiHandler.postDelayed(this, gpsHasFirstFix ? 10000L : 1000L);
        }
    };

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
    private static final String BMI_AGE_KEY = "bmi_age";
    private static final String DOB_KEY = "age_calculator_dob";
    private static final String BMI_HEIGHT_KEY = "bmi_height_cm";
    private static final String BMI_WEIGHT_LOG_KEY = "bmi_weight_log";
    private static final String WEATHER_CACHE_TIME = "weather_cache_time";
    private static final String WEATHER_CACHE_TEMP = "weather_cache_temp";
    private static final String WEATHER_CACHE_ICON = "weather_cache_icon";
    private static final String WEATHER_CACHE_TEXT = "weather_cache_text";
    private static final long WEATHER_REFRESH_MS = 5L * 60L * 1000L;

    private boolean pendingLogRequest = false;
    private String currentScreen = "home";
    private final java.util.ArrayList<String> screenHistory = new java.util.ArrayList<>();

    private void navigateTo(String destination) {
        if (destination == null || destination.equals(currentScreen)) return;
        screenHistory.add(currentScreen);
        renderScreen(destination);
    }

    private void renderScreen(String screen) {
        if (!"compass".equals(screen)) stopCompass();
        currentScreen = screen;
        if ("bmi".equals(screen)) showBmiScreen();
        else if ("age".equals(screen)) showAgeCalculatorScreen();
        else if ("travel".equals(screen)) showTravelHubScreen();
        else if ("utilities".equals(screen)) showUtilitiesHubScreen();
        else if ("train".equals(screen)) showTrainHubScreen();
        else if ("train_station".equals(screen)) showTrainSearchScreen("station");
        else if ("train_number".equals(screen)) showTrainSearchScreen("number");
        else if ("train_between".equals(screen)) showTrainSearchScreen("between");
        else if ("tamil_calendar".equals(screen)) showTamilCalendarScreen();
        else if ("weather".equals(screen)) showWeatherDetailScreen();
        else if ("weather_map".equals(screen)) showWeatherMapScreen();
        else if ("current_location".equals(screen)) showCurrentLocationScreen();
        else if ("compass".equals(screen)) showCompassScreen();
        else if ("news".equals(screen)) showNewsScreen();
        else if ("news_article".equals(screen)) showNewsArticleScreen(prefs().getString("news_open_url", "https://www.bbc.com/news"));
        else showHomeScreen();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if ("compass".equals(currentScreen) && compassSensorListener == null) {
            startCompass();
        }
    }

    @Override
    protected void onPause() {
        unregisterCompassSensors();
        super.onPause();
    }

    private static final String STATE_CURRENT_SCREEN = "travelkit_current_screen";
    private static final String STATE_SCREEN_HISTORY = "travelkit_screen_history";
    private static final String STATE_GPS_CAMERA_OPEN = "travelkit_gps_camera_open";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String restoreScreen = savedInstanceState == null ? null : savedInstanceState.getString(STATE_CURRENT_SCREEN);
        if (savedInstanceState != null) {
            ArrayList<String> restoredHistory = savedInstanceState.getStringArrayList(STATE_SCREEN_HISTORY);
            if (restoredHistory != null) {
                screenHistory.clear();
                screenHistory.addAll(restoredHistory);
            }
        }
        boolean restoreGpsCamera = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_GPS_CAMERA_OPEN, false)
                && "gps_camera".equals(restoreScreen);
        if (restoreGpsCamera) {
            // Restore the underlying Utilities page first. The camera is reopened below
            // only when the saved screen explicitly says GPS Camera was active.
            renderScreen("utilities");
        } else if (restoreScreen != null && !restoreScreen.isEmpty()
                && !"gps_camera".equals(restoreScreen)) {
            renderScreen(restoreScreen);
        } else {
            showHomeScreen();
        }
        if (restoreGpsCamera) {
            getWindow().getDecorView().postDelayed(() -> {
                if (!isFinishing() && !gpsCameraScreenOpen
                        && "utilities".equals(currentScreen)
                        && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    openInAppGpsCamera();
                }
            }, 350L);
        }
        // Ask once at startup so current-location weather can work without a separate tap.
        if (!hasLocationPermission()) requestLocationPermission();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_CURRENT_SCREEN, currentScreen);
        outState.putStringArrayList(STATE_SCREEN_HISTORY, new ArrayList<>(screenHistory));
        outState.putBoolean(STATE_GPS_CAMERA_OPEN,
                gpsCameraScreenOpen && "gps_camera".equals(currentScreen));
        super.onSaveInstanceState(outState);
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
        currentScreen = "home";
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(255, 255, 255));
        page.setPadding(dp(16), dp(12), dp(16), 0);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        String name = prefs().getString(NAME_KEY, "");
        title.setText(name.isEmpty() ? "Welcome to Travelkit" : "Welcome, " + name + "!");
        title.setTextSize(23);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.rgb(35, 47, 62));
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button user = new Button(this);
        user.setText("Profile"); user.setAllCaps(false); user.setTextSize(12);
        top.addView(user);
        user.setOnClickListener(v -> showUserDetails());
        page.addView(top);

        TextView hint = new TextView(this);
        hint.setText("What would you like to do today?");
        hint.setTextSize(14); hint.setTextColor(Color.rgb(90, 99, 110));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(4); hintParams.bottomMargin = dp(14);
        page.addView(hint, hintParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        firstRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(0, dp(158), 1f);
        tileParams.setMargins(dp(4), dp(4), dp(4), dp(4));
        LinearLayout weatherTile = makeWeatherHomeTile();
        LinearLayout travelTile = makeHomeTile("🧭", "Travel", "Location, visits & logs", Color.rgb(222, 244, 231));
        firstRow.addView(weatherTile, new LinearLayout.LayoutParams(tileParams));
        firstRow.addView(travelTile, new LinearLayout.LayoutParams(tileParams));
        grid.addView(firstRow);

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        secondRow.setGravity(Gravity.CENTER);
        LinearLayout utilityTile = makeHomeTile("🧮", "Utilities", "BMI, age & calendar", Color.rgb(255, 236, 214));
        LinearLayout.LayoutParams utilityParams = new LinearLayout.LayoutParams(0, dp(158), 1f);
        utilityParams.setMargins(dp(4), dp(4), dp(4), dp(4));
        secondRow.addView(utilityTile, new LinearLayout.LayoutParams(utilityParams));
        LinearLayout newsTile = makeHomeTile("📰", "News", "Your topics from many sources", Color.rgb(237, 227, 250));
        secondRow.addView(newsTile, new LinearLayout.LayoutParams(utilityParams));
        newsTile.setOnClickListener(v -> navigateTo("news"));
        grid.addView(secondRow);
        scroll.addView(grid);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout screenRoot = new LinearLayout(this);
        screenRoot.setOrientation(LinearLayout.VERTICAL);
        screenRoot.addView(page, new LinearLayout.LayoutParams(-1, 0, 1f));
        screenRoot.addView(createBottomNavigation(0));
        setContentView(screenRoot);
        weatherTile.setOnClickListener(v -> navigateTo("weather"));
        travelTile.setOnClickListener(v -> navigateTo("travel"));
        utilityTile.setOnClickListener(v -> navigateTo("utilities"));
        loadHomeWeather();
        if (hasLocationPermission() && prefs().contains(HOME_LAT_KEY) && prefs().contains(HOME_LON_KEY)) updateDistance();
    }

    private LinearLayout makeWeatherHomeTile() {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(10), dp(8), dp(10));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.rgb(222, 239, 255));
        bg.setCornerRadius(dp(20)); bg.setStroke(dp(1), Color.rgb(235, 235, 235));
        tile.setBackground(bg); tile.setClickable(true); tile.setFocusable(true);
        weatherIconText = new TextView(this); weatherIconText.setText("🌤️"); weatherIconText.setTextSize(34); weatherIconText.setGravity(Gravity.CENTER);
        tile.addView(weatherIconText);
        TextView label = new TextView(this); label.setText("Weather"); label.setTextSize(18); label.setTypeface(null, Typeface.BOLD);
        label.setTextColor(Color.rgb(38, 48, 60)); label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(5); tile.addView(label, lp);
        weatherTemperatureText = new TextView(this); weatherTemperatureText.setText("--°C"); weatherTemperatureText.setTextSize(22);
        weatherTemperatureText.setTypeface(null, Typeface.BOLD); weatherTemperatureText.setTextColor(Color.rgb(38, 48, 60)); weatherTemperatureText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2); tp.topMargin = dp(3); tile.addView(weatherTemperatureText, tp);
        return tile;
    }

    private LinearLayout makeHomeTile(String emoji, String label, String subtitle, int pastelColor) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(8), dp(10), dp(8), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(pastelColor); bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), Color.rgb(235, 235, 235));
        tile.setBackground(bg); tile.setClickable(true); tile.setFocusable(true);
        TextView icon = new TextView(this); icon.setText(emoji); icon.setTextSize(34); icon.setGravity(Gravity.CENTER);
        tile.addView(icon);
        TextView name = new TextView(this); name.setText(label); name.setTextSize(18);
        name.setTypeface(null, Typeface.BOLD); name.setTextColor(Color.rgb(38, 48, 60)); name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2); nameParams.topMargin = dp(5);
        tile.addView(name, nameParams);
        TextView sub = new TextView(this); sub.setText(subtitle); sub.setTextSize(11);
        sub.setTextColor(Color.rgb(65, 73, 82)); sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2); subParams.topMargin = dp(4);
        tile.addView(sub, subParams);
        return tile;
    }

    private static final int PASTEL_BLUE = Color.rgb(222, 239, 255);
    private static final int PASTEL_GREEN = Color.rgb(222, 244, 231);
    private static final int PASTEL_PEACH = Color.rgb(255, 236, 214);
    private static final int DARK_TEXT = Color.rgb(38, 48, 60);

    private void showTravelHubScreen() {
        currentScreen = "travel";
        showPastelGridScreen("Travel", "Location, journeys and city visits", 1,
                new String[]{"📍", "🏠", "🚗", "📝", "📚", "🚆"},
                new String[]{"Current Location", "Home Location", "Drive to Home", "Log a City Visit", "Travel Log", "Train"},
                new Runnable[]{
                    () -> navigateTo("current_location"),
                    () -> showUserDetails(),
                    () -> openDrivingDirections(),
                    () -> startCityLogging(),
                    () -> showTravelLog(),
                    () -> navigateTo("train")
                }, PASTEL_GREEN);
    }

    private void showUtilitiesHubScreen() {
        currentScreen = "utilities";
        showPastelGridScreen("Utilities", "Everyday tools in one place", 2,
                new String[]{"🧍", "🎂", "🗓️", "📷", "🧭"},
                new String[]{"BMI & Weight", "Age Calculator", "Tamil Calendar", "GPS Camera", "Compass"},
                new Runnable[]{
                    () -> navigateTo("bmi"),
                    () -> navigateTo("age"),
                    () -> navigateTo("tamil_calendar"),
                    () -> startGpsCamera(),
                    () -> navigateTo("compass")
                }, PASTEL_PEACH);
    }

    private void showPastelGridScreen(String headingText, String subtitleText, int selectedTab,
                                      String[] icons, String[] labels, Runnable[] actions, int pastel) {
        LinearLayout outer = new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setBackgroundColor(Color.WHITE);
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView homeTitle = new TextView(this); homeTitle.setText(headingText); homeTitle.setTextSize(22); homeTitle.setTypeface(null, Typeface.BOLD); homeTitle.setTextColor(DARK_TEXT);
        header.addView(homeTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        Button homeIcon = new Button(this); homeIcon.setText("⌂"); homeIcon.setTextSize(22); homeIcon.setMinWidth(dp(44)); homeIcon.setAllCaps(false); homeIcon.setContentDescription("Home"); header.addView(homeIcon);
        homeIcon.setOnClickListener(homeClick -> { screenHistory.clear(); renderScreen("home"); }); outer.addView(header);
        outer.setPadding(dp(12), dp(12), dp(12), 0);
        TextView heading = new TextView(this); heading.setText(headingText); heading.setTextSize(25);
        heading.setTypeface(null, Typeface.BOLD); heading.setTextColor(DARK_TEXT); heading.setGravity(Gravity.CENTER);
        outer.addView(heading);
        TextView subtitle = new TextView(this); subtitle.setText(subtitleText); subtitle.setTextSize(13); subtitle.setTextColor(Color.rgb(90,99,110)); subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams st = new LinearLayout.LayoutParams(-1,-2); st.topMargin=dp(3); st.bottomMargin=dp(10); outer.addView(subtitle,st);
        ScrollView scroll = new ScrollView(this); LinearLayout grid = new LinearLayout(this); grid.setOrientation(LinearLayout.VERTICAL);
        for (int i=0; i<icons.length; i+=2) {
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER);
            for (int j=i; j<Math.min(i+2,icons.length); j++) {
                final int index=j;
                LinearLayout tile = makeSubmenuCube(icons[j], labels[j], pastel);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(138), 1f); lp.setMargins(dp(5),dp(5),dp(5),dp(5));
                row.addView(tile,lp); tile.setOnClickListener(v -> actions[index].run());
            }
            if (icons.length % 2 == 1 && i+1 >= icons.length) {
                View spacer = new View(this); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,dp(138),1f); lp.setMargins(dp(5),dp(5),dp(5),dp(5)); row.addView(spacer,lp);
            }
            grid.addView(row);
        }
        scroll.addView(grid); outer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.addView(outer,new LinearLayout.LayoutParams(-1,0,1f)); root.addView(createBottomNavigation(selectedTab)); setContentView(root);
    }

    private LinearLayout makeSubmenuCube(String emoji, String label, int pastel) {
        LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER); tile.setPadding(dp(6),dp(8),dp(6),dp(8));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(pastel); bg.setCornerRadius(dp(18)); bg.setStroke(dp(1),Color.rgb(225,229,232)); tile.setBackground(bg); tile.setClickable(true); tile.setFocusable(true);
        TextView icon = new TextView(this); icon.setText(emoji); icon.setTextSize(30); icon.setGravity(Gravity.CENTER); tile.addView(icon);
        TextView text = new TextView(this); text.setText(label); text.setTextSize(14); text.setTypeface(null,Typeface.BOLD); text.setTextColor(DARK_TEXT); text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(7); tile.addView(text,lp); return tile;
    }

    // Compass: uses the device rotation-vector sensor where available, with a magnetic fallback.
    private SensorManager compassSensorManager;
    private SensorEventListener compassSensorListener;
    private View compassDialView;
    private TextView compassHeadingText;
    private TextView compassStatusText;
    private float compassAzimuth = 0f;
    private boolean compassHasReading = false;
    private boolean compassSensorsRegistered = false;

    private void showCompassScreen() {
        currentScreen = "compass";
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setPadding(dp(18), dp(12), dp(18), dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Compass");
        title.setTextSize(23);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(DARK_TEXT);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button home = new Button(this);
        home.setText("⌂"); home.setTextSize(21); home.setAllCaps(false);
        home.setOnClickListener(v -> { screenHistory.clear(); renderScreen("home"); });
        header.addView(home);
        root.addView(header);

        TextView sub = new TextView(this);
        sub.setText("Turn your phone to rotate the compass");
        sub.setTextSize(15); sub.setTextColor(Color.rgb(90, 99, 110));
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(8); sp.bottomMargin = dp(10); root.addView(sub, sp);

        compassHeadingText = new TextView(this);
        compassHeadingText.setText("—°"); compassHeadingText.setTextSize(30);
        compassHeadingText.setTypeface(null, Typeface.BOLD); compassHeadingText.setTextColor(DARK_TEXT);
        compassHeadingText.setGravity(Gravity.CENTER);
        root.addView(compassHeadingText);

        compassDialView = new View(this) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float cx = getWidth()/2f, cy = getHeight()/2f;
                float radius = Math.min(getWidth(), getHeight()) * 0.43f;
                paint.setStyle(Paint.Style.FILL); paint.setColor(PASTEL_PEACH);
                canvas.drawCircle(cx, cy, radius + dp(7), paint);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); paint.setColor(Color.rgb(190, 166, 139));
                canvas.drawCircle(cx, cy, radius, paint);
                canvas.save(); canvas.rotate(-compassAzimuth, cx, cy);
                paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER); paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD)); paint.setTextSize(dp(19)); paint.setColor(DARK_TEXT);
                canvas.drawText("N", cx, cy-radius+dp(26), paint);
                canvas.drawText("E", cx+radius-dp(23), cy+dp(7), paint);
                canvas.drawText("S", cx, cy+radius-dp(10), paint);
                canvas.drawText("W", cx-radius+dp(23), cy+dp(7), paint);
                paint.setColor(Color.rgb(191, 62, 52));
                Path needle = new Path(); needle.moveTo(cx, cy-radius*0.62f); needle.lineTo(cx-dp(12), cy+dp(9)); needle.lineTo(cx, cy+dp(2)); needle.lineTo(cx+dp(12), cy+dp(9)); needle.close(); canvas.drawPath(needle, paint);
                paint.setColor(Color.rgb(65, 85, 105));
                Path south = new Path(); south.moveTo(cx, cy+radius*0.62f); south.lineTo(cx-dp(10), cy-dp(3)); south.lineTo(cx+dp(10), cy-dp(3)); south.close(); canvas.drawPath(south, paint);
                paint.setColor(Color.WHITE); canvas.drawCircle(cx, cy, dp(6), paint);
                canvas.restore();
            }
        };
        LinearLayout.LayoutParams dialParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        dialParams.setMargins(dp(8), dp(8), dp(8), dp(8)); root.addView(compassDialView, dialParams);
        compassStatusText = new TextView(this);
        compassStatusText.setText("Waiting for compass sensor…"); compassStatusText.setTextSize(14);
        compassStatusText.setTextColor(DARK_TEXT); compassStatusText.setGravity(Gravity.CENTER);
        root.addView(compassStatusText);
        TextView note = new TextView(this);
        note.setText("Keep the phone away from magnets and metal objects. If the direction is inaccurate, move the phone in a figure-eight motion to calibrate it.");
        note.setTextSize(12); note.setTextColor(Color.rgb(100, 100, 100)); note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2); np.topMargin = dp(8); root.addView(note, np);
        setContentView(root);
        startCompass();
    }

    private void startCompass() {
        // Unregister an old listener without clearing the views currently on screen.
        unregisterCompassSensors();
        compassSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (compassSensorManager == null) {
            if (compassStatusText != null) compassStatusText.setText("Compass sensor is unavailable on this device.");
            return;
        }
        Sensor sensor = compassSensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        final boolean rotationVector = sensor != null;
        final Sensor rotationSensor = sensor;
        final Sensor accelerometer = compassSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        final Sensor magneticSensor = compassSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (rotationSensor == null && (accelerometer == null || magneticSensor == null)) {
            if (compassStatusText != null) compassStatusText.setText("No supported compass sensor found.");
            return;
        }
        final float[] gravity = new float[3];
        final float[] magnetic = new float[3];
        final boolean[] haveGravity = {false};
        final boolean[] haveMagnetic = {false};
        compassSensorListener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent event) {
                float azimuth;
                if (rotationVector && event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                    float[] matrix = new float[9];
                    SensorManager.getRotationMatrixFromVector(matrix, event.values);
                    float[] orientation = new float[3];
                    SensorManager.getOrientation(matrix, orientation);
                    azimuth = (float) Math.toDegrees(orientation[0]);
                } else {
                    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                        for (int i=0; i<3; i++) gravity[i] = gravity[i] * 0.8f + event.values[i] * 0.2f;
                        haveGravity[0] = true;
                    } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                        for (int i=0; i<3; i++) magnetic[i] = magnetic[i] * 0.8f + event.values[i] * 0.2f;
                        haveMagnetic[0] = true;
                    } else return;
                    if (!haveGravity[0] || !haveMagnetic[0]) return;
                    float[] matrix = new float[9];
                    if (!SensorManager.getRotationMatrix(matrix, null, gravity, magnetic)) return;
                    float[] orientation = new float[3]; SensorManager.getOrientation(matrix, orientation);
                    azimuth = (float) Math.toDegrees(orientation[0]);
                }
                azimuth = (azimuth + 360f) % 360f;
                // Smooth the heading to reduce jitter around sensor noise.
                if (!compassHasReading) { compassAzimuth = azimuth; compassHasReading = true; }
                else { float delta = ((azimuth - compassAzimuth + 540f) % 360f) - 180f; compassAzimuth = (compassAzimuth + delta * 0.22f + 360f) % 360f; }
                final int degrees = Math.round(compassAzimuth) % 360;
                final String direction = compassDirection(degrees);
                runOnUiThread(() -> {
                    if (compassDialView != null) compassDialView.invalidate();
                    if (compassHeadingText != null) compassHeadingText.setText(degrees + "°  " + direction);
                    if (compassStatusText != null) compassStatusText.setText("Live heading • " + (rotationVector ? "rotation sensor" : "magnetic sensor"));
                });
            }
            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
                if (compassStatusText != null && accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) compassStatusText.setText("Compass needs calibration. Move the phone in a figure-eight motion.");
            }
        };
        compassHasReading = false;
        if (rotationSensor != null) {
            compassSensorsRegistered = compassSensorManager.registerListener(compassSensorListener, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        } else {
            boolean a = compassSensorManager.registerListener(compassSensorListener, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            boolean m = compassSensorManager.registerListener(compassSensorListener, magneticSensor, SensorManager.SENSOR_DELAY_GAME);
            compassSensorsRegistered = a && m;
        }
    }

    private String compassDirection(int degrees) {
        String[] directions = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
        return directions[(int)Math.round(degrees / 22.5) % 16];
    }

    private void unregisterCompassSensors() {
        if (compassSensorManager != null && compassSensorListener != null) {
            try { compassSensorManager.unregisterListener(compassSensorListener); } catch (Exception ignored) {}
        }
        compassSensorsRegistered = false;
        compassSensorListener = null;
    }

    private void stopCompass() {
        unregisterCompassSensors();
        compassSensorManager = null;
        compassDialView = null;
        compassHeadingText = null;
        compassStatusText = null;
        compassHasReading = false;
    }

    private void showTrainHubScreen() {
        currentScreen = "train";
        showPastelGridScreen("Train", "All-India timetable is unofficial; download once for offline search", 1,
                new String[]{"🚉", "🔢", "↔️", "➕", "⬇️"},
                new String[]{"Search by Station", "Search by Train Number", "Between Stations", "Add Train Record", "Download / Update All-India Data"},
                new Runnable[]{
                    () -> navigateTo("train_station"),
                    () -> navigateTo("train_number"),
                    () -> navigateTo("train_between"),
                    () -> showAddTrainRecordDialog(),
                    () -> downloadAndImportTrainFeed()
                }, PASTEL_GREEN);
    }

    private static class TrainDb extends SQLiteOpenHelper {
        TrainDb(Activity activity) { super(activity, "travelkit_trains.db", null, 2); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE trains (_id INTEGER PRIMARY KEY AUTOINCREMENT, number TEXT NOT NULL, name TEXT NOT NULL, source TEXT NOT NULL, destination TEXT NOT NULL, route TEXT NOT NULL DEFAULT '', schedule TEXT NOT NULL DEFAULT '', run_days TEXT NOT NULL DEFAULT 'Not available')");
            db.execSQL("CREATE TABLE stations (code TEXT PRIMARY KEY, name TEXT NOT NULL)");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                try { db.execSQL("ALTER TABLE trains ADD COLUMN schedule TEXT NOT NULL DEFAULT ''"); } catch (Exception ignored) { }
                try { db.execSQL("ALTER TABLE trains ADD COLUMN run_days TEXT NOT NULL DEFAULT 'Not available'"); } catch (Exception ignored) { }
                db.execSQL("CREATE TABLE IF NOT EXISTS stations (code TEXT PRIMARY KEY, name TEXT NOT NULL)");
            }
        }
    }

    private TrainDb trainDb() { return new TrainDb(this); }

    // Community-maintained Indian Railways GTFS feed. Unofficial; validate before import.
    private static final String TRAIN_GTFS_URL = "https://github.com/Neo2308/indianrailways-gtfs/raw/refs/heads/main/gtfs/gtfs.zip";

    private static class TrainRouteInfo {
        String number = "", name = "";
    }
    private static class StationInfo { String code, name; StationInfo(String c, String n){code=c;name=n;} }
    private static class StopLine { String code, name, arrival, departure; int sequence; StopLine(String c,String n,String a,String d,int q){code=c;name=n;arrival=a;departure=d;sequence=q;} }
    private static class TripStopInfo {
        String routeId, first = "", last = "", serviceId = "";
        int count = 0;
        StringBuilder route = new StringBuilder();
        List<StopLine> stops = new ArrayList<>();
        TripStopInfo(String r) { routeId = r; }
        void add(String code, String station, String arrival, String departure, int sequence) {
            if (count == 0) first = station;
            last = station;
            if (route.length() > 0) route.append(" → ");
            route.append(station); count++;
            stops.add(new StopLine(code, station, arrival, departure, sequence));
        }
    }

    private void downloadAndImportTrainFeed() {
        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("All-India train database");
        progress.setMessage("Downloading and validating the unofficial timetable…");
        progress.setIndeterminate(true); progress.setCancelable(false); progress.show();
        new Thread(() -> {
            String resultMessage;
            boolean success = false;
            try {
                File zipFile = new File(getCacheDir(), "indianrailways_gtfs.zip");
                HttpURLConnection conn = (HttpURLConnection) new URL(TRAIN_GTFS_URL).openConnection();
                conn.setConnectTimeout(25000); conn.setReadTimeout(45000); conn.setRequestProperty("User-Agent", "Travelkit-Android/1.0");
                conn.connect();
                int httpStatus = conn.getResponseCode();
                if (httpStatus < 200 || httpStatus >= 300) throw new IOException("Feed server returned HTTP " + httpStatus);
                try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(zipFile)) {
                    byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                } finally { conn.disconnect(); }
                if (zipFile.length() < 100000) throw new IOException("The downloaded file is too small to be a valid GTFS feed.");

                Map<String, TrainRouteInfo> routes = new HashMap<>();
                Map<String, String> tripToRoute = new HashMap<>();
                Map<String, String> tripToService = new HashMap<>();
                Map<String, String> stops = new HashMap<>();
                Map<String, String> stopCodes = new HashMap<>();
                Map<String, String> serviceDays = new HashMap<>();
                Map<String, TripStopInfo> tripStops = new HashMap<>();
                boolean hasRoutes=false, hasTrips=false, hasStops=false, hasStopTimes=false;
                try (ZipInputStream zis = new ZipInputStream(new java.io.FileInputStream(zipFile))) {
                    ZipEntry entry;
                    while ((entry = zis.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (name.endsWith("routes.txt")) {
                            hasRoutes=true; List<String[]> rows = readGtfsCsv(zis);
                            if (rows.isEmpty()) throw new IOException("routes.txt is empty");
                            Map<String,Integer> h = headerMap(rows.get(0));
                            for (int i=1;i<rows.size();i++) { String[] r=rows.get(i); String id=field(r,h,"route_id"); if(id.isEmpty()) continue; TrainRouteInfo info=new TrainRouteInfo(); info.number=field(r,h,"route_short_name"); info.name=field(r,h,"route_long_name"); if(info.number.isEmpty()) info.number=id; routes.put(id,info); }
                        } else if (name.endsWith("trips.txt")) {
                            hasTrips=true; List<String[]> rows=readGtfsCsv(zis); if(rows.isEmpty()) throw new IOException("trips.txt is empty"); Map<String,Integer> h=headerMap(rows.get(0));
                            for(int i=1;i<rows.size();i++){String[] r=rows.get(i);String trip=field(r,h,"trip_id"),route=field(r,h,"route_id"),service=field(r,h,"service_id");if(!trip.isEmpty()&&!route.isEmpty()){tripToRoute.put(trip,route);tripToService.put(trip,service);}}
                        } else if (name.endsWith("stops.txt")) {
                            hasStops=true; List<String[]> rows=readGtfsCsv(zis); if(rows.isEmpty()) throw new IOException("stops.txt is empty"); Map<String,Integer> h=headerMap(rows.get(0));
                            for(int i=1;i<rows.size();i++){String[] r=rows.get(i);String id=field(r,h,"stop_id"),nm=field(r,h,"stop_name"),stationCode=field(r,h,"stop_code");if(!id.isEmpty()&&!nm.isEmpty()){stops.put(id,nm);stopCodes.put(id,stationCode.isEmpty()?id:stationCode);}}
                        } else if (name.endsWith("stop_times.txt")) {
                            hasStopTimes=true; List<String[]> rows=readGtfsCsv(zis); if(rows.isEmpty()) throw new IOException("stop_times.txt is empty"); Map<String,Integer> h=headerMap(rows.get(0));
                            for(int i=1;i<rows.size();i++){String[] r=rows.get(i);String trip=field(r,h,"trip_id"),sid=field(r,h,"stop_id");if(trip.isEmpty()||sid.isEmpty())continue;String route=tripToRoute.get(trip);if(route==null)continue;String station=stops.get(sid);if(station==null)station=sid;String arr=field(r,h,"arrival_time"),dep=field(r,h,"departure_time");int seq=0;try{seq=Integer.parseInt(field(r,h,"stop_sequence"));}catch(Exception ignored){}TripStopInfo info=tripStops.get(trip);if(info==null){info=new TripStopInfo(route);info.serviceId=tripToService.get(trip);tripStops.put(trip,info);}info.add(stopCodes.get(sid)==null?sid:stopCodes.get(sid),station,arr,dep,seq);}
                        } else if (name.endsWith("calendar.txt")) {
                            List<String[]> rows=readGtfsCsv(zis); if(!rows.isEmpty()){Map<String,Integer> h=headerMap(rows.get(0));String[] dayKeys={"monday","tuesday","wednesday","thursday","friday","saturday","sunday"};String[] dayNames={"Mon","Tue","Wed","Thu","Fri","Sat","Sun"};for(int i=1;i<rows.size();i++){String[] r=rows.get(i);String sid=field(r,h,"service_id");if(sid.isEmpty())continue;StringBuilder days=new StringBuilder();for(int d=0;d<dayKeys.length;d++)if("1".equals(field(r,h,dayKeys[d]))){if(days.length()>0)days.append(", ");days.append(dayNames[d]);}serviceDays.put(sid,days.length()==0?"No operating days listed":days.toString());}}
                        }
                        zis.closeEntry();
                    }
                }
                if(!hasRoutes||!hasTrips||!hasStops||!hasStopTimes) throw new IOException("The feed is missing one or more required GTFS files.");
                if(routes.size()<1000||stops.size()<1000||tripStops.size()<1000) throw new IOException("Validation failed: too few routes, stations or trips were found.");
                Map<String,TripStopInfo> bestByRoute = new HashMap<>();
                for(TripStopInfo t:tripStops.values()){if(t.count<2)continue;TripStopInfo old=bestByRoute.get(t.routeId);if(old==null||t.count>old.count)bestByRoute.put(t.routeId,t);}
                if(bestByRoute.size()<1000) throw new IOException("Validation failed: too few usable train routes were found.");
                SQLiteDatabase db=trainDb().getWritableDatabase(); db.beginTransaction(); int inserted=0;
                try {
                    db.delete("trains",null,null); db.delete("stations",null,null);
                    for(Map.Entry<String,String> st:stops.entrySet()){ContentValues scv=new ContentValues();String code=stopCodes.get(st.getKey());scv.put("code",code==null?st.getKey():code);scv.put("name",st.getValue());db.insertWithOnConflict("stations",null,scv,SQLiteDatabase.CONFLICT_REPLACE);}
                    for(Map.Entry<String,TripStopInfo> e:bestByRoute.entrySet()){
                        TrainRouteInfo r=routes.get(e.getKey());TripStopInfo t=e.getValue();if(r==null||t.first.isEmpty()||t.last.isEmpty())continue;
                        java.util.Collections.sort(t.stops,(x,y)->Integer.compare(x.sequence,y.sequence));StringBuilder schedule=new StringBuilder();for(StopLine sl:t.stops){if(schedule.length()>0)schedule.append("\n");schedule.append(sl.sequence).append("|").append(sl.code).append("|").append(sl.name).append("|").append(sl.arrival).append("|").append(sl.departure);}
                        String days=serviceDays.get(t.serviceId);if(days==null||days.isEmpty())days="Not available in feed";
                        ContentValues cv=new ContentValues();cv.put("number",r.number);cv.put("name",r.name.isEmpty()?r.number:r.name);cv.put("source",t.first);cv.put("destination",t.last);cv.put("route",t.route.toString());cv.put("schedule",schedule.toString());cv.put("run_days",days);
                        if(db.insert("trains",null,cv)!=-1)inserted++;
                    }
                    if(inserted<1000) throw new IOException("Import validation failed: fewer than 1,000 routes were saved.");
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); db.close(); }
                resultMessage="Imported " + inserted + " train routes and " + stops.size() + " stations. This is an unofficial community feed; timetable accuracy is not guaranteed. Searches are now available offline.";success=true;
                zipFile.delete();
            } catch(Exception ex) { resultMessage="Could not update train data: " + (ex.getMessage()==null?"unknown error":ex.getMessage()) + ". Your existing database was not intentionally replaced unless import completed."; }
            final String message=resultMessage; final boolean ok=success;
            runOnUiThread(() -> { if(progress.isShowing())progress.dismiss(); new AlertDialog.Builder(MainActivity.this).setTitle(ok?"Offline train database ready":"Train database update failed").setMessage(message).setPositiveButton("OK",null).show(); });
        }).start();
    }

    private List<String[]> readGtfsCsv(ZipInputStream zis) throws IOException {
        List<String[]> rows = new ArrayList<>();
        BufferedReader br = new BufferedReader(new InputStreamReader(zis, java.nio.charset.StandardCharsets.UTF_8));
        String line; while((line=br.readLine())!=null){ if(!line.isEmpty()) rows.add(parseCsvLine(line)); }
        return rows;
    }
    private String[] parseCsvLine(String line) {
        List<String> out=new ArrayList<>();StringBuilder field=new StringBuilder();boolean quoted=false;
        for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){field.append('"');i++;}else quoted=!quoted;}else if(c==','&&!quoted){out.add(field.toString());field.setLength(0);}else field.append(c);}
        out.add(field.toString());return out.toArray(new String[0]);
    }
    private Map<String,Integer> headerMap(String[] header) { Map<String,Integer> map=new HashMap<>();for(int i=0;i<header.length;i++)map.put(header[i].replace("\uFEFF", "").trim(),i);return map; }
    private String field(String[] row,Map<String,Integer> header,String key){Integer i=header.get(key);return i==null||i<0||i>=row.length?"":row[i].trim();}

    private void showTrainSearchScreen(String mode) {
        currentScreen = "train_" + mode;
        String heading = "station".equals(mode) ? "Search by Station" : "number".equals(mode) ? "Search by Train Number" : "Between Stations";
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(14),dp(12),dp(14),0); page.setBackgroundColor(Color.WHITE);
        LinearLayout titleRow = new LinearLayout(this); titleRow.setOrientation(LinearLayout.HORIZONTAL); titleRow.setGravity(Gravity.CENTER_VERTICAL); TextView title = new TextView(this); title.setText(heading); title.setTextSize(21); title.setTypeface(null,Typeface.BOLD); title.setTextColor(DARK_TEXT); titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1f)); Button homeButton = new Button(this); homeButton.setText("⌂"); homeButton.setTextSize(21); homeButton.setAllCaps(false); homeButton.setMinWidth(dp(44)); homeButton.setOnClickListener(homeClick->{screenHistory.clear();renderScreen("home");}); titleRow.addView(homeButton); page.addView(titleRow);
        AutoCompleteTextView first = new AutoCompleteTextView(this); first.setSingleLine(true); first.setTextSize(15); first.setThreshold(1);
        AutoCompleteTextView second = new AutoCompleteTextView(this); second.setSingleLine(true); second.setTextSize(15); second.setThreshold(1);
        if ("number".equals(mode)) { first.setHint("Train number or name"); page.addView(first); }
        else if ("station".equals(mode)) { first.setHint("Station code (e.g. CDM) or name"); page.addView(first); }
        else { first.setHint("From station code or name"); second.setHint("To station code or name"); page.addView(first); page.addView(second); }
        if (!"number".equals(mode)) { List<String> stationOptions=loadStationSuggestions(); ArrayAdapter<String> stationAdapter=new ArrayAdapter<>(this,android.R.layout.simple_dropdown_item_1line,stationOptions); first.setAdapter(stationAdapter); second.setAdapter(stationAdapter); }
        Button search = new Button(this); search.setText("Search offline records"); search.setAllCaps(false); page.addView(search);
        TextView help = new TextView(this); help.setText("Station searches accept railway station codes (for example CDM) or names; choose a suggestion when available. Tap any train result to view its full station-by-station schedule. Searches work offline after the timetable has been downloaded. This community feed is unofficial and may be incomplete or outdated."); help.setTextSize(12); help.setTextColor(Color.DKGRAY); help.setPadding(0,dp(8),0,dp(8)); page.addView(help);
        ScrollView resultScroll = new ScrollView(this); LinearLayout results = new LinearLayout(this); results.setOrientation(LinearLayout.VERTICAL); resultScroll.addView(results); page.addView(resultScroll,new LinearLayout.LayoutParams(-1,0,1f));
        search.setOnClickListener(v -> {
            String a=first.getText().toString().trim(); String b=second.getText().toString().trim();
            if (a.isEmpty() || ("between".equals(mode) && b.isEmpty())) { Toast.makeText(this,"Enter the required station or train details.",Toast.LENGTH_SHORT).show(); return; }
            results.removeAllViews(); SQLiteDatabase db=trainDb().getReadableDatabase(); Cursor c=null;
            try {
                String likeA="%"+a+"%", likeB="%"+b+"%";
                if ("number".equals(mode)) c=db.rawQuery("SELECT number,name,source,destination,route,schedule,run_days FROM trains WHERE number LIKE ? OR name LIKE ? ORDER BY number LIMIT 200",new String[]{likeA,likeA});
                else if ("station".equals(mode)) c=db.rawQuery("SELECT number,name,source,destination,route,schedule,run_days FROM trains WHERE source LIKE ? OR destination LIKE ? OR route LIKE ? OR schedule LIKE ? ORDER BY number LIMIT 300",new String[]{likeA,likeA,likeA,likeA});
                else { String from=stationSearchValue(a), to=stationSearchValue(b); String lf="%"+from+"%", lt="%"+to+"%"; c=db.rawQuery("SELECT number,name,source,destination,route,schedule,run_days FROM trains WHERE (source LIKE ? AND destination LIKE ?) OR (route LIKE ? AND route LIKE ?) OR (schedule LIKE ? AND schedule LIKE ?) ORDER BY number LIMIT 300",new String[]{lf,lt,lf,lt,lf,lt}); }
                int displayed = 0;
                if (c != null && c.moveToFirst()) { do { final String num=c.getString(0), nm=c.getString(1), src=c.getString(2), dst=c.getString(3), route=c.getString(4), schedule=c.getString(5), days=c.getString(6);
                    if ("between".equals(mode) && !isForwardTrain(schedule, route, src, dst, a, b)) continue;
                    String requested="between".equals(mode)?b:a; String atTime=findStationTime(schedule,requested); String record="Train "+num+" — "+nm+"\nFrom: "+src+"\nTo: "+dst+"\nRuns: "+days+ (("station".equals(mode)||"between".equals(mode))?"\nTime at "+("between".equals(mode)?"destination station":"requested station")+": "+(atTime.isEmpty()?"Not available":atTime):""); TextView item=new TextView(this); item.setText(record+"\nTap to view full schedule"); item.setTextColor(DARK_TEXT); item.setTextSize(15); item.setPadding(dp(12),dp(12),dp(12),dp(12)); GradientDrawable card=new GradientDrawable(); card.setColor(PASTEL_GREEN); card.setCornerRadius(dp(14)); item.setBackground(card); LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2); rp.setMargins(0,dp(5),0,dp(5)); results.addView(item,rp); item.setOnClickListener(trainItemView->showTrainScheduleDialog(num,nm,src,dst,days,schedule)); displayed++; } while(c.moveToNext()); }
                if (displayed == 0) { TextView empty=new TextView(this); empty.setText("No trains found travelling from the selected origin to the selected destination. Check the station order or update the offline timetable."); empty.setTextColor(DARK_TEXT); empty.setTextSize(14); empty.setPadding(dp(8),dp(12),dp(8),dp(12)); results.addView(empty); }
            } catch(Exception ex) { Toast.makeText(this,"Could not search saved train records.",Toast.LENGTH_LONG).show(); }
            finally { if(c!=null)c.close(); db.close(); }
        });
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.addView(page,new LinearLayout.LayoutParams(-1,0,1f)); root.addView(createBottomNavigation(1)); setContentView(root);
    }

    private boolean isForwardTrain(String schedule, String route, String source, String destination, String fromInput, String toInput) {
        String from = stationSearchValue(fromInput).trim().toLowerCase(Locale.ROOT);
        String to = stationSearchValue(toInput).trim().toLowerCase(Locale.ROOT);
        int fromSeq = -1, toSeq = -1;
        if (schedule != null && !schedule.trim().isEmpty()) {
            int seq = 0;
            for (String line : schedule.split("\\n")) {
                String[] p = line.split("\\|", -1);
                if (p.length >= 3) {
                    String code = p[1].trim().toLowerCase(Locale.ROOT), name = p[2].trim().toLowerCase(Locale.ROOT);
                    if (matchesStation(from, code, name)) fromSeq = seq;
                    if (matchesStation(to, code, name)) toSeq = seq;
                }
                seq++;
            }
            if (fromSeq >= 0 && toSeq >= 0) return fromSeq < toSeq;
        }
        String r = (route == null ? "" : route).toLowerCase(Locale.ROOT);
        int fi = r.indexOf(from), ti = r.indexOf(to);
        if (fi >= 0 && ti >= 0) return fi < ti;
        // If the schedule lacks ordered stops, only accept the train's declared origin/destination direction.
        String src = source == null ? "" : source.toLowerCase(Locale.ROOT);
        String dst = destination == null ? "" : destination.toLowerCase(Locale.ROOT);
        return matchesStation(from, "", src) && matchesStation(to, "", dst);
    }

    private boolean matchesStation(String query, String code, String name) {
        if (query == null || query.isEmpty()) return false;
        return (code != null && (code.equals(query) || code.contains(query))) ||
               (name != null && (name.equals(query) || name.contains(query))) ||
               (code != null && code.contains(query));
    }

    private List<String> loadStationSuggestions() {
        List<String> out=new ArrayList<>(); SQLiteDatabase db=trainDb().getReadableDatabase(); Cursor c=null;
        try { c=db.rawQuery("SELECT code,name FROM stations ORDER BY name",null); while(c.moveToNext()) out.add(c.getString(0)+" — "+c.getString(1)); }
        catch(Exception ignored) { }
        finally { if(c!=null)c.close(); db.close(); }
        return out;
    }
    private String stationSearchValue(String raw) { if(raw==null)return ""; String[] parts=raw.split("\\s*[—–]\\s*",2); return parts.length>1?parts[1].trim():raw.trim(); }
    private String displayTrainTime(String raw, int sequence) {
        if (raw == null || raw.trim().isEmpty()) return "—";
        String value = raw.trim();
        try {
            String[] parts = value.split(":");
            int hours = Integer.parseInt(parts[0]);
            int minutes = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int day = hours / 24 + 1;
            int hour = hours % 24;
            return (day > 1 ? "Day " + day + " · " : "") + String.format(Locale.getDefault(), "%02d:%02d", hour, minutes);
        } catch (Exception ignored) { return value; }
    }

    private String findStationTime(String schedule,String query) {
        if(schedule==null||query==null)return "";String q=query.trim().toLowerCase(Locale.ROOT);String stationName=stationSearchValue(query).toLowerCase(Locale.ROOT);String stationCode=q.contains("—")?q.substring(0,q.indexOf("—")).trim():q;
        for(String line:schedule.split("\\n")){String[] p=line.split("\\|",-1);if(p.length>=5&&(p[1].toLowerCase(Locale.ROOT).equals(stationCode)||p[1].toLowerCase(Locale.ROOT).contains(stationCode)||p[2].toLowerCase(Locale.ROOT).contains(stationName))){String a=displayTrainTime(p[3],Integer.parseInt(p[0])),d=displayTrainTime(p[4],Integer.parseInt(p[0]));return "Arrival "+a+", departure "+d;}}
        return "";
    }
    private void showTrainScheduleDialog(String number,String name,String source,String destination,String days,String schedule) {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(14),dp(8),dp(14),dp(8));
        TextView summary=new TextView(this);summary.setText("Train "+number+" — "+name+"\n"+source+" → "+destination+"\nRuns: "+days+"\n\nFull schedule (times are from the unofficial feed)");summary.setTextColor(DARK_TEXT);summary.setTextSize(15);box.addView(summary);
        ScrollView scroll=new ScrollView(this);LinearLayout rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);if(schedule==null||schedule.trim().isEmpty()){TextView t=new TextView(this);t.setText("No station-by-station timings are available for this record.");t.setTextColor(DARK_TEXT);rows.addView(t);}else{for(String line:schedule.split("\\n")){String[] p=line.split("\\|",-1);if(p.length<5)continue;TextView t=new TextView(this);String ar=displayTrainTime(p[3],Integer.parseInt(p[0])),dpv=displayTrainTime(p[4],Integer.parseInt(p[0]));t.setText(p[0]+". "+p[2]+" ("+p[1]+")\nArrival: "+ar+"    Departure: "+dpv);t.setTextColor(DARK_TEXT);t.setTextSize(14);t.setPadding(dp(8),dp(8),dp(8),dp(8));rows.addView(t);View divider=new View(this);divider.setBackgroundColor(0xFFE0E0E0);rows.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));}}box.addView(scroll,new LinearLayout.LayoutParams(-1,dp(360)));new AlertDialog.Builder(this).setTitle("Train timetable").setView(box).setPositiveButton("Close",null).show();
    }

    private void showAddTrainRecordDialog() {
        LinearLayout form = new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(8),dp(4),dp(8),dp(4));
        EditText number=new EditText(this); number.setHint("Train number"); number.setSingleLine(true); form.addView(number);
        EditText name=new EditText(this); name.setHint("Train name"); name.setSingleLine(true); form.addView(name);
        EditText source=new EditText(this); source.setHint("Source station"); source.setSingleLine(true); form.addView(source);
        EditText destination=new EditText(this); destination.setHint("Destination station"); destination.setSingleLine(true); form.addView(destination);
        EditText route=new EditText(this); route.setHint("Route / intermediate stations (optional)"); form.addView(route);
        new AlertDialog.Builder(this).setTitle("Add offline train record").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",(dialog,which)->{
            String n=number.getText().toString().trim(), nm=name.getText().toString().trim(), s=source.getText().toString().trim(), d=destination.getText().toString().trim(), r=route.getText().toString().trim();
            if(n.isEmpty()||nm.isEmpty()||s.isEmpty()||d.isEmpty()){Toast.makeText(this,"Train number, name, source and destination are required.",Toast.LENGTH_LONG).show();return;}
            SQLiteDatabase db=trainDb().getWritableDatabase(); ContentValues cv=new ContentValues(); cv.put("number",n);cv.put("name",nm);cv.put("source",s);cv.put("destination",d);cv.put("route",r);cv.put("schedule","");cv.put("run_days","Not available"); long id=db.insert("trains",null,cv);db.close();
            Toast.makeText(this,id==-1?"Could not save train record.":"Train record saved on this phone.",Toast.LENGTH_LONG).show();
        }).show();
    }

    // RSS-based news reader. Articles open inside this app; publishers remain the source of truth.
    private static class NewsItem {
        String title = "", description = "", source = "", date = "", link = "";
    }

    private void showNewsScreen() {
        currentScreen = "news";
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12),dp(8),dp(12),0); root.setBackgroundColor(Color.WHITE);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(this); title.setText("News"); title.setTextSize(24); title.setTypeface(null,Typeface.BOLD); title.setTextColor(DARK_TEXT);
        header.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
        Button home = new Button(this); home.setText("⌂"); home.setTextSize(20); home.setAllCaps(false); header.addView(home); home.setOnClickListener(v->{screenHistory.clear();renderScreen("home");}); root.addView(header);
        TextView intro = new TextView(this); intro.setText("Headlines from multiple publishers · tap a story to read it here"); intro.setTextColor(Color.GRAY); intro.setTextSize(12); root.addView(intro);
        LinearLayout prefsRow = new LinearLayout(this); prefsRow.setOrientation(LinearLayout.HORIZONTAL);
        Button preferences = new Button(this); preferences.setText("Preferences"); preferences.setAllCaps(false);
        Button refresh = new Button(this); refresh.setText("Refresh news"); refresh.setAllCaps(false);
        prefsRow.addView(preferences,new LinearLayout.LayoutParams(0,-2,1f)); prefsRow.addView(refresh,new LinearLayout.LayoutParams(0,-2,1f)); root.addView(prefsRow);
        TextView prefsSummary = new TextView(this); prefsSummary.setText("Topics: " + newsPrefsSummary()); prefsSummary.setTextSize(12); prefsSummary.setTextColor(DARK_TEXT); root.addView(prefsSummary);
        ScrollView scroll = new ScrollView(this); LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(0,dp(4),0,dp(16));
        TextView status = new TextView(this); status.setText("Loading headlines…"); status.setTextSize(14); status.setPadding(dp(8),dp(12),dp(8),dp(12)); list.addView(status); scroll.addView(list); root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1f));
        root.addView(createBottomNavigation(0)); setContentView(root);
        preferences.setOnClickListener(v -> showNewsPreferencesDialog(() -> { prefsSummary.setText("Topics: " + newsPrefsSummary()); loadNewsInto(list,status); }));
        refresh.setOnClickListener(v -> loadNewsInto(list,status));
        final float[] downY = {0};
        scroll.setOnTouchListener((v,event)->{ if(event.getAction()==MotionEvent.ACTION_DOWN){downY[0]=event.getY();} else if(event.getAction()==MotionEvent.ACTION_UP && scroll.getScrollY()==0 && event.getY()-downY[0]>dp(70)){loadNewsInto(list,status);} return false; });
        loadNewsInto(list,status);
    }

    private String newsPrefsSummary() {
        String topic=prefs().getString("news_topic", "technology"), country=prefs().getString("news_country", "India"), place=prefs().getString("news_place", "Tamil Nadu"), sport=prefs().getString("news_sport", "sports");
        return topic + " · " + country + " · " + place + " · " + sport;
    }

    private void showNewsPreferencesDialog(Runnable onSaved) {
        LinearLayout form=new LinearLayout(this); form.setOrientation(LinearLayout.VERTICAL); form.setPadding(dp(8),0,dp(8),0);
        EditText topic=new EditText(this); topic.setSingleLine(true); topic.setHint("Industry / topic (e.g. technology, business)"); topic.setText(prefs().getString("news_topic","technology")); form.addView(topic);
        EditText country=new EditText(this); country.setSingleLine(true); country.setHint("Country (e.g. India)"); country.setText(prefs().getString("news_country","India")); form.addView(country);
        EditText place=new EditText(this); place.setSingleLine(true); place.setHint("State / city / specific place"); place.setText(prefs().getString("news_place","Tamil Nadu")); form.addView(place);
        EditText sport=new EditText(this); sport.setSingleLine(true); sport.setHint("Sports / team / tournament"); sport.setText(prefs().getString("news_sport","sports")); form.addView(sport);
        new AlertDialog.Builder(this).setTitle("News preferences").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{prefs().edit().putString("news_topic",topic.getText().toString().trim()).putString("news_country",country.getText().toString().trim()).putString("news_place",place.getText().toString().trim()).putString("news_sport",sport.getText().toString().trim()).apply();onSaved.run();}).show();
    }

    private void loadNewsInto(LinearLayout list, TextView status) {
        list.removeAllViews(); status.setText("Fetching headlines from news RSS feeds…"); list.addView(status);
        Thread worker = new Thread(() -> {
            ArrayList<NewsItem> items=new ArrayList<>(); String[] feeds={
                "https://feeds.bbci.co.uk/news/world/rss.xml",
                "https://feeds.bbci.co.uk/news/technology/rss.xml",
                "https://feeds.bbci.co.uk/sport/rss.xml",
                "https://feeds.feedburner.com/ndtvnews-top-stories",
                "https://www.thehindu.com/news/national/feeder/default.rss"
            };
            for(String feed:feeds){ try { items.addAll(readNewsFeed(feed)); } catch(Exception ignored){} }
            final ArrayList<NewsItem> filtered=filterNews(items);
            runOnUiThread(() -> {
                list.removeAllViews();
                if(filtered.isEmpty()) { TextView empty=new TextView(this); empty.setText("No headlines could be loaded. Check your internet connection or try Refresh news later."); empty.setTextColor(DARK_TEXT); empty.setPadding(dp(12),dp(20),dp(12),dp(20)); list.addView(empty); return; }
                TextView note=new TextView(this); note.setText("RSS headlines from BBC, NDTV and The Hindu where feeds are available. Some feeds may be temporarily unavailable."); note.setTextSize(11); note.setTextColor(Color.GRAY); note.setPadding(dp(4),dp(4),dp(4),dp(8)); list.addView(note);
                for(NewsItem item:filtered){
                    LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(12),dp(10),dp(12),dp(10)); GradientDrawable bg=new GradientDrawable(); bg.setColor(Color.rgb(248,250,252)); bg.setCornerRadius(dp(14)); bg.setStroke(dp(1),Color.rgb(228,232,237)); card.setBackground(bg);
                    TextView headline=new TextView(this); headline.setText(item.title); headline.setTextSize(16); headline.setTypeface(null,Typeface.BOLD); headline.setTextColor(DARK_TEXT); card.addView(headline);
                    TextView meta=new TextView(this); meta.setText(item.source+(item.date.isEmpty()?"":" · "+item.date)); meta.setTextSize(11); meta.setTextColor(Color.GRAY); LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2); mp.topMargin=dp(4); card.addView(meta,mp);
                    if(!item.description.isEmpty()){TextView desc=new TextView(this);desc.setText(item.description);desc.setTextSize(13);desc.setTextColor(Color.rgb(70,78,88));LinearLayout.LayoutParams dp0=new LinearLayout.LayoutParams(-1,-2);dp0.topMargin=dp(5);card.addView(desc,dp0);}
                    LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.setMargins(0,dp(4),0,dp(5));list.addView(card,cp);
                    card.setOnClickListener(v->{prefs().edit().putString("news_open_url",item.link).apply();navigateTo("news_article");});
                }
            });
        }); worker.start();
    }

    private ArrayList<NewsItem> filterNews(ArrayList<NewsItem> input) {
        ArrayList<NewsItem> out=new ArrayList<>(); String terms=(prefs().getString("news_topic","technology")+" "+prefs().getString("news_country","India")+" "+prefs().getString("news_place","Tamil Nadu")+" "+prefs().getString("news_sport","sports")).toLowerCase(Locale.ROOT);
        String[] words=terms.split("[,; ]+");
        for(NewsItem n:input){String hay=(n.title+" "+n.description+" "+n.source).toLowerCase(Locale.ROOT); boolean match=false; for(String word:words){if(word.length()>2 && hay.contains(word)){match=true;break;}} if(match || out.size()<15) {out.add(n);} if(out.size()>=80)break;}
        return out;
    }

    private ArrayList<NewsItem> readNewsFeed(String feedUrl) throws Exception {
        ArrayList<NewsItem> out=new ArrayList<>(); HttpURLConnection conn=(HttpURLConnection)new URL(feedUrl).openConnection(); conn.setConnectTimeout(7000);conn.setReadTimeout(7000);conn.setRequestProperty("User-Agent","Travelkit Android RSS Reader/1.0");
        try(InputStream stream=conn.getInputStream()) { XmlPullParser parser=Xml.newPullParser(); parser.setInput(stream,"UTF-8"); int event=parser.getEventType(); NewsItem item=null; String tag=""; int count=0;
            while(event!=XmlPullParser.END_DOCUMENT && count<40){ if(event==XmlPullParser.START_TAG){tag=parser.getName(); if("item".equalsIgnoreCase(tag)||"entry".equalsIgnoreCase(tag)){item=new NewsItem();} else if(item!=null && "link".equalsIgnoreCase(tag)){String href=parser.getAttributeValue(null,"href"); if(href!=null)item.link=href;}}
                else if(event==XmlPullParser.TEXT && item!=null){String value=parser.getText(); if(value!=null){ if("title".equalsIgnoreCase(tag))item.title=value.trim(); else if("description".equalsIgnoreCase(tag)||"summary".equalsIgnoreCase(tag))item.description=android.text.Html.fromHtml(value,android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim(); else if("pubDate".equalsIgnoreCase(tag)||"published".equalsIgnoreCase(tag)||"updated".equalsIgnoreCase(tag))item.date=value.trim(); else if("link".equalsIgnoreCase(tag)&&item.link.isEmpty())item.link=value.trim(); }}
                else if(event==XmlPullParser.END_TAG && ("item".equalsIgnoreCase(parser.getName())||"entry".equalsIgnoreCase(parser.getName()))){ if(item!=null&&!item.title.isEmpty()&&!item.link.isEmpty()){item.source=Uri.parse(feedUrl).getHost();out.add(item);count++;}item=null;} event=parser.next(); }
        } finally {conn.disconnect();} return out;
    }

    private void showNewsArticleScreen(String url) {
        currentScreen="news_article"; LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);Button back=new Button(this);back.setText("← News");back.setAllCaps(false);bar.addView(back);TextView label=new TextView(this);label.setText("Article");label.setTextSize(18);label.setTypeface(null,Typeface.BOLD);label.setTextColor(DARK_TEXT);bar.addView(label);root.addView(bar);back.setOnClickListener(v->{if(webView!=null){webView.stopLoading();webView.destroy();webView=null;} if(!screenHistory.isEmpty())renderScreen(screenHistory.remove(screenHistory.size()-1));else renderScreen("news");});
        webView=new WebView(this);WebSettings settings=webView.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);webView.setWebViewClient(new WebViewClient());root.addView(webView,new LinearLayout.LayoutParams(-1,0,1f));setContentView(root); if(url!=null && (url.startsWith("https://")||url.startsWith("http://")))webView.loadUrl(url);else webView.loadUrl("https://www.bbc.com/news");
    }

    private void showTamilCalendarScreen() {
        currentScreen = "tamil_calendar";
        if (webView != null) { webView.destroy(); webView = null; }
        webView = new WebView(this);
        WebSettings settings = webView.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.addView(webView,new LinearLayout.LayoutParams(-1,0,1f)); root.addView(createBottomNavigation(2)); setContentView(root);
        webView.loadUrl("https://www.prokerala.com/general/calendar/tamilcalendar.php");
    }

    private LinearLayout createBottomNavigation(int selectedTab) {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        // Extra bottom inset keeps labels clear of Android's system navigation area.
        nav.setPadding(dp(2), dp(1), dp(2), dp(10));
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setBackgroundColor(Color.rgb(245, 247, 250));
        String[] labels = {"Travel", "Utilities"};
        String[] screens = {"travel", "utilities"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            Button button = new Button(this);
            button.setText(labels[i]);
            button.setAllCaps(false);
            button.setTextSize(11);
            button.setMinHeight(dp(40));
            button.setMinimumHeight(dp(40));
            button.setPadding(dp(1), 0, dp(1), 0);
            button.setIncludeFontPadding(false);
            button.setTextColor(((i == 0 && selectedTab == 1) || (i == 1 && selectedTab == 2)) ? Color.rgb(20, 90, 160) : Color.DKGRAY);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    ((i == 0 && selectedTab == 1) || (i == 1 && selectedTab == 2)) ? Color.rgb(225, 238, 252) : Color.rgb(245, 247, 250)));
            nav.addView(button, new LinearLayout.LayoutParams(0, dp(42), 1f));
            button.setOnClickListener(v -> {
                if (webView != null) { webView.destroy(); webView = null; }
                navigateTo(screens[tab]);
            });
        }
        return nav;
    }

    private void showAgeCalculatorScreen() {
        currentScreen = "age";
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(18, 16, 18, 12);
        page.setBackgroundColor(Color.WHITE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, 14);

        LinearLayout bmiHeader = new LinearLayout(this);
        bmiHeader.setOrientation(LinearLayout.HORIZONTAL);
        bmiHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = new TextView(this);
        heading.setText("Age Calculator");
        heading.setTextSize(25);
        heading.setTextColor(Color.rgb(25, 55, 90));
        heading.setGravity(Gravity.CENTER);
        bmiHeader.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        Button bmiHome = new Button(this); bmiHome.setText("⌂"); bmiHome.setTextSize(21); bmiHome.setAllCaps(false); bmiHome.setMinWidth(dp(44)); bmiHome.setOnClickListener(v -> { screenHistory.clear(); renderScreen("home"); }); bmiHeader.addView(bmiHome);
        content.addView(bmiHeader);

        TextView intro = new TextView(this);
        intro.setText("Save your date of birth once to calculate your exact age and next birthday.");
        intro.setTextSize(14);
        intro.setTextColor(Color.DKGRAY);
        intro.setPadding(0, 8, 0, 14);
        content.addView(intro);

        content.addView(sectionTitle("Date of birth"));
        final Calendar dob = Calendar.getInstance();
        String savedDob = prefs().getString(DOB_KEY, "");
        if (!savedDob.isEmpty()) {
            try {
                String[] parts = savedDob.split("-");
                dob.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                        Integer.parseInt(parts[2]), 0, 0, 0);
                dob.set(Calendar.MILLISECOND, 0);
            } catch (Exception ignored) { }
        } else {
            dob.add(Calendar.YEAR, -18);
        }

        TextView dobValue = new TextView(this);
        dobValue.setText(savedDob.isEmpty() ? "No date of birth saved" : formatDob(savedDob));
        dobValue.setTextSize(17);
        dobValue.setTextColor(Color.BLACK);
        dobValue.setPadding(0, 8, 0, 8);
        content.addView(dobValue);

        Button chooseDob = new Button(this);
        chooseDob.setText("Choose Date of Birth");
        chooseDob.setAllCaps(false);
        content.addView(chooseDob);
        chooseDob.setOnClickListener(v -> {
            Calendar initial = (Calendar) dob.clone();
            DatePickerDialog picker = new DatePickerDialog(this,
                    (DatePicker view, int year, int month, int day) -> {
                        Calendar selected = Calendar.getInstance();
                        selected.clear();
                        selected.set(year, month, day);
                        Calendar todayCal = Calendar.getInstance();
                        if (selected.after(todayCal)) {
                            Toast.makeText(this, "Date of birth cannot be in the future.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        dob.setTimeInMillis(selected.getTimeInMillis());
                        String value = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day);
                        prefs().edit().putString(DOB_KEY, value).apply();
                        int years = calculateAgeYears(dob, todayCal);
                        prefs().edit().putInt(BMI_AGE_KEY, years).apply();
                        showAgeCalculatorScreen();
                    }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH),
                    initial.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis());
            picker.show();
        });

        content.addView(sectionTitle("Your age today"));
        TextView result = new TextView(this);
        result.setTextSize(20);
        result.setTextColor(Color.rgb(35, 65, 90));
        result.setPadding(0, 8, 0, 12);
        content.addView(result);
        if (savedDob.isEmpty()) {
            result.setText("Choose your date of birth to calculate your age.");
        } else {
            try {
                Calendar birth = Calendar.getInstance();
                String[] parts = savedDob.split("-");
                birth.clear();
                birth.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                        Integer.parseInt(parts[2]));
                Calendar todayCal = Calendar.getInstance();
                if (birth.after(todayCal)) {
                    result.setText("The saved date of birth is in the future. Please choose it again.");
                } else {
                    int years = todayCal.get(Calendar.YEAR) - birth.get(Calendar.YEAR);
                    Calendar anniversary = (Calendar) birth.clone();
                    anniversary.set(Calendar.YEAR, todayCal.get(Calendar.YEAR));
                    if (anniversary.after(todayCal)) years--;
                    Calendar lastBirthday = (Calendar) birth.clone();
                    lastBirthday.add(Calendar.YEAR, years);
                    int months = 0;
                    while (true) {
                        Calendar nextMonth = (Calendar) lastBirthday.clone();
                        nextMonth.add(Calendar.MONTH, 1);
                        if (nextMonth.after(todayCal)) break;
                        lastBirthday = nextMonth;
                        months++;
                    }
                    long days = (todayCal.getTimeInMillis() - lastBirthday.getTimeInMillis()) / 86400000L;
                    result.setText(years + " years, " + months + " months, " + days + " days");
                    Calendar nextBirthday = (Calendar) birth.clone();
                    nextBirthday.set(Calendar.YEAR, todayCal.get(Calendar.YEAR));
                    nextBirthday.set(Calendar.HOUR_OF_DAY, 0);
                    nextBirthday.set(Calendar.MINUTE, 0);
                    nextBirthday.set(Calendar.SECOND, 0);
                    nextBirthday.set(Calendar.MILLISECOND, 0);
                    Calendar todayMidnight = (Calendar) todayCal.clone();
                    todayMidnight.set(Calendar.HOUR_OF_DAY, 0);
                    todayMidnight.set(Calendar.MINUTE, 0);
                    todayMidnight.set(Calendar.SECOND, 0);
                    todayMidnight.set(Calendar.MILLISECOND, 0);
                    if (!nextBirthday.after(todayMidnight)) nextBirthday.add(Calendar.YEAR, 1);
                    long daysUntil = (nextBirthday.getTimeInMillis() - todayMidnight.getTimeInMillis()) / 86400000L;
                    content.addView(sectionTitle("Next birthday"));
                    TextView next = new TextView(this);
                    next.setText(formatDate(nextBirthday.getTime()) + "  •  " + daysUntil + " days to go");
                    next.setTextSize(16);
                    next.setTextColor(Color.DKGRAY);
                    next.setPadding(0, 4, 0, 10);
                    content.addView(next);
                    prefs().edit().putInt(BMI_AGE_KEY, years).apply();
                }
            } catch (Exception e) {
                result.setText("Choose your date of birth to calculate your age.");
            }
        }
        TextView note = new TextView(this);
        note.setText("Your date of birth is saved only on this phone. The calculated age is also used by the BMI screen.");
        note.setTextSize(13);
        note.setTextColor(Color.DKGRAY);
        note.setPadding(0, 10, 0, 8);
        content.addView(note);

        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(createBottomNavigation(2));
        setContentView(page);
    }

    private int calculateAgeYears(Calendar birth, Calendar today) {
        int years = today.get(Calendar.YEAR) - birth.get(Calendar.YEAR);
        Calendar anniversary = (Calendar) birth.clone();
        anniversary.set(Calendar.YEAR, today.get(Calendar.YEAR));
        if (anniversary.after(today)) years--;
        return years;
    }

    private String formatDob(String isoDate) {
        try {
            String[] parts = isoDate.split("-");
            Calendar c = Calendar.getInstance();
            c.clear();
            c.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1, Integer.parseInt(parts[2]));
            return "Date of birth: " + formatDate(c.getTime());
        } catch (Exception e) { return isoDate; }
    }

    private String formatDate(Date date) {
        return new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date);
    }

    private void showBmiScreen() {
        currentScreen = "bmi";
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(18, 16, 18, 18);
        page.setBackgroundColor(Color.WHITE);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, 12);

        LinearLayout bmiHeader = new LinearLayout(this); bmiHeader.setOrientation(LinearLayout.HORIZONTAL); bmiHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = new TextView(this);
        heading.setText("BMI & Weight Tracker");
        heading.setTextSize(25);
        heading.setTextColor(Color.rgb(25, 55, 90));
        heading.setGravity(Gravity.CENTER);
        content.addView(heading);

        TextView intro = new TextView(this);
        intro.setText("Your profile and weight history are saved on this phone.");
        intro.setTextSize(14);
        intro.setTextColor(Color.DKGRAY);
        intro.setPadding(0, 6, 0, 14);
        content.addView(intro);

        TextView profileTitle = sectionTitle("Basic information");
        content.addView(profileTitle);
        EditText ageInput = new EditText(this);
        ageInput.setSingleLine(true);
        ageInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        ageInput.setHint("Age in years");
        ageInput.setText(prefs().contains(BMI_AGE_KEY)
                ? String.valueOf(prefs().getInt(BMI_AGE_KEY, 0)) : "");
        content.addView(ageInput);

        EditText heightInput = new EditText(this);
        heightInput.setSingleLine(true);
        heightInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        heightInput.setHint("Height in centimetres (e.g. 170)");
        heightInput.setText(prefs().contains(BMI_HEIGHT_KEY)
                ? String.valueOf(prefs().getFloat(BMI_HEIGHT_KEY, 0f)) : "");
        content.addView(heightInput);

        Button saveProfile = new Button(this);
        saveProfile.setText("Save Basic Information");
        saveProfile.setAllCaps(false);
        content.addView(saveProfile);
        saveProfile.setOnClickListener(v -> {
            try {
                int age = Integer.parseInt(ageInput.getText().toString().trim());
                float height = Float.parseFloat(heightInput.getText().toString().trim());
                if (age < 1 || age > 120 || height < 50 || height > 260) {
                    Toast.makeText(this, "Enter a valid age and height.", Toast.LENGTH_LONG).show();
                    return;
                }
                prefs().edit().putInt(BMI_AGE_KEY, age)
                        .putFloat(BMI_HEIGHT_KEY, height).apply();
                Toast.makeText(this, "Basic information saved on this phone.", Toast.LENGTH_SHORT).show();
                showBmiScreen();
            } catch (Exception e) {
                Toast.makeText(this, "Enter age and height first.", Toast.LENGTH_LONG).show();
            }
        });

        content.addView(sectionTitle("Today's weight"));
        TextView today = new TextView(this);
        today.setText("Date: " + new SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(new Date())
                + " (one entry per day; saving again updates today's value)");
        today.setTextSize(13);
        today.setTextColor(Color.DKGRAY);
        today.setPadding(0, 4, 0, 6);
        content.addView(today);

        EditText weightInput = new EditText(this);
        weightInput.setSingleLine(true);
        weightInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        weightInput.setHint("Weight in kg (e.g. 72.5)");
        String todayKey = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        JSONObject weights = readWeightLog();
        if (weights.has(todayKey)) weightInput.setText(String.valueOf(weights.optDouble(todayKey)));
        content.addView(weightInput);

        Button saveWeight = new Button(this);
        saveWeight.setText("Save Today's Weight");
        saveWeight.setAllCaps(false);
        content.addView(saveWeight);
        saveWeight.setOnClickListener(v -> {
            try {
                double weight = Double.parseDouble(weightInput.getText().toString().trim());
                if (weight < 15 || weight > 350) {
                    Toast.makeText(this, "Enter a weight between 15 and 350 kg.", Toast.LENGTH_LONG).show();
                    return;
                }
                JSONObject log = readWeightLog();
                String key = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                log.put(key, weight);
                prefs().edit().putString(BMI_WEIGHT_LOG_KEY, log.toString()).apply();
                Toast.makeText(this, "Today's weight saved.", Toast.LENGTH_SHORT).show();
                showBmiScreen();
            } catch (Exception e) {
                Toast.makeText(this, "Enter your weight in kg.", Toast.LENGTH_LONG).show();
            }
        });

        content.addView(sectionTitle("Your BMI"));
        TextView bmiSummary = new TextView(this);
        bmiSummary.setTextSize(17);
        bmiSummary.setTextColor(Color.rgb(35, 65, 90));
        bmiSummary.setPadding(0, 8, 0, 10);
        if (!prefs().contains(BMI_HEIGHT_KEY)) {
            bmiSummary.setText("Save your height and enter today's weight to calculate BMI.");
        } else {
            double h = prefs().getFloat(BMI_HEIGHT_KEY, 0f) / 100.0;
            double latestWeight = latestWeight(weights);
            if (latestWeight > 0 && h > 0) {
                double bmi = latestWeight / (h * h);
                int savedAge = prefs().getInt(BMI_AGE_KEY, 0);
                if (savedAge >= 18) {
                    String category = bmi < 18.5 ? "Underweight" : bmi < 25 ? "Healthy range" : bmi < 30 ? "Overweight" : "Obesity range";
                    bmiSummary.setText(String.format(Locale.getDefault(),
                            "BMI: %.1f\nCategory: %s\nUsing your latest saved weight (%.1f kg).\n\nFor adults, BMI is a screening measure, not a diagnosis.",
                            bmi, category, latestWeight));
                } else {
                    bmiSummary.setText(String.format(Locale.getDefault(),
                            "BMI: %.1f\nUsing your latest saved weight (%.1f kg).\n\nFor people under 18, BMI must be interpreted using age- and sex-specific growth charts; adult categories do not apply.",
                            bmi, latestWeight));
                }
            } else {
                bmiSummary.setText("Save today's weight to calculate your BMI.");
            }
        }
        content.addView(bmiSummary);
        content.addView(sectionTitle("BMI colour scale (adults)"));
        LinearLayout bmiScale = new LinearLayout(this);
        bmiScale.setOrientation(LinearLayout.HORIZONTAL);
        String[] scaleNames = {"Low", "Healthy", "High", "Very high"};
        int[] scaleColors = {Color.rgb(70, 145, 230), Color.rgb(54, 160, 95),
                Color.rgb(245, 170, 45), Color.rgb(220, 75, 75)};
        for (int i = 0; i < scaleNames.length; i++) {
            TextView segment = new TextView(this);
            segment.setText(scaleNames[i]);
            segment.setTextColor(Color.WHITE);
            segment.setTextSize(10);
            segment.setGravity(Gravity.CENTER);
            segment.setPadding(dp(2), dp(8), dp(2), dp(8));
            segment.setBackgroundColor(scaleColors[i]);
            bmiScale.addView(segment, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        content.addView(bmiScale);
        TextView scaleLabels = new TextView(this);
        scaleLabels.setText("< 18.5       18.5–24.9       25–29.9       ≥ 30\nAdult screening ranges only; BMI for under-18s needs age- and sex-specific charts.");
        scaleLabels.setTextSize(11);
        scaleLabels.setTextColor(Color.DKGRAY);
        scaleLabels.setPadding(0, dp(4), 0, dp(8));
        content.addView(scaleLabels);

        content.addView(sectionTitle("Weight change"));
        TextView changeSummary = new TextView(this);
        changeSummary.setTextSize(15);
        changeSummary.setTextColor(Color.rgb(35, 65, 90));
        changeSummary.setPadding(0, dp(4), 0, dp(8));
        changeSummary.setText(buildWeightChangeSummary(readWeightLog()));
        content.addView(changeSummary);

        content.addView(sectionTitle("Weight trend"));
        TextView graphHelp = new TextView(this);
        graphHelp.setText("Daily saved weights (kg). Save a weight each day to build your trend.");
        graphHelp.setTextSize(13);
        graphHelp.setTextColor(Color.DKGRAY);
        content.addView(graphHelp);
        WeightGraph graph = new WeightGraph(this, readWeightLog());
        LinearLayout.LayoutParams graphParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(230));
        graphParams.topMargin = dp(8);
        graphParams.bottomMargin = dp(8);
        content.addView(graph, graphParams);

        content.addView(sectionTitle("Saved entries"));
        addWeightHistory(content, readWeightLog());

        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(createBottomNavigation(2));
        setContentView(page);
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this);
        title.setText(text);
        title.setTextSize(18);
        title.setTextColor(Color.BLACK);
        title.setPadding(0, 16, 0, 4);
        return title;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String buildWeightChangeSummary(JSONObject log) {
        try {
            java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
            String latestDate = "";
            java.util.Iterator<String> it = log.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (k.compareTo(latestDate) > 0) latestDate = k;
            }
            if (latestDate.isEmpty()) {
                return "Save your weight on different days to compare daily and weekly changes.";
            }
            double latest = log.optDouble(latestDate, -1);
            if (latest <= 0) return "Save your weight on different days to compare daily and weekly changes.";
            Calendar reference = Calendar.getInstance();
            String[] parts = latestDate.split("-");
            reference.clear();
            reference.set(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) - 1,
                    Integer.parseInt(parts[2]));
            Calendar target = (Calendar) reference.clone();
            target.add(Calendar.DAY_OF_YEAR, -1);
            String previousDayKey = df.format(target.getTime());
            String daily = log.has(previousDayKey)
                    ? formatWeightDelta(latest, log.optDouble(previousDayKey))
                    : "No entry for " + previousDayKey;
            target = (Calendar) reference.clone();
            target.add(Calendar.DAY_OF_YEAR, -7);
            String weekKey = df.format(target.getTime());
            String weekly = log.has(weekKey)
                    ? formatWeightDelta(latest, log.optDouble(weekKey))
                    : "No entry for " + weekKey;
            return "Latest saved weight: " + String.format(Locale.getDefault(), "%.1f kg", latest)
                    + " (" + latestDate + ")\nSince previous day: " + daily
                    + "\nSince 7 days earlier: " + weekly
                    + "\nPercentages compare with the exact date's entry; missing dates are not estimated.";
        } catch (Exception e) {
            return "Save weights on different days to see daily and weekly changes.";
        }
    }

    private String formatWeightDelta(double current, double previous) {
        if (previous <= 0) return "Not available";
        double kg = current - previous;
        double pct = kg / previous * 100.0;
        String direction = kg > 0 ? "+" : "";
        return String.format(Locale.getDefault(), "%s%.1f kg (%s%.1f%%)", direction, kg, direction, pct);
    }

    private JSONObject readWeightLog() {
        try {
            return new JSONObject(prefs().getString(BMI_WEIGHT_LOG_KEY, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private double latestWeight(JSONObject log) {
        String latestDate = "";
        double latest = -1;
        java.util.Iterator<String> keys = log.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.compareTo(latestDate) > 0) {
                latestDate = key;
                latest = log.optDouble(key, -1);
            }
        }
        return latest;
    }

    private void addWeightHistory(LinearLayout content, JSONObject log) {
        java.util.ArrayList<String> dates = new java.util.ArrayList<>();
        java.util.Iterator<String> keys = log.keys();
        while (keys.hasNext()) dates.add(keys.next());
        java.util.Collections.sort(dates, java.util.Collections.reverseOrder());
        if (dates.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No weights saved yet.");
            empty.setTextColor(Color.DKGRAY);
            content.addView(empty);
            return;
        }
        int count = Math.min(dates.size(), 30);
        for (int i = 0; i < count; i++) {
            String date = dates.get(i);
            TextView row = new TextView(this);
            row.setText(date + "     " + String.format(Locale.getDefault(), "%.1f kg", log.optDouble(date)));
            row.setTextColor(Color.DKGRAY);
            row.setTextSize(15);
            row.setPadding(4, 8, 4, 8);
            content.addView(row);
            View line = new View(this);
            line.setBackgroundColor(Color.rgb(230, 230, 230));
            content.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        }
    }

    private class WeightGraph extends View {
        private final JSONObject log;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final java.util.ArrayList<String> dates = new java.util.ArrayList<>();

        WeightGraph(android.content.Context context, JSONObject data) {
            super(context);
            log = data;
            java.util.Iterator<String> keys = log.keys();
            while (keys.hasNext()) dates.add(keys.next());
            java.util.Collections.sort(dates);
            setBackgroundColor(Color.rgb(250, 252, 255));
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float left = dp(44), right = getWidth() - dp(12), top = dp(18), bottom = getHeight() - dp(34);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.LTGRAY);
            for (int i = 0; i < 4; i++) {
                float y = top + (bottom - top) * i / 3f;
                canvas.drawLine(left, y, right, y, paint);
            }
            if (dates.size() < 1) {
                paint.setColor(Color.DKGRAY); paint.setTextSize(dp(14));
                canvas.drawText("No data yet", left, (top + bottom) / 2, paint);
                return;
            }
            double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
            for (String date : dates) { double w = log.optDouble(date); min = Math.min(min, w); max = Math.max(max, w); }
            if (max - min < 1) { max += 0.5; min -= 0.5; }
            paint.setColor(Color.DKGRAY); paint.setTextSize(dp(10));
            canvas.drawText(String.format(Locale.getDefault(), "%.1f", max), dp(3), top + dp(4), paint);
            canvas.drawText(String.format(Locale.getDefault(), "%.1f", min), dp(3), bottom, paint);
            paint.setColor(Color.rgb(30, 115, 210)); paint.setStrokeWidth(dp(3)); paint.setStyle(Paint.Style.STROKE);
            android.graphics.Path path = new android.graphics.Path();
            for (int i = 0; i < dates.size(); i++) {
                float x = dates.size() == 1 ? (left + right) / 2 : left + (right - left) * i / (dates.size() - 1f);
                float y = bottom - (float) ((log.optDouble(dates.get(i)) - min) / (max - min)) * (bottom - top);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int i = 0; i < dates.size(); i++) {
                float x = dates.size() == 1 ? (left + right) / 2 : left + (right - left) * i / (dates.size() - 1f);
                float y = bottom - (float) ((log.optDouble(dates.get(i)) - min) / (max - min)) * (bottom - top);
                canvas.drawCircle(x, y, dp(4), paint);
            }
            paint.setColor(Color.DKGRAY); paint.setTextSize(dp(9));
            canvas.drawText(dates.get(0).substring(5), left, getHeight() - dp(8), paint);
            String last = dates.get(dates.size() - 1);
            float textWidth = paint.measureText(last.substring(5));
            canvas.drawText(last.substring(5), Math.max(left, right - textWidth), getHeight() - dp(8), paint);
        }
    }

    private Location getLastKnownWeatherLocation() {
        if (!hasLocationPermission()) return null;
        try {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location best = null;
            for (String provider : manager.getProviders(true)) {
                Location loc = manager.getLastKnownLocation(provider);
                if (loc != null && (best == null || loc.getTime() > best.getTime())) best = loc;
            }
            return best;
        } catch (SecurityException ignored) { return null; }
    }

    private void requestFreshWeatherLocation() {
        if (!hasLocationPermission()) return;
        try {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            LocationListener listener = new LocationListener() {
                @Override public void onLocationChanged(Location location) {
                    try { manager.removeUpdates(this); } catch (Exception ignored) { }
                    if (location != null) loadHomeWeather();
                }
                @Override public void onProviderEnabled(String provider) { }
                @Override public void onProviderDisabled(String provider) { }
                @Override public void onStatusChanged(String provider, int status, android.os.Bundle extras) { }
            };
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                manager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper());
            else if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER))
                manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper());
        } catch (Exception ignored) { }
    }

    private void loadHomeWeather() {
        if (weatherTemperatureText == null && homeWeatherText == null) return;
        Location current = getLastKnownWeatherLocation();
        if (current == null) {
            if (weatherTemperatureText != null) weatherTemperatureText.setText("Finding location…");
            if (homeWeatherText != null) homeWeatherText.setText("Waiting for current location. Allow location permission and ensure Location is enabled.");
            if (hasLocationPermission()) requestFreshWeatherLocation();
            return;
        }
        final double lat = current.getLatitude();
        final double lon = current.getLongitude();
        long now = System.currentTimeMillis();
        long cachedAt = prefs().getLong(WEATHER_CACHE_TIME, 0L);
        if (now - cachedAt < WEATHER_REFRESH_MS && cachedAt > 0) {
            if (weatherTemperatureText != null) weatherTemperatureText.setText(prefs().getString(WEATHER_CACHE_TEMP, "--°C"));
            if (weatherIconText != null) weatherIconText.setText(prefs().getString(WEATHER_CACHE_ICON, "🌤️"));
            if (homeWeatherText != null) homeWeatherText.setText(prefs().getString(WEATHER_CACHE_TEXT, "Weather data was recently updated."));
            return;
        }
        if (homeWeatherText != null) homeWeatherText.setText("Loading current-location forecast…");
        if (weatherTemperatureText != null) weatherTemperatureText.setText("Loading…");

        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String apiUrl = "https://api.open-meteo.com/v1/forecast"
                        + "?latitude=" + lat
                        + "&longitude=" + lon
                        + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
                        + "&hourly=temperature_2m,weather_code,precipitation_probability,relative_humidity_2m"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                        + "&forecast_days=3&timezone=auto";

                connection = (HttpURLConnection) new URL(apiUrl).openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/json");

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) {
                    runOnUiThread(() -> {
                        if (homeWeatherText != null) { homeWeatherText.setText("Forecast unavailable. Check your internet connection and refresh."); }
                        if (weatherTemperatureText != null) weatherTemperatureText.setText("Tap to retry");
                    });
                    return;
                }

                StringBuilder response = new StringBuilder();
                try (InputStream stream = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
                    String line;
                    while ((line = reader.readLine()) != null) response.append(line);
                }

                JSONObject root = new JSONObject(response.toString());
                JSONObject currentWeather = root.optJSONObject("current");
                JSONObject daily = root.optJSONObject("daily");
                if (currentWeather == null || daily == null) {
                    runOnUiThread(() -> {
                        if (homeWeatherText != null) { homeWeatherText.setText("Forecast data was incomplete. Please refresh."); }
                        if (weatherTemperatureText != null) weatherTemperatureText.setText("Unavailable");
                    });
                    return;
                }

                double temperature = currentWeather.optDouble("temperature_2m", Double.NaN);
                double feelsLike = currentWeather.optDouble("apparent_temperature", Double.NaN);
                int humidity = currentWeather.optInt("relative_humidity_2m", -1);
                int weatherCode = currentWeather.optInt("weather_code", -1);
                double wind = currentWeather.optDouble("wind_speed_10m", Double.NaN);

                JSONArray dates = daily.optJSONArray("time");
                JSONArray maxTemps = daily.optJSONArray("temperature_2m_max");
                JSONArray minTemps = daily.optJSONArray("temperature_2m_min");
                JSONArray rainChance = daily.optJSONArray("precipitation_probability_max");
                JSONArray dailyCodes = daily.optJSONArray("weather_code");

                StringBuilder display = new StringBuilder();
                display.append("Current location").append("\n")
                        .append(weatherDescription(weatherCode))
                        .append("  •  ")
                        .append(formatWeatherNumber(temperature)).append("°C");

                if (!Double.isNaN(feelsLike)) {
                    display.append("  (feels like ")
                            .append(formatWeatherNumber(feelsLike)).append("°C)");
                }
                display.append("\nHumidity: ")
                        .append(humidity >= 0 ? humidity + "%" : "—")
                        .append("  •  Wind: ")
                        .append(!Double.isNaN(wind)
                                ? formatWeatherNumber(wind) + " km/h" : "—");

                display.append("\n\n3-day forecast");
                if (dates != null && maxTemps != null && minTemps != null) {
                    int count = Math.min(3, Math.min(dates.length(),
                            Math.min(maxTemps.length(), minTemps.length())));
                    for (int i = 0; i < count; i++) {
                        String day = dates.optString(i, "");
                        double max = maxTemps.optDouble(i, Double.NaN);
                        double min = minTemps.optDouble(i, Double.NaN);
                        int code = dailyCodes == null ? -1 : dailyCodes.optInt(i, -1);
                        int rain = rainChance == null ? -1 : rainChance.optInt(i, -1);

                        display.append("\n").append(day).append(": ")
                                .append(weatherDescription(code)).append(", ")
                                .append(formatWeatherNumber(min)).append("–")
                                .append(formatWeatherNumber(max)).append("°C");
                        if (rain >= 0) display.append(", rain chance ").append(rain).append("%");
                    }
                }

                display.append("\n\nWeather: Open-Meteo");
                String finalText = display.toString();
                final String tempText = formatWeatherNumber(temperature) + "°C";
                final String iconText = weatherIcon(weatherCode);
                prefs().edit().putLong(WEATHER_CACHE_TIME, System.currentTimeMillis())
                        .putString(WEATHER_CACHE_TEMP, tempText)
                        .putString(WEATHER_CACHE_ICON, iconText)
                        .putString(WEATHER_CACHE_TEXT, finalText).apply();
                runOnUiThread(() -> {
                    if (homeWeatherText != null) homeWeatherText.setText(finalText);
                    if (weatherTemperatureText != null) weatherTemperatureText.setText(tempText);
                    if (weatherIconText != null) weatherIconText.setText(iconText);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (homeWeatherText != null) { homeWeatherText.setText("Could not load forecast. Check internet and retry."); }
                    if (weatherTemperatureText != null) weatherTemperatureText.setText("Unavailable");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private String weatherIcon(int code) {
        if (code == 0 || code == 1) return "☀️";
        if (code == 2 || code == 3 || code == 45 || code == 48) return "☁️";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82) || code == 95 || code == 96 || code == 99) return "🌧️";
        if (code >= 71 && code <= 86) return "🌨️";
        return "🌤️";
    }

    private void showWeatherDetailScreen() {
        currentScreen = "weather";
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(8), dp(14), 0);
        page.setBackgroundColor(Color.WHITE);
        TextView heading = new TextView(this); heading.setText("Home Weather Forecast");
        heading.setTextSize(23); heading.setTextColor(Color.rgb(25,55,90)); heading.setGravity(Gravity.CENTER);
        page.addView(heading);
        TextView intro = new TextView(this); intro.setText("Hourly weather for the next 24 hours and a 3-day outlook.");
        intro.setTextColor(Color.DKGRAY); intro.setPadding(0, dp(5), 0, dp(8)); page.addView(intro);
        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = new Button(this); refresh.setText("Refresh"); refresh.setAllCaps(false);
        Button map = new Button(this); map.setText("Weather Map"); map.setAllCaps(false);
        actions.addView(refresh, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(map, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        page.addView(actions);
        TextView forecast = new TextView(this); forecast.setTextSize(14); forecast.setTextColor(Color.BLACK);
        forecast.setPadding(dp(6), dp(8), dp(6), dp(18));
        ScrollView scroll = new ScrollView(this); scroll.addView(forecast);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(createBottomNavigation(0)); setContentView(page);
        refresh.setOnClickListener(v -> fetchDetailedWeather(forecast));
        map.setOnClickListener(v -> navigateTo("weather_map"));
        fetchDetailedWeather(forecast);
    }

    private void fetchDetailedWeather(TextView target) {
        Location currentLocation = getLastKnownWeatherLocation();
        if (currentLocation == null) {
            target.setText("Current location unavailable. Allow location permission and turn on Location.");
            if (!hasLocationPermission()) requestLocationPermission(); else requestFreshWeatherLocation();
            return;
        }
        double lat = currentLocation.getLatitude(); double lon = currentLocation.getLongitude();
        target.setText("Loading hourly forecast…");
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                String api = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m"
                    + "&hourly=temperature_2m,weather_code,precipitation_probability,relative_humidity_2m"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                    + "&forecast_days=3&forecast_hours=24&timezone=auto";
                connection = (HttpURLConnection) new URL(api).openConnection(); connection.setConnectTimeout(12000); connection.setReadTimeout(12000);
                StringBuilder body = new StringBuilder();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(connection.getInputStream()))) { String line; while ((line=r.readLine())!=null) body.append(line); }
                JSONObject root = new JSONObject(body.toString()); JSONObject current = root.optJSONObject("current");
                JSONObject hourly = root.optJSONObject("hourly"); JSONObject daily = root.optJSONObject("daily");
                StringBuilder out = new StringBuilder();
                String location = prefs().getString(HOME_ADDRESS_KEY, "Saved home");
                out.append(location).append("\n");
                if (current != null) out.append("NOW  ").append(weatherIcon(current.optInt("weather_code", -1))).append("  ")
                    .append(formatWeatherNumber(current.optDouble("temperature_2m", Double.NaN))).append("°C  ·  ")
                    .append(weatherDescription(current.optInt("weather_code", -1))).append("\nHumidity ")
                    .append(current.optInt("relative_humidity_2m", -1)).append("%  ·  Wind ")
                    .append(formatWeatherNumber(current.optDouble("wind_speed_10m", Double.NaN))).append(" km/h\n\n");
                out.append("NEXT 24 HOURS\n");
                if (hourly != null) {
                    JSONArray times=hourly.optJSONArray("time"), temps=hourly.optJSONArray("temperature_2m"), codes=hourly.optJSONArray("weather_code"), rains=hourly.optJSONArray("precipitation_probability");
                    int n = times == null ? 0 : Math.min(24, times.length());
                    for (int i=0;i<n;i++) { String t=times.optString(i, ""); String clock=t.length()>=16?t.substring(11,16):t;
                        out.append(clock).append("  ").append(weatherIcon(codes==null?-1:codes.optInt(i,-1))).append("  ")
                           .append(temps==null?"—":formatWeatherNumber(temps.optDouble(i,Double.NaN))).append("°C");
                        if (rains!=null && rains.optInt(i,-1)>=0) out.append("  · Rain ").append(rains.optInt(i)).append("%");
                        out.append("\n");
                    }
                }
                out.append("\n3-DAY FORECAST\n");
                if (daily != null) { JSONArray days=daily.optJSONArray("time"), mins=daily.optJSONArray("temperature_2m_min"), maxs=daily.optJSONArray("temperature_2m_max"), codes=daily.optJSONArray("weather_code"), rain=daily.optJSONArray("precipitation_probability_max");
                    int n=days==null?0:Math.min(3,days.length()); for(int i=0;i<n;i++) { out.append(days.optString(i)).append("  ").append(weatherIcon(codes==null?-1:codes.optInt(i,-1))).append("  ")
                        .append(mins==null?"—":formatWeatherNumber(mins.optDouble(i,Double.NaN))).append("–")
                        .append(maxs==null?"—":formatWeatherNumber(maxs.optDouble(i,Double.NaN))).append("°C");
                        if(rain!=null && rain.optInt(i,-1)>=0) out.append("  · Rain ").append(rain.optInt(i)).append("%"); out.append("\n"); } }
                out.append("\nForecast by Open-Meteo · internet required."); String result=out.toString();
                runOnUiThread(() -> target.setText(result));
            } catch(Exception e) { runOnUiThread(() -> target.setText("Weather unavailable. Check your internet connection and tap Refresh.")); }
            finally { if(connection!=null) connection.disconnect(); }
        }).start();
    }

    private void showCurrentLocationScreen() {
        currentScreen = "current_location";
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(16), dp(12), dp(16), dp(12)); page.setBackgroundColor(Color.WHITE);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this); title.setText("Current Location"); title.setTextSize(22); title.setTypeface(null, Typeface.BOLD); title.setTextColor(DARK_TEXT);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f)); Button home = new Button(this); home.setText("⌂"); home.setAllCaps(false); home.setOnClickListener(v -> { screenHistory.clear(); renderScreen("home"); }); header.addView(home); page.addView(header);
        TextView details = new TextView(this); details.setText("Finding your current location…"); details.setTextSize(17); details.setTextColor(DARK_TEXT); details.setPadding(0, dp(20), 0, dp(20)); page.addView(details);
        Button refresh = new Button(this); refresh.setText("Refresh location"); refresh.setAllCaps(false); page.addView(refresh);
        Button map = new Button(this); map.setText("Open map"); map.setAllCaps(false); page.addView(map);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.addView(page, new LinearLayout.LayoutParams(-1, 0, 1f)); root.addView(createBottomNavigation(1)); setContentView(root);
        Runnable update = () -> {
            if (!hasLocationPermission()) { details.setText("Location permission is required to show town, district and PIN code."); return; }
            Location loc = getLastKnownLocation();
            if (loc == null) { details.setText("Waiting for location. Make sure Location is enabled, then tap Refresh location."); requestFreshLocationForScreen(details); return; }
            details.setText(String.format(Locale.getDefault(), "Latitude: %.6f\nLongitude: %.6f\n\nResolving town, district and PIN code…", loc.getLatitude(), loc.getLongitude()));
            new Thread(() -> {
                String town = "Place unavailable", district = "District unavailable", pin = "PIN unavailable", address = "";
                try { if (Geocoder.isPresent()) { List<Address> list = new Geocoder(MainActivity.this, Locale.getDefault()).getFromLocation(loc.getLatitude(), loc.getLongitude(), 1); if (list != null && !list.isEmpty()) { Address a = list.get(0); town = locationFirstNonEmpty(a.getSubLocality(), a.getLocality(), a.getSubAdminArea(), a.getAdminArea()); district = locationFirstNonEmpty(a.getSubAdminArea(), a.getAdminArea()); pin = locationFirstNonEmpty(a.getPostalCode()); address = a.getAddressLine(0) == null ? "" : a.getAddressLine(0); } } } catch (Exception ignored) { }
                final String t=town, d=district, z=pin, ad=address; runOnUiThread(() -> details.setText("Place: " + t + "\nDistrict: " + d + "\nPIN code: " + z + "\n\nLatitude: " + String.format(Locale.US,"%.6f",loc.getLatitude()) + "\nLongitude: " + String.format(Locale.US,"%.6f",loc.getLongitude()) + (ad.isEmpty()?"":"\n\n"+ad)));
            }).start();
        };
        refresh.setOnClickListener(v -> update.run()); map.setOnClickListener(v -> { Location loc=getLastKnownLocation(); if(loc==null) { Toast.makeText(this,"Location not available yet.",Toast.LENGTH_SHORT).show(); return; } startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("geo:"+loc.getLatitude()+","+loc.getLongitude()+"?q="+loc.getLatitude()+","+loc.getLongitude()))); });
        update.run();
    }

    private String locationFirstNonEmpty(String... values) { if (values != null) for (String value : values) if (value != null && !value.trim().isEmpty()) return value.trim(); return "Unavailable"; }

    private void requestFreshLocationForScreen(TextView target) {
        if (!hasLocationPermission()) { requestLocationPermission(); return; }
        try { LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE); LocationListener listener=new LocationListener() { @Override public void onLocationChanged(Location location) { try { lm.removeUpdates(this); } catch(Exception ignored) {} showCurrentLocationScreen(); } @Override public void onProviderEnabled(String provider) {} @Override public void onProviderDisabled(String provider) {} @Override public void onStatusChanged(String provider,int status,Bundle extras) {} }; if(lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) lm.requestSingleUpdate(LocationManager.NETWORK_PROVIDER,listener,Looper.getMainLooper()); else if(lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) lm.requestSingleUpdate(LocationManager.GPS_PROVIDER,listener,Looper.getMainLooper()); } catch(Exception ignored) {}
    }

    private void showWeatherMapScreen() {
        currentScreen = "weather_map";
        if (!prefs().contains(HOME_LAT_KEY) || !prefs().contains(HOME_LON_KEY)) {
            Toast.makeText(this, "Set your home location first.", Toast.LENGTH_LONG).show();
            renderScreen("weather"); return;
        }
        double lat=prefs().getFloat(HOME_LAT_KEY,0f), lon=prefs().getFloat(HOME_LON_KEY,0f);
        LinearLayout page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(Color.WHITE);
        TextView title=new TextView(this); title.setText("Weather Radar Map"); title.setTextSize(20); title.setTextColor(Color.BLACK); title.setPadding(dp(12),dp(8),dp(12),dp(8)); page.addView(title);
        WebView map=new WebView(this); WebSettings settings=map.getSettings(); settings.setJavaScriptEnabled(true); settings.setDomStorageEnabled(true);
        String url="https://www.rainviewer.com/map.html?loc="+lat+","+lon+",7&oFa=0&oC=0&oU=0&oCS=1&oF=0&oAP=1&c=3&o=83&lm=1&layer=radar&sm=1&sn=1";
        map.setWebViewClient(new WebViewClient()); map.loadUrl(url);
        page.addView(map,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));
        Button back=new Button(this); back.setText("Back to Forecast"); back.setAllCaps(false); back.setOnClickListener(v -> { map.destroy(); navigateTo("weather"); }); page.addView(back);
        page.addView(createBottomNavigation(0)); setContentView(page);
    }

    private String formatWeatherNumber(double value) {
        if (Double.isNaN(value)) return "—";
        return String.format(Locale.getDefault(), "%.0f", value);
    }

    private String weatherDescription(int code) {
        switch (code) {
            case 0: return "Clear sky";
            case 1: return "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45:
            case 48: return "Fog";
            case 51:
            case 53:
            case 55: return "Drizzle";
            case 56:
            case 57: return "Freezing drizzle";
            case 61:
            case 63:
            case 65: return "Rain";
            case 66:
            case 67: return "Freezing rain";
            case 71:
            case 73:
            case 75: return "Snow";
            case 77: return "Snow grains";
            case 80:
            case 81:
            case 82: return "Rain showers";
            case 85:
            case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96:
            case 99: return "Thunderstorm with hail";
            default: return "Weather conditions unavailable";
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

    private void startGpsCamera() {
        // Camera capture must not depend on a fresh GPS fix: indoors or with weak GPS,
        // waiting indefinitely makes it look as if the camera button is broken.
        if (!hasLocationPermission()) {
            pendingGpsCameraPermission = true;
            requestLocationPermission();
            return;
        }
        Location cached = getLastKnownLocation();
        if (cached != null) {
            gpsCameraLocation = cached;
            resolveGpsCameraAddressThenLaunch(cached);
            return;
        }

        Toast.makeText(this, "Finding location (camera will open shortly)...", Toast.LENGTH_SHORT).show();
        final boolean[] completed = {false};
        final android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());
        Runnable timeout = () -> {
            if (completed[0]) return;
            completed[0] = true;
            gpsCameraLocation = null;
            gpsCameraPlace = "Location unavailable";
            gpsCameraDistrict = "District unavailable";
            gpsCameraPin = "PIN unavailable";
            showGpsCameraOptions();
        };
        handler.postDelayed(timeout, 6000);
        requestFreshLocation(new LocationCallback() {
            @Override public void onLocation(Location location) {
                if (completed[0]) return;
                completed[0] = true;
                handler.removeCallbacks(timeout);
                gpsCameraLocation = location;
                resolveGpsCameraAddressThenLaunch(location);
            }
            @Override public void onError(String message) {
                if (completed[0]) return;
                completed[0] = true;
                handler.removeCallbacks(timeout);
                gpsCameraLocation = null;
                gpsCameraPlace = "Location unavailable";
                gpsCameraDistrict = "District unavailable";
                gpsCameraPin = "PIN unavailable";
                Toast.makeText(MainActivity.this, message + ". Photo can still be taken without GPS coordinates.", Toast.LENGTH_LONG).show();
                showGpsCameraOptions();
            }
        });
    }

    private boolean pendingGpsCameraPermission = false;

    private void resolveGpsCameraAddressThenLaunch(Location location) {
        new Thread(() -> {
            String place = "Place unavailable", district = "District unavailable", pin = "PIN unavailable";
            try {
                if (Geocoder.isPresent()) {
                    Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address a = addresses.get(0);
                        String locality = a.getLocality();
                        if (locality == null || locality.trim().isEmpty()) locality = a.getSubLocality();
                        if (locality == null || locality.trim().isEmpty()) locality = a.getSubAdminArea();
                        if (locality != null && !locality.trim().isEmpty()) place = locality;
                        String d = a.getSubAdminArea();
                        if (d == null || d.trim().isEmpty()) d = a.getAdminArea();
                        if (d != null && !d.trim().isEmpty()) district = d;
                        if (a.getPostalCode() != null && !a.getPostalCode().trim().isEmpty()) pin = a.getPostalCode();
                    }
                }
            } catch (Exception ignored) { }
            final String p = place, d = district, z = pin;
            runOnUiThread(() -> {
                gpsCameraPlace = p; gpsCameraDistrict = d; gpsCameraPin = z;
                showGpsCameraOptions();
            });
        }).start();
    }

    private void showGpsCameraOptions() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(10), dp(16), dp(8));

        TextView cameraLabel = new TextView(this);
        cameraLabel.setText("Camera"); cameraLabel.setTextSize(15); cameraLabel.setTypeface(null, Typeface.BOLD);
        panel.addView(cameraLabel);
        LinearLayout cameraRow = new LinearLayout(this); cameraRow.setOrientation(LinearLayout.HORIZONTAL);
        Button rear = new Button(this); rear.setText("Back camera"); rear.setAllCaps(false);
        Button front = new Button(this); front.setText("Front camera"); front.setAllCaps(false);
        cameraRow.addView(rear, new LinearLayout.LayoutParams(0, -2, 1f));
        cameraRow.addView(front, new LinearLayout.LayoutParams(0, -2, 1f)); panel.addView(cameraRow);

        TextView ratioLabel = new TextView(this); ratioLabel.setText("Photo format"); ratioLabel.setTextSize(15); ratioLabel.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2); rlp.topMargin = dp(8); panel.addView(ratioLabel, rlp);
        LinearLayout ratioRow = new LinearLayout(this); ratioRow.setOrientation(LinearLayout.HORIZONTAL);
        Button ratio43 = new Button(this); ratio43.setText("4:3"); ratio43.setAllCaps(false);
        Button ratio169 = new Button(this); ratio169.setText("16:9"); ratio169.setAllCaps(false);
        ratioRow.addView(ratio43, new LinearLayout.LayoutParams(0, -2, 1f)); ratioRow.addView(ratio169, new LinearLayout.LayoutParams(0, -2, 1f)); panel.addView(ratioRow);

        TextView note = new TextView(this);
        note.setText("Note: front/back and aspect-ratio choices depend on support in your phone's camera app.");
        note.setTextSize(12); note.setTextColor(Color.DKGRAY);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2); nlp.topMargin = dp(4); panel.addView(note, nlp);

        TextView galleryTitle = new TextView(this); galleryTitle.setText("Recent GPS Camera photos"); galleryTitle.setTextSize(15); galleryTitle.setTypeface(null, Typeface.BOLD);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, -2); glp.topMargin = dp(12); panel.addView(galleryTitle, glp);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout thumbs = new LinearLayout(this); thumbs.setOrientation(LinearLayout.HORIZONTAL); hs.addView(thumbs); panel.addView(hs);
        populateGpsCameraThumbnails(thumbs);

        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle("GPS Camera")
                .setView(panel).setNegativeButton("Close", null).setPositiveButton("Take Photo", null).create();
        rear.setOnClickListener(v -> { gpsCameraFacing = 0; rear.setText("✓ Back camera"); front.setText("Front camera"); });
        front.setOnClickListener(v -> { gpsCameraFacing = 1; front.setText("✓ Front camera"); rear.setText("Back camera"); });
        ratio43.setOnClickListener(v -> { gpsCameraAspect = "4:3"; ratio43.setText("✓ 4:3"); ratio169.setText("16:9"); });
        ratio169.setOnClickListener(v -> { gpsCameraAspect = "16:9"; ratio169.setText("✓ 16:9"); ratio43.setText("4:3"); });
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            dialog.dismiss(); openInAppGpsCamera();
        }));
        dialog.show();
        // Reapply positive button listener after show; AlertDialog creates the button at show time.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> { dialog.dismiss(); openInAppGpsCamera(); });
    }

    private void populateGpsCameraThumbnails(LinearLayout thumbs) {
        try {
            String[] projection = new String[]{MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                    MediaStore.Images.Media.RELATIVE_PATH};
            Cursor c = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
                    null, null, MediaStore.Images.Media.DATE_ADDED + " DESC");
            int shown = 0;
            if (c != null) {
                while (c.moveToNext() && shown < 8) {
                    String path = c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH) >= 0
                            ? c.getString(c.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)) : "";
                    if (path == null || !path.contains("GPS Camera")) continue;
                    long id = c.getLong(c.getColumnIndexOrThrow(MediaStore.Images.Media._ID));
                    Uri uri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                    ImageView image = new ImageView(this);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(88), dp(88));
                    lp.setMargins(dp(4), dp(4), dp(4), dp(4)); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    image.setImageURI(uri); image.setContentDescription("Saved GPS photo"); thumbs.addView(image, lp);
                    image.setOnClickListener(v -> {
                        Intent view = new Intent(Intent.ACTION_VIEW); view.setDataAndType(uri, "image/jpeg");
                        view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        try { startActivity(view); } catch (Exception ignored) {}
                    });
                    shown++;
                }
                c.close();
            }
            if (shown == 0) {
                TextView empty = new TextView(this); empty.setText("No stamped photos yet"); empty.setTextSize(12); empty.setPadding(dp(4),dp(8),dp(4),dp(8)); thumbs.addView(empty);
            }
        } catch (Exception e) {
            TextView unavailable = new TextView(this); unavailable.setText("Photo thumbnails unavailable"); thumbs.addView(unavailable);
        }
    }


    private void openInAppGpsCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 303);
            return;
        }
        gpsCameraScreenOpen = true;
        currentScreen = "gps_camera";
        gpsHasFirstFix = gpsCameraLocation != null;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(12), dp(8), dp(12), dp(8));
        top.setBackgroundColor(Color.rgb(25,25,25));
        Button close = new Button(this); close.setText("Back"); close.setAllCaps(false);
        close.setOnClickListener(v -> closeInAppGpsCamera());
        top.addView(close);
        TextView title = new TextView(this); title.setText("GPS Camera · " + gpsCameraAspect);
        title.setTextColor(Color.WHITE); title.setTextSize(16); title.setTypeface(null, Typeface.BOLD);
        title.setPadding(dp(10),0,0,0);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button switchCam = new Button(this); switchCam.setText("Flip"); switchCam.setAllCaps(false);
        switchCam.setOnClickListener(v -> {
            gpsCameraFacing = gpsCameraFacing == 0 ? 1 : 0;
            closeCameraDevice();
            openCameraWhenReady();
        });
        top.addView(switchCam);
        root.addView(top);

        FrameLayout previewFrame = new FrameLayout(this);
        gpsPreview = new TextureView(this);
        previewFrame.addView(gpsPreview, new FrameLayout.LayoutParams(-1, -1));
        gpsLiveOverlay = new TextView(this);
        gpsLiveOverlay.setTextColor(Color.WHITE);
        gpsLiveOverlay.setTextSize(13);
        gpsLiveOverlay.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        gpsLiveOverlay.setPadding(dp(10), dp(8), dp(10), dp(8));
        gpsLiveOverlay.setBackgroundColor(0xB8000000);
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.RIGHT);
        overlayParams.setMargins(dp(8), dp(8), dp(8), dp(12));
        previewFrame.addView(gpsLiveOverlay, overlayParams);
        root.addView(previewFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setGravity(Gravity.CENTER);
        bottom.setPadding(dp(10), dp(8), dp(10), dp(8));
        bottom.setBackgroundColor(Color.rgb(20,20,20));
        Button ratio = new Button(this); ratio.setText(gpsCameraAspect); ratio.setAllCaps(false);
        ratio.setOnClickListener(v -> {
            gpsCameraAspect = "4:3".equals(gpsCameraAspect) ? "16:9" : "4:3";
            ratio.setText(gpsCameraAspect);
            closeCameraDevice();
            openCameraWhenReady();
        });
        Button shutter = new Button(this); shutter.setText("●"); shutter.setTextSize(25);
        shutter.setContentDescription("Take photo");
        Button gallery = new Button(this); gallery.setText("Photos"); gallery.setAllCaps(false);
        gallery.setOnClickListener(v -> showGpsCameraOptions());
        bottom.addView(ratio);
        LinearLayout.LayoutParams shutterLp = new LinearLayout.LayoutParams(dp(86), dp(64));
        shutterLp.setMargins(dp(18), 0, dp(18), 0);
        bottom.addView(shutter, shutterLp);
        bottom.addView(gallery);
        shutter.setOnClickListener(v -> takeInAppGpsPhoto());
        root.addView(bottom);
        setContentView(root);

        gpsPreview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) { openCameraWhenReady(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) { }
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) { closeCameraDevice(); return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture surface) { }
        });
        startGpsCameraThread();
        startLiveGpsUpdates();
        updateGpsOverlay();
        if (gpsPreview.isAvailable()) openCameraWhenReady();
    }

    private void startGpsCameraThread() {
        if (gpsCameraThread != null) return;
        gpsCameraThread = new HandlerThread("TravelkitCamera2");
        gpsCameraThread.start();
        gpsCameraHandler = new Handler(gpsCameraThread.getLooper());
    }

    private void openCameraWhenReady() {
        if (!gpsCameraScreenOpen || gpsPreview == null || !gpsPreview.isAvailable()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 303); return;
        }
        startGpsCameraThread();
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            String selectedId = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics ch = manager.getCameraCharacteristics(id);
                Integer facing = ch.get(CameraCharacteristics.LENS_FACING);
                if (gpsCameraFacing == 0 && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { selectedId = id; break; }
                if (gpsCameraFacing == 1 && facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) { selectedId = id; break; }
            }
            if (selectedId == null) throw new IOException("Selected camera is not available");
            CameraCharacteristics chars = manager.getCameraCharacteristics(selectedId);
            android.hardware.camera2.params.StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) throw new IOException("Camera preview is not supported");
            Size[] outputs = map.getOutputSizes(android.graphics.ImageFormat.JPEG);
            Size selected = choosePhotoSize(outputs, gpsCameraAspect);
            if (gpsImageReader != null) { gpsImageReader.close(); gpsImageReader = null; }
            gpsImageReader = ImageReader.newInstance(selected.getWidth(), selected.getHeight(), android.graphics.ImageFormat.JPEG, 2);
            gpsImageReader.setOnImageAvailableListener(reader -> {
                Image image = null;
                try {
                    image = reader.acquireNextImage();
                    if (image == null) return;
                    java.nio.ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()]; buffer.get(bytes);
                    saveStampedGpsPhoto(bytes, gpsCameraLocation, gpsCameraPlace, gpsCameraDistrict, gpsCameraPin);
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Could not capture photo: " + e.getMessage(), Toast.LENGTH_LONG).show());
                } finally { if (image != null) image.close(); }
            }, gpsCameraHandler);

            manager.openCamera(selectedId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice camera) {
                    if (!gpsCameraScreenOpen) { camera.close(); return; }
                    gpsCameraDevice = camera; createCameraPreviewSession();
                }
                @Override public void onDisconnected(CameraDevice camera) { camera.close(); gpsCameraDevice = null; }
                @Override public void onError(CameraDevice camera, int error) {
                    camera.close(); gpsCameraDevice = null;
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Camera error (" + error + ").", Toast.LENGTH_LONG).show());
                }
            }, gpsCameraHandler);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open in-app camera: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private Size choosePhotoSize(Size[] sizes, String aspect) {
        if (sizes == null || sizes.length == 0) return new Size(1280, 960);
        double target = "16:9".equals(aspect) ? 16.0 / 9.0 : 4.0 / 3.0;
        Size best = sizes[0]; double bestDiff = Double.MAX_VALUE;
        for (Size size : sizes) {
            double ratio = (double) size.getWidth() / (double) size.getHeight();
            double diff = Math.abs(ratio - target);
            if (diff < bestDiff || (Math.abs(diff-bestDiff) < 0.001 && size.getWidth() > best.getWidth())) { best = size; bestDiff = diff; }
        }
        return best;
    }

    private Size choosePreviewSize(Size[] sizes, int viewWidth, int viewHeight, String aspect) {
        if (sizes == null || sizes.length == 0) return new Size(Math.max(640, viewWidth), Math.max(480, viewHeight));
        double target = "16:9".equals(aspect) ? 16.0/9.0 : 4.0/3.0;
        if (viewHeight > viewWidth) target = 1.0 / target;
        Size best = sizes[0]; double bestScore = Double.MAX_VALUE;
        for (Size size : sizes) {
            double ratio = (double) size.getWidth() / size.getHeight();
            double ratioError = Math.abs(ratio - target);
            double sizePenalty = Math.abs(size.getWidth() - Math.max(viewWidth, viewHeight)) / (double)Math.max(1, Math.max(viewWidth, viewHeight));
            double score = ratioError * 10.0 + sizePenalty;
            if (score < bestScore) { bestScore = score; best = size; }
        }
        return best;
    }

    private void applyPreviewTransform(TextureView view, Size bufferSize) {
        if (view == null || bufferSize == null || view.getWidth() == 0 || view.getHeight() == 0) return;
        Matrix matrix = new Matrix();
        float viewW = view.getWidth(), viewH = view.getHeight();
        float bufferW = bufferSize.getWidth(), bufferH = bufferSize.getHeight();
        RectF viewRect = new RectF(0, 0, viewW, viewH);
        RectF bufferRect = new RectF(0, 0, bufferH, bufferW);
        float cx = viewW / 2f, cy = viewH / 2f;
        bufferRect.offset(cx - bufferRect.centerX(), cy - bufferRect.centerY());
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
        float scale = Math.max(viewW / bufferH, viewH / bufferW);
        matrix.postScale(scale, scale, cx, cy);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (rotation == android.view.Surface.ROTATION_90) matrix.postRotate(90, cx, cy);
        else if (rotation == android.view.Surface.ROTATION_270) matrix.postRotate(-90, cx, cy);
        view.setTransform(matrix);
    }

    private void createCameraPreviewSession() {
        try {
            if (gpsCameraDevice == null || gpsPreview == null || !gpsPreview.isAvailable() || gpsImageReader == null) return;
            SurfaceTexture texture = gpsPreview.getSurfaceTexture();
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            String activeId = gpsCameraDevice.getId();
            CameraCharacteristics activeChars = manager.getCameraCharacteristics(activeId);
            android.hardware.camera2.params.StreamConfigurationMap streamMap = activeChars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] previewSizes = streamMap == null ? null : streamMap.getOutputSizes(SurfaceTexture.class);
            gpsPreviewSize = choosePreviewSize(previewSizes, gpsPreview.getWidth(), gpsPreview.getHeight(), gpsCameraAspect);
            texture.setDefaultBufferSize(gpsPreviewSize.getWidth(), gpsPreviewSize.getHeight());
            applyPreviewTransform(gpsPreview, gpsPreviewSize);
            Surface previewSurface = new Surface(texture);
            Surface imageSurface = gpsImageReader.getSurface();
            final CaptureRequest.Builder builder = gpsCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(previewSurface);
            gpsCameraDevice.createCaptureSession(java.util.Arrays.asList(previewSurface, imageSurface), new CameraCaptureSession.StateCallback() {
                @Override public void onConfigured(CameraCaptureSession session) {
                    if (gpsCameraDevice == null) return;
                    gpsCameraSession = session;
                    try {
                        builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        session.setRepeatingRequest(builder.build(), null, gpsCameraHandler);
                    } catch (CameraAccessException e) {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Preview could not start.", Toast.LENGTH_LONG).show());
                    }
                }
                @Override public void onConfigureFailed(CameraCaptureSession session) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Camera preview setup failed.", Toast.LENGTH_LONG).show());
                }
            }, gpsCameraHandler);
        } catch (Exception e) {
            Toast.makeText(this, "Preview error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void takeInAppGpsPhoto() {
        if (gpsCameraDevice == null || gpsCameraSession == null || gpsImageReader == null) {
            Toast.makeText(this, "Camera is still opening. Please wait.", Toast.LENGTH_SHORT).show(); return;
        }
        try {
            CaptureRequest.Builder capture = gpsCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            capture.addTarget(gpsImageReader.getSurface());
            capture.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);

            // Tell Camera2 how to rotate the JPEG for the phone's current orientation.
            // Without this, landscape captures can be saved upside down on some devices.
            CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            CameraCharacteristics cameraChars =
                    cameraManager.getCameraCharacteristics(gpsCameraDevice.getId());
            Integer sensorOrientationValue =
                    cameraChars.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int sensorOrientation = sensorOrientationValue == null ? 90 : sensorOrientationValue;
            int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
            int deviceDegrees;
            switch (displayRotation) {
                case Surface.ROTATION_90: deviceDegrees = 90; break;
                case Surface.ROTATION_180: deviceDegrees = 180; break;
                case Surface.ROTATION_270: deviceDegrees = 270; break;
                case Surface.ROTATION_0:
                default: deviceDegrees = 0; break;
            }
            Integer lensFacing = cameraChars.get(CameraCharacteristics.LENS_FACING);
            int jpegOrientation;
            if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                jpegOrientation = (sensorOrientation + deviceDegrees) % 360;
            } else {
                jpegOrientation = (sensorOrientation - deviceDegrees + 360) % 360;
            }
            capture.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation);

            gpsCameraSession.capture(capture.build(), new CameraCaptureSession.CaptureCallback() {
                @Override public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, android.hardware.camera2.TotalCaptureResult result) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Photo captured. Saving stamped image…", Toast.LENGTH_SHORT).show());
                }
            }, gpsCameraHandler);
        } catch (Exception e) {
            Toast.makeText(this, "Could not take photo: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveStampedGpsPhoto(byte[] jpeg, Location location, String place, String district, String pin) {
        new Thread(() -> {
            Uri uri = null;
            try {
                Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                if (bitmap == null) throw new IOException("Photo data could not be decoded");
                Bitmap stamped = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                Canvas canvas = new Canvas(stamped);
                float scale = Math.max(1f, stamped.getWidth() / 900f);
                float pad = 14f * scale;
                float textSize = Math.max(18f, stamped.getWidth() / 42f);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setTextSize(textSize); paint.setColor(Color.WHITE); paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                String date = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(new Date());
                String lat = location == null ? "Lat: waiting for GPS" : String.format(Locale.US, "Lat: %.6f", location.getLatitude());
                String lon = location == null ? "Lon: waiting for GPS" : String.format(Locale.US, "Lon: %.6f", location.getLongitude());
                String[] lines = new String[]{date, place, "District: " + district, "PIN: " + pin, lat, lon};
                float lineH = textSize * 1.3f, maxW = 0;
                for (String line : lines) maxW = Math.max(maxW, paint.measureText(line));
                float boxW = Math.min(stamped.getWidth() - 2*pad, maxW + 2*pad);
                float boxH = lines.length*lineH + 2*pad;
                float left = stamped.getWidth()-boxW-pad, top = stamped.getHeight()-boxH-pad;
                Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG); bg.setColor(0xB8000000);
                canvas.drawRoundRect(left, top, left+boxW, top+boxH, 10f*scale, 10f*scale, bg);
                float y = top+pad+textSize;
                for (String line : lines) {
                    float x = Math.max(left+pad, stamped.getWidth()-pad-paint.measureText(line)-pad);
                    canvas.drawText(line, x, y, paint); y += lineH;
                }
                String filename = "GPS_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg";
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, filename);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/GPS Camera");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                }
                uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("Could not create photo in DCIM/GPS Camera");
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new IOException("Cannot save photo");
                    stamped.compress(Bitmap.CompressFormat.JPEG, 94, out); out.flush();
                }
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    ContentValues done = new ContentValues(); done.put(MediaStore.Images.Media.IS_PENDING, 0);
                    getContentResolver().update(uri, done, null, null);
                }
                stamped.recycle(); bitmap.recycle();
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "GPS photo saved in DCIM/GPS Camera", Toast.LENGTH_LONG).show();
                    updateGpsOverlay();
                });
            } catch (Exception e) {
                if (uri != null) try { getContentResolver().delete(uri, null, null); } catch (Exception ignored) {}
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Could not save stamped photo: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private void startLiveGpsUpdates() {
        gpsLiveLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        gpsUiHandler.removeCallbacks(gpsLocationRefreshRunnable);
        gpsUiHandler.post(gpsLocationRefreshRunnable);
        if (!hasLocationPermission()) {
            requestLocationPermission();
            return;
        }
        try {
            if (gpsLiveLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gpsLiveLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gpsLiveLocationListener, Looper.getMainLooper());
            }
            if (gpsLiveLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                gpsLiveLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, gpsLiveLocationListener, Looper.getMainLooper());
            }
        } catch (SecurityException ignored) {}
    }

    private final LocationListener gpsLiveLocationListener = new LocationListener() {
        @Override public void onLocationChanged(Location location) {
            if (location == null) return;
            gpsCameraLocation = location;
            gpsHasFirstFix = true;
            updateGpsOverlay();
            long now = System.currentTimeMillis();
            if (now - gpsLastAddressLookup > 10000L) {
                gpsLastAddressLookup = now;
                resolveLiveGpsAddress(location);
            }
        }
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    };

    private void refreshGpsCameraLocation() {
        if (!hasLocationPermission() || gpsLiveLocationManager == null) { updateGpsOverlay(); return; }
        try {
            Location latest = gpsLiveLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (latest == null) latest = gpsLiveLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (latest != null && (gpsCameraLocation == null || latest.getTime() >= gpsCameraLocation.getTime())) {
                gpsCameraLocation = latest; gpsHasFirstFix = true; updateGpsOverlay();
            }
            if (!gpsHasFirstFix) {
                if (gpsLiveLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    gpsLiveLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, gpsLiveLocationListener, Looper.getMainLooper());
                }
            }
        } catch (Exception ignored) {}
    }

    private void resolveLiveGpsAddress(Location location) {
        new Thread(() -> {
            String place = gpsCameraPlace, district = gpsCameraDistrict, pin = gpsCameraPin;
            try {
                if (Geocoder.isPresent()) {
                    List<Address> addresses = new Geocoder(MainActivity.this, Locale.getDefault())
                            .getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                    if (addresses != null && !addresses.isEmpty()) {
                        Address a = addresses.get(0);
                        String locality = a.getLocality();
                        if (locality == null || locality.trim().isEmpty()) locality = a.getSubLocality();
                        if (locality == null || locality.trim().isEmpty()) locality = a.getSubAdminArea();
                        if (locality != null && !locality.trim().isEmpty()) place = locality;
                        String d = a.getSubAdminArea();
                        if (d == null || d.trim().isEmpty()) d = a.getAdminArea();
                        if (d != null && !d.trim().isEmpty()) district = d;
                        if (a.getPostalCode() != null && !a.getPostalCode().trim().isEmpty()) pin = a.getPostalCode();
                    }
                }
            } catch (Exception ignored) {}
            final String p = place, d = district, z = pin;
            runOnUiThread(() -> { gpsCameraPlace = p; gpsCameraDistrict = d; gpsCameraPin = z; updateGpsOverlay(); });
        }).start();
    }

    private void updateGpsOverlay() {
        if (gpsLiveOverlay == null) return;
        Location l = gpsCameraLocation;
        String lat = l == null ? "Lat: waiting for GPS…" : String.format(Locale.US, "Lat: %.6f", l.getLatitude());
        String lon = l == null ? "Lon: waiting for GPS…" : String.format(Locale.US, "Lon: %.6f", l.getLongitude());
        String time = new SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).format(new Date());
        gpsLiveOverlay.setText(time + "\n" + gpsCameraPlace + "\nDistrict: " + gpsCameraDistrict + "\nPIN: " + gpsCameraPin + "\n" + lat + "\n" + lon);
    }

    private void closeCameraDevice() {
        try { if (gpsCameraSession != null) { gpsCameraSession.close(); gpsCameraSession = null; } } catch (Exception ignored) {}
        try { if (gpsCameraDevice != null) { gpsCameraDevice.close(); gpsCameraDevice = null; } } catch (Exception ignored) {}
    }

    private void closeInAppGpsCamera() {
        gpsCameraScreenOpen = false;
        currentScreen = "utilities";
        gpsUiHandler.removeCallbacks(gpsLocationRefreshRunnable);
        try { if (gpsLiveLocationManager != null) gpsLiveLocationManager.removeUpdates(gpsLiveLocationListener); } catch (Exception ignored) {}
        closeCameraDevice();
        try { if (gpsImageReader != null) { gpsImageReader.close(); gpsImageReader = null; } } catch (Exception ignored) {}
        if (gpsCameraThread != null) {
            gpsCameraThread.quitSafely();
            gpsCameraThread = null; gpsCameraHandler = null;
        }
        gpsLiveOverlay = null; gpsPreview = null;
        showUtilitiesHubScreen();
    }

    private void cleanupGpsCameraUri() {
        if (gpsCameraPhotoUri != null) {
            try { getContentResolver().delete(gpsCameraPhotoUri, null, null); } catch (Exception ignored) {}
        }
        gpsCameraPhotoUri = null;
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
        if (requestCode == 303) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openInAppGpsCamera();
            } else {
                Toast.makeText(this, "Camera permission is needed for GPS Camera.", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (requestCode == LOCATION_REQUEST) {
            if (hasLocationPermission()) {
                if (pendingGpsCameraPermission) {
                    pendingGpsCameraPermission = false;
                    startGpsCamera();
                } else if (pendingLogRequest) {
                    pendingLogRequest = false;
                    startCityLogging();
                } else if (webView == null) {
                    updateDistance();
                    loadHomeWeather();
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
            if (!screenHistory.isEmpty()) {
                String previous = screenHistory.remove(screenHistory.size() - 1);
                renderScreen(previous);
            } else {
                showHomeScreen();
            }
        } else if (!screenHistory.isEmpty()) {
            String previous = screenHistory.remove(screenHistory.size() - 1);
            renderScreen(previous);
        } else if (!"home".equals(currentScreen)) {
            renderScreen("home");
        } else {
            // Android Back on the Home screen exits/minimizes the app normally.
            super.onBackPressed();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Reopen the camera only if GPS Camera is the screen the user is currently using.
        if (!gpsCameraScreenOpen || !"gps_camera".equals(currentScreen)) return;
        closeCameraDevice();
        final TextureView previewAtRotation = gpsPreview;
        if (previewAtRotation == null) return;
        Runnable reopen = new Runnable() {
            int attempts = 0;
            @Override public void run() {
                if (!gpsCameraScreenOpen || !"gps_camera".equals(currentScreen)
                        || gpsPreview != previewAtRotation || isFinishing()) return;
                if (previewAtRotation.isAvailable()) {
                    openCameraWhenReady();
                } else if (++attempts < 8) {
                    previewAtRotation.postDelayed(this, 150L);
                }
            }
        };
        previewAtRotation.postDelayed(reopen, 150L);
    }

    @Override
    protected void onDestroy() {
        if (gpsCameraScreenOpen) {
            gpsCameraScreenOpen = false;
            gpsUiHandler.removeCallbacks(gpsLocationRefreshRunnable);
            try { if (gpsLiveLocationManager != null) gpsLiveLocationManager.removeUpdates(gpsLiveLocationListener); } catch (Exception ignored) {}
            closeCameraDevice();
            try { if (gpsImageReader != null) gpsImageReader.close(); } catch (Exception ignored) {}
            if (gpsCameraThread != null) gpsCameraThread.quitSafely();
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
