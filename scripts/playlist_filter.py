#!/usr/bin/env python3
"""
TeleMate Playlist Filter & Deduplicator
- Fetches raw India IPTV playlist from iptv-org
- Removes resolution clutter [1080p], [720p], [Geo-blocked]
- Normalizes and groups duplicate channel variants
- Tests stream URLs asynchronously to eliminate dead links
- Keeps highest-quality working stream per channel/language
- Outputs clean_india.m3u
"""

import re
import sys
import urllib.request
import urllib.error
from concurrent.futures import ThreadPoolExecutor, as_completed

SOURCE_URL = "https://iptv-org.github.io/iptv/countries/in.m3u"
OUTPUT_FILE = "clean_india.m3u"
TIMEOUT_SECONDS = 3.0
MAX_WORKERS = 40

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "*/*"
}

def clean_channel_name(title):
    # Remove resolution tags like (1080p), [720p], (576p), (480p)
    name = re.sub(r'[\(\[]\s*\d{3,4}p\s*[\)\]]', '', title, flags=re.IGNORECASE)
    # Remove clutter tags
    name = re.sub(r'[\(\[]\s*(Geo-blocked|Not 24/7|Backup|Test)\s*[\)\]]', '', name, flags=re.IGNORECASE)
    # Remove trailing/leading dashes, spaces, pipes
    name = re.sub(r'\s*-\s*$', '', name)
    name = re.sub(r'\s*\|\s*$', '', name)
    name = name.strip()
    return name

def extract_quality_score(title, url):
    title_lower = title.lower() + " " + url.lower()
    if "1080" in title_lower or "fhd" in title_lower:
        return 1080
    if "720" in title_lower or "hd" in title_lower:
        return 720
    if "576" in title_lower:
        return 576
    if "480" in title_lower:
        return 480
    if "360" in title_lower:
        return 360
    return 500

def check_stream_alive(url):
    try:
        req = urllib.request.Request(url, headers=HEADERS, method="HEAD")
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            code = resp.getcode()
            return code in range(200, 400)
    except Exception:
        # Some servers reject HEAD; try a lightweight GET with range 0-100 bytes
        try:
            req2 = urllib.request.Request(url, headers={**HEADERS, "Range": "bytes=0-100"}, method="GET")
            with urllib.request.urlopen(req2, timeout=TIMEOUT_SECONDS) as resp2:
                return resp2.getcode() in range(200, 400)
        except Exception:
            return False

def parse_m3u(content):
    lines = content.splitlines()
    channels = []
    current_extinf = None
    
    for line in lines:
        line = line.strip()
        if not line:
            continue
        if line.startswith("#EXTINF:"):
            current_extinf = line
        elif current_extinf and not line.startswith("#"):
            url = line
            # Extract channel title after the comma
            parts = current_extinf.split(",", 1)
            raw_title = parts[1].strip() if len(parts) > 1 else "Unknown Channel"
            clean_name = clean_channel_name(raw_title)
            quality = extract_quality_score(raw_title, url)
            
            channels.append({
                "extinf": current_extinf,
                "raw_title": raw_title,
                "clean_name": clean_name,
                "url": url,
                "quality": quality
            })
            current_extinf = None
            
    return channels

def main():
    print(f"[TeleMate] Fetching playlist from {SOURCE_URL}...")
    try:
        req = urllib.request.Request(SOURCE_URL, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=10) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
    except Exception as e:
        print(f"[Error] Failed to fetch playlist: {e}")
        sys.exit(1)

    raw_channels = parse_m3u(content)
    print(f"[TeleMate] Found {len(raw_channels)} total channel entries.")

    # Deduplicate and prioritize highest quality per clean channel name
    grouped = {}
    for ch in raw_channels:
        key = ch["clean_name"].lower()
        if key not in grouped:
            grouped[key] = []
        grouped[key].append(ch)

    # Sort each group by quality descending (1080 > 720 > 576...)
    for key in grouped:
        grouped[key].sort(key=lambda x: x["quality"], reverse=True)

    print(f"[TeleMate] Grouped into {len(grouped)} unique channels.")
    print(f"[TeleMate] Testing stream health (concurrent checking)...")

    verified_channels = []

    def verify_group(group_key, items):
        # Test streams starting from highest quality down to lowest
        for ch in items:
            if check_stream_alive(ch["url"]):
                # Update extinf with clean name
                extinf = ch["extinf"]
                if "," in extinf:
                    prefix = extinf.split(",", 1)[0]
                    clean_extinf = f"{prefix},{ch['clean_name']}"
                else:
                    clean_extinf = extinf
                return {
                    "extinf": clean_extinf,
                    "url": ch["url"],
                    "clean_name": ch["clean_name"]
                }
        return None

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = {executor.submit(verify_group, k, v): k for k, v in grouped.items()}
        completed = 0
        total = len(futures)
        for fut in as_completed(futures):
            res = fut.result()
            if res:
                verified_channels.append(res)
            completed += 1
            if completed % 50 == 0 or completed == total:
                print(f"[TeleMate] Checked {completed}/{total} channels... ({len(verified_channels)} alive)")

    # Sort output alphabetically by clean channel name
    verified_channels.sort(key=lambda x: x["clean_name"].lower())

    print(f"[TeleMate] Writing {len(verified_channels)} verified channels to {OUTPUT_FILE}...")
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        f.write("#EXTM3U\n")
        for ch in verified_channels:
            f.write(f"{ch['extinf']}\n")
            f.write(f"{ch['url']}\n")

    print("[TeleMate] Successfully generated clean playlist!")

if __name__ == "__main__":
    main()
