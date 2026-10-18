
package com.example.myfirstapp;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    private WebView webView;
    private LinearLayout homeLayout;

    private static final String WEBSITE_URL =
            "https://beautiful-liger-ce28f9.netlify.app/";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        showHomeScreen();
    }

    private void showHomeScreen() {
        homeLayout = new LinearLayout(this);
        homeLayout.setOrientation(LinearLayout.VERTICAL);
        homeLayout.setGravity(Gravity.CENTER);
        homeLayout.setPadding(24, 24, 24, 24);
        homeLayout.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Welcome to Travelkit");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams titleParams =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
        titleParams.bottomMargin = 32;
        homeLayout.addView(title, titleParams);

        Button locationButton = new Button(this);
        locationButton.setText("Current Location");

        homeLayout.addView(locationButton);

        locationButton.setOnClickListener(v -> openWebsite());

        setContentView(homeLayout);
    }

    private void openWebsite() {
        webView = new WebView(this);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        // Keep website navigation inside Travelkit.
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        setContentView(webView);
        webView.loadUrl(WEBSITE_URL);
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

