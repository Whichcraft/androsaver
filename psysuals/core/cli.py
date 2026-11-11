"""Command-line parsing without display or audio initialization."""

import argparse


def build_parser(version: str) -> argparse.ArgumentParser:
    desc = "psysuals — The Ultimate Psychedelic Music Visualizer v" + version + "\n\n"
    desc += "Controls:\n"
    desc += "  Space / Click   Cycle modes\n"
    desc += "  1-9             Jump to mode\n"
    desc += "  Arrows          Intensity / Settings / Modes\n"
    desc += "  Tab             Toggle settings pane\n"
    desc += "  F               Toggle fullscreen\n"
    desc += "  V               Cycle Auto / Laptop / TV profile\n"
    desc += "  H / Shift+H     HUD visibility / Detail\n"
    desc += "  M / Shift+M     Tap Tempo / Span Mode\n"
    desc += "  D               Device picker / Span cycle\n"
    desc += "  Shift+R         Curated foreground/background recipe\n"
    desc += "  Shift+X         Cycle psychedelic post-process\n"
    desc += "  Q / Esc         Quit"

    parser = argparse.ArgumentParser(
        description=desc,
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("-d", "--display", type=int, default=None, help="Target display index (e.g. 0, 1)")
    parser.add_argument("-m", "--mode", type=int, default=None, help="Starting mode index (0-based)")
    parser.add_argument("-g", "--gl", action="store_true", help="Enable ModernGL hardware acceleration")
    parser.add_argument("--low-spec", action="store_true", help="Optimize performance for low-end systems (lowers FPS and particle counts)")
    parser.add_argument("--visual-profile", choices=("auto", "laptop", "tv"), default=None,
                        help="Motion/detail profile; V cycles profiles during playback")
    parser.add_argument("--span-child", action="store_true", help=argparse.SUPPRESS)
    return parser


def parse_args(version: str, argv=None) -> argparse.Namespace:
    """Parse explicit arguments, or process arguments when argv is None."""
    return build_parser(version).parse_args(argv)
