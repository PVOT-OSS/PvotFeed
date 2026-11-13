#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
# SPDX-License-Identifier: Apache-2.0
"""Check that every starter-pack feed is live, parses, is fresh and has pictures.

Usage: tools/check-packs.py [app/src/main/assets/packs/starter.json]
Exits non-zero when a feed fails; stale or picture-less feeds are only warned about.
"""
import concurrent.futures
import datetime
import email.utils
import json
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET

UA = "PvotFeed/0.1 (+https://github.com/PVOT-OSS/PvotFeed)"
STALE_DAYS = 7


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    r = urllib.request.urlopen(req, timeout=25)
    try:
        return r.read()
    finally:
        r.close()


def local(tag):
    return tag.rsplit("}", 1)[-1]


def parse_date(text):
    text = (text or "").strip()
    if not text:
        return None
    try:
        return email.utils.parsedate_to_datetime(text)
    except (TypeError, ValueError):
        pass
    try:
        return datetime.datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        return None


def has_picture(item):
    for child in item.iter():
        name = local(child.tag)
        url = child.get("url", "")
        if name == "thumbnail" and url:
            return True
        if name in ("content", "enclosure") and url:
            kind = child.get("medium", "") + child.get("type", "")
            if "image" in kind or url.split("?")[0].lower().endswith((".jpg", ".jpeg", ".png", ".webp")):
                return True
        if "<img" in (child.text or ""):
            return True
    return False


def check(source):
    body = fetch(source["feed"])
    root = ET.fromstring(body)
    items = [e for e in root.iter() if local(e.tag) in ("item", "entry")]
    dates, images = [], 0
    for item in items:
        for child in item.iter():
            name = local(child.tag)
            if name in ("pubDate", "date", "published", "updated"):
                d = parse_date(child.text)
                if d:
                    if d.tzinfo is None:
                        d = d.replace(tzinfo=datetime.timezone.utc)
                    dates.append(d)
                    break
        if has_picture(item):
            images += 1
    newest = max(dates) if dates else None
    return len(items), newest, images


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/assets/packs/starter.json"
    sources = json.load(open(path))["sources"]
    now = datetime.datetime.now(datetime.timezone.utc)
    failed = 0

    def run(source):
        if "reddit.com" in source["feed"]:
            time.sleep(2 * sum(1 for s in sources[: sources.index(source)] if "reddit.com" in s["feed"]))
        try:
            return source, check(source), None
        except Exception as e:  # noqa: BLE001 - report every kind of failure
            return source, None, e

    with concurrent.futures.ThreadPoolExecutor(8) as pool:
        for source, result, error in pool.map(run, sources):
            label = f'{source["region"]:5} {source["topic"]:13} {source["title"]}'
            if error:
                failed += 1
                print(f"FAIL  {label}: {error}")
                continue
            count, newest, images = result
            age = (now - newest).total_seconds() / 3600 if newest else None
            notes = []
            if count == 0:
                notes.append("no items")
                failed += 1
            if age is None:
                notes.append("no dates")
            elif age > STALE_DAYS * 24:
                notes.append(f"stale {age / 24:.0f} d")
            if count and images / count < 0.5:
                notes.append(f"pictures {images}/{count}")
            mark = "FAIL" if count == 0 else ("warn" if notes else "ok")
            age_text = f"{age:5.1f} h" if age is not None else "    ? h"
            print(f"{mark:5} {label}: {count:3} items, newest {age_text} {'; '.join(notes)}")
    print(f"{len(sources)} feeds, {failed} failed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
