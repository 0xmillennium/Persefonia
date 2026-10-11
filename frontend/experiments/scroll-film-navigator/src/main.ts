import { FrameCache, TraversalEstimator, FilmTransport, keyFor, otherFilm, FILMS, scrollAt, type Film, type FrameKey } from "./transport";
import { Hold, type Modality } from "./hold";
import { GpuFluid } from "./gpu-fluid";
import { LiquidField } from "./field";
import { PointerObserver, eligible } from "./input";
import { HoldMaterial } from "./hold-material";
import { Renderer } from "./renderer";
import { ImageRenderer } from "./image-renderer";
import { CanvasRenderer } from "./canvas-renderer";
import { Metrics } from "./metrics";
import { loadBitmap } from "./loader";

const element=<T extends HTMLElement>(id: string)=>document.getElementById(id) as T;
const stage=element("stage"), canvas=element<HTMLCanvasElement>("film"), fallback=element<HTMLCanvasElement>("fallback");
const core=element("core"),countdown=element<HTMLOutputElement>("countdown"),message=element("message");
const slider=element<HTMLInputElement>("progress"), frame=element("frame"),selection=element("selection"),debug=element("debug");
const robot=element<HTMLButtonElement>("robot"),tree=element<HTMLButtonElement>("tree"),retry=element<HTMLButtonElement>("retry"),debugToggle=element<HTMLButtonElement>("debug-toggle");
const transport=new FilmTransport(),hold=new Hold(),pointer=new PointerObserver(),metrics=new Metrics();
const motion=matchMedia("(prefers-reduced-motion: reduce)");
// Tier is stable for this session. Resize cannot churn decoded/GPU resources or request generations.
const poster=element<HTMLImageElement>("poster"),posterLayer=element("poster-layer");
const mediaVersion=document.querySelector<HTMLMetaElement>('meta[name="film-media-version"]')!.content;
const tier=window.innerWidth<=700?960:1600;
const traversal=new TraversalEstimator();
let width=window.innerWidth,height=window.innerHeight,field:LiquidField|GpuFluid=new LiquidField(width,height),material=new HoldMaterial(width,height);
let contextControl: WEBGL_lose_context|null=null;
let renderer: Renderer|CanvasRenderer|ImageRenderer|null=null,renderFailure: string|null=null,acceleratedFailure:string|null=null,disposed=false,suspended=document.hidden;
const accelerated=new URLSearchParams(location.search).get("renderer")!=="basic";
let persistenceTimer=0,lastPersistence=0,historyFailure:string|null=null;
let testingFrozen=false,testingDelta=0,suspendedAt=performance.now();
let raf=0,lastTime=0,materialTime=0,debugTime=0,gestureId:number|null=null,captureExpected=false,requestSignature="",presentedNext:FrameKey|null=null;
let requestStarted=performance.now(),observedVersion=-1,decodedVersion=-1,drawnKey:FrameKey|null=null,pointerVisible=false,coverageValue=0,reveal=false;
let countdownBounds:{left:number;right:number;top:number;bottom:number}|null=null,countdownValue="";
const fallbackContext=fallback.getContext("2d",{alpha:false,willReadFrequently:true});
const abortEvents=new AbortController();
const events={signal:abortEvents.signal};
const cache=new FrameCache<ImageBitmap>(async (film,index,signal,attempt)=>{
  const start=performance.now();
  let bitmap:ImageBitmap;
  if(film==="robotic-arm"&&index===0&&attempt===0){
    await poster.decode();if(signal.aborted)throw new DOMException("Aborted","AbortError");
    bitmap=await createImageBitmap(poster);if(signal.aborted){bitmap.close();throw new DOMException("Aborted","AbortError");}
  }else bitmap=await loadBitmap(`/media/${mediaVersion}/${tier}/${film}/frame_${String(index+1).padStart(5,"0")}.webp${attempt?`?retry=${attempt}`:""}`,signal);
  metrics.decode.add(performance.now()-start);
  if(film===transport.film&&index===transport.desired&&decodedVersion!==transport.version){decodedVersion=transport.version;metrics.targetToDecode.add(performance.now()-requestStarted);}
  return bitmap;
},20,2,()=>{requestSignature="";requestFrames();wake();});

function range(): number {return document.documentElement.scrollHeight-window.innerHeight;}
function state(): object {return {
  film:transport.film,progress:transport.progress,desired:transport.desired,presented:transport.presented,drawn:drawnKey,next:presentedNext,
  uploaded:renderer?.residentKeys??[],presentationEvidence:"draw submission; captured video/trace is evaluated separately",version:transport.version,commits:transport.commits,hold:{phase:hold.phase,revealProgress:hold.revealProgress,revealCenter:{...hold.revealCenter},material:{bytes:material.bytes,area:material.area,coverage:material.coverage,generations:material.generations,revision:material.revision},progress:hold.progress,expansion:hold.expansion,center:{...hold.center},target:{...hold.target},countdown:hold.countdown,pressed:hold.pressed,cycles:hold.cycles,coverage:coverageValue},
  frameStages:{desired:keyFor(transport.film,transport.desired),scheduled:cache.scheduledKeys,decoding:cache.decodingKeys,decoded:[...cache.entries.keys()],uploaded:renderer?.residentKeys??[],drawSubmitted:drawnKey},
  modality:pointer.modality,core:{visible:pointerVisible,x:`${core.dataset.x??""}px`,y:`${core.dataset.y??""}px`},field:{backend:field instanceof GpuFluid?"gpu-fluid":"basic",passes:field instanceof GpuFluid?field.passes:0,droppedSamples:field instanceof GpuFluid?field.droppedSamples:0,transportSecondsDiscarded:field instanceof GpuFluid?field.transportSecondsDiscarded:0,active:field.active,energyEvidence:field instanceof GpuFluid?"last explicit inspection readback; not a live per-frame metric":"CPU fallback sample",energy:field.energy,peak:field.peak,injections:field.injections,bytes:field.bytes},
  cache:{size:cache.entries.size,limit:cache.limit,pending:cache.pending,queued:cache.queued,peakPending:cache.peakPending,bytes:cache.bytes,hits:cache.hits,misses:cache.misses,evictions:cache.evictions,staleCompletions:cache.staleCompletions,failures:cache.failures,error:cache.error},
  capture:{id:gestureId,expected:captureExpected,owned:gestureId!==null&&stage.hasPointerCapture(gestureId)},
  renderer:{kind:renderer?.kind??null,bytes:renderer?.bytes??0,uploads:renderer?.uploads??0,draws:renderer?.draws??0,info:renderer?.info??null,acceleratedFailure},
  gpu:{bytes:renderer instanceof Renderer?renderer.bytes:0,textures:renderer instanceof Renderer?renderer.textureCount:0,uploads:renderer instanceof Renderer?renderer.uploads:0,draws:renderer instanceof Renderer?renderer.draws:0,queriesBound:4,info:renderer instanceof Renderer?renderer.info:null,samples:renderer instanceof Renderer?renderer.gpuSamples:[]},
  stalePresentations:transport.stalePresentations,reveal,renderFailure,historyFailure,persistencePending:!!persistenceTimer,tier,mediaVersion,reduced:motion.matches,suspended,disposed,scheduled:!!raf,metrics:metrics.report(),viewport:{width,height,dpr:devicePixelRatio}
};}
function durable(force=false): void {
  if(disposed||suspended||document.hidden)return;
  const saved=history.state?.scrollFilmNavigator;
  if(saved?.film===transport.film&&saved?.y===window.scrollY)return;
  const now=performance.now();
  if(!force&&now-lastPersistence<500) {
    if(!persistenceTimer)persistenceTimer=window.setTimeout(()=>{persistenceTimer=0;durable();},500-(now-lastPersistence));
    return;
  }
  if(persistenceTimer){clearTimeout(persistenceTimer);persistenceTimer=0;}
  const savedState={film:transport.film,y:window.scrollY};
  try {history.replaceState({...history.state,scrollFilmNavigator:savedState},"");lastPersistence=now;historyFailure=null;}
  catch(error){historyFailure=String(error);lastPersistence=now;}
}
function restore(preserveFluid=false): void {
  const saved=history.state?.scrollFilmNavigator as {film?:Film;y?:number}|undefined;
  if(saved?.film&&FILMS.includes(saved.film))transport.restore(saved.film);
  // Native history/reload restoration owns scroll position. A throttled history
  // snapshot can lag the latest scroll and must never override browser restoration.
  hold.reset();material.clear();if(!preserveFluid)field.clear();pointer.reset();requestSignature="";presentedNext=null;drawnKey=null;
  syncScroll();wake();
}
function syncScroll(): void {transport.setProgress(scrollAt(window.scrollY,range()));traversal.observe(transport.desired,performance.now());requestFrames();wake();}
function requestFrames(): void {
  if(suspended||disposed)return;
  const targets=[{film:transport.film,index:transport.desired}];
  if(hold.preparing)targets.push({film:otherFilm(transport.film),index:transport.desired});
  const signature=targets.map(t=>keyFor(t.film,t.index)).join("|")+`:${traversal.direction}:${Math.ceil(traversal.speed*.18)}`;
  if(transport.version!==observedVersion){observedVersion=transport.version;requestStarted=performance.now();if(cache.entries.has(keyFor(transport.film,transport.desired))){decodedVersion=transport.version;metrics.targetToDecode.add(0);}}
  if(signature===requestSignature)return;
  requestSignature=signature;
  cache.request(targets,renderer?.residentKeys??(transport.presented?[transport.presented]:[]),traversal.hint);
}
function initializeRenderer(): void {
  renderer?.dispose();renderer=null;
  if(accelerated) {
    try {renderer=new Renderer(canvas,import.meta.env.DEV&&new URLSearchParams(location.search).has("capture"));renderer.resize(width,height);field=renderer.fluid!;renderFailure=null;fallback.hidden=true;canvas.hidden=false;return;}
    catch(error){acceleratedFailure=String(error);renderer?.dispose();renderer=null;}
  }
  initializeCanvas();
}
function initializeCanvas():CanvasRenderer|ImageRenderer {
  try {const created=new CanvasRenderer(fallback);renderer=created;created.resize(width,height);field=new LiquidField(width,height);renderFailure=null;fallback.hidden=false;canvas.hidden=true;return created;}
  catch(error){renderFailure=String(error);renderer?.dispose();renderer=new ImageRenderer(posterLayer,mediaVersion,tier,wake);field=new LiquidField(width,height);fallback.hidden=true;canvas.hidden=true;return renderer;}
}
function drawFallback(image: ImageBitmap): void {
  if(!fallbackContext)return;
  const ctx=fallbackContext;
  ctx.fillStyle="#0c0f0d";ctx.fillRect(0,0,fallback.width,fallback.height);
  const portrait=width/height<.9;
  const scale=portrait?width/image.width:Math.max(width/image.width,height/image.height);
  const w=image.width*scale,h=image.height*scale;
  ctx.drawImage(image,(width-w)*(transport.film==="robotic-arm"?.56:.5),(height-h)*.5,w,h);
}
function wake(): void {if(!raf&&!disposed&&!suspended){lastTime=0;raf=requestAnimationFrame(tick);}}
function tick(now:number): void {
  raf=0;if(disposed||suspended)return;
  const start=performance.now(),elapsed=import.meta.env.DEV&&testingFrozen?testingDelta:lastTime?(now-lastTime)/1000:1/60;
  testingDelta=0;
  if(lastTime){metrics.intervals.add(now-lastTime);if(now-lastTime>25)metrics.frameDrops++;}
  lastTime=now;
  if(renderer instanceof Renderer)renderer.beginFrame();
  field.step(elapsed);if(hold.active)materialTime+=Math.min(elapsed,.05);
  const activeKey=keyFor(transport.film,transport.desired),nextKey=keyFor(otherFilm(transport.film),transport.desired);
  const active=cache.get(activeKey),next=hold.preparing?cache.get(nextKey):undefined;
  const paired=!!active&&!!next;
  material.step(hold);coverageValue=material.coverage;
  const reduced=motion.matches||!renderer||renderer instanceof ImageRenderer;
  // Keep the last matching pair while scrub loads asynchronously. Never reveal mismatched indices.
  let submittedKey:FrameKey|null=null;
  if(renderer) {
    try {
      const uploadStarted=performance.now();
      if(active&&(hold.phase!=="REVEALING"||paired)) {renderer.upload(0,activeKey,active);submittedKey=activeKey;}
      if(paired) {renderer.upload(1,nextKey,next!);presentedNext=nextKey;reveal=hold.phase==="REVEALING"&&!reduced;}
      else if(hold.phase!=="REVEALING") {presentedNext=null;reveal=false;}
      metrics.upload.add(performance.now()-uploadStarted);
    } catch(error) {
      const wasAccelerated=renderer instanceof Renderer;renderer.dispose();renderer=null;field.clear();hold.reset();
      if(wasAccelerated){acceleratedFailure=String(error);const created=initializeCanvas();requestSignature="";requestFrames();if(active){created?.upload(0,activeKey,active);submittedKey=activeKey;}}
      else {renderFailure=String(error);fallback.hidden=false;canvas.hidden=true;}
    }
  } else if(active) {drawFallback(active);submittedKey=activeKey;}
  // Readiness belongs to the latest target, independently of the elapsed hold clock.
  const commit=hold.step(elapsed,paired,coverageValue,reduced);
  material.step(hold);coverageValue=material.coverage;reveal=hold.phase==="REVEALING"&&!reduced;
  if(commit) {
    // The outgoing draw was already fully covered, so exchanging texture ownership is continuous.
    transport.switchFilm();renderer?.swap();submittedKey=keyFor(transport.film,transport.desired);
    reveal=false;presentedNext=null;requestSignature="";requestFrames();durable(true);
    if(!renderer&&next)drawFallback(next);
  }
  const submit=performance.now();
  try {const submitted=renderer?.draw({field,material,hold,time:materialTime,reveal,reduced,current:transport.film,next:otherFilm(transport.film)});if(renderer instanceof Renderer&&submitted===false)submittedKey=null;}
  catch(error){renderFailure=String(error);renderer?.dispose();renderer=null;hold.reset();field.clear();releaseCapture();fallback.width=width;fallback.height=height;fallback.hidden=false;canvas.hidden=true;const plain=commit?next:active;if(plain){drawFallback(plain);submittedKey=keyFor(transport.film,transport.desired);}}
  if(renderer instanceof ImageRenderer)submittedKey=renderer.presentedKey===keyFor(transport.film,transport.desired)?renderer.presentedKey:null;
  if(submittedKey)transport.present(submittedKey);
  metrics.drawSubmission.add(performance.now()-submit);
  if(transport.presented && !renderFailure && !(renderer instanceof ImageRenderer))posterLayer.hidden=true;
  if(transport.presented===keyFor(transport.film,transport.desired)&&drawnKey!==transport.presented) {
    drawnKey=transport.presented;metrics.targetToDraw.add(performance.now()-requestStarted);
  }
  updateControls(now);metrics.javascript.add(performance.now()-start);
  if(field.active||hold.animating)raf=requestAnimationFrame(tick);else {lastTime=0;durable();}
}
function updateControls(now:number): void {
  element("quality").hidden=renderer instanceof Renderer;
  slider.value=String(Math.round(transport.progress*1000));frame.textContent=`${String(transport.desired+1).padStart(3,"0")} / 192`;
  robot.setAttribute("aria-pressed",String(transport.film===FILMS[0]));tree.setAttribute("aria-pressed",String(transport.film===FILMS[1]));
  const label=transport.film==="robotic-arm"?"Robotic arm":"Tree";
  const incoming=transport.film==="robotic-arm"?"Tree":"Robotic arm";
  const waiting=hold.phase==="COVERED_WAIT_READY"&&(!cache.entries.has(keyFor(transport.film,transport.desired))||!cache.entries.has(keyFor(otherFilm(transport.film),transport.desired)));
  const number=hold.phase==="GROWING"?hold.countdown:null;
  const status=`${label} selected.${hold.phase==="COVERED_WAIT_READY"?waiting?` Preparing ${incoming} at this film position.`:` Changing to ${incoming}.`:number!==null?` Hold: ${number}.`:hold.phase==="CANCELING"?" Hold canceled.":""}`;
  if(selection.textContent!==status)selection.textContent=status;
  countdown.hidden=number===null;
  if(number!==null) {
    const value=String(number);countdown.textContent=value;
    if(!countdownBounds||countdownValue!==value) {
      // Read geometry only on a number/layout change, never for every fluid tick.
      const box=countdown.getBoundingClientRect(),top=stage.querySelector("header")!.getBoundingClientRect(),bottom=stage.querySelector("footer")!.getBoundingClientRect();
      countdownBounds={left:top.left+box.width*.5,right:top.right-box.width*.5,top:top.bottom+12+box.height*.5,bottom:bottom.top-12-box.height*.5};countdownValue=value;
    }
    const bounds=countdownBounds;
    countdown.hidden=bounds.bottom<bounds.top||bounds.right<bounds.left;
    const x=Math.min(bounds.right,Math.max(bounds.left,hold.center.x)),y=Math.min(bounds.bottom,Math.max(bounds.top,hold.center.y));
    countdown.style.transform=`translate(${x}px, ${y}px) translate(-50%, -50%)`;
  }
  if(cache.error){message.hidden=false;message.textContent=`Frame unavailable. Last valid frame retained. ${cache.error}`;retry.hidden=false;}
  else if(renderer instanceof ImageRenderer&&renderer.failure){message.hidden=false;message.textContent=renderer.failure;retry.hidden=false;}
  else if(hold.failure){message.hidden=false;message.textContent=hold.failure;retry.hidden= !cache.error;}
  else if(renderer instanceof ImageRenderer&&renderer.failure){message.hidden=false;message.textContent=renderer.failure;retry.hidden=false;}
  else if(renderFailure){message.hidden=false;message.textContent="Graphics unavailable. Plain film mode; scene and position controls remain available.";retry.hidden=true;}
  else if(!transport.presented){message.hidden=false;message.textContent="Preparing the first frame…";}
  else if(waiting){message.hidden=false;message.textContent=`Preparing ${incoming} at this film position…`;retry.hidden=true;}
  else if(transport.presented!==keyFor(transport.film,transport.desired)){message.hidden=false;message.textContent=`Preparing frame ${String(transport.desired+1).padStart(3,"0")}…`;retry.hidden=true;}
  else {message.hidden=true;retry.hidden=true;}
  const pending=String(!!transport.presented&&!cache.error&&!renderFailure);
  if(message.dataset.pending!==pending)message.dataset.pending=pending;
  if(import.meta.env.DEV&&!debug.hidden&&now-debugTime>250){debugTime=now;debug.textContent=JSON.stringify(state(),null,2);}
}
function releaseCapture(): void {
  const id=gestureId;gestureId=null;captureExpected=false;
  if(id!==null&&stage.hasPointerCapture(id))stage.releasePointerCapture(id);
}
function interrupt(): void {hold.release();releaseCapture();pointer.reset();core.hidden=true;pointerVisible=false;wake();}
function resize(): void {
  const start=performance.now();width=window.innerWidth;height=window.innerHeight;countdownBounds=null;
  pointer.reset();core.hidden=true;pointerVisible=false;
  hold.reset();material=new HoldMaterial(width,height);releaseCapture();field.clear();try{renderer?.resize(width,height);}catch(error){acceleratedFailure=String(error);renderer?.dispose();initializeCanvas();}
  field=renderer instanceof Renderer?renderer.fluid!:new LiquidField(width,height);
  if(!(renderer instanceof CanvasRenderer)){fallback.width=width;fallback.height=height;}syncScroll();metrics.resize.add(performance.now()-start);
}
function selectFilm(film:Film): void {
  if(film===transport.film){hold.cancel();interrupt();return;}
  releaseCapture();hold.select({x:width*.5,y:height*.5});pointer.modality="keyboard";
  requestSignature="";requestFrames();wake();
}
stage.addEventListener("pointerdown",event=>{
  const excluded=!!(event.target as Element).closest("[data-control],button,input,label,a");
  const point={x:event.clientX,y:event.clientY};
  if(!eligible(event.button,event.isPrimary,excluded,point,width,height)||gestureId!==null||suspended)return;
  const modality=event.pointerType as Modality;pointer.modality=modality;
  if(modality==="touch"){pointer.reset();core.hidden=true;pointerVisible=false;}
  if(hold.press(point,modality)) {
    gestureId=event.pointerId;
    try {stage.setPointerCapture(event.pointerId);captureExpected=stage.hasPointerCapture(event.pointerId);} catch {captureExpected=false; /* window listeners still observe eligible live input */ }
    requestSignature="";requestFrames();wake();
  }
},events);
window.addEventListener("pointermove",event=>{
  if(suspended||disposed)return;
  if(gestureId===event.pointerId) {
    // Some engines drop pending capture before dispatching got/lostpointercapture.
    // Verify ownership on the next physical packet as well as listening for loss.
    if(captureExpected&&!stage.hasPointerCapture(event.pointerId))interrupt();
    else {hold.move({x:event.clientX,y:event.clientY});if(!hold.pressed)releaseCapture();wake();}
  }
  pointer.modality=event.pointerType as Modality;
  if(event.pointerType==="touch") {pointer.reset();core.hidden=true;pointerVisible=false;return;}
  const excluded=!!(event.target as Element).closest("[data-control],button,input,label,a");
  const visible=event.clientX>=0&&event.clientY>=0&&event.clientX<=width&&event.clientY<=height&&!excluded;
  core.hidden=!visible||motion.matches;pointerVisible=visible&&!motion.matches;
  core.style.transform=`translate(${event.clientX}px, ${event.clientY}px) translate(-50%, -50%)`;
  core.dataset.x=String(event.clientX);core.dataset.y=String(event.clientY);
  if(!visible||motion.matches||!(renderer instanceof Renderer)) {pointer.reset();return;}
  const inputStart=performance.now();
  const coalesced=event.getCoalescedEvents?.();
  const samples=coalesced?.length?coalesced:[event];
  for(const sample of samples) {const observed=pointer.observe(sample.clientX,sample.clientY,sample.timeStamp);if(observed)field.inject(observed);}
  metrics.input.add(performance.now()-inputStart);wake();
},events);
window.addEventListener("pointerup",event=>{if(event.pointerId===gestureId)interrupt();},events);
window.addEventListener("pointercancel",event=>{if(event.pointerId===gestureId)interrupt();},events);
stage.addEventListener("lostpointercapture",event=>{if(event.pointerId===gestureId)interrupt();},events);
stage.addEventListener("pointerleave",()=>{core.hidden=true;pointerVisible=false;pointer.reset();},events);
window.addEventListener("blur",interrupt,events);
window.addEventListener("scroll",syncScroll,{...events,passive:true});
window.addEventListener("resize",resize,events);
window.addEventListener("orientationchange",resize,events);
slider.addEventListener("input",()=>window.scrollTo({top:Number(slider.value)/1000*range(),behavior:"instant"}),events);
robot.addEventListener("click",()=>selectFilm(FILMS[0]),events);tree.addEventListener("click",()=>selectFilm(FILMS[1]),events);
retry.addEventListener("click",()=>{cache.retry();if(renderer instanceof ImageRenderer)renderer.retry();requestSignature="";requestFrames();wake();},events);
motion.addEventListener("change",()=>{field.clear();core.hidden=true;pointerVisible=false;wake();},events);
function suspend(): void {
  // During traversal WebKit may already expose the destination entry here.
  // Never replace history from an outgoing/inactive document.
  if(!suspended)suspendedAt=performance.now();
  suspended=true;if(persistenceTimer)clearTimeout(persistenceTimer);persistenceTimer=0;
  cache.suspend();if(raf)cancelAnimationFrame(raf);raf=0;lastTime=0;
  if(renderer instanceof ImageRenderer)renderer.suspend();
  hold.reset();material.clear();releaseCapture();pointer.reset();core.hidden=true;pointerVisible=false;
}
function resume(): void {if(disposed||!suspended)return;const elapsed=(performance.now()-suspendedAt)/1000;suspended=false;lastTime=0;field.step(elapsed);restore(true);}
document.addEventListener("visibilitychange",()=>document.hidden?suspend():resume(),events);
window.addEventListener("pagehide",suspend,events);
window.addEventListener("pageshow",resume,events);
window.addEventListener("popstate",()=>{restore();syncScroll();},events);
canvas.addEventListener("webglcontextlost",event=>{
  event.preventDefault();acceleratedFailure="WebGL context lost";hold.reset();field.clear();releaseCapture();
  renderer?.dispose();renderer=null;initializeCanvas();requestSignature="";drawnKey=null;syncScroll();
},events);
canvas.addEventListener("webglcontextrestored",()=>{initializeRenderer();drawnKey=null;requestSignature="";syncScroll();},events);
function dispose(): void {
  if(disposed)return;suspend();if(persistenceTimer)clearTimeout(persistenceTimer);persistenceTimer=0;disposed=true;abortEvents.abort();cache.dispose();renderer?.dispose();renderer=null;metrics.dispose();field.clear();
}
if(import.meta.env.DEV) {
  debugToggle.hidden=false;
  debugToggle.addEventListener("click",()=>{debug.hidden=!debug.hidden;debugToggle.setAttribute("aria-expanded",String(!debug.hidden));wake();},events);
  // Dev-only deterministic inspection and lifecycle testing; stripped from production artifacts.
  Object.assign(window,{__filmExperiment:{state,freeze:(frozen:boolean)=>{testingFrozen=frozen;testingDelta=0;wake();},advance:async(seconds:number)=>{
    if(!testingFrozen || !Number.isFinite(seconds) || seconds<0 || seconds>10)throw new Error("Freeze first; advance 0–10 seconds");
    const count=hold.phase==="SUCCESS_WAIT_RELEASE"||(!hold.animating&&(!field.active||seconds>=4.2))?1:Math.ceil(seconds*60);
    for(let i=0;i<count;i++){testingDelta=seconds/count;wake();await new Promise<void>(resolve=>requestAnimationFrame(()=>resolve()));}
  },resetMetrics:()=>metrics.reset(),clearFluid:()=>{field.clear();pointer.reset();wake();},fieldMetrics:()=>field.metrics(),dispose,
    suspend,resume,contextLoss:()=>{contextControl=renderer instanceof Renderer?renderer.gl.getExtension("WEBGL_lose_context"):null;contextControl?.loseContext();},
    contextRestore:()=>contextControl?.restoreContext()
  }});
}
initializeRenderer();restore();
for(const control of [robot,tree,slider])control.disabled=false;
let resolution=matchMedia(`(resolution: ${devicePixelRatio}dppx)`);
const resolutionChange=()=>{resolution.removeEventListener("change",resolutionChange);resize();resolution=matchMedia(`(resolution: ${devicePixelRatio}dppx)`);resolution.addEventListener("change",resolutionChange,events);};
resolution.addEventListener("change",resolutionChange,events);
