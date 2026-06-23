package net.onixary.shapeShifterCurseFabric.ssc_addon.client.keybind;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.IForm;
import net.onixary.shapeShifterCurseFabric.player_form.RegPlayerForms;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 提供「特殊键位设置」二级菜单要列出的 SSCA 形态清单（自动检测，非硬编码）。
 * <p>
 * 来源 = {@link RegPlayerForms#playerForms} 中所有命名空间为 {@code my_addon} 的已注册形态
 * （SSCA 注册的形态全部如此），按注册顺序排列；如此新增/数据包形态会自动出现。
 * 显示名走 lang 键 {@code origin.my_addon.form_<path>.name}（与游戏内形态名一致）。
 */
public final class SscAddonSkillForms {

	/**
	 * 始终显示的额外形态：数据驱动形态（如契灵 ssc_form）在数据加载前不在注册表里，
	 * 列在这里可保证它和静态注册的 SP 形态一样、即使在主菜单也能显示。
	 */
	private static final List<String> ALWAYS_SHOWN_FORMS = List.of(
			"familiar_fox_mancianima"
	);

	private SscAddonSkillForms() {
	}

	/** 自动检测要展示的形态 path 列表（去重、保序）。 */
	public static List<String> getFormPaths() {
		LinkedHashSet<String> paths = new LinkedHashSet<>();
		try {
			for (IForm form : RegPlayerForms.playerForms.values()) {
				if (form == null) {
					continue;
				}
				Identifier id = form.getFormID();
				if (id != null && "my_addon".equals(id.getNamespace())) {
					paths.add(id.getPath());
				}
			}
		} catch (Throwable ignored) {
			// 注册表异常时仅返回始终显示项
		}
		paths.addAll(ALWAYS_SHOWN_FORMS);
		return new ArrayList<>(paths);
	}

	/** 形态显示名（复用游戏内形态名 lang 键）。 */
	public static Text displayName(String formPath) {
		return Text.translatable("origin.my_addon.form_" + formPath + ".name");
	}
}
