'use strict';
// Simulated engine for map-navigation-runtime.js: builds once per level, only when settled, never for stock levels.
const fs=require('fs'),vm=require('vm'),assert=require('assert'),path=require('path');
function world(o){
 const st={thread:'main',loading:0,level:'L1',mesh:'M1',init:{M1:false,M2:true,M3:false},creates:[],fail:false,...o};
 const P=v=>({v,isNull:()=>v===null,equals:x=>x.v===v,toString:()=>String(v),toUInt32:()=>v==='main'?7:8,readPointer:()=>P(v==='gMain'?'main':v==='gGame'?'game':v)});
 let hook;
 const fns={
  _ZN4GAME15GetMainThreadIdEv:()=>7,
  _ZNK4GAME10GameEngine13GetMainPlayerEv:()=>P('player'),
  _ZNK4GAME6Entity9GetRegionEv:()=>P('region'),
  _ZNK4GAME6Region8GetLevelEb:()=>P(st.level),
  _ZN4GAME6Region8GetWorldEv:()=>P('world'),
  _ZNK4GAME5World19GetNumLevelsLoadingEv:()=>st.loading,
  _ZNK4GAME5Level8IsLoadedEv:()=>true,
  _ZNK4GAME5Level17GetPostLoadCalledEv:()=>true,
  _ZNK4GAME5Level9GetRegionEv:()=>P('region'),
  _ZN4GAME5Level17GetPathMeshRecastEv:()=>P(st.mesh),
  _ZNK4GAME14PathMeshRecast13IsInitializedEv:m=>st.init[m.v]?1:0,
  _ZN4GAME5Level14CreatePathMeshEv:l=>{st.creates.push(l.v);if(!st.fail)st.init[st.mesh]=true;},
  pthread_self:()=>P(st.thread)};
 const c={Process:{getModuleByName:()=>({getExportByName:n=>n})},
  NativeFunction:function(n){return fns[n];},
  Interceptor:{attach:(_a,h)=>(hook=h,{})}};
 vm.createContext(c);
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../bootstrap/map-navigation-runtime.js'),'utf8'),c);
 const engine={getExportByName:n=>P(n==='_ZN4GAME13gMainThreadIdE'?'gMain':'gGame')};
 let report;
 c.tqInstallMapNavigation(engine,{address:(_e,n)=>n},r=>{report=JSON.parse(JSON.stringify(r));},{settle:5,limit:3});
 return {st,frames:n=>{for(let i=0;i<n;i++)hook.onLeave();},report:()=>report};
}
let w=world();w.frames(5);assert.equal(w.st.creates.length,0,'waits for settle');
w.frames(2);assert.deepEqual(w.st.creates,['L1']);assert.equal(w.report().built,1);
w.frames(50);assert.equal(w.st.creates.length,1,'built once');
w.st.level='L2';w.st.mesh='M2';w.frames(50);assert.equal(w.st.creates.length,1,'stock level untouched');
w.st.level='L3';w.st.mesh='M3';w.st.loading=1;w.frames(50);assert.equal(w.st.creates.length,1,'waits while loading');
w.st.loading=0;w.frames(10);assert.deepEqual(w.st.creates,['L1','L3']);
w=world({fail:true});w.frames(100);assert.equal(w.st.creates.length,1,'failed build not retried');assert.equal(w.report().failed,1);
w=world({thread:'other'});w.frames(20);assert.equal(w.st.creates.length,0);assert.equal(w.report().status,'wrong-thread');
w=world({fail:true});for(const l of ['A','B','C','D']){w.st.level=l;w.st.mesh='N'+l;w.frames(10);}
assert.equal(w.st.creates.length,3);assert.equal(w.report().status,'limit-reached');
console.log('PASS: map navigation built once per level after loading settles; stock levels, other threads, failed and over-limit builds left alone');
