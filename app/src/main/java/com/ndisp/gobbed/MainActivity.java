/* Copyright (C) David Hubbard 2016. Licensed under the GPLv3. */

package com.ndisp.gobbed;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;

public class MainActivity extends AppCompatActivity {
    public static final String TAG = "MainActivity";
    public static final int GOBBED_REQUEST_DISCOVERABLE = 65004;
    private static final int PERMISSION_REQUEST_LOCATION = 1;
    private static final String PERMISSION_REQUEST_LOCATION_KEY = "PERMISSION_REQUEST_LOCATION";

    private boolean alreadyAskedForPermission = false;

    protected GobbedWebView mWeb;
    protected Handler mHandler;

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

        if (savedInstanceState != null) {
            alreadyAskedForPermission =
                    savedInstanceState.getBoolean(PERMISSION_REQUEST_LOCATION_KEY, false);
        }
        checkPermissions();

        mWeb = (GobbedWebView) findViewById(R.id.web_view);
        mWeb.initGobbedWebView(this, getRawResourceAsString(R.raw.index));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(PERMISSION_REQUEST_LOCATION_KEY, alreadyAskedForPermission);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        // Do nothing.
    }

    @Override
    public void onPause() {
        super.onPause();
        mWeb.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        mWeb.onResume();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case GOBBED_REQUEST_DISCOVERABLE:
                mWeb.onBluetoothDiscoveryResult(resultCode != RESULT_CANCELED);
                break;
        }
    }

    private boolean checkPermissions() {
        if (alreadyAskedForPermission) {
            // don't check again because the dialog is still open
            return false;
        }

        if (Build.VERSION.SDK_INT < 23) {
            alreadyAskedForPermission = true;
            return true;
        }

        // Android M (API 23) new permissions. The redundant if condition silences Android Studio.
        if (Build.VERSION.SDK_INT >= 23 &&
                this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {
            return true;
        }

        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("This app needs location access");
        builder.setMessage(
                "Please grant location access so this app can detect bluetooth devices.");
        builder.setPositiveButton(android.R.string.ok, null);
        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @TargetApi(23)
            @Override
            public void onDismiss(DialogInterface dialog) {
                // the dialog will be opened so we have to save that
                alreadyAskedForPermission = true;
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, PERMISSION_REQUEST_LOCATION);
            }
        });
        builder.show();
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_LOCATION: {
                // the request returned a result so the dialog is closed
                alreadyAskedForPermission = false;

                if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Coarse and fine location permissions granted");
                    // Now run the code that needs the permissions.
                } else {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("Bluetooth scan permission denied");
                        builder.setMessage("The location access permission is needed to " +
                                "scan for bluetooth devices. (I guess it could give away " +
                                "your location?)");
                        builder.setPositiveButton(android.R.string.ok, null);
                        builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            @TargetApi(23)
                            public void onDismiss(DialogInterface dialog) {
                            }
                        });
                        builder.show();
                    }
                }
            }
        }
    }
}
