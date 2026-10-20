#!/usr/bin/env python3
"""Builds the Neo Geo romset table the app uses to check arcade ZIPs before FBNeo opens them
(ArcadeCheck.java): which files (by CRC) each FBNeo set needs, its parent and its BIOS. With it
the app can rename a ZIP that has the right files but the wrong name ("King of Fighters 2002.zip"
-> kof2002.zip) and say exactly what is missing (an old neogeo.zip without sp-s3.sp1...).

Usage: python3 scripts/make_fbneo_db.py <FBNeo>/src/burn/drv/neogeo/d_neogeo.cpp \
           overlay/assets/arcade/fbneo_neogeo.txt

Output, one set per line (tab separated):
  set  parent  board  title  roms
where roms is "name:crc:flag,..." and flag is r (required) or o (optional). The BIOS files a
game also needs are the required ones of its board's line (neogeo).
"""
import re
import sys

BRF_OPT = "BRF_OPT"
BRF_NODUMP = "BRF_NODUMP"


def preprocess(text):
    out = []
    stack = []   # (keep_this_branch, parent_keep)
    keep = True
    macros = {}
    lines = text.split("\n")
    i = 0
    while i < len(lines):
        line = lines[i]
        s = line.strip()
        if s.startswith("#if"):
            parent = keep
            if re.match(r"#if\s+0\b", s):
                cond = False
            elif re.match(r"#if\s+!\s*defined\s*\(?\s*ROM_VERIFY", s):
                cond = True
            elif re.match(r"#if\s+defined\s*\(?\s*ROM_VERIFY", s) or s.startswith("#ifdef ROM_VERIFY"):
                cond = False
            else:
                cond = True
            stack.append((cond, parent))
            keep = parent and cond
            i += 1
            continue
        if s.startswith("#else"):
            cond, parent = stack[-1]
            stack[-1] = (not cond, parent)
            keep = parent and not cond
            i += 1
            continue
        if s.startswith("#endif"):
            cond, parent = stack.pop()
            keep = parent
            i += 1
            continue
        if s.startswith("#define") and keep:
            body = line
            while body.rstrip().endswith("\\") and i + 1 < len(lines):
                i += 1
                body = body.rstrip()[:-1] + "\n" + lines[i]
            m = re.match(r"\s*#define\s+(\w+)\s*(.*)", body, re.S)
            if m and '{ "' in m.group(2):
                macros[m.group(1)] = m.group(2)
            i += 1
            continue
        if keep:
            out.append(line)
        i += 1
    text = "\n".join(out)
    # Expand the ROM-list macros (a few sets share components).
    for _ in range(3):
        for name, body in macros.items():
            text = re.sub(r"\b%s\b" % name, lambda _m: body, text)
    return text


ENTRY = re.compile(r'\{\s*"([^"]*)"\s*,\s*(0x[0-9a-fA-F]+|\d+)\s*,\s*(0x[0-9a-fA-F]+|\d+)\s*,\s*([^}]*)\}')


def main():
    src, dst = sys.argv[1], sys.argv[2]
    text = preprocess(open(src, encoding="latin-1").read())

    descs = {}
    for m in re.finditer(r"static\s+struct\s+BurnRomInfo\s+(\w+)RomDesc\s*\[\]\s*=\s*\{(.*?)\n\};", text, re.S):
        roms = []
        for e in ENTRY.finditer(m.group(2)):
            name, length, crc, flags = e.group(1), int(e.group(2), 0), int(e.group(3), 0), e.group(4)
            if not name or length == 0 or crc == 0 or BRF_NODUMP in flags or flags.strip() == "0":
                continue
            roms.append((name, crc, BRF_OPT in flags))
        descs[m.group(1)] = roms

    picks = {}   # RomInfo prefix -> (desc, bios desc or None)
    for m in re.finditer(r"STDROMPICKEXT\(\s*(\w+)\s*,\s*(\w+)\s*,\s*(\w+)\s*\)", text):
        picks[m.group(1)] = (m.group(2), m.group(3))
    for m in re.finditer(r"STD_ROM_PICK\(\s*(\w+)\s*\)", text):
        picks.setdefault(m.group(1), (m.group(1), None))

    rows = []
    seen = set()
    for m in re.finditer(r"struct\s+BurnDriver\s+\w+\s*=\s*\{(.*?)\};", text, re.S):
        body = m.group(1)
        toks = re.findall(r'"((?:[^"\\]|\\.)*)"|\b(NULL)\b', body)
        vals = [t[0] if t[0] or not t[1] else None for t in toks]
        if len(vals) < 6:
            continue
        short, parent, board = vals[0], vals[1], vals[2]
        title = (vals[5] or "").split("\\0")[0]
        ri = re.search(r"\b(\w+)RomInfo\b", body)
        if not short or not ri or ri.group(1) not in picks:
            continue
        desc, bios = picks[ri.group(1)]
        roms = ["%s:%08x:%s" % (n.replace(",", "_").replace(":", "_"), c, "o" if o else "r") for n, c, o in descs.get(desc, [])]
        if short in seen:
            continue
        seen.add(short)
        rows.append("\t".join([short, parent or "", board or "", title.replace("\t", " "), ",".join(roms)]))

    with open(dst, "w", encoding="utf-8") as f:
        f.write("# FBNeo Neo Geo romsets (made by scripts/make_fbneo_db.py from d_neogeo.cpp)\n")
        f.write("\n".join(rows) + "\n")
    print("%d sets" % len(rows))


if __name__ == "__main__":
    main()
