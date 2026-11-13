// Opens a page's #Encrypted blocks with WebCrypto, the way the web app does,
// to check what the phone wrote. Prints the decrypted children.
//   deno run -A tools/decrypt-check.ts http://127.0.0.1:3970 <server pw> <encryption pw> <page>
const [BASE, SERVER_PW, ENC_PW, PAGE] = Deno.args;
const auth = { authorization: "Basic " + btoa("check:" + SERVER_PW) };
const get = async (p: string) => (await fetch(BASE + p, { headers: auth })).json();
const unb64 = (s: string) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));
const cfg = (await get("/api/encryption")).encryption;
const base = await crypto.subtle.importKey("raw", new TextEncoder().encode(ENC_PW), "PBKDF2", false, ["deriveKey"]);
const kek = await crypto.subtle.deriveKey({ name: "PBKDF2", hash: "SHA-256", salt: unb64(cfg.salt), iterations: cfg.iterations }, base, { name: "AES-GCM", length: 256 }, false, ["decrypt"]);
const raw = await crypto.subtle.decrypt({ name: "AES-GCM", iv: unb64(cfg.pw_iv) }, kek, unb64(cfg.pw_data));
const key = await crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["decrypt"]);
const page = await get(`/api/page?name=${encodeURIComponent(PAGE)}`);
let plaintextOnServer = false;
for (const b of page.blocks) {
  const m = /encrypted:: (v1:[^:\s]+:[^:\s]+)/.exec(b.content);
  if (!m) { if (/hunter2/.test(b.content)) plaintextOnServer = true; continue; }
  const [, iv, ct] = m[1].split(":");
  const text = new TextDecoder().decode(await crypto.subtle.decrypt({ name: "AES-GCM", iv: unb64(iv) }, key, unb64(ct)));
  console.log(`--- ${b.content.split("\n")[0]}\n${text}`);
}
console.log("plaintext on server:", plaintextOnServer);
