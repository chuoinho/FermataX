// Runs the fermata autoDebug unit tests through Gradle and prints the JUnit XML results as TAP.
// Usage: node tools/delegate/gradle-tap.mjs [--tests <pattern>]
// Gradle output goes to stderr; stdout carries only TAP. Exit code 0 only when every test passed.
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const resultsDir = path.join(root, 'fermata', 'build', 'test-results', 'testAutoDebugUnitTest');
const args = process.argv.slice(2);
const testsIdx = args.indexOf('--tests');
const filter = testsIdx >= 0 ? args[testsIdx + 1] : null;

fs.rmSync(resultsDir, { recursive: true, force: true });

const gradleArgs = [':fermata:testAutoDebugUnitTest', '--offline', '--continue', '--console=plain',
  '-PAPP_ID_SFX=.test', '-PALLOW_LEGACY_TEST_SIGNING=true'];
if (filter) gradleArgs.push('--tests', filter);
const isWin = process.platform === 'win32';
const run = isWin
  // Absolute wrapper path: the current directory may be excluded from command lookup.
  ? spawnSync('cmd.exe', ['/d', '/s', '/c', path.join(root, 'gradlew.bat'), ...gradleArgs], { cwd: root, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 })
  : spawnSync(path.join(root, 'gradlew'), gradleArgs, { cwd: root, encoding: 'utf8', maxBuffer: 256 * 1024 * 1024 });
process.stderr.write(run.stdout || '');
process.stderr.write(run.stderr || '');

const unescape = s => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&amp;/g, '&');
const attr = (tag, name) => { const m = tag.match(new RegExp(`\\s${name}="([^"]*)"`)); return m ? unescape(m[1]) : ''; };

const cases = [];
if (fs.existsSync(resultsDir)) {
  for (const file of fs.readdirSync(resultsDir).filter(f => f.startsWith('TEST-') && f.endsWith('.xml')).sort()) {
    const xml = fs.readFileSync(path.join(resultsDir, file), 'utf8');
    const re = /<testcase\b([^>]*?)(\/>|>([\s\S]*?)<\/testcase>)/g;
    for (let m; (m = re.exec(xml));) {
      const head = m[1];
      const body = m[3] || '';
      const status = /<(failure|error)\b/.test(body) ? 'fail' : /<skipped\b/.test(body) ? 'skip' : 'pass';
      const message = status === 'fail' ? (body.match(/<(?:failure|error)\b[^>]*message="([^"]*)"/) || [])[1] : null;
      cases.push({ name: `${attr(head, 'classname')}.${attr(head, 'name')}`, status, message: message ? unescape(message) : null });
    }
  }
}

let fail = cases.filter(c => c.status === 'fail').length;
const lines = ['TAP version 13', `# filter: ${filter ?? 'all fermata autoDebug unit tests'}`];
const total = cases.length + (run.status !== 0 && fail === 0 ? 1 : 0);
lines.push(`1..${total}`);
cases.forEach((c, i) => {
  if (c.status === 'skip') lines.push(`ok ${i + 1} - ${c.name} # SKIP`);
  else lines.push(`${c.status === 'pass' ? 'ok' : 'not ok'} ${i + 1} - ${c.name}`);
  if (c.message) lines.push(`  ---\n  message: ${JSON.stringify(c.message.slice(0, 500))}\n  ...`);
});
if (run.status !== 0 && fail === 0) {
  // Gradle failed (e.g. compilation) without a failing test result: report it, never infer success.
  fail += 1;
  lines.push(`not ok ${total} - gradle exited with ${run.status ?? run.error}`);
}
const pass = cases.filter(c => c.status === 'pass').length;
const skipped = cases.filter(c => c.status === 'skip').length;
lines.push(`# tests ${pass + fail + skipped}`, `# pass ${pass}`, `# fail ${fail}`, `# skipped ${skipped}`);
process.stdout.write(lines.join('\n') + '\n');
process.exit(fail === 0 && run.status === 0 ? 0 : 1);
