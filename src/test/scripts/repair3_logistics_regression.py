"""Offline Java behavior probe for manual deposits, live/cold NBT, rerouting, pickup and stored skull release.

Runs extracted, unchanged production methods against small Minecraft API doubles. Not a Forge run.
Run with JDK 17: python3 src/test/scripts/repair3_logistics_regression.py
"""
from pathlib import Path
from tempfile import TemporaryDirectory
import os
import subprocess

base = Path(__file__).resolve().parent
core = base.parents[2] / 'src/main/java/com/solegendary/reignofnether'
template = (base / 'fixtures/LogisticsProbe.java.in').read_text(encoding='utf8')
resources = 'resources/Resources.java'
unit = 'unit/interfaces/Unit.java'


def member(file, signature):
    src = (core / file).read_text(encoding='utf8')
    start = src.index(signature)
    opening = src.index('{', start)
    depth = 0
    for i in range(opening, len(src)):
        depth += (src[i] == '{') - (src[i] == '}')
        if depth == 0:
            return src[start:i + 1]
    raise ValueError(f'unterminated {signature} in {file}')


members = {
    'PRICING': [member(resources, x) for x in (
        'public int getTotalValue()', 'public void changeInstantly(', 'private void addItemValue(',
        'public static Resources getTotalResourcesFromItems(', 'public Resources depositCarriedItems(',
        'public Resources depositChestItems(', 'private Resources depositItemSlots(')],
    'ACCOUNT': [member('resources/ExtendedResourcesSync.java', 'public static Resources account(')],
    'MANUAL': [member('building/buildings/placements/ManualChestDeposit.java', 'static void collect(')],
    'CODEC': [member('resources/UnitCargoSaveData.java', 'public final class UnitCargoSaveData')],
    'ROUTE': [member('unit/goals/ReturnResourcesGoal.java', 'public class ReturnResourcesGoal')],
    'PICKUP': [member(unit, x) for x in (
        'private static void checkAndPickupResources(', 'public static void startEatingOrDrinking(',
        'private static void finishPickup(')],
    'RELEASE': [member('building/buildings/placements/GraveyardPlacement.java', 'public void releaseNextUnit()')],
}
for name, content in members.items():
    template = template.replace('/*' + name + '*/', '\n'.join(content))
with TemporaryDirectory() as tmp:
    src = Path(tmp) / 'LogisticsProbe.java'
    src.write_text(template, encoding='utf8')
    binary = lambda name: str(Path(os.environ['JAVA_HOME'])/'bin'/name) if os.environ.get('JAVA_HOME') else name
    subprocess.run([binary('javac'), '--release', '17', '-d', tmp, str(src)], check=True)
    subprocess.run([binary('java'), '-cp', tmp, 'LogisticsProbe'], check=True)
