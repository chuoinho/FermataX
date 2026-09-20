// Execute the production click gate: a selection must not reach YouTube's player first.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname,
  '../main/java/me/aap/fermata/addon/web/yt/YoutubeSelectionRouting.java'), 'utf8');
const listener = source.match(/state\.intentListener = function\(e\) \{([\s\S]*?)\n\s*\};/)[1];
for (const [url, intercept] of [
  ['https://www.youtube.com/watch?v=abc123', true],
  ['https://m.youtube.com/shorts/abc123', true],
  ['https://www.youtube.com/results?search_query=test', false],
  ['https://example.com/watch?v=abc123', false],
]) {
  const events = [];
  const e = {
    isTrusted: true,
    composedPath: () => [{ tagName: 'A', href: url }],
    preventDefault() { this.prevented = true; },
    stopPropagation() { this.stopped = true; },
    stopImmediatePropagation() { this.immediate = true; },
  };
  vm.runInNewContext('(function(e){' + listener.replace('%d', '42') + '})(e)', {
    e, URL, location: { href: 'https://www.youtube.com/' },
    event: (code, href) => events.push({code, href}),
  });
  assert.equal(!!e.prevented, intercept, url + ': phone navigation must wait for target capture');
  assert.equal(!!e.immediate, intercept, url + ': player click handlers must not prepare locally');
  assert.deepEqual(events, intercept ? [{code: 42, href: url}] : []);
}
console.log('YouTube selection click gate: 4 cases passed');
