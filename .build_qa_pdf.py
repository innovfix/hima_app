"""
One-shot converter: QA_MODULE_BREAKDOWN.md -> QA_MODULE_BREAKDOWN.pdf
Uses Microsoft Edge in headless mode to print a styled HTML to PDF.
This script is idempotent and safe to re-run.
"""
import subprocess
import sys
from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parent
MD = ROOT / "QA_MODULE_BREAKDOWN.md"
HTML = ROOT / "QA_MODULE_BREAKDOWN.html"
PDF = ROOT / "QA_MODULE_BREAKDOWN.pdf"

EDGE_CANDIDATES = [
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
]

CSS = """
@page {
    size: A4;
    margin: 18mm 16mm 18mm 16mm;
}
* { box-sizing: border-box; }
html, body {
    font-family: "Segoe UI", "Helvetica Neue", Arial, sans-serif;
    color: #1f2937;
    font-size: 11pt;
    line-height: 1.55;
    margin: 0;
    padding: 0;
    -webkit-print-color-adjust: exact;
    print-color-adjust: exact;
}
.container { padding: 0; }
h1 {
    font-size: 24pt;
    color: #0f172a;
    border-bottom: 3px solid #2563eb;
    padding-bottom: 6px;
    margin: 0 0 12px 0;
}
h2 {
    font-size: 16pt;
    color: #0f172a;
    border-bottom: 1px solid #cbd5e1;
    padding-bottom: 4px;
    margin: 28px 0 10px 0;
}
h3 {
    font-size: 13.5pt;
    color: #1e3a8a;
    background: #eff6ff;
    padding: 8px 12px;
    border-left: 4px solid #2563eb;
    margin: 24px 0 10px 0;
    page-break-before: auto;
    page-break-after: avoid;
    break-inside: avoid;
}
h3 + p, h3 + ul, h3 + blockquote { page-break-before: avoid; }
h4 {
    font-size: 11.5pt;
    color: #334155;
    margin: 14px 0 6px 0;
}
p { margin: 6px 0; }
ul, ol { margin: 6px 0 6px 0; padding-left: 22px; }
li { margin: 2px 0; }
blockquote {
    border-left: 4px solid #94a3b8;
    background: #f8fafc;
    color: #334155;
    padding: 8px 12px;
    margin: 10px 0;
    border-radius: 3px;
}
strong { color: #0f172a; }
code {
    background: #f1f5f9;
    color: #0f172a;
    padding: 1px 5px;
    border-radius: 3px;
    font-family: "Consolas", "Courier New", monospace;
    font-size: 10pt;
}
pre { background: #f1f5f9; padding: 8px; border-radius: 4px; overflow-x: auto; }
hr {
    border: 0;
    border-top: 1px dashed #cbd5e1;
    margin: 20px 0;
}
table {
    width: 100%;
    border-collapse: collapse;
    margin: 10px 0;
    font-size: 10pt;
    page-break-inside: auto;
}
thead { display: table-header-group; }
tr { page-break-inside: avoid; page-break-after: auto; }
th {
    background: #1e3a8a;
    color: #ffffff;
    text-align: left;
    padding: 7px 9px;
    border: 1px solid #1e3a8a;
    font-weight: 600;
    font-size: 10pt;
}
td {
    padding: 6px 9px;
    border: 1px solid #cbd5e1;
    vertical-align: top;
}
tbody tr:nth-child(odd) { background: #f8fafc; }
tbody tr:hover { background: #eff6ff; }
a { color: #2563eb; text-decoration: none; }

.cover {
    text-align: center;
    padding: 80px 20px 40px 20px;
    border-bottom: 4px double #2563eb;
    margin-bottom: 30px;
}
.cover .badge {
    display: inline-block;
    background: #2563eb;
    color: #fff;
    padding: 4px 14px;
    border-radius: 20px;
    font-size: 10pt;
    letter-spacing: 1px;
    margin-bottom: 20px;
}
.cover h1 {
    font-size: 32pt;
    border: none;
    margin: 0 0 8px 0;
}
.cover .subtitle {
    font-size: 12pt;
    color: #475569;
    margin-bottom: 24px;
}
.cover .meta {
    font-size: 10pt;
    color: #64748b;
}
.page-break { page-break-before: always; }
/* Each M## section starts on a new page for easy QA handout */
h3[id^="m0"], h3[id^="m1"] { page-break-before: always; }
h3#m01-first-time-setup-login { page-break-before: avoid; } /* first module continues after index */
/* Prevent awkward breaks */
h2, h3, h4 { break-after: avoid; }
ul, ol, blockquote { break-inside: avoid; }
"""


def find_edge() -> str:
    for p in EDGE_CANDIDATES:
        if Path(p).exists():
            return p
    raise FileNotFoundError("Microsoft Edge not found. Install Edge or provide another browser.")


def md_to_html(md_text: str) -> str:
    html_body = markdown.markdown(
        md_text,
        extensions=[
            "tables",
            "fenced_code",
            "toc",
            "sane_lists",
            "attr_list",
            "md_in_html",
        ],
        output_format="html5",
    )
    cover = (
        '<div class="cover">'
        '<div class="badge">QA DOCUMENT</div>'
        '<h1>HI ma App &mdash; QA Module Breakdown</h1>'
        '<div class="subtitle">Module-wise testing scope (user perspective)</div>'
        '<div class="meta">19 modules &middot; M01 &mdash; M19 &middot; Test cases will be added later</div>'
        '</div>'
    )
    return (
        "<!doctype html>\n"
        '<html lang="en"><head><meta charset="utf-8">'
        "<title>QA Module Breakdown - HI ma</title>"
        f"<style>{CSS}</style>"
        "</head><body><div class=\"container\">"
        f"{cover}{html_body}"
        "</div></body></html>"
    )


def html_to_pdf(html_path: Path, pdf_path: Path) -> None:
    edge = find_edge()
    html_uri = html_path.resolve().as_uri()
    cmd = [
        edge,
        "--headless=new",
        "--disable-gpu",
        "--no-pdf-header-footer",
        f"--print-to-pdf={pdf_path}",
        html_uri,
    ]
    print(f"Running: {' '.join(cmd)}")
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
    if result.returncode != 0:
        print("Edge stdout:", result.stdout)
        print("Edge stderr:", result.stderr)
        raise RuntimeError(f"Edge print-to-pdf failed with exit {result.returncode}")


def main() -> int:
    if not MD.exists():
        print(f"[ERROR] Markdown source not found: {MD}", file=sys.stderr)
        return 1

    print(f"[1/3] Reading {MD.name}")
    md_text = MD.read_text(encoding="utf-8")

    print(f"[2/3] Converting to HTML -> {HTML.name}")
    html = md_to_html(md_text)
    HTML.write_text(html, encoding="utf-8")
    print(f"       size: {HTML.stat().st_size:,} bytes")

    print(f"[3/3] Printing HTML to PDF -> {PDF.name}")
    html_to_pdf(HTML, PDF)

    if not PDF.exists():
        print("[ERROR] PDF was not produced.", file=sys.stderr)
        return 2

    print("")
    print(f"SUCCESS: {PDF}")
    print(f"         {PDF.stat().st_size:,} bytes")
    return 0


if __name__ == "__main__":
    sys.exit(main())
