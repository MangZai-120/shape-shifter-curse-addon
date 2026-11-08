package net.jackcooper.shapeShifterCurseAddon.client.hud;

import java.awt.image.BufferedImage;
import java.util.List;
import javax.imageio.ImageIO;

import static net.jackcooper.shapeShifterCurseAddon.client.hud.SkillHudLayout.*;

/** Regress the approved offsets, complete viewport bounds and original texture preservation. */
public final class SkillHudLayoutTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        check(LOWER_X == 4 && UPPER_X == 3 && LOWER_Y == 63 && UPPER_Y == -13, "latest approved small-frame offsets");
        check(EXTRA_SIZE == 18 && PADDING == 8, "approved frame and background padding");
        check(bounds(0, false).equals(new HudScaleGeometry.Box(0, 0, 33, 68)), "old two-skill bounds");
        check(bounds(1, false).equals(new HudScaleGeometry.Box(0, 0, 33, 84)), "third skill extends only downward");
        check(bounds(2, false).equals(new HudScaleGeometry.Box(0, -16, 33, 100)), "latest manually-sized fourth-skill background");
        check(extraX(Slot.LOWER, true) == 11 && extraX(Slot.LOWER, false) == 4, "lower frame reflects around original panel");
        check(extraX(Slot.UPPER, true) == 12 && extraX(Slot.UPPER, false) == 3, "upper frame has its own approved offset");
        for (int extra = 0; extra < 3; extra++) check(backgroundBounds(extra, false).width() == 14, "background width is always fourteen");
        for (int extras = 0; extras <= 2; extras++) {
            for (boolean right : new boolean[]{false, true}) {
                var b = bounds(extras, right);
                for (int[] size : new int[][]{{320, 240}, {640, 360}, {1280, 720}}) {
                    for (int[] wanted : new int[][]{{-999, -999}, {0, 0}, {30, 50}, {9999, 9999}}) {
                        var p = place(wanted[0], wanted[1], right, size[0], size[1], extras);
                        check(p.x() + b.x() >= 0 && p.y() + b.y() >= 0, "entire panel stays within top/left");
                        check(p.x() + b.x() + b.width() <= size[0]
                                && p.y() + b.y() + b.height() <= size[1], "entire panel stays within bottom/right");
                        var source = new HudScaleGeometry.Box(p.x() + b.x(), p.y() + b.y(), b.width(), b.height());
                        for (int scale : new int[]{50, 100, 150, 200}) {
                            var shown = scaledView(source, scale, size[0], size[1], right, extras).bounds();
                            check(shown.x() >= 0 && shown.y() >= 0 && shown.x() + shown.width() <= size[0]
                                    && shown.y() + shown.height() <= size[1], "expanded panel scales without clipping");
                        }
                    }
                }
            }
        }
        for (boolean right : new boolean[]{false, true}) for (int scale : new int[]{50, 75, 100, 125, 150, 175, 200}) {
            var plain = scaledView(new HudScaleGeometry.Box(200, 200, 33, 68), scale, 1280, 720, right, 0);
            var extent = bounds(2, right);
            var full = scaledView(new HudScaleGeometry.Box(200 + extent.x(), 200 + extent.y(), extent.width(), extent.height()), scale, 1280, 720, right, 2);
            double mainX = full.bounds().x() - extent.x() * full.scale();
            double mainY = full.bounds().y() - extent.y() * full.scale();
            check(Math.abs(mainX - plain.bounds().x()) < 1.01 && Math.abs(mainY - plain.bounds().y()) < 1.01,
                    "learning an extra skill preserves the main-pair scale pivot");
        }
        var assigned = select(List.of("secondary", "extra1", "primary", "extra2"), "primary"::equals);
        check(assigned.equals(List.of(new Entry<>("primary", Slot.PRIMARY), new Entry<>("secondary", Slot.SECONDARY),
                new Entry<>("extra1", Slot.LOWER), new Entry<>("extra2", Slot.UPPER))), "fixed pair first, third below, fourth above");
        check(select(List.of("secondary"), "primary"::equals).get(0).slot() == Slot.SECONDARY, "missing primary never moves secondary into its slot");
        check(extraCount(select(List.of("primary", "secondary"), "primary"::equals)) == 0, "no empty extra slots");
        check(extraCount(select(List.of("primary", "secondary", "extra"), "primary"::equals)) == 1, "one extra uses one small frame");
        var withPerk = select(List.of("primary", "secondary", "craft", "burst", "jump"), "primary"::equals,
                value -> value.equals("burst"), List.of("perk"));
        check(withPerk.get(2).equals(new Entry<>("burst", Slot.LOWER))
                && withPerk.get(3).equals(new Entry<>("perk", Slot.UPPER)), "crafting/movement excluded, learned Perk follows burst");
        var perkOnly = select(List.<String>of(), "primary"::equals, ignored -> true, List.of("perk"));
        check(perkOnly.get(0).slot() == Slot.LOWER, "Perk never takes a missing main slot");
        BufferedImage original = image("skill_cd_panel"), oldRight = image("skill_cd_panel_right");
        check(original.getWidth() == 33 && original.getHeight() == 68 && oldRight.getWidth() == 33, "original texture dimensions retained");
        try (var in = SkillHudLayoutTest.class.getClassLoader().getResourceAsStream("assets/my_addon/textures/gui/skill_cd_panel.png")) {
            check(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))
                    .equals("40191f7d7f8e9c14d2919d68306ebf71655ea3ef749065612ed6883e55d00cda"), "original panel texture remains untouched");
        }
        String[] approvedHashes = {"8bd8600cb672bae208aab5dd240e08c5cc897e7c1f935de5cc79d3234fa8dfbd",
                "c4260fef416c1f798b746c11af632c47ed0a7b9cc9a83326941935bace99c740",
                "c22f9c568677881979e3f6173a66744cd75f977f4f805a10bebd8ab0f103c3b6"};
        for (int count : new int[]{2, 3, 4}) {
            String name = "skill_cd_panel_" + count;
            try (var in = SkillHudLayoutTest.class.getClassLoader().getResourceAsStream("assets/my_addon/textures/gui/" + name + ".png")) {
                check(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(in.readAllBytes()))
                        .equals(approvedHashes[count - 2]), "game asset matches the approved layout with the new Desktop border: " + count);
            }
            BufferedImage panel = image(name), right = image(name + "_right");
            check(panel.getWidth() == 33 && panel.getHeight() == bounds(count - 2, false).height(), "art dimensions match dynamic draw bounds");
            for (int y = 0; y < panel.getHeight(); y++) for (int x = 0; x < 33; x++) {
                check(panel.getRGB(x, y) == right.getRGB(32 - x, y), "complete authored panel has an exact mirror");
            }
            int offset = count == 4 ? 16 : 0;
            for (int[] xy : new int[][]{{12, 21}, {12, 22}, {13, 77}, {13, 78}})
                check(panel.getRGB(xy[0], xy[1] - 16 + offset) == 0xC4333333, "user background edge edits survive every variant");
            for (int y : new int[]{49, 50}) check(panel.getRGB(13, y - 16 + offset) == 0, "user edge notches remain transparent");
        }
        System.out.println("Skill HUD layout: " + checks + " layout, viewport, scaling and texture checks passed.");
    }
    private static BufferedImage image(String name) throws Exception {
        try (var in = SkillHudLayoutTest.class.getClassLoader().getResourceAsStream("assets/my_addon/textures/gui/" + name + ".png")) {
            if (in == null) throw new AssertionError("Missing HUD texture: " + name);
            return ImageIO.read(in);
        }
    }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
