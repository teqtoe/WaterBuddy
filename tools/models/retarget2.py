import glb, struct, math, bisect, os
from retarget import qmul,qinv,qnorm,qrot,parents,byname,add_acc,hips_name

def channels(j,b,anim):
    d={}
    for ch in anim['channels']:
        s=anim['samplers'][ch['sampler']]
        ts=[x[0] for x in glb.read_acc(j,b,s['input'])]; vs=glb.read_acc(j,b,s['output'])
        d[(ch['target']['node'],ch['target']['path'])]=(ts,vs)
    return d
def samp(tv,t,path):
    ts,vs=tv; k=bisect.bisect_right(ts,t)-1
    if k<0: return vs[0]
    if k>=len(ts)-1: return vs[-1]
    a=(t-ts[k])/(ts[k+1]-ts[k]) if ts[k+1]>ts[k] else 0; v0,v1=vs[k],vs[k+1]
    if path=='rotation':
        if sum(x*y for x,y in zip(v0,v1))<0: v1=tuple(-x for x in v1)
        return qnorm(tuple(x+(y-x)*a for x,y in zip(v0,v1)))
    return tuple(x+(y-x)*a for x,y in zip(v0,v1))
def order(j):
    par=parents(j); seen=[]; 
    def depth(i): return 0 if i not in par else 1+depth(par[i])
    return sorted(range(len(j['nodes'])),key=depth), par
def world_rots(j,ordr,par,localrot):
    W={}
    for i in ordr:
        r=localrot(i); W[i]=qmul(W[par[i]],r) if i in par else r
    return W
def rest_locals(j,b):
    tp=[a for a in j.get('animations',[]) if a['name'].lower()=='tpose']
    ch=channels(j,b,tp[0]) if tp else {}
    def lr(i):
        if (i,'rotation') in ch: return ch[(i,'rotation')][1][0]
        return tuple(j['nodes'][i].get('rotation',[0,0,0,1]))
    return lr


def rest_positions(j,ordr,par,lr):
    W={}
    for i in ordr:
        n=j['nodes'][i]; r=lr(i); t=n.get('translation',[0,0,0]); sc=n.get('scale',[1,1,1])[0]
        if i in par:
            pr,pt,ps=W[par[i]]; p=qrot(pr,(t[0]*ps,t[1]*ps,t[2]*ps)); W[i]=(qmul(pr,r),(pt[0]+p[0],pt[1]+p[1],pt[2]+p[2]),ps*sc)
        else: W[i]=(r,tuple(t),sc)
    return {i:v[1] for i,v in W.items()}
def sub(a,b): return (a[0]-b[0],a[1]-b[1],a[2]-b[2])
def norm(v):
    L=math.sqrt(sum(x*x for x in v)); return tuple(x/L for x in v)
def cross(a,b): return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
def dot(a,b): return sum(x*y for x,y in zip(a,b))
def mat_to_q(m):
    # m = columns (x,y,z)
    (m00,m10,m20),(m01,m11,m21),(m02,m12,m22)=m
    tr=m00+m11+m22
    if tr>0:
        s=math.sqrt(tr+1)*2; return qnorm(((m21-m12)/s,(m02-m20)/s,(m10-m01)/s,0.25*s))
    if m00>m11 and m00>m22:
        s=math.sqrt(1+m00-m11-m22)*2; return qnorm((0.25*s,(m01+m10)/s,(m02+m20)/s,(m21-m12)/s))
    if m11>m22:
        s=math.sqrt(1+m11-m00-m22)*2; return qnorm(((m01+m10)/s,0.25*s,(m12+m21)/s,(m02-m20)/s))
    s=math.sqrt(1+m22-m00-m11)*2; return qnorm(((m02+m20)/s,(m12+m21)/s,0.25*s,(m10-m01)/s))
def basis_q(j,ordr,par,lr,nm):
    P=rest_positions(j,ordr,par,lr)
    def p(name): return P[nm['mixamorig:'+name]]
    right=norm(sub(p('RightUpLeg'),p('LeftUpLeg'))); up0=sub(p('Head'),p('Hips'))
    up=norm(sub(up0,tuple(x*dot(up0,right) for x in right))); fwd=cross(right,up)
    return mat_to_q((right,up,fwd))

def build(target_path,out_path,sources):
    tj,tb=glb.load(target_path); tord,tpar=order(tj); tn=byname(tj); tname={i:n.get('name') for i,n in enumerate(tj['nodes'])}
    t_rest_l=rest_locals(tj,tb); Wt_rest=world_rots(tj,tord,tpar,t_rest_l)
    Bt=basis_q(tj,tord,tpar,t_rest_l,tn)
    th=tn[hips_name(tn)]; pt_rest=tj['nodes'][th].get('translation',[0,0,0]); Pt=Wt_rest[tpar[th]] if th in tpar else (0,0,0,1)
    cache={}
    tj['animations']=[]
    for spath,sname,newname,inplace in sources:
        if spath not in cache: cache[spath]=glb.load(spath)
        sj,sb=cache[spath]; sord,spar=order(sj); sn=byname(sj); sname_of={i:n.get('name') for i,n in enumerate(sj['nodes'])}
        s_rest_l=rest_locals(sj,sb); Ws_rest=world_rots(sj,sord,spar,s_rest_l)
        sh=sn[hips_name(sn)]; ps_rest=sj['nodes'][sh].get('translation',[0,0,0]); Ps=Ws_rest[spar[sh]] if sh in spar else (0,0,0,1)
        hs=abs(qrot(Ps,ps_rest)[1]); ht=abs(qrot(Pt,pt_rest)[1]); ratio=ht/hs
        C=qmul(Bt,qinv(basis_q(sj,sord,spar,s_rest_l,sn)))
        anim=[a for a in sj['animations'] if a['name']==sname][0]; ch=channels(sj,sb,anim)
        times=max((v[0] for k,v in ch.items()),key=len)
        mapped=[i for i in tord if tname[i] in sn]
        rot_out={i:[] for i in mapped}; tr_out=[]
        hips_t0=None
        for t in times:
            def slr(i):
                k=(i,'rotation')
                return samp(ch[k],t,'rotation') if k in ch else tuple(sj['nodes'][i].get('rotation',[0,0,0,1]))
            Ws=world_rots(sj,sord,spar,slr)
            Wt={}
            for i in tord:
                if tname[i] in sn:
                    si=sn[tname[i]]
                    delta=qmul(qmul(C,qmul(Ws[si],qinv(Ws_rest[si]))),qinv(C))
                    Wt[i]=qnorm(qmul(delta,Wt_rest[i]))
                    pw=Wt[tpar[i]] if i in tpar else (0,0,0,1)
                    rot_out[i].append(qnorm(qmul(qinv(pw),Wt[i])))
                else:
                    pw=Wt[tpar[i]] if i in tpar else (0,0,0,1)
                    Wt[i]=qmul(pw,t_rest_l(i))
            k=(sh,'translation')
            v=samp(ch[k],t,'translation') if k in ch else ps_rest
            d=qrot(Ps,(v[0]-ps_rest[0],v[1]-ps_rest[1],v[2]-ps_rest[2]))
            d=qrot(C,d)
            if inplace: d=(0.0,d[1],0.0)
            d=qrot(qinv(Pt),(d[0]*ratio,d[1]*ratio,d[2]*ratio))
            tr_out.append((pt_rest[0]+d[0],pt_rest[1]+d[1],pt_rest[2]+d[2]))
        acc_i=add_acc(tj,tb,[(x,) for x in times],'SCALAR',minmax=True)
        out={'name':newname,'channels':[],'samplers':[]}
        for i in mapped:
            o=add_acc(tj,tb,rot_out[i],'VEC4')
            out['samplers'].append({'input':acc_i,'output':o,'interpolation':'LINEAR'})
            out['channels'].append({'sampler':len(out['samplers'])-1,'target':{'node':i,'path':'rotation'}})
        o=add_acc(tj,tb,tr_out,'VEC3')
        out['samplers'].append({'input':acc_i,'output':o,'interpolation':'LINEAR'})
        out['channels'].append({'sampler':len(out['samplers'])-1,'target':{'node':th,'path':'translation'}})
        tj['animations'].append(out)
        print('  %-5s <- %-13s %-11s frames=%d bones=%d'%(newname,spath.split('/')[-1],sname,len(times),len(mapped)))
    # பழைய (இப்போ பயன்படுத்தாத) animation data-வை நீக்க buffer-ஐ சுருக்க வேண்டாம் — எளிமைக்கு அப்படியே
    glb.save(out_path,tj,tb); print('wrote',out_path,os.path.getsize(out_path))

base='../three/examples/models/gltf/'
S,M,X=base+'Soldier.glb',base+'Michelle.glb',base+'Xbot.glb'
src=[(S,'Idle','Idle',False),(S,'Run','Run',True),(M,'SambaDance','Dance',False),(X,'agree','Yes',False),(X,'headShake','No',False),(X,'sad_pose','Sad',False)]
if __name__=='__main__':
    os.makedirs('../out',exist_ok=True)
    build(S,'../out/arjun.glb',src); build(M,'../out/meera.glb',src)
