/* Copyright (C) David Hubbard 2016. Licensed under the GPLv3. */

function btSend(str) {
  var api = detectChromeApi();
  if (!api || !api.bluetoothSocket) {
    console.log('send failed: API not found');
    return;
  }
  if (!window.sockId) {
    console.log('send failed: not connected');
    return;
  }
  if (!window.sockSendQ) {
    window.sockSendQ = [];
  }
  if (window.sockSendQ.push(str) > 1) {
    return;
  }
  //console.log('btSend(' + escape(str) + ')');
  str = str + '\r';
  var arrayBuf = new ArrayBuffer(str.length);
  var bufView = new Uint8Array(arrayBuf);
  for (var i = 0, strLen = str.length; i < strLen; i++) {
    bufView[i] = str.charCodeAt(i);
  }
  api.bluetoothSocket.send(window.sockId, arrayBuf, function(count) {
    if (typeof(api.runtime.lastError) != 'undefined') {
      console.log('chrome.bluetoothSocket.send returned failure:');
      console.log(api.runtime.lastError);
      return;
    }
    if (count != str.length) {
      console.log('chrome.bluetoothSocket.send only sent ' + count + ' of ' + str.length + ' bytes');
      return;
    }
  })
}

function btPopSendQueue() {
  window.sockSendQ.shift();
  if (window.sockSendQ.length) {
    var saveQ = window.sockSendQ;
    var nextStr = window.sockSendQ.shift();
    window.sockSendQ = undefined;
    this.send(nextStr);
    saveQ.unshift(nextStr);
    window.sockSendQ = saveQ;
  } else {
    this.onIdle();
  }
}

function btReceive(buf) {
  // See http://stackoverflow.com/questions/6965107/converting-between-strings-and-arraybuffers
  // If buf.data is too large, it will fail
  if (buf.data.byteLength > 32767) {
    console.log('btReceive got ' + buf.data.byteLength + ' bytes, which is fine');
    console.log('but it would require a more complex decoder here. dropping it on the floor...');
    return;
  }
  var s = String.fromCharCode.apply(null, new Uint8Array(buf.data));
  //var qstr = (window.sockSendQ.length == 0) ? '' :  (' q=' + JSON.stringify(window.sockSendQ));
  //console.log('btReceive:"' + s.replace('\r','').split('\n').join('\\n') + '"' + qstr);
  window.connDriver.onRecv(s);
}

function btReceiveError(errInfo) {
  console.log('btReceiveError:');
  console.log(errInfo);
  var api = detectChromeApi();
  if (!api || !api.bluetoothSocket) {
    return;
  }
  api.bluetoothSocket.close(socketId);
  window.connDriver.onDisconnected(errInfo);
  delete window.connDriver;
  delete window.sockId;
}

function btConnectTo(api, addr, thenCb) {
  var connectedCb = function(sockId) {
    return function() {
      if (typeof(api.runtime.lastError) != 'undefined') {
        console.log('chrome.bluetoothSocket.connect returned failure:');
        console.log(api.runtime.lastError);
        thenCb(api.runtime.lastError.message);
        return;
      }
      window.sockId = sockId;
      thenCb();
    }
  }
  api.bluetoothSocket.create(function(sock) {
    if (api.runtime.lastError) {
      console.log("chrome.bluetoothSocket.create returned failure:");
      console.log(api.runtime.lastError);
      thenCb(api.runtime.lastError.message);
      return;
    }
    var sockId = sock.socketId;
    api.bluetoothSocket.connect(sockId, addr, "1101", connectedCb(sockId));
    if (api.runtime.lastError) {
      console.log("chrome.bluetoothSocket.connect failed:");
      console.log(api.runtime.lastError);
      thenCb(api.runtime.lastError.message);
      return;
    }
  });
  if (typeof(api.runtime.lastError) != 'undefined') {
    console.log("chrome.bluetoothSocket.create failed:");
    console.log(api.runtime.lastError);
    thenCb(api.runtime.lastError.message);
  }
}

function btStartConnectionResponse(err) {
  window.connectionRequestInProgress = 0;
  if (err) {
    setHTMLofClass('main-connect', addr + ' failed:<br/>\n' + err);
    return;
  }

  window.connDriver = new Elm327();
  window.connDriver.send = btSend;
  window.connDriver.popSendQueue = btPopSendQueue;
  window.connDriver.onConnected();
}

function btStartConnection() {
  var api = detectChromeApi();
  if (!api || !api.bluetooth || !api.bluetoothSocket || !api.storage) {
    console.log('btStartConnection: API not found');
    return;
  }
  if (window.connectionRequestInProgress) {
    // The button is not disabled but should be while a connection is in progress.
    // Ignore this click.
    return;
  }
  if (window.sockId) {
    // The button is reused as a disconnect button.
    api.bluetoothSocket.close(window.sockId, function() {
      if (typeof(api.runtime.lastError) != 'undefined') {
        console.log("chrome.bluetoothSocket.close failed:");
        console.log(api.runtime.lastError);
      }
      showSelectedBtDev();
    });
    window.connDriver.onDisconnected();
    delete window.sockId;
    return;
  }

  api.bluetooth.getAdapterState(function(adapter) {
    if (adapter.available) {
      // Start a new connection...
      window.connectionRequestInProgress = 1;
      var addr = window.selectedBtDev;
      setHTMLofClass('main-connect', 'Connecting ' + addr + '...');
      btConnectTo(api, addr, btStartConnectionResponse);
    } else {
      console.log('ignoring click because BT adapter is off.');
    }
  });
}



function stopScan() {
  var api = detectChromeApi();
  if (api && api.bluetooth) {
    api.bluetooth.getAdapterState(function(adapter) {
      if (!adapter.discovering) return;
      api.bluetooth.stopDiscovery(function() {
        if (typeof(api.runtime.lastError) == 'undefined') return;
        console.log('chrome.bluetooth.stopDiscovery failed:');
        console.log(api.runtime.lastError);
      });
    });
  }
}

function selectMainTab() {
  stopScan();
}

function btUpdateSelectedDev(addr) {
  stopScan();
  window.selectedBtDev = addr;
  var api = detectChromeApi();
  if (!api || !api.storage) {
    console.log('storage failed: API not found');
    return;
  }
  api.storage.local.set({ 'selectedBtDev': addr }, function() {
    if (typeof(api.runtime.lastError) != 'undefined') {
      console.log('chrome.storage.local.set failed:');
      console.log(api.runtime.lastError);
      return;
    }
    btListDevices();
  });
}

function btListDevices() {
  var api = detectChromeApi();
  if (!api || !api.bluetooth || !api.bluetoothSocket || !api.storage) {
    console.log('btListDevices: API not found');
    return;
  }
  api.bluetooth.getAdapterState(function(adapter) {
    var showBtDevs = function (devlist) {
      var results_txt = adapter.discovering ? 'Stop' : 'Start';
      var h = '<div class="device-list-action bluetooth-scan-action">' + results_txt
        + ' scanning...</div>';
      if (!adapter.available) h = '';
      h += '<div class="device-list-class">bluetooth devices:</div>\n';
      var formatBtDev = function(dev) {
        var h = '<div class="device-list-item';
        if (dev.address == window.selectedBtDev) h += '-selected';
        h += ' bt-list-item"><span class="bluetooth-addr">' + dev.address
          + '</span>' + (dev.name || dev.address).abbreviate(16);  // abbreviate() in util.js.
        if (dev.paired) h += ' <span class="bluetooth-paired">(paired)</span>'
        h += '</div>';
        return h;
      }
      if (!adapter.available) {
        h += 'Bluetooth turned off.';
      } else if (devlist.length == 0) {
        h += 'No bluetooth devices found.';
      } else {
        for (var i = 0; i < devlist.length; i++) if (devlist[i].paired) {
          h += formatBtDev(devlist[i]) + '\n';
        }
        for (var i = 0; i < devlist.length; i++) if (!devlist[i].paired) {
          h += formatBtDev(devlist[i]) + '\n';
        }
      }
      setHTMLofClass('device-list-bluetooth', h);
      if (devlist.length == 0) return;
      var btScanClick = function() {
        if (adapter.discovering) {
          stopScan();
        } else {
          api.bluetooth.startDiscovery(function() {
            if (typeof(api.runtime.lastError) == 'undefined') return;
            console.log('chrome.bluetooth.startDiscovery failed:');
            console.log(api.runtime.lastError);
          });
        }
      }
      setHandlerForClass('bluetooth-scan-action', 'click', btScanClick);
      setHandlerForClass('bt-list-item', 'click', function(e) {
        var el = e.target;
        var addrSpanStr = '<span class="bluetooth-addr">';
        var addr = el.innerHTML.indexOf(addrSpanStr);
        if (addr == -1) {
          console.log('connect: unknown addr: "' + el.innerHTML + '"');
          return;
        }
        addr = el.innerHTML.substr(addr + addrSpanStr.length);
        if (addr.indexOf('</span>') == -1) {
          console.log('connect: unknown addr: "' + el.innerHTML + '"');
          return;
        }
        addr = addr.substr(0, addr.indexOf('</span>'));
        btUpdateSelectedDev(addr);
      });
    };
    if (adapter.available) {
      api.bluetooth.getDevices(showBtDevs);
      setHTMLofClass('main-connect', 'Connect to ' + window.selectedBtDev + ' (BT)');
    } else {
      showBtDevs([]);
      setHTMLofClass('main-connect', 'Bluetooth off');
    }
  });
}

function btHandleDevEvent(note, dev) {
  if (note == 'rm' && window.connectionRequestInProgress && window.selectedBtDev == dev.address) {
    // connection failed, and this is the only way to detect it.
    btStartConnectionResponse('removed');
  }
  //console.log('btDev ' + note + ' ' + dev.address + ' ' + dev.uuids.length + ' uuids');
  //console.log(dev);
  listDevices();
}

function initBluetooth() {
  var api = detectChromeApi();
  if (api && api.bluetooth && api.bluetoothSocket && api.storage) {
    window.selectedBtDev = 'error';
    api.bluetooth.onAdapterStateChanged.addListener(btListDevices);
    api.bluetooth.onDeviceAdded.addListener(function(dev) {btHandleDevEvent('add', dev)});
    api.bluetooth.onDeviceChanged.addListener(function(dev) {btHandleDevEvent('change', dev)});
    api.bluetooth.onDeviceRemoved.addListener(function(dev) {btHandleDevEvent('rm', dev)});
    api.bluetoothSocket.onReceive.addListener(btReceive);
    listDevices();  // So btListDevices() can find its div.
    api.storage.local.get({ 'selectedBtDev': 'none set' }, function(result) {
      if (typeof(api.runtime.lastError) != 'undefined') {
        console.log('chrome.storage.local.get failed:');
        console.log(api.runtime.lastError);
        return;
      }
      setHandlerForClass('main-connect', 'click', btStartConnection);
      window.selectedBtDev = result.selectedBtDev;
      btListDevices();
    });
    setHandlerForClass('term-chat', 'keydown', function(e) {
      // trap the return key being pressed
      if (e.keyCode === 13) {
        var lines = appendAfterCleanup(e.target, '\n').split('\n');
        if (lines.length > 0) {
          var line = lines[lines.length - 1];
          if (line != "") {
            // Bug: <pre> is still combining the last line of received data with the typed line.
            // This is a hacky workaround.
            var gtpos = line.indexOf('&gt;');
            if (gtpos != -1) line = line.substr(gtpos + 4);
            btSend(line);
          }
        }
        return false;
      }
      // allow default behavior
      return true;
    });
  }
}

function selectTerm() {
  var elist = document.getElementsByClassName('term-chat');
  if (elist.length != 1) {
    throw 'unable to set contentEditable on .term-chat: found ' + elist.length;
    return;
  }
  var el = elist[0];
  el.setAttribute("contentEditable", true);
  focusAndSelectEnd(el);
}

function listDevices() {
  var h = '';
  var api = detectChromeApi();
  if (api && api.bluetooth && api.bluetoothSocket && api.storage) {
    h += '<div class="device-list-bluetooth"></div>';
    btListDevices();
  }
  if (api && api.serial) {
    h += '<div class="device-list-serial"></div>';
    api.serial.getDevices(function (a) {
      var h = '<div class="device-list-class">serial devices: (not yet working)</div>\n';
      var dev = {};
      if (a.length == 0) {
        h += 'No serial devices found. (see bug #5)';
      } else for (var i = 0; i < a.length; i++) {
        dev = a[i];
        h += '<div class="device-list-item-disabled">' + dev.path;
        if (dev.displayName) h += ' "' + dev.displayName + '"';
        h += '</div>\n'
      }
      setHTMLofClass('device-list-serial', h);
    });
  }
  if (h == '') {
    h = 'No API support found. Please check manifest.json.';
    setHTMLofClass('device-list', h);
  } else if (getHTMLofClass('device-list') == '') {
    // Populate <div class="device-list"> only the first time.
    // BUG: This will break if support for a class of devices changes during runtime.
    setHTMLofClass('device-list', 'Select a device:' + h);
  }
}

var tabChangeHandlers = {
  'main': selectMainTab,
  'term': selectTerm,
  'settings': listDevices,
};

function setTabTo(e) {
  // Find clicked tab
  var elist = document.getElementsByClassName('ttab');
  var newTab = -1;
  for (var i = 0; i < elist.length; i++) {
    if (elist[i] == e.target) newTab = i;
  }
  if (newTab == -1) {
    console.log('BUG: failed to find new tab, resetting to tab 0');
    newTab = 0;
    e.target = elist[newTab];
  }
  window.curTab = newTab;

  // Hide all tabs
  elist = document.getElementsByClassName('btab');
  for (var i = 0; i < elist.length; i++) {
    elist[i].style.display = 'none';
  }

  // Show curTab
  var tabClassName = e.target.className;
  if (tabClassName.indexOf("bg-") != -1) {
    tabClassName = tabClassName.substr(tabClassName.indexOf("bg-")).split(" ")[0].substr(3);
    elist = document.getElementsByClassName('tab-' + tabClassName);
    for (var i = 0; i < elist.length; i++) {
      elist[i].style.display = 'inline-block';
    }
  }
  setTabStyle();
  tabChangeHandlers[tabClassName]();
}

function setTabStyle() {
  var elist = document.getElementsByClassName('ttab');
  for (var i = 0; i < elist.length; i++) {
    var base = elist[i].className;
    var disabledStr = ' ttab-disabled';
    if (i != window.curTab) {
      if (base.indexOf(disabledStr) == -1) {
        base += disabledStr;
      }
    } else {
      base = base.replace(disabledStr, '');
    }
    elist[i].className = base;
  }
}

function initTabs() {
  window.curTab = 0;
  setHandlerForClass('ttab', 'click', setTabTo);
  setTabStyle();
}

function gobbedInit() {
  initTabs();
  initBluetooth();
}

window.addEventListener('load', gobbedInit);
