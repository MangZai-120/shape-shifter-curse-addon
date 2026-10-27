"""Validate generated resources and local document links independently of Gradle."""
from pathlib import Path
import json
import re
import zipfile

root=Path(__file__).resolve().parents[1]
resources=root/'src/main/resources'
files=list(resources.rglob('*.json'))
for file in files:json.loads(file.read_text(encoding='utf-8'))
zh=json.loads((resources/'assets/ssca_neomagic/lang/zh_cn.json').read_text(encoding='utf-8'))
en=json.loads((resources/'assets/ssca_neomagic/lang/en_us.json').read_text(encoding='utf-8'))
assert zh.keys()==en.keys()
for key in zh:
    assert zh[key].count('%s')==en[key].count('%s'),key
    assert '\ufffd' not in zh[key] and '\ufffd' not in en[key],key
client=Path.home()/'.gradle/caches/fabric-loom/1.20.1/minecraft-client.jar'
with zipfile.ZipFile(client) as jar:
    names=set(jar.namelist())
    for file in (resources/'assets/ssca_neomagic/models/item').glob('*.json'):
        for texture in json.loads(file.read_text(encoding='utf-8')).get('textures',{}).values():
            namespace,path=texture.split(':')
            assert f'assets/{namespace}/textures/{path}.png' in names,(file,texture)
for file in (root/'docs').glob('*.md'):
    text=re.sub(r'```.*?```','',file.read_text(encoding='utf-8'),flags=re.S)
    for label,target in re.findall(r'\[([^\]]+)\]\(([^)]+)\)',text):
        if target.startswith(('http','C:','#')) or label.startswith('0,'):continue
        assert (file.parent/target.split('#')[0]).exists(),(file,target)
print(f'Resource review passed: {len(files)} JSON files, {len(zh)} bilingual keys, item textures and document links.')
