import { inflateSync } from "node:zlib";
// Playwright screenshot PNGs: lossless RGB/RGBA, noninterlaced. No image dependency required.
export function pixels(png:Buffer):{data:Uint8Array;width:number;height:number;channels:number} {
  const width=png.readUInt32BE(16),height=png.readUInt32BE(20),type=png[25];
  if(png[24]!==8 || png[28]!==0 || (type!==2&&type!==6))throw new Error("Unsupported screenshot PNG");
  const channels=type===6?4:3,compressed:Buffer[]=[];
  for(let offset=8;offset<png.length;) {const n=png.readUInt32BE(offset);if(png.toString("ascii",offset+4,offset+8)==="IDAT")compressed.push(png.subarray(offset+8,offset+8+n));offset+=n+12;}
  const raw=inflateSync(Buffer.concat(compressed)),stride=width*channels,data=new Uint8Array(stride*height);
  const paeth=(a:number,b:number,c:number)=>{const p=a+b-c,pa=Math.abs(p-a),pb=Math.abs(p-b),pc=Math.abs(p-c);return pa<=pb&&pa<=pc?a:pb<=pc?b:c;};
  for(let y=0;y<height;y++) {const filter=raw[y*(stride+1)]!;for(let x=0;x<stride;x++){const i=y*stride+x,left=x>=channels?data[i-channels]!:0,up=y?data[i-stride]!:0,corner=y&&x>=channels?data[i-stride-channels]!:0;let predictor=0;if(filter===1)predictor=left;if(filter===2)predictor=up;if(filter===3)predictor=Math.floor((left+up)/2);if(filter===4)predictor=paeth(left,up,corner);data[i]=(raw[y*(stride+1)+1+x]!+predictor)&255;}}
  return {data,width,height,channels};
}
export function difference(a:Buffer,b:Buffer):{changed:number;fraction:number;mean:number} {
  const x=pixels(a),y=pixels(b);if(x.width!==y.width||x.height!==y.height)throw new Error("Screenshot dimensions differ");
  let changed=0,sum=0;
  for(let i=0;i<x.width*x.height;i++){let delta=0;for(let c=0;c<3;c++)delta+=Math.abs(x.data[i*x.channels+c]!-y.data[i*y.channels+c]!);sum+=delta/3;if(delta>8)changed++;}
  return {changed,fraction:changed/(x.width*x.height),mean:sum/(x.width*x.height)};
}
