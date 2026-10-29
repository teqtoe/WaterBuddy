import json, struct
def load(p):
    d=open(p,'rb').read()
    assert d[:4]==b'glTF'
    off=12; j=None; b=b''
    while off<len(d):
        ln,ty=struct.unpack('<II',d[off:off+8]); c=d[off+8:off+8+ln]
        if ty==0x4E4F534A: j=json.loads(c)
        else: b=c
        off+=8+ln
    return j,bytearray(b)
CT={5126:('f',4),5123:('H',2),5125:('I',4),5121:('B',1),5120:('b',1),5122:('h',2)}
NC={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4,'MAT4':16}
def read_acc(j,b,i):
    a=j['accessors'][i]; bv=j['bufferViews'][a['bufferView']]
    fmt,sz=CT[a['componentType']]; n=NC[a['type']]
    stride=bv.get('byteStride',sz*n); base=bv.get('byteOffset',0)+a.get('byteOffset',0)
    out=[]
    for k in range(a['count']):
        o=base+k*stride
        out.append(struct.unpack('<'+fmt*n,bytes(b[o:o+sz*n])))
    return out
def save(p,j,b):
    while len(b)%4: b.append(0)
    j['buffers'][0]['byteLength']=len(b)
    js=json.dumps(j,separators=(',',':')).encode()
    while len(js)%4: js+=b' '
    total=12+8+len(js)+8+len(b)
    with open(p,'wb') as f:
        f.write(struct.pack('<III',0x46546C67,2,total))
        f.write(struct.pack('<II',len(js),0x4E4F534A)); f.write(js)
        f.write(struct.pack('<II',len(b),0x004E4942)); f.write(bytes(b))
