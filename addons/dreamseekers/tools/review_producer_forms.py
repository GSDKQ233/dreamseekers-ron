#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Check actual NBT-to-OBJ contact/clearance. Optional --preview FILE uses Pillow + NumPy.
Renders the actual packaged meshes, with schematic base blocks/colors, not a game screenshot."""
import argparse
import gzip
from hashlib import sha256
import io
import json
import math
from pathlib import Path
import re
import struct
import sys
sys.dont_write_bytecode = True
from generate_producer_forms import ADDON, ASSETS, COLORS, FORM, SWATCH, cross, dot, norm, unit, SUB, generate

DATA = ADDON / "src/main/resources/data/dreamseekers/structures"
CASES = (("reverie_garden","moonflower","moonflower"), ("shadow_mine","crystal_windlass","crystal_windlass"),
         ("shadow_furnace","eclipse_chimney","eclipse_collar"))
def nbt(path):
    s=io.BytesIO(gzip.decompress(path.read_bytes()))
    def num(code): return struct.unpack(">"+code,s.read(struct.calcsize(">"+code)))[0]
    def string(): return s.read(num("H")).decode()
    def payload(t):
        if 0<t<7: return num("bhiqfd"[t-1])
        if t==8: return string()
        if t==9:
            subtype=num("b"); return [payload(subtype) for _ in range(num("i"))]
        if t==10:
            d={}
            while (k:=num("b"))!=0:
                key=string(); d[key]=payload(k)
            return d
        if t in (7,11,12):
            code={7:"b",11:"i",12:"q"}[t]; return [num(code) for _ in range(num("i"))]
        raise ValueError(t)
    assert num("b")==10; string(); root=payload(10); assert s.read()==b""
    palette=root["palette"]
    blocks={tuple(b["pos"]):palette[b["state"]] for b in root["blocks"] if palette[b["state"]]["Name"]!="minecraft:air"}
    return root["size"],blocks

def obj(name):
    vertices=[]; tex=[]; faces=[]
    for line in (ASSETS/FORM/(name+".obj")).read_text().splitlines():
        t,*v=line.split()
        if t=="v": vertices.append(tuple(map(float,v)))
        elif t=="vt": tex.append(tuple(map(float,v)))
        elif t=="f":
            pts=[]; mats=set()
            for p in v:
                i,j=map(int,p.split("/")); pts.append(vertices[i-1]); u,w=tex[j-1]
                assert 0<=u<1 and 0<=w<1; mats.add(SWATCH[4*int(4*w)+int(4*u)][0])
            assert len(mats)==1 and len(v) in (3,4)
            assert norm(cross(SUB(pts[1],pts[0]),SUB(pts[2],pts[0])))>1e-8, (name,line)
            faces.append((pts,mats.pop()))
    assert faces
    return faces

def rotate(p,k,mesh):
    x,y,z=p
    # Continuous vertex rotation about first block's center; integer NBT cell around origin.
    d=.5 if mesh else 0; x-=d; z-=d
    for _ in range(k): x,z=-z,x
    return x+d,y,z+d

def overlap_box(pts,cell,y0,y1):
    # A deliberately conservative AABB test: a PASS proves even each face's bounds
    # miss one clearance volume, not merely that its vertices were outside.
    x,z=cell; bounds=((x,x+1),(y0,y1),(z,z+1))
    return all(min(p[i] for p in pts)<hi-1e-6 and max(p[i] for p in pts)>lo+1e-6 for i,(lo,hi) in enumerate(bounds))

def check():
    for filename,data in generate().items():
        assert (ASSETS/filename).read_bytes()==data, f"Stale generated asset: {filename}"
    source=(ADDON/"src/main/java/com/dreamseekers/client/form/ProducerFormMount.java").read_text()
    feet={}
    enum=source[source.index('    GARDEN('):source.index('    final ResourceLocation')]
    for row in re.split(r'\n    (?=[A-Z]+\(")',enum.strip()):
        name=re.search(r'\("([a-z_]+)"',row)[1]
        positions=[tuple(map(int,parts)) for parts in re.findall(r'new BlockPos\((\d+),(\d+),(\d+)\)',row)]
        assert len(positions)==5; feet[name]=positions[1:]
    manifest=json.loads((ADDON/"structure_manifest.json").read_text())
    entries={e["id"]:e for e in manifest["structures"]+manifest["runtime_template_assets"]}
    pairs=[]
    for base,old,new in CASES:
        for suffix,name,other in (("",old,new),("_rework_v1",new,old)):
            st=base+suffix; path=DATA/(st+".nbt"); size,blocks=nbt(path); faces=obj(name)
            assert sha256(path.read_bytes()).hexdigest()==entries[st]["sha256"]
            assert len(blocks)==entries[st]["non_air_blocks"] and size==entries[st]["size_xyz"]
            if name!=other: assert not all(p in blocks for p in feet[other]), (st,"ambiguous mount")
            crop=[p for p,b in blocks.items() if b["Name"]=="minecraft:wheat"]
            if base=="reverie_garden": assert len(crop)==16
            for k in range(4):
                transformed={rotate(p,k,False) for p in blocks}
                assert all(rotate(p,k,False) in transformed for p in feet[name])
                allcells=[rotate(p,k,False) for p in ((0,0,0),(size[0]-1,0,size[2]-1))]
                lower=[min(p[i] for p in allcells) for i in (0,2)]
                upper=[max(p[i]+1 for p in allcells) for i in (0,2)]
                for vs,_ in faces:
                    corners=[rotate(p,k,True) for p in vs]
                    assert all(lower[0]<=x<=upper[0] and lower[1]<=z<=upper[1] for x,y,z in corners), (st,k,vs)
            # Cardinal transforms preserve axis-aligned empty volume, so one exact local check suffices.
            for vs,_ in faces:
                for x,y,z in crop: assert not overlap_box(vs,(x,z),y,y+1), (st,"crop",x,y,z)
                if min(p[1] for p in vs)<3 and max(p[1] for p in vs)>1:
                    for x,z in entries[base]["access_xz"]: assert not overlap_box(vs,(x,z),1,3), (st,"access",x,z)
            # Ensure the legs physically meet all specified supports (including the top half of an
            # original fence, or new slab); being merely somewhere over the building is not enough.
            for x,y,z in feet[name]:
                assert any(overlap_box(vs,(x,z),y,y+1) for vs,_ in faces),(st,"no foot contact",x,y,z)
            print(f"{st:28} {len(blocks):3} base cells, {len(faces):3} faces, 4 support cells × 4 rotations; access/crops clear")
            if suffix: pairs.append((base,size,blocks,faces))
    return pairs

# Neutral base-block palette. The actual world uses its installed textures and block models.
BASE_COLORS = {"grass_block":"89a37b","lantern":"dba568","light_blue_stained_glass":"91c5d5",
    "purple_stained_glass":"9d83be","stone_bricks":"8d939c","wheat":"ddbd68","chiseled_dyedreamquartz_block":"d6c9df",
    "dyedream_dirt":"8f7184","dyedream_glass":"dcc8df","dyedream_lantern":"e9c3de","dyedream_leaves":"d999bb",
    "dyedream_log":"b38baa","dyedream_planks":"caa2b6","dyedreamquartz_block":"e0d0e3","flower_14":"d882ba",
    "polished_calcite":"d2d5d2","amethyst_block":"957bae","blackstone":"454653","cobbled_deepslate":"60626b",
    "deepslate_bricks":"616571","deepslate_tiles":"515563","polished_blackstone_bricks":"514e5b","chain":"797a84",
    "spruce_log":"776656","waxed_cut_copper":"ab7d68","deepslate_tile":"515563","deepslate_brick":"616571"}
def box_faces(lo,hi):
    x,y,z=lo; a,b,c=hi
    return [((x,y,z),(x,y,c),(x,b,c),(x,b,z)),((a,y,c),(a,y,z),(a,b,z),(a,b,c)),
            ((x,b,c),(a,b,c),(a,b,z),(x,b,z)),((x,y,z),(a,y,z),(a,y,c),(x,y,c)),
            ((a,y,z),(x,y,z),(x,b,z),(a,b,z)),((x,y,c),(a,y,c),(a,b,c),(x,b,c))]
def boxes(key,props):
    if key.endswith("_slab"):
        t=props.get("type","bottom"); return [((0,.5 if t=="top" else 0,0),(1,.5 if t=="bottom" else 1,1))]
    if key.endswith("_stairs"):
        y=props.get("half")=="top"
        lo,hi={"north":((0,0,0),(1,1,.5)),"south":((0,0,.5),(1,1,1)),"west":((0,0,0),(.5,1,1)),"east":((.5,0,0),(1,1,1))}[props["facing"]]
        return [((0,.5 if y else 0,0),(1,1 if y else .5,1)),((lo[0],0 if y else .5,lo[2]),(hi[0],.5 if y else 1,hi[2]))]
    if key=="wheat": return [((u,.02,v),(u+.09,.83,v+.09)) for u in (.15,.46,.77) for v in (.15,.46,.77)]
    if "flower" in key: return [((.46,0,.46),(.54,.65,.54)),((.22,.5,.22),(.78,.72,.78))]
    if "lantern" in key: return [((.31,.1,.31),(.69,.66,.69)),((.40,.65,.40),(.60,.86,.60))]
    if key.endswith("_fence"): return [((.35,0,.35),(.65,1,.65))]
    if key in ("chain","iron_bars"): return [((.43,0,.43),(.57,1,.57))]
    return [((0,0,0),(1,1,1))]

def render(pairs,outpath):
    from PIL import Image,ImageDraw,ImageFont
    import numpy as np
    view=unit((.64,.82,-1)); right=unit((-view[2],0,view[0])); up=unit(cross(right,view))
    light=unit((-.45,1,-.8))
    def rgb(h): return tuple(int(h.lstrip("#")[i:i+2],16) for i in (0,2,4))
    def panel(base_f,extra,project,width,height):
        # Orthographic depth buffer, not a face-center painter's sort; crossing braces and the old
        # structure occlude one another identically in both panels. Each pair shares one fixed camera.
        ox,oy,scale=project; color=np.zeros((height,width,4),np.uint8); depth=np.full((height,width),-np.inf)
        for vs,col,variation in base_f+extra:
            n=unit(cross(SUB(vs[1],vs[0]),SUB(vs[2],vs[0])))
            if dot(n,view)<=.00001: continue
            shade=(.73+.25*max(0,dot(n,light)))*variation
            c=tuple(max(0,min(255,round(t*shade))) for t in rgb(col))+(255,)
            verts=[(ox+dot(p,right)*scale,oy-dot(p,up)*scale,dot(p,view)) for p in vs]
            for v in [verts[0:3]]+([verts[0:1]+verts[2:4]] if len(verts)==4 else []):
                x0=max(0,math.floor(min(p[0] for p in v))); x1=min(width-1,math.ceil(max(p[0] for p in v)))
                y0=max(0,math.floor(min(p[1] for p in v))); y1=min(height-1,math.ceil(max(p[1] for p in v)))
                if x1<x0 or y1<y0: continue
                (a,b,c0),(d,e,f),(g,h,i)=v; det=(e-h)*(a-g)+(g-d)*(b-h)
                if abs(det)<1e-7: continue
                yy,xx=np.ogrid[y0:y1+1,x0:x1+1]; xx=xx+.5; yy=yy+.5
                s=((e-h)*(xx-g)+(g-d)*(yy-h))/det; t=((h-b)*(xx-g)+(a-g)*(yy-h))/det; u=1-s-t
                z=s*c0+t*f+u*i; dst=depth[y0:y1+1,x0:x1+1]; inside=(s>=-1e-7)&(t>=-1e-7)&(u>=-1e-7)&(z>dst)
                dst[inside]=z[inside]; color[y0:y1+1,x0:x1+1][inside]=c
        return Image.fromarray(color)
    def font(size,bold=False):
        for path,index in [(Path("/usr/share/fonts/opentype/noto/NotoSansCJK-"+("Bold" if bold else "Regular")+".ttc"),2),
                           (Path("C:/Windows/Fonts/msyh.ttc"),0),(Path("/System/Library/Fonts/PingFang.ttc"),0)]:
            if path.exists(): return ImageFont.truetype(str(path),size,index=index)
        return ImageFont.load_default(size)
    w,h=1596,1118; paper=Image.new("RGB",(w,h),"#182029"); draw=ImageDraw.Draw(paper)
    def txt(x,y,t,size,col,b=False): draw.text((x,y),t,font=font(size,b),fill=col,stroke_width=0)
    txt(58,22,"DREAMSEEKERS   /   FIELD GUIDE     03",19,"#b3b9b7",True)
    txt(54,53,"资源建筑 · 升级形态",49,"#f4f0e8",True)
    txt(1062,84,"同一建筑　／　同一视角　／　同一比例",21,"#d9d8ce")
    titles=[("梦生花园","凝露花冠","四柱架起莲冠，保留全部作物。","#acdec5"),
            ("潜影矿井","晶脉绞架","双晶悬于矿道两侧，绞轮连接横梁。","#c7b7e6"),
            ("凝影炉场","蚀环熔炉","双层炉环束起影核，导流翼张开。","#e9b497")]
    for idx,((base,size,blocks,mesh),(name,title,sub,accent)) in enumerate(zip(pairs,titles)):
        x=46+idx*509; cw=488
        draw.rounded_rectangle((x,149,x+cw,1036),radius=17,fill="#27313b",outline="#43505a",width=2)
        txt(x+20,164,name,32,"#f3eee8",True); txt(x+22,220,title,23,accent,True)
        draw.line((x+21,270,x+cw-21,270),fill="#4a535c")
        base_f=[]
        for (bx,by,bz),b in blocks.items():
            key=b["Name"].split(":")[-1]; stem=key.removesuffix("_stairs").removesuffix("_slab")
            shade=.995+(((bx*11+by*3+bz*7)%7)-3)*.010
            for lo,hi in boxes(key,b.get("Properties",{})):
                q=(bx+lo[0],by+lo[1],bz+lo[2]); r=(bx+hi[0],by+hi[1],bz+hi[2])
                base_f.extend((v,BASE_COLORS.get(stem,"9c8c9b"),shade) for v in box_faces(q,r))
        extra=[(v,COLORS[m],1.05) for v,m in mesh]
        allv=[p for f in base_f+extra for p in f[0]]
        xx=[dot(p,right) for p in allv]; yy=[-dot(p,up) for p in allv]
        # Both before and after have the same neutral background and camera.
        pw,ph=455,292; scale=min((pw-30)/(max(xx)-min(xx)),(ph-18)/(max(yy)-min(yy)))
        pr=((pw-scale*(max(xx)+min(xx)))/2,(ph-scale*(max(yy)+min(yy)))/2,scale)
        for k,yy0 in enumerate((310,663)):
            txt(x+20,yy0-34,"01  原形" if k==0 else "02  此建筑升级后",17,"#b7c0c6" if k==0 else accent,True)
            layer=panel(base_f,extra if k else [],pr,pw,ph)
            paper.paste(layer,(x+16,yy0+4),layer)
        draw.line((x+21,979,x+cw-21,979),fill="#4a535c")
        txt(x+20,985,sub,18,"#d5d8d4")
    txt(55,1056,"模型示意，非游戏截图。基础建筑读取 NBT，升级构件读取游戏所用 OBJ；材质和光照为示意。",21,"#c4c9c9")
    outpath.parent.mkdir(exist_ok=True,parents=True); paper.save(outpath)

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--preview",type=Path)
    args=parser.parse_args(); pairs=check()
    if args.preview: render(pairs,args.preview)
