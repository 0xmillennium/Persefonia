import { it,expect } from "vitest";
import { DeliveryTimeline } from "../src/metrics";
import { analyzeDelivery } from "../scripts/delivery-analysis.mjs";
it("Opt-in delivery observations retain ordered bounded history and disclose dropped events",()=>{
  const timeline=new DeliveryTimeline(3);
  for(let n=0;n<7;n++)timeline.record({stage:"selected",time:n,key:`tree:${n}`});
  expect(timeline.snapshot()).toMatchObject({total:7,dropped:4});
  expect(timeline.snapshot().events.map(e=>e.time)).toEqual([4,5,6]);
});
it("Moving delay distinguishes never-admitted generations, readiness and stationary captured endpoints",()=>{
  const events=[
    {stage:"measure-start",time:0},{stage:"input",time:0,index:1},
    {stage:"selected",time:10,key:"robotic-arm:1",version:1,cacheHit:false,active:[]},
    {stage:"queued",time:10,key:"robotic-arm:1",id:1},
    {stage:"admitted",time:20,key:"robotic-arm:1",id:1,current:true},
    {stage:"ready",time:40,key:"robotic-arm:1",id:1,current:true},
    {stage:"settled",time:41,key:"robotic-arm:1",id:1},
    {stage:"draw-submitted",time:45,key:"robotic-arm:1",version:1,accepted:true},
    {stage:"input",time:55,index:2},
    {stage:"selected",time:60,key:"robotic-arm:2",version:2,cacheHit:false,active:[]},
    {stage:"queued",time:60,key:"robotic-arm:2",id:2},
    {stage:"input",time:79,index:3},
    {stage:"selected",time:80,key:"robotic-arm:3",version:3,cacheHit:true,active:[]},
    {stage:"draw-submitted",time:90,key:"robotic-arm:3",version:3,accepted:true},
    {stage:"measure-end",time:100}
  ];
  const captured=[{time:5,index:0,valid:true},{time:25,index:0,valid:true},{time:50,index:1,valid:true},{time:75,index:1,valid:true},{time:95,index:3,valid:true},{time:150,index:3,valid:true}];
  const result=analyzeDelivery({initial:{state:{drawn:"robotic-arm:0"}},ending:{timeline:{events},state:{drawn:"robotic-arm:3"}},captured});
  expect(result).toMatchObject({selectedTargets:3,neverAdmittedBeforeSuperseded:1,neverReadyBeforeSuperseded:1,neverDrawnBeforeSuperseded:1,submittedMovingMismatch:{maxMs:35,totalMs:65},maxMovingCapturedDuplicateMs:40,stopDrawWaitMs:0});
  expect(result.targetRecords[0]).toMatchObject({admissionMs:10,readyMs:30,drawMs:35,readyToDrawMs:5});
  expect(result.targetRecords[1]).toMatchObject({admissionMs:null,readyMs:null,drawMs:null});
});
