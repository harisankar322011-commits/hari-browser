package com.haricalcx.haribrowser;

/**
 * Best-effort fingerprint-surface reduction for Android WebView.
 * This cannot hide IP/network metadata or make all devices indistinguishable.
 */
final class FingerprintProtection {
    private FingerprintProtection() {}

    static final String SCRIPT = """
        (() => {
          'use strict';
          try {
            const defineFixed = (target, key, value) => {
              try {
                if (!target) return;
                const own = Object.getOwnPropertyDescriptor(target, key);
                if (own && !own.configurable) return;
                Object.defineProperty(target, key, {
                  get: () => value,
                  configurable: false,
                  enumerable: own ? own.enumerable : true
                });
              } catch (_) {}
            };

            // Keep selected hardware hints in a common range.
            if (typeof navigator !== 'undefined') {
              defineFixed(navigator, 'hardwareConcurrency', 4);
              defineFixed(navigator, 'deviceMemory', 4);
              try { defineFixed(navigator, 'plugins', Object.freeze([])); } catch (_) {}
              try { defineFixed(navigator, 'mimeTypes', Object.freeze([])); } catch (_) {}
              try { defineFixed(navigator, 'getBattery', undefined); } catch (_) {}
              try { defineFixed(navigator, 'connection', undefined); } catch (_) {}

              // Do not reveal exact input-device identifiers to scripts. Keep device kinds
              // so sites can still present generic microphone/camera choices.
              try {
                const mediaDevices = navigator.mediaDevices;
                if (mediaDevices && typeof mediaDevices.enumerateDevices === 'function') {
                  const originalEnumerate = mediaDevices.enumerateDevices.bind(mediaDevices);
                  Object.defineProperty(mediaDevices, 'enumerateDevices', {
                    configurable: true,
                    writable: true,
                    value: () => originalEnumerate().then(devices => devices.map(device =>
                      Object.freeze({
                        kind: device.kind,
                        deviceId: '',
                        groupId: '',
                        label: ''
                      })
                    ))
                  });
                }
              } catch (_) {}
            }

            // Round screen dimensions into broad buckets to reduce device-specific precision.
            try {
              const bucket = value => {
                const n = Number(value);
                return Number.isFinite(n) && n > 0 ? Math.max(300, Math.round(n / 100) * 100) : value;
              };
              if (typeof screen !== 'undefined') {
                defineFixed(screen, 'width', bucket(screen.width));
                defineFixed(screen, 'height', bucket(screen.height));
                defineFixed(screen, 'availWidth', bucket(screen.availWidth));
                defineFixed(screen, 'availHeight', bucket(screen.availHeight));
                defineFixed(screen, 'colorDepth', 24);
                defineFixed(screen, 'pixelDepth', 24);
              }
            } catch (_) {}

            // Hide the WebGL debug extension and cap unusually distinctive device limits.
            const patchWebGL = Constructor => {
              if (!Constructor || !Constructor.prototype) return;
              const prototype = Constructor.prototype;
              try {
                const originalGetExtension = prototype.getExtension;
                if (typeof originalGetExtension === 'function') {
                  Object.defineProperty(prototype, 'getExtension', {
                    configurable: true,
                    writable: true,
                    value: function (name) {
                      if (typeof name === 'string' &&
                          name.toLowerCase() === 'webgl_debug_renderer_info') return null;
                      return originalGetExtension.apply(this, arguments);
                    }
                  });
                }
              } catch (_) {}
              try {
                const originalGetSupportedExtensions = prototype.getSupportedExtensions;
                if (typeof originalGetSupportedExtensions === 'function') {
                  Object.defineProperty(prototype, 'getSupportedExtensions', {
                    configurable: true,
                    writable: true,
                    value: function () {
                      const extensions = originalGetSupportedExtensions.apply(this, arguments);
                      return Array.isArray(extensions)
                        ? extensions.filter(e => String(e).toLowerCase() !== 'webgl_debug_renderer_info')
                        : extensions;
                    }
                  });
                }
              } catch (_) {}
              try {
                const originalGetParameter = prototype.getParameter;
                if (typeof originalGetParameter === 'function') {
                  Object.defineProperty(prototype, 'getParameter', {
                    configurable: true,
                    writable: true,
                    value: function (parameter) {
                      if (parameter === 0x1F00) return 'WebKit';       // VENDOR
                      if (parameter === 0x1F01) return 'WebKit WebGL';  // RENDERER
                      if (parameter === 0x9245) return 'WebKit';       // UNMASKED_VENDOR
                      if (parameter === 0x9246) return 'WebKit WebGL'; // UNMASKED_RENDERER
                      const value = originalGetParameter.apply(this, arguments);
                      if (typeof value === 'number') {
                        if (parameter === 0x0D33 || parameter === 0x851C ||
                            parameter === 0x84E8) return Math.min(value, 4096);
                        if (parameter === 0x8869 || parameter === 0x8872 ||
                            parameter === 0x8B4C) return Math.min(value, 16);
                        if (parameter === 0x8B4D) return Math.min(value, 32);
                      }
                      if (parameter === 0x0D3A && value && value.length >= 2) {
                        return new Int32Array([Math.min(value[0], 4096), Math.min(value[1], 4096)]);
                      }
                      return value;
                    }
                  });
                }
              } catch (_) {}
              // Add minimal, deterministic perturbation to WebGL readback pixels only.
              try {
                const originalReadPixels = prototype.readPixels;
                if (typeof originalReadPixels === 'function') {
                  Object.defineProperty(prototype, 'readPixels', {
                    configurable: true,
                    writable: true,
                    value: function () {
                      const result = originalReadPixels.apply(this, arguments);
                      const pixels = arguments[6];
                      if (pixels && typeof pixels.length === 'number' && pixels.length > 0) {
                        const stride = Math.max(1, Math.floor(pixels.length / 24));
                        for (let i = 0; i < pixels.length && i < stride * 24; i += stride) {
                          if (typeof pixels[i] === 'number' && Number.isFinite(pixels[i])) {
                            const delta = ((i / stride) % 2 === 0) ? 1 : -1;
                            if (pixels instanceof Float32Array) {
                              pixels[i] = pixels[i] + delta * 1e-7;
                            } else {
                              pixels[i] = Math.max(0, Math.min(255, pixels[i] + delta));
                            }
                          }
                        }
                      }
                      return result;
                    }
                  });
                }
              } catch (_) {}
            };
            patchWebGL(globalThis.WebGLRenderingContext);
            patchWebGL(globalThis.WebGL2RenderingContext);

            // Canvas readback protections. Drawing actually displayed is left untouched.
            const canvasProto = globalThis.HTMLCanvasElement &&
                                globalThis.HTMLCanvasElement.prototype;
            const contextProto = globalThis.CanvasRenderingContext2D &&
                                 globalThis.CanvasRenderingContext2D.prototype;
            if (canvasProto && contextProto) {
              let seed = 0x6d2b79f5;
              try {
                if (globalThis.crypto && crypto.getRandomValues) {
                  const words = new Uint32Array(1);
                  crypto.getRandomValues(words);
                  seed = words[0] >>> 0;
                } else {
                  seed = (Date.now() ^ Math.floor(Math.random() * 0xffffffff)) >>> 0;
                }
              } catch (_) {}

              const originalGetImageData = contextProto.getImageData;
              const originalPutImageData = contextProto.putImageData;
              const originalDrawImage = contextProto.drawImage;
              const originalToDataURL = canvasProto.toDataURL;
              const originalToBlob = canvasProto.toBlob;

              const perturb = imageData => {
                try {
                  const data = imageData.data;
                  const pixels = Math.min(imageData.width * imageData.height, data.length / 4);
                  if (pixels < 1 || pixels > 1048576) return imageData;
                  const count = Math.min(48, Math.max(3, Math.floor(pixels / 96)));
                  let state = (seed ^ Math.imul(imageData.width + 1, 0x45d9f3b) ^
                               Math.imul(imageData.height + 1, 0x119de1f3)) >>> 0;
                  const used = new Set();
                  for (let n = 0; n < count && used.size < pixels; n++) {
                    state ^= state << 13; state ^= state >>> 17; state ^= state << 5;
                    state >>>= 0;
                    const pixel = state % pixels;
                    if (used.has(pixel)) continue;
                    used.add(pixel);
                    const offset = pixel * 4;
                    if (data[offset + 3] === 0) continue;
                    const channel = offset + ((state >>> 24) % 3);
                    data[channel] = Math.max(0, Math.min(255,
                      data[channel] + ((state & 1) ? 1 : -1)));
                  }
                } catch (_) {}
                return imageData;
              };

              if (typeof originalGetImageData === 'function') {
                try {
                  Object.defineProperty(contextProto, 'getImageData', {
                    configurable: true, writable: true,
                    value: function () {
                      return perturb(originalGetImageData.apply(this, arguments));
                    }
                  });
                } catch (_) {}
              }

              const protectedCopy = source => {
                try {
                  if (!source || !source.width || !source.height ||
                      source.width * source.height > 1048576) return null;
                  const copy = document.createElement('canvas');
                  copy.width = source.width; copy.height = source.height;
                  const context = copy.getContext('2d', { willReadFrequently: true });
                  if (!context) return null;
                  originalDrawImage.call(context, source, 0, 0);
                  const imageData = originalGetImageData.call(context, 0, 0, copy.width, copy.height);
                  perturb(imageData);
                  originalPutImageData.call(context, imageData, 0, 0);
                  return copy;
                } catch (_) { return null; }
              };

              if (typeof originalToDataURL === 'function') {
                try {
                  Object.defineProperty(canvasProto, 'toDataURL', {
                    configurable: true, writable: true,
                    value: function () {
                      const copy = protectedCopy(this);
                      return originalToDataURL.apply(copy || this, arguments);
                    }
                  });
                } catch (_) {}
              }
              if (typeof originalToBlob === 'function') {
                try {
                  Object.defineProperty(canvasProto, 'toBlob', {
                    configurable: true, writable: true,
                    value: function () {
                      const copy = protectedCopy(this);
                      return originalToBlob.apply(copy || this, arguments);
                    }
                  });
                } catch (_) {}
              }
            }
          } catch (_) {
            // One unsupported API must not prevent a website from loading.
          }
        })();
        """;
}
