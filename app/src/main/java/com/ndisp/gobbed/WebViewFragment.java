/* Copyright (C) David Hubbard 2016. Licensed under the GPLv3. */

package com.ndisp.gobbed;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

@TargetApi(11)
public class WebViewFragment extends Fragment {
    private static final String TAG = "WebViewFragment";
    public static final int GOBBED_REQUEST_DISCOVERABLE = 65004;
    private static final int PERMISSION_REQUEST_LOCATION = 1;
    private static final String PERMISSION_REQUEST_LOCATION_KEY = "PERMISSION_REQUEST_LOCATION";

    protected Activity mMainActivity;
    protected GobbedWebView mWeb;
    protected LinearLayout mWebParentLayout;

    protected boolean permissionRequestLocationDialogIsOpen = false;

    protected String getRawResourceAsString(int resourceId) {
        // openRawResourceFd() only works if the resource file is stored in the apk uncompressed.
        // The safest and most portable way is to use openRawResource() and stream the resource into
        // a string. Java does not make it easy, but see http://web.archive.org/web/20140531042945/
        // https://weblogs.java.net/blog/pat/archive/2004/10/stupid_scanner_1.html
        java.util.Scanner s =
                new java.util.Scanner(getResources().openRawResource(resourceId), "UTF-8")
                        .useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    // Android will pass a reference to the MainActivity after each configuration change.
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mMainActivity = activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_web_view, container, false);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        lp.setMargins(0,0,0,0);
        mWeb.setLayoutParams(lp);
        mWeb.setBackgroundResource(android.R.color.transparent);
        mWebParentLayout = (LinearLayout) view.findViewById(R.id.web_view_parent);
        mWebParentLayout.addView(mWeb);
        return view;
    }

    // Called by MainActivity() just before a configuration change destroys mWebParentLayout.
    public void removeLinkToWebView() {
        mWebParentLayout.removeView(mWeb);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retain this fragment even when MainActivity is torn down and recreated.
        setRetainInstance(true);

        mWeb = new GobbedWebView(mMainActivity.getApplicationContext());
        mWeb.initGobbedWebView(this, getRawResourceAsString(R.raw.index));
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

    public void requestLocationPermission() {
        if (permissionRequestLocationDialogIsOpen) {
            // Don't ask again. The dialog is still open.
            return;
        }

        if (Build.VERSION.SDK_INT < 23) {
            permissionRequestLocationDialogIsOpen = true;
        } else {
            if (mMainActivity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED) {
                return;
            }
            permissionRequestLocationDialogIsOpen = true;
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_LOCATION: {
                permissionRequestLocationDialogIsOpen = false;

                String result;
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    Log.d(TAG, "Coarse and fine location permissions granted");
                    result = "Y";
                } else {
                    Log.d(TAG, "Coarse and fine location permissions DENIED");
                    result = "N";
                }
                mWeb.runJsIgnoreReturn("systemLocationPermissionResult('" + result + "')");
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case GOBBED_REQUEST_DISCOVERABLE:
                mWeb.onBluetoothDiscoveryResult(resultCode != Activity.RESULT_CANCELED);
                break;
        }
    }

    // Glue logic because WebViewFragment is not an Activity.
    public SharedPreferences getDefaultSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(mMainActivity.getApplicationContext());
    }

    // Glue logic because WebViewFragment is not an Activity.
    public void registerReceiver(BroadcastReceiver br, IntentFilter filter) {
        mMainActivity.registerReceiver(br, filter);
    }

    // Glue logic because WebViewFragment is not an Activity.
    public void unregisterReceiver(BroadcastReceiver br) {
        mMainActivity.unregisterReceiver(br);
    }

    // Glue logic because WebViewFragment is not an Activity.
    public void sendBroadcast(Intent intent) {
        mMainActivity.sendBroadcast(intent);
    }
}
