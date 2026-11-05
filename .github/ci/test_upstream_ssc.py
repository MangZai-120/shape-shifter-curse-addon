import json
from pathlib import Path
import shutil
import tempfile
import unittest
from zipfile import ZipFile

import upstream_ssc as ssc

COMMIT = 'a' * 40
REPOSITORY = 'https://github.com/onixary/shape-shifter-curse-fabric.git'


class UpstreamSnapshotTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)

    def jar(self, name='ssc.jar', version='1.10.0', namespace='intermediary', multi_eye=True):
        path = self.directory / name
        with ZipFile(path, 'w') as jar:
            jar.writestr('fabric.mod.json', json.dumps({'id': 'shape-shifter-curse', 'version': version,
                                                       'accessWidener': 'ssc.accesswidener'}))
            jar.writestr('ssc.accesswidener', f'accessWidener v1 {namespace}\n')
            jar.writestr('net/onixary/shapeShifterCurseFabric/render/form_render/sub_controller/FormEyeBlinkController.class',
                         b'\xca\xfe\xba\xbe' + (b'EYE_ROOT_BONES' if multi_eye else b'EYE_ROOT_BONE'))
        return path

    def record(self):
        return ssc.record(self.directory, COMMIT, REPOSITORY)

    def test_resolves_default_branch_without_assuming_master(self):
        self.assertEqual(ssc.parse_remote(f'ref: refs/heads/development\tHEAD\n{COMMIT}\tHEAD\n'),
                         {'branch': 'development', 'commit': COMMIT})

    def test_missing_default_branch_is_rejected(self):
        with self.assertRaises(ValueError):
            ssc.parse_remote(f'{COMMIT}\tHEAD\n')

    def test_complete_commit_required(self):
        for commit in ['main', 'a' * 7, 'A' * 40, 'a' * 39 + '\n']:
            with self.subTest(commit=commit), self.assertRaises(ValueError):
                ssc.require_commit(commit)

    def test_record_and_verify_relocatable_cache(self):
        self.jar()
        self.record()
        relocated = self.directory / 'another-runner'
        relocated.mkdir()
        for name in ['ssc.jar', ssc.PROVENANCE]:
            shutil.move(str(self.directory / name), relocated / name)
        verified = ssc.verify(relocated, COMMIT)
        self.assertEqual(verified['jar_path'], str((relocated / 'ssc.jar').resolve()))
        self.assertNotIn('jar_path', json.loads((relocated / ssc.PROVENANCE).read_text('utf-8')))

    def test_same_version_different_commit_does_not_reuse_cache(self):
        self.jar()
        self.record()
        with self.assertRaisesRegex(ValueError, 'commit'):
            ssc.verify(self.directory, 'b' * 40)

    def test_same_version_replaced_bytes_are_rejected(self):
        jar = self.jar()
        self.record()
        with ZipFile(jar, 'a') as archive:
            archive.writestr('different-build.txt', 'same version, different code')
        with self.assertRaisesRegex(ValueError, 'sha256'):
            ssc.verify(self.directory, COMMIT)

    def test_duplicate_ssc_distributables_are_rejected(self):
        self.jar('ssc-one.jar')
        self.jar('ssc-two.jar')
        with self.assertRaisesRegex(ValueError, 'found 2'):
            ssc.inspect_jar(self.directory)

    def test_sources_and_dev_are_not_distributables(self):
        self.jar()
        self.jar('ssc-sources.jar')
        self.jar('ssc-dev.jar')
        self.assertEqual(ssc.inspect_jar(self.directory)['filename'], 'ssc.jar')

    def test_missing_ssc_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'found 0'):
            ssc.inspect_jar(self.directory)

    def test_named_access_widener_is_not_shipped(self):
        self.jar(namespace='named')
        with self.assertRaisesRegex(ValueError, 'widener'):
            ssc.inspect_jar(self.directory)

    def test_old_same_version_snapshot_is_rejected(self):
        self.jar(multi_eye=False)
        with self.assertRaisesRegex(ValueError, 'multi-eye'):
            ssc.inspect_jar(self.directory)

    def test_unresolved_version_is_rejected(self):
        self.jar(version='${version}')
        with self.assertRaisesRegex(ValueError, 'unresolved'):
            ssc.inspect_jar(self.directory)

    def test_renamed_cached_artifact_is_rejected(self):
        jar = self.jar()
        self.record()
        jar.rename(self.directory / 'renamed.jar')
        with self.assertRaisesRegex(ValueError, 'filename'):
            ssc.verify(self.directory, COMMIT)

    def test_outputs_do_not_accept_additional_lines(self):
        output = self.directory / 'output.txt'
        with self.assertRaises(ValueError):
            ssc.output({'version': '1.10.0\ncommit=unexpected'}, output)
        self.assertFalse(output.exists())


if __name__ == '__main__':
    unittest.main()
