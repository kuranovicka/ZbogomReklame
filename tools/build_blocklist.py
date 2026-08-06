#!/usr/bin/env python3
"""Replicates ZbogomReklame Windows azuriranje.c merge logic exactly, to keep
the Android list (and, if desired, Windows repo copy) in sync automatically."""
import re
import sys
import urllib.request

IZVOR_GLAVNI     = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
IZVOR_REGIONALNI = "https://raw.githubusercontent.com/DandelionSprout/adfilt/master/SerboCroatianList.txt"
IZVOR_MALWARE    = "https://malware-filter.gitlab.io/malware-filter/urlhaus-filter-hosts.txt"
IZVOR_OISD       = "https://raw.githubusercontent.com/sjhgvr/oisd/main/domainswild2_big.txt"
IZVOR_KRIPTO     = "https://raw.githubusercontent.com/hoshsadiq/adblock-nocoin-list/master/hosts.txt"
IZVOR_PREVARE    = "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/wildcard/fake.txt"
IZVOR_PHISHING   = "https://raw.githubusercontent.com/mitchellkrogza/Phishing.Database/master/phishing-domains-ACTIVE.txt"

def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "ZbogomReklame/1.0"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8", errors="replace")

def parse_stevenblack(text):
    out = []
    for line in text.splitlines():
        line = line.rstrip("\r")
        if not line.startswith("0.0.0.0"):
            continue
        rest = line[len("0.0.0.0"):]
        if not rest[:1] in (" ", "\t"):
            continue
        domain = rest.split()[0] if rest.split() else ""
        if not domain:
            continue
        if domain in ("0.0.0.0", "localhost", "local"):
            continue
        out.append(domain.lower())
    return out

def parse_adblock(text):
    out = []
    for line in text.splitlines():
        line = line.rstrip("\r")
        if not line.startswith("||"):
            continue
        rest = line[2:]
        m = re.match(r'^([^\^/*$]+)\^', rest)
        if not m:
            continue
        domain = m.group(1)
        if not (4 <= len(domain) <= 250):
            continue
        if not re.match(r'^[A-Za-z0-9.\-]+$', domain):
            continue
        out.append(domain.lower())
    return out

def parse_oisd(text):
    out = []
    for line in text.splitlines():
        line = line.rstrip("\r").strip()
        if not line or line.startswith("#"):
            continue
        if 2 <= len(line) <= 250:
            out.append(line.lower())
    return out

def parse_wildcard(text):
    out = []
    for line in text.splitlines():
        line = line.rstrip("\r").strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("*.") and len(line) > 2:
            line = line[2:]
        if 2 <= len(line) <= 250:
            out.append(line.lower())
    return out

def main():
    domains = []
    sources = [
        (IZVOR_GLAVNI, parse_stevenblack, True),
        (IZVOR_REGIONALNI, parse_adblock, False),
        (IZVOR_MALWARE, parse_stevenblack, False),
        (IZVOR_OISD, parse_oisd, False),
        (IZVOR_KRIPTO, parse_stevenblack, False),
        (IZVOR_PREVARE, parse_wildcard, False),
        (IZVOR_PHISHING, parse_oisd, False),
    ]
    for url, parser, critical in sources:
        try:
            text = fetch(url)
            parsed = parser(text)
            print(f"  {url}: {len(parsed)} domena", file=sys.stderr)
            domains.extend(parsed)
        except Exception as e:
            print(f"  {url}: GRESKA ({e})", file=sys.stderr)
            if critical:
                print("Glavni izvor nije uspeo, prekidam.", file=sys.stderr)
                sys.exit(1)

    if len(domains) < 1000:
        print("Premalo domena, necu upisati.", file=sys.stderr)
        sys.exit(1)

    with open("zbogomreklame_hosts.txt", "w") as f:
        f.write("\n".join(domains) + "\n")
    print(f"UKUPNO: {len(domains)} linija", file=sys.stderr)

if __name__ == "__main__":
    main()
