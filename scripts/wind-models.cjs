// Authored cuboids -> exterior mesh. Keep runtime rotor envelope unchanged.
const fs=require('node:fs');
const {exterior}=require('./battery-models.cjs');
const root='src/main/resources/assets/homelink_energy/models';
const tex={paint:'wind_painted_steel',dark:'wind_graphite',copper:'wind_copper',gold:'wind_brass',blade:'wind_blade',vent:'wind_vent'};
const textures=Object.fromEntries(Object.entries(tex).map(([k,v])=>[k,'homelink_energy:block/'+v]));
textures.particle=textures.paint;
function box(from,to,t,omit=[]) {return {from,to,faces:Object.fromEntries(['up','down','north','south','east','west'].filter(f=>!omit.includes(f)).map(f=>[f,{texture:'#'+t,uv:[0,0,16,16]}]))};}
function write(name,e,extra={},item=false) {fs.writeFileSync(`${root}/${item?'item':'block'}/${name}.json`,JSON.stringify({ambientocclusion:false,textures,...extra,elements:[...exterior(e.filter(b=>!b.rotation)),...e.filter(b=>b.rotation)]},null,2)+'\n');}
const tower=[box([4,0,4],[12,16,12],'paint',['up','down']),box([3.7,.4,3.7],[12.3,1,12.3],'dark')];
write('wind_tower',tower);
// Root is outside the hub silhouette; fronts have distinct depth from the hub shell.
const blade=[box([6.7,11.2,7],[9.3,16,9],'dark'),box([6.3,16,7],[9.3,22.5,9],'blade'),
 box([6.6,22.5,7.1],[9.1,27.5,8.9],'blade'),box([7,27.5,7.2],[8.8,30.4,8.8],'copper')];
write('wind_blade',blade);
write('wind_hub',[box([5,5,4],[11,11,12],'dark'),box([4.5,6,5],[11.5,10,11],'copper'),
 box([6,4.5,5],[10,11.5,11],'copper'),box([6,6,2.5],[10,10,4],'paint'),box([7,7,2],[9,9,2.5],'dark')]);
for(let tier=1;tier<=3;tier++) {
 const label={...textures,label:`homelink_energy:block/wind_label_${tier}`};
 const width=tier===1?1:2,depth=tier===3?2:1,cx=width*8,cz=depth*8;
 const base=[];for(const x of [1,16*width-3.5])for(const z of [1,16*depth-3.5])base.push(box([x,0,z],[x+2.5,1.4,z+2.5],'dark'));
 base.push(box([0,1.4,0],[16*width,3,16*depth],'dark'),box([2,3,2],[16*width-2,11,16*depth-2],'paint'),box([1.6,3,1.6],[16*width-1.6,4,16*depth-1.6],tier===1?'copper':'gold'),
 box([cx-3.5,5,1.65],[cx+3.5,9.5,2],'dark'),box([cx-3,5.5,1.5],[cx+3,9,1.65],'label'),box([cx-3.5,11,cz-3.5],[cx+3.5,14,cz+3.5],'paint'),box([cx-4,13.5,cz-4],[cx+4,14.5,cz+4],'copper'));
 write(`wind_turbine_${tier}`,base,{textures:label});
 // Clip the shared exterior mesh into occupied cells; masks create no internal seam faces.
 const variants={};
 for(let row=0;row<2;row++)for(let col=0;col<2;col++) {
  const name=`wind_turbine_${tier}_${col}_${row}`,masks=[];
  if(col>0)masks.push({from:[-100,-100,-100],to:[16*col,100,100],faces:{}});
  if(col<width-1)masks.push({from:[16*(col+1),-100,-100],to:[100,100,100],faces:{}});
  if(row>0)masks.push({from:[-100,-100,-100],to:[100,100,16*row],faces:{}});
  if(row<depth-1)masks.push({from:[-100,-100,16*(row+1)],to:[100,100,100],faces:{}});
  const mesh=col<width&&row<depth?exterior([...base,...masks]).map(e=>({...e,from:e.from.map((v,i)=>v-(i===0?16*col:i===2?16*row:0)),to:e.to.map((v,i)=>v-(i===0?16*col:i===2?16*row:0))})):[];
  write(name,mesh,{textures:label});
  for(const [facing,y] of [['north',0],['east',90],['south',180],['west',270]])variants[`facing=${facing},column=${col},row=${row}`]={model:`homelink_energy:block/${name}`,y};
 }
 fs.writeFileSync(`${root}/../blockstates/wind_turbine_${tier}.json`,JSON.stringify({variants},null,2)+'\n');
 const loot=`src/main/resources/data/homelink_energy/loot_table/blocks/wind_turbine_${tier}.json`,table=JSON.parse(fs.readFileSync(loot,'utf8'));
 table.pools[0].conditions=table.pools[0].conditions.filter(c=>c.condition!=='minecraft:block_state_property');
 table.pools[0].conditions.push({condition:'minecraft:block_state_property',block:`homelink_energy:wind_turbine_${tier}`,properties:{column:'0',row:'0'}});
 fs.writeFileSync(loot,JSON.stringify(table,null,2)+'\n');
 const size=tier-1;
 const e=[box([3-size*.5,4-size*.5,-3],[13+size*.5,12+size*.5,14+size*2],'paint'),
 box([4-size*.5,3-size*.5,-1],[12+size*.5,13+size*.5,12+size*2],'paint'),
 box([4,5,-5],[12,11,-3],'dark'),box([6.2,6.2,-8],[9.8,9.8,-5],'copper'),
 box([4,5,14+size*2],[12,11,15+size*2],'vent')];
 // Real raised service panels; no paper-thin faces laid on the shell.
 e.push(box([2.7-size*.5,6,3],[3-size*.5,10,9],'label'),box([13+size*.5,6,3],[13.3+size*.5,10,9],'label'));
 if(tier>=2) {
  e.push(box([3-size*.5,4-size*.5,10],[13+size*.5,12+size*.5,11],tier===2?'copper':'gold'));
  e.push(box([5,13+size*.5,5],[11,14+size*.5,13],'dark'));
 }
 if(tier===3)for(const x of [1.1,14.1])for(const z of [10,12,14])e.push(box([x,5,z],[x+.8,11,z+1],'dark'));
 write(tier===1?'wind_nacelle':`wind_nacelle_${tier}`,e,{textures:label});
 // Complete miniature with one upright and two diagonal lower blades.
 const fit=.7/Math.max(width,depth),offset=[8-cx*fit,2,8-cz*fit];
 const miniature=[...base.map(b=>({...b,from:b.from.map((v,i)=>v*(i===1?.48:fit)+offset[i]),to:b.to.map((v,i)=>v*(i===1?.48:fit)+offset[i])})),
 box([7.1,8.75,7.1],[8.9,22,8.9],'paint'),box([5.5,21,5],[10.5,24,11],'paint'),
 box([7.4,23.5,3.1],[8.6,30,4.3],'blade'),
 ...[-45,45].map(angle=>({...box([7.4,15.3,3.1],[8.6,21.5,4.3],'blade'),rotation:{origin:[8,22.5,3.7],axis:'z',angle}})),
 box([6.7,21.2,2.8],[9.3,23.8,4.6],'copper')];
 write(`wind_turbine_${tier}`,miniature,{textures:label,display:{gui:{rotation:[10,35,0],translation:[0,-3,0],scale:[.65,.65,.65]},ground:{translation:[0,2,0],scale:[.3,.3,.3]},fixed:{translation:[0,-3,0],scale:[.6,.6,.6]}}},true);
}
console.log('Wind meshes regenerated with dedicated materials.');
