'use strict';
// Startup orchestrator: one selected mod per process, its own save profile.
rpc.exports.init = function(stage, parameters) {
  const report={schema:1,pid:Process.id,stage,status:'preparing',modsActivated:false};
  const held=[];globalThis.__tqProfileLoader={report,held};
  const write=()=>{const f=new File(parameters.report,'w');try{f.write(JSON.stringify(report));}finally{f.close();}};
  const abort=new NativeFunction(Process.getModuleByName('libc.so').getExportByName('abort'),'void',[],{exceptions:'propagate'});
  const refuse=e=>{report.status='refused';report.error=String(e);try{write();}finally{abort();}};
  try {
    const magisk=parameters.mode==='magisk';
    let text;
    try{text=File.readAllText(parameters.selectionRequest);}
    catch(e){if(!magisk)throw e;text='{"schema":1,"id":null,"database":null}';} // Never used the manager: original game.
    let request=JSON.parse(text);
    if(magisk){request=tqMagiskRequest(request,Process.id,parameters.perfEnabled,parameters.profilesRoot,p=>File.readAllText(p));text=JSON.stringify(request);}
    // Injected after the engine started (Magisk): safe only while the character list was never read.
    const late=magisk&&Process.findModuleByName('libtq.so')!==null;
    report.late=late;
    if(request.startupPid!==Process.id)throw Error('Selection belongs to another startup');
    const selection=tqResolveSelection(text,parameters.profiles,parameters.libraryRoot,p=>File.readAllBytes(p));
    if(!['vanilla','recognized','content','map-preflight-verified'].includes(selection.status))
      throw Error(selection.reason==='custom-world'?'Custom maps are not supported yet':'Selected mod is unsupported');
    report.menuProbe={selection:{status:selection.status,id:selection.id,name:selection.name}};
    const profile=tqResolveSaveProfile(request,Process.id,parameters.profilesRoot,parameters.liveSaveRoot);
    const isMap=selection.status==='map-preflight-verified',isContent=selection.status==='content';
    let perfStarted=false;
    const startPerf=()=>{
      if(request.perfEnabled!==true||perfStarted)return;
      perfStarted=true;
      report.perf={requested:true,status:'starting'};write();
      Promise.resolve(globalThis.tqStartPerf(stage)).then(()=>{report.perf.status='initialization-finished';write();}).catch(e=>{report.perf.status='error';report.perf.error=String(e);write();});
    };
    if(request.perfEnabled===true&&typeof globalThis.tqStartPerf!=='function')throw Error('Perf module is not bundled in this build');
    let mapConfig;
    if(isMap){
      if(selection.plan.map!=='maps/gladiators.map')throw Error('Map adapter unavailable');
      mapConfig={...parameters.gladiators,root:selection.plan.root.replace(/\/$/,''),api:parameters.mapApi};
      if(selection.plan.resources.length!==mapConfig.resources.length)throw Error('Map resource inventory changed');
      for(const expected of mapConfig.resources){
        const actual=selection.plan.resources.find(x=>x.path===mapConfig.root+'/resources/'+expected.name);
        if(!actual || actual.size!==expected.size || actual.fnv1a!==expected.fnv1a)throw Error('Map adapter fingerprint mismatch');
      }
    }
    held.push(...tqObserveEngineStartup(engine=>{
      const groups=[parameters.expectedApi];
      if(profile)groups.push(parameters.saveApi,parameters.cloudApi);
      if(isMap||isContent)groups.push(parameters.mapApi);
      const gate=tqPreflightNativeApi(engine,groups.map(g=>g.map(a=>({...a,rva:Number(a.rva)}))));
      report.apiPreflight={status:'matched',count:gate.count};
      const E=n=>engine.getExportByName(n);
      const fn=(n,r,a)=>new NativeFunction(gate.address(engine,n),r,a);
      const getArchive=fn('_ZN4GAME6Engine18GetDatabaseArchiveEv','pointer',['pointer']);
      const getPlayer=fn('_ZNK4GAME10GameEngine13GetMainPlayerEv','pointer',['pointer']);
      let calls=0,done=false;
      // Returns true once the selection is active. Called from the menu loop and,
      // if the player starts very quickly, from LoadGame before any save is read.
      const activate=()=>{
        if(done)return report.modsActivated||selection.status==='vanilla';
        const instance=E('_ZN4GAME7gEngineE').readPointer(),game=E('_ZN4GAME11gGameEngineE').readPointer();
        if(instance.isNull()||game.isNull())return false;
        const archive=getArchive(instance),player=getPlayer(game);
        if(!player.isNull())throw Error('Activation requires menu without player');
        if(archive.isNull())return false;
        done=true;
        // The game stands still while a mod activates; the app tells the player.
        if(selection.status!=='vanilla'){report.status='activating';write();}
        if(isMap){
          report.menuProbe.map=tqActivateGladiatorsMap(engine,archive,player,mapConfig,gate,r=>{report.menuProbe.map=r;write();});
          if(report.menuProbe.map.status!=='map-ready')throw Error('Map setup incomplete');
          report.modsActivated=true;
        }else if(isContent){
          report.menuProbe.content=tqActivateGenericMod(engine,archive,player,selection.plan,gate,r=>{report.menuProbe.content=r;write();});
          report.modsActivated=true;
        }else if(selection.status==='recognized'){
          report.menuProbe.database=tqLabDatabaseProbe(engine,archive,selection.config);
          if(report.menuProbe.database.status!=='overrides-verified')throw Error('Database setup incomplete');
          report.modsActivated=true;
        }else if(parameters.profiles.length){
          const sample=parameters.profiles.find(p=>p.kind!=='custom-map');
          if(sample)report.menuProbe.database=tqLabDatabaseProbe(engine,archive,{...sample,activate:false});
        }
        report.status='ready';write();
        // Install map call-site hooks before optional Perf can rewrite PLT calls.
        if(isMap)startPerf();
        return true;
      };
      let cloudPolicy;
      if(profile){cloudPolicy=tqInstallLocalOnlyCloud(engine,gate,refuse);held.push(cloudPolicy);report.cloud=cloudPolicy.report;}
      let saveRuntime=null;
      if(profile)held.push(saveRuntime=tqInstallSaveProfile(engine,gate,{...profile,cloudPolicy,beforeLoad(){
        if(!activate()||report.status!=='ready'||!report.modsActivated)throw Error('Mod activation is not ready; restart and wait at character selection');
      },onReport:r=>{report.saveProfile=r;write();}}));
      held.push(Interceptor.attach(gate.address(engine,'_ZN4GAME8PlayMenu6UpdateEv'),{onLeave(){
        if(calls===0&&late&&saveRuntime&&saveRuntime.report.redirects===0){done=true;refuse(Error('Character list opened before the loader was ready'));return;}
        if(done)return;
        if(++calls>600){done=true;refuse(Error('Menu activation timed out'));return;}
        try{activate();}catch(e){refuse(e);}
      }}));
      report.status='waiting-for-menu';write();
    },refuse,{allowRunning:magisk}));
    if(request.perfEnabled===true){
      if(isMap)report.perf={requested:true,status:'waiting-for-map'};
      else startPerf();
    } else report.perf={requested:false,status:'off'};
    if(report.status==='preparing')report.status='waiting-for-engine'; // A late install already moved on.
    write();
  }catch(e){refuse(e);}
};
