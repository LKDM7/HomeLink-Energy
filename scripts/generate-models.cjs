// Code-native Minecraft meshes. Run with node scripts/generate-models.cjs.
const fs = require('node:fs');
const path = require('node:path');
const root = 'src/main/resources';
const assets = `${root}/assets/homelink_energy`;
function write(file, data) { fs.mkdirSync(path.dirname(file), {recursive:true}); fs.writeFileSync(file, JSON.stringify(data, null, 2) + '\n'); }
const tex = name => `homelink_energy:block/${name}`;
const texture = {metal:tex('solar_panel_side'), dark:tex('solar_panel_bottom'), frame:tex('battery_top'), copper:tex('copper_energy_cable'), cell:tex('solar_panel_1_top')};
function box(from, to, material, uv = [0,0,16,16]) {
  return {from,to,faces:Object.fromEntries(['up','down','north','south','east','west'].map(face=>[face,{texture:`#${material}`,uv}]))};
}
const metal = (a,b)=>box(a,b,'metal',[1,11,4,13]);
const silver = (a,b)=>box(a,b,'frame',[0,0,1,1]);
const dark = (a,b)=>box(a,b,'dark',[1,1,4,4]);
const copper = (a,b)=>box(a,b,'copper',[1,7,4,8]);
const display = {
 gui:{rotation:[30,225,0],translation:[0,0,0],scale:[0.65,0.65,0.65]},
 ground:{rotation:[0,0,0],translation:[0,3,0],scale:[0.35,0.35,0.35]},
 fixed:{rotation:[0,0,0],translation:[0,0,0],scale:[0.65,0.65,0.65]},
 thirdperson_righthand:{rotation:[75,45,0],translation:[0,2.5,0],scale:[0.375,0.375,0.375]},
 firstperson_righthand:{rotation:[0,45,0],translation:[0,0,0],scale:[0.5,0.5,0.5]}
};
for(let tier=1;tier<=3;tier++) {
  const width=tier===1?1:2, depth=tier===3?2:1;
  const variants={}, all=[];
  for(let row=0;row<2;row++) for(let col=0;col<2;col++) {
    const e=[];
    // Raised chassis with four isolated feet and twin rails; open space below the cells.
    for(const x of [2,12]) {
      e.push(metal([x,1,1],[x+2,5.5,15]),silver([x-0.5,0,1],[x+2.5,1,4]),silver([x-0.5,0,12],[x+2.5,1,15]));
    }
    e.push(dark([0,5,0],[16,6,16]));
    // Individual inset photovoltaic tiles, fine busbars, continuous outer frame.
    for(const x of [0.8,8.1]) for(const z of [0.8,8.1]) {
      e.push(box([x,6,z],[x+7.1,6.7,z+7.1],'cell',[1,1,14,14]));
      e.push(silver([x+3.4,6.7,z+0.2],[x+3.55,6.76,z+6.9]));
    }
    if(col===0) e.push(silver([0,5.5,0],[0.6,7.3,16]));
    if(col===width-1) e.push(silver([15.4,5.5,0],[16,7.3,16]));
    if(row===0) e.push(silver([0,5.5,0],[16,7.3,0.6]));
    if(row===depth-1) e.push(silver([0,5.5,15.4],[16,7.3,16]));
    for(const x of [0.8,14.4]) for(const z of [0.8,14.4]) e.push(metal([x,6.7,z],[x+0.8,7.1,z+0.8]));
    // Under-array output hub, copper terminals and tier marks.
    e.push(metal([5,1.5,5],[11,5,11]),copper([6,0,6],[10,1.5,10]));
    if(col===0 && row===0) for(let i=0;i<tier;i++) e.push(copper([5+i*2,5.5,-0.03],[6+i*2,6.6,0.2]));
    const name=`solar_panel_${tier}_${col}_${row}`;
    // Adjacent array tiles share a boundary; omit both internal end caps.
    for(const el of e) for(const [face,axis,plane,internal] of [
      ['west',0,0,col>0],['east',0,16,col<width-1],['north',2,0,row>0],['south',2,16,row<depth-1]]) {
      if(internal && Math.abs((face==='east'||face==='south'?el.to:el.from)[axis]-plane)<1e-6)delete el.faces[face];
    }
    write(`${assets}/models/block/${name}.json`,{ambientocclusion:true,textures:{...texture,cell:tex(`solar_panel_${tier}_top`),particle:tex(`solar_panel_${tier}_top`)},elements:e});
    for(const [facing,y] of [['north',0],['east',90],['south',180],['west',270]]) variants[`facing=${facing},column=${col},row=${row}`]={model:tex(name),y};
    if(col<width && row<depth) for(const el of e) all.push({...el,from:el.from.map((v,i)=>v+(i===0?16*col:i===2?16*row:0)),to:el.to.map((v,i)=>v+(i===0?16*col:i===2?16*row:0))});
  }
  write(`${assets}/blockstates/solar_panel_${tier}.json`,{variants});
  // A complete array in the hand, fitted inside a 16-unit item model (no oversized JSON elements).
  const scale=1/Math.max(width,depth), offset=[(16-16*width*scale)/2,2,(16-16*depth*scale)/2];
  const itemElements=all.map(el=>({...el,from:el.from.map((v,i)=>v*scale+offset[i]),to:el.to.map((v,i)=>v*scale+offset[i])}));
  write(`${assets}/models/item/solar_panel_${tier}.json`,{textures:{...texture,cell:tex(`solar_panel_${tier}_top`),particle:tex(`solar_panel_${tier}_top`)},elements:itemElements,display});
  const loot=`${root}/data/homelink_energy/loot_table/blocks/solar_panel_${tier}.json`;
  const table=JSON.parse(fs.readFileSync(loot,'utf8').replace(/^\uFEFF/,''));
  table.pools[0].conditions=table.pools[0].conditions.filter(c=>c.condition!=='minecraft:block_state_property');
  table.pools[0].conditions.push({condition:'minecraft:block_state_property',block:`homelink_energy:solar_panel_${tier}`,properties:{column:'0',row:'0'}});
  write(loot,table);
  const {battery,exterior}=require('./battery-models.cjs');
  const e=battery(tier,{box,metal,silver,dark,copper}).map(part=>({...part,
    from:part.from.map((v,i)=>v*(i===0?width:i===2?depth:1)),to:part.to.map((v,i)=>v*(i===0?width:i===2?depth:1))}));
  const batteryTextures={...texture,body:tex(`battery_${tier}_side`),indicator:tex(`battery_${tier}_side`),particle:tex(`battery_${tier}_side`)};
  write(`${assets}/models/block/battery_${tier}.json`,{textures:batteryTextures,elements:e,display});
  const batteryVariants={};
  for(let row=0;row<2;row++)for(let col=0;col<2;col++) {
    const name=`battery_${tier}_${col}_${row}`,masks=[];
    if(col>0)masks.push({from:[-100,-100,-100],to:[16*col,100,100],faces:{}});
    if(col<width-1)masks.push({from:[16*(col+1),-100,-100],to:[100,100,100],faces:{}});
    if(row>0)masks.push({from:[-100,-100,-100],to:[100,100,16*row],faces:{}});
    if(row<depth-1)masks.push({from:[-100,-100,16*(row+1)],to:[100,100,100],faces:{}});
    const parts=col<width&&row<depth?exterior([...e,...masks]).map(part=>({...part,
      from:part.from.map((v,i)=>v-(i===0?16*col:i===2?16*row:0)),to:part.to.map((v,i)=>v-(i===0?16*col:i===2?16*row:0))})):[];
    write(`${assets}/models/block/${name}.json`,{textures:batteryTextures,elements:parts});
    for(const [facing,y] of [['north',0],['east',90],['south',180],['west',270]])batteryVariants[`facing=${facing},column=${col},row=${row}`]={model:tex(name),y};
  }
  write(`${assets}/blockstates/battery_${tier}.json`,{variants:batteryVariants});
  const fit=1/Math.max(width,depth),itemOffset=[(16-16*width*fit)/2,0,(16-16*depth*fit)/2];
  write(`${assets}/models/item/battery_${tier}.json`,{textures:batteryTextures,display,elements:e.map(part=>({...part,
    from:part.from.map((v,i)=>v*fit+itemOffset[i]),to:part.to.map((v,i)=>v*fit+itemOffset[i])}))});
  const batteryLoot=`${root}/data/homelink_energy/loot_table/blocks/battery_${tier}.json`,batteryTable=JSON.parse(fs.readFileSync(batteryLoot,'utf8'));
  batteryTable.pools[0].conditions=batteryTable.pools[0].conditions.filter(c=>c.condition!=='minecraft:block_state_property');
  batteryTable.pools[0].conditions.push({condition:'minecraft:block_state_property',block:`homelink_energy:battery_${tier}`,properties:{column:'0',row:'0'}});
  write(batteryLoot,batteryTable);
}
// Surface nodes use multipart models; the renderer adds only electrically connected arms.
const directions=[['down',0,0],['up',180,0],['north',90,180],['south',90,0],['west',90,90],['east',90,270]];
write(`${assets}/models/block/cable_surface.json`,{textures:{...texture,particle:tex('copper_energy_cable')},elements:[dark([6,0,6],[10,0.45,10]),copper([6.5,0.45,6.5],[9.5,0.7,9.5])]});
const orient={down:([x,y,z])=>[x,y,z],up:([x,y,z])=>[x,16-y,z],north:([x,y,z])=>[x,z,y],south:([x,y,z])=>[x,z,16-y],west:([x,y,z])=>[y,x,z],east:([x,y,z])=>[16-y,x,z]};
for(const [face] of directions) {
  const elements=[dark([6,0,6],[10,0.45,10]),copper([6.5,0.45,6.5],[9.5,0.7,9.5])].map(e=>{
    const a=orient[face](e.from),b=orient[face](e.to);
    return {...e,from:a.map((v,i)=>Math.min(v,b[i])),to:a.map((v,i)=>Math.max(v,b[i]))};
  });
  // Keep the support-facing skin: glass supports expose the back of the cable.
  write(`${assets}/models/block/cable_surface_${face}.json`,{textures:{...texture,particle:tex('copper_energy_cable')},elements});
}
write(`${assets}/blockstates/copper_energy_cable.json`,{multipart:directions.map(([face])=>({when:{[face]:'true'},apply:{model:tex(`cable_surface_${face}`)}}))});
// End before the central node: perpendicular renderer arms must never overlap.
const arm=[dark([6.5,0,0],[9.5,0.4,6]),copper([7,0.4,0],[9,0.65,6])];
for(const e of arm) { delete e.faces.north; delete e.faces.south; }
write(`${assets}/models/block/cable_trace.json`,{textures:{...texture,particle:tex('copper_energy_cable')},elements:arm});
// One side owns an inside bend; the other begins after its thickness to prevent duplicate side faces.
const cornerArm=arm.map(e=>({...e,from:[e.from[0],e.from[1],.65]}));
write(`${assets}/models/block/cable_trace_corner.json`,{textures:{...texture,particle:tex('copper_energy_cable')},elements:cornerArm});
const cross=[...arm,...arm.map(e=>({...e,from:[e.from[2],e.from[1],e.from[0]],to:[e.to[2],e.to[1],e.to[0]]})),dark([6.5,0,8],[9.5,0.4,16]),copper([7,0.4,8],[9,0.65,16]),dark([8,0,6.5],[16,0.4,9.5]),copper([8,0.4,7],[16,0.65,9])];
write(`${assets}/models/item/copper_energy_cable.json`,{textures:{...texture,particle:tex('copper_energy_cable')},elements:cross,display});
// Each installed face consumes and returns one item.
write(`${root}/data/homelink_energy/loot_table/blocks/copper_energy_cable.json`,{type:'minecraft:block',pools:directions.map(([face])=>({rolls:1,entries:[{type:'minecraft:item',name:'homelink_energy:copper_energy_cable'}],conditions:[{condition:'minecraft:survives_explosion'},{condition:'minecraft:block_state_property',block:'homelink_energy:copper_energy_cable',properties:{[face]:'true'}}]}))});
for(const [lang,label,tip] of [['fr_fr','Emprise au sol','Dimensions : %s × %s blocs'],['en_us','Footprint','Dimensions: %s × %s blocks']]) {
  const file=`${assets}/lang/${lang}.json`, json=JSON.parse(fs.readFileSync(file,'utf8').replace(/^\uFEFF/,''));
  json['gui.homelink_energy.footprint']=label; json['tooltip.homelink_energy.footprint']=tip;
  json['tooltip.homelink_energy.surface_cable']=lang==='fr_fr'?'Se fixe au sol, aux murs et au plafond. Ajoutez une face pour tourner.':'Attaches to floors, walls and ceilings. Add a face to turn a corner.';
  write(file,json);
}
require('./clean-models.cjs').clean();
