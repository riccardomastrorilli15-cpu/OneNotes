#!/usr/bin/env python3
"""Fail closed when a packaged APK/AAB font has not been individually approved.

Resource/asset overrides must still be checked in each actual package. This
checks fonts only; it is not a license audit of drawables, code or dependencies.
"""

import hashlib
import sys
import zipfile
from collections import Counter
from pathlib import Path

# DejaVu Sans Bold, under the DejaVu/Bitstream Vera license in licenses/DejaVu.txt.
APPROVED_SHA256 = {
    "0d977336a6d5fba34eab8e3199eb218327161b5143749f802982c2bc34df0c96",
}
INDEX_ASSET = "assets/sesl_indexscroll_group_font.ttf"
FONT_EXTENSIONS = (".ttf", ".otf", ".ttc", ".woff", ".woff2")


def audit(package_path: Path) -> list[str]:
    errors = []
    expected_asset = f"base/{INDEX_ASSET}" if package_path.suffix.lower() == ".aab" else INDEX_ASSET
    with zipfile.ZipFile(package_path) as package:
        names = package.namelist()
        duplicates = [name for name, count in Counter(names).items() if count > 1]
        if duplicates:
            errors.append(f"Duplicate ZIP entries: {', '.join(duplicates)}")
        if expected_asset not in names:
            errors.append(f"Expected overriding asset not found: {expected_asset}")
        fonts = [name for name in names if name.lower().endswith(FONT_EXTENSIONS)]
        for name in fonts:
            digest = hashlib.sha256(package.read(name)).hexdigest()
            if digest not in APPROVED_SHA256:
                errors.append(f"Unapproved font: {name} (SHA-256 {digest})")
            else:
                print(f"Approved font: {name} ({digest})")
    return errors


def main() -> int:
    if len(sys.argv) != 2:
        print(f"Usage: {sys.argv[0]} path/to/app.apk-or-app.aab", file=sys.stderr)
        return 2
    try:
        errors = audit(Path(sys.argv[1]))
    except (OSError, zipfile.BadZipFile) as exc:
        print(f"Cannot inspect package: {exc}", file=sys.stderr)
        return 1
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print("Font audit passed. Code, other assets and dependency licenses still require review.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
