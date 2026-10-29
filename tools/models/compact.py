import glb, sys
def compact(p):
    j,b=glb.load(p)
    used_acc=set()
    for m in j['meshes']:
        for pr in m['primitives']:
            used_acc.update(pr['attributes'].values())
            if 'indices' in pr: used_acc.add(pr['indices'])
            for t in pr.get('targets',[]): used_acc.update(t.values())
    for s in j.get('skins',[]):
        if 'inverseBindMatrices' in s: used_acc.add(s['inverseBindMatrices'])
    for a in j['animations']:
        for s in a['samplers']: used_acc.update([s['input'],s['output']])
    used_bv=set(j['accessors'][i]['bufferView'] for i in used_acc if 'bufferView' in j['accessors'][i])
    for im in j.get('images',[]):
        if 'bufferView' in im: used_bv.add(im['bufferView'])
    nb=bytearray(); bvmap={}
    for i in sorted(used_bv):
        bv=j['bufferViews'][i]; s=bv.get('byteOffset',0)
        while len(nb)%4: nb.append(0)
        nbv=dict(bv); nbv['byteOffset']=len(nb); nb.extend(b[s:s+bv['byteLength']])
        bvmap[i]=len(bvmap); j['bufferViews'][i]=nbv
    j['bufferViews']=[j['bufferViews'][i] for i in sorted(used_bv)]
    accmap={}; accs=[]
    for i in sorted(used_acc):
        a=dict(j['accessors'][i])
        if 'bufferView' in a: a['bufferView']=bvmap[a['bufferView']]
        accmap[i]=len(accs); accs.append(a)
    j['accessors']=accs
    for m in j['meshes']:
        for pr in m['primitives']:
            pr['attributes']={k:accmap[v] for k,v in pr['attributes'].items()}
            if 'indices' in pr: pr['indices']=accmap[pr['indices']]
            if 'targets' in pr: pr['targets']=[{k:accmap[v] for k,v in t.items()} for t in pr['targets']]
    for s in j.get('skins',[]):
        if 'inverseBindMatrices' in s: s['inverseBindMatrices']=accmap[s['inverseBindMatrices']]
    for a in j['animations']:
        for s in a['samplers']: s['input']=accmap[s['input']]; s['output']=accmap[s['output']]
    for im in j.get('images',[]):
        if 'bufferView' in im: im['bufferView']=bvmap[im['bufferView']]
    glb.save(p,j,nb)
import os
for f in sys.argv[1:]:
    before=os.path.getsize(f); compact(f); print(f, before,'->',os.path.getsize(f))
