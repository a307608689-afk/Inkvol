# Generate local-only regression fixtures (NOT committed).
# Usage: D:\Python311\python.exe scripts\gen_regress_fixtures.py <out_dir>
import os
import sys
import zipfile
import shutil
from PIL import Image, ImageDraw

OUT = sys.argv[1] if len(sys.argv) > 1 else r"C:\Users\30760\AppData\Local\Temp\inkvol_regress"
ROOT = r"d:\Desktop\Android-APK\Local comics"
os.makedirs(OUT, exist_ok=True)

# --- comic pages + zip ---
pages = []
for i in range(1, 5):
    img = Image.new("RGB", (800, 1200), (250 - i * 8, 246 - i * 6, 238 - i * 4))
    d = ImageDraw.Draw(img)
    d.rectangle((40, 40, 760, 1160), outline=(60, 60, 60), width=6)
    d.text((320, 560), "PAGE %d" % i, fill=(30, 30, 30))
    p = os.path.join(OUT, "p%d.jpg" % i)
    img.save(p, quality=90)
    pages.append(p)

with zipfile.ZipFile(os.path.join(OUT, "comic.zip"), "w") as z:
    for i, p in enumerate(pages, 1):
        z.write(p, "page%02d.jpg" % i)

# --- txt ---
with open(os.path.join(OUT, "book.txt"), "w", encoding="utf-8") as f:
    f.write("第一章 开始\n")
    for i in range(60):
        f.write("这是第一章的第 %d 行正文，用于验证分页与滚动。\n" % i)
    f.write("\n第二章 继续\n")
    for i in range(60):
        f.write("这是第二章的第 %d 行正文，用于验证跨章跳转。\n" % i)

# --- epub (minimal, valid enough for the parser) ---
epub = os.path.join(OUT, "book.epub")
with zipfile.ZipFile(epub, "w") as z:
    z.writestr(zipfile.ZipInfo("mimetype"), "application/epub+zip", compress_type=zipfile.ZIP_STORED)
    z.writestr("META-INF/container.xml", """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>""")
    z.writestr("OEBPS/content.opf", """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bid">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>Regression EPUB</dc:title><dc:identifier id="bid">regress-1</dc:identifier>
    <dc:language>zh</dc:language>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="c1" href="ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="ch2.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine toc="ncx"><itemref idref="c1"/><itemref idref="c2"/></spine>
</package>""")
    z.writestr("OEBPS/toc.ncx", """<?xml version="1.0" encoding="utf-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head><meta name="dtb:uid" content="regress-1"/></head>
<docTitle><text>Regression EPUB</text></docTitle>
<navMap>
<navPoint id="n1" playOrder="1"><navLabel><text>第一章</text></navLabel><content src="ch1.xhtml"/></navPoint>
<navPoint id="n2" playOrder="2"><navLabel><text>第二章</text></navLabel><content src="ch2.xhtml"/></navPoint>
</navMap></ncx>""")
    for i in (1, 2):
        body = "".join("<p>第 %d 章第 %d 段，用于验证 EPUB 渲染。</p>" % (i, j) for j in range(1, 21))
        z.writestr("OEBPS/ch%d.xhtml" % i, """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>第%d章</title></head>
<body><h1>第%d章</h1>%s</body></html>""" % (i, i, body))

# --- minimal pdf (ASCII text) ---
pdf = os.path.join(OUT, "book.pdf")
content = b"BT /F1 28 Tf 72 720 Td (Inkvol PDF Page 1) Tj ET"
objs = [
    b"<< /Type /Catalog /Pages 2 0 R >>",
    b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
    b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
    b"<< /Length %d >>\nstream\n" % len(content) + content + b"\nendstream",
    b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
]
buf = bytearray(b"%PDF-1.4\n")
offsets = []
for i, body in enumerate(objs, 1):
    offsets.append(len(buf))
    buf += ("%d 0 obj\n" % i).encode() + body + b"\nendobj\n"
xref = len(buf)
buf += ("xref\n0 %d\n0000000000 65535 f \n" % (len(objs) + 1)).encode()
for off in offsets:
    buf += ("%010d 00000 n \n" % off).encode()
buf += ("trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objs) + 1, xref)).encode()
with open(pdf, "wb") as f:
    f.write(buf)

# --- mobi samples from repo ---
for name in ("sample_cn.mobi", "sample_long.mobi"):
    src = os.path.join(ROOT, "test_samples", name)
    if os.path.exists(src):
        shutil.copy2(src, os.path.join(OUT, name))

print("fixtures in", OUT)
for f in sorted(os.listdir(OUT)):
    print(" ", f, os.path.getsize(os.path.join(OUT, f)))
