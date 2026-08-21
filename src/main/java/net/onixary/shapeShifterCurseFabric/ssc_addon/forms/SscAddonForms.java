package net.onixary.shapeShifterCurseFabric.ssc_addon.forms;

import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.NormalForm;
import net.onixary.shapeShifterCurseFabric.player_form.NormalGroup;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBodyType;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_FeralCatSP;
import net.onixary.shapeShifterCurseFabric.player_form.forms.Form_Ocelot3;
import net.onixary.shapeShifterCurseFabric.ssc_addon.util.FormIdentifiers;

import static net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.NoInstinct;
import static net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.NoCursedMoonEffect;
import static net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.SpecialForm;
import static net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.InhibitorImmune;
import static net.onixary.shapeShifterCurseFabric.player_form.utils.FormUtils.HasSlowFall;

/**
 * SSCA 全部玩家形态注册（从 SscAddon.registerForms 拆分而来）。
 * 主类 onInitialize 通过 SscAddonForms.register() 调用。
 */
public final class SscAddonForms {

	private SscAddonForms() {}

	public static void register() {
		Form_Axolotl3 axolotlForm = new Form_Axolotl3(FormIdentifiers.AXOLOTL_SP);
		axolotlForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 美西螈SP为人形不缩放(scale=1.0)，但仍需 RESET 兜底清除变身前残留的缩放值
		axolotlForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(1.0f, 1.0f));
		RegPlayerForms.registerPlayerForm(axolotlForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_axolotl_sp")).registerForm(1, 5, axolotlForm));

		// 进化美西螈（Upgrade Axolotl）- SSCA 进化加点路线起点形态，复用 SP 美西螈模型/动画，能力按进化树解锁
		Form_Axolotl3 upgradeAxolotlForm = new Form_Axolotl3(FormIdentifiers.UPGRADE_AXOLOTL);
		upgradeAxolotlForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		upgradeAxolotlForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(1.0f, 1.0f));
		RegPlayerForms.registerPlayerForm(upgradeAxolotlForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_upgrade_axolotl")).registerForm(1, 5, upgradeAxolotlForm));

		// 荧光幼灵（Axolotl Fluorescent）- SP美西螈经进化石进化获得，复用美西螈模型/动画，体型缩小到 0.75
		Form_AxolotlFluorescent fluorescentForm = new Form_AxolotlFluorescent(FormIdentifiers.AXOLOTL_FLUORESCENT);
		fluorescentForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 体型等比缩小到 0.75（宽高/眼高/碰撞箱一致），兜底清除变身前残留缩放
		fluorescentForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.75f, 0.75f));
		RegPlayerForms.registerPlayerForm(fluorescentForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_axolotl_fluorescent")).registerForm(1, 5, fluorescentForm));

		// 阿澪（Aling）- 特殊形态，基于荧光幼灵（技能完全一致），专属模型/贴图，颜色不可改。复用 Form_AxolotlFluorescent 类。
		Form_AxolotlFluorescent alingForm = new Form_AxolotlFluorescent(FormIdentifiers.AXOLOTL_ALING);
		alingForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		alingForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.75f, 0.75f));
		RegPlayerForms.registerPlayerForm(alingForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_axolotl_aling")).registerForm(1, 5, alingForm));

		Form_FamiliarFox3 familiarFoxForm = new Form_FamiliarFox3(FormIdentifiers.FAMILIAR_FOX_SP);
		familiarFoxForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 四足形态变身后重置玩家缩放到本形态大小（原版体型上调后，使魔SP 同步放大到 0.6）
		familiarFoxForm.applyScale(0.6f, 0.6f);

		RegPlayerForms.registerPlayerForm(familiarFoxForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_familiar_fox_sp")).registerForm(1, 5, familiarFoxForm));

		// 进化使魔（复用使魔模型/动画，能力按进化解锁——批次2 形态骨架）
		Form_FamiliarFox3 upgradeFamiliarFoxForm = new Form_FamiliarFox3(FormIdentifiers.UPGRADE_FAMILIAR_FOX);
		upgradeFamiliarFoxForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 进化使魔为四足形态，变身后重置玩家缩放到本形态大小（对齐原版 FAMILIAR_FOX_3 新体型 0.55）
		upgradeFamiliarFoxForm.applyScale(0.55f, 0.6f);

		RegPlayerForms.registerPlayerForm(upgradeFamiliarFoxForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_upgrade_familiar_fox")).registerForm(1, 5, upgradeFamiliarFoxForm));

		// 契灵（Mancianima）—— 复用使魔模型/动画，经月髓环/进化石进化获得。
		// 之前是纯数据驱动(ssc_form json)，但原版新版 DynamicForm 缺 originLayerID 字段会 NPE 致其注册失败消失，
		// 故改为与其它 SP 形态一致的代码注册（不再依赖数据驱动），模型由 FormID 查 ssc_form_model 自动得到契灵外观。
		Form_FamiliarFox3 mancianimaForm = new Form_FamiliarFox3(FormIdentifiers.FAMILIAR_FOX_MANCIANIMA);
		mancianimaForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 四足形态变身后重置玩家缩放到本形态大小（对齐原版 FAMILIAR_FOX_3 新体型 0.55）
		mancianimaForm.applyScale(0.55f, 0.6f);

		RegPlayerForms.registerPlayerForm(mancianimaForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_familiar_fox_mancianima")).registerForm(1, 5, mancianimaForm));

		Form_FamiliarFoxRed familiarFoxRedForm = new Form_FamiliarFoxRed(FormIdentifiers.FAMILIAR_FOX_RED);
		familiarFoxRedForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 四足形态变身后重置玩家缩放到本形态大小（原本就比基准大，等倍率放大后到 0.65）
		familiarFoxRedForm.applyScale(0.65f, 0.6f);

		RegPlayerForms.registerPlayerForm(familiarFoxRedForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_familiar_fox_red")).registerForm(1, 5, familiarFoxRedForm));

		Form_SnowFoxSP snowFoxForm = new Form_SnowFoxSP(FormIdentifiers.SNOW_FOX_SP);
		snowFoxForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 四足形态变身后重置玩家缩放到本形态大小（对齐原版 SNOW_FOX_3 新体型 0.55，eye_scale 保持 0.6 以支持潜行过半格）
		snowFoxForm.applyScale(0.55f, 0.6f);

		RegPlayerForms.registerPlayerForm(snowFoxForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_snow_fox_sp")).registerForm(1, 7, snowFoxForm));

		// 寒棘狐（Frostspine）- 雪狐线月髓环进化形态（原版雪狐 snow_fox_3 经月髓环进化），复用原版雪狐模型/贴图，能力完全等同原版雪狐
		Form_SnowFoxSP frostspineForm = new Form_SnowFoxSP(FormIdentifiers.SNOW_FOX_FROSTSPINE);
		frostspineForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 缩放与雪狐SP一致（对齐原版 SNOW_FOX_3 体型 0.55，eye_scale 0.6 支持潜行过半格）
		frostspineForm.applyScale(0.55f, 0.6f);
		RegPlayerForms.registerPlayerForm(frostspineForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_snow_fox_frostspine")).registerForm(1, 7, frostspineForm));

		Form_Allay allayForm = new Form_Allay(FormIdentifiers.ALLAY_SP);
		allayForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 悦灵缩放对齐原版 ALLAY_SP 上调后的新体型（scale=0.55, eye_scale=1.0 保持正常视角高度）
		allayForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.55f, 1.0f));
		RegPlayerForms.registerPlayerForm(allayForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_allay_sp")).registerForm(1, 8, allayForm));

		Form_FeralCatSP wildCatForm = new Form_FeralCatSP(FormIdentifiers.WILD_CAT_SP);
		wildCatForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		wildCatForm.canSneakRush = true;
		// 四足形态变身后重置玩家缩放到本形态大小（值与原版野猫 form_feral_cat_sp_scale 一致）
		wildCatForm.applyScale(0.55f, 0.6f);

		RegPlayerForms.registerPlayerForm(wildCatForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_wild_cat_sp")).registerForm(1, 5, wildCatForm));

		// 食梦魔（Nightmare）- 野猫线月髓环进化形态（原版野猫 feral_cat_sp 经月髓环进化），复用月光魅影野猫模型/贴图
		// 被动与月光魅影对齐（不含真隐身/震慑冲刺两个主动）；核心被动「入梦」：累计 10 伤害 → 敌方入梦 20s，
		// 期间入梦敌对其施加的 debuff 全无效（含 STUN），食梦魔看入梦敌有粉红描边
		Form_FeralCatSP nightmareForm = new Form_FeralCatSP(FormIdentifiers.WILD_CAT_NIGHTMARE);
		nightmareForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		nightmareForm.canSneakRush = true;
		// 缩放与月光魅影一致（原版野猫 form_feral_cat_sp_scale 同值）
		nightmareForm.applyScale(0.55f, 0.6f);
		RegPlayerForms.registerPlayerForm(nightmareForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_wild_cat_nightmare")).registerForm(1, 5, nightmareForm));

		// 风灵（月髓环豹猫）——完全复用原版豹猫 Form_Ocelot3 的模型与动画，四足兽形，可疾跑；核心为「疾风连爪」左键连击技能
		Form_Ocelot3 ocelotSpForm = new Form_Ocelot3(FormIdentifiers.OCELOT_SP);
		ocelotSpForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 标记为 FERAL 四足兽体——原版 AdjustItemHoldFeatureRendererMixin/MouthItemFeature 依此把副手物品渲染到背上而非手臂
		ocelotSpForm.bodyType(PlayerFormBodyType.FERAL);
		// 缩放与原版豹猫 ocelot_3 一致（RegPlayerForms 里 OCELOT_3 用 0.75f/0.6f）
		ocelotSpForm.applyScale(0.75f, 0.6f);
		RegPlayerForms.registerPlayerForm(ocelotSpForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_ocelot_wind_spirit")).registerForm(1, 5, ocelotSpForm));

		// 朔望（月髓环豹猫）——与风灵同源，复用原版豹猫 Form_Ocelot3 模型动画，四足兽形；定位九命灵猫（生存/不死），技能待设计
		Form_Ocelot3 ocelotNovaForm = new Form_Ocelot3(FormIdentifiers.OCELOT_NOVA);
		ocelotNovaForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 标记为 FERAL 四足兽体——与风灵同理，触发原版副手→背渲染
		ocelotNovaForm.bodyType(PlayerFormBodyType.FERAL);
		// 缩放与原版豹猫 ocelot_3 一致（与风灵相同 0.75f/0.6f）
		ocelotNovaForm.applyScale(0.75f, 0.6f);
		RegPlayerForms.registerPlayerForm(ocelotNovaForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_ocelot_nova")).registerForm(1, 5, ocelotNovaForm));

		// Fallen Allay SP
		Form_FallenAllaySP fallenAllayForm = new Form_FallenAllaySP(FormIdentifiers.FALLEN_ALLAY_SP);
		fallenAllayForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 堕落悦灵复用悦灵模型，缩放对齐原版 ALLAY_SP 上调后的新体型(scale=0.55, eye_scale=1.0 保持正常视角高度)
		fallenAllayForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.55f, 1.0f));
		RegPlayerForms.registerPlayerForm(fallenAllayForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_fallen_allay_sp")).registerForm(1, 8, fallenAllayForm));

		// Anubis Wolf SP
		Form_AnubisWolfSP anubisWolfForm = new Form_AnubisWolfSP(FormIdentifiers.ANUBIS_WOLF_SP);
		anubisWolfForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		anubisWolfForm.canSneakRush = true;
		// 四足形态变身后重置玩家缩放到本形态大小（值与 origin power form_anubis_wolf_3_scale 一致）
		anubisWolfForm.applyScale(0.8f, 0.6f);

		RegPlayerForms.registerPlayerForm(anubisWolfForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_anubis_wolf_sp")).registerForm(1, 12, anubisWolfForm));

		// Golden Sandstorm SP (金沙岚)
		Form_GoldenSandstormSP goldenSandstormForm = new Form_GoldenSandstormSP(FormIdentifiers.GOLDEN_SANDSTORM_SP);
		goldenSandstormForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		goldenSandstormForm.canSneakRush = true;
		// 金沙岚复用阿努比斯之狼四足模型，缩放与原版 ANUBIS_WOLF_3 一致(scale=0.8, eye_scale=0.6)
		goldenSandstormForm.applyScale(0.8f, 0.6f);
		RegPlayerForms.registerPlayerForm(goldenSandstormForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_golden_sandstorm_sp")).registerForm(1, 12, goldenSandstormForm));

		// 吸血蝙蝠（Desmodus）SP形态 - 复用蝙蝠模型/动画，经月髓环在诅咒之月夜进化获得
		Form_BatDesmodus batDesmodusForm = new Form_BatDesmodus(FormIdentifiers.BAT_DESMODUS);
		batDesmodusForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune, HasSlowFall);
		// 蝙蝠缩放需与原版 bat_3 一致（宽度/高度0.6、眼睛/碰撞箱0.7），否则保持上个形态大小不缩放
		batDesmodusForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.6f, 0.7f));
		RegPlayerForms.registerPlayerForm(batDesmodusForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_bat_desmodus")).registerForm(1, 12, batDesmodusForm));

		// 月织蛛（Moon Weaver）SP形态 - 复用原版蜘蛛三阶段模型/动画，经月髓环在诅咒之月夜进化获得
		// 被动与特性完全与原版 spider_3 平齐（爬墙、吐丝、搭桥、毒素免疫、夜视等）
		Form_SpiderMoonWeaver SpiderMoonWeaverForm = new Form_SpiderMoonWeaver(FormIdentifiers.SPIDER_MOON_WEAVER);
		SpiderMoonWeaverForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune);
		// 蜘蛛缩放：原版 spider_3（0.9）的 90% → 0.81；眼睛/碰撞箱保持 1.0（NORMAL 体型第一人称不变）
		SpiderMoonWeaverForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.81f, 1.0f));
		RegPlayerForms.registerPlayerForm(SpiderMoonWeaverForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_spider_moon_weaver")).registerForm(1, 12, SpiderMoonWeaverForm));

		// 寄生果蝠 - 原版三阶段蝙蝠使用进化石进化获得，复用蝙蝠模型/动画
		Form_BatParasiticFruit batParasiticFruitForm = new Form_BatParasiticFruit(FormIdentifiers.BAT_PARASITIC_FRUIT);
		batParasiticFruitForm.formFlag(NoInstinct, NoCursedMoonEffect, SpecialForm, InhibitorImmune, HasSlowFall);
		// 蝙蝠缩放需与原版 bat_3 一致（宽度/高度0.6、眼睛/碰撞箱0.7），否则保持上个形态大小不缩放
		batParasiticFruitForm.applyScaleFunc(NormalForm.NORMAL_SCALE_FUNC_BUILDER.apply(0.6f, 0.7f));
		RegPlayerForms.registerPlayerForm(batParasiticFruitForm);
		RegPlayerForms.registerPlayerFormGroup(new NormalGroup(new Identifier("my_addon", "group_bat_parasitic_fruit")).registerForm(1, 12, batParasiticFruitForm));
	}
}
