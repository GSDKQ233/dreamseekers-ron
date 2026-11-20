#!/usr/bin/env python3
"""Check parsed Forge metadata in both built JARs (Python 3.11+)."""
import argparse
from pathlib import Path
import tomllib
import zipfile

METADATA = 'META-INF/mods.toml'
CORE = 'reignofnether'
ADDON = 'dreamseekers'


def read_descriptor(path):
    with zipfile.ZipFile(path) as jar:
        entries = [entry for entry in jar.infolist() if entry.filename == METADATA]
        if len(entries) != 1:
            raise ValueError(f'{path.name}: expected exactly one {METADATA}')
        return tomllib.loads(jar.read(entries[0]).decode('utf-8'))


def require_mod(descriptor, mod_id, expected_version):
    mods = descriptor.get('mods')
    if not isinstance(mods, list) or len(mods) != 1 or not isinstance(mods[0], dict):
        raise ValueError(f'{mod_id}: expected exactly one [[mods]] entry')
    if mods[0].get('modId') != mod_id or mods[0].get('version') != expected_version:
        raise ValueError(f'{mod_id}: expected exact mod ID and version {expected_version!r}')


def verify_pair(core_jar, addon_jar, core_version, addon_version):
    core = read_descriptor(core_jar)
    addon = read_descriptor(addon_jar)
    require_mod(core, CORE, core_version)
    require_mod(addon, ADDON, addon_version)
    dependencies = addon.get('dependencies', {})
    rows = dependencies.get(ADDON, []) if isinstance(dependencies, dict) else None
    if not isinstance(rows, list) or any(not isinstance(row, dict) for row in rows):
        raise ValueError(f'{ADDON}: invalid dependencies table')
    match = [row for row in rows if row.get('modId') == CORE]
    if (len(match) != 1 or match[0].get('mandatory') is not True
            or match[0].get('side') != 'BOTH' or match[0].get('versionRange') != f'[{core_version}]'):
        raise ValueError(f'{ADDON}: expected one mandatory {CORE} dependency [{core_version}] on BOTH sides')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('core_jar', type=Path)
    parser.add_argument('addon_jar', type=Path)
    parser.add_argument('core_version')
    parser.add_argument('addon_version')
    args = parser.parse_args()
    try:
        verify_pair(args.core_jar, args.addon_jar, args.core_version, args.addon_version)
    except (OSError, zipfile.BadZipFile, ValueError) as exc:
        parser.exit(1, f'Paired JAR metadata check failed: {exc}\n')
    print(f'Paired JAR metadata verified: {args.core_version} / {args.addon_version}')


if __name__ == '__main__':
    main()
