package com.kregosh.mtglifetracker.web.platform

import kotlin.js.Promise

// Small browser APIs the platform layer needs.

/** The file at [url] as base64. */
fun fetchBase64(url: String): Promise<JsString> = js("""
    fetch(url).then(function (r) {
        if (!r.ok) throw new Error('HTTP ' + r.status);
        return r.arrayBuffer();
    }).then(function (buf) {
        var bytes = new Uint8Array(buf), s = '';
        for (var i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
        return btoa(s);
    })
""")

/**
 * Lets the player pick an image file, and hands back a JPEG data URL of it at most
 * [maxSize] pixels on its longest side, small enough to keep in localStorage.
 */
fun pickImage(maxSize: Int, onPicked: (String) -> Unit): Unit = js("""{
    var input = document.createElement('input');
    input.type = 'file';
    input.accept = 'image/*';
    // Attached (but hidden), as some browsers ignore clicks on a detached file input.
    input.style.display = 'none';
    document.body.appendChild(input);
    input.onchange = function () {
        var file = input.files && input.files[0];
        input.remove();
        if (!file) return;
        var url = URL.createObjectURL(file);
        var img = new Image();
        img.onload = function () {
            var scale = Math.min(1, maxSize / Math.max(img.naturalWidth, img.naturalHeight));
            var canvas = document.createElement('canvas');
            canvas.width = Math.round(img.naturalWidth * scale);
            canvas.height = Math.round(img.naturalHeight * scale);
            canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
            URL.revokeObjectURL(url);
            onPicked(canvas.toDataURL('image/jpeg', 0.85));
        };
        img.src = url;
    };
    input.click();
}""")

/** The share sheet where there is one (phones), otherwise the clipboard. */
fun shareText(text: String, title: String): Unit = js("""{
    if (navigator.share) navigator.share({ title: title, text: text }).catch(function () {});
    else if (navigator.clipboard) navigator.clipboard.writeText(text).catch(function () {});
}""")

/**
 * Keeps the screen on while the page is visible; the browser drops the lock when the page
 * is hidden, so it's taken again on return. Returns a function that ends it.
 */
fun keepScreenOn(): JsAny = js("""(function () {
    var lock = null, active = true;
    function request() {
        if (active && navigator.wakeLock && document.visibilityState === 'visible')
            navigator.wakeLock.request('screen').then(function (l) { lock = l; }).catch(function () {});
    }
    document.addEventListener('visibilitychange', request);
    request();
    return function () {
        active = false;
        document.removeEventListener('visibilitychange', request);
        if (lock) lock.release().catch(function () {});
    };
})()""")
