import glb, struct, math, copy, sys
def qmul(a,b):
    ax,ay,az,aw=a; bx,by,bz,bw=b
    return (aw*bx+ax*bw+ay*bz-az*by, aw*by-ax*bz+ay*bw+az*bx, aw*bz+ax*by-ay*bx+az*bw, aw*bw-ax*bx-ay*by-az*bz)
def qinv(a): return (-a[0],-a[1],-a[2],a[3])
def qnorm(a):
    n=math.sqrt(sum(x*x for x in a)); return tuple(x/n for x in a)
def qrot(q,v):
    p=qmul(qmul(q,(v[0],v[1],v[2],0)),qinv(q)); return p[:3]
def parents(j):
    par={}
    for i,n in enumerate(j['nodes']):
        for c in n.get('children',[]): par[c]=i
    return par
def world_rot(j,par,i):
    q=(0,0,0,1); chain=[]
    while i in par: i=par[i]; chain.append(i)
    for k in reversed(chain): q=qmul(q,tuple(j['nodes'][k].get('rotation',[0,0,0,1])))
    return q
def byname(j): return {n.get('name'):i for i,n in enumerate(j['nodes'])}
def add_acc(j,b,vals,typ,minmax=False):
    while len(b)%4: b.append(0)
    off=len(b); n=len(vals[0])
    for v in vals: b.extend(struct.pack('<'+'f'*n,*v))
    j['bufferViews'].append({'buffer':0,'byteOffset':off,'byteLength':len(b)-off})
    a={'bufferView':len(j['bufferViews'])-1,'componentType':5126,'count':len(vals),'type':typ}
    if minmax: a['min']=[min(v[k] for v in vals) for k in range(n)]; a['max']=[max(v[k] for v in vals) for k in range(n)]
    j['accessors'].append(a); return len(j['accessors'])-1
def hips_name(names): return [n for n in names if n and n.endswith('Hips')][0]

def build(target_path, out_path, sources):
    tj,tb=glb.load(target_path); tpar=parents(tj); tn=byname(tj)
    th=tn[hips_name(tn)]
    Pt=world_rot(tj,tpar,th); qt_rest=tuple(tj['nodes'][th].get('rotation',[0,0,0,1])); pt_rest=tj['nodes'][th].get('translation',[0,0,0])
    tj['animations']=[]
    for (spath, sname, newname) in sources:
        sj,sb=glb.load(spath); spar=parents(sj); sn={i:n.get('name') for i,n in enumerate(sj['nodes'])}
        sh=byname(sj)[hips_name(byname(sj))]
        Ps=world_rot(sj,spar,sh); qs_rest=tuple(sj['nodes'][sh].get('rotation',[0,0,0,1])); ps_rest=sj['nodes'][sh].get('translation',[0,0,0])
        ws_rest=qmul(Ps,qs_rest); wt_rest=qmul(Pt,qt_rest)
        off=qmul(qinv(ws_rest),wt_rest)
        hs=abs(qrot(Ps,ps_rest)[1]); ht=abs(qrot(Pt,pt_rest)[1]); ratio=ht/hs if hs else 1.0
        anim=[a for a in sj['animations'] if a['name']==sname][0]
        out={'name':newname,'channels':[],'samplers':[]}
        for ch in anim['channels']:
            node=ch['target']['node']; path=ch['target']['path']; name=sn[node]
            if name not in tn or path=='scale': continue
            isH = node==sh
            if path=='translation' and not isH: continue
            smp=anim['samplers'][ch['sampler']]
            times=glb.read_acc(sj,sb,smp['input']); vals=glb.read_acc(sj,sb,smp['output'])
            if path=='rotation':
                if isH:
                    vals=[qnorm(qmul(qinv(Pt),qmul(qmul(Ps,v),off))) for v in vals]
                acc_o=add_acc(tj,tb,vals,'VEC4')
            else:
                nv=[]
                for v in vals:
                    d=qrot(Ps,(v[0]-ps_rest[0],v[1]-ps_rest[1],v[2]-ps_rest[2]))
                    d=qrot(qinv(Pt),(d[0]*ratio,d[1]*ratio,d[2]*ratio))
                    nv.append((pt_rest[0]+d[0],pt_rest[1]+d[1],pt_rest[2]+d[2]))
                acc_o=add_acc(tj,tb,nv,'VEC3')
            acc_i=add_acc(tj,tb,times,'SCALAR',minmax=True)
            out['samplers'].append({'input':acc_i,'output':acc_o,'interpolation':smp.get('interpolation','LINEAR')})
            out['channels'].append({'sampler':len(out['samplers'])-1,'target':{'node':tn[name],'path':path}})
        tj['animations'].append(out)
        print(' ',newname,'<-',spath.split('/')[-1],sname,len(out['channels']),'ch ratio %.3f'%ratio)
    glb.save(out_path,tj,tb)
    print('wrote',out_path)

if __name__=='__main__':
    base='../three/examples/models/gltf/'
    S,M,X=base+'Soldier.glb',base+'Michelle.glb',base+'Xbot.glb'
    src=[(S,'Idle','Idle'),(S,'Run','Run'),(M,'SambaDance','Dance'),(X,'agree','Yes'),(X,'headShake','No'),(X,'sad_pose','Sad')]
    import os; os.makedirs('../out',exist_ok=True)
    build(S,'../out/arjun.glb',src)
    build(M,'../out/meera.glb',src)
