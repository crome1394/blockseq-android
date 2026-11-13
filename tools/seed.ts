// Fills a throwaway Blockseq server with made-up notes for testing the app:
// pages, journals, tasks, and an #Encrypted block (encrypted exactly as the
// web app does, see web/js/crypto.js in the server repo).
//
//   deno run -A tools/seed.ts http://127.0.0.1:3970 <server password> <encryption password> [photo.jpg]
const [BASE, SERVER_PW, ENC_PW] = Deno.args;
const auth = { authorization: "Basic " + btoa("seed:" + SERVER_PW) };
const enc = new TextEncoder();
const b64 = (b: ArrayBuffer | Uint8Array) => btoa(String.fromCharCode(...new Uint8Array(b)));
const rand = (n: number) => crypto.getRandomValues(new Uint8Array(n));

async function api(method: string, path: string, body?: unknown) {
  const r = await fetch(BASE + path, { method, headers: { ...auth, "content-type": "application/json" }, body: body === undefined ? undefined : JSON.stringify(body) });
  if (!r.ok) throw new Error(`${method} ${path}: ${r.status} ${await r.text()}`);
  return r.status === 204 ? null : r.json();
}

// The encryption key, wrapped with the password (600k rounds like the web app).
const raw = rand(32);
async function derive(secret: string, salt: Uint8Array, rounds: number) {
  const base = await crypto.subtle.importKey("raw", enc.encode(secret), "PBKDF2", false, ["deriveKey"]);
  return crypto.subtle.deriveKey({ name: "PBKDF2", hash: "SHA-256", salt, iterations: rounds }, base, { name: "AES-GCM", length: 256 }, false, ["encrypt"]);
}
async function digest(prefix: string) {
  const p = enc.encode(prefix);
  const buf = new Uint8Array(p.length + raw.length);
  buf.set(p); buf.set(raw, p.length);
  return b64(await crypto.subtle.digest("SHA-256", buf));
}
const salt = rand(16), rkSalt = rand(16), iv = rand(12), rkIv = rand(12);
const pwData = await crypto.subtle.encrypt({ name: "AES-GCM", iv }, await derive(ENC_PW, salt, 600000), raw);
const rkData = await crypto.subtle.encrypt({ name: "AES-GCM", iv: rkIv }, await derive("TESTRECOVERYKEY", rkSalt, 100000), raw);
const proof = await digest("blockseq-key-proof:");
await api("PUT", "/api/encryption", {
  encryption: {
    version: 1, salt: b64(salt), iterations: 600000, pw_iv: b64(iv), pw_data: b64(pwData),
    rk_salt: b64(rkSalt), rk_iterations: 100000, rk_iv: b64(rkIv), rk_data: b64(rkData),
    check: await digest("blockseq-key-check:"),
    proof_hash: b64(await crypto.subtle.digest("SHA-256", enc.encode(proof))),
  },
});
const key = await crypto.subtle.importKey("raw", raw, "AES-GCM", false, ["encrypt"]);
const seal = async (text: string) => {
  const biv = rand(12);
  return `v1:${b64(biv)}:${b64(await crypto.subtle.encrypt({ name: "AES-GCM", iv: biv }, key, enc.encode(text)))}`;
};

let n = 0;
const id = () => `00000000-0000-4000-8000-${String(++n).padStart(12, "0")}`;
async function page(name: string, outline: [number, string][]) {
  const blocks: unknown[] = [];
  const stack: string[] = [];
  for (const [depth, content] of outline) {
    const bid = id();
    stack[depth] = bid;
    blocks.push({ id: bid, parent_id: depth ? stack[depth - 1] : null, content, collapsed: false });
  }
  await api("PUT", `/api/page?name=${encodeURIComponent(name)}`, { base_version: 0, blocks });
}
const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const ord = (d: number) => d + (d % 10 === 1 && d !== 11 ? "st" : d % 10 === 2 && d !== 12 ? "nd" : d % 10 === 3 && d !== 13 ? "rd" : "th");
const journal = (ago: number) => { const d = new Date(Date.now() - ago * 864e5); return `${MONTHS[d.getMonth()]} ${ord(d.getDate())}, ${d.getFullYear()}`; };

const secretKids = "- Wifi password: hunter2\n  id:: 6f1c2a9e-3b7d-4c55-9a10-2f4e8d7b1c03\n\t- Guest network: garden-guest\n\t  id:: 0a9b8c7d-6e5f-4a3b-8c2d-1e0f9a8b7c6d";
await page("Garden plan", [
  [0, "Spring planting for the raised beds #gardening"],
  [1, "DONE Order seed potatoes #task"],
  [1, "DOING Build the trellis for the [[Climbing beans]] #task"],
  [1, "TODO Test the soil pH in bed 3 #task"],
  [0, "Bed layout"],
  [1, "| Bed | Crop | Sown |\n|---|---|---|\n| 1 | Carrots | Mar 20 |\n| 2 | Lettuce | Mar 28 |"],
  [0, "Sensor script\n```python\ndef needs_water(r):\n    return r < 0.3\n```"],
  [0, "#+BEGIN_TIP\nMulch after the first watering.\n#+END_TIP"],
  [0, "Logins #Encrypted\nencrypted:: " + await seal(secretKids)],
]);
await page("Climbing beans", [[0, "Pole varieties against the south fence #gardening"], [0, "IN-REVIEW Compare last year's yields #task"]]);
await page("Reading list", [[0, "DOING *The Overstory* #task"], [0, "BACKLOG A field guide to garden birds #task"]]);
// A picture (made by demo-server.sh), uploaded like the web app does.
let picture = "";
const photo = Deno.args[3];
if (photo) {
  const r = await fetch(BASE + "/api/assets", { method: "POST", headers: { ...auth, "x-filename": "garden-photo.jpg" }, body: await Deno.readFile(photo) });
  picture = (await r.json()).path;
}
await page(journal(0), [
  [0, "Morning walk, then planning the beds. See [[Garden plan]]."],
  [0, "TODO Pick up stakes and twine #task"],
  ...(picture ? [[0, `The beds this morning ![garden-photo](${picture}){:width 1200}`] as [number, string]] : []),
]);
await page(journal(1), [[0, "Swapped the router firmware; all good."], [0, "DONE Sharpen the pruning shears #task"]]);
console.log("seeded");
