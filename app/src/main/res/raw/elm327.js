/* Copyright (C) David Hubbard 2016. Licensed under the GPLv3. */

function Elm327() {
}

Elm327.prototype.onConnected = function() {
  setHTMLofClass('main-connect', 'Checking proto');
  this.send('ATE0');
  this.send('ATL1');
  this.send('ATI');
  this.send('STDI');
  this.send('AT@1');
  this.send('STBR115200');
  this.send('STMFR');
  this.send('STSN');
  this.send('ATRV');
  this.send('ATSHC410F1');
}

Elm327.prototype.onRecv = function(str) {
  str = str.replace('\r', '');
  //console.log('> "' + str.split('\n').join('\\n') + '"');
  if (str.endsWith('>')) {
    str += '\n';
  }
  var prevStr = window.sockSendQ[0];
  if (str.endsWith('>\n')) {
    this.popSendQueue();
  }
  var el = document.getElementsByClassName('term-chat')[0];
  appendAfterCleanup(el, str);

  // Store latest response in a dictionary keyed on what was sent.
  if (str.endsWith('>\n')) {
    var hist = getHTMLofClass('term-chat').replace('<div><br></div>','').split('&gt;');
    if (hist.length > 0 && hist[hist.length - 1] == '') hist.pop();
    if (hist.length > 0) {
      window.elmHist = window.elmHist || {};
      window.elmHist[prevStr] = hist[hist.length - 1].trim();
      console.log('[' + prevStr + ']="' + window.elmHist[prevStr] + '"');
      if (prevStr == 'ATRV') {
        setHTMLofClass('main-connect', window.elmHist['STDI']);
        setHTMLofClass('main-volts', window.elmHist['ATRV']);
      } else if (prevStr == '22096d' || prevStr == '221430') {
        setHTMLofClass(prevStr, window.elmHist[prevStr]);
      }
    }
  }
}

Elm327.prototype.onDisconnected = function(errInfo) {
  if (errInfo) {
    console.log('onDisconnected: ' + JSON.stringify(errInfo));
  }
}

Elm327.prototype.onIdle = function() {
  if (window.curTab != 0) return;

  setTimeout(function() {
  this.send('22096d');
  this.send('221430');
  }.bind(this), 200);
}
