/* Copyright (C) David Hubbard 2016. Licensed under the GPLv3. */

chrome.app.runtime.onLaunched.addListener(function() {
  chrome.app.window.create('raw/index.html', {
    'outerBounds': {
      'width': 600,
      'height': 340
    }
  });
});