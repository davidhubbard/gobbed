/* Copyright (C) David Hubbard 2016. Licensed under the GPLv3. */

package com.ndisp.gobbed;

import android.webkit.JavascriptInterface;

public class WebViewInterfaces {
    public static class BluetoothSocket {
        @JavascriptInterface
        public String send(String sockId, String str) {
            return "{\"count\":0, \"lastError\":\"not implemented\"}";
        }
    }

    public static class Bluetooth {
        @JavascriptInterface
        public String getDevices() {
            return "[]";
        }

        @JavascriptInterface
        public String getAdapterState() {
            return "{\"available\":false, \"discovering\":false}";
        }
    }

    public static class Serial {
        @JavascriptInterface
        public String getDevices() {
            return "[]";
        }
    }

    public static class Storage {
        @JavascriptInterface
        public String get(String k) {
            return "{\"value\":{}, \"lastError\":\"not implemented\"}";
        }

        @JavascriptInterface
        public String set(String o) {
            return "{\"lastError\":\"not implemented\"}";
        }
    }
}
