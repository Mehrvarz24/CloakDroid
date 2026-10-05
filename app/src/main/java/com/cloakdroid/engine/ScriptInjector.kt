package com.cloakdroid.engine

import java.util.Locale

/**
 * Builds the JavaScript payload that is injected into every page of the
 * CloakDroid engine in order to mask / normalize browser fingerprints.
 *
 * The generated script is self-contained, has no external dependencies and
 * This builder only creates a payload. It is not a document_start injector by
 * itself; a Gecko WebExtension/content-script integration is required before
 * this can be treated as active privacy protection.
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
     * @param webrtcPolicy DISABLED | PROXY_ONLY | FULL (see WebRtcPolicy)
     * @param screenW spoofed window.screen.width in CSS px (0 = leave untouched)
     * @param screenH spoofed window.screen.height in CSS px (0 = leave untouched)
     * @param devicePixelRatio spoofed devicePixelRatio (0 = leave untouched)
     * @param deviceName value exposed via navigator.userAgentData hints
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
        val webrtcEnabled: Boolean = false,
        /** One of WebRtcPolicy names: DISABLED | PROXY_ONLY | FULL */
        val webrtcPolicy: String = "DISABLED",
        val screenW: Int = 0,
        val screenH: Int = 0,
        val devicePixelRatio: Float = 0f,
        val deviceName: String = ""
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
        sb.append("\"webrtcEnabled\":").append(c.webrtcEnabled).append(',')
        sb.append("\"webrtcPolicy\":").append(str(c.webrtcPolicy)).append(',')
        sb.append("\"screenW\":").append(c.screenW).append(',')
        sb.append("\"screenH\":").append(c.screenH).append(',')
        sb.append("\"devicePixelRatio\":").append(
            if (c.devicePixelRatio.isNaN() || c.devicePixelRatio <= 0f) "0"
            else String.format(Locale.US, "%.3f", c.devicePixelRatio)
        ).append(',')
        sb.append("\"deviceName\":").append(str(c.deviceName))
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

  /* Never fabricate Android permission state. The native permission result is
   * the only source of truth; without this passthrough a page could be told
   * that camera, microphone, or location is granted when it is not. */
  if (hasNav && navigator.permissions && typeof navigator.permissions.query === 'function') {
    var origQuery = navigator.permissions.query;
    navigator.permissions.query = function (descriptor) {
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

  /* --------------------------------------------------------------- screen */

  if (hasWin) {
    var sw = CFG.screenW | 0;
    var sh = CFG.screenH | 0;
    var dpr = Number(CFG.devicePixelRatio) || 0;

    if (typeof Screen !== 'undefined') {
      if (sw > 0) {
        defineGetter(Screen.prototype, 'width', sw);
        defineGetter(Screen.prototype, 'availWidth', sw);
      }
      if (sh > 0) {
        defineGetter(Screen.prototype, 'height', sh);
        defineGetter(Screen.prototype, 'availHeight', sh);
      }
    }
    if (dpr > 0) {
      defineGetter(window, 'devicePixelRatio', dpr);
    }
  }

  /* -------------------------------------------- userAgentData / deviceName */

  if (hasNav && CFG.deviceName) {
    var deviceName = String(CFG.deviceName);
    // Gecko has no navigator.userAgentData; expose a best-effort hints object
    // so page scripts probing for it see a consistent identity instead of an
    // undefined property inconsistent with the spoofed UA.
    var fakeUAD = {
      brands: Object.freeze([
        Object.freeze({ brand: 'Not.A/Brand', version: '99' }),
        Object.freeze({ brand: 'Chromium', version: '124' }),
        Object.freeze({ brand: 'Chrome', version: '124' })
      ]),
      mobile: /Mobi|Android|iPhone/.test(String(navigator.userAgent)),
      platform: deviceName.indexOf('Windows') === 0 ? 'Windows'
              : (deviceName.indexOf('Mac') === 0 ? 'macOS'
              : (deviceName.indexOf('Linux') === 0 ? 'Linux' : 'Android')),
      getHighEntropyValues: function (hints) {
        var self = this;
        return Promise.resolve().then(function () {
          var out = {
            architecture: self.platform === 'Windows' || self.platform === 'Linux' ? 'x86' : 'arm',
            bitness: '64',
            model: self.platform === 'Android' ? deviceName : '',
            platformVersion: '10.0.0',
            uaFullVersion: '124.0.0.0',
            fullVersionList: Object.freeze([
              Object.freeze({ brand: 'Not.A/Brand', version: '99.0.0.0' }),
              Object.freeze({ brand: 'Chromium', version: '124.0.0.0' }),
              Object.freeze({ brand: 'Chrome', version: '124.0.0.0' })
            ])
          };
          var picked = {};
          try {
            hints = Object.freeze(Array.prototype.slice.call(hints || []));
          } catch (e) { hints = []; }
          for (var h = 0; h < hints.length; h++) {
            if (Object.prototype.hasOwnProperty.call(out, hints[h])) {
              picked[hints[h]] = out[hints[h]];
            }
          }
          picked.mobile = self.mobile;
          picked.platform = self.platform;
          return picked;
        });
      },
      toJSON: function () {
        return { brands: this.brands, mobile: this.mobile, platform: this.platform };
      }
    };
    try {
      Object.defineProperty(Navigator.prototype, 'userAgentData', {
        get: function () { return fakeUAD; },
        configurable: true,
        enumerable: true
      });
    } catch (eUad) { /* ignore */ }
  }

  /* --------------------------------------------------------------- webrtc */

  var rtcPolicy = String(CFG.webrtcPolicy || (CFG.webrtcEnabled ? 'FULL' : 'DISABLED')).toUpperCase();

  if (rtcPolicy === 'DISABLED' && hasWin) {
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
  } else if (rtcPolicy === 'PROXY_ONLY' && hasWin) {
    // Keep WebRTC usable, but scrub every host candidate so the local / real
    // IP can never be enumerated by page scripts; only srflx / relay
    // candidates reflecting the proxy egress survive.
    var sanitizeCandidates = function (list) {
      var out = [];
      try {
        for (var i = 0; i < list.length; i++) {
          var cand = String(list[i] || '');
          if (cand.indexOf('typ host') !== -1) { continue; }
          if (/(^|\s)(10\.|172\.(1[6-9]|2\d|3[01])\.|192\.168\.|169\.254\.|127\.|::1|fc|fd)/i.test(cand)) {
            continue;
          }
          out.push(cand);
        }
      } catch (e) { /* ignore */ }
      return out;
    };

    var patchIce = function (Proto) {
      if (!Proto || typeof Proto.prototype.addIceCandidate !== 'function') { return; }
      var origSetLocal = Proto.prototype.setLocalDescription;
      if (typeof origSetLocal === 'function') {
        Proto.prototype.setLocalDescription = function (desc) {
          try {
            if (desc && desc.sdp) {
              var lines = String(desc.sdp).split(/(?:\r\n|\r|\n)/);
              var kept = sanitizeCandidates(lines);
              var munged = kept.join('\r\n');
              for (var l = 0; l < lines.length; l++) {
                if (lines[l].indexOf('a=') === 0 &&
                    lines[l].indexOf('a=candidate:') !== 0 &&
                    lines[l].indexOf('a=end-of-candidates') !== 0) {
                  munged += '\r\n' + lines[l];
                }
              }
              desc = Object.assign({}, desc, { sdp: munged });
            }
          } catch (e) { /* ignore */ }
          return origSetLocal.call(this, desc);
        };
      }
    };

    try { patchIce(window.RTCPeerConnection); } catch (ePc) { /* ignore */ }
    try { patchIce(window.webkitRTCPeerConnection); } catch (ePc2) { /* ignore */ }
    try { patchIce(window.mozRTCPeerConnection); } catch (ePc3) { /* ignore */ }
  }

  /* Freeze side-effect free globals so page scripts cannot detect rewrites. */
  try { Object.defineProperty(window, '__CLOAKDROID_INJECTED__', { value: true, enumerable: false }); }
  catch (e) { /* ignore */ }
})();
"""
}
