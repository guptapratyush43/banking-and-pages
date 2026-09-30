"""
Fetches a logo for every bank in banks.py and writes:
  app/src/main/assets/banks.json      the picker list, with each logo's source and fingerprint
  app/src/main/assets/logos/<id>.webp a trimmed, square 256px logo

For each bank it gathers candidates from the bank's own site (web manifest icons,
apple-touch-icon, <link rel=icon>), Google's favicon service and the Wikidata logo,
then keeps the sharpest square one. The recorded source URL and SHA-256 let the app
spot later, on its daily check, that the bank has changed its logo.

Run:  python tools/fetch_logos.py        (from the project folder)
"""
import concurrent.futures as cf
import hashlib
import html.parser
import io
import json
import os
import ssl
import subprocess
import sys
import urllib.parse
import urllib.request

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from banks import BANKS

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
LOGOS = os.path.join(ASSETS, "logos")
REPORT = os.path.join(ROOT, "tools", "logo_report.json")
UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0 Mobile Safari/537.36"
CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE  # several bank sites serve incomplete chains; these are public images only


def get(url, timeout=20, limit=4_000_000):
    """curl rather than urllib: several bank sites reject Python's TLS handshake outright."""
    out = subprocess.run(["curl", "-sL", "-k", "--compressed", "-m", str(timeout), "-A", UA, "-H", "Accept-Language: en-IN,en",
                          "-w", "\n%{http_code} %{url_effective}", url], capture_output=True)
    body, _, tail = out.stdout.rpartition(b"\n")
    code, _, final = tail.decode().partition(" ")
    if out.returncode != 0 or not code.startswith("2"):
        raise IOError(f"{code} {url}")
    return final, body[:limit]


class IconParser(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.icons, self.manifest = [], None

    def handle_starttag(self, tag, attrs):
        if tag != "link":
            return
        a = {k.lower(): (v or "") for k, v in attrs}
        rel = a.get("rel", "").lower()
        href = a.get("href")
        if not href:
            return
        if "manifest" in rel:
            self.manifest = href
        elif "icon" in rel and "mask-icon" not in rel:
            self.icons.append(href)


def decode(data):
    try:
        im = Image.open(io.BytesIO(data))
        if getattr(im, "format", "") == "ICO":
            sizes = im.info.get("sizes") or im.ico.sizes()
            if sizes:
                im.size = max(sizes)
        im.load()
        return im.convert("RGBA")
    except Exception:
        return None


def site_candidates(domain):
    if not domain:
        return []
    urls = []
    for base in (f"https://www.{domain}/", f"https://{domain}/"):
        try:
            final, body = get(base, limit=1_500_000)
        except Exception:
            continue
        p = IconParser()
        try:
            p.feed(body.decode("utf-8", "ignore"))
        except Exception:
            pass
        urls += [urllib.parse.urljoin(final, h) for h in p.icons]
        if p.manifest:
            try:
                murl = urllib.parse.urljoin(final, p.manifest)
                _, m = get(murl)
                for ic in json.loads(m.decode("utf-8", "ignore")).get("icons", []):
                    if ic.get("src"):
                        urls.append(urllib.parse.urljoin(murl, ic["src"]))
            except Exception:
                pass
        urls.append(urllib.parse.urljoin(final, "/apple-touch-icon.png"))
        break
    urls.append(f"https://www.google.com/s2/favicons?domain={domain}&sz=256")
    urls.append(f"https://icons.duckduckgo.com/ip3/{domain}.ico")
    seen, out = set(), []
    for u in urls:
        if u not in seen and not u.lower().endswith(".svg"):
            seen.add(u)
            out.append(u)
    return out


def wikidata_logo(title):
    if not title:
        return None
    try:
        q = urllib.parse.urlencode({"action": "query", "titles": title, "prop": "pageprops", "redirects": 1, "format": "json"})
        _, b = get("https://en.wikipedia.org/w/api.php?" + q)
        pages = json.loads(b)["query"]["pages"]
        qid = next(iter(pages.values())).get("pageprops", {}).get("wikibase_item")
        if not qid:
            return None
        _, b = get(f"https://www.wikidata.org/w/api.php?action=wbgetclaims&entity={qid}&property=P154&format=json")
        claims = json.loads(b).get("claims", {}).get("P154", [])
        if not claims:
            return None
        name = claims[-1]["mainsnak"]["datavalue"]["value"]
        return qid, name
    except Exception:
        return None


def commons_url(name, width=512):
    return "https://commons.wikimedia.org/wiki/Special:FilePath/" + urllib.parse.quote(name.replace(" ", "_")) + f"?width={width}"


def normalize(im):
    """Trim a white/transparent margin, centre on a transparent square, 256px."""
    w, h = im.size
    px = im.load()
    corners = [px[0, 0], px[w - 1, 0], px[0, h - 1], px[w - 1, h - 1]]

    def blank(c):
        return c[3] < 16 or (c[0] > 240 and c[1] > 240 and c[2] > 240)

    if all(blank(c) for c in corners):
        mask = Image.new("L", im.size, 0)
        mp = mask.load()
        for y in range(h):
            for x in range(w):
                if not blank(px[x, y]):
                    mp[x, y] = 255
        box = mask.getbbox()
        if box:
            im = im.crop(box)
    side = max(im.size)
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.paste(im, ((side - im.width) // 2, (side - im.height) // 2))
    return sq.resize((256, 256), Image.LANCZOS)


# github.com/praveenpuglia/indian-banks: hand-cleaned vector symbols, keyed by IFSC prefix.
REPO = "https://raw.githubusercontent.com/praveenpuglia/indian-banks/main/assets/logos/{}/symbol.svg"
REPO_SLUGS = {
    "airtel": "airp", "au": "aubl", "bob": "barb", "bandhan": "bdbl", "boi": "bkid", "central": "cbin", "cub": "ciub",
    "canara": "cnrb", "csb": "csbk", "dcb": "dcbl", "dhanlaxmi": "dlxb", "esaf": "esmf", "federal": "fdrl", "fino": "fino",
    "hdfc": "hdfc", "idbi": "ibkl", "icici": "icic", "idfcfirst": "idfb", "indian": "idib", "indusind": "indb", "iob": "ioba",
    "jkbank": "jaka", "jio": "jiop", "karnataka": "karb", "kotak": "kkbk", "kvb": "kvbl", "bom": "mahb", "nainital": "ntbl",
    "psb": "psib", "pnb": "punb", "paytm": "pytm", "rbl": "ratn", "sbi": "sbin", "sc": "scbl", "southindian": "sibl",
    "tmb": "tmbl", "union": "ubin", "uco": "ucba", "ujjivan": "ujvn", "axis": "utib", "yes": "yesb",
}
# Rendered from the SVGs with resvg (see README); a few needed their broken clip paths stripped first.
RENDERED = os.environ.get("RENDERED_DIR", "")


def process(bank):
    bid, name, cat, domain, wiki = bank
    cands = []
    slug = REPO_SLUGS.get(bid)
    png = slug and RENDERED and os.path.join(RENDERED, slug + ".r.png")
    if png and os.path.exists(png):
        url = REPO.format(slug)
        _, data = get(url)
        im = Image.open(png).convert("RGBA")
        cands.append({"kind": "repo", "url": url, "w": 999, "h": 999, "sha": hashlib.sha256(data).hexdigest(), "im": im})
    for url in site_candidates(domain):
        try:
            _, data = get(url)
        except Exception:
            continue
        im = decode(data)
        if im is None:
            continue
        cands.append({"kind": "site", "url": url, "w": im.width, "h": im.height,
                      "sha": hashlib.sha256(data).hexdigest(), "im": im})
    wd = wikidata_logo(wiki)
    if wd:
        qid, fname = wd
        try:
            _, data = get(commons_url(fname))
            im = decode(data)
            if im:
                cands.append({"kind": "wikidata", "qid": qid, "file": fname, "url": commons_url(fname),
                              "w": im.width, "h": im.height, "sha": hashlib.sha256(data).hexdigest(), "im": im})
        except Exception:
            pass

    def side(c):
        return min(c["w"], c["h"])

    def squareish(c):
        return max(c["w"], c["h"]) / max(1, side(c)) <= 1.3

    # Google's service returns a generic globe (16px) for unknown sites; ignore anything that tiny.
    usable = [c for c in cands if side(c) >= 24]
    good_icons = sorted([c for c in usable if c["kind"] == "site" and squareish(c) and side(c) >= 96], key=side, reverse=True)
    wiki_logo = next((c for c in usable if c["kind"] == "wikidata" and max(c["w"], c["h"]) / max(1, side(c)) <= 1.6), None)
    small_icons = sorted([c for c in usable if c["kind"] == "site" and squareish(c)], key=side, reverse=True)
    repo = [c for c in cands if c["kind"] == "repo"]
    pick = (repo[:1] or good_icons[:1] or ([wiki_logo] if wiki_logo else []) or small_icons[:1] or [None])[0]

    entry = {"id": bid, "name": name, "category": cat, "domain": domain}
    if wd:
        entry["wikidata"] = wd[0]
    if pick:
        normalize(pick["im"]).save(os.path.join(LOGOS, f"{bid}.webp"), "WEBP", quality=92, method=6)
        entry["logo"] = True
        entry["source"] = {"kind": pick["kind"], "url": pick["url"], "sha256": pick["sha"]}
        if pick["kind"] == "wikidata":
            entry["source"]["file"] = pick["file"]
    else:
        entry["logo"] = False
    report = {"id": bid, "pick": pick and {k: v for k, v in pick.items() if k != "im"},
              "candidates": [{k: v for k, v in c.items() if k != "im"} for c in cands]}
    return entry, report


def main():
    os.makedirs(LOGOS, exist_ok=True)
    only = set(sys.argv[1:])
    todo = [b for b in BANKS if not only or b[0] in only]
    results = {}
    with cf.ThreadPoolExecutor(6) as ex:
        futs = {ex.submit(process, b): b[0] for b in todo}
        for f in cf.as_completed(futs):
            bid = futs[f]
            try:
                entry, rep = f.result()
            except Exception as e:
                b = next(x for x in BANKS if x[0] == bid)
                entry, rep = {"id": bid, "name": b[1], "category": b[2], "domain": b[3], "logo": False}, {"id": bid, "error": str(e)}
            results[bid] = (entry, rep)
            p = rep.get("pick")
            print(f"{bid:14} {'-' if not p else p['kind'] + ' ' + str(p['w']) + 'x' + str(p['h'])}", flush=True)

    # Keep earlier results for banks not re-run this time.
    old = {}
    bj = os.path.join(ASSETS, "banks.json")
    if only and os.path.exists(bj):
        old = {e["id"]: e for e in json.load(open(bj, encoding="utf-8"))}
    ordered = [results[b[0]][0] if b[0] in results else old.get(b[0]) for b in BANKS]
    json.dump([e for e in ordered if e], open(bj, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    json.dump([results[k][1] for k in results], open(REPORT, "w", encoding="utf-8"), indent=1)
    print("with logo:", sum(1 for e in ordered if e and e.get("logo")), "/", len(BANKS))


if __name__ == "__main__":
    main()
