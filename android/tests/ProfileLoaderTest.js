'use strict';
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
function run(mode){
 let early,menu,last,beforeLoad,archiveReady=true,redirects=0;const events=[],statuses=[];const magisk=mode.startsWith('magisk');
 const id='00000000-0000-0000-0000-000000000001',root='/profiles/'+id+'/SaveData';
 const request={schema:1,startupPid:mode==='stale'?99:12,id,database:'/lib/mod.arz',preparedProfile:{root,runtimeBound:false}};
 if(['perf','perf-map-fails','perf-vanilla'].includes(mode))request.perfEnabled=true;
 if(['vanilla','perf-vanilla'].includes(mode)){request.id=null;request.database=null;}
 if(mode==='missing')delete request.preparedProfile;
 if(magisk){delete request.startupPid;request.preparedProfile={root:'/elsewhere',runtimeBound:false};}
 const ptr={isNull:()=>false},player={isNull:()=>true};
 function File(){this.write=s=>{last=JSON.parse(s);if(statuses[statuses.length-1]!==last.status)statuses.push(last.status);};this.close=()=>{};}
 File.readAllText=p=>p.endsWith('profile.properties')?(mode==='magisk-noprofile'?(()=>{throw Error('ENOENT');})():['#x','schema=1','id='+id].join(String.fromCharCode(10))):JSON.stringify(request);
 const c={rpc:{exports:{}},Process:{id:12,getModuleByName:()=>({getExportByName:()=> 'abort'}),findModuleByName:()=>magisk?{}:null},File,
  NativeFunction:function(n,_r,_a,options){if(n==='abort')assert.equal(options.exceptions,'propagate');if(n==='abort')return ()=>{events.push('abort');throw Error('ABORT');};return ()=>n.includes('GetMainPlayer')?player:n.includes('GetDatabaseArchive')&&!archiveReady?player:ptr;},
  Interceptor:{attach:(_p,h)=>(menu=h,{})},
  tqStartPerf:()=>{events.push('perf');return Promise.resolve();},
  tqResolveSelection:()=>['vanilla','perf-vanilla'].includes(mode)?{status:'vanilla'}:mode==='content'||mode==='early'||magisk?{status:'content',id,plan:{root:'/library/',database:'/library/mod.arz',resources:[]}}:{status:'map-preflight-verified',id,plan:{root:'/library/',map:'maps/gladiators.map',resources:[{path:'/library/resources/Maps.arc',size:1,fnv1a:'abc'}]}},
  tqObserveEngineStartup:(cb,_r,o)=>(assert.equal(!!(o&&o.allowRunning),magisk),early=cb,events.push('observer'),[{}]),
  tqPreflightNativeApi:()=>({count:8,address:(_e,n)=>n}),
  tqInstallLocalOnlyCloud:()=>{events.push('cloud');return {report:{status:'local-only'}};},
  tqInstallSaveProfile:(_e,_g,o)=>{beforeLoad=o.beforeLoad;events.push('profile');assert.equal(o.profileRoot,root+'/');return {report:{get redirects(){return redirects;}}};},
  tqActivateGladiatorsMap:()=>{events.push('map');return {status:mode==='perf-map-fails'?'error-restart-required':'map-ready'};},
  tqLabDatabaseProbe:()=>({status:'baseline-verified'}),
  tqActivateGenericMod:()=>{events.push('content');return {status:'content-loaded'};}};
 vm.createContext(c);
 for(const n of ['plan-save-path.js','resolve-save-profile.js','magisk-request.js','profile-loader.js'])vm.runInContext(fs.readFileSync(path.join(__dirname,'../bootstrap',n),'utf8'),c);
 try{c.rpc.exports.init('early',{mode:magisk?'magisk':undefined,perfEnabled:false,report:'report',selectionRequest:'request',profiles:[],libraryRoot:'/library',profilesRoot:'/profiles',liveSaveRoot:'/live/SaveData/',expectedApi:[],saveApi:[],cloudApi:[],mapApi:[],gladiators:{resources:[{name:'Maps.arc',size:1,fnv1a:'abc'}]}});}catch(e){assert.match(String(e),/ABORT/);}
 if(early){
  if(mode==='perf'){assert(!events.includes('perf'));assert.equal(last.perf.status,'waiting-for-map');}
  early({getExportByName:()=>({readPointer:()=>ptr})});
  if(mode==='magisk-late'){assert.throws(()=>menu.onLeave(),/ABORT/);return {last,events,statuses};}
  if(mode==='magisk'){assert.equal(last.status,'waiting-for-menu');redirects=1;menu.onLeave();assert.equal(last.late,true);assert.equal(last.modsActivated,true);return {last,events,statuses};}
  if(mode==='early'){assert.doesNotThrow(beforeLoad);assert.equal(last.modsActivated,true);menu.onLeave();return {last,events,statuses};}
  if(beforeLoad){archiveReady=false;assert.throws(beforeLoad,/not ready/);archiveReady=true;}
  if(mode==='delayed'){archiveReady=false;menu.onLeave();assert.equal(last.modsActivated,false);assert.throws(beforeLoad,/not ready/);archiveReady=true;}
  if(mode==='perf-map-fails')assert.throws(()=>menu.onLeave(),/ABORT/);
  else {menu.onLeave();menu.onLeave();if(beforeLoad)assert.doesNotThrow(beforeLoad);}
 }
 return {last,events,statuses};
}
const map=run('map');assert.deepEqual(map.events,['observer','cloud','profile','map']);assert.equal(map.last.modsActivated,true);assert.equal(map.last.status,'ready');
assert.equal(run('delayed').last.modsActivated,true);
const perf=run('perf');assert.deepEqual(perf.events,['observer','cloud','profile','map','perf']);
assert.equal(perf.events.filter(x=>x==='perf').length,1);
assert(!run('perf-map-fails').events.includes('perf'));
const perfVanilla=run('perf-vanilla');assert.equal(perfVanilla.events[1],'perf');
const vanilla=run('vanilla');assert.deepEqual(vanilla.events,['observer']);assert.equal(vanilla.last.modsActivated,false);
for(const mode of ['missing','stale']){const r=run(mode);assert.ok(r.events.includes('abort'));assert.ok(!r.events.includes('observer'));assert.equal(r.last.status,'refused');}
const content=run('content');assert.deepEqual(content.statuses.slice(-3),['waiting-for-menu','activating','ready']);
assert(!vanilla.statuses.includes('activating'));
assert.deepEqual(content.events,['observer','cloud','profile','content']);assert.equal(content.last.modsActivated,true);
const fast=run('early');assert.deepEqual(fast.events,['observer','cloud','profile','content']);
const late=run('magisk-late');assert.ok(late.events.includes('abort'));assert.match(late.last.error,/before the loader was ready/);
const mg=run('magisk');assert.ok(!mg.events.includes('abort'));assert.deepEqual(mg.events,['observer','cloud','profile','content']);
const noProfile=run('magisk-noprofile');assert.ok(noProfile.events.includes('abort'));assert.match(noProfile.last.error,/separate save copy/);
console.log('PASS: magisk late attach (guarded by save redirects), magisk profile check, content mod with an activating status, activation on a fast LoadGame, selected profile before map, vanilla without profile hooks, missing/stale startup refusal');
