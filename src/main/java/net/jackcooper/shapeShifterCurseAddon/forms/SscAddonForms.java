package net.jackcooper.shapeShifterCurseAddon.forms;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormPhase;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralCatSP;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_Ocelot3;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormGroup;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;

/**
 * SSCA 全部玩家形态注册（从 SscAddon.registerForms 拆分而来）。
 * 主类 onInitialize 通过 SscAddonForms.register() 调用。
 *
 * <p>SSC 1.9.2 分支适配说明：
 * <ul>
 *   <li>1.10 的 formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune) 在 1.9.2
 *       无字符串 flag 系统，映射为 setPhase(PHASE_SP)（SP 判定依据，月髓环爆炸/抑制剂拦截等
 *       逻辑等价）+ setIgnoreCursedMoon(true)（月变免疫）。本能（Instinct）在 1.9.2 仅对
 *       PHASE_0~2 阶梯形态生效，PHASE_SP 天然无本能条，无需额外开关。</li>
 *   <li>1.10 的 applyScale/applyScaleFunc 在 1.9.2 的 PlayerFormBase 无对应链路，体型缩放
 *       统一由数据层 shape-shifter-curse:scale power 承担（data/my_addon/powers/form_*_scale.json，
 *       1.9.2 与 1.10 同格式），origins json 已挂对应 power；缺失的形态见各注册处注释。</li>
 *   <li>组注册：1.9.2 为 new PlayerFormGroup(id).addForm(form, tier)（参数序与 1.10 相反）。</li>
 * </ul></p>
 */
public final class SscAddonForms {

	private SscAddonForms() {}

	/** 1.9.2 等价的 SP flag 组合（phase + 月变免疫）。 */
	private static void applySpFlags(PlayerFormBase form) {
		form.setPhase(PlayerFormPhase.PHASE_SP);
		form.setIgnoreCursedMoon(true);
	}

	/** 1.9.2 等价的 SP flag 组合 + 缓降（蝙蝠系）。 */
	private static void applySpFlagsSlowFall(PlayerFormBase form) {
		applySpFlags(form);
		form.setHasSlowFall(true);
	}

	public static void register() {
		Form_Axolotl3 axolotlForm = new Form_Axolotl3(FormIdentifiers.AXOLOTL_SP);
		applySpFlags(axolotlForm);
		// 美西螈SP为人形不缩放(scale=1.0)；缩放由数据层 form_axolotl_sp_scale power 承担
		RegPlayerForms.registerPlayerForm(axolotlForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_axolotl_sp")).addForm(axolotlForm, 5));

		// 进化美西螈（Upgrade Axolotl）- SSCA 进化加点路线起点形态，复用 SP 美西螈模型/动画，能力按进化树解锁
		Form_Axolotl3 upgradeAxolotlForm = new Form_Axolotl3(FormIdentifiers.UPGRADE_AXOLOTL);
		applySpFlags(upgradeAxolotlForm);
		RegPlayerForms.registerPlayerForm(upgradeAxolotlForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_upgrade_axolotl")).addForm(upgradeAxolotlForm, 5));

		// 荧光幼灵（Axolotl Fluorescent）- SP美西螈经进化石进化获得，复用美西螈模型/动画，体型缩小到 0.75
		Form_AxolotlFluorescent fluorescentForm = new Form_AxolotlFluorescent(FormIdentifiers.AXOLOTL_FLUORESCENT);
		applySpFlags(fluorescentForm);
		// 体型 0.75 由数据层 form_axolotl_fluorescent_scale power 承担
		RegPlayerForms.registerPlayerForm(fluorescentForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_axolotl_fluorescent")).addForm(fluorescentForm, 5));

		// 阿澪（Aling）- 特殊形态，基于荧光幼灵（技能完全一致），专属模型/贴图，颜色不可改。复用 Form_AxolotlFluorescent 类。
		Form_AxolotlFluorescent alingForm = new Form_AxolotlFluorescent(FormIdentifiers.AXOLOTL_ALING);
		applySpFlags(alingForm);
		// 体型 0.75 与荧光幼灵一致；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(alingForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_axolotl_aling")).addForm(alingForm, 5));

		Form_FamiliarFox3 familiarFoxForm = new Form_FamiliarFox3(FormIdentifiers.FAMILIAR_FOX_SP);
		applySpFlags(familiarFoxForm);
		// 使魔SP 体型 0.6 由数据层 form_familiar_fox_sp_scale power 承担
		RegPlayerForms.registerPlayerForm(familiarFoxForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_familiar_fox_sp")).addForm(familiarFoxForm, 5));

		// 进化使魔（复用使魔模型/动画，能力按进化解锁——批次2 形态骨架）
		Form_FamiliarFox3 upgradeFamiliarFoxForm = new Form_FamiliarFox3(FormIdentifiers.UPGRADE_FAMILIAR_FOX);
		applySpFlags(upgradeFamiliarFoxForm);
		// 进化使魔体型 0.55 由数据层 form_upgrade_familiar_fox_scale power 承担
		RegPlayerForms.registerPlayerForm(upgradeFamiliarFoxForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_upgrade_familiar_fox")).addForm(upgradeFamiliarFoxForm, 5));

		// 契灵（Mancianima）—— 复用使魔模型/动画，经月髓环/进化石进化获得。
		// 之前是纯数据驱动(ssc_form json)，但原版新版 DynamicForm 缺 originLayerID 字段会 NPE 致其注册失败消失，
		// 故改为与其它 SP 形态一致的代码注册（不再依赖数据驱动），模型由 FormID 查 ssc_form_model 自动得到契灵外观。
		Form_FamiliarFox3 mancianimaForm = new Form_FamiliarFox3(FormIdentifiers.FAMILIAR_FOX_MANCIANIMA);
		applySpFlags(mancianimaForm);
		// 契灵体型 0.55（对齐 FAMILIAR_FOX_3）；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(mancianimaForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_familiar_fox_mancianima")).addForm(mancianimaForm, 5));

		Form_FamiliarFoxRed familiarFoxRedForm = new Form_FamiliarFoxRed(FormIdentifiers.FAMILIAR_FOX_RED);
		applySpFlags(familiarFoxRedForm);
		// 红狐体型 0.65 由数据层 form_familiar_fox_red_scale power 承担
		RegPlayerForms.registerPlayerForm(familiarFoxRedForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_familiar_fox_red")).addForm(familiarFoxRedForm, 5));

		Form_SnowFoxSP snowFoxForm = new Form_SnowFoxSP(FormIdentifiers.SNOW_FOX_SP);
		applySpFlags(snowFoxForm);
		// 雪狐SP 体型 0.55；1.9.2 需补 scale power 数据（对齐原版 SNOW_FOX_3）
		RegPlayerForms.registerPlayerForm(snowFoxForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_snow_fox_sp")).addForm(snowFoxForm, 7));

		// 寒棘狐（Frostspine）- 雪狐线月髓环进化形态（原版雪狐 snow_fox_3 经月髓环进化），复用原版雪狐模型/贴图，能力完全等同原版雪狐
		Form_SnowFoxSP frostspineForm = new Form_SnowFoxSP(FormIdentifiers.SNOW_FOX_FROSTSPINE);
		applySpFlags(frostspineForm);
		// 寒棘狐体型 0.55 与雪狐SP 一致；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(frostspineForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_snow_fox_frostspine")).addForm(frostspineForm, 7));

		Form_Allay allayForm = new Form_Allay(FormIdentifiers.ALLAY_SP);
		applySpFlags(allayForm);
		// 悦灵缩放 0.55/1.0 由数据层 form_allay_sp_scale power 承担
		RegPlayerForms.registerPlayerForm(allayForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_allay_sp")).addForm(allayForm, 8));

		Form_FeralCatSP wildCatForm = new Form_FeralCatSP(FormIdentifiers.WILD_CAT_SP);
		applySpFlags(wildCatForm);
		wildCatForm.setCanSneakRush(true);
		// 野猫体型 0.55（对齐原版 form_feral_cat_sp_scale）；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(wildCatForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_wild_cat_sp")).addForm(wildCatForm, 5));

		// 食梦魔（Nightmare）- 野猫线月髓环进化形态（原版野猫 feral_cat_sp 经月髓环进化），复用月光魅影野猫模型/贴图
		// 被动与月光魅影对齐（不含真隐身/震慑冲刺两个主动）；核心被动「入梦」：累计 10 伤害 → 敌方入梦 20s，
		// 期间入梦敌对其施加的 debuff 全无效（含 STUN），食梦魔看入梦敌有粉红描边
		Form_FeralCatSP nightmareForm = new Form_FeralCatSP(FormIdentifiers.WILD_CAT_NIGHTMARE);
		applySpFlags(nightmareForm);
		nightmareForm.setCanSneakRush(true);
		// 食梦魔体型 0.55 与月光魅影一致；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(nightmareForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_wild_cat_nightmare")).addForm(nightmareForm, 5));

		// 风灵（月髓环豹猫）——完全复用原版豹猫 Form_Ocelot3 的模型与动画，四足兽形，可疾跑；核心为「疾风连爪」左键连击技能
		Form_Ocelot3 ocelotSpForm = new Form_Ocelot3(FormIdentifiers.OCELOT_SP);
		applySpFlags(ocelotSpForm);
		// 标记为 FERAL 四足兽体——原版 AdjustItemHoldFeatureRendererMixin/MouthItemFeature 依此把副手物品渲染到背上而非手臂
		ocelotSpForm.setBodyType(PlayerFormBodyType.FERAL);
		// 风灵体型 0.75/0.6（对齐原版 OCELOT_3）；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(ocelotSpForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_ocelot_wind_spirit")).addForm(ocelotSpForm, 5));

		// 朔望（月髓环豹猫）——与风灵同源，复用原版豹猫 Form_Ocelot3 模型动画，四足兽形；定位九命灵猫（生存/不死）
		Form_Ocelot3 ocelotNovaForm = new Form_Ocelot3(FormIdentifiers.OCELOT_NOVA);
		applySpFlags(ocelotNovaForm);
		// 标记为 FERAL 四足兽体——与风灵同理，触发原版副手→背渲染
		ocelotNovaForm.setBodyType(PlayerFormBodyType.FERAL);
		// 朔望体型 0.75/0.6 与风灵相同；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(ocelotNovaForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_ocelot_nova")).addForm(ocelotNovaForm, 5));

		// Fallen Allay SP
		Form_FallenAllaySP fallenAllayForm = new Form_FallenAllaySP(FormIdentifiers.FALLEN_ALLAY_SP);
		applySpFlags(fallenAllayForm);
		// 堕落悦灵缩放 0.55/1.0（对齐 ALLAY_SP）；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(fallenAllayForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_fallen_allay_sp")).addForm(fallenAllayForm, 8));

		// Anubis Wolf SP
		Form_AnubisWolfSP anubisWolfForm = new Form_AnubisWolfSP(FormIdentifiers.ANUBIS_WOLF_SP);
		applySpFlags(anubisWolfForm);
		anubisWolfForm.setCanSneakRush(true);
		// 阿努比斯之狼体型 0.8/0.6（对齐 form_anubis_wolf_3_scale）；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(anubisWolfForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_anubis_wolf_sp")).addForm(anubisWolfForm, 12));

		// Golden Sandstorm SP (金沙岚)
		Form_GoldenSandstormSP goldenSandstormForm = new Form_GoldenSandstormSP(FormIdentifiers.GOLDEN_SANDSTORM_SP);
		applySpFlags(goldenSandstormForm);
		goldenSandstormForm.setCanSneakRush(true);
		// 金沙岚复用阿努比斯之狼四足模型，缩放 0.8/0.6 与 ANUBIS_WOLF_3 一致；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(goldenSandstormForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_golden_sandstorm_sp")).addForm(goldenSandstormForm, 12));

		// 吸血蝙蝠（Desmodus）SP形态 - 复用蝙蝠模型/动画，经月髓环在诅咒之月夜进化获得
		Form_BatDesmodus batDesmodusForm = new Form_BatDesmodus(FormIdentifiers.BAT_DESMODUS);
		applySpFlagsSlowFall(batDesmodusForm);
		// 蝙蝠缩放 0.6/0.7 需与原版 bat_3 一致；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(batDesmodusForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_bat_desmodus")).addForm(batDesmodusForm, 12));

		// 月织蛛（Moon Weaver）SP形态 - 复用原版蜘蛛三阶段模型/动画，经月髓环在诅咒之月夜进化获得
		// 被动与特性完全与原版 spider_3 平齐（爬墙、吐丝、搭桥、毒素免疫、夜视等）
		Form_SpiderMoonWeaver SpiderMoonWeaverForm = new Form_SpiderMoonWeaver(FormIdentifiers.SPIDER_MOON_WEAVER);
		applySpFlags(SpiderMoonWeaverForm);
		// 月织蛛缩放 0.81/1.0（原版 spider_3 的 90%）；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(SpiderMoonWeaverForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_spider_moon_weaver")).addForm(SpiderMoonWeaverForm, 12));

		// 跳蛛（Salticidae）SP形态 - 复用原版蜘蛛三阶段模型/动画，经进化石从 spider_3 进化获得
		// 与月髓环→月织蛛并行（同源不同道具，不冲突）；被动与特性完全与原版 spider_3 平齐
		Form_SpiderSalticidae SpiderSalticidaeForm = new Form_SpiderSalticidae(FormIdentifiers.SPIDER_SALTICIDAE);
		applySpFlags(SpiderSalticidaeForm);
		// 跳蛛体格 0.6/1.0（正常玩家 60%）；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(SpiderSalticidaeForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_spider_salticidae")).addForm(SpiderSalticidaeForm, 12));

		// 寄生果蝠 - 原版三阶段蝙蝠使用进化石进化获得，复用蝙蝠模型/动画
		Form_BatParasiticFruit batParasiticFruitForm = new Form_BatParasiticFruit(FormIdentifiers.BAT_PARASITIC_FRUIT);
		applySpFlagsSlowFall(batParasiticFruitForm);
		// 蝙蝠缩放 0.6/0.7 需与原版 bat_3 一致；1.9.2 需补 scale power 数据
		RegPlayerForms.registerPlayerForm(batParasiticFruitForm);
		RegPlayerForms.registerPlayerFormGroup(new PlayerFormGroup(new Identifier("my_addon", "group_bat_parasitic_fruit")).addForm(batParasiticFruitForm, 12));
	}
}
