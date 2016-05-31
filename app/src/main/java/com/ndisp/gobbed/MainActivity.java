/* Copyright (C) David Hubbard 2016. Licensed under the GPLv3. */

package com.ndisp.gobbed;

import android.app.FragmentManager;
import android.os.Build;
import android.os.Handler;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    private static final String TAG_WEB_VIEW_FRAGMENT = "MainActivity.TAG_WEB_VIEW_FRAGMENT";

    protected Handler mHandler;
    protected WebViewFragment mWebViewFragment;

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

        if (Build.VERSION.SDK_INT >= 11) {  // API 11: Android 3.0 Honeycomb.
            FragmentManager fm = getFragmentManager();
            mWebViewFragment = (WebViewFragment) fm.findFragmentByTag(TAG_WEB_VIEW_FRAGMENT);

            // A non-null mWebViewFragment means it is being retained across a configuration change.
            if (mWebViewFragment == null) {
                mWebViewFragment = new WebViewFragment();
                fm.beginTransaction()
                        .add(mWebViewFragment, TAG_WEB_VIEW_FRAGMENT)
                        .replace(R.id.web_view_fragment, mWebViewFragment)
                        .commit();
            }
        } else {
            // TODO(David, bug #8): pre-Honeycomb devices will need to add WebView directly to
            // MainActivity.
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Bug #8!");
            builder.setMessage(
                    "Your device is running Gingerbread. That is ok, but this code needs to be" +
                    "fixed to hook up the WebView correctly. Please go to: " +
                    "https://github.com/davidhubbard/gobbed/issues/8 and add your thumbs-up under" +
                    "the issue title.");
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mWebViewFragment.removeLinkToWebView();
    }
}
