// Acceptance oracle for task "CarStream vs FermataX speedo comparison report".
// Authored by the orchestrator; protected from the worker.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const root = process.cwd();
const REPORT = 'audit_reports/feat-aa-glass-theme/2026-10-23-carstream-speedo-comparison.md';
const reportPath = path.join(root, REPORT);
const read = () => fs.readFileSync(reportPath, 'utf8');

const HEADINGS = [
  /^## 1\. Nguồn và cách xuất/m,
  /^## 2\. Logic speed của CarStream/m,
  /^## 3\. Logic speed của FermataX/m,
  /^## 4\. So sánh/m,
  /^## 5\. Khi nào hiện "--" hoặc số nhảy/m,
  /^## 6\. Điểm CarStream làm tốt hơn và rủi ro nếu áp dụng/m,
  /^## 7\. Giới hạn/m,
];

function section(text, n) {
  const start = text.search(HEADINGS[n - 1]);
  if (start < 0) return '';
  const rest = text.slice(start + 3);
  const next = rest.search(/^## \d+\./m);
  return next < 0 ? text.slice(start) : text.slice(start, start + 3 + next);
}

test('AC-01 report exists', () => {
  assert.ok(fs.existsSync(reportPath), `missing ${REPORT}`);
});

test('AC-01 has the 7 required sections in order', () => {
  const text = read();
  let last = -1;
  for (const h of HEADINGS) {
    const at = text.search(h);
    assert.ok(at >= 0, `missing heading ${h}`);
    assert.ok(at > last, `heading out of order ${h}`);
    last = at;
  }
});

test('AC-02 source section records version and SHA-256', () => {
  const s1 = section(read(), 1);
  assert.match(s1, /1\.24\.93/);
  assert.match(s1, /\b733\b/);
  assert.match(s1, /\b[0-9a-fA-F]{64}\b/, 'SHA-256 of the pulled base.apk');
});

test('AC-03 cites at least 5 decompiled CarStream locations', () => {
  const s2 = section(read(), 2);
  const refs = new Set((s2.match(/[\w$./-]+\.(?:java|kt):\d+/g) || [])
    .filter(r => !r.startsWith('fermata/') && !r.startsWith('modules/')));
  assert.ok(refs.size >= 5, `only ${refs.size} CarStream references: ${[...refs].join(', ')}`);
});

test('AC-03 cites at least 5 real FermataX locations', () => {
  const s3 = section(read(), 3);
  const refs = [...new Set(s3.match(/fermata\/src\/main\/java\/[\w/$.-]+\.java:\d+/g) || [])];
  const valid = refs.filter(r => {
    const [file, line] = r.split(':');
    const abs = path.join(root, file);
    if (!fs.existsSync(abs)) return false;
    return Number(line) <= fs.readFileSync(abs, 'utf8').split('\n').length;
  });
  assert.ok(valid.length >= 5, `only ${valid.length} valid FermataX references; invalid: ${refs.filter(r => !valid.includes(r)).join(', ')}`);
});

test('AC-04 section 4 has a CarStream/FermataX comparison table', () => {
  const s4 = section(read(), 4);
  const header = s4.split('\n').find(l => /^\s*\|.*CarStream.*\|.*FermataX.*\|/i.test(l) || /^\s*\|.*FermataX.*\|.*CarStream.*\|/i.test(l));
  assert.ok(header, 'table header with CarStream and FermataX columns');
  const rows = s4.split('\n').filter(l => /^\s*\|/.test(l) && !/^\s*\|\s*:?-{3,}/.test(l));
  assert.ok(rows.length >= 9, `table needs at least 8 aspect rows, got ${rows.length - 1}`);
});

test('AC-04 covers the required topics', () => {
  const text = read().toLowerCase();
  const topics = {
    'speed source': /getspeed|hasspeed/,
    accuracy: /accuracy|độ chính xác/,
    smoothing: /làm mượt|smoothing|\bema\b|kalman|bộ lọc/,
    'stale/timeout': /stale|timeout|hết hạn|quá hạn/,
    'GPS loss': /hầm|tunnel|mất gps|mất tín hiệu/,
    'dash display': /"--"|`--`/,
    'UI update rate': /tần suất|\bhz\b|interval|fps/,
  };
  for (const [name, re] of Object.entries(topics)) assert.match(text, re, `topic not covered: ${name}`);
});

test('AC-05 no copied source: code blocks are at most 15 lines', () => {
  const blocks = read().match(/```[\s\S]*?```/g) || [];
  for (const b of blocks) {
    const lines = b.split('\n').length - 2;
    assert.ok(lines <= 15, `code block of ${lines} lines`);
  }
});

test('AC-05 no CarStream source in the repository', () => {
  const tracked = execFileSync('git', ['ls-files', '--cached', '--others', '--exclude-standard'],
    { cwd: root, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024 }).split('\n').filter(Boolean);
  for (const f of tracked) {
    assert.ok(!/\.(smali|dex|apk)$/i.test(f), `binary or smali in repo: ${f}`);
    if (!/\.(java|kt)$/.test(f)) continue;
    const head = fs.readFileSync(path.join(root, f), 'utf8').slice(0, 400);
    assert.ok(!/^\s*package\s+com\.carstream/m.test(head), `CarStream source in repo: ${f}`);
  }
});
