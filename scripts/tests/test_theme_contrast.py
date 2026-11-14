"""Static contrast checks for selected opaque theme color pairs, not a UI audit."""

import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def colors(variant):
    path = ROOT / "app/src/main/res" / variant / "colors.xml"
    return {element.attrib["name"]: element.text for element in ET.parse(path).getroot() if element.tag == "color"}


def luminance(hex_color):
    if not hex_color.startswith("#") or len(hex_color) != 7:
        raise ValueError("Expected an opaque #RRGGBB color")
    channels = [int(hex_color[i : i + 2], 16) / 255 for i in (1, 3, 5)]
    channels = [c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4 for c in channels]
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2]


def contrast(first, second):
    values = sorted((luminance(first), luminance(second)))
    return (values[1] + 0.05) / (values[0] + 0.05)


class ThemeContrastTest(unittest.TestCase):
    def test_selected_text_pairs_reach_aa_normal_text(self):
        pairs = [
            ("lume_foreground", "lume_surface"),
            ("lume_foreground", "lume_background"),
            ("lume_secondary", "lume_surface"),
            ("lume_secondary", "lume_background"),
            ("lume_accent", "lume_surface"),
            ("lume_accent", "lume_background"),
            ("lume_accent", "lume_accent_soft"),
            ("lume_on_accent", "lume_accent"),
        ]
        for variant in ("values", "values-night"):
            theme = colors(variant)
            for foreground, background in pairs:
                with self.subTest(variant=variant, foreground=foreground, background=background):
                    self.assertGreaterEqual(contrast(theme[foreground], theme[background]), 4.5)


if __name__ == "__main__":
    unittest.main()
