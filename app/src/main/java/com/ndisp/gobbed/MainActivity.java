/* Copyright (C) David Hubbard 2016. Licensed under the GPLv3. */

package com.ndisp.gobbed;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    protected WebView mWeb;
    protected Handler mHandler;

    @SuppressLint("SetJavascriptEnabled")
    protected void initWebView(String html) {
        mWeb.getSettings().setJavaScriptEnabled(true);
        // Allow chrome://inspect to connect to all WebView instances in this app.
        if (Build.VERSION.SDK_INT >= 19) WebView.setWebContentsDebuggingEnabled(true);
        mWeb.loadData(html, "text/html", "UTF-8");
    }

    protected void getBackInImmersive() {
        if (Build.VERSION.SDK_INT < 14) return;
        // API level 14 (Build.VERSION.SDK_INT is not the API level of the app, but of the OS)
        // See http://developer.android.com/training/system-ui/immersive.html.

        View dv = getWindow().getDecorView();
        dv.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
            @Override
            public void onSystemUiVisibilityChange(int visibility) {
                if (visibility != 0) return;
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        MainActivity.this.getBackInImmersive();
                    }
                }, 2000 /*milliseconds*/);
            }
        });
        int uiFlags = View.SYSTEM_UI_FLAG_LOW_PROFILE  // Dim the status bar and nav bar
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION  // Hide nav bar.
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION  // Resize the view for no nav bar.
                | View.SYSTEM_UI_FLAG_FULLSCREEN  // Hide status bar (top).
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;  // Resize the view for no status bar.
        if (Build.VERSION.SDK_INT >= 16) uiFlags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Build.VERSION.SDK_INT >= 19) uiFlags |= View.SYSTEM_UI_FLAG_IMMERSIVE;
        dv.setSystemUiVisibility(uiFlags);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        mHandler = new Handler();
        getBackInImmersive();
        setContentView(R.layout.activity_main);

        mWeb = (WebView) findViewById(R.id.web_view);
        initWebView("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head>"
                + "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=iso-8859-1\">"
                + "<title>Lorem Ipsum</title>"
                + "</head><body style=\"width:300px; color: #00000; \">"
                + "<p><strong> About us</strong> </p><p><strong> Lorem Ipsum</strong> is simply dummy text .</p><p><strong> Lorem Ipsum</strong> is simply dummy text </p><p><strong> Lorem Ipsum</strong> is simply dummy text </p></body></html>"
        );
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // Do nothing.
    }
}
