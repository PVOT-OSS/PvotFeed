<!--
SPDX-FileCopyrightText: 2026 Saalim Quadri <danascape@gmail.com>
SPDX-License-Identifier: Apache-2.0
-->

# Privacy Policy — Pvot Feed

_Effective 13 November 2026_

Pvot Feed (package `org.prauga.feed`) is a news and mailing list reader published by Saalim Quadri
for PVOT-OSS. This policy explains what the app does with your data.

**In short: the app has no accounts, no ads, no analytics and no tracking, and it never sends your
data to us. It fetches news straight from the outlets and sites you follow. Everything you read,
hide or mute stays on your phone.**

## What the app sends, and to whom

The app has no server of its own. It talks only to the websites of the sources you follow, directly
from your phone, the same way a browser would when you visit them. Each request reveals your IP
address to that website. Every request identifies the app as `PvotFeed/<version>` with a link to its
source code, but carries no account, device ID, advertising ID or cookie.

| When | What the app fetches | From |
|---|---|---|
| Refreshing the feed, while you use the app and about every 3 hours in the background | Each source's feed (RSS, Atom or JSON Feed) | The outlet or site that publishes it |
| Showing a story without a picture in its feed | The article's web page, to read the picture the publisher chose for sharing | That outlet |
| Showing a source for the first time | The site's home page, to find its icon | That site |
| Showing pictures | The pictures in feeds and articles | The outlet, or the image server it links to |
| Opening an article | The article's web page, to show it in the reader | That outlet |
| Adding a source from a link you paste or share | That page, and the feed addresses it points to | That site |
| Opening a mailing list thread | The whole thread, as a feed | The list archive (for example lore.kernel.org) |

Which websites these are depends on you: the starter sources you pick at setup (a list of news
outlets bundled with the app) and any sources you add. Their own privacy policies apply to what they
receive. The app does not run any of their scripts, ads or trackers; it reads only the feed or the
page's text and pictures.

**Open original**, **Share** and links in **About** hand the link to another app you choose (your
browser or a sharing target); that app's privacy terms apply from there.

## What the app keeps on your device

- **Your sources and settings**: the feeds you follow, which ones you hid, your muted words, text
  size and whether background refresh is on.
- **Articles and messages** from the last 7 days (and whole mailing list threads you opened), with
  which ones you have seen or opened, so the feed can tell you what is new and work offline.
- **Article text** of articles you opened, so they read offline.
- **Pictures** in a cache of up to 150 MB, which Android may clear at any time.

All of this stays on your phone and is deleted when you clear the app's storage or uninstall it.
Older articles are deleted automatically.

If you have turned on Android's backup, Android may include this data in your device backup and in
transfers to a new phone, under your backup provider's terms. The app itself never uploads it.

## Permissions

- `INTERNET`: to fetch feeds, articles and pictures, as described above.
- `ACCESS_NETWORK_STATE`, `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`: added by
  Android's WorkManager library, which refreshes the feed in the background only when the phone is
  connected. They don't give the app access to any of your data.

The app asks for no other permissions: no location, contacts, camera, microphone or storage.

## Children

The app is not directed at children and collects no data from anyone, children included. News
outlets you follow may publish content that is not suitable for children.

## Changes

If the app starts handling data differently, this policy will be updated before that version is
released, with a new effective date at the top.

## Contact

Questions about this policy: danascape@gmail.com, or open an issue at
https://github.com/PVOT-OSS/PvotFeed/issues.
