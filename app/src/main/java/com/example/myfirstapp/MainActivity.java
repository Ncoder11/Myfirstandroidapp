
package com.example.myfirstapp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
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

public class MainActivity extends Activity {

    private WebView webView;

    private static final int LOCATION_REQUEST = 101;

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

        Button button = new Button(this);
        button.setText("Current Location");
        layout.addView(button);

        button.setOnClickListener(v -> {
            if (hasLocationPermission()) {
                openWebsite();
            } else {
                requestPermissions(
                        new String[]{
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        LOCATION_REQUEST
                );
            }
        });

        setContentView(layout);
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

                boolean permissionGranted =
                        hasLocationPermission();

                callback.invoke(
                        origin,
                        trustedWebsite && permissionGranted,
                        false
                );
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
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode == LOCATION_REQUEST) {
            if (hasLocationPermission()) {
                openWebsite();
            } else {
                Toast.makeText(
                        this,
                        "Please allow location permission in app settings.",
                        Toast.LENGTH_LONG
                ).show();
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
