// Executes the production origin-scoped control shim; no network or real media playback.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname,
  '../main/java/me/aap/fermata/addon/web/stremio/StremioWebMediaSessionBridge.java'), 'utf8');
const shim = source.match(/String\.format\(Locale\.ROOT, """([\s\S]*?)""", generation\)/)[1]
  .replaceAll('%d', '7');
// The command's Java concatenation is also JavaScript; substitute only the JSON quoting API.
const commandExpression = source.match(/private static String dispatchSource\([^)]*\) \{\s*return ([\s\S]*?);\r?\n\t\}/)[1];
const command = new Function('action', 'generation', 'session',
  'return ' + commandExpression.replace('JSONObject.quote(session)', 'JSON.stringify(session)'));
function fixture(nativeSession) {
  const messages = [], callbacks = {}, calls = [];
  const context = { navigator: {}, Math, Date, addEventListener: (name, callback) => callbacks[name] = callback };
  if (nativeSession) {
    // A native-shaped accessor fixture exercises the observation branch, not another shim.
    let state = 'none', metadata = null;
    context.navigator.mediaSession = {
      get playbackState() { return state; },
      set playbackState(value) { state = value; },
      get metadata() { return metadata; },
      set metadata(value) { metadata = value; },
      setActionHandler() {},
    };
  }
  context.window = context;
  context.top = context;
  context.fermataStremioControl = {postMessage: value => messages.push(JSON.parse(value))};
  vm.createContext(context);
  vm.runInContext(shim, context);
  const session = messages.find(message => message.t === 'READY').s;
  const media = context.navigator.mediaSession;
  for (const action of ['play', 'pause', 'nexttrack']) media.setActionHandler(action, () => calls.push(action));
  return { media, session, callbacks, calls, bridge: {
    dispatch: (action, generation, session) => vm.runInContext(command(action, generation, session), context),
  }};
}
const cases = {
  'READY and handlers are not PLAYING': f => {
    assert.equal(f.bridge.dispatch('play', 7, f.session), false);
    assert.deepEqual(f.calls, []);
  },
  'stale document cannot control the new player': f => {
    f.media.playbackState = 'paused';
    assert.equal(f.bridge.dispatch('play', 6, f.session), false);
    assert.deepEqual(f.calls, []);
  },
  'stale session cannot control the new player': f => {
    f.media.playbackState = 'paused';
    assert.equal(f.bridge.dispatch('play', 7, 'old-session'), false);
    assert.deepEqual(f.calls, []);
  },
  'play requires paused state, never a playing toggle': f => {
    f.media.playbackState = 'paused';
    assert.equal(f.bridge.dispatch('play', 7, f.session), true);
    f.media.playbackState = 'playing';
    assert.equal(f.bridge.dispatch('play', 7, f.session), false);
    assert.deepEqual(f.calls, ['play']);
  },
  'pagehide invalidates queued controls': f => {
    f.media.playbackState = 'playing';
    f.callbacks.pagehide();
    assert.equal(f.bridge.dispatch('nexttrack', 7, f.session), false);
    assert.deepEqual(f.calls, []);
  },
  'next remains the current player handler': f => {
    f.media.playbackState = 'playing';
    assert.equal(f.bridge.dispatch('nexttrack', 7, f.session), true);
    assert.deepEqual(f.calls, ['nexttrack']);
  },
};
let failures = 0;
for (const nativeSession of [false, true]) {
  for (const [name, run] of Object.entries(cases)) {
    const label = (nativeSession ? 'native: ' : 'fallback: ') + name;
    try { run(fixture(nativeSession)); console.log('PASS ' + label); }
    catch (error) { failures++; console.error('FAIL ' + label + ': ' + error.message); }
  }
}
assert.equal(failures, 0, 'Stremio control safety cases');
