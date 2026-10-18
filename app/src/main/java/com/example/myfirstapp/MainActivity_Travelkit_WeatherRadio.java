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
import android.graphics.Typeface;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
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
    private TextView homeWeatherText;
    private TextView weatherTemperatureText;
    private TextView weatherIconText;
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
    private static final String BMI_AGE_KEY = "bmi_age";
    private static final String DOB_KEY = "age_calculator_dob";
    private static final String BMI_HEIGHT_KEY = "bmi_height_cm";
    private static final String BMI_WEIGHT_LOG_KEY = "bmi_weight_log";

    private boolean pendingLogRequest = false;
    private String currentScreen = "home";
    private final java.util.ArrayList<String> screenHistory = new java.util.ArrayList<>();

    private void navigateTo(String destination) {
        if (destination == null || destination.equals(currentScreen)) return;
        screenHistory.add(currentScreen);
        renderScreen(destination);
    }

    private void renderScreen(String screen) {
        currentScreen = screen;
        if ("bmi".equals(screen)) showBmiScreen();
        else if ("age".equals(screen)) showAgeCalculatorScreen();
        else if ("fm".equals(screen)) showFmStationsScreen();
        else if ("weather".equals(screen)) showWeatherDetailScreen();
        else if ("weather_map".equals(screen)) showWeatherMapScreen();
        else showHomeScreen();
    }

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
        currentScreen = "home";
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(24, 16, 24, 24);
        layout.setBackgroundColor(Color.WHITE);

        // Compact, tappable weather tile: condition thumbnail + temperature only.
        LinearLayout weatherCard = new LinearLayout(this);
        weatherCard.setOrientation(LinearLayout.HORIZONTAL);
        weatherCard.setGravity(Gravity.CENTER_VERTICAL);
        weatherCard.setPadding(dp(18), dp(10), dp(18), dp(10));
        weatherCard.setBackgroundColor(Color.rgb(232, 243, 255));
        weatherCard.setClickable(true);
        weatherCard.setFocusable(true);
        weatherIconText = new TextView(this);
        weatherIconText.setText("🌤️");
        weatherIconText.setTextSize(34);
        weatherCard.addView(weatherIconText);
        LinearLayout weatherInfo = new LinearLayout(this);
        weatherInfo.setOrientation(LinearLayout.VERTICAL);
        weatherInfo.setPadding(dp(14), 0, 0, 0);
        TextView weatherLabel = new TextView(this);
        weatherLabel.setText("Home Weather · tap for forecast");
        weatherLabel.setTextSize(13);
        weatherLabel.setTextColor(Color.rgb(20, 55, 90));
        weatherInfo.addView(weatherLabel);
        weatherTemperatureText = new TextView(this);
        weatherTemperatureText.setText("--°C");
        weatherTemperatureText.setTextSize(26);
        weatherTemperatureText.setTypeface(null, Typeface.BOLD);
        weatherTemperatureText.setTextColor(Color.BLACK);
        weatherInfo.addView(weatherTemperatureText);
        weatherCard.addView(weatherInfo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        weatherCard.setOnClickListener(v -> navigateTo("weather"));
        LinearLayout.LayoutParams weatherParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        weatherParams.bottomMargin = dp(10);
        layout.addView(weatherCard, weatherParams);

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

        // Prominent shortcuts on Home ensure BMI and Age Calculator remain easy to find
        // even if the bottom navigation is hard to see on a small phone screen.
        LinearLayout healthShortcuts = new LinearLayout(this);
        healthShortcuts.setOrientation(LinearLayout.HORIZONTAL);
        healthShortcuts.setGravity(Gravity.CENTER);
        Button bmiShortcut = new Button(this);
        bmiShortcut.setText("BMI & Weight");
        bmiShortcut.setAllCaps(false);
        bmiShortcut.setTextSize(13);
        Button ageShortcut = new Button(this);
        ageShortcut.setText("Age Calculator");
        ageShortcut.setAllCaps(false);
        ageShortcut.setTextSize(13);
        healthShortcuts.addView(bmiShortcut, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        healthShortcuts.addView(ageShortcut, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams shortcutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shortcutParams.bottomMargin = dp(12);
        layout.addView(healthShortcuts, shortcutParams);
        bmiShortcut.setOnClickListener(v -> navigateTo("bmi"));
        ageShortcut.setOnClickListener(v -> navigateTo("age"));

        Button fmShortcut = new Button(this);
        fmShortcut.setText("📻 Tamil Nadu FM Stations");
        fmShortcut.setAllCaps(false);
        layout.addView(fmShortcut);
        setFullWidth(fmShortcut, 10);
        fmShortcut.setOnClickListener(v -> navigateTo("fm"));

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

        Button viewLogButton = new Button(this);
        viewLogButton.setText("View Travel Log");
        layout.addView(viewLogButton);
        setFullWidth(viewLogButton, 0);
        viewLogButton.setOnClickListener(v -> showTravelLog());

        ScrollView homeScroll = new ScrollView(this);
        homeScroll.setFillViewport(true);
        homeScroll.addView(layout);
        LinearLayout screenRoot = new LinearLayout(this);
        screenRoot.setOrientation(LinearLayout.VERTICAL);
        screenRoot.addView(homeScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        screenRoot.addView(createBottomNavigation(0));
        setContentView(screenRoot);
        loadHomeWeather();

        if (hasLocationPermission() && prefs().contains(HOME_LAT_KEY)
                && prefs().contains(HOME_LON_KEY)) {
            updateDistance();
        }
    }

    private LinearLayout createBottomNavigation(int selectedTab) {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        // Extra bottom inset keeps labels clear of Android's system navigation area.
        nav.setPadding(dp(2), dp(1), dp(2), dp(10));
        nav.setGravity(Gravity.CENTER_VERTICAL);
        nav.setBackgroundColor(Color.rgb(245, 247, 250));
        String[] labels = {"⌂ Home", "BMI", "Age", "FM"};
        String[] screens = {"home", "bmi", "age", "fm"};
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
            button.setTextColor(selectedTab == i ? Color.rgb(20, 90, 160) : Color.DKGRAY);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    selectedTab == i ? Color.rgb(225, 238, 252) : Color.rgb(245, 247, 250)));
            nav.addView(button, new LinearLayout.LayoutParams(0, dp(42), 1f));
            button.setOnClickListener(v -> navigateTo(screens[tab]));
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

        TextView heading = new TextView(this);
        heading.setText("Age Calculator");
        heading.setTextSize(25);
        heading.setTextColor(Color.rgb(25, 55, 90));
        heading.setGravity(Gravity.CENTER);
        content.addView(heading);

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
        page.addView(createBottomNavigation(1));
        setContentView(page);
    }

    private void showFmStationsScreen() {
        currentScreen = "fm";
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(14), dp(8), dp(14), 0);
        page.setBackgroundColor(Color.WHITE);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this);
        heading.setText("Tamil Nadu FM Stations"); heading.setTextSize(24);
        heading.setTextColor(Color.rgb(25, 55, 90)); heading.setGravity(Gravity.CENTER);
        content.addView(heading);
        TextView note = new TextView(this);
        note.setText("Browse by area and tune your radio to the listed FM frequency. Frequencies may change; reception depends on your location. Tap Listen online to search for a web stream.");
        note.setTextSize(13); note.setTextColor(Color.DKGRAY); note.setPadding(0, dp(6), 0, dp(8));
        content.addView(note);
        Button stopRadio = new Button(this); stopRadio.setText("■ Stop radio playback"); stopRadio.setAllCaps(false);
        stopRadio.setOnClickListener(v -> { Intent stop = new Intent(this, RadioPlaybackService.class); stop.setAction(RadioPlaybackService.ACTION_STOP); startService(stop); Toast.makeText(this, "Radio stopped.", Toast.LENGTH_SHORT).show(); });
        content.addView(stopRadio);
        String[][] areas = {
            {"Chennai", "AIR FM Gold — 100.1 MHz", "AIR FM Rainbow — 101.4 MHz", "Vividh Bharati — 102.3 MHz", "Suryan FM — 93.5 MHz", "Hello FM — 106.4 MHz", "Radio Mirchi — 98.3 MHz", "Radio City — 91.1 MHz", "Big FM — 92.7 MHz", "Fever FM — 91.9 MHz"},
            {"Chengalpattu / Vellore / North TN", "Hello FM — 91.5 MHz (Salem/Vellore listings vary by city)", "Suryan FM — 93.9 MHz (Vellore listing)", "AIR relay — 100.1 MHz (Vellore/Tirupattur)"},
            {"Coimbatore / Erode / Western TN", "AIR FM Rainbow — 103.0 MHz (Coimbatore)", "Suryan FM — 93.5 MHz (Coimbatore listing)", "Hello FM — 106.4 MHz (Coimbatore listing)", "Radio Mirchi — 98.3 MHz (Coimbatore)", "Radio City — 91.1 MHz (Coimbatore)", "Suryan FM — 91.9 MHz (Erode listing)", "Hello FM — 92.7 MHz (Erode listing)"},
            {"Salem / Dharmapuri", "AIR LRS — 102.5 MHz (Dharmapuri)", "AIR relay — 103.7 MHz (Yercaud/Salem)", "Suryan FM — 93.9 MHz (Salem listing)", "Hello FM — 91.5 MHz (Salem listing)"},
            {"Tiruchirappalli / Thanjavur / Delta", "AIR FM Rainbow — 102.1 MHz (Trichy)", "Suryan FM — 93.5 MHz (Trichy listing)", "Hello FM — 106.4 MHz (Trichy listing)", "Radio Mirchi — 95.0 MHz (Trichy listing)", "AIR relay — 101.2 MHz (Thanjavur)"},
            {"Madurai / South Central TN", "AIR FM Rainbow — 103.3 MHz (Madurai)", "Suryan FM — 93.5 MHz (Madurai listing)", "Hello FM — 106.4 MHz (Madurai listing)", "Radio Mirchi — 98.3 MHz (Madurai)", "Radio City — 91.9 MHz (Madurai)"},
            {"Tirunelveli / Thoothukudi / Kanyakumari", "AIR FM Rainbow — 102.6 MHz (Tirunelveli)", "AIR LRS — 101.0 MHz (Nagercoil)", "AIR relay — 100.1 MHz (Thoothukudi)", "Suryan FM — 93.5 MHz (Tirunelveli/Thoothukudi listings)", "Hello FM — 106.4 MHz (Tirunelveli/Thoothukudi listings)", "Radio Mirchi — 95.0 MHz (Tirunelveli listing)"},
            {"Nilgiris / Kodaikanal / Other areas", "AIR FM Rainbow — 101.8 MHz (Ooty)", "AIR FM Rainbow — 100.5 MHz (Kodaikanal)", "AIR relay — 100.1 MHz (Rameswaram)", "AIR relay — 100.1 MHz (Thanjavur listing)"}
        };
        for (String[] area : areas) {
            content.addView(sectionTitle(area[0]));
            for (int i = 1; i < area.length; i++) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(8), dp(7), dp(8), dp(7));
                row.setBackgroundColor(Color.rgb(247, 249, 252));
                TextView station = new TextView(this); station.setText(area[i]);
                station.setTextSize(14); station.setTextColor(Color.BLACK);
                row.addView(station);
                Button listen = new Button(this); listen.setText("Listen online / find stream"); listen.setAllCaps(false); listen.setTextSize(11);
                row.addView(listen);
                final String stationName = area[i];
                listen.setText("▶ Play station");
                listen.setOnClickListener(v -> playFmStation(stationName));
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.bottomMargin = dp(5); content.addView(row, rp);
            }
        }
        TextView source = new TextView(this);
        source.setText("Frequency reference: Prasar Bharati's All India Radio station listings and published station frequency lists. Commercial frequencies can vary by city; confirm locally.");
        source.setTextSize(11); source.setTextColor(Color.DKGRAY); source.setPadding(0, dp(8), 0, dp(12)); content.addView(source);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        page.addView(createBottomNavigation(3));
        setContentView(page);
    }

    private void playFmStation(String stationName) {
        String lower = stationName.toLowerCase(Locale.ROOT);
        String stream = null;
        if (lower.contains("suryan fm")) stream = "https://tamil.crabdance.com:8002/2";
        else if (lower.contains("radio mirchi")) stream = "http://radios.crabdance.com:8002/1";
        else if (lower.contains("hello fm")) stream = "http://radios.crabdance.com:8002/3";
        else if (lower.contains("air fm gold") || lower.contains("vividh bharati"))
            stream = "https://airhlspush.pc.cdn.bitgravity.com/httppush/hlspbaudio005/hlspbaudio005_Auto.m3u8";
        if (stream == null) {
            Toast.makeText(this, "A reliable direct stream is not available for this station yet.", Toast.LENGTH_LONG).show(); return;
        }
        Intent intent = new Intent(this, RadioPlaybackService.class);
        intent.setAction(RadioPlaybackService.ACTION_PLAY); intent.putExtra(RadioPlaybackService.EXTRA_URL, stream);
        intent.putExtra(RadioPlaybackService.EXTRA_STATION, stationName);
        try { if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
            Toast.makeText(this, "Starting " + stationName + ". Playback can continue in background.", Toast.LENGTH_LONG).show();
        } catch (Exception e) { Toast.makeText(this, "Could not start radio. Check connection and app permissions.", Toast.LENGTH_LONG).show(); }
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

    private void loadHomeWeather() {
        if (weatherTemperatureText == null && homeWeatherText == null) return;

        if (!prefs().contains(HOME_LAT_KEY) || !prefs().contains(HOME_LON_KEY)) {
            if (weatherTemperatureText != null) weatherTemperatureText.setText("Set home location");
            if (homeWeatherText != null) homeWeatherText.setText(
                    "Set your home in User Details → Pinpoint Home on Map to see the forecast.");
            return;
        }

        double lat = prefs().getFloat(HOME_LAT_KEY, 0f);
        double lon = prefs().getFloat(HOME_LON_KEY, 0f);
        String homeName = prefs().getString(HOME_ADDRESS_KEY, "Saved home");
        if (homeWeatherText != null) homeWeatherText.setText("Loading forecast for " + homeName + "...");
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
                JSONObject current = root.optJSONObject("current");
                JSONObject daily = root.optJSONObject("daily");
                if (current == null || daily == null) {
                    runOnUiThread(() -> {
                        if (homeWeatherText != null) { homeWeatherText.setText("Forecast data was incomplete. Please refresh."); }
                        if (weatherTemperatureText != null) weatherTemperatureText.setText("Unavailable");
                    });
                    return;
                }

                double temperature = current.optDouble("temperature_2m", Double.NaN);
                double feelsLike = current.optDouble("apparent_temperature", Double.NaN);
                int humidity = current.optInt("relative_humidity_2m", -1);
                int weatherCode = current.optInt("weather_code", -1);
                double wind = current.optDouble("wind_speed_10m", Double.NaN);

                JSONArray dates = daily.optJSONArray("time");
                JSONArray maxTemps = daily.optJSONArray("temperature_2m_max");
                JSONArray minTemps = daily.optJSONArray("temperature_2m_min");
                JSONArray rainChance = daily.optJSONArray("precipitation_probability_max");
                JSONArray dailyCodes = daily.optJSONArray("weather_code");

                StringBuilder display = new StringBuilder();
                display.append(homeName).append("\n")
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
        if (!prefs().contains(HOME_LAT_KEY) || !prefs().contains(HOME_LON_KEY)) {
            target.setText("Set your home location first: User Details → Pinpoint Home on Map."); return;
        }
        double lat = prefs().getFloat(HOME_LAT_KEY, 0f); double lon = prefs().getFloat(HOME_LON_KEY, 0f);
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
        } else if (!screenHistory.isEmpty()) {
            String previous = screenHistory.remove(screenHistory.size() - 1);
            renderScreen(previous);
        } else if (!"home".equals(currentScreen)) {
            renderScreen("home");
        } else {
            // Keep the app on Home instead of letting Back immediately minimize it.
            Toast.makeText(this, "You are on the Travelkit home screen.", Toast.LENGTH_SHORT).show();
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
