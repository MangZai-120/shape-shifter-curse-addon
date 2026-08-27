package net.jackcooper.shapeShifterCurseAddon.mixin.render;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FormColorSelectMenu (xu233 PR #418) 颜色解析相关的附属补丁。
 *
 * <p><b>当前依赖现状</b>：modrinth 已发布的 SSC（最新 1.9.1）尚未包含 FormColorSelectMenu 类，
 * PR #418 仅存在于 master 未发布版本。因此本 mixin 使用 {@link Pseudo} + 字符串 target，
 * 编译期不引用任何 PR 新类；目标类不存在时 {@code @Inject(require = 0)} 让 mixin 静默不生效。</p>
 *
 * <p><b>@Pseudo + 字符串 target 的硬限制</b>：</p>
 * <ul>
 *   <li>不能用 {@link org.spongepowered.asm.mixin.Overwrite}（AP 无目标类做 mapping 校验，
 *       报 {@code Unable to locate obfuscation mapping}）。</li>
 *   <li>不能 {@link Inject} 描述符里含 vanilla 类型（{@code Text}/{@code Identifier} 等）的方法，
 *       因为 Loom 会把 vanilla 类型 remap 到 intermediary 名，AP 不知道目标类则无法生成 refmap。</li>
 * </ul>
 *
 * <p>所以本类<b>只能修</b>描述符为纯 JDK / 模组自身类型的 PR 新方法。涉及 vanilla 类型的修复
 * （#1 instance 单例 / #2 mouseClicked 左键 / #3 timer 重置 / #4 getTexture 缓存上限）
 * 必须等 SSC 发布带 PR #418 的版本后，附属升级 ssc_version 再切回 typed mixin 实现。</p>
 *
 * <h3>已修复项</h3>
 * <ul>
 *   <li><b>[中危 #5]</b> {@code colorChannel2Int(String, int, int)}：原方法对空 / 纯空白字符串会
 *       抛 {@link NumberFormatException} 并被吞掉返回 min(=0)，导致编辑框被清空时颜色被强制变 0。
 *       本 inject 在 HEAD 拦截：空 / 纯空白直接 setReturnValue(min)，跳过 parse；
 *       其余情况让原方法继续执行。</li>
 *   <li><b>[中危 #6]</b> {@code decodeColor(String)}：原方法只接受 {@code #AARRGGBB} 8 位完整 hex，
 *       不支持 {@code #RGB} / {@code #ARGB} / {@code #RRGGBB} 简写。本 inject 在 HEAD 拦截，
 *       识别短 hex 并补齐到 8 位后 setReturnValue；标准 8 位 / 非法格式留给原方法处理。</li>
 * </ul>
 *
 * <p>这两个方法签名纯 String/int，不涉及 vanilla 类型，所以可以安全 inject。
 * {@code remap = false} 让 mixin AP 跳过 refmap 处理（SSC 自定义方法名 Loom 不会重映射）。</p>
 */
@Pseudo
@Mixin(targets = "net.onixary.shapeShifterCurseFabric.custom_ui.FormColorSelectMenu", priority = 1100)
public abstract class FormColorSelectMenuMixin extends Screen {

    protected FormColorSelectMenuMixin(Text title) {
        super(title);
    }

    // ---- 修复 #5：colorChannel2Int 空字符串保护 ----
    @Inject(
        method = "colorChannel2Int(Ljava/lang/String;II)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void sscAddon$guardEmptyChannel(String channel, int min, int max,
                                            CallbackInfoReturnable<Integer> cir) {
        if (channel == null || channel.trim().isEmpty()) {
            cir.setReturnValue(min);
        }
    }

    // ---- 修复 #6：decodeColor 短 hex 归一化 ----
    @Inject(
        method = "decodeColor(Ljava/lang/String;)I",
        at = @At("HEAD"),
        cancellable = true,
        require = 0,
        remap = false
    )
    private void sscAddon$normalizeShortHex(String color, CallbackInfoReturnable<Integer> cir) {
        if (color == null || color.isEmpty()) {
            return;
        }
        String s = color.trim();
        if (!s.startsWith("#")) {
            return;
        }
        String hex = s.substring(1);
        // 仅处理需要补齐的短 hex；标准 8 位 (#AARRGGBB) 和非法长度都留给原方法
        String normalized;
        if (hex.length() == 3) {
            // #RGB → #FFRRGGBB
            char r = hex.charAt(0);
            char g = hex.charAt(1);
            char b = hex.charAt(2);
            normalized = "FF" + r + r + g + g + b + b;
        } else if (hex.length() == 4) {
            // #ARGB → #AARRGGBB
            char a = hex.charAt(0);
            char r = hex.charAt(1);
            char g = hex.charAt(2);
            char b = hex.charAt(3);
            normalized = "" + a + a + r + r + g + g + b + b;
        } else if (hex.length() == 6) {
            // #RRGGBB → #FFRRGGBB
            normalized = "FF" + hex;
        } else {
            return;
        }
        try {
            cir.setReturnValue(Integer.parseUnsignedInt(normalized, 16));
        } catch (NumberFormatException ignored) {
            // 解析失败留给原方法
        }
    }
}
