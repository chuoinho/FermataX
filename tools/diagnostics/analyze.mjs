#!/usr/bin/env node
// Reads a FermataX diagnostic report (the ZIP from Settings > Diagnostics > Export) and prints
// what is needed to find the cause of an issue: device and permission state, a timeline of the
// journal events, the events around each error, the speedo verdict and trip log summaries.
//
// Usage: node tools/diagnostics/analyze.mjs <report.zip> [--area <category>] [--around-errors <s>] [--json]
import fs from 'node:fs';
import zlib from 'node:zlib';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const GAP_MS = 8_000;

/** All entries of a ZIP (stored or deflated) by name. */
export function readZip(buf) {
  let eocd = -1;
  for (let i = buf.length - 22; i >= Math.max(0, buf.length - 65_557); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('Not a ZIP file');
  const count = buf.readUInt16LE(eocd + 10);
  let p = buf.readUInt32LE(eocd + 16);
  const entries = new Map();
  for (let n = 0; n < count; n++) {
    if (buf.readUInt32LE(p) !== 0x02014b50) throw new Error('Bad ZIP central directory');
    const method = buf.readUInt16LE(p + 10);
    const size = buf.readUInt32LE(p + 20);
    const nameLen = buf.readUInt16LE(p + 28);
    const extraLen = buf.readUInt16LE(p + 30);
    const commentLen = buf.readUInt16LE(p + 32);
    const local = buf.readUInt32LE(p + 42);
    const name = buf.toString('utf8', p + 46, p + 46 + nameLen);
    const dataStart = local + 30 + buf.readUInt16LE(local + 26) + buf.readUInt16LE(local + 28);
    const data = buf.subarray(dataStart, dataStart + size);
    if (method === 0) entries.set(name, Buffer.from(data));
    else if (method === 8) entries.set(name, zlib.inflateRawSync(data));
    else throw new Error(`Unsupported ZIP method ${method} for ${name}`);
    p += 46 + nameLen + extraLen + commentLen;
  }
  return entries;
}

/** Journal lines as event objects; lines that are not JSON are skipped. */
export function parseJournal(text) {
  const events = [];
  for (const line of text.split(/\r?\n/)) {
    if (!line.trim()) continue;
    try { events.push(JSON.parse(line)); } catch { /* a torn last line after a crash */ }
  }
  return events;
}

/**
 * Why the speed card showed no speed: 'no_delivery' (the system gave no fixes), 'no_usable_speed'
 * (fixes arrived without a usable Doppler speed), 'intermittent_delivery' (fixes arrive but stop
 * for longer than 8 s at times), 'ok', or 'unknown' (no speedo data).
 */
export function speedoVerdict(events, tripRows) {
  const evidence = [];
  const speedo = events.filter((e) => e.category === 'speedo' || e.category === 'location');
  const summaries = speedo.filter((e) => e.event === 'speedo_fix_summary');
  const sum = (key) => summaries.reduce((s, e) => s + (Number(e.data?.[key]) || 0), 0);
  const fixes = sum('fixes');
  const usable = sum('speed_ok') + sum('speed_stop');
  const stale = speedo.some((e) => e.event === 'speedo_signal_changed' && e.data?.signal === 'STALE');
  const gaps = speedo.filter((e) => e.event === 'location_gap_started').length;
  const unknownTracking = speedo.some((e) => e.event === 'speedo_signal_changed'
      && e.data?.signal === 'TRACKING' && e.data?.speed_unknown === true);
  const tripFixes = tripRows.filter((r) => r[0] === 'L').length;
  if (!speedo.length && !tripFixes) return { verdict: 'unknown', evidence: ['no speedo or location events'] };
  if (stale) evidence.push('signal went STALE (no accepted fix for 8 s)');
  if (gaps) evidence.push(`${gaps} location delivery gap(s) longer than 8 s`);
  if (summaries.length) evidence.push(`${fixes} fixes in summaries, ${usable} with a usable speed`);
  if (unknownTracking) evidence.push('tracking with fixes but no speed to show');
  for (const key of ['mock', 'mock_rejected', 'invalid_time', 'invalid_accuracy', 'rejected', 'signal_only', 'recovering',
    'speed_missing', 'speed_low_confidence', 'speed_out_of_range']) {
    const n = sum(key);
    if (n) evidence.push(`${key}: ${n}`);
  }
  if (fixes === 0 && tripFixes === 0 && (stale || gaps)) return { verdict: 'no_delivery', evidence };
  if (fixes > 0 && usable / fixes < 0.2) return { verdict: 'no_usable_speed', evidence };
  if (fixes === 0 && unknownTracking) return { verdict: 'no_usable_speed', evidence };
  const tripGaps = tripSummary(tripRows).gaps.length;
  if (tripGaps) evidence.push(`${tripGaps} gap(s) longer than 8 s in the trip logs`);
  // Fixes do arrive but stop for longer than the card waits: it shows "GPS signal lost" in between.
  if ((fixes > 0 || tripFixes > 0) && (gaps || tripGaps)) return { verdict: 'intermittent_delivery', evidence };
  if (fixes > 0) return { verdict: 'ok', evidence };
  return { verdict: stale || gaps ? 'no_delivery' : 'unknown', evidence };
}

/** Observed source intervals end at unregister or the last event of that process/session. */
export function sourceSummary(events) {
  const fixes = {gps:0, google_fused:0, platform_fused:0};
  const durationMs = {...fixes};
  let changes = 0;
  const streams = new Map();
  for (const e of events) {
    if (e.event === 'speedo_fix_summary') for (const source of Object.keys(fixes))
      fixes[source] += Number(e.data?.['src_' + source]) || 0;
    const key = JSON.stringify([e.session_id, e.process, e.pid]);
    if (!streams.has(key)) streams.set(key, []);
    streams.get(key).push(e);
  }
  for (const stream of streams.values()) {
    stream.sort((a,b) => a.elapsed_ms - b.elapsed_ms);
    let source, previous;
    for (const e of stream) {
      if (source && Number.isFinite(e.elapsed_ms) && Number.isFinite(previous))
        durationMs[source] += Math.max(0, e.elapsed_ms - previous);
      previous = e.elapsed_ms;
      if (e.event === 'location_source_changed') {
        changes++;
        source = Object.hasOwn(fixes, e.data?.to) ? e.data.to : undefined;
      } else if (e.event === 'location_unregistered') source = undefined;
    }
  }
  return {changes, fixes, durationMs};
}

/** Summary of one trip log (rows split on commas, header lines removed). */
export function tripSummary(rows) {
  const fixes = rows.filter((r) => r[0] === 'L');
  const gaps = [];
  for (let i = 1; i < fixes.length; i++) {
    const prev = Number(fixes[i - 1][1]);
    const t = Number(fixes[i][1]);
    if (t - prev > GAP_MS) gaps.push({ startMs: prev, durationMs: t - prev });
  }
  const acc = fixes.map((r) => Number(r[4])).filter((n) => Number.isFinite(n) && n > 0).sort((a, b) => a - b);
  const pct = (p) => (acc.length ? acc[Math.min(acc.length - 1, Math.floor(p * acc.length))] : null);
  return {
    fixes: fixes.length,
    gaps,
    speedShare: fixes.length ? fixes.filter((r) => r[5] !== '').length / fixes.length : 0,
    mockShare: fixes.length ? fixes.filter((r) => r[7] === '1').length / fixes.length : 0,
    accuracyP50: pct(0.5),
    accuracyP90: pct(0.9),
  };
}

function csvRows(text) {
  return text.split(/\r?\n/).map((l) => l.trim())
    .filter((l) => l && !l.startsWith('#') && !l.startsWith('type,'))
    .map((l) => l.split(','));
}

function eventLine(e) {
  const data = e.data && Object.keys(e.data).length ? ' ' + JSON.stringify(e.data) : '';
  return `${String(e.elapsed_ms).padStart(10)}ms ${e.priority.padEnd(6)} ${e.category}/${e.event}${data}`;
}

function analyze(zipPath, { area, aroundErrorsS = 30, json = false }) {
  const entries = readZip(fs.readFileSync(zipPath));
  const text = (name) => entries.get(name)?.toString('utf8');
  const manifest = JSON.parse(text('manifest.json') ?? '{}');
  const state = JSON.parse(text('state.json') ?? '{}');
  const events = [];
  for (const [name, buf] of entries) {
    if (name.endsWith('.jsonl')) events.push(...parseJournal(buf.toString('utf8')));
  }
  events.sort((a, b) => (a.timestamp_ms - b.timestamp_ms) || (a.sequence - b.sequence));
  const trips = [...entries].filter(([n]) => n.startsWith('trip/'))
    .map(([n, buf]) => ({ name: n, rows: csvRows(buf.toString('utf8')) }));
  const allRows = trips.flatMap((t) => t.rows);
  const result = {
    manifest,
    state,
    speedo: speedoVerdict(events, allRows),
    sources: sourceSummary(events),
    trips: trips.map((t) => ({ name: t.name, ...tripSummary(t.rows) })),
    events: area ? events.filter((e) => e.category === area) : events,
  };
  if (json) return JSON.stringify(result, null, 2);

  const out = [];
  out.push('== Report');
  out.push(`app ${manifest.version_name ?? '?'} (${manifest.version_code ?? '?'}) ${manifest.build_type ?? ''}`.trim());
  out.push(`device ${manifest.device_manufacturer ?? ''} ${manifest.device_model ?? ''}, Android ${manifest.android_release ?? '?'} (API ${manifest.android_api ?? '?'})`);
  out.push('== State');
  out.push(JSON.stringify(state, null, 2));
  out.push('== Speedo');
  out.push(`verdict: ${result.speedo.verdict}`);
  for (const ev of result.speedo.evidence) out.push(`  - ${ev}`);
  out.push('== Location sources');
  out.push('source changes: ' + result.sources.changes);
  for (const source of Object.keys(result.sources.fixes))
    out.push(source + ': ' + result.sources.fixes[source] + ' fixes, ' + result.sources.durationMs[source] + ' ms observed');
  out.push('== Trip logs');
  if (!result.trips.length) out.push('(none)');
  for (const t of result.trips) {
    out.push(`${t.name}: ${t.fixes} fixes, ${t.gaps.length} gap(s), speed on ${(t.speedShare * 100).toFixed(0)}%,`
      + ` mock ${(t.mockShare * 100).toFixed(0)}%, accuracy p50 ${t.accuracyP50 ?? '-'} m p90 ${t.accuracyP90 ?? '-'} m`);
    for (const g of t.gaps) out.push(`  gap at ${g.startMs} ms for ${g.durationMs} ms`);
  }
  out.push(`== Around errors (±${aroundErrorsS} s)`);
  const errors = events.filter((e) => e.priority === 'error');
  if (!errors.length) out.push('(no errors)');
  for (const err of errors) {
    out.push(`-- ${err.category}/${err.event}`);
    for (const e of events) {
      if (e.session_id === err.session_id && Math.abs(e.elapsed_ms - err.elapsed_ms) <= aroundErrorsS * 1000) {
        out.push(eventLine(e));
      }
    }
  }
  out.push(`== Timeline${area ? ` (${area})` : ''}`);
  let session;
  for (const e of result.events) {
    if (e.session_id !== session) {
      session = e.session_id;
      out.push(`-- session ${session} ${new Date(e.timestamp_ms).toISOString()}`);
    }
    out.push(eventLine(e));
  }
  return out.join('\n');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const zip = args.find((a, i) => !a.startsWith('--') && !['--area', '--around-errors'].includes(args[i - 1]));
  if (!zip) {
    console.error('Usage: node tools/diagnostics/analyze.mjs <report.zip> [--area <category>] [--around-errors <s>] [--json]');
    process.exit(2);
  }
  const opt = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : undefined; };
  console.log(analyze(zip, {
    area: opt('--area'),
    aroundErrorsS: Number(opt('--around-errors') ?? 30),
    json: args.includes('--json'),
  }));
}
