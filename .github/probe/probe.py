import json, time, urllib.request, urllib.error, sys

VID = sys.argv[1] if len(sys.argv) > 1 else "Xgxzdt-rzPg"
UA = ("Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 "
      "(KHTML, like Gecko) Version/26.0 Safari/605.1.15")
MEDIA_UA = "okhttp/4.12.0"


def player(host):
    body = {
        "context": {"client": {
            "clientName": "VISIONOS", "clientVersion": "1.02", "osName": "visionOS",
            "osVersion": "26.5.23O471", "deviceMake": "Apple", "deviceModel": "RealityDevice17,1",
            "hl": "en", "gl": "US", "userAgent": UA}},
        "videoId": VID, "contentCheckOk": True, "racyCheckOk": True,
    }
    req = urllib.request.Request(
        f"https://{host}/youtubei/v1/player?prettyPrint=false",
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json", "User-Agent": UA,
                 "X-YouTube-Client-Name": "101", "X-YouTube-Client-Version": "1.02"})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def fetch(url, rng=None, limit=None, timeout=40):
    h = {"User-Agent": MEDIA_UA}
    if rng:
        h["Range"] = rng
    req = urllib.request.Request(url, headers=h)
    t0 = time.time()
    try:
        r = urllib.request.urlopen(req, timeout=timeout)
    except urllib.error.HTTPError as e:
        return f"HTTP {e.code} {dict(e.headers).get('Content-Range')}"
    got = 0
    err = None
    try:
        while True:
            if time.time() - t0 > timeout:
                err = "time limit"
                break
            b = r.read(65536)
            if not b:
                break
            got += len(b)
            if limit and got >= limit:
                err = "stopped by probe"
                break
    except Exception as e:  # noqa
        err = repr(e)
    dt = time.time() - t0
    return (f"status={r.status} content-range={r.headers.get('Content-Range')} "
            f"content-length={r.headers.get('Content-Length')} got={got} in {dt:.1f}s "
            f"({got / max(dt, 0.01) / 1024:.0f} KB/s) end={err or 'server closed'}")


for host in ["music.youtube.com", "www.youtube.com"]:
    try:
        p = player(host)
    except Exception as e:  # noqa
        print(host, "player failed", e)
        continue
    ps = p.get("playabilityStatus", {})
    print(host, "playability:", ps.get("status"), ps.get("reason"))
    fmts = p.get("streamingData", {}).get("adaptiveFormats", [])
    audio = [f for f in fmts if f.get("mimeType", "").startswith("audio/")]
    for f in audio:
        print("  itag", f.get("itag"), f.get("mimeType"), "clen", f.get("contentLength"),
              "url" if "url" in f else "cipher", f.get("audioTrack", {}).get("displayName", ""),
              "drc" if f.get("isDrc") else "")
    usable = [f for f in audio if "url" in f]
    if not usable:
        continue
    for f in sorted(usable, key=lambda f: f.get("itag") != 140)[:2]:
        url, clen = f["url"], int(f.get("contentLength") or 0)
        print(f"== itag {f['itag']} clen={clen}")
        print("  range 0-0:          ", fetch(url, "bytes=0-0"))
        print("  range 0-1MiB:       ", fetch(url, "bytes=0-1048575"))
        print("  range 0-4MiB:       ", fetch(url, "bytes=0-4194303"))
        mid = clen // 2
        print("  range mid+1MiB:     ", fetch(url, f"bytes={mid}-{mid + 1048575}"))
        print("  range last-1MiB+:   ", fetch(url, f"bytes={clen - 1000}-{clen + 1048575}"))
        print("  range past end:     ", fetch(url, f"bytes={clen}-{clen + 1000}"))
        print("  open bytes=0-:      ", fetch(url, "bytes=0-", timeout=60))
        print("  no range header:    ", fetch(url, None, timeout=60))
        print("  &range=0-4194303:   ", fetch(url + "&range=0-4194303"))
    break
