package com.cloakdroid.engine

import java.util.Locale

/**
 * Builds the JavaScript payload that is injected into every page of the
 * CloakDroid engine in order to mask / normalize browser fingerprints.
 *
 * The generated script is self-contained, has no external dependencies and
 * runs before any page script (document_start injection).
 */
object ScriptInjector {

    const val DEFAULT_ACCURACY_MIN = 15.0
    const val DEFAULT_ACCURACY_MAX = 45.0

    /**
     * Everything the injector needs to know about the fake identity that is
     * currently active.
     *
     * @param lat spoofed latitude in decimal degrees (WGS84)
     * @param lon spoofed longitude in decimal degrees (WGS84)
     * @param accuracyMeters nominal reported accuracy, clamped to 15..45 m
     * @param timezoneId value returned by Intl / Date timezone queries
     * @param localeTag value returned by navigator.language
     * @param hardwareConcurrency fake core count
     * @param deviceMemory fake device memory in GiB
     * @param platform fake navigator.platform
     * @param canvasSeed seed for deterministic canvas/audio noise
     * @param audioNoiseEnabled perturb AudioBuffer.getChannelData
     * @param canvasNoiseEnabled perturb canvas readbacks
     * @param webrtcEnabled when false RTCPeerConnection is removed entirely
     */
    data class SpoofConfig(
        val lat: Double,
        val lon: Double,
        val accuracyMeters: Double = 30.0,
        val timezoneId: String = "UTC",
        val localeTag: String = "en-US",
        val hardwareConcurrency: Int = 8,
        val deviceMemory: Double = 8.0,
        val platform: String = "Linux aarch64",
        val canvasSeed: Long = 0x5EEDL,
        val audioNoiseEnabled: Boolean = true,
        val canvasNoiseEnabled: Boolean = true,
        val webrtcEnabled: Boolean = false
    )

    /**
     * Serializes [config] to JSON and wraps it in the spoofing runtime.
     * Every dollar sign that must appear literally inside the generated
     * JavaScript is written as `${'$'}` so that Kotlin does not treat it as
     * a string template.
     */
    fun buildScript(config: SpoofConfig): String {
        val json = buildConfigJson(config)
        return SCRIPT_TEMPLATE.replace(CONFIG_TOKEN, json)
    }

    // ------------------------------------------------------------------ JSON

    private fun buildConfigJson(c: SpoofConfig): String {
        val sb = StringBuilder(256)
        sb.append('{')
        sb.append("\"lat\":").append(num(c.lat)).append(',')
        sb.append("\"lon\":").append(num(c.lon)).append(',')
        sb.append("\"accuracyMeters\":").append(num(c.accuracyMeters.coerceIn(
            DEFAULT_ACCURACY_MIN, DEFAULT_ACCURACY_MAX))).append(',')
        sb.append("\"accuracyMin\":").append(num(DEFAULT_ACCURACY_MIN)).append(',')
        sb.append("\"accuracyMax\":").append(num(DEFAULT_ACCURACY_MAX)).append(',')
        sb.append("\"timezoneId\":").append(str(c.timezoneId)).append(',')
        sb.append("\"localeTag\":").append(str(c.localeTag)).append(',')
        sb.append("\"hardwareConcurrency\":").append(c.hardwareConcurrency).append(',')
        sb.append("\"deviceMemory\":").append(num(c.deviceMemory)).append(',')
        sb.append("\"platform\":").append(str(c.platform)).append(',')
        sb.append("\"canvasSeed\":").append(c.canvasSeed).append(',')
        sb.append("\"audioNoiseEnabled\":").append(c.audioNoiseEnabled).append(',')
        sb.append("\"canvasNoiseEnabled\":").append(c.canvasNoiseEnabled).append(',')
        sb.append("\"webrtcEnabled\":").append(c.webrtcEnabled)
        sb.append('}')
        return sb.toString()
    }

    private fun num(v: Double): String =
        if (v.isNaN() || v.isInfinite()) "0" else String.format(Locale.US, "%.10f", v)

    private fun str(v: String): String {
        val clean = v.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"" + clean + "\""
    }

    // --------------------------------------------------------------- runtime

    private const val CONFIG_TOKEN = "__CLOAKDROID_CONFIG__"

    private val SCRIPT_TEMPLATE = """
(function () {
  'use strict';

  var CFG = __CLOAKDROID_CONFIG__;

  var hasNav = typeof Navigator !== 'undefined';
  var hasWin = typeof window !== 'undefined';

  /* ---------------------------------------------------------------- random */

  function mulberry32(a) {
    a = a | 0;
    return function () {
      a = (a + 0x6D2B79F5) | 0;
      var t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function makeNormal(rand) {
    var spare = null;
    return function () {
      if (spare !== null) {
        var keep = spare;
        spare = null;
        return keep;
      }
      var u = 0, v = 0, s = 0;
      do {
        u = rand() * 2 - 1;
        v = rand() * 2 - 1;
        s = u * u + v * v;
      } while (s === 0 || s >= 1);
      var m = Math.sqrt(-2 * Math.log(s) / s);
      spare = v * m;
      return u * m;
    };
  }

  function clamp255(v) {
    return v < 0 ? 0 : (v > 255 ? 255 : v);
  }

  function clampLat(v) {
    return v < -90 ? -90 : (v > 90 ? 90 : v);
  }

  function wrapLon(v) {
    var x = v;
    while (x > 180) { x -= 360; }
    while (x < -180) { x += 360; }
    return x;
  }

  function defineGetter(proto, prop, value) {
    try {
      Object.defineProperty(proto, prop, {
        get: function () { return value; },
        configurable: true,
        enumerable: true
      });
      return true;
    } catch (e) {
      try { proto[prop] = value; } catch (e2) { /* ignore */ }
      return false;
    }
  }

  /* ----------------------------------------------------------- navigator */

  var localeRaw = String(CFG.localeTag || 'en-US').replace(/\s+${'$'}/g, '');
  var localeBase = localeRaw.split('-')[0].split('_')[0];

  if (hasNav) {
    defineGetter(Navigator.prototype, 'hardwareConcurrency', CFG.hardwareConcurrency);
    defineGetter(Navigator.prototype, 'deviceMemory', CFG.deviceMemory);
    defineGetter(Navigator.prototype, 'platform', CFG.platform);
    defineGetter(Navigator.prototype, 'language', localeRaw);
    defineGetter(Navigator.prototype, 'languages', Object.freeze([localeRaw, localeBase]));
  }

  /* ------------------------------------------------- timezone & locales */

  var TZ = String(CFG.timezoneId || 'UTC');
  var resolvedOk = false;
  try {
    var origResolved = Intl.DateTimeFormat.prototype.resolvedOptions;
    Intl.DateTimeFormat.prototype.resolvedOptions = function () {
      var opts = origResolved.call(this);
      try { opts.timeZone = TZ; } catch (e) { /* ignore */ }
      try { opts.locale = localeRaw; } catch (e2) { /* ignore */ }
      return opts;
    };
    resolvedOk = true;
  } catch (e) { /* ignore */ }

  function timezoneOffsetMinutes(date) {
    var dtf = new Intl.DateTimeFormat('en-US', {
      timeZone: TZ,
      hour12: false,
      year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit'
    });
    var parts = dtf.formatToParts(date);
    var map = {};
    for (var i = 0; i < parts.length; i++) { map[parts[i].type] = parts[i].value; }
    var asUtc = Date.UTC(
      parseInt(map.year, 10),
      parseInt(map.month, 10) - 1,
      parseInt(map.day, 10),
      parseInt(map.hour, 10) % 24,
      parseInt(map.minute, 10),
      parseInt(map.second, 10)
    );
    return -Math.round((asUtc - date.getTime()) / 60000);
  }

  try {
    var origOffset = Date.prototype.getTimezoneOffset;
    Date.prototype.getTimezoneOffset = function () {
      try {
        var off = timezoneOffsetMinutes(this);
        return typeof off === 'number' && isFinite(off) ? off : origOffset.call(this);
      } catch (e) {
        return origOffset.call(this);
      }
    };
  } catch (e2) { /* ignore */ }

  /* ------------------------------------------------------- permissions */

  if (hasNav && navigator.permissions && typeof navigator.permissions.query === 'function') {
    var origQuery = navigator.permissions.query;
    var grantedFor = {
      geolocation: true,
      notifications: true,
      camera: true,
      microphone: true,
      midi: true,
      bluetooth: true,
      'persistent-storage': true
    };
    navigator.permissions.query = function (descriptor) {
      var name = descriptor && descriptor.name;
      if (name && grantedFor[name] === true) {
        var status = {
          state: 'granted',
          status: 'granted',
          name: name,
          onchange: null,
          addEventListener: function () { return undefined; },
          removeEventListener: function () { return undefined; },
          dispatchEvent: function () { return true; },
          on: function () { return undefined; },
          off: function () { return undefined; },
          once: function () { return undefined; }
        };
        try {
          if (typeof EventTarget !== 'undefined') {
            EventTarget.call(status);
            Object.setPrototypeOf(status, EventTarget.prototype);
          }
        } catch (e) { /* ignore */ }
        return Promise.resolve(status);
      }
      try {
        return origQuery.call(this, descriptor);
      } catch (e) {
        return Promise.reject(e);
      }
    };
  }

  /* -------------------------------------------------------- geolocation */

  var geoRand = mulberry32((CFG.canvasSeed | 0) ^ 0x9E3779B9);
  var geoNorm = makeNormal(geoRand);

  function nextAccuracy() {
    var base = CFG.accuracyMeters;
    var min = CFG.accuracyMin;
    var max = CFG.accuracyMax;
    var jittered = base + (geoRand() * 2 - 1) * ((max - min) / 2);
    if (jittered < min) { jittered = min + (min - jittered); }
    if (jittered > max) { jittered = max - (jittered - max); }
    return jittered;
  }

  function fakePosition() {
    var accuracy = nextAccuracy();
    var sigmaLat = accuracy / 111320;
    var cosLat = Math.cos(CFG.lat * Math.PI / 180);
    if (!cosLat || cosLat < 0.01) { cosLat = 0.01; }
    var lat = clampLat(CFG.lat + geoNorm() * sigmaLat);
    var lon = wrapLon(CFG.lon + geoNorm() * (sigmaLat / cosLat));
    return {
      coords: {
        latitude: lat,
        longitude: lon,
        accuracy: accuracy,
        altitude: null,
        altitudeAccuracy: null,
        heading: null,
        speed: null
      },
      timestamp: Date.now()
    };
  }

  function positionError(code, message) {
    var err = { code: code, message: message };
    err.PERMISSION_DENIED = 1;
    err.POSITION_UNAVAILABLE = 2;
    err.TIMEOUT = 3;
    return err;
  }

  var fakeGeo = {
    getCurrentPosition: function (onSuccess, onError, options) {
      setTimeout(function () {
        try {
          if (typeof onSuccess === 'function') {
            onSuccess(fakePosition());
          }
        } catch (e) {
          if (typeof onError === 'function') {
            onError(positionError(2, 'Position unavailable'));
          }
        }
      }, 0);
    },
    watchPosition: function (onSuccess, onError, options) {
      var id = fakeGeo._nextId++;
      fakeGeo._watchers[id] = true;
      var tick = function () {
        if (!fakeGeo._watchers[id]) { return; }
        try {
          if (typeof onSuccess === 'function') { onSuccess(fakePosition()); }
        } catch (e) { /* ignore */ }
        fakeGeo._timers[id] = setTimeout(tick, 15000);
      };
      fakeGeo._timers[id] = setTimeout(tick, 0);
      return id;
    },
    clearWatch: function (id) {
      fakeGeo._watchers[id] = false;
      if (fakeGeo._timers[id]) {
        clearTimeout(fakeGeo._timers[id]);
        delete fakeGeo._timers[id];
      }
      return true;
    },
    _nextId: 1,
    _watchers: {},
    _timers: {}
  };

  if (hasNav) {
    defineGetter(Navigator.prototype, 'geolocation', fakeGeo);
  }

  /* ------------------------------------------------------------- canvas */

  function noiseImageData(img, seed) {
    var data = img.data;
    var rnd = mulberry32((seed | 0) ^ ((img.width * 7919) | 0) ^ ((img.height * 104729) | 0));
    for (var i = 0; i < data.length; i += 4) {
      var n = Math.floor(rnd() * 3) - 1;
      if (n !== 0) {
        data[i] = clamp255(data[i] + n);
        if (rnd() < 0.6) { data[i + 1] = clamp255(data[i + 1] + n); }
        if (rnd() < 0.4) { data[i + 2] = clamp255(data[i + 2] - n); }
      }
    }
    return img;
  }

  if (CFG.canvasNoiseEnabled && typeof HTMLCanvasElement !== 'undefined') {

    if (typeof CanvasRenderingContext2D !== 'undefined' &&
        typeof CanvasRenderingContext2D.prototype.getImageData === 'function') {
      var origGetImageData = CanvasRenderingContext2D.prototype.getImageData;
      CanvasRenderingContext2D.prototype.getImageData = function (sx, sy, sw, sh) {
        var img = origGetImageData.apply(this, arguments);
        try {
          noiseImageData(img, (CFG.canvasSeed | 0) ^ (sw * 31) ^ (sh * 17) ^ (sx * 7) ^ (sy * 3));
        } catch (e) { /* ignore */ }
        return img;
      };
    }

    var origToDataURL = HTMLCanvasElement.prototype.toDataURL;
    HTMLCanvasElement.prototype.toDataURL = function () {
      var w = this.width | 0;
      var h = this.height | 0;
      if (!w || !h) { return origToDataURL.apply(this, arguments); }

      var ctx = null;
      try { ctx = this.getContext('2d'); } catch (e) { ctx = null; }
      if (!ctx || typeof ctx.getImageData !== 'function') {
        return origToDataURL.apply(this, arguments);
      }

      var snapshot = null;
      try { snapshot = ctx.getImageData(0, 0, w, h); } catch (e2) { snapshot = null; }
      if (!snapshot) { return origToDataURL.apply(this, arguments); }

      var original = new Uint8ClampedArray(snapshot.data);
      var result;
      try {
        noiseImageData(snapshot, CFG.canvasSeed | 0);
        ctx.putImageData(snapshot, 0, 0);
        result = origToDataURL.apply(this, arguments);
      } finally {
        try {
          var restore = ctx.createImageData(w, h);
          restore.data.set(original);
          ctx.putImageData(restore, 0, 0);
        } catch (e3) {
          try {
            ctx.putImageData(new ImageData(original, w, h), 0, 0);
          } catch (e4) { /* ignore */ }
        }
      }
      return result;
    };
  }

  /* -------------------------------------------------------------- audio */

  if (CFG.audioNoiseEnabled && typeof AudioBuffer !== 'undefined' &&
      typeof AudioBuffer.prototype.getChannelData === 'function') {
    var origGetChannelData = AudioBuffer.prototype.getChannelData;
    AudioBuffer.prototype.getChannelData = function (channel) {
      var src = origGetChannelData.call(this, channel);
      if (!src || !src.length) { return src; }
      var out = new Float32Array(src.length);
      var rnd = mulberry32((CFG.canvasSeed | 0) ^ ((channel + 1) * 2654435761) ^ (src.length | 0));
      for (var i = 0; i < src.length; i++) {
        var v = src[i] + (rnd() - 0.5) * 0.00098;
        out[i] = v < -1 ? -1 : (v > 1 ? 1 : v);
      }
      return out;
    };
  }

  /* --------------------------------------------------------------- webrtc */

  if (!CFG.webrtcEnabled && hasWin) {
    var disabled = function RTCPeerConnection() {
      var err = new Error('RTCPeerConnection is disabled by CloakDroid');
      err.name = 'NotSupportedError';
      throw err;
    };
    disabled.prototype = {};
    disabled.generateCertificate = function () {
      var err = new Error('RTCPeerConnection is disabled by CloakDroid');
      err.name = 'NotSupportedError';
      throw err;
    };
    var rtcNames = ['RTCPeerConnection', 'webkitRTCPeerConnection', 'mozRTCPeerConnection'];
    for (var r = 0; r < rtcNames.length; r++) {
      try {
        Object.defineProperty(window, rtcNames[r], {
          value: disabled,
          writable: false,
          configurable: false,
          enumerable: false
        });
      } catch (e) {
        try { window[rtcNames[r]] = disabled; } catch (e2) { /* ignore */ }
      }
    }
    try {
      Object.defineProperty(window, 'RTCDataChannel', { get: function () { return undefined; } });
    } catch (e3) { /* ignore */ }
  }

  /* Freeze side-effect free globals so page scripts cannot detect rewrites. */
  try { Object.defineProperty(window, '__CLOAKDROID_INJECTED__', { value: true, enumerable: false }); }
  catch (e) { /* ignore */ }
})();
"""
}
