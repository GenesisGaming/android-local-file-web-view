var originalOnLoadHandler = window.onload

function redirectToVerifiedUrl() {

    var xmlHttp = new XMLHttpRequest();
    xmlHttp.onreadystatechange = function() {
        if (xmlHttp.readyState == 4 && xmlHttp.status == 200) {
            var data = JSON.parse(xmlHttp.responseText);
            window.location.href = "index.html?partner=8c31b93c-24bd-4dfa-aa16-db96c0296b3a&session="
            + data.session_token + "&mode=real&turbo=true";
        }
    }
    xmlHttp.open("GET", "https://krug-bo.star9ad.com/m4/wallet/balance/harvey-test", true); // true for asynchronous
    xmlHttp.setRequestHeader("X-Genesis-PartnerToken", "8c31b93c-24bd-4dfa-aa16-db96c0296b3a");
    xmlHttp.setRequestHeader("X-Genesis-Secret", "eeb847b7-16a2-4618-951c-8d07c78f3dd6");
    xmlHttp.send(null);
}

window.onload = () => {
  'use strict';

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('./sw.js');
  }

  if (!new URLSearchParams(window.location.search).get("session")) {
    redirectToVerifiedUrl();
  }

  if (originalOnLoadHandler) {
    originalOnLoadHandler();
  }
}