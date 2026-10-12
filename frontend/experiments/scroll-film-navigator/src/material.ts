import type { Point } from "./hold";
import { PIGMENT } from "./pigment";
import { HOLD_OPTICS } from "./hold-optics";
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
uniform int captureCode;
uniform sampler2D filmA;
uniform sampler2D filmB;
uniform sampler2D field;
uniform sampler2D holdField;
uniform vec2 viewport;
uniform vec2 fieldSize;
uniform vec2 holdSize;
uniform float revealing;
uniform float reduced;
uniform float fieldActive;
uniform float holdActive;
uniform float portrait;
uniform float framingA;
uniform float framingB;
vec2 filmUV(vec2 p,float framing){
 float aspect=viewport.x/viewport.y;
 float displayAspect=mix(aspect,16.0/9.0,portrait);
 vec2 scale=displayAspect>16.0/9.0?vec2(1.0,(16.0/9.0)/displayAspect):vec2(displayAspect/(16.0/9.0),1.0);
 vec2 q=(p-.5)*scale+.5;
 if(portrait>.5)q.y=(p.y-.5)/max(viewport.x/(16.0/9.0)/viewport.y,.01)+.5;
 q.x+=(1.0-scale.x)*(framing-.5);return q;
}
vec3 sampleFilm(sampler2D film,vec2 p,float framing){
 vec2 q=filmUV(p,framing);
 if(portrait>.5 && (q.y<0.0||q.y>1.0))return texture(film,clamp(q,vec2(.01),vec2(.99))).rgb*.15;
 return texture(film,clamp(q,vec2(0),vec2(1))).rgb;
}
vec3 pigmentAt(vec2 p){return texture(field,p).rgb;}
vec3 tinted(vec3 a){return mix(a,a*vec3(.55,.93,.9)+vec3(.025,.2,.17),${HOLD_OPTICS.tintStrength});}
void main(){
 vec4 m=texture(holdField,uv);vec3 f=vec3(0);vec2 gradient=vec2(0),displacement=vec2(0);float density=0.0;
 if(reduced<.5 && fieldActive>.5){
  vec2 px=1.0/fieldSize,fluidUV=vec2(uv.x,1.0-uv.y);
  f=pigmentAt(fluidUV);density=smoothstep(.015,.65,f.r);
  // Interpolate optical slopes as well as pigment so refraction does not
  // jump between simulation texels. RGBA16F dye uses hardware LINEAR reads.
  gradient=vec2(pigmentAt(fluidUV+vec2(px.x,0)).r-pigmentAt(fluidUV-vec2(px.x,0)).r,pigmentAt(fluidUV-vec2(0,px.y)).r-pigmentAt(fluidUV+vec2(0,px.y)).r);
  displacement=clamp(gradient*${PIGMENT.refraction}.0,vec2(-${PIGMENT.maxRefraction}.0),vec2(${PIGMENT.maxRefraction}.0))/viewport;
 }
 if(reduced<.5 && holdActive>.5){vec2 h=1.0/holdSize;vec4 left=texture(holdField,uv-vec2(h.x,0)),right=texture(holdField,uv+vec2(h.x,0)),top=texture(holdField,uv-vec2(0,h.y)),bottom=texture(holdField,uv+vec2(0,h.y));vec2 slope=(revealing>.5?vec2(right.g-left.g,bottom.g-top.g):vec2(right.r-left.r,bottom.r-top.r))*holdSize/viewport;vec2 normal=slope/max(length(slope),.00001);float edge=revealing>.5?m.b:m.a;displacement+=normal*${HOLD_OPTICS.refraction}.0*edge/viewport;}
 vec3 a=sampleFilm(filmA,uv+displacement,framingA);
 vec3 image=mix(a,tinted(a),reduced>.5?0.0:m.r);
 if(revealing>.5 && reduced<.5){image=mix(tinted(a),sampleFilm(filmB,uv+displacement,framingB),m.g);image=mix(image,vec3(.93,.98,.96),m.b*.8);}
 else if(reduced<.5 && holdActive>.5){image=mix(image,vec3(.78,1.0,.94),m.a*${HOLD_OPTICS.rimStrength});}
 float marble=clamp(f.g/(f.r+.001),0.0,1.0);
 vec3 pigment=mix(vec3(.005,.25,.21),vec3(.08,.85,.69),marble);
 float vein=clamp(f.b/(f.r+.001),0.0,1.0);pigment*=1.0-vein*.35;
 // Absorption is a convex blend, not added light: pale films must not clip.
 image=mix(image,mix(image*vec3(.35,.72,.65),pigment,.65),density*${PIGMENT.opacity});
 image=mix(image,vec3(.22,.76,.65),min(length(gradient)*.16,.12)*density);
 color=vec4(image,1.0);
 if(captureCode>=0 && uv.y>1.0-16.0/viewport.y && uv.x<120.0/viewport.x){int bit=int(uv.x*viewport.x)/12;int value=(captureCode>>bit)&1;color=vec4(vec3(float(value)),1.0);}
}`;
