"""Resolve, record and verify an SSC snapshot without launching Minecraft."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
from zipfile import BadZipFile, ZipFile

PROVENANCE = 'ssc-build.json'
SHA = re.compile(r'[0-9a-f]{40}')


def require_commit(commit: str) -> None:
    if not SHA.fullmatch(commit):
        raise ValueError('SSC commit must be a complete Git SHA')


def parse_remote(text: str) -> dict[str, str]:
    commit = branch = None
    for line in text.splitlines():
        left, _, target = line.partition('\t')
        if target == 'HEAD' and left.startswith('ref: refs/heads/'):
            branch = left.removeprefix('ref: refs/heads/')
        elif target == 'HEAD' and SHA.fullmatch(left):
            commit = left
    if not branch or not commit:
        raise ValueError('Cannot resolve the upstream default branch and HEAD')
    return {'commit': commit, 'branch': branch}


def resolve(repository: str) -> dict[str, str]:
    result = subprocess.run(['git', 'ls-remote', '--symref', repository, 'HEAD'],
                            check=True, capture_output=True, text=True, encoding='utf-8')
    return parse_remote(result.stdout)


def digest(path: Path) -> str:
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def inspect_jar(directory: Path) -> dict[str, str]:
    candidates = []
    for path in sorted(directory.glob('*.jar')):
        if path.name.endswith(('-sources.jar', '-javadoc.jar', '-dev.jar')):
            continue
        with ZipFile(path) as jar:
            if 'fabric.mod.json' not in jar.namelist():
                continue
            metadata = json.loads(jar.read('fabric.mod.json'))
            if metadata.get('id') != 'shape-shifter-curse':
                continue
            version = metadata.get('version')
            if not isinstance(version, str) or not version or '${' in version:
                raise ValueError('SSC JAR has an unresolved version')
            widener = jar.read(metadata['accessWidener']).decode('utf-8').splitlines()[0].split()
            if len(widener) != 3 or widener[0] != 'accessWidener' or widener[2] != 'intermediary':
                raise ValueError('SSC distributable access widener was not remapped')
            blink = jar.read('net/onixary/shapeShifterCurseFabric/render/form_render/sub_controller/FormEyeBlinkController.class')
            if not blink.startswith(b'\xca\xfe\xba\xbe') or b'EYE_ROOT_BONES' not in blink:
                raise ValueError('SSC snapshot does not provide the required multi-eye controller')
        candidates.append({'jar_path': str(path.resolve()), 'filename': path.name,
                           'version': version, 'sha256': digest(path)})
    if len(candidates) != 1:
        raise ValueError(f'Expected one distributable SSC JAR, found {len(candidates)}')
    return candidates[0]


def record(directory: Path, commit: str, repository: str) -> dict[str, str]:
    require_commit(commit)
    jar = inspect_jar(directory)
    data = {**jar, 'commit': commit, 'repository': repository}
    data.pop('jar_path')  # Runner-specific absolute paths do not belong in the cache.
    (directory / PROVENANCE).write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', 'utf-8')
    return {**jar, 'commit': commit, 'valid': 'true'}


def verify(directory: Path, commit: str) -> dict[str, str]:
    require_commit(commit)
    data = json.loads((directory / PROVENANCE).read_text('utf-8'))
    if data.get('commit') != commit:
        raise ValueError('Cached SSC commit does not match the resolved upstream HEAD')
    jar = inspect_jar(directory)
    for key in ('filename', 'version', 'sha256'):
        if data.get(key) != jar[key]:
            raise ValueError(f'Cached SSC {key} does not match its provenance')
    return {**jar, 'commit': commit, 'valid': 'true'}


def output(data: dict[str, str], path: Path | None) -> None:
    if any('\n' in str(value) or '\r' in str(value) for value in data.values()):
        raise ValueError('Invalid multiline workflow output')
    if path:
        with path.open('a', encoding='utf-8') as stream:
            for key, value in data.items():
                stream.write(f'{key}={value}\n')
    print(json.dumps(data, ensure_ascii=False))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    item = sub.add_parser('resolve')
    item.add_argument('--repository', required=True)
    item.add_argument('--github-output', type=Path)
    for command in ('record', 'verify'):
        item = sub.add_parser(command)
        item.add_argument('--directory', type=Path, required=True)
        item.add_argument('--commit', required=True)
        item.add_argument('--github-output', type=Path)
        if command == 'record':
            item.add_argument('--repository', required=True)
        else:
            item.add_argument('--allow-missing', action='store_true')
    args = parser.parse_args()
    if args.command == 'resolve':
        result = resolve(args.repository)
    elif args.command == 'record':
        result = record(args.directory, args.commit, args.repository)
    else:
        try:
            result = verify(args.directory, args.commit)
        except (OSError, ValueError, KeyError, IndexError, BadZipFile) as error:
            if not args.allow_missing:
                raise
            print(f'SSC cache unavailable: {error}')
            result = {'valid': 'false'}
    output(result, args.github_output)


if __name__ == '__main__':
    main()
