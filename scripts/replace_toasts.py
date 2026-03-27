#!/usr/bin/env python3
"""One-off: replace Toast.makeText(...).show() with showAppToast for compact UI."""
import re
from pathlib import Path

PROJECT = Path(__file__).resolve().parents[1]
ROOT = PROJECT / "app" / "src" / "main" / "java"
IMPORT = "import com.gmwapp.hima.utils.showAppToast\n"


def add_import(text: str) -> str:
    if "showAppToast" not in text:
        return text
    if "import com.gmwapp.hima.utils.showAppToast" in text:
        return text
    # After package declaration
    m = re.match(r"(package [^\n]+\n)", text)
    if m:
        insert_at = m.end()
        return text[:insert_at] + "\n" + IMPORT + text[insert_at:]
    return IMPORT + text


def process(text: str) -> str:
    # android.widget.Toast.makeText(this, ..., LENGTH_SHORT)
    text = re.sub(
        r"android\.widget\.Toast\.makeText\(\s*this\s*,\s*(.*?)\s*,\s*android\.widget\.Toast\.(LENGTH_\w+)\s*\)\s*\.show\(\)",
        r"showAppToast(\1, Toast.\2)",
        text,
        flags=re.DOTALL,
    )
    # Toast.makeText(this@Class, msg, LENGTH) multiline or single
    text = re.sub(
        r"Toast\.makeText\(\s*this@(\w+)\s*,\s*([\s\S]*?)\s*,\s*(Toast\.LENGTH_\w+)\s*\)\s*\.show\(\)",
        r"showAppToast(\2, \3)",
        text,
    )
    # Toast.makeText(this, msg, LENGTH)
    text = re.sub(
        r"Toast\.makeText\(\s*this\s*,\s*(.*?)\s*,\s*(Toast\.LENGTH_\w+)\s*\)\s*\.show\(\)",
        r"showAppToast(\1, \2)",
        text,
        flags=re.DOTALL,
    )
    # Toast.makeText(requireContext(), ...)
    text = re.sub(
        r"Toast\.makeText\(\s*requireContext\(\)\s*,\s*(.*?)\s*,\s*(Toast\.LENGTH_\w+)\s*\)\s*\.show\(\)",
        r"requireContext().showAppToast(\1, \2)",
        text,
        flags=re.DOTALL,
    )
    # Toast.makeText(applicationContext, ...)
    text = re.sub(
        r"Toast\.makeText\(\s*applicationContext\s*,\s*(.*?)\s*,\s*(Toast\.LENGTH_\w+)\s*\)\s*\.show\(\)",
        r"applicationContext.showAppToast(\1, \2)",
        text,
        flags=re.DOTALL,
    )
    # Toast.makeText(context, ...) when context is variable
    text = re.sub(
        r"Toast\.makeText\(\s*context\s*,\s*(.*?)\s*,\s*(Toast\.LENGTH_\w+)\s*\)\s*\.show\(\)",
        r"context.showAppToast(\1, \2)",
        text,
        flags=re.DOTALL,
    )
    return text


def main():
    for path in sorted(ROOT.rglob("*.kt")):
        if path.name == "CompactToast.kt":
            continue
        raw = path.read_text(encoding="utf-8")
        new = process(raw)
        if new == raw:
            continue
        new = add_import(new)
        path.write_text(new, encoding="utf-8")
        print(path.relative_to(PROJECT))


if __name__ == "__main__":
    main()
