'use strict';
// Custom maps often ship without navigation data, so the hero cannot walk. The engine can build a
// level's navigation itself (Level::CreatePathMesh). This builds it once per level the player is in,
// on the game thread, after loading has settled. Stock levels already have it and are left alone.
function tqInstallMapNavigation(engine, gate, onReport, options) {
 const limit=(options&&options.limit)||64,settle=(options&&options.settle)||30;
 const report={status:'waiting-for-world',built:0,failed:0,levels:[]};
 const F=(n,r,a)=>new NativeFunction(gate.address(engine,n),r,a);
 const mainThread=F('_ZN4GAME15GetMainThreadIdEv','uint',[]);
 const getPlayer=F('_ZNK4GAME10GameEngine13GetMainPlayerEv','pointer',['pointer']);
 const regionOf=F('_ZNK4GAME6Entity9GetRegionEv','pointer',['pointer']);
 const levelOf=F('_ZNK4GAME6Region8GetLevelEb','pointer',['pointer','bool']);
 const worldOf=F('_ZN4GAME6Region8GetWorldEv','pointer',['pointer']);
 const loading=F('_ZNK4GAME5World19GetNumLevelsLoadingEv','int',['pointer']);
 const loaded=F('_ZNK4GAME5Level8IsLoadedEv','bool',['pointer']);
 const postLoaded=F('_ZNK4GAME5Level17GetPostLoadCalledEv','bool',['pointer']);
 const levelRegion=F('_ZNK4GAME5Level9GetRegionEv','pointer',['pointer']);
 const meshOf=F('_ZN4GAME5Level17GetPathMeshRecastEv','pointer',['pointer']);
 const initialized=F('_ZNK4GAME14PathMeshRecast13IsInitializedEv','bool',['pointer']);
 const create=F('_ZN4GAME5Level14CreatePathMeshEv','void',['pointer']);
 const threadGlobal=engine.getExportByName('_ZN4GAME13gMainThreadIdE');
 const self=new NativeFunction(Process.getModuleByName('libc.so').getExportByName('pthread_self'),'pointer',[]);
 const gameEngine=engine.getExportByName('_ZN4GAME11gGameEngineE');
 const failed=new Set();
 let last='',stable=0,busy=false,stopped=false;
 const save=()=>onReport(report);
 const stop=(status,error)=>{stopped=true;report.status=status;if(error)report.error=String(error);save();};
 const listener=Interceptor.attach(gate.address(engine,'_ZN4GAME11UIPlayerHud12WidgetUpdateEi'),{onLeave(){
  if(stopped||busy)return;busy=true;
  try{
   // The build touches world data: only on the engine's main thread (full handle and its low bits).
   const thread=self();
   if(!thread.equals(threadGlobal.readPointer())||thread.toUInt32()!==mainThread()){stop('wrong-thread');return;}
   const game=gameEngine.readPointer();if(game.isNull())return;
   const player=getPlayer(game);if(player.isNull())return;
   const region=regionOf(player);if(region.isNull())return;
   const world=worldOf(region);if(world.isNull())return;
   if(loading(world)!==0){stable=0;return;}
   const level=levelOf(region,1);
   if(level.isNull()||!loaded(level)||!postLoaded(level)||!levelRegion(level).equals(region)){stable=0;return;}
   const mesh=meshOf(level);if(mesh.isNull()||initialized(mesh))return;
   const key=level.toString()+':'+mesh.toString();
   if(key!==last){last=key;stable=0;return;}
   if(++stable<settle||failed.has(key))return;
   if(report.built+report.failed>=limit){stop('limit-reached');return;}
   const started=Date.now();
   create(level);
   const after=meshOf(level),ok=levelOf(region,1).equals(level)&&!after.isNull()&&Boolean(initialized(after));
   if(ok)report.built++;else{report.failed++;failed.add(key);}
   if(report.levels.length<16)report.levels.push({ms:Date.now()-started,ok});
   report.status='active';stable=0;last='';save();
  }catch(e){stop('error',e);}finally{busy=false;}
 }});
 save();
 return listener;
}
