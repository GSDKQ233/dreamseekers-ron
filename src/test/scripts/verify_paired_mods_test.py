"""Offline checks for the paired-JAR metadata checker; use Python 3.11+."""
import importlib.util
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
import warnings
import zipfile

module_path = Path(__file__).resolve().parents[3] / 'addons/dreamseekers/verify_paired_mods.py'
spec = importlib.util.spec_from_file_location('verify_paired_mods', module_path)
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)

CORE = 'core-repair3'
ADDON = 'addon-repair3'
CORE_TOML = f'[[mods]]\nmodId="reignofnether"\nversion="{CORE}"\n'
ADDON_TOML = f"""[[mods]]
modId="dreamseekers"
version="{ADDON}"

[[dependencies.dreamseekers]]
modId="reignofnether"
mandatory=true
versionRange="[{CORE}]"
side="BOTH"
"""


class VerifyPairedMods(unittest.TestCase):
    def verify(self, core=CORE_TOML, addon=ADDON_TOML, *, duplicate=False):
        with TemporaryDirectory() as directory:
            a, b = (Path(directory) / name for name in ('core.jar', 'addon.jar'))
            with zipfile.ZipFile(a, 'w') as jar:
                jar.writestr(verify.METADATA, core)
            with zipfile.ZipFile(b, 'w') as jar:
                jar.writestr(verify.METADATA if addon else 'other.toml', addon)
                if duplicate:
                    with warnings.catch_warnings():
                        warnings.simplefilter('ignore', UserWarning)
                        jar.writestr(verify.METADATA, addon)
            verify.verify_pair(a, b, CORE, ADDON)

    def test_exact_pair(self):
        self.verify()

    def test_wrong_core_version_even_with_expected_text_in_comment(self):
        with self.assertRaisesRegex(ValueError, 'exact mod ID and version'):
            self.verify(core=CORE_TOML.replace(f'version="{CORE}"', f'version="wrong" # {CORE}'))

    def test_wrong_addon_version(self):
        with self.assertRaisesRegex(ValueError, 'exact mod ID and version'):
            self.verify(addon=ADDON_TOML.replace(ADDON, ADDON + '-other'))

    def test_wider_core_dependency(self):
        with self.assertRaisesRegex(ValueError, 'mandatory reignofnether'):
            self.verify(addon=ADDON_TOML.replace(f'[{CORE}]', f'[{CORE},)'))

    def test_optional_and_client_only(self):
        for addon in (ADDON_TOML.replace('mandatory=true', 'mandatory=false'),
                      ADDON_TOML.replace('side="BOTH"', 'side="CLIENT"')):
            with self.subTest(addon=addon), self.assertRaises(ValueError):
                self.verify(addon=addon)

    def test_duplicate_core_dependency(self):
        with self.assertRaises(ValueError):
            self.verify(addon=ADDON_TOML + ADDON_TOML[ADDON_TOML.index('[[dependencies.'):])

    def test_missing_and_duplicate_descriptor(self):
        with self.assertRaisesRegex(ValueError, 'exactly one'):
            self.verify(addon='')
        with self.assertRaisesRegex(ValueError, 'exactly one'):
            self.verify(duplicate=True)


if __name__ == '__main__':
    unittest.main()
