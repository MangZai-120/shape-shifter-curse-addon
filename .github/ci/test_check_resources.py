"""Exercise cache selection, namespace references and packaging through the checker CLI."""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[2]
CHECKER = ROOT / 'tools/check_resources.py'
CLIENT_SUFFIX = Path('caches/fabric-loom/1.20.1/minecraft-client.jar')


class ResourceLayoutTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix='ssca-layout-')
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.checker = self.root / 'tools/check_resources.py'
        self.checker.parent.mkdir()
        shutil.copyfile(CHECKER, self.checker)
        self.resources = self.root / 'src/main/resources'
        self.put('fabric.mod.json', b'{"id":"ssc_addon","mixins":["ssc_addon.mixins.json"]}')
        self.put('ssc_addon.mixins.json', b'{}')

    def put(self, name, content):
        file = self.resources / name
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(content)

    def failure(self, *args):
        result = subprocess.run([sys.executable, '-X', 'utf8', str(self.checker), 'resources', *args],
                                capture_output=True, text=True, encoding='utf-8', timeout=30)
        self.assertNotEqual(result.returncode, 0)
        return result.stderr

    def test_old_id_in_moved_origin(self):
        origin = 'data/ssc_addon/origins/test.json'
        self.put(origin, b'{"powers":["my_addon:old"]}')
        error = self.failure()
        self.assertIn(origin, error)
        self.assertIn('my_addon:old', error)

    def test_texture_lookup_is_case_sensitive_on_every_platform(self):
        self.put('assets/ssc_addon/textures/fx.png', b'image')
        self.put('assets/ssc_addon/ssc_form_model/test.json', b'{"texture":"ssc_addon:textures/Fx.png"}')
        self.assertIn('textures/Fx.png', self.failure())

    def test_jar_reports_missing_stale_changed_and_duplicate_files(self):
        icon = 'assets/ssc_addon/icon.png'
        power = 'data/ssc_addon/powers/test.json'
        legacy = 'data/my_addon/powers/test.json'
        self.put(icon, b'current')
        self.put(power, b'{}')
        package = self.root / 'build.jar'
        with ZipFile(package, 'w') as jar:
            jar.write(self.resources / 'fabric.mod.json', 'fabric.mod.json')
            jar.writestr(icon, b'outdated')
            with self.assertWarns(UserWarning):
                jar.writestr(icon, b'outdated')
            jar.writestr(legacy, b'{}')
        error = self.failure('--addon-jar', str(package))
        for issue in ('missing: ' + power, 'missing: ssc_addon.mixins.json', 'unexpected: ' + legacy,
                      'changed: ' + icon, 'duplicate: ' + icon):
            self.assertIn(issue, error)


class ResourceCheckerCliTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        configured = os.environ.get('GRADLE_USER_HOME')
        candidates = ([Path(configured).expanduser() / CLIENT_SUFFIX] if configured else [])
        candidates.append(Path.home() / '.gradle' / CLIENT_SUFFIX)
        cls.client = next((path for path in candidates if path.is_file()), None)
        if cls.client is None:
            raise unittest.SkipTest('Build Minecraft once to provide a real client JAR for resource checks')
        cls.directory = tempfile.TemporaryDirectory(prefix='ssca-resource-cli-')
        cls.temporary = Path(cls.directory.name)
        cls.cache = cls.temporary / 'custom-gradle-home'
        copied = cls.cache / CLIENT_SUFFIX
        copied.parent.mkdir(parents=True)
        shutil.copyfile(cls.client, copied)

    @classmethod
    def tearDownClass(cls):
        cls.directory.cleanup()

    def run_checker(self, *arguments, gradle_home=None):
        env = os.environ.copy()
        env['PYTHONIOENCODING'] = 'utf-8'
        if gradle_home is None:
            env.pop('GRADLE_USER_HOME', None)
        else:
            env['GRADLE_USER_HOME'] = str(gradle_home)
        return subprocess.run([sys.executable, str(CHECKER), *arguments], cwd=ROOT, env=env,
                              stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                              text=True, encoding='utf-8', errors='replace', timeout=30)

    def assert_resources_pass(self, result):
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn('Resource review passed:', result.stdout)
        self.assertIn('Form tutorial review passed:', result.stdout)

    def test_custom_gradle_home(self):
        self.assert_resources_pass(self.run_checker('resources', gradle_home=self.cache))

    def test_missing_custom_home_does_not_fall_back_silently(self):
        missing = self.temporary / 'missing-cache'
        result = self.run_checker('resources', gradle_home=missing)
        self.assertEqual(result.returncode, 2, result.stdout)
        self.assertIn(str(missing / CLIENT_SUFFIX), result.stdout)
        self.assertIn('--minecraft-client PATH', result.stdout)
        self.assertNotIn('Traceback', result.stdout)

    def test_explicit_client_overrides_missing_environment_cache(self):
        self.assert_resources_pass(self.run_checker('resources', '--minecraft-client', str(self.client),
                                                   gradle_home=self.temporary / 'missing-cache'))

    def test_missing_explicit_client_overrides_valid_environment_cache(self):
        missing = self.temporary / 'missing-client.jar'
        result = self.run_checker('resources', '--minecraft-client', str(missing), gradle_home=self.cache)
        self.assertEqual(result.returncode, 2, result.stdout)
        self.assertIn(str(missing), result.stdout)
        self.assertNotIn('Traceback', result.stdout)

    def test_relative_explicit_client(self):
        try:
            relative = os.path.relpath(self.client, ROOT)
        except ValueError:
            self.skipTest('Windows cannot express a relative path between different drives')
        self.assert_resources_pass(self.run_checker('resources', '--minecraft-client', relative))

    def test_default_user_home(self):
        if not (Path.home() / '.gradle' / CLIENT_SUFFIX).is_file():
            self.skipTest('Default Gradle cache is not installed in this environment')
        self.assert_resources_pass(self.run_checker('resources'))

    def test_docs_do_not_require_minecraft_cache(self):
        result = self.run_checker('docs', gradle_home=self.temporary / 'missing-cache')
        self.assertEqual(result.returncode, 0, result.stdout)
        self.assertIn('Local document links passed.', result.stdout)

    def test_all_keeps_resources_tutorials_and_document_validation(self):
        result = self.run_checker('all', '--minecraft-client', str(self.client))
        self.assert_resources_pass(result)
        self.assertIn('Local document links passed.', result.stdout)


if __name__ == '__main__':
    unittest.main(verbosity=2)
