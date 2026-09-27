// Collect only results that were actually produced; fail if an assertion run failed.
const fs=require('node:fs'), path=require('node:path'), crypto=require('node:crypto');
process.chdir(path.resolve(__dirname,'..'));
const out='docs/validation';fs.mkdirSync(out,{recursive:true});
const read=p=>fs.readFileSync(p,'utf8');
const sha=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
let tests=0,failures=0,errors=0;
for(const name of fs.readdirSync('build/test-results/test').filter(n=>n.endsWith('.xml'))) {
 const xml=read('build/test-results/test/'+name);
 const count=key=>Number(xml.match(new RegExp('<testsuite[^>]* '+key+'="(\\d+)"'))?.[1]??0);
 tests+=count('tests');failures+=count('failures');errors+=count('errors');
 fs.copyFileSync('build/test-results/test/'+name,out+'/'+name);
}
if(!tests||failures||errors)throw Error('Unit tests absent or failing');
const gameLog=read('build/validation/gametest/logs/latest.log');
const passed=gameLog.match(/All (\d+) required tests passed/);
if(!passed||/required tests failed/.test(gameLog))throw Error('GameTests absent or failing');
fs.copyFileSync('build/validation/gametest/logs/latest.log',out+'/gametest.log');
for(const lang of ['fr_fr','en_us']) {
 const log=read(out+'/client-'+lang+'.log');
 if(!log.includes('ENERGY_SMOKE_OK language='+lang)||log.includes('ENERGY_SMOKE_FAILED'))throw Error('Client smoke failed '+lang);
 for(const kind of ['solar','battery']) fs.copyFileSync('build/validation/client/screenshots/energy-'+kind+'-'+lang+'.png',out+'/'+kind+'-'+lang+'.png');
}
fs.copyFileSync('build/reports/solar-cycles.txt',out+'/solar-cycles.txt');
const report={collectedAt:new Date().toISOString(),unitTests:{tests,failures,errors},gameTests:Number(passed[1]),
 clientLanguages:['fr_fr','en_us'],load:gameLog.split('\n').find(s=>s.includes('ENERGY_LOAD_OK'))?.trim(),
 homecoreCommit:'55e252a64b3b6929c086624184274edf6a8d971e',
 jarSha256:sha('build/libs/homelink_energy-0.1.0.jar'),homecoreJarSha256:sha('.dependencies/HomeCore/build/libs/homecore-1.8.0.jar'),
 finalCommand:'gradlew.bat build releaseBundle --console=plain',status:'build and validations passed; bundle collected after this report'};
fs.writeFileSync(out+'/results.json',JSON.stringify(report,null,2)+'\n');
const walk=d=>fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(d,e.name)):[path.join(d,e.name)]);
const originals='build/original-prototype';
const paths=[...walk('src'),...walk('scripts'),'build.gradle','settings.gradle','gradle.properties','.gitignore','README.md',...walk('docs').filter(p=>!p.endsWith('CHANGED_FILES.md'))];
const lines=paths.map(p=>p.replaceAll('\\','/')).filter(p=>!fs.existsSync(path.join(originals,p))||sha(p)!==sha(path.join(originals,p)))
 .sort().map(p=>'- `'+p+'` — '+(fs.existsSync(path.join(originals,p))?'modifié':'ajouté'));
fs.writeFileSync('docs/CHANGED_FILES.md','# Fichiers modifiés ou ajoutés\n\nComparaison avec le prototype archivé avant intervention, et non avec l’index Git incomplet du workspace. Les ressources inchangées restent livrées.\n\n'+lines.join('\n')+'\n');
console.log(JSON.stringify(report,null,2));
