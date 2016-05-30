/* Copyright (C) David Hubbard 2016. Licensed under the GPLv3. */

package com.ndisp.gobbed;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Iterator;

public class GobbedWebView extends WebView {
    public Activity parentActivity;
    private GobbedBluetooth gobbedBluetooth;

    GobbedWebView(Context c) { super(c); }
    GobbedWebView(Context c, AttributeSet a) { super(c, a); }
    GobbedWebView(Context c, AttributeSet a, int defStyleAttr) { super(c, a, defStyleAttr); }

    /*
    API level 11-17, but now deprecated and will be removed soon:
    GobbedWebView(Context context, AttributeSet attrs, int defStyleAttr, boolean privateBrowsing) {
        super(context, attrs, defStyleAttr, privateBrowsing);
    }
    */

    @TargetApi(21)
    GobbedWebView(Context c, AttributeSet a, int defStyleAttr, int defStyleRes) {
        super(c, a, defStyleAttr, defStyleRes);
    }

    @SuppressLint("SetJavascriptEnabled")
    private void initGobbedWebViewEnableJS() {
        getSettings().setJavaScriptEnabled(true);
    }

    @SuppressLint("JavascriptInterface")  // TODO: suppressing this doesn't appear to do anything.
    public void initGobbedWebView(Activity parentActivity, String html) {
        this.parentActivity = parentActivity;

        initGobbedWebViewEnableJS();

        // Allow chrome://inspect to connect to all WebView instances in this app.
        if (Build.VERSION.SDK_INT >= 19) WebView.setWebContentsDebuggingEnabled(true);

        // Note: exposing Java objects below JELLY_BEAN_MR1 may expose any public fields to
        // untrusted javascript code via reflection. But this app does not load external js.
        // To lock it down, add: if (Build.VERSION.SDK_INT >= 17)
        gobbedBluetooth = new GobbedBluetooth(this);
        addJavascriptInterface(gobbedBluetooth, "webview_bluetooth");
        addJavascriptInterface(new BluetoothSocket(), "webview_bluetoothsocket");
        addJavascriptInterface(new Serial(), "webview_serial");
        addJavascriptInterface(new Storage(
                PreferenceManager.getDefaultSharedPreferences(parentActivity.getApplicationContext())),
                "webview_storage");
        loadData("", "text/html", null);  // Dummy load: see addJavascriptInterface() docs.
        loadDataWithBaseURL("file:///android_res/raw/", html, "text/html", "UTF-8", "");
    }

    public void onBluetoothDiscoveryResult(boolean discovery_granted) {
        if (gobbedBluetooth != null) gobbedBluetooth.onBluetoothDiscoveryResult(discovery_granted);
    }

    public void onPause() {
        if (gobbedBluetooth != null) gobbedBluetooth.onPause();
    }

    public void onResume() {
        if (gobbedBluetooth != null) gobbedBluetooth.onResume();
    }

    public void runJsIgnoreReturn(String js) {
        if (Build.VERSION.SDK_INT >= 19) {
            evaluateJavascript(js, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    // Do nothing.
                }
            });
            return;
        }
        // This is the "old way" of triggering js - it cannot directly return a value.
        loadUrl("javascript:" + js);
    }

    public void runJsCallbacks(String className, String cbName, @Nullable Integer devIndex) {
        className = "window.chromeApiWrapped." + className;
        if (devIndex == null) {
            runJsIgnoreReturn(className + ".fireCbs(\"" + cbName + "\");");
        } else {
            runJsIgnoreReturn(className + ".fireCbs(\"" + cbName + "\"," + devIndex + ");");
        }
    }

    public static class BluetoothSocket {
        @JavascriptInterface
        public String send(String sockId, String str) {
            return "{\"count\":0, \"lastError\":\"not implemented\"}";
        }
    }

    private static class Serial {
        @JavascriptInterface
        public String getDevices() {
            JSONArray devices = new JSONArray();
            // TODO(david, bug #5): USB serial devices for example require rewriting the entire
            // device driver: https://github.com/felHR85/UsbSerial
            return devices.toString();
        }
    }

    private static class Storage {
        public static final String TAG = "webview_storage";
        private SharedPreferences prefs;

        public Storage(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @JavascriptInterface
        public String get(String ostr) {
            JSONObject json = null;
            try {
                json = new JSONObject(ostr);
            } catch (JSONException e) {
                // Should only happen if the wrapper class prepared ostr incorrectly.
                Log.e(TAG, "should not happen: " + e.getMessage(), e);
                // Skip a JSONObject for producing the return value because of JSONException!
                return "{\"lastError\":\"should not happen: " + e.getMessage() + "\"}";
            }
            JSONObject value = new JSONObject();
            Iterator<String> keyIter = json.keys();
            while (keyIter.hasNext()) {
                String key = keyIter.next();
                String defValue = null;
                try {
                    defValue = json.getString(key);
                } catch (JSONException e) {
                    // Skip JSONObject because it throws JSONException so much!
                    return "{\"lastError\":\"failed to get default passed for \"" + key + "\": " +
                            e.getMessage() + "\"}";
                }
                try {
                    value.put(key, prefs.getString(key, defValue));
                } catch (ClassCastException e) {
                    return "{\"lastError\":\"failed to get \"" + key + "\": " + e.getMessage() +
                            "\"}";
                } catch (JSONException e) {
                    // Must never happen. put(String, String) may only throw if:
                    // 1. key is null - the above code will never have a null key.
                    // 2. testValidity() throws for a String object.
                    throw new RuntimeException("JSONException for put(non-null String, String)", e);
                }
            }
            JSONObject response = new JSONObject();
            try {
                response.put("value", value);
            } catch (JSONException e) {
                // Must never happen. put(String, String) may only throw if:
                // 1. key is null - the above code will never have a null key.
                // 2. testValidity() throws for a JSONObject.
                throw new RuntimeException("JSONException for put(non-null String, JSONObject)", e);
            }
            return response.toString();
        }

        @JavascriptInterface
        public String set(String ostr) {
            JSONObject json = null;
            try {
                json = new JSONObject(ostr);
            } catch (JSONException e) {
                // Should only happen if the wrapper class prepared ostr incorrectly.
                Log.e(TAG, "should not happen: " + e.getMessage(), e);
                // Skip a JSONObject for producing the return value because of JSONException!
                return "{\"lastError\":\"should not happen: " + e.getMessage() + "\"}";
            }
            SharedPreferences.Editor editor = prefs.edit();
            Iterator<String> keyIter = json.keys();
            while (keyIter.hasNext()) {
                String key = keyIter.next();
                String value = null;
                try {
                    value = json.getString(key);
                } catch (JSONException e) {
                    // Skip JSONObject because it throws JSONException so much!
                    return "{\"lastError\":\"failed to get string for \"" + key + "\": " +
                            e.getMessage() + "\"}";
                }
                editor.putString(key, value);
            }
            editor.apply();
            // yeah, no JSONObject needed!
            return "{}";
        }
    }
}
