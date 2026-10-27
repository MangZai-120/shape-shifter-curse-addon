"""Generate explicit design defaults and vanilla-backed resources, without touching the old system."""
from pathlib import Path
import json
import re
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
REPO = ROOT.parent

def write(path, data):
    file = RES / path
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

cores = dict(zip("THERMAL FROST LUNAR CURSE SUMMON VOID SPACE".split(), "热蚀 降温 修复 附着 构成 侵蚀 连接".split()))
carriers = dict(zip("SELF CONTACT PROJECTILE AREA LOCK".split(), "自体 接触 投射 区域 锁定".split()))
controls = dict(zip("SPLIT MERGE STORE CONSTRAIN BRIDGE CONVERT".split(), "分流 汇合 储存 约束 跨越 转化".split()))
zh = {
    "group": "术式学研究", "title": "术式研究台", "page.0": "观测", "page.1": "构式", "page.2": "实验", "page.3": "笔记",
    "panel.0": "元件", "panel.1": "术式盘", "panel.2": "参数", "panel.3": "承载", "close": "关闭",
    "need_table": "请在术式研究台附近解析样本；编辑和制作需从研究台打开。",
    "target_help": "观测靶记录到达的作用量，生物实际伤害另行记录。用笔记比较基准与变式。",
    "blocked": "当前无法开始新术式：检查状态、现有施法及储存结构。", "invalid": "术式结构或请求无效。",
    "cooldown": "这个术式仍在冷却。", "target_invalid": "目标、射程或作用位置不符合条件。", "failed": "崩解失败",
    "unknown": "尚未掌握这份蓝图需要的作用核、载形或控制。", "empty_blueprint": "这是空白蓝图。",
    "materials": "制作需要纸 ×1、紫水晶碎片 ×1。", "need_blueprint": "先制作对应蓝图，再将它装载到背包中的导引杖。",
    "crafted": "蓝图已制作。", "equipped": "蓝图已装载到导引杖。", "discovered": "已解析：%s", "summon_name": "术式构成物",
    "hud_bar": "崩解 %s / 安全 %s · 超额 %s", "hud_risk": "负担 %s · 失败 %s · 偏移 %s",
    "hud_preparing": "准备 %s/%s tick · 已支付 %s", "hud_recovery": "恢复等待 %ss · CD %ss · 禁施法 %ss",
    "hud_deviation": "累计偏移：水平 %s° / 垂直 %s°", "stored_hint": "结构已储存：潜行使用导引杖释放，10秒失效。",
    "select_core": "作用核：%s", "select_carrier": "载形：%s", "add_control": "+ %s", "undo": "撤销末端", "clear_controls": "直通组织",
    "blueprint_name": "蓝图名称", "save_baseline": "保存基准", "save_variant": "保存变式", "craft": "制作蓝图", "equip": "装载导引杖",
    "scale_value": "作用规模 %s", "prep_value": "准备 %s tick", "range_value": "路径 %s 格", "next_a": "切换基准记录 A", "next_b": "切换变式记录 B",
    "cite": "引用 A/B，编写假说与结论", "save_notes": "保存笔记及引用", "hypothesis": "假说（可修改的解释）", "conclusion": "结论（注明适用条件）",
    "structure": "%s / %s / %s", "parameters": "规模 %s · 准备 %s tick · 路径 %s 格", "plain": "直通",
    "observation_intro": "观察、提出解释、改变一个变量，再比较结果。安全范围内可验证结构规律。", "known_count": "已掌握 %s 个元件：",
    "latest": "最新实测记录", "no_records": "还没有真实施法记录。用导引杖施放已制作的蓝图后返回查看。",
    "sample_help": "合成或探索取得样本，在研究台附近使用可解析元件。解析不直接给出唯一完整术式。",
    "to_craft": "进入构式", "to_records": "查看实测", "budget": "作用预算 %s · 终端 %s · 控制负荷 %s",
    "static_valid": "结构检查：%s", "valid": "合法", "quote": "正常负担 %s · 实际 %s · 身体适应 %s",
    "body": "当前崩解 %s · 安全阈值 %s · 超额 %s", "current_risk": "当前单次失败 %s · 偏移 %s", "form": "形态：%s",
    "safe_guarantee": "安全阈值以内，合法术式没有崩解失败或随机偏移。当前风险不代表整次累计概率。",
    "reference_profile": "这个形态使用明确标注的参考参数。", "record_pair": "正在比较第 %s / %s 条真实记录",
    "comparison_hint": "先检查条件差异。少量随机样本不能证明精确的未来概率。", "different_form": "条件不同：形态",
    "different_burden": "条件不同：起始崩解", "different_scale": "条件不同：作用规模", "different_prep": "条件不同：准备时长",
    "different_target": "条件不同：目标或路径", "different_version": "条件不同：规则版本", "record_a": "—— 基准 A ——", "record_b": "—— 变式 B ——",
    "notes_hint": "观察由服务器记录，不能手写替换。保存时引用实验页选中的 A/B。", "citations": "已保存引用：%s",
    "shared_help": "蓝图可分享结构与结论；不复制身体适应、材料或个人进度。", "scroll": "滚轮翻阅",
    "record_id": "记录 %s · 规则 %s", "result": "结果：%s", "record_cost": "起始 %s · 报价 %s · 实付 %s",
    "measured": "到达作用 %s · 扣血 %s · 治疗 %s · 命中 %s · 召唤 %s",
    "checkpoint": "%st：C=%s E=%s 失败%s 偏移%s；%s；累计%s°/%s°", "shifted": "产生偏移", "passed": "通过",
    "meaning.split": "分流：既有作用分给三路，不复制总量。", "meaning.merge": "汇合：缩小覆盖、提高集中程度；先分流再汇合另有损耗。",
    "meaning.store": "储存：保留85%作用，最多10秒；潜行使用导引杖触发。", "meaning.constrain": "约束：减小偏移概率与幅度，每层增加控制负荷。",
    "meaning.bridge": "跨越：允许路径扩展到64格，支付更长传导与结构代价。", "meaning.convert": "转化：热↔霜、修复→侵蚀，保留80%作用，目标载形仍需适配。",
}
en = {
    "group":"Spellcraft Research", "title":"Spellcraft Workbench", "page.0":"Observe", "page.1":"Compose", "page.2":"Experiment", "page.3":"Notes",
    "panel.0":"Components", "panel.1":"Diagram", "panel.2":"Parameters", "panel.3":"Burden", "close":"Close",
    "need_table":"Analyze samples near a spellcraft workbench. Open the workbench to edit or craft.",
    "target_help":"Targets measure arriving effect, separately from actual damage. Compare records in your notebook.",
    "blocked":"Cannot begin: check status, active cast and stored structure.", "invalid":"Invalid structure or request.", "cooldown":"This spell is cooling down.",
    "target_invalid":"Invalid target, range or position.", "failed":"Collapse failure", "unknown":"Required components have not been learned.",
    "empty_blueprint":"This blueprint is empty.", "materials":"Requires 1 paper and 1 amethyst shard.", "need_blueprint":"Craft a matching blueprint, then load it into a guiding wand in your inventory.",
    "crafted":"Blueprint crafted.", "equipped":"Wand loaded.", "discovered":"Analyzed: %s", "summon_name":"Spell Construct",
    "hud_bar":"Burden %s / Safe %s / Excess %s", "hud_risk":"Cost %s / Fail %s / Drift %s", "hud_preparing":"Prepare %s/%s ticks / Paid %s",
    "hud_recovery":"Recovery %ss / CD %ss / Blocked %ss", "hud_deviation":"Drift: yaw %s / pitch %s", "stored_hint":"Stored: sneak-use the wand to release; expires in 10s.",
    "select_core":"Core: %s", "select_carrier":"Carrier: %s", "add_control":"+ %s", "undo":"Undo", "clear_controls":"Clear", "blueprint_name":"Blueprint name",
    "save_baseline":"Save baseline", "save_variant":"Save variant", "craft":"Craft blueprint", "equip":"Load wand", "scale_value":"Scale %s", "prep_value":"Prepare %s ticks", "range_value":"Path %s blocks",
    "next_a":"Next baseline A", "next_b":"Next variant B", "cite":"Cite A/B and write notes", "save_notes":"Save notes and citations", "hypothesis":"Hypothesis (editable explanation)", "conclusion":"Conclusion (include conditions)",
    "structure":"%s / %s / %s", "parameters":"Scale %s / Prep %s ticks / Path %s", "plain":"Direct", "observation_intro":"Observe, explain, change one variable, then compare. Study structure within the safe range.",
    "known_count":"%s learned components:", "latest":"Latest measured record", "no_records":"No cast records yet. Cast a crafted blueprint with your wand, then return.",
    "sample_help":"Craft or explore for samples, then analyze them near a workbench. Samples do not reveal a unique complete recipe.", "to_craft":"Compose", "to_records":"Measured results",
    "budget":"Effect budget %s / Ends %s / Load %s", "static_valid":"Structure: %s", "valid":"Valid", "quote":"Normal %s / Actual %s / Adaptation %s", "body":"Burden %s / Safety %s / Excess %s",
    "current_risk":"Current check: fail %s / drift %s", "form":"Form: %s", "safe_guarantee":"Valid spells have no collapse failure or drift within safety. Current risk is not the whole-cast probability.",
    "reference_profile":"This form uses a labeled reference profile.", "record_pair":"Comparing measured records %s / %s", "comparison_hint":"Check conditions first. Small random samples do not prove an exact future rate.",
    "different_form":"Different condition: form", "different_burden":"Different condition: initial burden", "different_scale":"Different condition: scale", "different_prep":"Different condition: preparation",
    "different_target":"Different condition: target or path", "different_version":"Different condition: rules version", "record_a":"-- Baseline A --", "record_b":"-- Variant B --",
    "notes_hint":"Observations are server records. Saving cites the A/B records selected on the experiment page.", "citations":"Saved citations: %s", "shared_help":"Share structure and conclusions, never body adaptation, materials or personal progress.",
    "scroll":"Scroll", "record_id":"Record %s / Rules %s", "result":"Result: %s", "record_cost":"Initial %s / Quote %s / Paid %s",
    "measured":"Arrived %s / Damage %s / Heal %s / Hits %s / Summons %s", "checkpoint":"%st: C=%s E=%s fail %s drift %s; %s; total %s/%s", "shifted":"Drifted", "passed":"Passed",
    "meaning.split":"Split: distribute existing effect to three paths; no duplication.", "meaning.merge":"Merge: narrower coverage, concentrated output. Recombining split paths loses effect.",
    "meaning.store":"Store: retain 85%, up to 10 seconds. Sneak-use wand to trigger.", "meaning.constrain":"Constrain: reduce drift chance and angle at extra load.",
    "meaning.bridge":"Bridge: allow paths up to 64 blocks at added transmission and control cost.", "meaning.convert":"Convert: heat/frost, repair to erosion. Retain 80%; carrier must remain compatible.",
}
results = {"preparing":("准备中","Preparing"),"stored":("储存完成，等待触发","Stored; awaiting trigger"),"released":("效果已发出","Effect released"),"resolved":("效果已结算","Effect resolved"),"failed":("崩解失败","Collapse failure"),"cancelled":("主动取消","Cancelled"),"interrupted":("外部中断","Interrupted"),"form_changed":("变形中断","Form changed"),"disconnected":("断线中断","Disconnected"),"death":("死亡中断","Death"),"server_stopped":("服务器关闭中断","Server stopped"),"storage_lost":("储存结构失效","Stored structure lost"),"storage_expired":("储存超时","Storage expired")}
zh.update({"wand_help":"使用：施法；潜行使用：取消准备或触发储存。先在研究台装载蓝图。","notebook_help":"使用查看自己的观测、实验与笔记；编辑和制作请使用研究台。","blueprint_help":"使用导入结构。分享蓝图不转移身体适应；必须已掌握对应元件。","loaded":"已装载：%s","unnamed_blueprint":"未命名术式","unnamed_form":"未标定形态","sample_summary":"%s 同条件样本 %s 次；完全失败 %s 次（仅观察计数）","histogram":"偏移 %s：%s %s","bin.0":"0°","bin.1":"0–5°","bin.2":"5–15°","bin.3":">15°"})
en.update({"wand_help":"Use to cast. Sneak-use to cancel preparation or trigger storage. Load a blueprint at a workbench first.","notebook_help":"Use to view your observations, experiments and notes. Edit and craft at a workbench.","blueprint_help":"Use to import structure. Requires learned components and never transfers adaptation.","loaded":"Loaded: %s","unnamed_blueprint":"Unnamed spell","unnamed_form":"Uncalibrated form","sample_summary":"%s matched samples: %s; complete failures: %s (observed counts only)","histogram":"Drift %s: %s %s","bin.0":"0 degrees","bin.1":"0–5 degrees","bin.2":"5–15 degrees","bin.3":">15 degrees"})
results["target_lost"]=("目标条件已失效（普通中断）","Target lost (ordinary interruption)")
results["missed"]=("效果结束，未命中测量目标","Effect finished; no measured target hit")
results["effect_lost"]=("效果被外部生命周期清理","Effect removed by external lifecycle")
zh["absorption"]="实际消耗吸收量：%s";en["absorption"]="Absorption actually consumed: %s"
zh["shared_evidence"]="共享证据 %s 条（不计入个人实测或适应）";en["shared_evidence"]="%s shared evidence records (no personal adaptation or observations)"
zh["hud_deviation"]="已历风险 %s · 偏移 %s°/%s°";en["hud_deviation"]="Past risk %s / Drift %s/%s"
zh["evidence_summary"]="%s：起始%s / 负担%s / 到达%s";en["evidence_summary"]="%s: initial %s / cost %s / arrived %s"
errors={"name":("名称不能超过32字","Name exceeds 32 characters"),"scale":("作用规模必须在0.5至4之间","Scale must be 0.5 to 4"),"prep":("准备必须在0至80 tick之间","Preparation must be 0 to 80 ticks"),"range":("路径超限；超过32格需要跨越控制","Path exceeds capacity; bridge required beyond 32 blocks"),"carrier":("作用核不支持此载形","Core does not support this carrier"),"capacity":("最多6个控制节点","At most 6 control nodes"),"repeat":("储存、跨越、转化各最多1次","Store, bridge and convert are each limited to one"),"constraints":("约束最多2层","At most 2 constraint layers"),"conversion":("没有此作用核的转换适配","No conversion adapter for this core"),"branches":("终端超过6路","More than 6 terminals"),"self_split":("自体没有多路目标通路","Self carrier has no multiple target paths"),"summon_merge":("构成尚无集中对象适配","No concentrated summon adapter")}
for key,(cn,eng) in errors.items():zh["error."+key]=cn;en["error."+key]=eng
for key,(cn,eng) in results.items():zh["result."+key]=cn;en["result."+key]=eng
for group,values in [("core",cores),("carrier",carriers),("control",controls)]:
    for key,value in values.items():zh[group+"."+key.lower()]=value;en[group+"."+key.lower()]=key.title()
zh={"neomagic."+key:value for key,value in zh.items()};en={"neomagic."+key:value for key,value in en.items()}

items={"guiding_wand":("导引杖","Guiding Wand","blaze_rod"),"observation_notebook":("观测笔记","Observation Notebook","writable_book"),"blueprint":("术式蓝图","Spell Blueprint","paper"),"moon_dust_vial":("月尘试管","Moon Dust Vial","honey_bottle"),"moon_dust_bottle":("月尘瓶装药水","Moon Dust Bottle","potion"),"moon_dust_splash":("月尘支援药水","Moon Dust Support Flask","splash_potion")}
sample_materials={"THERMAL":"blaze_powder","FROST":"snowball","LUNAR":"glowstone_dust","CURSE":"fermented_spider_eye","SUMMON":"bone","VOID":"ender_pearl","SPACE":"chorus_fruit", "SELF":"apple","CONTACT":"stick","PROJECTILE":"arrow","AREA":"gunpowder","LOCK":"compass", "SPLIT":"quartz","MERGE":"iron_ingot","STORE":"glass_bottle","CONSTRAIN":"string","BRIDGE":"ender_pearl","CONVERT":"prismarine_crystals"}
for group,values,prefix in [("core",cores,"sample"),("carrier",carriers,"carrier"),("control",controls,"control")]:
    for key,cn in values.items():items[prefix+"_"+key.lower()]=(cn+"术式样本",key.title()+" Sample",sample_materials[key])
for key,(cn,eng,texture) in items.items():
    if texture=="compass":texture="compass_16"
    zh["item.ssca_neomagic."+key]=cn;en["item.ssca_neomagic."+key]=eng
    write("assets/ssca_neomagic/models/item/"+key+".json",{"parent":"minecraft:item/handheld" if key=="guiding_wand" else "minecraft:item/generated","textures":{"layer0":"minecraft:item/"+texture}})
for key,cn,eng,base in [("research_table","术式研究台","Spellcraft Workbench","enchanting_table"),("observation_target","术式观测靶","Spell Observation Target","target")]:
    zh["block.ssca_neomagic."+key]=cn;en["block.ssca_neomagic."+key]=eng
    write("assets/ssca_neomagic/blockstates/"+key+".json",{"variants":{"":{"model":"minecraft:block/"+base}}})
    write("assets/ssca_neomagic/models/item/"+key+".json",{"parent":"minecraft:block/"+base})
    write("data/ssca_neomagic/loot_tables/blocks/"+key+".json",{"type":"minecraft:block","pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"ssca_neomagic:"+key}],"conditions":[{"condition":"minecraft:survives_explosion"}]}]})
for key,cn,eng in [("disorder","魔力失调","Magic Disorder"),("disintegration","散解崩除","Disintegration"),("breakdown","身体崩解","Bodily Breakdown")]:zh["effect.ssca_neomagic."+key]=cn;en["effect.ssca_neomagic."+key]=eng
for index,key in enumerate(["disorder","disintegration","breakdown"]):
    icon=Image.new("RGBA",(18,18));draw=ImageDraw.Draw(icon)
    color=["#D6B76B","#C878A4","#EF6C75"][index]
    draw.line([(9,1),(16,5),(16,13),(9,17),(2,13),(2,5),(9,1)],fill=color,width=1)
    draw.line([(9,3),(6,8),(11,10),(8,15)],fill=color,width=2)
    if index>0:draw.line([(3,12),(7,11),(12,5),(15,6)],fill=color,width=1)
    if index>1:draw.rectangle((7,7,10,10),fill="#FFFFFF")
    target=RES/"assets/ssca_neomagic/textures/mob_effect"/(key+".png");target.parent.mkdir(parents=True,exist_ok=True);icon.save(target)
write("assets/ssca_neomagic/lang/zh_cn.json",zh);write("assets/ssca_neomagic/lang/en_us.json",en)

def recipe(name,ingredients,count=1):
    write("data/ssca_neomagic/recipes/"+name+".json",{"type":"minecraft:crafting_shapeless","ingredients":[{"item":item if ":" in item else "minecraft:"+item} for item in ingredients],"result":{"item":"ssca_neomagic:"+name,"count":count}})
recipe("guiding_wand",["stick","amethyst_shard","gold_ingot"])
recipe("observation_notebook",["book","amethyst_shard"])
recipe("research_table",["crafting_table","book","amethyst_shard","gold_ingot"])
recipe("observation_target",["target","amethyst_shard"])
for key in items:
    if key.startswith(("sample_","carrier_","control_")):recipe(key,["paper","amethyst_shard",items[key][2]])
recipe("moon_dust_vial",["glass_bottle","shape-shifter-curse:untreated_moondust","glowstone_dust"])
recipe("moon_dust_bottle",["ssca_neomagic:moon_dust_vial","glowstone_dust","glowstone_dust"])
recipe("moon_dust_splash",["ssca_neomagic:moon_dust_bottle","gunpowder"])
write("data/minecraft/tags/blocks/mineable/pickaxe.json",{"replace":False,"values":["ssca_neomagic:research_table"]})
write("data/minecraft/tags/blocks/mineable/axe.json",{"replace":False,"values":["ssca_neomagic:observation_target"]})

# Enumerate actual registered SSC and SSCA form IDs. No guessed group/skill IDs.
upstream=(REPO.parent/"幻型者诅咒原版_official/src/main/java/net/onixary/shapeShifterCurseFabric/player_form/RegPlayerForms.java").read_text(encoding="utf-8")
ids={"shape-shifter-curse:"+x for x in re.findall(r'registerPlayerForm\(.*?identifier\("([a-z0-9_]+)"\)',upstream)}
identifiers=(REPO/"src/main/java/net/jackcooper/shapeShifterCurseAddon/util/FormIdentifiers.java").read_text(encoding="utf-8")
mapping=dict(re.findall(r'Identifier\s+(\w+)\s*=\s*new Identifier\("my_addon",\s*"([a-z0-9_]+)"\)',identifiers))
addon=(REPO/"src/main/java/net/jackcooper/shapeShifterCurseAddon/forms/SscAddonForms.java").read_text(encoding="utf-8")
used=set(re.findall(r'new (?:Form_\w+|NormalForm)\(FormIdentifiers\.(\w+)\)',addon))
ids.update("my_addon:"+mapping[name] for name in used if name in mapping)
profiles={}
for form in sorted(ids):
    path=form.split(":")[1]
    if "familiar" in path:
        s,f,j=200,.8,.9
    elif "axolotl" in path:
        s,f,j=80,1.15,.85
    elif "allay" in path:
        s,f,j=70,.9,1.1
    elif "spider" in path:
        s,f,j=90,1.05,.8
    elif "bat" in path:
        s,f,j=85,1.08,.9
    elif "wolf" in path or "sandstorm" in path:
        s,f,j=120,1.12,1.05
    elif "original" in path:
        s,f,j=100,1,1
    else:s,f,j=95,1.02,.95
    stage=re.search(r"_(\d)$",path)
    if stage:
        rank=int(stage.group(1))
        s += (rank-3)*(20 if "familiar" in path else 5)
        f += (3-rank)*.025
        j += (3-rank)*.02
    if path=="upgrade_familiar_fox":s,f,j=220,.74,.86
    if path=="familiar_fox_red":s,f,j=185,.86,.94
    if path=="familiar_fox_mancianima":s,f,j=240,.7,.8
    if path=="upgrade_axolotl":s,f,j=90,1.1,.88
    # Explicit per-ID design defaults; human/axolotl/familiar anchors retained.
    profiles[form]={"safety":s,"failure":f,"offset":j,"merge":.8 if "familiar" in path else 1,"split":1.1 if "familiar" in path else 1}
costs={c:{"a":a,"b":b} for c,(a,b) in zip(cores,[(6,18),(5,15),(8,20),(4,12),(10,30),(12,36),(14,42)])}
write("data/ssca_neomagic/rules/defaults.json",{"version":"v2-playtest-1","recovery_delay":60,"recovery_per_tick":.25,"costs":costs,"spells":{},"forms":profiles})
print(f"Generated {len(profiles)} explicit form profiles, {len(items)} item models, and matching {len(zh)}-key localizations.")
