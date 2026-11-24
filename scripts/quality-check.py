"""Host-side check that AKSdown's format rules pick the highest available resolution.
Runs the exact bundled yt-dlp build with the same -f/-S options the app uses (see Engine.kt)."""
import json, subprocess, sys
YTDLP = sys.argv[1]
TESTS = [
    # Public, freely licensed test videos (Blender Foundation open movies, CC-BY; yt-dlp's own test video).
    "https://www.youtube.com/watch?v=aqz-KE-bpKQ",   # Big Buck Bunny 60fps, up to 4K/8K
    "https://www.youtube.com/watch?v=jNQXAC9IVRw",   # "Me at the zoo"
    "https://vimeo.com/1084537",                      # Big Buck Bunny on Vimeo
]
COMMON = ["--no-playlist", "--js-runtimes", "node", "--no-warnings"]
ok = True
for url in TESTS:
    try:
        info = json.loads(subprocess.run([sys.executable, YTDLP, *COMMON, "-J", url], capture_output=True, text=True, timeout=300).stdout)
    except Exception as e:
        print(f"SKIP {url}: could not read info ({e})"); continue
    vids = [f for f in info.get("formats", []) if f.get("vcodec") not in (None, "none") and f.get("height")]
    max_h = max((f["height"] for f in vids), default=0)
    sel = subprocess.run([sys.executable, YTDLP, *COMMON, "-f", "bv*+ba/b", "-S", "res,fps,hdr:12,acodec:aac",
                          "--print", "%(format_id)s|%(height)s|%(fps)s|%(vcodec)s|%(acodec)s", url],
                         capture_output=True, text=True, timeout=300)
    line = sel.stdout.strip().splitlines()[-1] if sel.stdout.strip() else ""
    parts = line.split("|")
    h = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else 0
    status = "PASS" if h and h == max_h else "FAIL"
    if status == "FAIL": ok = False
    print(f"{status} {url}\n   highest available: {max_h}p | AKSdown selects: {line or sel.stderr.strip()[-300:]}")
sys.exit(0 if ok else 1)
