import { clamp,FRAME_COUNT } from "./transport";
export interface WheelPacket {deltaX:number;deltaY:number;deltaMode:number;timeStamp:number;ctrlKey:boolean;shiftKey:boolean}
/** Browsers expose deltas, not physical detents. Calibrate common 120px / 3-line
 * wheel packets and leave continuous, horizontal, page and zoom input native.
 * A continuous packet keeps the remainder of its burst native (180ms).
 */
export class WheelCalibration {
  private continuousAt=-Infinity;
  private remainder=0;
  target(packet:WheelPacket,y:number,range:number):number|null {
    const {deltaY:dy,deltaMode:mode,timeStamp:time}=packet;
    if(!Number.isFinite(dy+packet.deltaX+time+y+range)||!dy||range<=0||packet.ctrlKey||packet.shiftKey||packet.deltaX!==0||mode===2){this.remainder=0;return null;}
    const quantized=mode===1?Number.isInteger(dy):mode===0&&Math.abs(dy)>=80&&Number.isInteger(dy)&&Math.abs(dy)%20===0;
    if(!quantized){this.continuousAt=time;this.remainder=0;return null;}
    if(time-this.continuousAt<180){this.remainder=0;return null;}
    const detents=dy/(mode===1?3:120),exact=clamp(y+detents*range/(FRAME_COUNT-1)+this.remainder,0,range);
    const top=Math.round(exact);this.remainder=exact-top;
    return top;
  }
  reset():void{this.continuousAt=-Infinity;this.remainder=0;}
}
