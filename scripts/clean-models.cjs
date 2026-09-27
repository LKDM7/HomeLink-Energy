// Remove hidden cuboid surfaces and coplanar duplicates without changing the silhouette.
const fs=require('node:fs');
const {exterior}=require('./battery-models.cjs');
function clean() {
  let count=0;
  for(const dir of ['block','item']) for(const file of fs.readdirSync(`src/main/resources/assets/homelink_energy/models/${dir}`)) {
    if(!file.endsWith('.json'))continue;
    const p=`src/main/resources/assets/homelink_energy/models/${dir}/${file}`, model=JSON.parse(fs.readFileSync(p,'utf8'));
    if(!model.elements?.some(e=>e.to.every((v,i)=>v>e.from[i]+1e-6)))continue;
    const rotated=model.elements.filter(e=>e.rotation);
    model.elements=[...exterior(model.elements.filter(e=>!e.rotation)),...rotated];
    fs.writeFileSync(p,JSON.stringify(model,null,2)+'\n'); count++;
  }
  console.log(`Exterior meshes rebuilt: ${count}`);
}
if(require.main===module)clean();
module.exports={clean};
