"""Exercise release ordering and remote corruption handling without GitHub access."""
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


MOCK = r'''#!/usr/bin/env python3
import json, os, shutil, sys
from pathlib import Path
root = Path(os.environ["TEST_RELEASE_STATE"])
args = sys.argv[1:]
tool = Path(sys.argv[0]).name
with (root / "calls.jsonl").open("a") as log:
    log.write(json.dumps([tool] + args) + "\n")
def option(name):
    return args[args.index(name) + 1]
if tool == "git":
    sha, ref = args[2].split(":")
    (root / (ref.split("/")[-1] + ".tag")).write_text(sha)
    sys.exit(0)
if args[0] == "api":
    print((root / (args[1].split("/")[-1] + ".tag")).read_text())
    sys.exit(0)
action, tag = args[1:3]
release = root / tag
if action == "view":
    if not release.is_dir(): sys.exit(1)
    if "--json" in args: print((release / "draft").read_text())
elif action in ("create", "upload"):
    release.mkdir(exist_ok=True)
    if action == "create":
        draft = "--draft" in args
        (release / "draft").write_text(str(draft).lower())
        if not draft: (root / (tag + ".tag")).write_text(os.environ["GITHUB_SHA"])
    for value in args[3:]:
        source = Path(value)
        if source.is_file(): shutil.copyfile(source, release / source.name)
elif action == "download":
    name = option("--pattern")
    output = Path(option("--dir")) / name
    shutil.copyfile(release / name, output)
    corrupt = os.environ.get("TEST_CORRUPT", "")
    if name.endswith((".apk", ".aab")) and (
        (corrupt == "immutable" and tag.startswith("build-")) or
        (corrupt == "rolling" and not tag.startswith("build-"))):
        output.write_bytes(b"damaged remote artifact")
elif action == "edit":
    if "--draft=false" in args:
        (release / "draft").write_text("false")
        (root / (tag + ".tag")).write_text(os.environ["GITHUB_SHA"])
else:
    raise RuntimeError(args)
'''


class PublishReleaseTest(unittest.TestCase):
    def exercise(self, channel="dev-channel", corrupt="", bundle=False, existing=False):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            bin_dir = root / "bin"
            bin_dir.mkdir()
            state = root / "state"
            state.mkdir()
            for name in ("gh", "git"):
                command = bin_dir / name
                command.write_text(MOCK)
                command.chmod(0o755)
            artifact = root / ("androsaver.aab" if bundle else "androsaver.apk")
            artifact.write_bytes(b"non-secret release test fixture")
            manifest = root / "version.json"
            manifest.write_text(json.dumps({
                "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(),
                "sizeBytes": artifact.stat().st_size,
            }))
            if existing:
                (state / channel).mkdir()
                (state / channel / "draft").write_text("false")
                (state / (channel + ".tag")).write_text("old-commit")
            env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ["PATH"],
                       TEST_RELEASE_STATE=str(state), TEST_CORRUPT=corrupt,
                       GITHUB_SHA="a" * 40, GITHUB_REPOSITORY="example/test",
                       RUNNER_TEMP=str(root))
            args = ["bash", str(Path(__file__).with_name("publish-release.sh")), channel, str(artifact)]
            if not bundle:
                args.append(str(manifest))
            result = subprocess.run(args, env=env, capture_output=True, text=True, timeout=20)
            calls = [json.loads(line) for line in (state / "calls.jsonl").read_text().splitlines()]
            pushes = [i for i, call in enumerate(calls) if call[:2] == ["git", "push"]]
            if corrupt:
                self.assertNotEqual(0, result.returncode)
                self.assertEqual([], pushes)
                if existing:
                    self.assertEqual("old-commit", (state / (channel + ".tag")).read_text())
            else:
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(1, len(pushes))
                downloads = [i for i, call in enumerate(calls)
                             if call[:4] == ["gh", "release", "download", channel]]
                self.assertTrue(downloads)
                self.assertLess(max(downloads), pushes[0])
                self.assertEqual("a" * 40, (state / (channel + ".tag")).read_text())

    def test_apk_is_downloaded_and_verified_before_channel_tag_moves(self):
        self.exercise(existing=True)

    def test_damaged_immutable_asset_prevents_channel_update(self):
        self.exercise(corrupt="immutable", existing=True)

    def test_damaged_rolling_asset_prevents_existing_tag_move(self):
        self.exercise(corrupt="rolling", existing=True)

    def test_playstore_bundle_is_also_verified(self):
        self.exercise(channel="playstore-stable", bundle=True)


if __name__ == "__main__":
    unittest.main()
