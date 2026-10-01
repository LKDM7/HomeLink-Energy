// Heavy industrial Hydro family. Polygon meshes baked by NeoForge's built-in OBJ loader.
// +X = column, +Y = layer, +Z = row. Ports and animated-part pivots match HydroLayout/the renderer.
// Run from the project root: node scripts/hydro-models.cjs
const fs=require('node:fs');
const G=require('./hydro-geometry.cjs');
const {box,cylinder,lathe,ring,move,rotateZ}=G;
const root='src/main/resources/assets/homelink_energy';
const base={dark:'solar_panel_bottom',paint:'battery_top',copper:'copper_energy_cable',gold:'wind_brass',body:'wind_graphite',blade:'wind_blade',vent:'wind_vent',accent:'hydro_accent',screen:'hydro_screen',intake:'hydro_intake'};
const textures=extra=>({...Object.fromEntries(Object.entries({...base,...extra}).map(([k,v])=>[k,'homelink_energy:block/'+v])),particle:'homelink_energy:block/wind_graphite'});
fs.writeFileSync(`${root}/models/hydro.mtl`,Object.keys({...base,label:''}).map(k=>`newmtl ${k}\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd #${k}\n`).join('\n'));
const display={gui:{rotation:[30,225,0],scale:[.625,.625,.625]},ground:{translation:[0,3,0],scale:[.25,.25,.25]},fixed:{scale:[.5,.5,.5]},thirdperson_righthand:{rotation:[75,45,0],translation:[0,2.5,0],scale:[.375,.375,.375]},firstperson_righthand:{rotation:[0,45,0],scale:[.4,.4,.4]}};
const ROT=[['north',0],['east',90],['south',180],['west',270]];
const save=(name,mesh,tex=textures({}),extra={})=>G.write(root,name,G.clean(mesh),tex,extra);
function item(name,mesh,size,tex=textures({})) {
  const scale=15/Math.max(...size),offset=size.map(v=>(16-v*scale)/2);offset[1]=0;
  save('item/'+name,move(mesh,p=>p.map((v,i)=>v*scale+offset[i])),tex,{display});
}
function cells(name,mesh,size,tex,layers=false) {
  mesh=G.clean(mesh);
  const variants={};
  for(let l=0;l<(layers?2:1);l++)for(let r=0;r<2;r++)for(let c=0;c<2;c++) {
    const part=`${name}_${c}_${layers?l+'_':''}${r}`,origin=[c*16,l*16,r*16];
    // A polygon exactly on a shared cell plane belongs only to the lower-index cell.
    const faces=G.clip(mesh,origin,origin.map(v=>v+16)).filter(f=>!origin.some((v,axis)=>v>0&&f.vertices.every(p=>Math.abs(p[axis]-v)<1e-7)));
    save('block/'+part,move(faces,p=>p.map((v,i)=>v-origin[i])),tex);
    for(const [facing,y] of ROT)variants[`facing=${facing},column=${c},${layers?'layer='+l+',':''}row=${r}`]={model:'homelink_energy:block/'+part,y};
  }
  fs.writeFileSync(`${root}/blockstates/${name}.json`,JSON.stringify({variants},null,2)+'\n');
}
function bolts(axis,center,radius,start,end,count=8,size=.55) {
  const axes=[0,1,2].filter(a=>a!==axis),out=[];
  for(let i=0;i<count;i++) {const a=(i+.5)*Math.PI*2/count,p=[...center];p[axes[0]]+=radius*Math.cos(a);p[axes[1]]+=radius*Math.sin(a);out.push(...cylinder(axis,p,size,start,end,'copper',{segments:8}));}
  return out;
}

// Thick octagonal pipework with cast junctions and bolted industrial couplings.
const core=box([3,3,3],[13,13,13],'body');
const arm=[...cylinder(2,[8,8,0],4.6,0,8,'body',{segments:8,capStart:false,capEnd:false}),
  ...ring(2,[8,8,0],6.8,4.6,0,.8,'paint',8),...ring(2,[8,8,0],5.3,4.6,.9,1.6,'copper',8),
  ...bolts(2,[8,8,0],5.8,.8,1.3,8,.45)];
save('block/hydro_pipe_core',core);save('block/hydro_pipe_arm',arm);
item('hydro_pipe',[...core,...arm,...move(arm,p=>[16-p[0],p[1],16-p[2]])],[16,16,16]);
const sideRot={north:{},south:{y:180},east:{y:90},west:{y:270},up:{x:270},down:{x:90}};
fs.writeFileSync(`${root}/blockstates/hydro_pipe.json`,JSON.stringify({multipart:[{apply:{model:'homelink_energy:block/hydro_pipe_core'}},...Object.entries(sideRot).map(([side,rot])=>({when:{[side]:'true'},apply:{model:'homelink_energy:block/hydro_pipe_arm',...rot}}))]},null,2)+'\n');

// Integrated pump stations: one cast pump head, a long motor enclosure at tier II,
// and a full rear radiator/collector bank at tier III. A single continuous structural skid.
for(let tier=1;tier<=3;tier++) {
  const w=tier===1?1:2,d=tier===3?2:1,W=16*w,D=16*d,e=[],tex=textures({label:`hydro_label_${tier}`});
  e.push(...box([0,0,0],[W,1.8,D],'dark'),...box([1,1.8,1],[W-1,3,D-1],'body'));
  for(const x of [.5,W-2.5])for(const z of [.5,D-2.5])e.push(...box([x,1.8,z],[x+2,12,z+2],'paint'),...box([x+.3,10.6,z+.3],[x+1.7,11.6,z+1.7],'copper'));
  e.push(...box([2,3,2],[14,10.8,14],'body'),...box([2,10.8,2],[14,12,14],'dark'));
  // Recessed intake behind three thick protective slats.
  e.push(...box([2.5,3.3,.7],[13.5,10.8,2],'paint'),...box([3.3,4,.5],[12.7,10.1,.7],'intake'));
  for(const x of [4.5,7.7,10.9])e.push(...box([x,4.1,.15],[x+.6,10,.5],'dark'));
  // Side access plate and exposed return pipe running behind the pump head.
  e.push(...box([1.4,4,4],[2,9.8,10],'label'),...cylinder(2,[13.8,6.5,0],.8,3,13,'copper',{segments:8}));
  if(tier>=2) {
    e.push(...box([14,3,3],[30,10.5,13],'body'),...box([16,10.5,2.5],[29,11.7,13.5],'dark'));
    e.push(...box([16,4,1.5],[29,9.8,3],'vent'));
    for(const x of [18,21,24,27])e.push(...box([x,10.5,3],[x+.8,12,13],'paint'));
    e.push(...cylinder(0,[0,8,14],1.1,10,28,'copper',{segments:8}));
    e.push(...box([25,4,13],[29,10,14.5],'body'));
  }
  if(tier===3) {
    e.push(...box([3,3,18],[29,10.6,29],'body'),...box([2.5,10.6,18],[29.5,12,29],'dark'));
    for(let x=4;x<29;x+=2.6)e.push(...box([x,4,29],[x+.9,10.2,30.5],'paint'));
    for(const x of [5,26])e.push(...cylinder(2,[x,8,0],1.3,12,27,'copper',{segments:8}));
    e.push(...box([14.8,2.8,2],[17.2,11.5,30],'dark'),...box([1.5,11,15],[30.5,12,17],'paint'));
  }
  // Raised master-cell outlet, still aligned with the existing hydraulic connection.
  e.push(...box([3,11.5,3],[13,13,13],'dark'),...cylinder(1,[8,0,8],4.6,12.5,16,'body',{segments:8,capStart:false,capEnd:false}),...ring(1,[8,0,8],5.3,4.6,13.8,14.5,'copper',8),...ring(1,[8,0,8],6.8,4.6,15.2,16,'paint',8));
  // Cast base, bolt heads and short copper warning marks.
  for(let x=3;x<W-2;x+=4)e.push(...box([x,.5,0],[x+1.5,1.5,.3],'copper'));
  for(const x of [3,W-3])e.push(...cylinder(2,[x,2.3,0],.5,.3,.8,'copper',{segments:8}));
  cells(`hydro_pump_${tier}`,e,[W,16,D],tex);item(`hydro_pump_${tier}`,e,[W,16,D],tex);
}

// Turbine: a heavy generator enclosure on a reinforced skid. Open rear fan chamber,
// tall corner columns, a stepped hood, external cooling pipes and a protected service face.
const center=[16,16.5,0],t=[],tex=textures({label:'hydro_label_turbine'});
t.push(...box([0,0,0],[32,2,32],'dark'),...box([3,2,4],[29,8,20],'body'),...box([3,8,3],[29,27,20],'body'));
for(const x of [.3,29.2])for(const z of [.3,29.2]) {
  t.push(...box([x,2,z],[x+2.5,31.5,z+2.5],'paint'));
  for(const y of [3.5,27.5])t.push(...box([x-.2,y,z-.2],[x+2.7,y+1.5,z+2.7],'dark'));
}
// Deep upper frame and stepped motor hood; separate from the rear inlet.
t.push(...box([0,29,0],[32,32,2.5],'dark'),...box([0,29,29.5],[32,32,32],'dark'),...box([0,29,2.5],[2.5,32,29.5],'dark'),...box([29.5,29,2.5],[32,32,29.5],'dark'));
t.push(...box([4,27,4],[28,28.5,18],'dark'),...box([7,28.5,6],[25,30,16],'body'));
for(const x of [9,12,15,18,21])t.push(...box([x,30,7],[x+1,30.8,15],'paint'));
// Small recessed control console on the left, protected radiator on the right.
t.push(...box([4.5,20,1.8],[14.5,27.5,3],'dark'),...box([5,20.5,1.5],[14,27,1.8],'paint'),...box([6,22,1.3],[13,26,1.5],'screen'));
t.push(...box([17,11,1.8],[27.5,27,3],'dark'),...box([17.7,11.7,1.6],[26.8,26.3,1.8],'vent'));
for(let y=13;y<26;y+=2.8)t.push(...box([17.5,y,.9],[27,y+.8,1.6],'paint'));
// Bolted service hatch, with a compact shutoff handwheel instead of a second large display.
t.push(...box([4.5,10.5,1.7],[14.5,18.8,3],'paint'),...box([5.2,11.2,1.4],[13.8,18.1,1.7],'body'));
t.push(...ring(2,[9.5,14.6,0],2.2,1.5,.5,1,'copper',12),...cylinder(2,[9.5,14.6,0],.55,.3,1.4,'dark',{segments:8}));
for(let a=0;a<360;a+=120)t.push(...rotateZ(box([9.25,14.6,.7],[9.75,16.7,1],'paint'),a,[9.5,14.6,0]));
t.push(...box([5.5,18.8,1.4],[13,20,2],'label'));
for(const x of [4,28])for(const y of [10,27.5])t.push(...cylinder(2,[x,y,0],.7,.8,2.8,'copper',{segments:8}));
// External side cooling loops and clamp brackets make the machine legible in profile.
for(const x of [1.9,30.1])for(const z of [7,15]) {
  t.push(...cylinder(1,[x,0,z],1.1,8,26,'copper',{segments:8}));
  for(const y of [10,22])t.push(...box([x-1.3,y,z-1.3],[x+1.3,y+1,z+1.3],'dark'));
}
// Open rear chamber stays outside the existing spinning rotor envelope.
t.push(...box([2,4,20],[3,29,29.2],'body'),...box([29,4,20],[30,29,29.2],'body'),...box([3,28.8,20],[29,29.8,29.2],'body'),...box([3,2,20],[29,4,29.2],'body'));
for(const y of [5,10.5,16,21.5,27])t.push(...box([3,y,31],[29,y+.65,31.7],'dark'));
for(const x of [8,15.5,23])t.push(...box([x,4,31.1],[x+.7,29,31.8],'paint'));
// Wide lower discharge and a reinforced bumper; louvers/water retain their original clearance.
t.push(...box([1,2,0],[3,8,4],'body'),...box([29,2,0],[31,8,4],'body'),...box([1,8,0],[31,9.5,3],'dark'));
for(let x=3;x<29;x+=4)t.push(...box([x,8.2,0],[x+2,9.2,.25],'copper'));
// Unchanged inlet: top of cell (0,1,1). Output: clockwise side of cell (1,0,1).
t.push(...cylinder(1,[8,0,24],4.6,28,32,'body',{segments:8,capStart:false,capEnd:false}),...ring(1,[8,0,24],5.3,4.6,30,30.7,'copper',8),...ring(1,[8,0,24],6.8,4.6,31.2,32,'paint',8));
t.push(...box([30,4,20],[32,12,28],'copper'),...box([31.5,5.5,21.5],[32,10.5,26.5],'dark'));
cells('hydro_turbine',t,[32,32,32],tex,true);

// Dynamic parts retain the renderer's existing pivots and envelopes.
const hub=[...cylinder(2,[8,8,0],2.9,6,10,'body',{segments:16}),...ring(2,[8,8,0],3.2,2.9,7,8,'copper'),...lathe(2,[8,8,0],[[10,2.5],[11.5,1.3]],'paint',{segments:16,capStart:false})];
// Swept paddle silhouette, with a narrow root outside the round hub.
const blade=[...box([7.65,11.3,7.4],[8.35,13,8.4],'copper'),...rotateZ(box([7.4,12.8,7.5],[9.4,19.6,8.25],'blade'),-8,[8,12.8,8])];
save('block/hydro_rotor_hub',hub);save('block/hydro_rotor_blade',blade);
save('block/hydro_louver',box([0,0,7.6],[16,1.9,8.4],'paint'));
const rotorAt=p=>[p[0]+8,p[1]+8.5,p[2]+17];
const stillRotor=[...move(hub,rotorAt),...Array.from({length:6},(_,i)=>move(rotateZ(blade,i*60,[8,8,8]),rotorAt)).flat()];
item('hydro_turbine',[...t,...stillRotor],[32,32,32],tex);
console.log('Industrial Hydro OBJ meshes and model references regenerated.');
