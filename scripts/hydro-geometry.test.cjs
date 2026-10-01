const test=require('node:test');
const assert=require('node:assert/strict');
const G=require('./hydro-geometry.cjs');
const {audit}=require('./audit-models.cjs');
const sub=(a,b)=>a.map((v,i)=>v-b[i]);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
function area(mesh) {
  return mesh.reduce((sum,f)=>sum+f.vertices.slice(1,-1).reduce((s,p,i)=>s+Math.hypot(...cross(sub(p,f.vertices[0]),sub(f.vertices[i+2],f.vertices[0])))/2,0),0);
}
test('partitioning a round shell preserves surface area and texture coordinates',()=>{
  const mesh=G.ring(2,[16,16,0],14,12,3,29,'body',24),parts=[];
  for(let x=0;x<2;x++)for(let y=0;y<2;y++)for(let z=0;z<2;z++) {
    const lo=[x*16,y*16,z*16],hi=lo.map(v=>v+16),part=G.clip(mesh,lo,hi);
    for(const f of part)for(let i=0;i<f.vertices.length;i++) {
      assert.ok(f.vertices[i].every((v,k)=>v>=lo[k]-1e-6&&v<=hi[k]+1e-6));
      assert.ok(f.uv[i].every(v=>Number.isFinite(v)&&v>=-1e-6&&v<=1.000001));
    }
    parts.push(...part);
  }
  assert.ok(Math.abs(area(mesh)-area(parts))<1e-5);
  assert.deepEqual(audit(parts.map((f,i)=>({...f,label:String(i)}))),[]);
});
test('coplanar cleanup retains the union area and the later detail material',()=>{
  const a={vertices:[[0,0,0],[3,0,0],[3,3,0],[0,3,0]],uv:[[0,0],[1,0],[1,1],[0,1]],material:'body'};
  const b={...a,vertices:[[2,1,0],[4,1,0],[4,2,0],[2,2,0]],material:'paint'};
  const clean=G.clean([a,b]);
  assert.ok(Math.abs(area(clean)-10)<1e-6);
  assert.ok(Math.abs(area(clean.filter(f=>f.material==='paint'))-2)<1e-6);
  assert.deepEqual(audit(clean.map((f,i)=>({...f,label:String(i)}))),[]);
});
test('circular wall and sphere faces point outwards for backface culling',()=>{
  for(const mesh of [G.cylinder(2,[8,8,8],5,3,13,'body'),G.sphere([8,8,8],5,'body')])for(const f of mesh) {
    const normal=cross(sub(f.vertices[1],f.vertices[0]),sub(f.vertices[2],f.vertices[0]));
    const midpoint=f.vertices.reduce((s,p)=>s.map((v,k)=>v+p[k]/f.vertices.length),[0,0,0]);
    assert.ok(normal.reduce((sum,v,k)=>sum+v*(midpoint[k]-8),0)>0);
  }
});
