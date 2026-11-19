#!/usr/bin/env python3
"""Verify separately downloaded official JARs and prepare a private Maven cache.

This does not download, alter, embed or redistribute third-party content.
See dependencies.lock.json for official project pages and exact tested hashes.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
GROUP = 'local.dreamseekers.dependencies'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('downloads', type=Path, help='directory containing the original official JAR files')
    parser.add_argument('--repository', type=Path, default=ROOT / '.dreamseekers-deps' / 'maven')
    args = parser.parse_args()
    lock = json.loads((HERE / 'dependencies.lock.json').read_text())
    # Verify every input before copying any files.
    for dep in lock['dependencies']:
        src = args.downloads / dep['filename']
        actual = hashlib.sha256(src.read_bytes()).hexdigest()
        if actual != dep['sha256']:
            raise SystemExit(f"SHA256 mismatch: {src.name}; expected {dep['sha256']}, got {actual}")
    for dep in lock['dependencies']:
        artifact, version = dep['mavenArtifact'], dep['mavenVersion']
        target = args.repository / Path(*GROUP.split('.')) / artifact / version
        target.mkdir(parents=True, exist_ok=True)
        shutil.copy2(args.downloads / dep['filename'], target / f'{artifact}-{version}.jar')
        (target / f'{artifact}-{version}.pom').write_text(
            '<?xml version="1.0" encoding="UTF-8"?>\n'
            '<project xmlns="http://maven.apache.org/POM/4.0.0">\n'
            '  <modelVersion>4.0.0</modelVersion>\n'
            f'  <groupId>{GROUP}</groupId><artifactId>{artifact}</artifactId>\n'
            f'  <version>{version}</version><packaging>jar</packaging>\n'
            '</project>\n'
        )
        print(f"Verified {dep['filename']} -> {target}")
    print('Private dependency cache ready. Do not include it in release/source archives.')


if __name__ == '__main__':
    main()
