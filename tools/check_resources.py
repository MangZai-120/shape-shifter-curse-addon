"""Validate generated resources and local document links independently of Gradle."""
from collections import Counter
from pathlib import Path
import json
import re
import zipfile
import argparse
import os

root=Path(__file__).resolve().parents[1]
project=root
resources=root/'src/main/resources'
LEGACY_NAMES = {'my_addon', 'ssca_neomagic'}
LEGACY_MIXINS = {name + '.mixins.json' for name in LEGACY_NAMES}


def check_packaged_resources(jar_path, resource_files):
    expected = {name: path for name, path in resource_files.items() if name.startswith(('assets/', 'data/'))}
    descriptor = json.loads(resource_files['fabric.mod.json'].read_text(encoding='utf-8-sig'))
    mixins = {entry if isinstance(entry, str) else entry['config'] for entry in descriptor.get('mixins', [])}
    with zipfile.ZipFile(jar_path) as jar:
        counts = Counter(info.filename for info in jar.infolist() if not info.is_dir())
        packaged = {name for name in counts if name.startswith(('assets/', 'data/'))}
        missing = (expected.keys() | mixins | {'fabric.mod.json'}) - counts.keys()
        extra = (packaged - expected.keys()) | (LEGACY_MIXINS & counts.keys())
        changed = {name for name in packaged & expected.keys() if jar.read(name) != expected[name].read_bytes()}
        duplicates = {name for name in packaged | mixins | {'fabric.mod.json'} if counts[name] > 1}
        if 'fabric.mod.json' in counts:
            archived = json.loads(jar.read('fabric.mod.json'))
            if any(archived.get(field) != descriptor.get(field) for field in ('id', 'icon', 'mixins')):
                changed.add('fabric.mod.json')
    problems = []
    for kind, names in [('missing', missing), ('unexpected', extra), ('changed', changed), ('duplicate', duplicates)]:
        problems.extend(f'{kind}: {name}' for name in sorted(names))
    if problems:
        raise SystemExit(f'Packaged resources differ in {jar_path}:\n' + '\n'.join(problems))
    print(f'Packaged resources passed: {len(packaged)} files match the source tree in {jar_path.name}.')


def check_document_links():
    failures=[]
    overview=root/'docs/spell-system.md'
    assert overview.is_file(),overview
    documents=[overview,root/'docs/plans/异常度系统建议表.md',
               root/'docs/plans/SSCA新法术系统设计方案.md',root/'docs/plans/SSCA崩解度法术系统设计方案.md']
    documents.extend((root/'docs/testing').glob('*法术*.md'))
    for file in documents:
        if not file.is_file():continue
        text=re.sub(r'```.*?```','',file.read_text(encoding='utf-8'),flags=re.S)
        for label,target in re.findall(r'\[([^\]]+)\]\(([^)]+)\)',text):
            if target.startswith(('http','C:','#')) or label.startswith('0,'):continue
            if not (file.parent/target.split('#')[0]).exists():failures.append((file,target))
    for file,target in failures:print(f'Document link failed: {file}: {target}')
    if failures:raise SystemExit(1)
    print('Local document links passed.')

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('mode',nargs='?',choices=('resources','docs','all'),default='resources',
                    help='resources (default), local archive docs, or both')
parser.add_argument('--minecraft-client',type=Path,
                    help='Minecraft client JAR; overrides the GRADLE_USER_HOME cache')
parser.add_argument('--addon-jar', type=Path, help='also compare the built addon JAR with the source assets and data')
args=parser.parse_args()
if args.mode=='docs':
    if args.addon_jar:
        parser.error('--addon-jar is available in resources and all modes')
    check_document_links()
    raise SystemExit(0)
# Keep legacy identifiers in save migration code and fixtures only; shipped resources use one namespace.
legacy_id = re.compile(r'(?<![a-z0-9_.:/-])(?:my_addon|ssca_neomagic):[a-z0-9_./-]+')
asset_reference = re.compile(r'"ssc_addon:((?:textures|geo|animations|player_animation)/[^"\\]+\.(?:png|json))"')
resource_files = {file.relative_to(resources).as_posix(): file for file in resources.rglob('*') if file.is_file()}
json_count = 0
for relative, file in resource_files.items():
    parts = relative.split('/')
    if len(parts) >= 3 and parts[0] in ('assets', 'data'):
        assert parts[1] not in LEGACY_NAMES, relative
        if parts[0] == 'assets' and (parts[2] == 'ssc_form_model' or parts[1:3] == ['orif-defaults', 'furs']):
            assert LEGACY_NAMES.isdisjoint(parts[-1].split('.')), relative
    assert relative not in LEGACY_MIXINS, relative
    if file.suffix not in ('.json', '.mcmeta'):
        continue
    document = json.loads(file.read_text(encoding='utf-8-sig'))
    if file.suffix == '.json':
        json_count += 1
    text = json.dumps(document, ensure_ascii=False)
    obsolete = legacy_id.search(text)
    assert not obsolete, (relative, obsolete.group())
    for path in asset_reference.findall(text):
        assert 'assets/ssc_addon/' + path in resource_files, (relative, path)
if args.addon_jar:
    if not args.addon_jar.is_file() or not zipfile.is_zipfile(args.addon_jar):
        parser.error(f'Addon JAR not found or invalid: {args.addon_jar}')
    check_packaged_resources(args.addon_jar, resource_files)
language_dir=project/'src/main/resources/assets/ssc_addon/lang'
zh=json.loads((language_dir/'zh_cn.json').read_text(encoding='utf-8-sig'))
en=json.loads((language_dir/'en_us.json').read_text(encoding='utf-8-sig'))
language_files=set((resources/'assets').glob('*/lang/*.json'))
assert language_files=={language_dir/'zh_cn.json',language_dir/'en_us.json'},language_files
assert zh.keys()==en.keys()
for file in (project/'src/main/resources/data/ssc_addon/spells').glob('*.json'):
    spell=json.loads(file.read_text(encoding='utf-8-sig'))
    assert type(spell.get('debuff')) is bool,(file,'explicit debuff metadata required')
    if any(level.get('rarity')=='red' for level in spell.get('levels',[])):assert not spell['debuff'],file
for key in zh:
    assert zh[key].count('%s')==en[key].count('%s'),key
    assert '\ufffd' not in zh[key] and '\ufffd' not in en[key],key
gradle_home=Path(os.environ.get('GRADLE_USER_HOME') or Path.home()/'.gradle').expanduser()
client=(args.minecraft_client or gradle_home/'caches/fabric-loom/1.20.1/minecraft-client.jar').expanduser()
if not client.is_file():
    parser.error(f'Minecraft client JAR not found: {client}; build first or pass --minecraft-client PATH')
with zipfile.ZipFile(client) as jar:
    names=set(jar.namelist())
    for file in (resources/'assets/ssc_addon/models/item').glob('*.json'):
        if not file.name.startswith(('test_tube', 'moon_dust_')):continue
        for texture in json.loads(file.read_text(encoding='utf-8')).get('textures',{}).values():
            if texture.startswith('#'):continue
            namespace,path=texture.split(':')
            # 自定义贴图在附属资源目录；仅原版贴图从 Minecraft jar 读取。
            relative=f'assets/{namespace}/textures/{path}.png'
            assert (resources/relative).is_file() or relative in names,(file,texture)
print(f'Resource review passed: {json_count} JSON files, {len(zh)} bilingual keys and item textures.')

# Check the actual registered forms, including temporary spell transformations.
java=project/'src/main/java/net/jackcooper/shapeShifterCurseAddon'
for source in java.rglob('*.java'):
    text=source.read_text(encoding='utf-8')
    for namespace,path in re.findall(r'RegistryKeys\.DAMAGE_TYPE[\s\S]{0,180}?new (?:net\.minecraft\.util\.)?Identifier\("([\w.-]+)",\s*"([\w/.-]+)"\)',text):
        if namespace!='minecraft':assert (resources/'data'/namespace/'damage_type'/(path+'.json')).is_file(),(source,namespace,path)
identifiers=dict(re.findall(r'Identifier\s+(\w+)\s*=\s*new Identifier\("ssc_addon",\s*"([^"]+)"\)',(java/'util/FormIdentifiers.java').read_text(encoding='utf-8')))
registered=set(re.findall(r'new\s+\w+\(FormIdentifiers\.(\w+)\)',(java/'forms/SscAddonForms.java').read_text(encoding='utf-8')))
assert registered
for language in ('zh_cn','en_us'):
    native=json.loads((project/f'src/main/resources/assets/ssc_addon/lang/{language}.json').read_text(encoding='utf-8-sig'))
    for constant in registered:
        for section in ('name','title','appearance','equip','pros','cons','instincts'):
            key=f'codex.form.ssc_addon.{identifiers[constant]}.{section}'
            assert key in native and native[key].strip(),(language,key)
            assert '\ufffd' not in native[key],(language,key)
print(f'Codex review passed: {len(registered)} registered forms, 7 sections, 2 languages; all translations use assets/ssc_addon/lang.')
tutorial_forms=set(re.findall(r'FormIdentifiers\.(\w+)',(java/'tutorial/FormTutorialCatalog.java').read_text(encoding='utf-8')))
playable = registered - {'SHEEP_FORM'}
missing_tutorials = playable - tutorial_forms
assert tutorial_forms <= playable, ('Unexpected tutorial forms', sorted(tutorial_forms - playable))
assert missing_tutorials <= {'UPGRADE_SNOW_FOX'}, ('Missing form tutorials', sorted(missing_tutorials))
if missing_tutorials:
    print('Tutorial pending: UPGRADE_SNOW_FOX (evolved snow fox).')
lessons_data=json.loads((project/'src/main/resources/assets/ssc_addon/tutorial/form_lessons.json').read_text(encoding='utf-8'))
assert lessons_data['schema_version']==1
assert set(lessons_data['forms'])=={'ssc_addon:'+identifiers[constant] for constant in tutorial_forms}
used_lessons=set()
for form,pages in lessons_data['forms'].items():
    assert pages and len(pages)==len(set(pages)),form
    for page in pages:
        assert page in lessons_data['lessons'],(form,page)
        used_lessons.add(page)
assert used_lessons==set(lessons_data['lessons']), 'Unreachable tutorial lesson'
for page,lesson in lessons_data['lessons'].items():
    assert lesson['kind'] in {'ACTIVE','PASSIVE','TRAIT'},page
    assert lesson['focus'] in {'SELF','TARGET','ALLY','MINIONS'},page
    assert lesson['diagram'] in {'PROJECTILE','AREA','DASH','HEAL','SHIELD','SUMMON','RESOURCE','STEALTH','MARK','WEB','FLIGHT','MELEE','EVOLUTION','CHAIN','WEATHER','SPELL'},page
    namespace,path=lesson['icon'].split(':',1)
    assert (project/'src/main/resources/assets'/namespace/path).is_file(),(page,lesson['icon'])
    for section in ('title','trigger','effect','note'):
        key=f'tutorial.ssc_addon.form.lesson.{page}.{section}'
        assert key in zh and zh[key].strip() and key in en and en[key].strip(),key
        assert set(re.findall(r'\{(\w+)\}',zh[key]))==set(re.findall(r'\{(\w+)\}',en[key])),key
        assert set(re.findall(r'\{(\w+)\}',zh[key]))<={'primary','secondary','sneak','attack','use','jump','sprint'},key
        assert '\n' not in zh[key] and '\n' not in en[key],key
print(f'Form tutorial review passed: {len(tutorial_forms)} playable forms, {len(used_lessons)} illustrated lessons, bilingual operation/effect/tip text; temporary sheep excluded.')

if args.mode=='all':check_document_links()
