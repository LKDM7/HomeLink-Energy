const fs = require('node:fs'), z = require('node:zlib');
const string = text => { const b=Buffer.from(text), h=Buffer.alloc(2); h.writeUInt16BE(b.length); return Buffer.concat([h,b]); };
const int = n => { const b=Buffer.alloc(4); b.writeInt32BE(n); return b; };
const tag = (t,n,b) => Buffer.concat([Buffer.from([t]),string(n),b]);
const data = Buffer.concat([
  Buffer.from([10,0,0]), tag(3,'DataVersion',int(3955)),
  tag(9,'size',Buffer.concat([Buffer.from([3]),int(3),int(34),int(8),int(34)])),
  tag(9,'palette',Buffer.concat([Buffer.from([10]),int(1),tag(8,'Name',string('minecraft:air')),Buffer.from([0])])),
  tag(9,'blocks',Buffer.concat([Buffer.from([10]),int(0)])),
  tag(9,'entities',Buffer.concat([Buffer.from([10]),int(0)])), Buffer.from([0])
]);
fs.writeFileSync('src/verification/resources/data/energy_validation/structure/load.nbt',z.gzipSync(data));
