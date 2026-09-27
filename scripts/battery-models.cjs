// Exterior-only meshes: remove internal surfaces and clip coplanar overlaps.
// Later cuboids own a shared exterior plane, preserving the intended trim material.
const EPS = 1e-6;
const planes = {west:[0,0],east:[0,1],down:[1,0],up:[1,1],north:[2,0],south:[2,1]};
function subtract(rect, cut) {
  const [x0,y0,x1,y1]=rect;
  const a=Math.max(x0,cut[0]),b=Math.max(y0,cut[1]),c=Math.min(x1,cut[2]),d=Math.min(y1,cut[3]);
  if(c-a<=EPS || d-b<=EPS) return [rect];
  return [[x0,y0,a,y1],[c,y0,x1,y1],[a,y0,c,b],[a,d,c,y1]].filter(r=>r[2]-r[0]>EPS && r[3]-r[1]>EPS);
}
function faceUV(face, box, u, v) {
  const [axis]=planes[face], axes=[0,1,2].filter(a=>a!==axis);
  const s=(u-box.from[axes[0]])/(box.to[axes[0]]-box.from[axes[0]]);
  const t=(v-box.from[axes[1]])/(box.to[axes[1]]-box.from[axes[1]]);
  return face==='up'?[s,t]:face==='down'?[s,1-t]:face==='north'?[1-s,1-t]:face==='south'?[s,1-t]:face==='west'?[t,1-s]:[1-t,1-s];
}
function exterior(boxes) {
  const result=[];
  boxes.forEach((box,i)=>{
    for(const [face,data] of Object.entries(box.faces)) {
      const [axis,positive]=planes[face],axes=[0,1,2].filter(a=>a!==axis);
      const c=(positive?box.to:box.from)[axis];
      let pieces=[[box.from[axes[0]],box.from[axes[1]],box.to[axes[0]],box.to[axes[1]]]];
      boxes.forEach((other,j)=>{
        if(i===j || !pieces.length) return;
        const outside=positive ? other.from[axis]<=c+EPS && other.to[axis]>c+EPS : other.from[axis]<c-EPS && other.to[axis]>=c-EPS;
        const shared=j>i && Math.abs((positive?other.to:other.from)[axis]-c)<EPS;
        if(outside || shared) {
          const cut=[other.from[axes[0]],other.from[axes[1]],other.to[axes[0]],other.to[axes[1]]];
          pieces=pieces.flatMap(rect=>subtract(rect,cut));
        }
      });
      for(const rect of pieces) {
        const from=[0,0,0],to=[0,0,0]; from[axis]=to[axis]=c;
        axes.forEach((a,k)=>{from[a]=rect[k];to[a]=rect[k+2];});
        const a=faceUV(face,box,rect[0],rect[1]),b=faceUV(face,box,rect[2],rect[3]);
        const uv=data.uv;
        const crop=[Math.min(a[0],b[0]),Math.min(a[1],b[1]),Math.max(a[0],b[0]),Math.max(a[1],b[1])];
        const mapped=crop.map((n,k)=>uv[k%2]+n*(uv[k%2+2]-uv[k%2]));
        result.push({from,to,faces:{[face]:{...data,uv:mapped.map(n=>Math.round(n*1e6)/1e6)}}});
      }
    }
  });
  return result;
}

function battery(tier, {box,metal,silver,dark,copper}) {
  const e=[];
  const add=(...parts)=>e.push(...parts);
  const indicator=(from,to)=>box(from,to,'indicator',[7,8,8,10]);
  function feet(x0,x1,z0,z1) {
    for(const x of [x0,x1]) for(const z of [z0,z1]) add(dark([x,0,z],[x+2,1.2,z+2]));
  }
  function terminal(x,y,z) {
    add(dark([x-0.4,y,z-0.4],[x+2.4,y+0.7,z+2.4]),copper([x,y+0.7,z],[x+2,y+1.7,z+2]));
  }
  if(tier===1) {
    // Narrow portable accumulator: cast body, exposed terminals and raised carry handle.
    feet(3,11,3,11);
    add(metal([2.5,1,2.5],[13.5,2,13.5]),box([3,2,3],[13,11.5,13],'body'));
    add(metal([2.6,11.5,2.6],[13.4,12.5,13.4]));
    for(const x of [3,12.25]) add(silver([x,2,2.7],[x+0.75,11.5,3.2]));
    // Faceplate and four horizontal charge segments.
    add(silver([5,4,2.5],[11,9.7,3]),dark([5.4,4.4,2.3],[10.6,9.3,2.6]));
    for(let i=0;i<4;i++) add(indicator([6,5+i,2.15],[8.7,5.5+i,2.35]));
    add(copper([9.3,5,2.15],[10,8.5,2.35]));
    for(const z of [5,7,9]) add(dark([2.8,5,z],[3.05,8,z+0.5]),dark([12.95,5,z],[13.2,8,z+0.5]));
    terminal(4,12.5,4);terminal(10,12.5,4);
    add(silver([4,12.5,9],[5,15.5,11]),silver([11,12.5,9],[12,15.5,11]),dark([4,14.5,9],[12,16,11]));
  } else if(tier===2) {
    // Twin exposed octagonal cells held by a low cradle and a copper top bridge.
    feet(1,13,3,11);
    add(metal([1,1,2],[15,2.5,14]));
    for(const x of [1.5,8.5]) {
      add(box([x+1,2.5,3],[x+5,12.7,13],'body'),box([x,2.5,4],[x+6,12.7,12],'body'));
      for(const y of [3.2,10.8]) add(silver([x-0.15,y,3.8],[x+6.15,y+0.65,12.2]),silver([x+0.8,y,2.8],[x+5.2,y+0.65,13.2]));
      add(metal([x+0.4,12.7,3.7],[x+5.6,13.5,12.3]));
      terminal(x+2,13.5,7);
      add(dark([x+1.8,5,2.8],[x+4.2,9.7,3.15]));
      for(let i=0;i<4;i++) add(indicator([x+2.2,5.6+i,2.65],[x+3.8,6.1+i,2.9]));
    }
    add(copper([4.5,14.85,7.65],[11.5,15.35,8.35]));
    add(dark([6.7,4,6],[9.3,9,10]));
    for(const x of [7.05,8.05]) add(copper([x,4.5,5.7],[x+0.6,8.5,6.1]));
  } else {
    // Industrial cabinet: three removable drawers, side heat sinks and top connector bus.
    feet(1,13,1,13);
    add(metal([0.75,1,0.75],[15.25,2,15.25]),dark([2,2,2],[14,14,14]));
    for(const x of [1,14]) for(const z of [1,14]) add(silver([x,2,z],[x+1,14.5,z+1]));
    for(let i=0;i<3;i++) {
      const y=2.4+i*3.6;
      add(box([2.2,y,1.6],[13.8,y+3.15,13.8],'body'));
      add(metal([2.5,y+0.25,1.35],[13.5,y+2.85,1.75]));
      add(silver([4,y+0.85,0.9],[4.7,y+2,1.6]),silver([9.3,y+0.85,0.9],[10,y+2,1.6]),silver([4,y+1.15,0.7],[10,y+1.75,1.1]));
      add(dark([11,y+0.65,1.15],[12.8,y+2.5,1.5]),indicator([11.4,y+1,1],[12.4,y+2.1,1.2]));
    }
    for(const x of [1.35,14.1]) for(const z of [3,5,7,9,11]) add(metal([x,3,z],[x+0.55,12.5,z+0.6]));
    add(metal([0.75,14,0.75],[15.25,14.8,15.25]));
    terminal(3,14.3,4);terminal(11,14.3,4);
    add(dark([4,14.8,9],[12,15.4,13]));
    for(let i=0;i<3;i++) add(copper([5+i*2,15.4,9.7],[6+i*2,15.75,12.3]));
  }
  return exterior(e);
}
module.exports={battery,exterior};
