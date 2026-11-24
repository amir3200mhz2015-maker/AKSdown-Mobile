"""Offline check that AKSdown's format rules (Engine.kt) always pick the highest available quality.
Uses yt-dlp's --load-info-json with YouTube-style format lists (144p..8K), so no network is needed."""
import os, subprocess, sys, tempfile
YTDLP = os.path.abspath(sys.argv[1]); here = os.path.dirname(os.path.abspath(__file__))
os.chdir(tempfile.mkdtemp()); ok = True
for top in (4320, 2160, 1440, 1080, 720, 360):
    subprocess.run([sys.executable, os.path.join(here, "quality-fixture.py"), str(top)], check=True)
    r = subprocess.run([sys.executable, "-I", YTDLP, "--load-info-json", f"info{top}.json", "--no-warnings",
        "-f", "bv*+ba/b", "-S", "res,fps,hdr:12,acodec:aac", "--print", "%(height)s|%(fps)s|%(format_id)s|%(vcodec)s|%(acodec)s"],
        capture_output=True, text=True)
    out = r.stdout.strip().splitlines()[-1]
    h = int(out.split("|")[0]); good = h == top
    ok &= good
    print(f"{'PASS' if good else 'FAIL'} highest available {top}p -> selected {out}")
r = subprocess.run([sys.executable, "-I", YTDLP, "--load-info-json", "info4320.json", "--no-warnings", "-f", "ba/b",
    "--print", "%(format_id)s|%(acodec)s|%(abr)s"], capture_output=True, text=True)
print("MP3 source audio ->", r.stdout.strip())
sys.exit(0 if ok else 1)
