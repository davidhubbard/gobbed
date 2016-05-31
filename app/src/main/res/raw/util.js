/* Copyright (C) David Hubbard 2016. Licensed under the GPLv3. */

String.prototype.abbreviate = function(targetLength) {
  if (this.length < targetLength) return this;
  if (this.length < targetLength + 3) return this.substr(0, targetLength);
  targetLength -= 3;
  var t = this.substr(0, targetLength / 2).trimRight();
  return t + '...' + this.substr(this.length + t.length - targetLength);
}

function getHTMLofClass(c) {
  var elist = document.getElementsByClassName(c);
  var h = '';
  for (var i = 0; i < elist.length; i++) {
    h += elist[i].innerHTML;
  }
  return h;
}

function setHTMLofClass(c, h) {
  var elist = document.getElementsByClassName(c);
  if (elist.length == 0) throw 'setHTMLofClass(' + c + '): no elements found';
  for (var i = 0; i < elist.length; i++) {
    elist[i].innerHTML = h;
  }
}

function setHandlerForClass(c, evname, cb) {
  var elist = document.getElementsByClassName(c);
  if (elist.length == 0) throw 'setHandlerForClass(' + c + ',' + evname + '): no elements found';
  for (var i = 0; i < elist.length; i++) {
    elist[i].addEventListener(evname, function(e_target) {
      return function(e) {
        cb({
          'keyCode': e.keyCode,
          'target': e_target,
        });
      }
    }(elist[i]));
  }
}

function focusAndSelectEnd(el) {
  el.focus();

  var range = document.createRange();
  range.selectNodeContents(el);
  range.setStart(range.endContainer, range.endOffset);

  var sel = window.getSelection();
  sel.removeAllRanges();
  sel.addRange(range);
}

function appendAfterCleanup(el, str) {
  var limitLen = function(x) {
    if (x.length < 1024) return x;
    return x.substr(x.length - 1024);
  }

  c = el.innerHTML;
  if (c == '') {
    c = '\n';
  } else {
    c = c.replace('<div>', '')
        .replace('</div>', '')
        .replace('<br>', '\n');
  }
  if (el.getClientRects().length) {
    // Tab is selected, el is visible.
    el.innerHTML = limitLen(c);
    focusAndSelectEnd(el);
    document.execCommand('insertHTML', false, str);
    focusAndSelectEnd(el);
  } else {
    if (c.startsWith('\n')) c = c.substr(1);
    c += str;
    if (c.endsWith('\n')) c = c.substr(0, c.length - 1) + '<div><br></div>';
    el.innerHTML = limitLen(c);
  }
  return c;
}

function WrapWebviewBluetoothSocket() {
  this.receiveCb = [];
  var self = this;
  this.onReceive = {
    'addListener': function(cb) { self.receiveCb.push(cb); }
  }
}

WrapWebviewBluetoothSocket.prototype.send = function(sockId, ab, cb) {
  var str = String.fromCharCode.apply(null, new Uint8Array(ab.data));
  var j = webview_bluetoothsocket.send(sockId, str);
  console.log('webview_bluetoothsocket.send():');
  console.log(j);
  var r = JSON.parse(j);
  window.chromeApiWrapped.runtime.lastError = r.lastError;
  setTimeout(cb.bind(window, r.count), 0);
}

function WrapWebviewBluetooth() {
  this.changeCb = [];
  this.addCb = [];
  this.devCb = [];
  this.removeCb = [];
  var self = this;
  this.onAdapterStateChanged = {
    'addListener': function(cb) { self.changeCb.push(cb); }
  }
  this.onDeviceAdded = {
    'addListener': function(cb) { self.addCb.push(cb); }
  }
  this.onDeviceChanged = {
    'addListener': function(cb) { self.devCb.push(cb); }
  }
  this.onDeviceRemoved = {
    'addListener': function(cb) { self.removeCb.push(cb); }
  }
}

WrapWebviewBluetooth.prototype.getDevices = function(cb) {
  setTimeout(cb.bind(window, JSON.parse(webview_bluetooth.getDevices())), 0);
}

WrapWebviewBluetooth.prototype.getAdapterState = function(cb) {
  setTimeout(cb.bind(window, JSON.parse(webview_bluetooth.getAdapterState())), 0);
}

WrapWebviewBluetooth.prototype.startDiscovery = function(cb) {
  var r = JSON.parse(webview_bluetooth.startDiscovery());
  window.chromeApiWrapped.runtime.lastError = r.lastError;
  setTimeout(cb.bind(window), 0);
}

WrapWebviewBluetooth.prototype.stopDiscovery = function(cb) {
  var r = JSON.parse(webview_bluetooth.stopDiscovery());
  window.chromeApiWrapped.runtime.lastError = r.lastError;
  setTimeout(cb.bind(window), 0);
}

WrapWebviewBluetooth.prototype.fireCbs = function(cbName, devIndex) {
  if (typeof(this[cbName]) == 'undefined') {
    console.log("fireCbs(" + cbName + "): not found");
    return;
  }
  cbArgs = []
  if (typeof(devIndex) != 'undefined') {
    devs = JSON.parse(webview_bluetooth.getDevices());
    if (devIndex >= 0 && devs.length > devIndex) {
      cbArgs.push(devs[devIndex]);
    } else {
      console.log("fireCbs(" + cbName + ", " + devIndex + "): bad devIndex");
      return;
    }
  }
  for (var i = 0; i < this[cbName].length; i++) {
    this[cbName][i].apply(window, cbArgs);
  }
}

function WrapWebviewSerial() {}

WrapWebviewSerial.prototype.getDevices = function(cb) {
  var r = webview_serial.getDevices();
  setTimeout(cb.bind(window, JSON.parse(r)), 0);
}

function WrapWebviewStorage() {
  this.local = {
    'get': function(o, cb) {
      var r = JSON.parse(webview_storage.get(JSON.stringify(o)));
      window.chromeApiWrapped.runtime.lastError = r.lastError;
      setTimeout(cb.bind(window, r.value), 0);
    },
    'set': function(o, cb) {
      var r = JSON.parse(webview_storage.set(JSON.stringify(o)));
      window.chromeApiWrapped.runtime.lastError = r.lastError;
      setTimeout(cb.bind(window), 0);
    },
  }
}

function detectChromeApi() {
  var api = {};
  try {
    api = chrome;
    return api;
  } catch (e) {
    // Fallthrough.
  }
  // Exception: chrome is undefined.
  if (typeof(window.chromeApiWrapped) != 'undefined') {
    // detectChromeApi() was previous called, and the result is already cached.
    return window.chromeApiWrapped;
  }
  // Is this an Android Webview?
  if (typeof(webview_serial) != 'undefined' && typeof(webview_bluetooth) != 'undefined') {
    window.chromeApiWrapped = {
      // Android exposes additional APIs that Chrome does not.
      'system': webview_system,
      'bluetooth': new WrapWebviewBluetooth(),
      'bluetoothSocket': new WrapWebviewBluetoothSocket(),
      'runtime': {},  // Modified by some of the wrapper classes above.
      'serial': new WrapWebviewSerial(),
      'storage': new WrapWebviewStorage(),
    }
    return window.chromeApiWrapped;
  }
  // No api detected.
  throw "No hardware api detected."
}
