"""Exercise cache selection through the real resource-checker command line."""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
CHECKER = ROOT / 'tools/check_resources.py'
CLIENT_SUFFIX = Path('caches/fabric-loom/1.20.1/minecraft-client.jar')


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
