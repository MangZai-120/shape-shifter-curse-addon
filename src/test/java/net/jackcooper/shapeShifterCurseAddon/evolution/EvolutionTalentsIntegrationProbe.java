package net.jackcooper.shapeShifterCurseAddon.evolution;

import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.NormalForm;

import java.util.LinkedHashMap;
import java.util.Map;

import static net.jackcooper.shapeShifterCurseAddon.evolution.EvolutionSystemIntegrationProbe.*;

/** Eligibility across successful/failed config changes and the actual job settlement boundary. */
final class EvolutionTalentsIntegrationProbe {
    private static int checks;

    static void run(Map<String, String> raw) throws Exception {
        try {
            for (EvolutionRoute route : EvolutionRegistry.INSTANCE.all().values()) {
                var player = player(route);
                player.evolution.setRoute(route.routeId);
                for (EvolutionNode node : route.nodes) {
                    check(!EvolutionTalents.isActive(player, node.id), "unacquired node is inactive: " + node.id);
                    player.evolution.unlock(node.id);
                    check(EvolutionTalents.isActive(player, node.id), "acquired node is active: " + node.id);
                }
                var saved = save(player);
                EvolutionTestRoutes.loadServer(Map.of());
                for (EvolutionNode node : route.nodes) {
                    check(!EvolutionTalents.isActive(player, node.id), "missing route pauses: " + node.id);
                    check(player.evolution.isUnlocked(node.id), "missing route keeps acquisition: " + node.id);
                }
                check(saved.equals(save(player)), "eligibility queries never rewrite saved progress");
                EvolutionTestRoutes.loadServer(raw);
                check(route.nodes.stream().allMatch(node -> EvolutionTalents.isActive(player, node.id)), "route restoration resumes acquired nodes");
            }

            var fox = EvolutionRegistry.INSTANCE.getRoute("familiar_fox");
            var axolotl = EvolutionRegistry.INSTANCE.getRoute("axolotl");
            var player = player(fox);
            player.evolution.setRoute(fox.routeId);
            player.evolution.unlock("spirit_vision");
            var saved = save(player);
            EvolutionTestRoutes.loadServer(EvolutionTestRoutes.withoutNode(raw, fox.routeId, "spirit_vision"));
            check(!EvolutionTalents.isActive(player, "spirit_vision") && saved.equals(save(player)), "missing node pauses without losing its record");
            EvolutionTestRoutes.loadServer(raw);
            check(EvolutionTalents.isActive(player, "spirit_vision"), "restored node resumes automatically");

            var edited = JsonParser.parseString(raw.get(fox.routeId)).getAsJsonObject();
            edited.addProperty("enabled", false);
            edited.addProperty("auto_branch_level", 50);
            for (var value : edited.getAsJsonArray("nodes")) {
                var node = value.getAsJsonObject();
                if (node.get("id").getAsString().equals("spirit_vision")) {
                    node.addProperty("cost", 99);
                    node.add("prereqs", JsonParser.parseString("[\"rocket\"]"));
                }
            }
            var changed = new LinkedHashMap<>(raw);
            changed.put(fox.routeId, edited.toString());
            EvolutionTestRoutes.loadServer(changed);
            check(EvolutionTalents.isActive(player, "spirit_vision"), "new entry/cost/prerequisite gates do not revoke acquisition");
            player.evolution.unlock("branch_mancianima");
            check(EvolutionTalents.isActive(player, "branch_mancianima"), "acquired automatic node does not recheck the current level");
            try { EvolutionTestRoutes.loadServer(Map.of("broken", "{")); throw new AssertionError("invalid reload accepted"); }
            catch (IllegalArgumentException expected) { }
            check(EvolutionTalents.isActive(player, "spirit_vision"), "failed reload keeps the last valid eligibility snapshot");
            EvolutionTestRoutes.loadServer(raw);

            player.evolution.clearUnlockedNodes();
            player.evolution.setUnlockAll(true);
            check(EvolutionTalents.isActive(player, "alchemy"), "admin unlock bypasses acquisition");
            for (String invalid : new String[]{null, "", "unknown", "water_spear"})
                check(!EvolutionTalents.isActive(player, invalid), "admin unlock cannot invent a node: " + invalid);
            check(!EvolutionTalents.isActive(null, "alchemy"), "absent player is inactive");
            player.form.nowForm = new NormalForm(axolotl.startForm);
            check(!EvolutionTalents.isActive(player, "alchemy"), "admin unlock cannot bypass current form");
            player.form.nowForm = new NormalForm(fox.startForm);
            player.evolution.setRoute("missing");
            check(!EvolutionTalents.isActive(player, "alchemy"), "admin unlock cannot bypass the saved route");
            player.evolution.setRoute(fox.routeId);
            EvolutionTestRoutes.loadServer(EvolutionTestRoutes.withoutNode(raw, fox.routeId, "alchemy"));
            check(!EvolutionTalents.isActive(player, "alchemy"), "admin unlock cannot bypass removed configuration");
            EvolutionTestRoutes.loadServer(raw);
            player.evolution.setRouteStartForm(axolotl.startForm.toString());
            check(!EvolutionTalents.isActive(player, "alchemy"), "repurposed route identity does not activate unrelated history");
            player.evolution.setRouteStartForm("my_addon:upgrade_familiar_fox");
            player.form.nowForm = new NormalForm(new Identifier("my_addon", "upgrade_familiar_fox"));
            check(EvolutionTalents.isActive(player, "alchemy"), "legacy form aliases retain eligibility");
            player.form.nowForm = null;
            check(!EvolutionTalents.isActive(player, "alchemy"), "missing actual form pauses talents");

            var entering = player(axolotl);
            entering.evolution.setRoute(fox.routeId);
            entering.evolution.setRouteStartForm(fox.startForm.toString());
            entering.evolution.unlock(fox.getBaseNodeId());
            entering.form.transformTargetForm = new NormalForm(fox.startForm);
            check(!EvolutionTalents.isActive(entering, fox.getBaseNodeId()), "initial animation does not activate the future form");
            entering.form.nowForm = entering.form.transformTargetForm;
            check(!entering.evolution.hasStarted() && EvolutionTalents.isActive(entering, fox.getBaseNodeId()), "initial base activates at actual form change before started is marked");

            for (boolean admin : new boolean[]{false, true}) {
                var moving = trained(fox);
                moving.evolution.setUnlockAll(admin);
                EvolutionManager.startJobChange(moving, axolotl.startForm.toString());
                check(moving.evolution.isJobChanging(), "job fixture starts a real transaction");
                check(EvolutionTalents.isActive(moving, "spark"), "old talents remain before actual form change");
                moving.form.nowForm = new NormalForm(axolotl.startForm);
                moving.form.transformTargetForm = null;
                check(!EvolutionTalents.isActive(moving, "spark") && !EvolutionTalents.isActive(moving, axolotl.getBaseNodeId()), "midpoint disables old talents and waits for settlement, including admin");
                complete(moving, moving.evolution.getJobChangeId());
                check(!EvolutionTalents.isActive(moving, "spark") && EvolutionTalents.isActive(moving, axolotl.getBaseNodeId()), "settlement enables only the new route");
            }
        } finally { EvolutionTestRoutes.loadServer(raw); }
        System.out.println("Evolution talent eligibility: " + checks + " checks passed.");
    }

    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
}
