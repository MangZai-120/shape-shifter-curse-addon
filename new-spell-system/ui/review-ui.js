'use strict';
// Local design QA. Uses a separate headless browser profile, never the user's browser profile.
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
const { spawn } = require('node:child_process');
const assert = require('node:assert/strict');
const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
const root = process.cwd();
const ui = path.join(root, 'new-spell-system', 'ui');
const output = path.join(ui, 'previews');
const profile = path.join(root, 'build', 'neomagic-ui-review-2026-10-27', 'browser-cdp-' + Date.now());
const browser = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
fs.mkdirSync(output, { recursive: true });
fs.mkdirSync(profile, { recursive: true });
const child = spawn(browser, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--disable-background-networking', '--allow-file-access-from-files', '--remote-debugging-port=0',
  '--user-data-dir=' + profile, 'about:blank'], { windowsHide: true, stdio: 'ignore' });
const pending = new Map(), errors = [], checks = [];
let socket, serial = 0;
function call(method, params = {}) {
  return new Promise((resolve, reject) => {
    const id = ++serial;
    const timeout = setTimeout(() => { pending.delete(id); reject(new Error('CDP timeout: ' + method)); }, 15000);
    pending.set(id, { resolve, reject, timeout }); socket.send(JSON.stringify({ id, method, params }));
  });
}
async function evaluate(expression) {
  const response = await call('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (response.exceptionDetails) throw new Error(JSON.stringify(response.exceptionDetails));
  return response.result.value;
}
async function navigate(query) {
  await call('Page.navigate', { url: pathToFileURL(path.join(ui, '\u7814\u7a76\u754c\u9762\u539f\u578b.html')).href + query });
  for (let i = 0; i < 100; i++) {
    if (await evaluate('document.readyState === "complete" && !!window.NeoMagicDesign && !!document.getElementById("diagram").children.length')) return;
    await delay(80);
  }
  throw new Error('UI did not finish loading.');
}
async function shot(name) {
  await delay(150);
  const response = await call('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
  fs.writeFileSync(path.join(output, name + '.png'), Buffer.from(response.data, 'base64'));
}
async function geometry(label) {
  const sizes = await evaluate('({window: innerWidth, document: document.documentElement.scrollWidth, workbench: document.querySelector(".workbench").getBoundingClientRect().right})');
  assert.ok(sizes.document <= sizes.window + 1, label + ' has horizontal overflow: ' + JSON.stringify(sizes));
  checks.push({ name: label, ...sizes });
  if (label.startsWith('craft')) {
    const inner = await evaluate('({diagramBottom: document.getElementById("diagram").getBoundingClientRect().bottom, budgetTop: document.querySelector(".budget-row").getBoundingClientRect().top, budgetBottom: document.querySelector(".budget-row").getBoundingClientRect().bottom, actionsTop: document.querySelector(".blueprint-actions").getBoundingClientRect().top})');
    assert.ok(inner.diagramBottom <= inner.budgetTop + 1, label + ' diagram overlaps budget summary: ' + JSON.stringify(inner));
    assert.ok(inner.budgetBottom <= inner.actionsTop, label + ' summary overlaps actions');
    checks.push({ name: label + ' internal sections do not overlap', ...inner });
  }
}
(async () => {
  const portFile = path.join(profile, 'DevToolsActivePort');
  let port;
  for (let i = 0; i < 180; i++) {
    if (fs.existsSync(portFile)) { port = fs.readFileSync(portFile, 'utf8').split(/\r?\n/)[0]; break; }
    if (child.exitCode !== null) throw new Error('Browser exited before CDP started: ' + child.exitCode);
    await delay(100);
  }
  assert.ok(port, 'No DevTools port.');
  const targets = await (await fetch('http://127.0.0.1:' + port + '/json/list')).json();
  const target = targets.find(value => value.type === 'page');
  assert.ok(target, 'No page target.');
  socket = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
  socket.addEventListener('message', event => {
    const message = JSON.parse(String(event.data));
    if (message.method === 'Runtime.exceptionThrown') errors.push(message.params.exceptionDetails);
    const waiting = pending.get(message.id);
    if (waiting) { clearTimeout(waiting.timeout); pending.delete(message.id); message.error ? waiting.reject(new Error(JSON.stringify(message.error))) : waiting.resolve(message.result); }
  });
  await call('Page.enable'); await call('Runtime.enable');
  await call('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1024, deviceScaleFactor: 1, mobile: false });
  await navigate('?view=craft');
  await evaluate('localStorage.clear(); location.reload();');
  await delay(350);
  await geometry('craft-desktop'); await shot('01-craft');
  await evaluate('document.getElementById("save-baseline").click(); document.querySelector("[data-structure=split]").click(); document.getElementById("run").click();');
  assert.equal(await evaluate('document.getElementById("page-experiment").hidden'), false);
  assert.equal(await evaluate('document.querySelectorAll(".timeline tbody tr").length'), 5);
  assert.equal(await evaluate('document.getElementById("record-count").textContent'), '1');
  checks.push({ name: 'save baseline, change structure, run, inspect five checkpoints', passed: true });
  await evaluate('document.querySelector("[data-page=craft]").click(); document.getElementById("form").value="axolotl"; document.getElementById("form").dispatchEvent(new Event("input"));');
  assert.equal(await evaluate('document.getElementById("burden-value").textContent'), '0.0');
  assert.equal(await evaluate('document.getElementById("safety-value").textContent'), '80');
  checks.push({ name: 'form change resets the single burden and changes safety', passed: true });
  await navigate('?view=observe'); await geometry('observe-desktop'); await shot('02-observe');
  await navigate('?view=experiment&example=1'); await geometry('experiment-desktop'); await shot('03-experiment');
  await evaluate('document.querySelector("[data-page=notes]").click(); document.getElementById("hypothesis").value="同样的总作用量分成三路，每路作用减少，覆盖增加。"; document.getElementById("conclusion").value="保持形态、起始负担、作用规模与准备方式一致后，比较直通和分流。当前只有演算记录，仍需在游戏中测量实际命中和伤害。"; document.getElementById("save-notes").click();');
  await delay(3600); await geometry('notes-desktop'); await shot('04-notes');
  await navigate('?view=notes');
  assert.ok((await evaluate('document.getElementById("hypothesis").value')).includes('三路'));
  checks.push({ name: 'notes persist on reload', passed: true });
  await call('Emulation.setDeviceMetricsOverride', { width: 960, height: 1000, deviceScaleFactor: 1, mobile: false });
  await navigate('?view=craft'); await geometry('craft-narrow');
  assert.equal(errors.length, 0, 'UI runtime exception(s).');
  fs.writeFileSync(path.join(output, 'review.json'), JSON.stringify({ scope: 'Local HTML design prototype, not Minecraft gameplay', checks, runtimeErrors: errors, screenshots: ['01-craft.png', '02-observe.png', '03-experiment.png', '04-notes.png'] }, null, 2) + '\n');
  console.log('UI review passed:', checks.length, 'checks; 4 screenshots; 0 runtime errors.');
  await call('Browser.close').catch(() => {});
})().catch(error => { console.error(error.stack || String(error)); process.exitCode = 1; }).finally(() => {
  if (socket) socket.close();
  for (const value of pending.values()) clearTimeout(value.timeout);
  pending.clear(); if (child.exitCode === null) child.kill();
});
