const {faces,audit,read}=require('./audit-models.cjs');
const fs=require('node:fs');
const failures=[];let assemblies=0;
function check(name,mesh){assemblies++;const errors=audit(mesh);if(errors.length)failures.push({name,count:errors.length,examples:errors.slice(0,5)});}
const rz=(p,angle)=>{const a=angle*Math.PI/180;return [p[0]*Math.cos(a)-p[1]*Math.sin(a),p[0]*Math.sin(a)+p[1]*Math.cos(a),p[2]];};
for(let tier=1;tier<=3;tier++) {
 const h=[0,4,7,11][tier],w=.7+.2*(tier-1),scale=(tier*2+1)/3;
 const bw=tier===1?1:2,bd=tier===3?2:1,sx=8*(bw-1),sz=8*(bd-1),fixed=[];
 for(let row=0;row<bd;row++)for(let col=0;col<bw;col++)fixed.push(...faces(read(`wind_turbine_${tier}_${col}_${row}`),p=>[p[0]+16*col,p[1],p[2]+16*row],`base${col}/${row}/`));
 for(let seg=0;seg<h;seg++)fixed.push(...faces(read('wind_tower'),p=>[8+sx+(p[0]-8)*w,14+16*seg+p[1]*(seg===h-1?.625:1),8+sz+(p[2]-8)*w],`tower${seg}/`));
 fixed.push(...faces(read(tier===1?'wind_nacelle':`wind_nacelle_${tier}`),p=>[p[0]+sx,16*h+p[1],p[2]+sz],'nacelle/'));
 for(let angle=0;angle<360;angle+=5) {
  const mesh=[...fixed];
  const transform=angle=>p=>{let v=rz([(p[0]-8)*scale,(p[1]-8)*scale,p[2]-8],angle);return [8+sx+v[0],16*h+8+v[1],-9.6+sz+v[2]];};
  for(let blade=0;blade<3;blade++)mesh.push(...faces(read('wind_blade'),transform(angle+120*blade),`blade${blade}/`));
  mesh.push(...faces(read('wind_hub'),transform(angle),'hub/'));check(`wind-${tier}@${angle}`,mesh);
 }
 const width=tier===1?1:2,depth=tier===3?2:1,mesh=[];
 for(let row=0;row<depth;row++)for(let col=0;col<width;col++)mesh.push(...faces(read(`solar_panel_${tier}_${col}_${row}`),p=>[p[0]+16*col,p[1],p[2]+16*row],`${col}/${row}/`));
 check(`solar-array-${tier}`,mesh);
 const batteryMesh=[];
 for(let row=0;row<depth;row++)for(let col=0;col<width;col++)batteryMesh.push(...faces(read(`battery_${tier}_${col}_${row}`),p=>[p[0]+16*col,p[1],p[2]+16*row],`${col}/${row}/`));
 check(`battery-array-${tier}`,batteryMesh);
}
const orientation={down:p=>p,up:([x,y,z])=>[x,16-y,z],north:([x,y,z])=>[x,z,y],south:([x,y,z])=>[x,z,16-y],west:([x,y,z])=>[y,x,z],east:([x,y,z])=>[16-y,x,z]};
const allFaces=Object.keys(orientation),trace=read('cable_trace');
for(let mask=1;mask<64;mask++) {
 const mesh=[];
 allFaces.forEach((side,i)=>{if(!(mask&(1<<i)))return;
  mesh.push(...faces(read('cable_surface_'+side),p=>p,side+'/node/'));
  for(let arm=0;arm<4;arm++) {
   const angle=arm*Math.PI/2,center=orientation[side]([8,0,8]),tip=orientation[side]([8+8*Math.sin(angle),0,8-8*Math.cos(angle)]),v=tip.map((n,i)=>n-center[i]);
   const tangent=Math.abs(v[0])>4?(v[0]<0?'west':'east'):Math.abs(v[1])>4?(v[1]<0?'down':'up'):(v[2]<0?'north':'south');
   const t=allFaces.indexOf(tangent),trim=(mask&(1<<t)) && i>t;
   mesh.push(...faces(trim?read('cable_trace_corner'):trace,p=>{
   let a=arm*Math.PI/2,x=p[0]-8,z=p[2]-8;
   return orientation[side]([8+x*Math.cos(a)-z*Math.sin(a),p[1],8+x*Math.sin(a)+z*Math.cos(a)]);
  },`${side}/arm${arm}/`));
  }
 });check(`cable-faces-${mask}`,mesh);
}
// Hydro: pump arrays, the eight turbine cells with rotor and louvers at several phases, and every pipe joint mask.
const rx=(p,angle)=>{const a=angle*Math.PI/180;return [p[0],p[1]*Math.cos(a)-p[2]*Math.sin(a),p[1]*Math.sin(a)+p[2]*Math.cos(a)];};
for(const [tier,w,d] of [[1,1,1],[2,2,1],[3,2,2]]) {
 const mesh=[];
 for(let row=0;row<d;row++)for(let col=0;col<w;col++)mesh.push(...faces(read(`hydro_pump_${tier}_${col}_${row}`),p=>[p[0]+16*col,p[1],p[2]+16*row],`${col}/${row}/`));
 check(`hydro-pump-${tier}`,mesh);
}
const casing=[];
for(let l=0;l<2;l++)for(let r=0;r<2;r++)for(let c=0;c<2;c++)casing.push(...faces(read(`hydro_turbine_${c}_${l}_${r}`),p=>[p[0]+16*c,p[1]+16*l,p[2]+16*r],`${c}/${l}/${r}/`));
for(let angle=0;angle<60;angle+=5)for(const open of [0,0.5,1]) {
 const mesh=[...casing],rotor=extra=>p=>{const v=rz([p[0]-8,p[1]-8,p[2]-8],angle+extra);return [16+v[0],16.5+v[1],25+v[2]];};
 for(let blade=0;blade<6;blade++)mesh.push(...faces(read('hydro_rotor_blade'),rotor(blade*60),`blade${blade}/`));
 mesh.push(...faces(read('hydro_rotor_hub'),rotor(0),'hub/'));
 for(let i=0;i<3;i++)mesh.push(...faces(read('hydro_louver'),p=>{const v=rx([p[0]*25.6/16,p[1]-0.95,p[2]-8],-72*open);return [3.2+v[0],3+2*i+v[1],2+v[2]];},`louver${i}/`));
 check(`hydro-turbine@${angle}/${open}`,mesh);
}
const armRot={north:p=>p,south:([x,y,z])=>[16-x,y,16-z],east:([x,y,z])=>[16-z,y,x],west:([x,y,z])=>[z,y,16-x],up:([x,y,z])=>[x,16-z,y],down:([x,y,z])=>[x,z,16-y]};
const sides=Object.keys(armRot);
for(let mask=0;mask<64;mask++) {
 const mesh=[...faces(read('hydro_pipe_core'),p=>p,'core/')];
 sides.forEach((side,i)=>{if(mask&(1<<i))mesh.push(...faces(read('hydro_pipe_arm'),armRot[side],side+'/'));});
 check(`hydro-pipe-${mask}`,mesh);
}
console.log(JSON.stringify({assemblies,failures},null,2));if(failures.length)process.exitCode=1;
