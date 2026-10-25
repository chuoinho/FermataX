import {test, after} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

const windows = process.platform === 'win32';
const temp = windows ? fs.mkdtempSync(path.join(os.tmpdir(), 'fermata dhu test ')) : null;
const launcher = path.resolve('open-dhu.bat');
const sdk = temp && path.join(temp, 'Android', 'Sdk');
const bin = temp && path.join(temp, 'bin');
if (windows) {
  fs.mkdirSync(bin, {recursive:true});
  fs.mkdirSync(path.join(sdk, 'platform-tools'), {recursive:true});
  fs.mkdirSync(path.join(sdk, 'extras/google/auto/config'), {recursive:true});
  for (const name of ['default.ini','default_6in.ini','default_720p.ini','default_wide.ini','default_1080p.ini','all_720p.ini'])
    fs.writeFileSync(path.join(sdk, 'extras/google/auto/config', name), '');
  const source=path.join(temp,'FakeTool.cs'), executable=path.join(temp,'FakeTool.exe');
  fs.writeFileSync(source, String.raw`
using System;
using System.IO;
using System.Linq;
using System.Web.Script.Serialization;
class FakeTool {
 static string Env(string name) { return Environment.GetEnvironmentVariable(name); }
 static int Main(string[] args) {
  var tool=Path.GetFileName(Environment.GetCommandLineArgs()[0]).ToLowerInvariant();
  if(!tool.EndsWith(".exe"))tool+=".exe";
  var json=new JavaScriptSerializer();
  File.AppendAllText(Env("FX_LOG"),json.Serialize(new {tool,args})+"\n");
  if(tool=="choice.exe") {
   int count=File.Exists(Env("FX_CHOICE_COUNT"))?int.Parse(File.ReadAllText(Env("FX_CHOICE_COUNT"))):0;
   File.WriteAllText(Env("FX_CHOICE_COUNT"),(count+1).ToString());
   var choices=Env("FX_CHOICES").Split(','); return count<choices.Length?int.Parse(choices[count]):1;
  }
  if(tool=="adb.exe") {
   var devices=json.Deserialize<string[][]>(Env("FX_DEVICES"));
   string selected=args.Length>1&&args[0]=="-s"?args[1]:null;
   string command=args.Length>0?args[selected!=null?2:0]:"";
   if(command=="devices") { Console.WriteLine("List of devices attached"); foreach(var d in devices)Console.WriteLine(d[0]+"\t"+d[1]); }
   else if(command=="connect") { File.WriteAllText(Env("FX_CONNECTED"),args[1]); Console.WriteLine("connected"); }
   else if(command=="get-state") {
    string connected=File.Exists(Env("FX_CONNECTED"))?File.ReadAllText(Env("FX_CONNECTED")):null;
    bool ok=selected!=null?devices.Any(d=>d[0]==selected&&d[1]=="device")||selected==connected:devices.Count(d=>d[1]=="device")==1;
    if(!ok)return 1; Console.WriteLine("device");
   }
  }
  return 0;
 }
}`);
  const compiled=spawnSync(path.join(process.env.WINDIR,'Microsoft.NET/Framework64/v4.0.30319/csc.exe'),
    ['/nologo','/target:exe','/reference:System.Web.Extensions.dll','/out:'+executable,source],{encoding:'utf8'});
  assert.equal(compiled.status,0,compiled.stdout+compiled.stderr);
  for(const file of [path.join(bin,'choice.exe'),path.join(bin,'taskkill.exe'),
    path.join(sdk,'platform-tools/adb.exe'),path.join(sdk,'extras/google/auto/desktop-head-unit.exe')])
    fs.copyFileSync(executable,file);
}
after(() => {
  if (!temp) return;
  const target=path.resolve(temp), parent=path.resolve(os.tmpdir())+path.sep;
  assert.ok(target.startsWith(parent) && path.basename(target).startsWith('fermata dhu test '));
  fs.rmSync(target, {recursive:true, force:true});
});

function run(devices, serial, choices='1,1') {
  const id=String(Math.random()).slice(2), log=path.join(temp, id+'.jsonl');
  const result=spawnSync(process.env.ComSpec || 'cmd.exe', ['/d','/c',launcher,...(serial?[serial]:[])], {
    encoding:'utf8', input:'\r\n', timeout:15000,
    env:{...process.env,LOCALAPPDATA:temp,PATH:bin+';'+process.env.PATH,
      FX_LOG:log,FX_DEVICES:JSON.stringify(devices),FX_CHOICES:choices,
      FX_CHOICE_COUNT:path.join(temp,id+'.choice'),FX_CONNECTED:path.join(temp,id+'.connected')}
  });
  assert.equal(result.status,0,result.stdout+'\n'+result.stderr+'\n'+result.error);
  const calls=fs.readFileSync(log,'utf8').trim().split(/\r?\n/).map(JSON.parse);
  const forwards=calls.filter(c=>c.tool==='adb.exe' && c.args.includes('forward'));
  return {calls,forwards};
}
function selected(result, serial) {
  assert.ok(result.forwards.length>=2,'must remove and recreate the forwarding: '+JSON.stringify(result.calls));
  for(const c of result.forwards) assert.deepEqual(c.args.slice(0,2),['-s',serial]);
  assert.ok(result.calls.some(c=>c.tool==='desktop-head-unit.exe'),'DHU must launch');
}
test('one USB device is selected explicitly for forwarding', {skip:!windows},()=> {
  selected(run([['USB123','device']]),'USB123');
});
test('one wireless device works without a serial argument', {skip:!windows},()=> {
  selected(run([['192.168.1.95:36535','device']]),'192.168.1.95:36535');
});
test('IP and mDNS aliases can be selected separately', {skip:!windows},()=> {
  selected(run([['192.168.1.95:36535','device'],['adb-Honor._adb-tls-connect._tcp','device']],null,'1,2'),
    'adb-Honor._adb-tls-connect._tcp');
});
test('explicit wireless serial wins over other transports', {skip:!windows},()=> {
  selected(run([['OTHER','device'],['192.168.1.95:36535','device']],'192.168.1.95:36535'),
    '192.168.1.95:36535');
});
test('offline and unauthorized transports are excluded', {skip:!windows},()=> {
  selected(run([['OFFLINE','offline'],['LOCKED','unauthorized'],['USB123','device']]),'USB123');
});
test('an explicit wireless endpoint is connected if needed', {skip:!windows},()=> {
  const result=run([], '192.168.1.95:36535');
  selected(result,'192.168.1.95:36535');
  assert.ok(result.calls.some(c=>c.tool==='adb.exe'&&c.args[0]==='connect'&&c.args[1]==='192.168.1.95:36535'));
});
