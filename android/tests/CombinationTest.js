'use strict';
// Several content mods: selection checks and the order of mounts, text and database loads.
const assert=require('assert'),fs=require('fs'),vm=require('vm'),path=require('path');
const B=n=>fs.readFileSync(path.join(__dirname,'../bootstrap',n),'utf8');
const uuid=i=>'00000000-0000-0000-0000-00000000000'+i,root='/lib';
const member=(i,extra)=>({mod:uuid(i),database:root+'/'+uuid(i)+'/db/m'+i+'.arz',size:10,name:'Mod '+i,customWorld:false,
 resources:[{path:root+'/'+uuid(i)+'/resources/Text.arc',size:5},{path:root+'/'+uuid(i)+'/resources/Textures.arc',size:5}],...extra});

// Selection
const sel={};vm.createContext(sel);vm.runInContext(B('resolve-selection.js'),sel);
const resolve=r=>sel.tqResolveSelection(JSON.stringify({schema:1,id:uuid(9),name:'Mod 1 + Mod 2',...r}),[],root,()=>{throw Error('no read');});
const ok=resolve({members:[member(1),member(2)]});
assert.equal(ok.status,'combination');assert.deepEqual(ok.plans.map(p=>p.id),[uuid(1),uuid(2)]);
assert.equal(ok.plans[0].resources.length,2);
assert.throws(()=>resolve({members:[member(1)]}),/2 to 8/);
assert.throws(()=>resolve({members:[member(1),member(1)]}),/Repeated/);
assert.throws(()=>resolve({members:[member(1),member(2,{customWorld:true})]}),/cannot be combined/);
assert.throws(()=>resolve({members:[member(1),member(2,{database:root+'/'+uuid(1)+'/db/x.arz'})]}),/database path/);
assert.throws(()=>resolve({id:'bad',members:[member(1),member(2)]}),/library ID/);

// Activation order with a simulated engine
const calls=[];
function P(v){const o={v,isNull:()=>v===0,sub:()=>P(0),toInt32:()=>0,readPointer:()=>P(1),writePointer:x=>{o.target=x;},
 writeByteArray(){},readU32:()=>0,readByteArray:()=>new ArrayBuffer(0)};o.add=()=>o;return o;}
const natives={
 assign:(p,t)=>{p.text=t.text;return p;},
 GetRecordIndexFromString:()=>1,
 GetFileSystem:()=>P(1),
 AddSourceArchive:(_fs,o)=>{calls.push('mount '+o.target.text);return 1;},
 ArchiveC2Ev:()=>{},ArchiveD2Ev:()=>{},
 Archive4Open:(a,p)=>{a.path=p.text;return 1;},
 FindFile:(a,n)=>n.text==='modstrings.txt'?0:-1,
 GetFileEntry:()=>({add:()=>({readU32:()=>4})}),
 ExtractFileToMemory:(a,_i,b)=>{b.content=a.path;return 1;},
 Deserialize:(_a,b)=>{calls.push('database '+b.name);},
 LocalizationManager11AddTagToMap:()=>{},HasLocalization:()=>1,
 assignEPKw:()=>{},D2Ev:()=>{}};
const ctx={Uint8Array,DataView,Map,Set,Array,String,Math,Error,JSON,
 NativeFunction:function(n){const k=Object.keys(natives).find(k=>n.includes(k));if(!k)throw Error('unmocked '+n);return natives[k];},
 Memory:{alloc:()=>{const p=P(1);p.readByteArray=()=>{calls.push('text '+p.content);return new TextEncoder().encode('tagX=1').buffer;};p.writeByteArray=d=>{if(d.mod)p.name=d.mod;};return p;},
  allocUtf8String:s=>({text:s})},
 File:{readAllBytes:p=>{const b=new Uint8Array(0);b.mod=p;return b;}},
 globalThis:{}};
vm.createContext(ctx);vm.runInContext(B('generic-mod-runtime.js'),ctx);
ctx.tqArzSummary=()=>({count:3,names:['a.dbr']});
ctx.tqDecodeModText=()=>'tagX=1';
ctx.tqAddModTextRows=(_e,_g,_h,rows)=>{calls.push('tags '+rows.length);};
const plans=ok.plans;
const report=ctx.tqActivateGenericMods({getExportByName:()=>P(1)},P(1),P(0),plans,{address:(_e,n)=>n},()=>{});
const order=calls.map(c=>c.replace(/\/lib\/[0-9-]*?(\d)\//g,'$1/'));
assert.deepEqual(order.filter(c=>c.startsWith('mount')),['mount 1/resources/Text.arc','mount 1/resources/Textures.arc','mount 2/resources/Text.arc','mount 2/resources/Textures.arc'],'first mod mounted first');
assert.deepEqual(order.filter(c=>c.startsWith('database')),['database 2/db/m2.arz','database 1/db/m1.arz'],'first mod applied last');
assert.equal(report.status,'content-loaded');assert.equal(report.mods.length,2);
assert.deepEqual(report.mods.map(m=>m.text.status),['registered','registered']);
assert.throws(()=>ctx.tqActivateGenericMods({getExportByName:()=>P(1)},P(1),P(0),[plans[0]],{address:(_e,n)=>n},()=>{}),/2 to 8/);
console.log('PASS: combination selection checks; first mod mounted first and its database applied last; text of every mod registered');
