import type { Point } from "./hold";
export function radiusFor(progress: number, center: Point, width: number,height: number): number {
  if(progress<=0) return -1;
  const far=Math.max(Math.hypot(center.x,center.y),Math.hypot(width-center.x,center.y),Math.hypot(center.x,height-center.y),Math.hypot(width-center.x,height-center.y));
  return Math.pow(progress,.85)*(far+Math.hypot(width,height)*.18);
}
export function boundaryNoise(x: number,y: number,center: Point,time: number): number {
  const angle=Math.atan2(y-center.y,x-center.x);
  return Math.sin(angle*3+time*.45)*.032+Math.sin(angle*7-time*.35)*.017+Math.sin(x*.013+y*.019+time*.3)*.023+Math.sin(x*.026-y*.012-time*.2)*.013;
}
export function coverage(progress: number,center: Point,width: number,height: number,time: number): number {
  const radius=radiusFor(progress,center,width,height);
  if(radius<0) return 0;
  const diagonal=Math.hypot(width,height);
  let covered=0;
  for(let y=0;y<18;y++) for(let x=0;x<32;x++) {
    const px=(x+.5)/32*width, py=(y+.5)/18*height;
    if(Math.hypot(px-center.x,py-center.y)+boundaryNoise(px,py,center,time)*diagonal<=radius-4) covered++;
  }
  return covered/(18*32);
}
export const vertex = `#version 300 es
precision highp float;
out vec2 uv;
void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);uv=vec2(p.x,1.0-p.y);gl_Position=vec4(p*2.0-1.0,0.0,1.0);}`;
export const fragment = `#version 300 es
precision highp float;
in vec2 uv;
out vec4 color;
uniform sampler2D filmA;
uniform sampler2D filmB;
uniform sampler2D field;
uniform vec2 viewport;
uniform vec2 fieldSize;
uniform vec2 center;
uniform float radius;
uniform float time;
uniform float reveal;
uniform float grace;
uniform float reduced;
uniform float fieldActive;
uniform float portrait;
uniform float framingA;
uniform float framingB;
float noise(vec2 p){float a=atan(p.y-center.y,p.x-center.x);return sin(a*3.0+time*.45)*.032+sin(a*7.0-time*.35)*.017+sin(p.x*.013+p.y*.019+time*.3)*.023+sin(p.x*.026-p.y*.012-time*.2)*.013;}
float sdf(vec2 p){return length(p-center)+noise(p)*length(viewport)-radius;}
vec2 filmUV(vec2 p,float framing){
  float aspect=viewport.x/viewport.y;
  // Portrait deliberately contains the entire 16:9 subject in a full-height material stage.
  float displayAspect=mix(aspect,16.0/9.0,portrait);
  vec2 scale=displayAspect>16.0/9.0?vec2(1.0,(16.0/9.0)/displayAspect):vec2(displayAspect/(16.0/9.0),1.0);
  vec2 q=(p-.5)*scale+.5;
  if(portrait>.5) q.y=(p.y-.5)/max(viewport.x/(16.0/9.0)/viewport.y,.01)+.5;
  q.x+=(1.0-scale.x)*(framing-.5);
  return q;
}
vec3 sampleFilm(sampler2D film,vec2 p,float framing){
  vec2 q=filmUV(p,framing);
  vec3 edge=texture(film,clamp(q,vec2(.01),vec2(.99))).rgb;
  if(portrait>.5 && (q.y<0.0||q.y>1.0)) return edge*.15;
  return texture(film,clamp(q,vec2(0),vec2(1))).rgb;
}
void main(){
 vec2 p=uv*viewport;
 vec4 f=vec4(.5,.5,0.0,1.0);
 vec2 gradient=vec2(0.0);
 float density=0.0;
 if(fieldActive>.5 && reduced<.5){
   f=texture(field,uv);
   vec2 pixel=1.0/fieldSize;
   gradient=vec2(texture(field,uv+vec2(pixel.x,0)).b-texture(field,uv-vec2(pixel.x,0)).b,texture(field,uv+vec2(0,pixel.y)).b-texture(field,uv-vec2(0,pixel.y)).b);
   density=smoothstep(.12,.18,f.b);
 }
 float mask=0.0;
 float rim=0.0;
 vec2 normal=vec2(0.0);
 if(radius>=0.0 && reduced<.5){
   float d=sdf(p);
   mask=1.0-smoothstep(-4.0,4.0,d);
   rim=exp(-abs(d)*.045);
   normal=vec2(sdf(p+vec2(2,0))-sdf(p-vec2(2,0)),sdf(p+vec2(0,2))-sdf(p-vec2(0,2)))*.25;
 }
 vec2 displacement=(gradient*25.0+(f.rg-.5)*density*18.0+normal*rim*7.0)/viewport;
 vec3 image=sampleFilm(filmA,uv+displacement,framingA);
 if(reveal>.5 && mask>0.0) image=mix(image,sampleFilm(filmB,uv+displacement*.7,framingB),mask);
 // Neutral smoked-glass material; contour and directional refraction bind it to the film.
 float material=density*.46+mask*.09*(1.0-reveal)*grace;
 image=mix(image,image*.78+vec3(.16,.17,.145),material);
 image+=vec3(.12,.14,.125)*(length(gradient)*density*.8+rim*.09)*grace;
 color=vec4(image,1.0);
}`;
