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
  var j = webview_bluetoothsocket.send(sockId, str);
  console.log('webview_bluetoothsocket.send():');
  console.log(j);
  var r = JSON.parse(j);
  window.wrapWebviewRuntime.lastError = r.lastError;
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
  var r = webview_bluetooth.getDevices();
  console.log('webview_bluetooth.getDevices():');
  console.log(r);
  setTimeout(cb.bind(window, JSON.parse(r)), 0);
}

WrapWebviewBluetooth.prototype.getAdapterState = function(cb) {
  var r = webview_bluetooth.getAdapterState();
  console.log('webview_bluetooth.getAdapterState():');
  console.log(r);
  setTimeout(cb.bind(window, JSON.parse(r)), 0);
}

function WrapWebviewSerial() {}

WrapWebviewSerial.prototype.getDevices = function(cb) {
  var r = webview_serial.getDevices();
  setTimeout(cb.bind(window, JSON.parse(r)), 0);
}

function WrapWebviewStorage() {
  this.local = {
    'get': function(k, cb) {
      var j = webview_storage.get(k);
      console.log('webview_storage.get():');
      console.log(j);
      var r = JSON.parse(j);
      window.wrapWebviewRuntime.lastError = r.lastError;
      setTimeout(cb.bind(window, r.value), 0);
    },
    'set': function(o, cb) {
      var j = webview_storage.set(JSON.stringify(o));
      console.log('webview_storage.set():');
      console.log(j);
      var r = JSON.parse(j);
      window.wrapWebviewRuntime.lastError = r.lastError;
      setTimeout(cb.bind(window), 0);
    },
  }
}

function detectChromeApi() {
  var api = {};
  try {
    api = chrome;
  } catch (e) {
    if (typeof(webview_serial) != 'undefined' && typeof(webview_bluetooth) != 'undefined') {
      window.wrapWebviewRuntime = {}
      return {
        'bluetooth': new WrapWebviewBluetooth(),
        'bluetoothSocket': new WrapWebviewBluetoothSocket(),
        'runtime': window.wrapWebviewRuntime,
        'serial': new WrapWebviewSerial(),
        'storage': new WrapWebviewStorage(),
      }
    }
    // Reset api
    return {};
  }
  return api;
}
