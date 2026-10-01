// Small polygon authoring toolkit. Units are Minecraft model pixels; OBJ output uses blocks.
const fs=require('node:fs');
const path=require('node:path');
const EPS=1e-7;
const sub=(a,b)=>a.map((v,i)=>v-b[i]);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0);
const swatches={dark:[1,1,4,4],paint:[0,0,1,1],copper:[1,7,4,8]};
function polygon(vertices,material,normal,uv) {
  if(dot(cross(sub(vertices[1],vertices[0]),sub(vertices[2],vertices[0])),normal)<0)vertices=[...vertices].reverse();
  const area=swatches[material]||[0,0,16,16];
  return {vertices,material,uv:vertices.map((p,i)=>{
    const q=uv?uv(p):[[0,0],[1,0],[1,1],[0,1]][i%4];
    return [(area[0]+q[0]*(area[2]-area[0]))/16,(area[1]+q[1]*(area[3]-area[1]))/16];
  })};
}
function box(lo,hi,material) {
  const out=[];
  for(let axis=0;axis<3;axis++)for(const sign of [-1,1]) {
    const axes=[0,1,2].filter(a=>a!==axis),n=[0,0,0];n[axis]=sign;
    const v=[[0,0],[1,0],[1,1],[0,1]].map(c=>{const p=[0,0,0];p[axis]=sign<0?lo[axis]:hi[axis];axes.forEach((a,k)=>p[a]=c[k]?hi[a]:lo[a]);return p;});
    out.push(polygon(v,material,n,p=>[(p[axes[0]]-lo[axes[0]])/(hi[axes[0]]-lo[axes[0]]),1-(p[axes[1]]-lo[axes[1]])/(hi[axes[1]]-lo[axes[1]])]));
  }
  return out;
}
// A lathed profile can describe a cylinder, chamfer, flange or hollow tube without overlapping caps.
function lathe(axis,center,profile,material,{segments=16,capStart=true,capEnd=true}={}) {
  const axes=[0,1,2].filter(a=>a!==axis),out=[];
  const point=(h,r,i)=>{const p=[...center],a=i*2*Math.PI/segments;p[axis]=h;p[axes[0]]+=r*Math.cos(a);p[axes[1]]+=r*Math.sin(a);return p;};
  for(let j=0;j<profile.length-1;j++) {
    const [h0,r0]=profile[j],[h1,r1]=profile[j+1];
    for(let i=0;i<segments;i++) {
      const n=[0,0,0],a=(i+.5)*2*Math.PI/segments;
      n[axis]=r0-r1;n[axes[0]]=(h1-h0)*Math.cos(a);n[axes[1]]=(h1-h0)*Math.sin(a);
      out.push(polygon([point(h0,r0,i),point(h0,r0,i+1),point(h1,r1,i+1),point(h1,r1,i)],material,n));
    }
  }
  for(const [enabled,index,sign] of [[capStart,0,-1],[capEnd,profile.length-1,1]]) {
    if(!enabled)continue;
    const [h,r]=profile[index],c=[...center],n=[0,0,0];c[axis]=h;n[axis]=sign;
    for(let i=0;i<segments;i+=2)out.push(polygon([c,point(h,r,i),point(h,r,i+1),point(h,r,i+2)],material,n,p=>[(p[axes[0]]-center[axes[0]])/(2*r)+.5,.5-(p[axes[1]]-center[axes[1]])/(2*r)]));
  }
  return out;
}
function cylinder(axis,center,radius,start,end,material,options={}) {
  return lathe(axis,center,[[start,radius],[end,radius]],material,options);
}
function ring(axis,center,outer,inner,start,end,material,segments=16) {
  // Cross-section follows the outer wall, rear annulus, inner wall, then front annulus.
  return lathe(axis,center,[[start,outer],[end,outer],[end,inner],[start,inner],[start,outer]],material,{segments,capStart:false,capEnd:false});
}
function sphere(center,radius,material,segments=16,rings=8) {
  const out=[],point=(j,i)=>{const a=i*2*Math.PI/segments,b=-Math.PI/2+j*Math.PI/rings;return [center[0]+radius*Math.cos(b)*Math.cos(a),center[1]+radius*Math.sin(b),center[2]+radius*Math.cos(b)*Math.sin(a)];};
  for(let j=0;j<rings;j++)for(let i=0;i<segments;i++) {
    let v=[point(j,i),point(j,i+1),point(j+1,i+1),point(j+1,i)];
    if(j===0)v=v.slice(1);if(j===rings-1)v=v.slice(0,3);
    const average=v.reduce((s,p)=>s.map((n,k)=>n+p[k]/v.length),[0,0,0]);out.push(polygon(v,material,sub(average,center)));
  }
  return out;
}
const move=(mesh,fn)=>mesh.map(f=>({...f,vertices:f.vertices.map(fn)}));
function rotateZ(mesh,angle,center=[0,0,0]) {
  const a=angle*Math.PI/180,c=Math.cos(a),s=Math.sin(a);
  return move(mesh,p=>[center[0]+(p[0]-center[0])*c-(p[1]-center[1])*s,center[1]+(p[0]-center[0])*s+(p[1]-center[1])*c,p[2]]);
}
// Sutherland-Hodgman clipping retains original winding and interpolates the texture coordinates.
function clip(mesh,lo,hi) {
  return mesh.flatMap(face=>{
    let points=face.vertices.map((p,i)=>({p,uv:face.uv[i]}));
    for(let axis=0;axis<3;axis++)for(const [plane,sign] of [[lo[axis],1],[hi[axis],-1]]) {
      const input=points;points=[];
      for(let i=0;i<input.length;i++) {
        const a=input[i],b=input[(i+1)%input.length],da=(a.p[axis]-plane)*sign,db=(b.p[axis]-plane)*sign;
        if(da>=-EPS)points.push(a);
        if((da>EPS&&db<-EPS)||(da<-EPS&&db>EPS)) {
          const t=da/(da-db);points.push({p:a.p.map((v,k)=>v+(b.p[k]-v)*t),uv:a.uv.map((v,k)=>v+(b.uv[k]-v)*t)});
        }
      }
    }
    points=points.filter((v,i)=>Math.hypot(...sub(v.p,points[(i+1)%points.length].p))>EPS);
    if(points.length<3)return [];
    // Remove collinear vertices introduced at exact cell boundaries.
    let changed=true;
    while(changed&&points.length>=3) {changed=false;for(let i=0;i<points.length;i++) {
      const prev=points[(i+points.length-1)%points.length].p,p=points[i].p,next=points[(i+1)%points.length].p;
      if(Math.hypot(...cross(sub(p,prev),sub(next,p)))<EPS) {points.splice(i,1);changed=true;break;}
    }}
    if(points.length<3)return [];
    const make=ps=>({...face,vertices:ps.map(v=>v.p),uv:ps.map(v=>v.uv)});
    if(points.length<=4)return [make(points)];
    return points.slice(1,-1).map((p,i)=>make([points[0],p,points[i+2]]));
  });
}
// Subtract coplanar overlaps before partitioning multiblocks. Later authored details own the surface.
function clean(mesh) {
  const planes=mesh.map(f=>{
    const n=cross(sub(f.vertices[1],f.vertices[0]),sub(f.vertices[2],f.vertices[0])),len=Math.hypot(...n);
    if(len<EPS)throw new Error('Degenerate authored Hydro face');
    const sign=n.find(v=>Math.abs(v)>EPS)<0?-1:1,unit=n.map(v=>v*sign/len);
    return {n:unit,key:[...unit,dot(unit,f.vertices[0])].map(v=>Math.round(v*1e6)).join('/')};
  });
  const buckets=new Map();planes.forEach((p,i)=>{const bucket=buckets.get(p.key)||[];bucket.push(i);buckets.set(p.key,bucket);});
  function half(points,origin,n,positive) {
    const out=[];
    for(let i=0;i<points.length;i++) {
      const a=points[i],b=points[(i+1)%points.length],da=dot(sub(a.p,origin),n)*(positive?1:-1),db=dot(sub(b.p,origin),n)*(positive?1:-1);
      if(da>=-EPS)out.push(a);
      if((da>EPS&&db<-EPS)||(da<-EPS&&db>EPS)) {const t=da/(da-db);out.push({p:a.p.map((v,k)=>v+(b.p[k]-v)*t),uv:a.uv.map((v,k)=>v+(b.uv[k]-v)*t)});}
    }
    return out;
  }
  const valid=ps=>ps.length>=3&&Math.hypot(...cross(sub(ps[1].p,ps[0].p),sub(ps[2].p,ps[0].p)))>EPS;
  return mesh.flatMap((f,i)=>{
    let pieces=[f.vertices.map((p,k)=>({p,uv:f.uv[k]}))];
    for(const j of buckets.get(planes[i].key)) {
      if(j<=i)continue;
      const cut=mesh[j].vertices,center=cut.reduce((s,p)=>s.map((v,k)=>v+p[k]/cut.length),[0,0,0]);
      pieces=pieces.flatMap(original=>{
        let inside=original;const outside=[];
        for(let k=0;k<cut.length&&inside.length>=3;k++) {
          const a=cut[k],edge=sub(cut[(k+1)%cut.length],a);let n=cross(planes[i].n,edge);
          if(dot(sub(center,a),n)<0)n=n.map(v=>-v);
          const part=half(inside,a,n,false);if(valid(part))outside.push(part);
          inside=half(inside,a,n,true);
        }
        // Disjoint or touching polygons must not be split along an infinite edge extension.
        return valid(inside)?outside:[original];
      });
    }
    return pieces.flatMap(ps=>{
      const make=v=>({...f,vertices:v.map(p=>p.p),uv:v.map(p=>p.uv)});
      if(ps.length<=4)return [make(ps)];
      return ps.slice(1,-1).map((p,k)=>make([ps[0],p,ps[k+2]]));
    });
  });
}
function write(root,name,mesh,textures,extra={}) {
  const filename=`${root}/models/${name}`,lines=['# Generated by scripts/hydro-models.cjs','mtllib homelink_energy:models/hydro.mtl'];
  fs.mkdirSync(path.dirname(filename),{recursive:true});
  let offset=1,material='';
  for(const face of mesh) {
    if(face.material!==material) {material=face.material;lines.push(`usemtl ${material}`);}
    for(const p of face.vertices)lines.push('v '+p.map(v=>+(v/16).toFixed(8)).join(' '));
    for(const uv of face.uv)lines.push('vt '+uv.map(v=>+v.toFixed(8)).join(' '));
    lines.push('f '+face.vertices.map((_,i)=>`${offset+i}/${offset+i}`).join(' '));offset+=face.vertices.length;
  }
  fs.writeFileSync(filename+'.obj',lines.join('\n')+'\n');
  fs.writeFileSync(filename+'.json',JSON.stringify({loader:'neoforge:obj',model:`homelink_energy:models/${name}.obj`,automatic_culling:false,shade_quads:true,emissive_ambient:false,flip_v:false,ambientocclusion:false,textures,...extra},null,2)+'\n');
}
module.exports={box,lathe,cylinder,ring,sphere,move,rotateZ,clip,clean,write};
