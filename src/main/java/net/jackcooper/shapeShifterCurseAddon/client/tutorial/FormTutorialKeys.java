package net.jackcooper.shapeShifterCurseAddon.client.tutorial;

import net.jackcooper.shapeShifterCurseAddon.config.SSCAddonConfig;
import net.jackcooper.shapeShifterCurseAddon.tutorial.FormTutorialCatalog;
import net.jackcooper.shapeShifterCurseAddon.util.FormIdentifiers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.client.ShapeShifterCurseFabricClient;

/** The native tutorial and the web tutorial show the same current key bindings. */
final class FormTutorialKeys {
    private FormTutorialKeys() {}

    static Text key(MinecraftClient client, Identifier form, String token) {
        return switch (token) {
            case "primary" -> skillKey(form, true);
            case "secondary" -> skillKey(form, false);
            case "sneak" -> keyName(client.options.sneakKey);
            case "attack" -> keyName(client.options.attackKey);
            case "use" -> keyName(client.options.useKey);
            case "jump" -> keyName(client.options.jumpKey);
            case "sprint" -> keyName(client.options.sprintKey);
            default -> Text.empty();
        };
    }

    private static Text skillKey(Identifier form, boolean primary) {
        if (!primary && FormIdentifiers.UPGRADE_FAMILIAR_FOX.equals(form)) return FormTutorialScreen.text("no_secondary");
        var custom = SSCAddonConfig.client().getFormKeybind(form.getPath());
        if (!FormTutorialCatalog.usesSscKeys(form) && custom != null && custom.enabled) {
            String name = primary ? custom.primaryKey : custom.secondaryKey;
            if (name == null || name.isBlank() || "key.keyboard.unknown".equals(name)) return FormTutorialScreen.text("unbound");
            try { return InputUtil.fromTranslationKey(name).getLocalizedText(); }
            catch (IllegalArgumentException invalidKey) { return FormTutorialScreen.text("unbound"); }
        }
        KeyBinding direct = primary ? ShapeShifterCurseFabricClient.useActiveSkill1PowerKeybind
                : ShapeShifterCurseFabricClient.useActiveSkill2PowerKeybind;
        KeyBinding toolbar = ShapeShifterCurseFabricClient.activeSkillKeybind;
        if (direct != null && !direct.isUnbound()) {
            return toolbar != null && !toolbar.isUnbound()
                    ? FormTutorialScreen.text("bound_and_toolbar", direct.getBoundKeyLocalizedText(), toolbar.getBoundKeyLocalizedText())
                    : direct.getBoundKeyLocalizedText();
        }
        return toolbar != null && !toolbar.isUnbound()
                ? FormTutorialScreen.text("toolbar_key", toolbar.getBoundKeyLocalizedText()) : FormTutorialScreen.text("unbound");
    }

    private static Text keyName(KeyBinding key) {
        return key == null || key.isUnbound() ? FormTutorialScreen.text("unbound") : key.getBoundKeyLocalizedText();
    }
}
