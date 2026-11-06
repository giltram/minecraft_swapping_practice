import hashlib,importlib.util,json,struct,zlib,gzip,sys
from pathlib import Path
root=Path(__file__).resolve().parents[3]
world=root/'run-m2-runtime-m3c-final-20261106/saves/hst-smoke'
assert world.resolve()==world and (world/'.hst-smoke-owned').read_text().strip()=='Hotbar Swap Trainer automated disposable smoke world v1'
s=importlib.util.spec_from_file_location('nbt',root/'scripts/verify-owned-smoke-save.py');m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
def freeze(v):
 if isinstance(v,dict): return tuple((k,freeze(v[k])) for k in sorted(v))
 if isinstance(v,(tuple,list)): return tuple(freeze(x) for x in v)
 return v
chunks={}
for path in sorted((world/'region').glob('*.mca')):
 assert not path.is_symlink()
 with path.open('rb') as f:
  header=f.read(4096)
  if not header:continue
  assert len(header)==4096
  for i in range(1024):
   location=struct.unpack_from('>I',header,i*4)[0]
   if not location: continue
   f.seek((location>>8)*4096);length=struct.unpack('>I',f.read(4))[0];kind=f.read(1)[0];raw=f.read(length-1)
   if kind==2: raw=zlib.decompress(raw)
   elif kind==1:raw=gzip.decompress(raw)
   else:raise ValueError('Unsupported fixture compression')
   reader=m.NbtReader(raw);assert reader.number('B')==10;reader.text();level=reader.payload(10)[1]['Level'][1]
   if level.get('Status')!=(8,'full'):continue
   sections=[]
   for _,section in level['Sections'][1][1]:
    if 'Palette' in section:sections.append({k:section[k] for k in ('Y','Palette','BlockStates')})
   key=str(level['xPos'][1])+','+str(level['zPos'][1]);assert key not in chunks
   chunks[key]=hashlib.sha256(repr(freeze(sorted(sections,key=lambda s:s['Y'][1]))).encode()).hexdigest()
assert not (world/'.hotbar-swap-trainer-arena.json').exists() and not (world/'data/hotbar-swap-trainer-arena.dat').exists()
p=root/'docs/verification/m3-c-2026-11-06/legacy-terrain-before.json'
if sys.argv[1]=='before':p.write_text(json.dumps({'method':'Independent typed NBT palette/block-state hash of existing full chunks in the explicitly owned old marked-test save','chunks':chunks},indent=2)+'\n');print('Recorded',len(chunks),'existing full chunks')
else:
 before=json.loads(p.read_text())['chunks'];assert all(chunks.get(k)==v for k,v in before.items())
 report={'status':'passed','originalFullChunksCompared':len(before),'afterFullChunks':len(chunks),'arenaOwnershipCreated':False,'terrainBlocksChanged':False}
 (p.parent/'legacy-terrain-after.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
