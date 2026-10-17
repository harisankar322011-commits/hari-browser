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
            // A per-document salt keeps noise stable for repeat reads within a page,
            // while avoiding one identical canvas/audio sample across every navigation.
            let pageSeed = 0x6d2b79f5;
            try {
              if (globalThis.crypto && crypto.getRandomValues) {
                const seedWords = new Uint32Array(1);
                crypto.getRandomValues(seedWords);
                pageSeed = seedWords[0] >>> 0;
              } else {
                pageSeed = (Date.now() ^ Math.floor(Math.random() * 0xffffffff)) >>> 0;
              }
            } catch (_) {}

            const defineFixed = (target, key, value) => {
              try {
                if (!target || !(key in target)) return;
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
                      if (pixels && typeof pixels.length === 'number' && pixels.length > 0 &&
                          (pixels instanceof Uint8Array || pixels instanceof Uint8ClampedArray ||
                           pixels instanceof Float32Array)) {
                        let state = (pageSeed ^ pixels.length ^
                          (Number(arguments[2]) || 0) ^ ((Number(arguments[3]) || 0) << 8)) >>> 0;
                        const count = Math.min(24, pixels.length);
                        const used = new Set();
                        for (let n = 0; n < count && used.size < pixels.length; n++) {
                          state ^= state << 13; state ^= state >>> 17; state ^= state << 5;
                          state >>>= 0;
                          const index = state % pixels.length;
                          if (used.has(index)) continue;
                          used.add(index);
                          if (typeof pixels[index] === 'number' && Number.isFinite(pixels[index])) {
                            const delta = (state & 1) ? 1 : -1;
                            if (pixels instanceof Float32Array) {
                              pixels[index] += delta * 1e-7;
                            } else {
                              pixels[index] = Math.max(0, Math.min(255, pixels[index] + delta));
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
              const seed = pageSeed;

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

              // Protect OffscreenCanvas readbacks used by workers and modern fingerprint scripts.
              try {
                const offscreenContext = globalThis.OffscreenCanvasRenderingContext2D &&
                                         globalThis.OffscreenCanvasRenderingContext2D.prototype;
                if (offscreenContext && typeof offscreenContext.getImageData === 'function') {
                  const originalOffscreenGetImageData = offscreenContext.getImageData;
                  Object.defineProperty(offscreenContext, 'getImageData', {
                    configurable: true, writable: true,
                    value: function () {
                      return perturb(originalOffscreenGetImageData.apply(this, arguments));
                    }
                  });
                }
              } catch (_) {}
              try {
                const offscreenProto = globalThis.OffscreenCanvas &&
                                       globalThis.OffscreenCanvas.prototype;
                const originalConvertToBlob = offscreenProto && offscreenProto.convertToBlob;
                if (typeof originalConvertToBlob === 'function' &&
                    typeof globalThis.createImageBitmap === 'function') {
                  Object.defineProperty(offscreenProto, 'convertToBlob', {
                    configurable: true, writable: true,
                    value: function (options) {
                      const source = this;
                      const args = arguments;
                      if (!source.width || !source.height ||
                          source.width * source.height > 1048576) {
                        return originalConvertToBlob.apply(source, args);
                      }
                      return globalThis.createImageBitmap(source).then(bitmap => {
                        const copy = document.createElement('canvas');
                        copy.width = source.width;
                        copy.height = source.height;
                        const context = copy.getContext('2d', { willReadFrequently: true });
                        if (!context) throw new Error('Canvas context unavailable');
                        originalDrawImage.call(context, bitmap, 0, 0);
                        const imageData = originalGetImageData.call(
                          context, 0, 0, copy.width, copy.height);
                        perturb(imageData);
                        originalPutImageData.call(context, imageData, 0, 0);
                        return new Promise((resolve, reject) => {
                          originalToBlob.call(copy, blob => {
                            if (blob) resolve(blob);
                            else reject(new Error('Canvas export failed'));
                          }, options && options.type, options && options.quality);
                        }).finally(() => {
                          try { bitmap.close(); } catch (_) {}
                        });
                      }).catch(() => originalConvertToBlob.apply(source, args));
                    }
                  });
                }
              } catch (_) {}
            }

            // Add tiny noise to OfflineAudioContext output, a common audio-fingerprint source.
            try {
              const OfflineAudio = globalThis.OfflineAudioContext ||
                                   globalThis.webkitOfflineAudioContext;
              const audioProto = OfflineAudio && OfflineAudio.prototype;
              const originalStartRendering = audioProto && audioProto.startRendering;
              if (typeof originalStartRendering === 'function') {
                Object.defineProperty(audioProto, 'startRendering', {
                  configurable: true, writable: true,
                  value: function () {
                    const result = originalStartRendering.apply(this, arguments);
                    if (!result || typeof result.then !== 'function') return result;
                    return result.then(buffer => {
                      try {
                        const channels = Math.min(buffer.numberOfChannels || 0, 2);
                        for (let channel = 0; channel < channels; channel++) {
                          const samples = buffer.getChannelData(channel);
                          let state = (pageSeed ^ samples.length ^ (channel << 16)) >>> 0;
                          const stride = Math.max(1, Math.floor(samples.length / 64));
                          for (let i = 0; i < samples.length; i += stride) {
                            state ^= state << 13; state ^= state >>> 17; state ^= state << 5;
                            state >>>= 0;
                            samples[i] = Math.max(-1, Math.min(1,
                              samples[i] + ((state & 1) ? 1e-7 : -1e-7)));
                          }
                        }
                      } catch (_) {}
                      return buffer;
                    });
                  }
                });
              }
            } catch (_) {}
          } catch (_) {
            // One unsupported API must not prevent a website from loading.
          }
        })();
        """;
}
