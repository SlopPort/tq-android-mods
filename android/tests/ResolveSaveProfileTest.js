'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm'),assert=require('assert');
const c=vm.createContext({});
for(const name of ['plan-save-path.js','resolve-save-profile.js'])
 vm.runInContext(fs.readFileSync(path.join(__dirname,'../bootstrap',name),'utf8'),c);
const id='00000000-0000-0000-0000-000000000001',base='/private/profiles',live='/external/SaveData/';
const request={schema:1,startupPid:12,id,database:'/library/mod.arz',preparedProfile:{root:base+'/'+id+'/SaveData',runtimeBound:false}};
const resolve=r=>c.tqResolveSaveProfile(r,12,base,live);
assert.equal(resolve(request).profileRoot,base+'/'+id+'/SaveData/');
assert.equal(c.tqResolveSaveProfile(request,12,['/system-alias/profiles',base],live).profileRoot,base+'/'+id+'/SaveData/');
assert.throws(()=>c.tqResolveSaveProfile(request,12,['/system-alias/profiles'],live));
assert.equal(resolve({schema:1,startupPid:12,id:null,database:null}),null);
for(const r of [{...request,startupPid:11},{...request,id:'../escape'},{...request,database:null},
 {...request,preparedProfile:null},{...request,preparedProfile:{...request.preparedProfile,runtimeBound:true}},
 {...request,preparedProfile:{root:'/external/SaveData',runtimeBound:false}},
 {...request,preparedProfile:{...request.preparedProfile,root:base+'/'+id+'/SaveData/'}},
 {...request,preparedProfile:{...request.preparedProfile,root:base+'/another/SaveData'}}])assert.throws(()=>resolve(r));
for(const invalid of ['/private/../profiles','relative','/private//profiles','/private/profiles/'])
 assert.throws(()=>c.tqResolveSaveProfile(request,12,invalid,live));
assert.throws(()=>c.tqResolveSaveProfile(request,12,base,base+'/'+id+'/SaveData/'));
// A combination has no database of its own but still needs its prepared copy.
assert.equal(c.tqResolveSaveProfile({...request,database:null,members:[{},{}]},12,base,live).id,id);
assert.throws(()=>c.tqResolveSaveProfile({...request,database:null,members:[{},{}],preparedProfile:undefined},12,base,live),/separate save copy/);
console.log('PASS: current prepared profile, vanilla, combination, stale startup and mismatched/overlapping roots');
