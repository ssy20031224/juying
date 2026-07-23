const fs = require("fs");
const vm = require("vm");
const crypto = require("crypto");
const zlib = require("zlib");
const cp = require("child_process");

const input = JSON.parse(fs.readFileSync(0, "utf8"));
const parseJson = (v) => { try { return JSON.parse(String(v || "")); } catch { return null; } };
const http = (url, opts, method, body) => {
  const o = parseJson(opts) || {};
  const args = ["-sS", "-L", "--max-time", "20", "--connect-timeout", "8"];
  for (const [k, v] of Object.entries(o.headers || {})) args.push("-H", `${k}: ${v}`);
  if (method === "POST") args.push("-X", "POST", "--data-raw", body || "");
  args.push(url);
  try { return cp.execFileSync("curl.exe", args, { encoding: "utf8", maxBuffer: 16 * 1024 * 1024, windowsHide: true }); } catch { return ""; }
};
const aes = (v, key, opt, decrypt) => {
  opt = opt || {};
  const iv = Buffer.from(String(opt.iv || key), opt.ivFormat === "hex" ? "hex" : "utf8");
  const alg = `aes-${Buffer.byteLength(key) * 8}-cbc`;
  const c = decrypt ? crypto.createDecipheriv(alg, Buffer.from(key), iv) : crypto.createCipheriv(alg, Buffer.from(key), iv);
  const b = Buffer.from(String(v), opt.input === "hex" ? "hex" : opt.input === "base64" ? "base64" : "utf8");
  const out = Buffer.concat([c.update(b), c.final()]);
  return opt.output === "hex" ? out.toString("hex") : opt.output === "utf8" ? out.toString("utf8") : out.toString("base64");
};
const pem = (value, label) => {
  if (String(value).includes("BEGIN")) return String(value);
  const b64 = Buffer.from(String(value), "base64").toString("base64");
  return `-----BEGIN ${label}-----\n${b64.match(/.{1,64}/g).join("\n")}\n-----END ${label}-----`;
};
const sandbox = {
  ext: {}, JSON, Math, Date, RegExp, String, Number, Boolean, Array, Object, parseInt, parseFloat, isNaN,
  encodeURIComponent, decodeURIComponent, encodeUri: encodeURIComponent, parseJson,
  timestamp: () => Date.now(), log: () => {},
  md5: (v) => crypto.createHash("md5").update(String(v)).digest("hex"),
  sha256: (v) => crypto.createHash("sha256").update(String(v)).digest("hex"),
  match: (s, p, g = 1) => new RegExp(p).exec(s || "")?.[g] || "",
  matchAll: (s, p) => [...(s || "").matchAll(new RegExp(p, "g"))],
  request: (u, o) => http(u, o, "GET"),
  post: (u, b, o) => http(u, o, "POST", b),
  crypto: {
    aes: { encrypt: (v, k, o) => aes(v, k, o, false), decrypt: (v, k, o) => aes(v, k, o, true) },
    rsa: {
      encrypt: (v, k, o = {}) => crypto.publicEncrypt({ key: pem(k, "PUBLIC KEY"), padding: crypto.constants.RSA_PKCS1_PADDING }, Buffer.from(v)).toString(o.output === "hex" ? "hex" : "base64"),
      decrypt: (v, k, o = {}) => crypto.privateDecrypt({ key: pem(k, "PRIVATE KEY"), padding: crypto.constants.RSA_PKCS1_PADDING }, Buffer.from(v, "base64")).toString(o.output || "utf8"),
    },
    base64: { encode: (v, o) => Buffer.from(v, o.input === "hex" ? "hex" : "utf8").toString("base64"), decode: (v, o) => Buffer.from(v, "base64").toString(o.output === "hex" ? "hex" : "utf8") },
    hex: { encode: (v) => Buffer.from(v, "base64").toString("hex"), decode: (v) => Buffer.from(v, "hex").toString("base64") },
    inflate: (v, o) => zlib.inflateSync(Buffer.from(v, "base64")).toString(o.output === "utf8" ? "utf8" : "base64"),
  },
  UA: { iphone: "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1" },
};
const code = input.source || fs.readFileSync(input.scriptPath, "utf8");
const context = vm.createContext(sandbox);
new vm.Script(code, { filename: input.scriptPath }).runInContext(context, { timeout: 5000 });
let method = input.method;
if (method === "home") method = typeof context.homeSections === "function" ? "homeSections" : typeof context.homeContent === "function" ? "homeContent" : "search";
const fn = context[method];
if (typeof fn !== "function") throw new Error(`missing ${method}`);
const args = method === "search" && input.args.length === 0 ? ["", 1] : input.args;
const out = fn(...args);
process.stdout.write(typeof out === "string" ? out : JSON.stringify(out));
