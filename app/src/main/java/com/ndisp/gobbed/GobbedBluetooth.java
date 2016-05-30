/* Copyright (C) David Hubbard 2016. Licensed under the GPLv3. */

package com.ndisp.gobbed;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class GobbedBluetooth {
    public static final String TAG = "GobbedBluetooth";
    private static final String ACTION_START_DISCOVERY = "com.ndisp.gobbed.action.START_DISCOVERY";
    private final BluetoothAdapter mAdapter;
    private final GobbedWebView gobbedWebView;
    private HashMap<String, BluetoothDevice> mDeviceList;
    private boolean fakeDiscoveringOneShot;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        private int findDevIndex(BluetoothDevice dev) {
            int i = 0;
            for (String addr : mDeviceList.keySet()) {
                if (addr.equals(dev.getAddress())) {
                    return i;
                }
                i++;
            }
            return -1;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (dev == null) {
                    Log.w(TAG, "EXTRA_DEVICE returned null BluetoothDevice");
                    return;
                }
                boolean isNewDev = mDeviceList.put(dev.getAddress(), dev) == null;
                gobbedWebView.runJsCallbacks("bluetooth", isNewDev ? "addCb" : "devCb",
                        findDevIndex(dev));
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                gobbedWebView.runJsCallbacks("bluetooth", "changeCb", null);
            } else if (ACTION_START_DISCOVERY.equals(action)) {
                startDiscoveryOnMainUIThread();
            } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                onActionStateChanged(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1));
            }
        }
    };

    // Called either by BroadcastReceiver or onBluetoothDiscoveryResult() because a broadcast is
    // not sent when discovering starts. See fakeDiscoveringOneShot.
    private void onActionStateChanged(int state) {
        boolean added = false;
        if (state != -1 && state == BluetoothAdapter.STATE_ON) {
            for (BluetoothDevice dev : mAdapter.getBondedDevices()) {
                added |= mDeviceList.put(dev.getAddress(), dev) == null;
            }
        }
        gobbedWebView.runJsCallbacks("bluetooth", "changeCb", null);
        if (added) {
            gobbedWebView.runJsIgnoreReturn("console.log(\"ACTION_STATE_CHANGED + added\");");
        }
    }

    GobbedBluetooth(GobbedWebView gobbedWebView) {
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        this.gobbedWebView = gobbedWebView;

        mDeviceList = new HashMap<String, BluetoothDevice>();
        for (BluetoothDevice dev : mAdapter.getBondedDevices()) {
            mDeviceList.put(dev.getAddress(), dev);
        }
    }

    protected void startDiscoveryOnMainUIThread() {
        // startDiscoveryOnMainUIThread() will ACTION_REQUEST_DISCOVERABLE, in addition to starting
        // discovery. The Chrome Bluetooth API does not expose the discoverable bit.
        // TODO(david, bug #2): API on Android to control the discoverable bit directly from js.
        if (mAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            startDiscoveryDiscoverable();
        } else {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            gobbedWebView.parentActivity.startActivityForResult(discoverableIntent,
                    MainActivity.GOBBED_REQUEST_DISCOVERABLE);
            // onBluetoothDiscoveryResult() will call startDiscoveryAndDeviceIsDiscoverable().
        }
    }

    protected void startDiscoveryDiscoverable() {
        mAdapter.startDiscovery();

        // At this point, discovering has NOT started but there will be no callback when it
        // does. So fake it for one shot. (This depends on the client code only asking for
        // the adapter state once after being notified of a change. FRAGILE!)
        fakeDiscoveringOneShot = true;
        // Notify the client by pretending BroadcastReceiver() got ACTION_STATE_CHANGED.
        onActionStateChanged(mAdapter.getState());
    }

    public void onBluetoothDiscoveryResult(boolean discovery_granted) {
        if (discovery_granted) {
            startDiscoveryDiscoverable();
        } else {
            Log.d(TAG, "onBluetoothDiscoveryResult(denied)");
            // Discovery does not require being discoverable.
            startDiscoveryDiscoverable();
        }
    }

    protected void cancelBluetoothDiscovery() {
        if (mAdapter.isDiscovering()) {
            mAdapter.cancelDiscovery();
        }
    }

    public void onPause() {
        cancelBluetoothDiscovery();
        gobbedWebView.parentActivity.unregisterReceiver(mReceiver);
    }

    public void onResume() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        gobbedWebView.parentActivity.registerReceiver(mReceiver, filter);

        // TODO(david, bug #2): receive ACTION_DEVICE_DISAPPEARED ... what broadcast is it?

        filter = new IntentFilter(ACTION_START_DISCOVERY);
        gobbedWebView.parentActivity.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        gobbedWebView.parentActivity.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        gobbedWebView.parentActivity.registerReceiver(mReceiver, filter);
    }

    @JavascriptInterface
    public String getDevices() {
        boolean added = false;
        for (BluetoothDevice dev : mAdapter.getBondedDevices()) {
            added |= mDeviceList.put(dev.getAddress(), dev) == null;
        }
        JSONArray devices = new JSONArray();
        for (BluetoothDevice dev: mDeviceList.values()) {
            JSONObject j = new JSONObject();
            try {
                String s = dev.getName();
                if (s != null) j.put("name", s);
                s = dev.getAddress();
                if (s != null) j.put("address", s);
                JSONArray uuids = new JSONArray();
                // If you have never attempted to pair or connect with dev before, getUuids() will
                // return null (no uuids in cache).
                if (Build.VERSION.SDK_INT >= 15 && dev.getUuids() != null) { // API 15: Icecream MR1
                    for (ParcelUuid uuid : dev.getUuids()) {
                        uuids.put(uuid.toString());
                    }
                }
                j.put("uuids", uuids);
            } catch (JSONException e) {
                // Must never happen. put(String, bool) may only throw if:
                // 1. key is null - the above code will never have a null key.
                // 2. testValidity() throws for a String.
                throw new RuntimeException("JSONException for put(non-null String, String)", e);
            }
            devices.put(j);
        }
        return devices.toString();
    }

    /**
     * If there is a need to call fetchUuidsWithSdp() prior to API 15, it can be called via
     * reflection:
     *
     * http://wiresareobsolete.com/2010/11/android-bluetooth-rfcomm/
     *
     * Class clazz = null;
     * try {
     *   clazz = Class.forName("android.bluetooth.BluetoothDevice");
     * } catch(ClassNotFoundException e) {
     *   Log.e(TAG, "android.bluetooth.BluetoothDevice:", e);
     *   return;
     * }
     * Class[] param = {};
     * Method method = null;
     * try {
     *   method = cl.getMethod("fetchUuidsWithSdp", param);
     * } catch(NoSuchMethodException e) {
     *   Log.e(TAG, "fetchUuidsWithSdp:", e);
     *   return;
     * }
     * Object[] args = {};
     * try {
     *   method.invoke(device, args);
     * } catch (Exception e) {
     *   Log.e(CTAG, "invoke(fetchUuidsWithSdp): ", e);
     * }
     */

    @JavascriptInterface
    public String getAdapterState() {
        JSONObject adapter = new JSONObject();
        try {
            adapter.put("available", mAdapter.getState() == BluetoothAdapter.STATE_ON);
            adapter.put("discovering", mAdapter.isDiscovering() || fakeDiscoveringOneShot);
            if (fakeDiscoveringOneShot) {
                fakeDiscoveringOneShot = false;
            }
        } catch (JSONException e) {
            // Must never happen. put(String, bool) may only throw if:
            // 1. key is null - the above code will never have a null key.
            // 2. testValidity() throws for a Boolean object.
            throw new RuntimeException("JSONException for put(non-null String, bool)", e);
        }
        return adapter.toString();
    }

    @JavascriptInterface
    public String startDiscovery() {
        cancelBluetoothDiscovery();

        // Send a broadcast because the call to runJsCallbacks() must happen on the UI thread.
        Intent intent = new Intent();
        intent.setAction(ACTION_START_DISCOVERY);
        gobbedWebView.parentActivity.sendBroadcast(intent);
        // No "lastError" means success.
        return "{}";
    }

    @JavascriptInterface
    public String stopDiscovery() {
        cancelBluetoothDiscovery();
        // No "lastError" means success.
        return "{}";
    }
}
