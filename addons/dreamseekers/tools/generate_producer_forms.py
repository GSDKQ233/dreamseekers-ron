#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Author the three original, read-only Forge OBJ dressings. No world or NBT writes.
Run without options to regenerate the checked-in assets, or --check for a byte-level check.
No Minecraft/third-party textures, models, or dependencies are used by the generator.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import random
import struct
import zlib

ADDON = Path(__file__).resolve().parents[1]
ASSETS = ADDON / "src/main/resources/assets/dreamseekers"
FORM = Path("models/producer_form")
TEX = "dreamseekers:block/producer_form_inlay"

# Four-by-four swatch atlas. Each face uses the interior of its own pixel-art tile.
SWATCH = [
    ("vine", "386f67"), ("jade", "75c5a4"), ("ivory", "f4dfcc"), ("peach", "f4a6b0"),
    ("rose", "c96c9e"), ("honey", "f0bd67"), ("dew", "9befe2"), ("ink", "403d57"),
    ("timber", "845952"), ("brass", "c2895d"), ("steel", "686c87"), ("amethyst", "9770da"),
    ("lilac", "c399fa"), ("violet", "6950a1"), ("ember", "ed8475"), ("moon", "dbdcff")]
COLORS = {key: "#" + color for key, color in SWATCH}
ADD = lambda a,b: tuple(x+y for x,y in zip(a,b))
SUB = lambda a,b: tuple(x-y for x,y in zip(a,b))
MUL = lambda a,s: tuple(x*s for x in a)
def cross(a,b):
    x,y,z = a; u,v,w = b
    return (y*w-z*v,z*u-x*w,x*v-y*u)
def dot(a,b): return sum(x*y for x,y in zip(a,b))
def norm(a): return math.sqrt(dot(a,a))
def unit(a): return MUL(a, 1 / norm(a))
def average(vs): return tuple(sum(p[i] for p in vs) / len(vs) for i in range(3))

class Mesh:
    def __init__(self): self.faces = []
    def face(self, vs, mat):
        assert mat in COLORS and len(vs) in (3,4)
        assert norm(cross(SUB(vs[1],vs[0]),SUB(vs[2],vs[0]))) > 1.e-7
        self.faces.append((tuple(vs), mat))
    def prism(self, rings, sides, cap=None):
        """Outward-facing six- or eight-sided solids, including oriented crystal/petal tips."""
        n = len(rings[0]); cap = cap or sides[0]
        center = average([p for ring in rings for p in ring])
        def outward(v,mat):
            normal = cross(SUB(v[1],v[0]), SUB(v[2],v[0]))
            self.face(v if dot(normal,SUB(average(v),center)) >= 0 else tuple(reversed(v)),mat)
        for ring in (rings[0],rings[-1]):
            mid = average(ring)
            for i in range(n):
                outward([mid,ring[i],ring[(i+1)%n]],cap)
        for k,(r0,r1) in enumerate(zip(rings,rings[1:])):
            for i in range(n):
                j=(i+1)%n
                outward([r0[i],r0[j],r1[j],r1[i]],sides[(k*n+i)%len(sides)])
    def beam(self,a,b,width,depth,mat):
        # Maintain a stable cross section even for upright posts.
        d=unit(SUB(b,a)); side=unit(cross(d,(0,1,0) if abs(d[1])<.98 else (0,0,1)))
        up=unit(cross(side,d)); rings=[]
        for p in (a,b):
            rings.append([ADD(p,ADD(MUL(side,u*width/2),MUL(up,v*depth/2)))
                          for u,v in ((1,1),(-1,1),(-1,-1),(1,-1))])
        self.prism(rings,[mat])
    def box(self,x,y,z,w,h,d,mat):
        self.beam((x,y-h/2,z),(x,y+h/2,z),w,d,mat)
    def crystal(self,x,y,z,h,r,sides,tilt=(0,0),n=6):
        # A narrow physical tip, not a degenerate face.
        rings=[]
        for t,f in ((0,.47),(.20,1),(.70,.82),(1,.035)):
            rings.append([(x+tilt[0]*t+r*f*math.cos(i*math.tau/n),y+h*t,
                           z+tilt[1]*t+r*f*math.sin(i*math.tau/n)) for i in range(n)])
        self.prism(rings,sides,sides[-1])
    def rim(self,xyz,r,width,height,mat,n=8,phase=math.pi/8,axis='y'):
        # Hollow flat-banded ring; no hidden end-caps at every slice.
        x,y,z=xyz
        def point(i,k,v):
            t=math.tau*i/n+phase; a=(r+k)*math.cos(t); b=(r+k)*math.sin(t)
            return (x+a,y+v,z+b) if axis=='y' else (x+a,y+b,z+v)
        span=[(-width/2,height/2),(width/2,height/2),(width/2,-height/2),(-width/2,-height/2)]
        for i in range(n):
            for j in range(4):
                k=(j+1)%4
                vs=[point(i,*span[j]),point(i,*span[k]),point(i+1,*span[k]),point(i+1,*span[j])]
                # In XZ plane the above winding points inward; XY plane points outward.
                if axis=='y': vs.reverse()
                self.face(vs,mat)
    def petal(self,angle,lower,upper,spine,half,colors):
        # A folded, solid petal: two top facets meet at a vein, rose underside.
        e=(math.cos(angle),0,math.sin(angle)); t=(-e[2],0,e[0])
        base=ADD(MUL(e,lower[0]),(0,lower[1],0)); tip=ADD(MUL(e,upper[0]),(0,upper[1],0))
        mid=ADD(MUL(e,spine[0]),(0,spine[1],0))
        vertices=[base,ADD(mid,MUL(t,half)),tip,ADD(mid,MUL(t,-half)),ADD(mid,(0,.27,0)),ADD(mid,(0,-.15,0))]
        center=average(vertices)
        for i in range(4):
            j=(i+1)%4
            for idx,mat in ((4,colors[i//2]),(5,colors[2])):
                f=[vertices[i],vertices[j],vertices[idx]]
                if dot(cross(SUB(f[1],f[0]),SUB(f[2],f[0])),SUB(average(f),center))<0: f.reverse()
                self.face(f,mat)

def moonflower():
    m=Mesh()
    # Four stilts on the existing corner posts keep every crop and its sightline open.
    for a in (-1,1):
        for b in (-1,1):
            foot=(2*a,.02,3*b); knee=(1.84*a,1.48,2.76*b); bow=(1.18*a,2.62,1.83*b)
            neck=(.32*a,3.09,.48*b)
            m.box(foot[0],.18,foot[2],.48,.36,.48,"ivory")
            m.beam(foot,knee,.23,.23,"vine"); m.beam(knee,bow,.22,.22,"jade")
            m.beam(bow,neck,.20,.20,"vine")
            m.box(knee[0],knee[1],knee[2],.36,.21,.36,"honey")
            m.crystal(bow[0],bow[1]-.05,bow[2],.60,.16,["jade","dew","jade"],(.15*a,.22*b),4)
    m.rim((0,3.12,0),.56,.22,.24,"honey",8)
    for i in range(6):
        a=i*math.tau/6+math.pi/6
        m.petal(a,(.13,3.10),(2.06,4.30),(1.36,3.58),.52,("peach","ivory","rose"))
        m.petal(a+math.pi/6,(.12,3.24),(1.22,4.66),(.82,3.79),.34,("rose","peach","rose"))
    m.crystal(0,3.39,0,1.48,.35,["honey","dew","honey","ivory"],n=6)
    return m

def crystal_windlass():
    m=Mesh()
    # Tall paired portals with an open ore track, a fixed capstan and two hanging ore teeth.
    for a in (-1,1):
        def p(x,y,z): return (a*x,y,z)
        for b in (-1,1):
            foot=p(3,0,b); mid=p(2.76,2.56,.66*b); top=p(2.49,5.20,.13*b)
            m.box(foot[0],.21,b,.83,.46,.78,"steel")
            m.beam(foot,mid,.45,.45,"timber"); m.beam(mid,top,.42,.42,"timber")
            m.box(mid[0],mid[1],mid[2],.51,.28,.51,"brass")
        m.beam(p(2.5,4.90,0),p(1.73,5.96,0),.43,.43,"steel")
        m.beam(p(2.53,4.41,-.12),p(1.15,5.04,-.12),.23,.24,"brass")
        m.beam(p(1.50,5.82,0),p(1.40,3.64,0),.12,.12,"ink")
        m.box(a*1.4,3.60,0,.68,.32,.59,"brass")
        m.crystal(a*1.40,3.52,0,-1.30,.42,["violet","amethyst","lilac","amethyst","lilac","violet"],(.12*a,0))
        m.crystal(a*2.35,5.13,0,1.37,.34,["amethyst","lilac","violet"],(.57*a,.10),4)
    m.beam((-2.45,5.09,0),(2.45,5.09,0),.52,.49,"ink")
    m.beam((-1.74,5.85,0),(1.74,5.85,0),.43,.36,"brass")
    m.rim((0,5.47,-.34),.75,.21,.28,"brass",8,0,'z')
    for a in range(4):
        x=.57*math.cos(a*math.pi/4); y=.57*math.sin(a*math.pi/4)
        m.beam((-x,5.47-y,-.35),(x,5.47+y,-.35),.12,.12,"steel")
    m.box(0,5.47,-.43,.33,.32,.34,"lilac")
    return m

def eclipse_collar():
    m=Mesh()
    # Three levels: an anchored copper stand, a hollow eclipse race, then a split ceramic iris.
    for i in range(4):
        a=i*math.pi/2; dx,dz=math.cos(a),math.sin(a)
        def p(r,h): return (dx*r,h,dz*r)
        m.beam(p(3.70,-.20),p(3.31,1.08),.54,.59,"ink")
        m.beam(p(3.31,1.08),p(2.50,1.50),.38,.41,"brass")
        m.box(dx*3.52,.40,dz*3.52,.64,.34,.64,"ember")
        m.petal(a,(2.0,1.45),(2.65,2.92 if i%2==0 else 2.49),(2.65,2.15),.29,
                ("moon","lilac","violet"))
    m.rim((0,1.39,0),2.78,.40,.42,"ink",8)
    m.rim((0,1.58,0),2.77,.32,.14,"brass",8)
    m.rim((0,2.06,0),1.98,.24,.24,"ember",8,0)
    for i in range(4):
        a=(i+.5)*math.pi/2
        m.beam((2.65*math.cos(a),1.56,2.65*math.sin(a)),
               (1.94*math.cos(a),2.03,1.94*math.sin(a)),.19,.19,"violet")
    m.crystal(0,.55,0,2.07,.61,["ink","violet","lilac","violet","amethyst","ink"],n=8)
    m.crystal(0,2.57,0,.55,.19,["ember","honey","ember"],n=4)
    return m

FORMS = {"moonflower":moonflower, "crystal_windlass":crystal_windlass, "eclipse_collar":eclipse_collar}

# Bake the mount into the OBJ so the preview and the game literally render the same
# geometry. The vintage furnace has a square chimney where the newer one has a wide bowl.
MOUNTS = {
    "moonflower": ("moonflower", (10.5,2.41,5.5), (1,1,1)),
    "crystal_windlass": ("crystal_windlass", (5.5,.96,2.5), (1,1,1)),
    "eclipse_collar": ("eclipse_collar", (5.5,8.78,7), (1,1,1)),
    "eclipse_chimney": ("eclipse_collar", (6,11.01,8), (.52,.92,.52))
}
def mounted(name):
    model,at,scale=MOUNTS[name]; result=Mesh()
    for vs,m in FORMS[model]().faces:
        result.face([tuple(v[i]*scale[i]+at[i] for i in range(3)) for v in vs],m)
    return result

def obj(mesh):
    vs,uvs,faces={},{},[]
    for face,mat in mesh.faces:
        i=[n for n,_ in SWATCH].index(mat); u=i%4; v=i//4
        coords=((.06,.06),(.94,.06),(.94,.94),(.06,.94))
        f=[]
        for p,(du,dv) in zip(face,coords):
            xy=((u+du)/4,(v+dv)/4); p=tuple(round(x,5)+0.0 for x in p)
            f.append((vs.setdefault(p,len(vs)+1),uvs.setdefault(xy,len(uvs)+1)))
        faces.append("f "+" ".join(f"{i}/{j}" for i,j in f))
    return ("# Original Dreamseekers producer form; coordinates in local block units.\nmtllib inlay.mtl\n"
            +"\n".join("v "+" ".join(f"{c:.5f}" for c in p) for p in vs)+"\n"
            +"\n".join("vt "+" ".join(f"{c:.5f}" for c in p) for p in uvs)
            +"\nusemtl inlay\n"+"\n".join(faces)+"\n").encode()

def tiles():
    def chunk(t,p):
        return struct.pack(">I",len(p))+t+p+struct.pack(">I",zlib.crc32(t+p)&0xffffffff)
    size=64; raw=bytearray(); rng=random.Random(0xD3E0)
    for y in range(size):
        raw.append(0)
        for x in range(size):
            key,h=SWATCH[(y//16)*4 + x//16]
            k=tuple(int(h[i:i+2],16) for i in (0,2,4))
            xx,yy=x%16,y%16
            if key in ("timber","vine"): line=5 if (xx+yy//9)%7==1 else -2
            elif key in ("amethyst","lilac","violet","dew","moon"): line=7 if (xx+yy)%12 in (2,3) else -1
            else: line=4 if yy in (2,12) and xx%7<4 else 0
            d=line+rng.choice((-3,-2,0,0,1,3))
            raw.extend(max(0,min(255,c+d)) for c in k)
    return b"\x89PNG\r\n\x1a\n"+chunk(b"IHDR",struct.pack(">IIBBBBB",size,size,8,2,0,0,0))+chunk(b"IDAT",zlib.compress(bytes(raw),9))+chunk(b"IEND",b"")

def generate():
    files = {FORM/"inlay.mtl":("newmtl inlay\nKa 0 0 0\nKd 1 1 1\nmap_Kd "+TEX+"\n").encode(),
             Path("textures/block/producer_form_inlay.png"):tiles()}
    for name in MOUNTS:
        files[FORM/(name+".obj")]=obj(mounted(name))
        files[FORM/(name+".json")]=(json.dumps({"loader":"forge:obj","model":f"dreamseekers:models/producer_form/{name}.obj",
            "automatic_culling":False,"flip_v":False,"emissive_ambient":False,
            "textures":{"particle":TEX}},indent=2)+"\n").encode()
    return files

if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__); p.add_argument("--check",action="store_true"); args=p.parse_args()
    for path,data in generate().items():
        target=ASSETS/path
        if args.check:
            if not target.exists() or target.read_bytes()!=data: raise SystemExit(f"Stale/missing art: {path}")
        else:
            target.parent.mkdir(exist_ok=True,parents=True); target.write_bytes(data)
        print(f"{hashlib.sha256(data).hexdigest()[:12]}  {path}")
