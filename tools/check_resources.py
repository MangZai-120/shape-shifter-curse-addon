"""Validate generated resources and local document links independently of Gradle."""
from pathlib import Path
import json
import re
import zipfile
import argparse
import os

root=Path(__file__).resolve().parents[1]
project=root
resources=root/'src/main/resources'

def check_document_links():
    failures=[]
    for file in (root/'docs/new-spell-system').rglob('*.md'):
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
args=parser.parse_args()
if args.mode=='docs':
    check_document_links()
    raise SystemExit(0)
files=list(resources.rglob('*.json'))
for file in files:json.loads(file.read_text(encoding='utf-8-sig'))
language_dir=project/'src/main/resources/assets/my_addon/lang'
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
    for file in list((resources/'assets/ssca_neomagic/models/item').glob('*.json')) + list((project/'src/main/resources/assets/ssc_addon/models/item').glob('test_tube*.json')) + list((project/'src/main/resources/assets/ssc_addon/models/item').glob('moon_dust_*.json')):
        for texture in json.loads(file.read_text(encoding='utf-8')).get('textures',{}).values():
            if texture.startswith('#'):continue
            namespace,path=texture.split(':')
            # 自定义贴图在附属资源目录；仅原版贴图从 Minecraft jar 读取。
            relative=f'assets/{namespace}/textures/{path}.png'
            assert (resources/relative).is_file() or relative in names,(file,texture)
print(f'Resource review passed: {len(files)} JSON files, {len(zh)} bilingual keys and item textures.')

# Check the actual registered forms, including temporary spell transformations.
java=project/'src/main/java/net/jackcooper/shapeShifterCurseAddon'
identifiers=dict(re.findall(r'Identifier\s+(\w+)\s*=\s*new Identifier\("my_addon",\s*"([^"]+)"\)',(java/'util/FormIdentifiers.java').read_text(encoding='utf-8')))
registered=set(re.findall(r'new\s+\w+\(FormIdentifiers\.(\w+)\)',(java/'forms/SscAddonForms.java').read_text(encoding='utf-8')))
assert registered
for language in ('zh_cn','en_us'):
    native=json.loads((project/f'src/main/resources/assets/my_addon/lang/{language}.json').read_text(encoding='utf-8-sig'))
    for constant in registered:
        for section in ('name','title','appearance','equip','pros','cons','instincts'):
            key=f'codex.form.my_addon.{identifiers[constant]}.{section}'
            assert key in native and native[key].strip(),(language,key)
            assert '\ufffd' not in native[key],(language,key)
print(f'Codex review passed: {len(registered)} registered forms, 7 sections, 2 languages; all translations use assets/my_addon/lang.')
tutorial_forms=set(re.findall(r'FormIdentifiers\.(\w+)',(java/'tutorial/FormTutorialCatalog.java').read_text(encoding='utf-8')))
assert tutorial_forms==registered-{'SHEEP_FORM'},(tutorial_forms,registered)
for constant in tutorial_forms:
    key=f'tutorial.ssc_addon.form.{identifiers[constant]}.skills'
    assert key in zh and zh[key].strip() and key in en and en[key].strip(),key
print(f'Form tutorial review passed: {len(tutorial_forms)} playable forms have bilingual skill lessons; temporary sheep excluded.')
if args.mode=='all':check_document_links()
