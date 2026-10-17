package com.haricalcx.haribrowser;

/**
 * Best-effort anti-fingerprinting measures for Android WebView.
 *
 * These measures reduce a few high-entropy JavaScript surfaces; they cannot
 * make a browser completely fingerprint-proof or hide network-level metadata.
 */
final class FingerprintProtection {
    private FingerprintProtection() {}

    static final String SCRIPT = """
        (() => {
          'use strict';
          try {
            const defineFixed = (target, key, value) => {
              try {
                const descriptor = Object.getOwnPropertyDescriptor(target, key);
                if (!descriptor || descriptor.configurable) {
                  Object.defineProperty(target, key, {
                    get: () => value,
                    configurable: false,
                    enumerable: descriptor ? descriptor.enumerable : true
                  });
                }
              } catch (_) {}
            };

            // Reduce variation in common hardware hints without changing language,
            // screen dimensions, touch support, or other compatibility-sensitive values.
            if (typeof navigator !== 'undefined') {
              defineFixed(navigator, 'hardwareConcurrency', 4);
              defineFixed(navigator, 'deviceMemory', 4);
              try {
                defineFixed(navigator, 'plugins', Object.freeze([]));
                defineFixed(navigator, 'mimeTypes', Object.freeze([]));
              } catch (_) {}
            }

            // Hide the WebGL debug extension and return generic renderer strings.
            const patchWebGL = (Constructor) => {
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
                          name.toLowerCase() === 'webgl_debug_renderer_info') {
                        return null;
                      }
                      return originalGetExtension.apply(this, arguments);
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
                      if (parameter === 0x9245) return 'WebKit';
                      if (parameter === 0x9246) return 'WebKit WebGL';
                      return originalGetParameter.apply(this, arguments);
                    }
                  });
                }
              } catch (_) {}
            };
            patchWebGL(globalThis.WebGLRenderingContext);
            patchWebGL(globalThis.WebGL2RenderingContext);

            // Add very small, repeatable-per-document changes to selected canvas
            // readbacks. The original page canvas is not visually modified.
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
                  seed = ((Date.now() ^ Math.floor(Math.random() * 0xffffffff)) >>> 0);
                }
              } catch (_) {}

              const originalGetImageData = contextProto.getImageData;
              const originalPutImageData = contextProto.putImageData;
              const originalDrawImage = contextProto.drawImage;
              const originalToDataURL = canvasProto.toDataURL;
              const originalToBlob = canvasProto.toBlob;

              const perturb = (imageData) => {
                try {
                  const data = imageData.data;
                  const pixels = Math.min(imageData.width * imageData.height, data.length / 4);
                  if (pixels < 1 || pixels > 1048576) return imageData;
                  const count = Math.min(48, Math.max(3, Math.floor(pixels / 96)));
                  let state = (seed ^ Math.imul(imageData.width + 1, 0x45d9f3b) ^
                               Math.imul(imageData.height + 1, 0x119de1f3)) >>> 0;
                  const used = new Set();
                  for (let n = 0; n < count && used.size < pixels; n++) {
                    state ^= state << 13;
                    state ^= state >>> 17;
                    state ^= state << 5;
                    state >>>= 0;
                    const pixel = state % pixels;
                    if (used.has(pixel)) continue;
                    used.add(pixel);
                    const offset = pixel * 4;
                    if (data[offset + 3] === 0) continue;
                    const channel = offset + ((state >>> 24) % 3);
                    const delta = (state & 1) ? 1 : -1;
                    data[channel] = Math.max(0, Math.min(255, data[channel] + delta));
                  }
                } catch (_) {}
                return imageData;
              };

              if (typeof originalGetImageData === 'function') {
                try {
                  Object.defineProperty(contextProto, 'getImageData', {
                    configurable: true,
                    writable: true,
                    value: function () {
                      return perturb(originalGetImageData.apply(this, arguments));
                    }
                  });
                } catch (_) {}
              }

              const protectedCopy = (source) => {
                try {
                  if (!source || !source.width || !source.height ||
                      source.width * source.height > 1048576) return null;
                  const copy = document.createElement('canvas');
                  copy.width = source.width;
                  copy.height = source.height;
                  const context = copy.getContext('2d', { willReadFrequently: true });
                  if (!context) return null;
                  originalDrawImage.call(context, source, 0, 0);
                  const imageData = originalGetImageData.call(
                    context, 0, 0, copy.width, copy.height
                  );
                  perturb(imageData);
                  originalPutImageData.call(context, imageData, 0, 0);
                  return copy;
                } catch (_) {
                  // Preserve the browser's native behaviour for tainted or unsupported canvases.
                  return null;
                }
              };

              if (typeof originalToDataURL === 'function') {
                try {
                  Object.defineProperty(canvasProto, 'toDataURL', {
                    configurable: true,
                    writable: true,
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
                    configurable: true,
                    writable: true,
                    value: function (callback) {
                      const args = Array.prototype.slice.call(arguments);
                      const copy = protectedCopy(this);
                      return originalToBlob.apply(copy || this, args);
                    }
                  });
                } catch (_) {}
              }
            }
          } catch (_) {
            // Never stop a website from loading if one protection cannot be applied.
          }
        })();
        """;
}
