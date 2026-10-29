import glb, math, bisect
from retarget import qmul,qrot,parents,byname
def sample(j,b,anim,t):
    out={}
    for ch in anim['channels']:
        s=anim['samplers'][ch['sampler']]; ts=[x[0] for x in glb.read_acc(j,b,s['input'])]; vs=glb.read_acc(j,b,s['output'])
        k=max(0,min(bisect.bisect_right(ts,t)-1,len(ts)-1)); out[(ch['target']['node'],ch['target']['path'])]=vs[k]
    return out
def world(j,b,animname,t):
    a=[x for x in j['animations'] if x['name']==animname][0]; sm=sample(j,b,a,t); par=parents(j)
    W={}
    def get(i):
        if i in W: return W[i]
        n=j['nodes'][i]
        r=tuple(sm.get((i,'rotation'),n.get('rotation',[0,0,0,1]))); tr=sm.get((i,'translation'),n.get('translation',[0,0,0])); sc=n.get('scale',[1,1,1])
        if i in par:
            pr,pt,ps=get(par[i]); p=qrot(pr,(tr[0]*ps,tr[1]*ps,tr[2]*ps)); res=(qmul(pr,r),(pt[0]+p[0],pt[1]+p[1],pt[2]+p[2]),ps*sc[0])
        else: res=(r,tuple(tr),sc[0])
        W[i]=res; return res
    nm=byname(j); return lambda name: get(nm[name])[1]
def dirs(j,b,anim,t):
    w=world(j,b,anim,t); h=w('mixamorig:Hips')
    out=[]
    for part in ['mixamorig:Head','mixamorig:LeftHand','mixamorig:RightHand','mixamorig:LeftFoot','mixamorig:RightFoot']:
        p=w(part); v=(p[0]-h[0],p[1]-h[1],p[2]-h[2]); L=math.sqrt(sum(x*x for x in v)); out.append(tuple(round(x/L,2) for x in v))
    return out
base='../three/examples/models/gltf/'
cases=[('Soldier.glb','Run','Run',0.3),('Michelle.glb','SambaDance','Dance',2.0),('Xbot.glb','agree','Yes',0.5),('Xbot.glb','sad_pose','Sad',0.0)]
for src,sa,ta,t in cases:
    sj,sb=glb.load(base+src); print(ta,'src ',dirs(sj,sb,sa,t))
    for tgt in ['arjun','meera']:
        tj,tb=glb.load('../out/%s.glb'%tgt); print('   ',tgt.ljust(5),dirs(tj,tb,ta,t))
