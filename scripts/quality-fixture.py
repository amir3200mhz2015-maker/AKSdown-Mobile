import json, sys
def v(fid, h, fps, vc, ext, tbr): return dict(format_id=fid, url=f"https://example.invalid/{fid}", ext=ext, protocol="https", height=h, width=h*16//9, fps=fps, vcodec=vc, acodec="none", tbr=tbr, filesize=1)
def a(fid, ac, ext, abr): return dict(format_id=fid, url=f"https://example.invalid/{fid}", ext=ext, protocol="https", vcodec="none", acodec=ac, abr=abr, tbr=abr, asr=48000, audio_channels=2, filesize=1)
fmts = [a("139","mp4a.40.5","m4a",48), a("140","mp4a.40.2","m4a",129), a("251","opus","webm",135),
  v("160",144,30,"avc1.4d400c","mp4",80), v("134",360,30,"avc1.4d401e","mp4",300), v("136",720,30,"avc1.4d401f","mp4",1500),
  v("137",1080,30,"avc1.640028","mp4",3000), v("299",1080,60,"avc1.64002a","mp4",5000), v("303",1080,60,"vp9","webm",4500),
  v("308",1440,60,"vp9","webm",9000), v("315",2160,60,"vp9","webm",20000), v("401",2160,60,"av01.0.13M.08","mp4",18000),
  v("571",4320,60,"av01.0.17M.08","mp4",40000), v("18",360,30,"avc1.42001E","mp4",500)|{"acodec":"mp4a.40.2"}]
top = int(sys.argv[1])
fmts = [f for f in fmts if f.get("height",0) <= top]
json.dump(dict(id="t"+str(top), title="Quality test", extractor="youtube", extractor_key="Youtube", webpage_url="https://www.youtube.com/watch?v=t", formats=fmts, duration=60), open(f"info{top}.json","w"))
