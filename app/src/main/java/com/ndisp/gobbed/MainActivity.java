/* Copyright (C) David Hubbard 2016. Licensed under the GPLv3. */

package com.ndisp.gobbed;

import android.annotation.SuppressLint;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";

    protected WebView mWeb;
    protected Handler mHandler;

    @SuppressLint("SetJavascriptEnabled")
    protected void initWebViewEnableJS() {
        mWeb.getSettings().setJavaScriptEnabled(true);
    }

    @SuppressLint("JavascriptInterface")  // TODO: doesn't appear to do anything.
    protected void initWebView(String html) {
        initWebViewEnableJS();
        // Allow chrome://inspect to connect to all WebView instances in this app.
        if (Build.VERSION.SDK_INT >= 19) WebView.setWebContentsDebuggingEnabled(true);
        // Note: exposing Java objects below JELLY_BEAN_MR1 may expose any public fields to
        // untrusted javascript code via reflection. But this app does not load external js.
        // To lock it down, add: if (Build.VERSION.SDK_INT >= 17)
        mWeb.addJavascriptInterface(new WebViewInterfaces.Bluetooth(), "webview_bluetooth");
        mWeb.addJavascriptInterface(new WebViewInterfaces.BluetoothSocket(),
                "webview_bluetoothsocket");
        mWeb.addJavascriptInterface(new WebViewInterfaces.Serial(), "webview_serial");
        mWeb.addJavascriptInterface(new WebViewInterfaces.Storage(), "webview_storage");
        mWeb.loadData("", "text/html", null);  // Dummy load: see addJavascriptInterface() docs.
        mWeb.loadDataWithBaseURL("file:///android_res/raw/", html, "text/html", "UTF-8", "");
    }

    protected String getRawResourceAsString(int resourceId) {
        // openRawResourceFd() only works if the resource file is stored in the apk uncompressed.
        // The safest and most portable way is to stream the resource into a string.
        // And java does not make that easy, but see http://web.archive.org/web/20140531042945/
        // https://weblogs.java.net/blog/pat/archive/2004/10/stupid_scanner_1.html
        java.util.Scanner s =
                new java.util.Scanner(getResources().openRawResource(resourceId), "UTF-8")
                .useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
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
        initWebView(getRawResourceAsString(R.raw.index));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // Do nothing.
    }
}
