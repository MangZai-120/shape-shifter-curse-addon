package net.jackcooper.shapeShifterCurseAddon.cooldown;

import java.util.UUID;

/** Real service transitions on a deterministic clock, without a running Minecraft world. */
public final class SkillCooldownTest {
    public static void main(String[] args) throws Exception {
        SkillCooldownPersistenceTest.run();
        for (String mode : new String[]{"on_cast", "on_release", "on_end"}) {
            SkillCastManager manager = new SkillCastManager();
            UUID owner = UUID.randomUUID();
            long cast = manager.begin(owner, "test:skill", new SkillCastManager.ResolvedConfig(100, 30, mode), 1000);
            check(cast > 0, "accepted");
            check(manager.begin(owner, "test:skill", new SkillCastManager.ResolvedConfig(100, 30, mode), 1001) < 0, "repeat rejected");
            check(remaining(manager, owner, 1000) == (mode.equals("on_cast") ? 100 : 0), "cast start " + mode);
            manager.released(cast, 1040);
            check(remaining(manager, owner, 1040) == (mode.equals("on_cast") ? 60 : mode.equals("on_release") ? 100 : 0), "release " + mode);
            manager.released(cast, 1060);
            check(remaining(manager, owner, 1060) == (mode.equals("on_cast") ? 40 : mode.equals("on_release") ? 80 : 0), "release is idempotent");
            manager.finish(cast, 1200);
            check(remaining(manager, owner, 1200) == (mode.equals("on_end") ? 100 : 0), "finish " + mode);
            manager.finish(cast, 1210);
            check(remaining(manager, owner, 1210) == (mode.equals("on_end") ? 90 : 0), "finish is idempotent");
            long next = manager.begin(owner, "test:skill", new SkillCastManager.ResolvedConfig(100, 30, mode), 1400);
            manager.finish(cast, 1410);
            check(manager.control(owner, "test:skill").castId == next, "old callback cannot finish new cast");
        }
        for (int failure : new int[]{0, 20, 100, 300}) {
            var manager = new SkillCastManager(); UUID owner = UUID.randomUUID();
            long cast = manager.begin(owner, "test:skill", new SkillCastManager.ResolvedConfig(100, failure, "on_cast"), 1000);
            manager.fail(cast, 1020);
            check(remaining(manager, owner, 1020) == failure, "failure replaces normal: " + failure);
            check(manager.control(owner, "test:skill") == null, "failure frees active record");
        }
        for (String mode : new String[]{"on_cast", "on_release", "on_end"}) {
            for (boolean released : new boolean[]{false, true}) {
                for (int failure : new int[]{0, 30}) {
                    var manager = new SkillCastManager(); UUID owner = UUID.randomUUID();
                    long cast = manager.begin(owner, "test:skill", new SkillCastManager.ResolvedConfig(100, failure, mode), 1000);
                    if (released) manager.released(cast, 1020);
                    manager.interrupt(cast, 1040);
                    int expected = !released ? failure : mode.equals("on_cast") ? 60 : mode.equals("on_release") ? 80 : 100;
                    check(remaining(manager, owner, 1040) == expected, "interrupt settles phase: " + mode + "/" + released);
                    check(manager.control(owner, "test:skill") == null, "interrupt frees gate");
                    manager.interrupt(cast, 1050);
                    check(remaining(manager, owner, 1050) == Math.max(0, expected - 10), "duplicate cleanup cannot restart CD");

                    long next = manager.begin(owner, "test:skill", new SkillCastManager.ResolvedConfig(100, failure, mode), 2000);
                    if (released) manager.released(next, 2020);
                    manager.onPlayerRemoved(owner, 2040, ignored -> null);
                    check(remaining(manager, owner, 2040) == expected, "disconnect without entity uses identical settlement");
                }
            }
        }
        checkFireRings();
        var tuned = new SkillCastManager(); UUID tunedOwner = UUID.randomUUID();
        long tunedCast = tuned.begin(tunedOwner, "test:skill", new SkillCastManager.ResolvedConfig(100, 0, "on_cast"), 1000);
        tuned.retune(tunedCast, 40);
        check(remaining(tuned, tunedOwner, 1010) == 30, "retune rewrites an already started cooldown");
        tuned.finish(tunedCast, 1010);
        tuned.force(tunedOwner, "test:skill", 70, 1100);
        check(remaining(tuned, tunedOwner, 1100) == 70, "force starts a cooldown without a cast");
        tuned.reset(tunedOwner, "test:skill");
        check(remaining(tuned, tunedOwner, 1100) == 0, "reset clears a cooldown");
        long cancelled = tuned.begin(tunedOwner, "test:skill", new SkillCastManager.ResolvedConfig(100, 50, "on_end"), 1200);
        tuned.abort(null, cancelled);
        check(remaining(tuned, tunedOwner, 1200) == 0 && tuned.control(tunedOwner, "test:skill") == null, "cancel frees without cooldown");
        check(new SkillCastManager.ResolvedConfig(-5, -1, "typo").cooldown == 0, "invalid runtime values do not throw");
        System.out.println("Skill cooldown: modes, callbacks, failures, interruption/disconnect, fire-ring resources, retune/force/reset/cancel PASS; live multiplayer NOT tested.");
    }

    private static void checkFireRings() throws Exception {
        for (String name : new String[]{"form_familiar_fox_sp_blue_fire_ring", "form_familiar_fox_red_blue_fire_ring",
                "form_familiar_fox_sp_blue_fire_ring_amulet", "form_familiar_fox_red_blue_fire_ring_amulet"}) {
            var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/main/resources/data/my_addon/powers", name + ".json"))).getAsJsonObject();
            var key = json.getAsJsonObject("key_activation");
            check(key.get("defer_release").getAsBoolean(), "ring owns lifecycle");
            check(checkRingActions(key.get("entity_action"), "my_addon:" + name + "_key_activation") == 1,
                    "exactly one opening sequence in " + name);
        }
    }

    private static int checkRingActions(com.google.gson.JsonElement node, String skill) {
        int count = 0;
        if (node.isJsonArray()) {
            var actions = node.getAsJsonArray();
            int begin = -1, effect = -1, release = -1;
            for (int i = 0; i < actions.size(); i++) {
                var entry = actions.get(i);
                if (!entry.isJsonObject() || !entry.getAsJsonObject().has("type")) continue;
                var action = entry.getAsJsonObject();
                String type = action.get("type").getAsString();
                if (type.equals("my_addon:skill_begin")) begin = i;
                if (type.equals("apoli:apply_effect")) effect = i;
                if (type.equals("my_addon:skill_released")) {
                    release = i;
                    check(action.get("power").getAsString().equals(skill), "release uses correct cooldown domain");
                }
            }
            if (begin >= 0) {
                check(effect > begin && release > effect, "ring release follows successful effect application: " + skill);
                count++;
            }
            for (var entry : actions) count += checkRingActions(entry, skill);
        } else if (node.isJsonObject()) {
            for (var entry : node.getAsJsonObject().entrySet()) count += checkRingActions(entry.getValue(), skill);
        }
        return count;
    }
    private static int remaining(SkillCastManager m, UUID owner, long now) {
        var cd = m.cooldown(owner, "test:skill"); return cd == null ? 0 : cd.remaining(now);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
