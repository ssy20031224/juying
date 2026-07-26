/**
 * JS Engine — executes remote_sources/*.js scripts in Node.js vm sandbox.
 * Same logic used by the Lanerc Android app (QuickJS), now running on our Node backend.
 */
import { createHash, createCipheriv, createDecipheriv, publicEncrypt, privateDecrypt, constants, randomBytes } from "node:crypto";
import { inflateSync } from "node:zlib";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import vm from "node:vm";
import fs from "node:fs";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
function findSourceDir(): string {
  if (process.env.SOURCE_DIR && fs.existsSync(process.env.SOURCE_DIR)) {
    return process.env.SOURCE_DIR.replace(/^\/([A-Za-z]:[/\\])/, "$1");
  }
  const candidates = [
    path.resolve(__dirname, "..", "..", "..", "remote_sources"),
    path.resolve(process.cwd(), "..", "remote_sources"),
    path.resolve(__dirname, "..", "..", "remote_sources"),
    path.resolve(process.cwd(), "remote_sources"),
  ].map((p) => p.replace(/^\/([A-Za-z]:[/\\])/, "$1"));

  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return candidates[0];
}
const SOURCE_DIR = findSourceDir();

// ── per-source persistent sandbox (keeps global state like token, cache)
const sandboxes = new Map<string, { context: vm.Context; exports: Record<string, (...args: unknown[]) => string> }>();

// ── in-memory storage (per-source, like Android SharedPreferences)
const storage = new Map<string, Map<string, string>>();

function sourceStore(key: string): Map<string, string> {
  let store = storage.get(key);
  if (!store) { store = new Map(); storage.set(key, store); }
  return store;
}

// ── crypto helpers ──
function aesCipher(mode: string, keyBuf: Buffer, ivBuf: Buffer, encrypt: boolean): (data: Buffer) => Buffer {
  if (mode === "ECB") {
    return (data: Buffer) => {
      const cipher = encrypt
        ? createCipheriv("aes-128-ecb", keyBuf, Buffer.alloc(0))
        : createDecipheriv("aes-128-ecb", keyBuf, Buffer.alloc(0));
      cipher.setAutoPadding(true);
      return Buffer.concat([cipher.update(data), cipher.final()]);
    };
  }
  const algo = `aes-${keyBuf.length * 8}-${mode.toLowerCase()}`;
  return (data: Buffer) => {
    const cipher = encrypt
      ? createCipheriv(algo, keyBuf, ivBuf)
      : createDecipheriv(algo, keyBuf, ivBuf);
    if (mode === "GCM") {
      (cipher as ReturnType<typeof createDecipheriv>).setAuthTag(data.subarray(data.length - 16));
      return Buffer.concat([(cipher as ReturnType<typeof createDecipheriv>).update(data.subarray(0, data.length - 16)), (cipher as ReturnType<typeof createDecipheriv>).final()]);
    }
    cipher.setAutoPadding(true);
    return Buffer.concat([cipher.update(data), cipher.final()]);
  };
}

function resolveBytes(value: string, fmt: string): Buffer {
  if (fmt === "hex") return Buffer.from(value, "hex");
  if (fmt === "base64") return Buffer.from(value, "base64");
  return Buffer.from(value, "utf8");
}

function encodeBytes(buf: Buffer, fmt: string): string {
  if (fmt === "hex") return buf.toString("hex");
  if (fmt === "base64") return buf.toString("base64");
  return buf.toString("utf8");
}

// ── build host API for a source ──
function buildHost(sourceKey: string): Record<string, unknown> {
  const store = sourceStore(sourceKey);

  // Dynamic require to avoid Vite SSR bundling issues with sync-request
  const _require = createRequire(import.meta.url);
  const syncRequest = _require("sync-request") as (method: string, url: string, opts?: Record<string, unknown>) => { getBody(enc: string): string };
  const req = (syncRequest as { default?: typeof syncRequest }).default || syncRequest;

  function syncGet(urlStr: string, opts?: string): string {
    const headers: Record<string, string> = { "User-Agent": "okhttp/3.15" };
    let timeoutMs = 15000;
    if (opts) {
      try {
        const parsed = JSON.parse(opts);
        if (parsed.headers) {
          for (const [k, v] of Object.entries(parsed.headers)) headers[k] = String(v);
        }
        if (parsed.timeout) timeoutMs = Number(parsed.timeout) || 15000;
        if (parsed.ua === "chrome") headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36";
        if (parsed.ua === "iphone") headers["User-Agent"] = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1";
      } catch { /* ignore */ }
    }
    try {
      const res = req("GET", urlStr, { headers, timeout: timeoutMs, maxRetries: 0, followRedirects: true });
      return res.getBody("utf8");
    } catch { return ""; }
  }

  function syncPost(urlStr: string, bodyStr: string, opts?: string): string {
    const headers: Record<string, string> = { "User-Agent": "okhttp/3.15", "Content-Type": "application/x-www-form-urlencoded" };
    let timeoutMs = 15000;
    if (opts) {
      try {
        const parsed = JSON.parse(opts);
        if (parsed.headers) {
          for (const [k, v] of Object.entries(parsed.headers)) headers[k] = String(v);
        }
        if (parsed.timeout) timeoutMs = Number(parsed.timeout) || 15000;
        if (parsed.ua === "chrome") headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0 Safari/537.36";
        if (parsed.ua === "iphone") headers["User-Agent"] = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1";
      } catch { /* ignore */ }
    }
    // Only set default Content-Type if none was explicitly provided
    const hasContentType = Object.keys(headers).some((k) => k.toLowerCase() === "content-type");
    if (!hasContentType) headers["Content-Type"] = "application/x-www-form-urlencoded";
    try {
      const res = req("POST", urlStr, { headers, body: bodyStr, timeout: timeoutMs, maxRetries: 0, followRedirects: true });
      return res.getBody("utf8");
    } catch { return ""; }
  }

  const host: Record<string, unknown> = {
    request: syncGet,
    post: syncPost,

    // ── crypto primitives ──
    md5: (v: string) => createHash("md5").update(v).digest("hex"),
    sha1: (v: string) => createHash("sha1").update(v).digest("hex"),
    sha256: (v: string) => createHash("sha256").update(v).digest("hex"),
    base64Decode: (v: string) => { try { return Buffer.from(v, "base64").toString("binary"); } catch { return ""; } },
    timestamp: () => Date.now(),
    log: (msg: string) => { /* console.log(`[${sourceKey}]`, msg); */ },

    // ── URI ──
    encodeUri: (v: string) => { try { return encodeURIComponent(v); } catch { return v; } },
    decodeUri: (v: string) => { try { return decodeURIComponent(v); } catch { return v; } },

    // ── JSON ──
    parseJson: (v: string) => { try { return JSON.parse(v); } catch { return v; } },

    // ── Regex helpers (used by many sources for HTML parsing) ──
    match: (html: string, pattern: string, group?: number) => {
      try {
        const re = new RegExp(pattern);
        const m = re.exec(html);
        if (!m) return "";
        if (group !== undefined) return m[group] || "";
        return m[0] || "";
      } catch { return ""; }
    },
    matchAll: (html: string, pattern: string) => {
      try {
        const re = new RegExp(pattern, "g");
        return JSON.stringify([...html.matchAll(re)].map(m => [...m]));
      } catch { return "[]"; }
    },

    // ── Storage (in-memory, per-source, survives across requests) ──
    getItem: (key: string, defaultValue: string) => store.get(key) ?? defaultValue ?? "",
    setItem: (key: string, value: string) => { store.set(key, value); },

    // ── static sniff fallback ──
    // A full browser/WebView sniffer is intentionally outside the Node
    // sandbox, but many player pages expose an m3u8/mp4 URL in HTML/JSON.
    sniffMedia: (url: string, opts?: Record<string, unknown>) => {
      const options = opts || {};
      const html = syncGet(url, JSON.stringify(options));
      const candidates = new Set<string>();
      const absolute = /https?:\/\/[^"'\\s<>]+(?:m3u8|mp4|m4v|mpd)(?:\?[^"'\\s<>]*)?/gi;
      for (const match of html.matchAll(absolute)) candidates.add(match[0].replace(/[),;]+$/, ""));
      const quoted = /["']([^"']+(?:m3u8|mp4|m4v|mpd)(?:\?[^"']*)?)["']/gi;
      for (const match of html.matchAll(quoted)) {
        try { candidates.add(new URL(match[1], url).toString()); } catch { /* ignore */ }
      }
      const picked = candidates.values().next().value as string | undefined;
      if (!picked) return null;
      return {
        ok: true,
        url: picked,
        referer: (options.referer || options.referrer || url) as string,
        headers: options.headers || {},
      };
    },
    sniffAllMedia: (url: string, opts?: Record<string, unknown>) => {
      const one = (host.sniffMedia as (u: string, o?: Record<string, unknown>) => unknown)(url, opts);
      return one ? [one] : [];
    },

    // ── crypto sub-object (matches QuickJS crypto API) ──
    crypto: {
      aes: {
        encrypt: (plain: string, key: string, opts: Record<string, string>) => {
          const mode = (opts.mode || "ECB").toUpperCase();
          const keyFmt = opts.keyFormat || "utf8";
          const ivFmt = opts.ivFormat || "utf8";
          const inputFmt = opts.input || "utf8";
          const outputFmt = opts.output || "base64";
          const keyBuf = resolveBytes(key, keyFmt);
          let ivBuf: Buffer;
          if (mode === "ECB") {
            ivBuf = Buffer.alloc(0);
          } else if (mode === "GCM") {
            ivBuf = resolveBytes(opts.iv || "", ivFmt);
            if (ivBuf.length < 12) ivBuf = Buffer.concat([ivBuf, randomBytes(12 - ivBuf.length)]);
            ivBuf = ivBuf.subarray(0, 12);
          } else {
            ivBuf = resolveBytes(opts.iv || "", ivFmt);
          }
          const data = resolveBytes(plain, inputFmt);
          const fn = aesCipher(mode, keyBuf, ivBuf, true);
          const result = fn(data);
          if (mode === "GCM") {
            const tag = (fn as unknown as { getAuthTag: () => Buffer }).getAuthTag?.() || Buffer.alloc(16);
            return encodeBytes(Buffer.concat([ivBuf, result, tag]), outputFmt);
          }
          return encodeBytes(result, outputFmt);
        },
        decrypt: (cipher: string, key: string, opts: Record<string, string>) => {
          const mode = (opts.mode || "ECB").toUpperCase();
          const keyFmt = opts.keyFormat || "utf8";
          const ivFmt = opts.ivFormat || "utf8";
          const inputFmt = opts.input || "base64";
          const outputFmt = opts.output || "utf8";
          const keyBuf = resolveBytes(key, keyFmt);
          let ivBuf: Buffer;
          let data = resolveBytes(cipher, inputFmt);
          if (mode === "GCM") {
            ivBuf = data.subarray(0, 12);
            data = data.subarray(12);
          } else if (mode === "ECB") {
            ivBuf = Buffer.alloc(0);
          } else {
            ivBuf = resolveBytes(opts.iv || "", ivFmt);
          }
          const fn = aesCipher(mode, keyBuf, ivBuf, false);
          return encodeBytes(fn(data), outputFmt);
        },
      },
      rsa: {
        encrypt: (plain: string, key: string, opts: Record<string, string>) => {
          const padding = (opts.padding || "PKCS1") === "PKCS1" ? constants.RSA_PKCS1_PADDING : constants.RSA_PKCS1_OAEP_PADDING;
          const inputFmt = opts.input || "utf8";
          const outputFmt = opts.output || "base64";
          const data = resolveBytes(plain, inputFmt);
          const pubKey = `-----BEGIN PUBLIC KEY-----\n${(key.match(/.{1,64}/g) || [key]).join("\n")}\n-----END PUBLIC KEY-----`;
          return encodeBytes(publicEncrypt({ key: pubKey, padding }, data), outputFmt);
        },
        decrypt: (cipher: string, key: string, opts: Record<string, string>) => {
          const padding = (opts.padding || "PKCS1") === "PKCS1" ? constants.RSA_PKCS1_PADDING : constants.RSA_PKCS1_OAEP_PADDING;
          const inputFmt = opts.input || "base64";
          const outputFmt = opts.output || "utf8";
          const data = resolveBytes(cipher, inputFmt);
          const privKey = `-----BEGIN PRIVATE KEY-----\n${(key.match(/.{1,64}/g) || [key]).join("\n")}\n-----END PRIVATE KEY-----`;
          return encodeBytes(privateDecrypt({ key: privKey, padding }, data), outputFmt);
        },
      },
      hex: {
        encode: (data: string, opts: Record<string, string>) => encodeBytes(resolveBytes(data, opts.input || "utf8"), "hex"),
        decode: (data: string, opts: Record<string, string>) => encodeBytes(resolveBytes(data, "hex"), opts.output || "utf8"),
      },
      base64: {
        encode: (data: string, opts: Record<string, string>) => encodeBytes(resolveBytes(data, opts.input || "utf8"), "base64"),
        decode: (data: string, opts: Record<string, string>) => encodeBytes(resolveBytes(data, "base64"), opts.output || "utf8"),
      },
      inflate: (data: string, opts: Record<string, string>) => {
        const buf = resolveBytes(data, opts.input || "base64");
        try { return inflateSync(buf).toString(opts.output as BufferEncoding || "utf8"); } catch { return ""; }
      },
    },
  };

  return host;
}

// ── Load and execute a JS source script ──
export function loadSource(key: string, localFile: string): Record<string, (...args: unknown[]) => string> {
  const cached = sandboxes.get(key);
  if (cached) return cached.exports;

  const filePath = path.join(SOURCE_DIR, localFile);
  if (!fs.existsSync(filePath)) throw new Error(`Source file not found: ${filePath}`);

  const code = fs.readFileSync(filePath, "utf8");
  const host = buildHost(key);
  const context = vm.createContext({ ...host, ext: {}, UA: {} });
  const script = new vm.Script(code, { filename: localFile });
  script.runInContext(context);

  const exports: Record<string, (...args: unknown[]) => string> = {};
  for (const name of ["search", "searchFiltered", "categories", "homeSections", "detail", "play"]) {
    const fn = (context as Record<string, unknown>)[name];
    if (typeof fn === "function") {
      exports[name] = (...args: unknown[]) => {
        const result = fn(...args);
        return typeof result === "string" ? result : JSON.stringify(result);
      };
    }
  }

  sandboxes.set(key, { context, exports });
  return exports;
}

// ── Invalidate one source's sandbox (force reload on next call) ──
export function invalidateSource(key: string): void {
  sandboxes.delete(key);
  storage.delete(key);
}
