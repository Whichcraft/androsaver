import contextlib
import io
import subprocess
import sys
import unittest
from unittest import mock

from core.cli import build_parser, parse_args


class CliTests(unittest.TestCase):
    def test_defaults(self):
        self.assertEqual(vars(parse_args("test", [])), {
            "display": None, "mode": None, "gl": False, "low_spec": False,
            "visual_profile": None, "span_child": False,
        })

    def test_all_flags_and_aliases(self):
        for flags in (("-d", "-m", "-g"), ("--display", "--mode", "--gl")):
            with self.subTest(flags=flags):
                args = parse_args("test", [flags[0], "2", flags[1], "-1", flags[2],
                    "--low-spec", "--visual-profile", "tv", "--span-child"])
                self.assertEqual(vars(args), {
                    "display": 2, "mode": -1, "gl": True, "low_spec": True,
                    "visual_profile": "tv", "span_child": True,
                })
        for profile in ("auto", "laptop", "tv"):
            self.assertEqual(parse_args("test", ["--visual-profile", profile]).visual_profile,
                             profile)

    def test_invalid_arguments_exit_with_parser_error(self):
        for argv in (["--visual-profile", "unknown"], ["--mode", "x"],
                     ["--display"], ["--unknown"]):
            with self.subTest(argv=argv), contextlib.redirect_stderr(io.StringIO()):
                with self.assertRaises(SystemExit) as caught:
                    parse_args("test", argv)
                self.assertEqual(caught.exception.code, 2)

    def test_help_contains_version_and_hides_child_flag(self):
        help_text = build_parser("example-version").format_help()
        self.assertIn("vexample-version", help_text)
        self.assertIn("Controls:", help_text)
        self.assertNotIn("--span-child", help_text)
        with contextlib.redirect_stdout(io.StringIO()):
            with self.assertRaises(SystemExit) as caught:
                parse_args("test", ["--help"])
        self.assertEqual(caught.exception.code, 0)

    def test_app_wrapper_uses_process_arguments_and_current_version(self):
        from psysualizer import VisualizerApp, __version__
        with mock.patch.object(sys, "argv", ["psysualizer.py", "--mode", "4"]):
            self.assertEqual(VisualizerApp._parse_args(None).mode, 4)
        with mock.patch("psysualizer.parse_args") as parser:
            VisualizerApp._parse_args(None)
            parser.assert_called_once_with(__version__)

    def test_fresh_import_and_parsing_do_not_load_runtime(self):
        subprocess.run([sys.executable, "-c", """
import sys
from core.cli import parse_args
parse_args('test', [])
assert not {'psysualizer', 'pygame', 'sounddevice', 'core.audio_engine',
            'core.display_manager', 'effects'} & sys.modules.keys()
"""], check=True, capture_output=True, text=True)


if __name__ == "__main__":
    unittest.main()
