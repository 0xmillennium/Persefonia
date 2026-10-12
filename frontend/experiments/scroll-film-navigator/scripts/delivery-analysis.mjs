const summary=values=>{
  const sorted=values.filter(Number.isFinite).sort((a,b)=>a-b),at=p=>sorted[Math.min(sorted.length-1,Math.floor(sorted.length*p))]??null;
  return {count:sorted.length,p50:at(.5),p95:at(.95),max:at(1)};
};
// Longest continuous mismatch during active input, never a stationary endpoint.
function mismatch(events,start,end,initialKey){
  let desired=initialKey,drawn=initialKey,since=null,max=0,total=0;
  for(const e of [...events,{stage:'end',time:end}]){
    if(e.time<start||e.time>end)continue;
    if(since!==null){const duration=e.time-since;max=Math.max(max,duration);}
    const was=desired!==drawn;
    if(e.stage==='selected')desired=e.key;
    if(e.stage==='draw-submitted'&&e.key)drawn=e.key;
    if(!was&&desired!==drawn)since=e.time;
    if(was&&desired===drawn){total+=e.time-since;since=null;}
  }
  if(since!==null)total+=end-since;
  return {maxMs:max,totalMs:total};
}
export function analyzeDelivery(run){
  const all=run.ending.timeline.events;
  const marker=all.filter(e=>e.stage==='measure-start').at(-1),endMarker=all.find(e=>e.stage==='measure-end'&&e.time>=marker.time);
  const start=marker.time,end=endMarker.time,events=all.filter(e=>e.time>=start&&e.time<=end);
  const selections=events.filter(e=>e.stage==='selected'),draws=all.filter(e=>e.stage==='draw-submitted'),requests=new Map(),active=new Map();
  for(const e of all){
    if(e.stage==='queued')requests.set(e.id,{key:e.key,queued:e.time});
    if(e.stage==='admitted'){const r=requests.get(e.id)??{key:e.key};Object.assign(r,{admitted:e.time,currentAtAdmission:e.current});requests.set(e.id,r);active.set(e.key,r);}
    const r=e.id?requests.get(e.id):active.get(e.key);
    if(r&&['image-start','image-ready','bitmap-start','bitmap-ready','cancel','ready','obsolete','settled'].includes(e.stage)){
      r[e.stage]=e.time;if(e.stage==='ready')r.currentAtCompletion=e.current;
    }
    if(e.stage==='settled')active.delete(e.key);
  }
  const records=[...requests.values()],targetRecords=[];
  for(let n=0;n<selections.length;n++){
    const selected=selections[n],until=selections[n+1]?.time??end;
    const r=records.filter(r=>r.key===selected.key&&r.admitted!==undefined&&r.admitted<until&&(r.settled??Infinity)>=selected.time).sort((a,b)=>a.admitted-b.admitted)[0];
    const admitted=selected.cacheHit?selected.time:r&&(!r.cancel||r.cancel>selected.time)?Math.max(selected.time,r.admitted):null;
    const ready=selected.cacheHit?selected.time:r?.ready!==undefined&&r.ready<until?Math.max(selected.time,r.ready):null;
    const draw=draws.find(d=>d.time>=selected.time&&d.time<until&&d.key===selected.key&&d.accepted&&d.version===selected.version);
    const busy=(selected.active??[]).map(key=>records.find(r=>r.key===key&&r.admitted<=selected.time&&(r.settled??Infinity)>=selected.time));
    targetRecords.push({key:selected.key,version:selected.version,selected:selected.time,until,cacheHit:selected.cacheHit,admissionMs:admitted===null?null:admitted-selected.time,readyMs:ready===null?null:ready-selected.time,drawMs:draw?draw.time-selected.time:null,readyToDrawMs:draw&&ready!==null?draw.time-ready:null,blockedByTwoSpeculative:!selected.cacheHit&&busy.length===2&&busy.every(r=>r&&!r.currentAtAdmission&&r.key!==selected.key),blockedByOccupiedSlots:!selected.cacheHit&&(selected.active??[]).length===2&&!(selected.active??[]).includes(selected.key)});
  }
  const admitted=records.filter(r=>r.admitted>=start&&r.admitted<=end),completed=admitted.filter(r=>r.ready!==undefined),canceled=admitted.filter(r=>r.cancel!==undefined);
  const raf=events.filter(e=>e.stage==='raf'),inputs=events.filter(e=>e.stage==='input');
  const captured=run.captured.filter(f=>f.time>=start&&f.time<=end),valid=captured.filter(f=>f.valid);
  let capturedMovingHold=0;
  for(let n=0;n<valid.length;){
    let next=n+1;while(next<valid.length&&valid[next].index===valid[n].index)next++;
    const left=valid[n].time,right=valid[next]?.time??valid.at(-1).time,key=`robotic-arm:${valid[n].index}`;
    // The fixed captured identity is distinct from the authoritative target.
    const selectedBefore=all.filter(e=>e.stage==='selected'&&e.time<=left).at(-1);
    const changing=events.filter(e=>e.stage==='selected'&&e.time>=left&&e.time<=right);
    let since=(selectedBefore?.key??run.initial.state.drawn)!==key?left:null;
    for(const e of [...changing,{key:null,time:right}]){
      if(since!==null)capturedMovingHold=Math.max(capturedMovingHold,e.time-since);
      if(e.key===key)since=null;else if(since===null)since=e.time;
    }
    n=next;
  }
  const lastInput=inputs.at(-1),stopDraw=draws.find(d=>d.time>=end&&d.accepted&&d.key===`robotic-arm:${lastInput.index}`);
  return {
    movingMs:end-start,selectedTargets:selections.length,cacheHitTargets:targetRecords.filter(r=>r.cacheHit).length,
    neverAdmittedBeforeSuperseded:targetRecords.filter(r=>r.admissionMs===null).length,neverReadyBeforeSuperseded:targetRecords.filter(r=>r.readyMs===null).length,neverDrawnBeforeSuperseded:targetRecords.filter(r=>r.drawMs===null).length,
    blockedByTwoSpeculative:targetRecords.filter(r=>r.blockedByTwoSpeculative).length,blockedByOccupiedSlots:targetRecords.filter(r=>r.blockedByOccupiedSlots).length,
    targetAdmissionMs:summary(targetRecords.map(r=>r.admissionMs).filter(v=>v!==null)),targetReadyMs:summary(targetRecords.map(r=>r.readyMs).filter(v=>v!==null)),targetDrawMs:summary(targetRecords.map(r=>r.drawMs).filter(v=>v!==null)),readyToDrawMs:summary(targetRecords.map(r=>r.readyToDrawMs).filter(v=>v!==null)),
    submittedMovingMismatch:mismatch(events,start,end,run.initial.state.drawn),
    decodeAdmissions:admitted.length,currentAdmissions:admitted.filter(r=>r.currentAtAdmission).length,speculativeAdmissions:admitted.filter(r=>!r.currentAtAdmission).length,
    successfulCompletions:completed.length,currentAtCompletion:completed.filter(r=>r.currentAtCompletion).length,canceledRequests:canceled.length,obsoleteBitmaps:admitted.filter(r=>r.obsolete!==undefined).length,
    queueWaitMs:summary(admitted.map(r=>r.admitted-r.queued)),imageAvailabilityMs:summary(completed.map(r=>r['image-ready']-r['image-start'])),bitmapOperationMs:summary(completed.map(r=>r['bitmap-ready']-r['bitmap-start'])),loadSlotMs:summary(admitted.filter(r=>r.settled!==undefined).map(r=>r.settled-r.admitted)),abortToSettlementMs:summary(canceled.filter(r=>r.settled!==undefined).map(r=>r.settled-r.cancel)),
    inputIntervalsMs:summary(inputs.slice(1).map((e,n)=>e.time-inputs[n].time)),rafIntervalsMs:summary(raf.slice(1).map((e,n)=>e.time-raf[n].time)),
    inputToSelectionMs:summary(selections.map(e=>{const input=inputs.filter(i=>i.time<=e.time&&i.index===Number(e.key.split(':')[1])).at(-1);return input?e.time-input.time:NaN;})),
    captures:captured.length,invalidCapturedIdentities:captured.length-valid.length,uniqueCapturedIdentities:new Set(valid.map(f=>f.index)).size,captureIntervalsMs:summary(captured.slice(1).map((f,n)=>f.time-captured[n].time)),maxMovingCapturedDuplicateMs:captured.length?capturedMovingHold:null,
    stopTarget:lastInput.index,stopDrawWaitMs:stopDraw?stopDraw.time-end:run.ending.state.drawn===`robotic-arm:${lastInput.index}`?0:null,
    targetRecords
  };
}
