"""Release state-machine regressions. No Android build or GitHub access required."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).with_name('publish-release.sh')
OLD, CURRENT, NEW = 'a' * 40, 'b' * 40, 'c' * 40
MOCK = r'''#!/usr/bin/env python3
import json, os, shutil, sys
from pathlib import Path
root = Path(os.environ['TEST_RELEASE_STATE'])
args = sys.argv[1:]
tool = Path(sys.argv[0]).name
with (root / 'calls.jsonl').open('a') as log:
    log.write(json.dumps([tool] + args) + '\n')
def option(name): return args[args.index(name) + 1]
def once(name):
    p = root / name
    if p.exists(): return False
    p.touch(); return True
if tool == 'sleep': sys.exit(0)
if tool == 'apkanalyzer':
    data = json.loads(Path(args[2]).read_text())
    print(data[{'version-code':'code', 'version-name':'version', 'application-id':'package'}[args[1]]])
    sys.exit(0)
if tool == 'git':
    if args[0] == 'fetch': sys.exit(0)
    if args[0] == 'merge-base':
        sys.exit(0 if args[2] == 'a' * 40 and args[3] == 'b' * 40 else 1)
    if args[0] == 'ls-remote':
        if os.environ.get('TEST_TRANSPORT_ERROR'): sys.exit(128)
        tag = args[-2].split('/')[-1]
        path = root / (tag + '.tag')
        if not path.exists(): sys.exit(2)
        value = path.read_text()
        if tag == 'dev-channel' and (root / 'pushed').exists():
            if os.environ.get('TEST_STALE_REF') and once('stale-ref'):
                value = 'a' * 40
            if os.environ.get('TEST_WRONG_REF'): value = 'a' * 40
        print(value + '\trefs/tags/' + tag)
        sys.exit(0)
    if args[0] == 'push':
        if os.environ.get('TEST_PUSH_FAILURE') and once('push-failure'): sys.exit(1)
        sha, ref = args[2].split(':')
        tag = ref.split('/')[-1]
        path = root / (tag + '.tag')
        expected = args[3].split(':', 1)[1]
        actual = path.read_text() if path.exists() else ''
        if actual != expected: sys.exit(1)
        path.write_text(sha); (root / 'pushed').touch(); sys.exit(0)
    raise RuntimeError(args)
action, tag = args[1:3]
release = root / tag
if action == 'view':
    if not release.is_dir(): sys.exit(1)
    if '--json' in args: print((release / 'draft').read_text())
elif action in ('create', 'upload'):
    release.mkdir(exist_ok=True)
    if action == 'create':
        draft = '--draft' in args
        (release / 'draft').write_text(str(draft).lower())
        if not draft: (root / (tag + '.tag')).write_text(os.environ['GITHUB_SHA'])
    for value in args[3:]:
        source = Path(value)
        if source.is_file():
            if (tag == 'dev-channel' and source.name == 'version.json'
                and os.environ.get('TEST_PARTIAL_UPLOAD') and once('partial-upload')):
                sys.exit(1)
            shutil.copyfile(source, release / source.name)
elif action == 'download':
    name = option('--pattern')
    if os.environ.get('TEST_DOWNLOAD_RETRY') and once('download-retry'): sys.exit(1)
    if os.environ.get('TEST_MISSING_MANIFEST') and name == 'version.json': sys.exit(1)
    source = release / name
    if not source.exists(): sys.exit(1)
    output = Path(option('--dir')) / name
    shutil.copyfile(source, output)
    corrupt = os.environ.get('TEST_CORRUPT', '')
    if name.endswith(('.apk', '.aab')) and (
        corrupt == 'immutable' and tag.startswith('build-') or
        corrupt == 'rolling' and not tag.startswith('build-')):
        output.write_bytes(b'damaged remote artifact')
elif action == 'edit':
    if '--draft=false' in args:
        (release / 'draft').write_text('false')
        (root / (tag + '.tag')).write_text(os.environ['GITHUB_SHA'])
        if os.environ.get('TEST_CHANNEL_MOVED'):
            (root / 'dev-channel.tag').write_text('c' * 40)
else: raise RuntimeError(args)
'''


class PublishReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.state = self.root / 'state'
        self.state.mkdir()
        bin_dir = self.root / 'bin'
        bin_dir.mkdir()
        for name in ('gh', 'git', 'apkanalyzer', 'sleep'):
            path = bin_dir / name
            path.write_text(MOCK)
            path.chmod(0o755)
        self.env = dict(os.environ, PATH=str(bin_dir) + os.pathsep + os.environ['PATH'],
                        TEST_RELEASE_STATE=str(self.state), GITHUB_SHA=CURRENT,
                        GITHUB_REPOSITORY='example/test', RUNNER_TEMP=str(self.root))
        self.channel = 'dev-channel'
        self.apk = self.root / 'androsaver.apk'
        self.manifest = self.root / 'version.json'
        self.build()

    def run_script(self, *args, **env):
        return subprocess.run(['bash', str(SCRIPT), *map(str, args)],
                              env=dict(self.env, **env), capture_output=True, text=True, timeout=20)

    def build(self, code=390, version='2.12.9', content='first build'):
        self.apk.write_text(json.dumps(dict(code=code, version=version, content=content,
                                           package='com.androsaver')))
        result = self.run_script('--manifest', self.channel, self.apk, self.manifest)
        self.assertEqual(0, result.returncode, result.stderr)

    def publish(self, **env):
        return self.run_script(self.channel, self.apk, self.manifest, **env)

    def assert_success(self, result):
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def calls(self):
        path = self.state / 'calls.jsonl'
        return [json.loads(line) for line in path.read_text().splitlines()]

    def seed_channel(self, sha=OLD, code=389):
        folder = self.state / self.channel
        folder.mkdir()
        (folder / 'draft').write_text('false')
        (folder / 'androsaver.apk').write_bytes(b'previous APK')
        (folder / 'version.json').write_text(json.dumps(dict(versionCode=code)))
        (self.state / (self.channel + '.tag')).write_text(sha)

    def assert_no_channel_write(self):
        self.assertFalse([c for c in self.calls() if c[:4] in (
            ['gh', 'release', 'upload', self.channel], ['gh', 'release', 'create', self.channel])])
        self.assertFalse([c for c in self.calls() if c[:2] == ['git', 'push']])

    def test_manifest_uses_actual_apk_identity(self):
        self.build(code=915, version='3.4.27')
        data = json.loads(self.manifest.read_text())
        self.assertEqual((915, '3.4.27'), (data['versionCode'], data['versionName']))
        self.assertEqual(hashlib.sha256(self.apk.read_bytes()).hexdigest(), data['sha256'])
        self.assertEqual(self.apk.stat().st_size, data['sizeBytes'])

    def test_apk_manifest_mismatch_is_rejected_before_upload(self):
        data = json.loads(self.manifest.read_text())
        data['versionName'] = '2.12.0'
        self.manifest.write_text(json.dumps(data))
        result = self.publish()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('disagree', result.stderr)
        self.assertFalse([c for c in self.calls() if c[:3] == ['gh', 'release', 'create']])

    def test_package_identity_is_checked(self):
        data = json.loads(self.apk.read_text()); data['package'] = 'other.app'
        self.apk.write_text(json.dumps(data))
        result = self.run_script('--manifest', self.channel, self.apk, self.manifest)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Unexpected APK package', result.stderr)

    def test_downloaded_assets_are_verified_before_leased_tag_push(self):
        self.seed_channel()
        self.assert_success(self.publish())
        calls = self.calls()
        push = next(i for i, c in enumerate(calls) if c[:2] == ['git', 'push'])
        downloads = [i for i, c in enumerate(calls) if c[:4] == ['gh', 'release', 'download', self.channel]]
        self.assertLess(max(downloads), push)
        self.assertIn('--force-with-lease=refs/tags/dev-channel:' + OLD, calls[push])
        self.assertEqual(CURRENT, (self.state / 'dev-channel.tag').read_text())

    def test_old_rerun_cannot_overwrite_a_newer_channel(self):
        self.seed_channel(NEW, 391)
        self.assert_success(self.publish())
        self.assert_no_channel_write()
        self.assertEqual(NEW, (self.state / 'dev-channel.tag').read_text())
        self.assertEqual(b'previous APK', (self.state / 'dev-channel/androsaver.apk').read_bytes())

    def test_same_or_lower_version_code_cannot_replace_ancestor(self):
        for code in (390, 391):
            with self.subTest(old_code=code):
                folder = self.state / self.channel
                if not folder.exists(): self.seed_channel(code=code)
                else: (folder / 'version.json').write_text(json.dumps(dict(versionCode=code)))
                result = self.publish()
                self.assertNotEqual(0, result.returncode)
                self.assertIn('Version code must increase', result.stderr)
                self.assert_no_channel_write()

    def test_same_commit_rebuild_reuses_published_bytes_and_manifest(self):
        self.assert_success(self.publish())
        first = self.apk.read_bytes()
        evidence = self.manifest.read_bytes()
        self.build(content='different nondeterministic rebuild')
        self.assert_success(self.publish())
        self.assertEqual(first, (self.state / 'dev-channel/androsaver.apk').read_bytes())
        self.assertEqual(evidence, (self.state / 'dev-channel/version.json').read_bytes())
        immutable_uploads = [c for c in self.calls() if c[:3] == ['gh', 'release', 'upload'] and c[3].startswith('build-')]
        self.assertEqual([], immutable_uploads)

    def test_interrupted_draft_can_be_replaced_and_completed(self):
        immutable = self.state / ('build-dev-channel-' + CURRENT)
        immutable.mkdir(); (immutable / 'draft').write_text('true')
        (immutable / 'androsaver.apk').write_bytes(b'incomplete build')
        self.assert_success(self.publish())
        self.assertEqual('false', (immutable / 'draft').read_text())
        self.assertEqual(self.apk.read_bytes(), (immutable / 'androsaver.apk').read_bytes())

    def test_partial_rolling_upload_can_be_resumed(self):
        self.seed_channel()
        self.assertNotEqual(0, self.publish(TEST_PARTIAL_UPLOAD='1').returncode)
        self.assertEqual(OLD, (self.state / 'dev-channel.tag').read_text())
        original = self.apk.read_bytes()
        self.build(content='rebuilt after publication failure')
        self.assert_success(self.publish())
        self.assertEqual(original, (self.state / 'dev-channel/androsaver.apk').read_bytes())
        self.assertEqual(CURRENT, (self.state / 'dev-channel.tag').read_text())

    def test_corrupt_immutable_download_never_changes_channel(self):
        self.seed_channel()
        self.assertNotEqual(0, self.publish(TEST_CORRUPT='immutable').returncode)
        self.assert_no_channel_write()

    def test_complete_upload_before_failed_tag_push_can_be_resumed(self):
        self.seed_channel()
        self.assertNotEqual(0, self.publish(TEST_PUSH_FAILURE='1').returncode)
        self.assertEqual(OLD, (self.state / 'dev-channel.tag').read_text())
        original = self.apk.read_bytes()
        self.build(content='rebuilt after tag failure')
        self.assert_success(self.publish())
        self.assertEqual(original, (self.state / 'dev-channel/androsaver.apk').read_bytes())
        self.assertEqual(CURRENT, (self.state / 'dev-channel.tag').read_text())

    def test_corrupt_rolling_download_prevents_tag_move(self):
        self.seed_channel()
        self.assertNotEqual(0, self.publish(TEST_CORRUPT='rolling').returncode)
        self.assertEqual(OLD, (self.state / 'dev-channel.tag').read_text())
        self.assertFalse([c for c in self.calls() if c[:2] == ['git', 'push']])

    def test_transient_download_failure_is_retried(self):
        self.assert_success(self.publish(TEST_DOWNLOAD_RETRY='1'))
        self.assertTrue((self.state / 'download-retry').exists())

    def test_missing_manifest_fails_without_channel_update(self):
        self.seed_channel()
        self.assertNotEqual(0, self.publish(TEST_MISSING_MANIFEST='1').returncode)
        self.assert_no_channel_write()

    def test_delayed_tag_visibility_is_retried(self):
        self.seed_channel()
        self.assert_success(self.publish(TEST_STALE_REF='1'))
        self.assertTrue((self.state / 'stale-ref').exists())

    def test_wrong_tag_does_not_report_success(self):
        self.seed_channel()
        result = self.publish(TEST_WRONG_REF='1')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('did not resolve', result.stderr)

    def test_channel_change_before_upload_is_rejected(self):
        self.seed_channel()
        result = self.publish(TEST_CHANNEL_MOVED='1')
        self.assertNotEqual(0, result.returncode)
        self.assert_no_channel_write()

    def test_git_transport_error_does_not_look_like_missing_tag(self):
        self.assertNotEqual(0, self.publish(TEST_TRANSPORT_ERROR='1').returncode)
        self.assert_no_channel_write()

    def test_immutable_tag_for_another_commit_is_rejected_before_upload(self):
        (self.state / ('build-dev-channel-' + CURRENT + '.tag')).write_text(NEW)
        result = self.publish()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Immutable tag belongs to another commit', result.stderr)
        self.assert_no_channel_write()
        self.assertFalse([c for c in self.calls() if c[:3] == ['gh', 'release', 'create']])

    def test_real_git_distinguishes_annotated_tag_lease_from_commit(self):
        # Exercise the actual Git wire output; mocks cannot prove ref peeling.
        git = shutil.which('git')
        remote = self.root / 'remote.git'
        checkout = self.root / 'checkout'
        def command(*args):
            return subprocess.check_output([git, *args], text=True, stderr=subprocess.DEVNULL).strip()
        command('init', '--bare', str(remote))
        command('init', str(checkout))
        command('-C', str(checkout), 'config', 'user.name', 'Release test')
        command('-C', str(checkout), 'config', 'user.email', 'release@example.invalid')
        command('-C', str(checkout), '-c', 'commit.gpgsign=false', 'commit', '--allow-empty', '-m', 'fixture')
        commit = command('-C', str(checkout), 'rev-parse', 'HEAD')
        command('-C', str(checkout), 'remote', 'add', 'origin', str(remote))
        command('-C', str(checkout), '-c', 'tag.gpgsign=false', 'tag', '-a', 'dev-channel', '-m', 'fixture')
        tag_object = command('-C', str(checkout), 'rev-parse', 'refs/tags/dev-channel')
        command('-C', str(checkout), 'push', 'origin', 'refs/tags/dev-channel')
        body = SCRIPT.read_text().split('remote_tag() {', 1)[1].split('\n}', 1)[0]
        result = subprocess.check_output(
            ['bash', '-c', 'remote_tag() {' + body + '\n}\nremote_tag dev-channel\nremote_tag dev-channel raw'],
            cwd=checkout, text=True)
        self.assertEqual([commit, tag_object], result.splitlines())

    def test_aab_rebuild_reuses_published_bundle_and_integrity_evidence(self):
        bundle = self.root / 'androsaver.aab'
        bundle.write_bytes(b'first bundle')
        self.assert_success(self.run_script('playstore-stable', bundle))
        bundle.write_bytes(b'rebuilt bundle')
        self.assert_success(self.run_script('playstore-stable', bundle))
        self.assertEqual(b'first bundle', (self.state / 'playstore-stable/androsaver.aab').read_bytes())
        self.assertTrue((self.state / 'playstore-stable/artifact-integrity.json').is_file())


if __name__ == '__main__':
    unittest.main()
