// Geometry audit also used for renderer assemblies. Strict positive area, touching edges allowed.
const fs=require('node:fs');
const root='src/main/resources/assets/homelink_energy/models';
const planes={west:[0,0],east:[0,1],down:[1,0],up:[1,1],north:[2,0],south:[2,1]};
function faces(elements,transform=p=>p,label='') {
  return elements.flatMap((e,i)=>e.vertices?[{vertices:e.vertices.map(transform),label:`${label}${i}:obj`}]:Object.keys(e.faces).map(face=>{
    const [axis,sign]=planes[face], axes=[0,1,2].filter(a=>a!==axis);
    const vertices=[[0,0],[1,0],[1,1],[0,1]].map(c=>{
      const p=[0,0,0];p[axis]=(sign?e.to:e.from)[axis];
      axes.forEach((a,k)=>p[a]=(c[k]?e.to:e.from)[a]);
      if(e.rotation) {
        const {origin,axis,angle}=e.rotation,other=[0,1,2].filter(a=>a!=={x:0,y:1,z:2}[axis]);
        const a=other[0],b=other[1],r=angle*Math.PI/180,s=Math.sin(r),c=Math.cos(r),u=p[a]-origin[a],v=p[b]-origin[b];
        p[a]=origin[a]+c*u-s*v;p[b]=origin[b]+s*u+c*v;
      }
      return transform(p);
    });
    return {vertices,label:`${label}${i}:${face}`};
  }));
}
const sub=(a,b)=>a.map((v,i)=>v-b[i]);
const dot=(a,b)=>a.reduce((s,v,i)=>s+v*b[i],0);
function normal(f) {
  const a=sub(f.vertices[1],f.vertices[0]),b=sub(f.vertices[2],f.vertices[0]);
  let n=[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]],len=Math.hypot(...n);
  if(len<1e-9)throw new Error('Degenerate face '+f.label);
  const sign=n.find(v=>Math.abs(v)>1e-8)<0?-1:1;return n.map(v=>sign*v/len);
}
function overlaps(a,b,n) {
  let axis=n.map(Math.abs).indexOf(Math.max(...n.map(Math.abs))), keep=[0,1,2].filter(i=>i!==axis);
  const pa=a.vertices.map(p=>keep.map(i=>p[i])),pb=b.vertices.map(p=>keep.map(i=>p[i]));
  for(const p of [pa,pb])for(let i=0;i<p.length;i++){
    const edge=sub(p[(i+1)%p.length],p[i]),v=[-edge[1],edge[0]],len=Math.hypot(...v); if(len<1e-10)continue;
    const x=pa.map(q=>dot(q,v)/len),y=pb.map(q=>dot(q,v)/len);
    if(Math.min(Math.max(...x),Math.max(...y))-Math.max(Math.min(...x),Math.min(...y))<1e-5)return false;
  }
  return true;
}
function audit(surfaces) {
  const buckets=new Map(),problems=[];
  for(const f of surfaces){
    const n=normal(f),d=dot(n,f.vertices[0]);
    const key=[...n,d].map(v=>Math.round(v*100000)).join('/');
    const others=buckets.get(key)||[];
    for(const other of others)if(overlaps(f,other,n))problems.push(`${other.label} <> ${f.label}`);
    others.push(f);buckets.set(key,others);
  }
  return problems;
}
function elements(model) {
  if(model.loader!=='neoforge:obj')return model.elements;
  const file='src/main/resources/assets/'+model.model.replace(':','/'),vertices=[],mesh=[];
  for(const line of fs.readFileSync(file,'utf8').split(/\r?\n/)) {
    const words=line.trim().split(/\s+/);
    if(words[0]==='v')vertices.push(words.slice(1).map(n=>Number(n)*16));
    if(words[0]==='f')mesh.push({vertices:words.slice(1).map(v=>{
      const p=vertices[Number(v.split('/')[0])-1];
      if(!p||p.some(n=>!Number.isFinite(n)))throw new Error('Invalid OBJ vertex in '+file);
      return p;
    })});
  }
  return mesh;
}
function read(name){return elements(JSON.parse(fs.readFileSync(`${root}/block/${name}.json`,'utf8')));}
function run(){
  let models=0,total=0,problems=[];
  for(const dir of ['block','item'])for(const file of fs.readdirSync(`${root}/${dir}`)) {
    if(!file.endsWith('.json'))continue;
    const model=JSON.parse(fs.readFileSync(`${root}/${dir}/${file}`,'utf8')),parts=elements(model);if(!parts)continue;
    const mesh=faces(parts);models++;total+=mesh.length;
    problems.push(...audit(mesh).map(p=>`${dir}/${file}: ${p}`));
  }
  const summary={models,faces:total,coplanarOverlaps:problems.length,examples:problems.slice(0,30)};
  console.log(JSON.stringify(summary,null,2));return summary;
}
if(require.main===module){const result=run();if(result.coplanarOverlaps)process.exitCode=1;}
module.exports={faces,audit,read};
