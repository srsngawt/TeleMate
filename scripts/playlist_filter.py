#!/usr/bin/env python3
"""
TeleMate Playlist Filter & Deduplicator
- Fetches raw India IPTV playlist from iptv-org
- Extracts Channel Base Name, Language, and Quality
- Tests stream URLs asynchronously to eliminate dead/broken links
- Preserves EVERY working quality (1080p, 720p, 576p, etc.) and language per channel
- Unpacks adaptive HLS master playlists into direct quality streams where applicable
- Discards dead/erroring streams (e.g. broken 540p links)
- Outputs clean_india.m3u for TeleMate TV with seamless multi-stream quality/language switching
"""

import re
import sys
import urllib.request
import urllib.error
from urllib.parse import urljoin
from concurrent.futures import ThreadPoolExecutor, as_completed

SOURCE_URL = "https://iptv-org.github.io/iptv/countries/in.m3u"
OUTPUT_FILE = "clean_india.m3u"
TIMEOUT_SECONDS = 4.0
MAX_WORKERS = 40

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "*/*"
}

LANGUAGES = [
    'Hindi', 'English', 'Tamil', 'Telugu', 'Kannada', 'Malayalam', 
    'Bengali', 'Marathi', 'Punjabi', 'Gujarati', 'Urdu', 'Odia', 
    'Bhojpuri', 'Assamese', 'Sanskrit'
]
LANG_REGEX = re.compile(r'\b(' + '|'.join(LANGUAGES) + r')\b', re.IGNORECASE)
QUAL_REGEX = re.compile(r'\(?\[?\b(2160p|4k|1080p|1080i|fhd|720p|hd|576p|480p|sd|360p)\b\]?\)?', re.IGNORECASE)

def normalize_quality(raw):
    raw_u = raw.upper()
    if '2160' in raw_u or '4K' in raw_u:
        return '4K'
    if '1080' in raw_u or 'FHD' in raw_u:
        return '1080p'
    if '720' in raw_u:
        return '720p'
    if 'HD' in raw_u:
        return '1080p'
    if '576' in raw_u:
        return '576p'
    if '480' in raw_u or 'SD' in raw_u:
        return '480p'
    if '360' in raw_u:
        return '360p'
    return '576p'

def get_base_name(title):
    # Remove resolution tags
    t = re.sub(r'[\(\[]\s*\d{3,4}p\s*[\)\]]', '', title, flags=re.IGNORECASE)
    t = re.sub(r'\b(4K|UHD|1080p|1080i|720p|576p|480p|360p|FHD|HD|SD)\b', '', t, flags=re.IGNORECASE)
    t = re.sub(r'[\(\[]\s*(Geo-blocked|Not 24/7|Backup|Test)\s*[\)\]]', '', t, flags=re.IGNORECASE)
    # Remove language tags from base title
    t = LANG_REGEX.sub('', t)
    # Remove extra brackets and punctuation
    t = re.sub(r'[\(\[\{].*?[\)\]\}]', '', t)
    t = re.sub(r'\s*[-|_|/|:]+\s*', ' ', t)
    return re.sub(r'\s+', ' ', t).strip()

def check_stream_alive(url):
    try:
        req = urllib.request.Request(url, headers=HEADERS, method="HEAD")
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            code = resp.getcode()
            if code in range(200, 400):
                return True
    except Exception:
        pass
    # Some CDN servers reject HEAD; try a lightweight GET with byte range
    try:
        req2 = urllib.request.Request(url, headers={**HEADERS, "Range": "bytes=0-100"}, method="GET")
        with urllib.request.urlopen(req2, timeout=TIMEOUT_SECONDS) as resp2:
            return resp2.getcode() in range(200, 400)
    except Exception:
        return False

def unpack_master_m3u8(master_url):
    """
    If a stream is an adaptive HLS master playlist, extract distinct working resolution streams
    (e.g. 1080p and 720p) so users can switch between them cleanly without track overriding.
    """
    try:
        req = urllib.request.Request(master_url, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
        if '#EXT-X-STREAM-INF' not in content:
            return None
        
        streams = {}
        lines = content.splitlines()
        for i, line in enumerate(lines):
            if line.startswith('#EXT-X-STREAM-INF:'):
                m_res = re.search(r'RESOLUTION=(\d+)x(\d+)', line)
                if m_res and i + 1 < len(lines):
                    h = int(m_res.group(2))
                    sub_url = lines[i+1].strip()
                    if not sub_url.startswith('#') and sub_url:
                        full_url = urljoin(master_url, sub_url)
                        if h >= 1080: q = '1080p'
                        elif h >= 720: q = '720p'
                        elif h >= 576: q = '576p'
                        elif h >= 480: q = '480p'
                        else: q = f'{h}p'
                        if q not in streams:
                            streams[q] = full_url
        # Only return if we found quality variants (e.g. 1080p, 720p)
        if len(streams) > 1:
            return streams
        return None
    except Exception:
        return None

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
            parts = current_extinf.split(",", 1)
            raw_title = parts[1].strip() if len(parts) > 1 else "Unknown Channel"
            base_name = get_base_name(raw_title)
            
            # Detect language
            lm = LANG_REGEX.search(raw_title)
            lang = lm.group(1).capitalize() if lm else ""
            
            # Detect quality
            qm = QUAL_REGEX.search(raw_title) or QUAL_REGEX.search(url)
            qual = normalize_quality(qm.group(1)) if qm else "576p"
            
            channels.append({
                "extinf": current_extinf,
                "raw_title": raw_title,
                "base_name": base_name,
                "language": lang,
                "quality": qual,
                "url": url
            })
            current_extinf = None
            
    return channels

def main():
    print(f"[TeleMate] Fetching playlist from {SOURCE_URL}...")
    try:
        req = urllib.request.Request(SOURCE_URL, headers=HEADERS)
        with urllib.request.urlopen(req, timeout=12) as resp:
            content = resp.read().decode('utf-8', errors='ignore')
    except Exception as e:
        print(f"[Error] Failed to fetch playlist: {e}")
        sys.exit(1)

    raw_channels = parse_m3u(content)
    print(f"[TeleMate] Found {len(raw_channels)} total channel entries.")

    # Group by (base_name, language, quality)
    # This ensures EVERY distinct quality and language gets preserved!
    grouped = {}
    for ch in raw_channels:
        key = (ch["base_name"].lower(), ch["language"].lower(), ch["quality"].lower())
        grouped.setdefault(key, []).append(ch)

    print(f"[TeleMate] Grouped into {len(grouped)} distinct (Channel, Language, Quality) variants.")
    print(f"[TeleMate] Testing stream health concurrently...")

    verified_channels = []

    def verify_variant(key, items):
        # Test URLs for this (channel, language, quality) variant
        for ch in items:
            url = ch["url"]
            if check_stream_alive(url):
                # Check if it's an adaptive master playlist that can be unpacked into distinct qualities
                master_subs = unpack_master_m3u8(url)
                if master_subs and len(master_subs) > 1:
                    unpacked_list = []
                    # Keep broadcast qualities: 1080p, 720p, etc.
                    for sub_q in ['1080p', '720p', '576p', '480p']:
                        if sub_q in master_subs and check_stream_alive(master_subs[sub_q]):
                            clean_extinf = format_extinf(ch, sub_q)
                            unpacked_list.append({
                                "extinf": clean_extinf,
                                "url": master_subs[sub_q],
                                "base_name": ch["base_name"],
                                "language": ch["language"],
                                "quality": sub_q
                            })
                    if unpacked_list:
                        return unpacked_list

                # Single direct working stream
                clean_extinf = format_extinf(ch, ch["quality"])
                return [{
                    "extinf": clean_extinf,
                    "url": url,
                    "base_name": ch["base_name"],
                    "language": ch["language"],
                    "quality": ch["quality"]
                }]
        # All URLs for this variant failed (e.g. dead 540p stream) -> discard!
        return None

    def format_extinf(ch, quality):
        extinf = ch["extinf"]
        prefix = extinf.split(",", 1)[0] if "," in extinf else extinf
        
        # Ensure tvg-id is distinct per stream quality/variant so Room DB and EPG do not collide
        if 'tvg-id="' in prefix:
            def replace_tvg_id(match):
                tid = match.group(1)
                base = tid.split('@')[0]
                lang_suffix = f"-{ch['language']}" if ch.get('language') else ""
                return f'tvg-id="{base}{lang_suffix}@{quality}"'
            prefix = re.sub(r'tvg-id="([^"]+)"', replace_tvg_id, prefix)

        # Build clean title with language and quality tag so TeleMate parser detects both
        parts = [ch["base_name"]]
        if ch["language"]:
            parts.append(ch["language"])
        parts.append(f"({quality})")
        
        clean_title = " ".join(parts)
        return f"{prefix},{clean_title}"

    with ThreadPoolExecutor(max_workers=MAX_WORKERS) as executor:
        futures = {executor.submit(verify_variant, k, v): k for k, v in grouped.items()}
        completed = 0
        total = len(futures)
        for fut in as_completed(futures):
            res = fut.result()
            if res:
                verified_channels.extend(res)
            completed += 1
            if completed % 50 == 0 or completed == total:
                print(f"[TeleMate] Checked {completed}/{total} variants... ({len(verified_channels)} streams alive)")

    # Deduplicate in case unpacking added overlapping streams
    unique_verified = {}
    for ch in verified_channels:
        u_key = (ch["base_name"].lower(), ch["language"].lower(), ch["quality"].lower(), ch["url"])
        unique_verified[u_key] = ch

    final_channels = list(unique_verified.values())

    # Sort alphabetically by base_name, then language, then quality descending
    qual_order = {'4k': 0, '1080p': 1, '720p': 2, '576p': 3, '480p': 4, '360p': 5}
    final_channels.sort(key=lambda x: (
        x["base_name"].lower(),
        x["language"].lower(),
        qual_order.get(x["quality"].lower(), 99)
    ))

    print(f"[TeleMate] Writing {len(final_channels)} verified streams to {OUTPUT_FILE}...")
    with open(OUTPUT_FILE, "w", encoding="utf-8") as f:
        f.write("#EXTM3U\n")
        for ch in final_channels:
            f.write(f"{ch['extinf']}\n")
            f.write(f"{ch['url']}\n")

    # Group summary for user verification
    summary = {}
    for ch in final_channels:
        summary.setdefault(ch["base_name"], []).append(f"{ch['language']} {ch['quality']}".strip())

    multi_streams = {k: v for k, v in summary.items() if len(v) > 1}
    print(f"[TeleMate] Done! {len(final_channels)} total streams across {len(summary)} unique channels.")
    print(f"[TeleMate] Channels with multiple qualities/languages: {len(multi_streams)}")

if __name__ == "__main__":
    main()
