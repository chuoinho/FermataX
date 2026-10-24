// Tests for the diagnostics report analyzer. The journals come from the golden fixtures the
// Java SpeedoDiagnosticsTest pins, so both sides agree on the event format.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import zlib from 'node:zlib';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { readZip, parseJournal, speedoVerdict, tripSummary } from './analyze.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const fixtures = path.join(here, '..', '..', 'fermata', 'src', 'test', 'resources', 'diagnostics');
const fixture = (name) => fs.readFileSync(path.join(fixtures, name), 'utf8');

// Minimal ZIP writer (deflate, no data descriptor), enough to build reports in memory.
function crc32(buf) {
  let c, crc = 0xffffffff;
  for (const b of buf) {
    c = (crc ^ b) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function makeZip(files) {
  const locals = [], centrals = [];
  let offset = 0;
  for (const [name, text] of Object.entries(files)) {
    const data = Buffer.from(text, 'utf8');
    const packed = zlib.deflateRawSync(data);
    const nameBuf = Buffer.from(name, 'utf8');
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt16LE(8, 8);
    local.writeUInt32LE(crc32(data), 14);
    local.writeUInt32LE(packed.length, 18);
    local.writeUInt32LE(data.length, 22);
    local.writeUInt16LE(nameBuf.length, 26);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt16LE(8, 10);
    central.writeUInt32LE(crc32(data), 16);
    central.writeUInt32LE(packed.length, 20);
    central.writeUInt32LE(data.length, 24);
    central.writeUInt16LE(nameBuf.length, 28);
    central.writeUInt32LE(offset, 42);
    locals.push(local, nameBuf, packed);
    centrals.push(central, nameBuf);
    offset += local.length + nameBuf.length + packed.length;
  }
  const dir = Buffer.concat(centrals);
  const end = Buffer.alloc(22);
  end.writeUInt32LE(0x06054b50, 0);
  end.writeUInt16LE(Object.keys(files).length, 8);
  end.writeUInt16LE(Object.keys(files).length, 10);
  end.writeUInt32LE(dir.length, 12);
  end.writeUInt32LE(offset, 16);
  return Buffer.concat([...locals, dir, end]);
}

test('readZip returns every entry', () => {
  const zip = readZip(makeZip({ 'a.txt': 'hello', 'journal/x.jsonl': '{}' }));
  assert.equal(zip.get('a.txt').toString(), 'hello');
  assert.equal(zip.get('journal/x.jsonl').toString(), '{}');
});

test('no delivery journal is classified as no_delivery', () => {
  const v = speedoVerdict(parseJournal(fixture('speedo-no-delivery.jsonl')), []);
  assert.equal(v.verdict, 'no_delivery');
  assert.ok(v.evidence.length > 0);
});

test('fixes without Doppler are classified as no_usable_speed', () => {
  const v = speedoVerdict(parseJournal(fixture('speedo-no-doppler.jsonl')), []);
  assert.equal(v.verdict, 'no_usable_speed');
});

test('an empty journal gives unknown', () => {
  assert.equal(speedoVerdict([], []).verdict, 'unknown');
});

test('parseJournal accepts CRLF line endings', () => {
  const crlf = fixture('speedo-no-delivery.jsonl').replace(/\r?\n/g, '\r\n');
  assert.equal(parseJournal(crlf).length, 2);
});

test('tripSummary finds gaps and the share of fixes with speed', () => {
  const rows = [
    ['L', '0', 'gps', '', '5.0', '10.0', '', '0'],
    ['L', '1000', 'gps', '10', '5.0', '', '', '0'],
    ['L', '20000', 'gps', '10', '7.0', '10.0', '', '0'],
  ];
  const s = tripSummary(rows);
  assert.equal(s.fixes, 3);
  assert.deepEqual(s.gaps, [{ startMs: 1000, durationMs: 19000 }]);
  assert.ok(Math.abs(s.speedShare - 2 / 3) < 1e-9);
});

test('CLI prints the speedo verdict for a report', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'fx-analyze-'));
  const report = path.join(dir, 'report.zip');
  fs.writeFileSync(report, makeZip({
    'manifest.json': '{"version_code":328,"device_model":"PPG-AN00"}',
    'state.json': '{"location":{"background_throttle_ms":"600000"},"auto":{"gearhead_version":"17.9"}}',
    'journal/diagnostics-1.jsonl': fixture('speedo-no-delivery.jsonl'),
    'trip/trip-1.csv': '#fermatax-trip-v2\ntype,elapsedMs,provider,stepM,acc,speed,speedAcc,mock\nE,0,start\n',
  }));
  const out = execFileSync(process.execPath, [path.join(here, 'analyze.mjs'), report], { encoding: 'utf8' });
  assert.match(out, /no_delivery/);
  assert.match(out, /PPG-AN00/);
  assert.match(out, /600000/);
});
