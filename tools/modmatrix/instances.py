import json,hashlib,pathlib,urllib.request,urllib.parse,time
P=pathlib.Path.home()/"Library/Application Support/PrismLauncher/instances"
H=lambda u:{"User-Agent":"theoria-planning/0.1","Content-Type":"application/json"}
def req(u,data=None):
    r=urllib.request.Request(u,data=json.dumps(data).encode() if data else None,headers=H(u))
    return json.load(urllib.request.urlopen(r,timeout=40))
jars={}
for inst in P.iterdir():
    m=inst/"minecraft/mods"
    if not m.is_dir(): continue
    for j in m.glob("*.jar"):
        jars.setdefault(hashlib.sha1(j.read_bytes()).hexdigest(),(j.name,inst.name))
res=req("https://api.modrinth.com/v2/version_files",{"hashes":list(jars),"algorithm":"sha1"})
proj={}
for h,v in res.items(): proj.setdefault(v["project_id"],[]).append(jars[h])
ids=list(proj)
info={p["id"]:p for p in req("https://api.modrinth.com/v2/projects?ids="+urllib.parse.quote(json.dumps(ids)))}
print("jars",len(jars),"modrinth-tanimli proje",len(ids),"tanimsiz",len(jars)-len(res))
out=[]
for pid in ids:
    p=info.get(pid); 
    if not p: continue
    q=urllib.parse.urlencode({"game_versions":json.dumps(["26.3"]),"loaders":json.dumps(["neoforge"])})
    try: v=req(f"https://api.modrinth.com/v2/project/{pid}/version?{q}")
    except Exception as e: v=None
    out.append((p["slug"],p["title"],p["project_type"],(v[0]["version_number"] if v else None),sorted({i for _,i in proj[pid]}),p.get("categories",[])[:3]))
    time.sleep(0.15)
json.dump(out,open("inst_mods.json","w"),ensure_ascii=False)
yes=[o for o in out if o[3]]; no=[o for o in out if not o[3]]
print("NEOFORGE 26.3 VAR:",len(yes)); 
for o in sorted(yes): print(" ",o[0],"|",o[3][:26],"|",",".join(o[5]))
print("NEOFORGE 26.3 YOK:",len(no)); print("  "+", ".join(sorted(o[0] for o in no)))
