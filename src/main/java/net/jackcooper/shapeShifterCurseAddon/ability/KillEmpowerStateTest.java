package net.jackcooper.shapeShifterCurseAddon.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class KillEmpowerStateTest {
	public static void main(String[] args) throws Exception {
		KillEmpowerState empty = new KillEmpowerState(0, 0);
		check(empty.startRing(280).equals(empty), "Ring needs a free charge");
		KillEmpowerState ready = empty.grant().tickFromRingEffect(0);
		check(ready.grant().equals(ready), "Kills must not refresh an existing charge");
		KillEmpowerState ring = ready.startRing(280);
		check(!ring.hasReady() && ring.ringTicks() == 280, "Ring consumes exactly one charge");
		check(ring.startRing(280).equals(ring), "No second free cast from the same charge");
		KillEmpowerState both = ring.tickFromRingEffect(279).grant();
		check(both.flags() == 3 && both.ringTicks() == 279, "Normal kills can grant during a ring");
		check(both.tickFromRingEffect(278).grant().readyTicks() == 199, "Repeated kills cannot refresh during a ring");
		check(both.consume().ringTicks() == 279 && !both.consume().hasReady(), "Free secondary preserves ring");
		check(both.stopRing().hasReady() && !both.stopRing().hasRing(), "Stopping ring preserves next charge");
		check(new KillEmpowerState(1, 20).tickFromRingEffect(19).equals(new KillEmpowerState(0, 19)), "Charge expiry preserves ring");
		check(new KillEmpowerState(20, 1).tickFromRingEffect(0).equals(new KillEmpowerState(19, 0)), "Ring expiry preserves charge");
		check(ring.consume().grant().hasReady(), "No passive cooldown or next-kill suppression");
		check(empty.tickFromRingEffect(0).equals(empty), "Timers cannot become negative");
		check(!empty.usesEmpoweredSkill(true) && !empty.usesEmpoweredSkill(false), "Normal input reaches normal skills without a client empower packet");
		check(ready.usesEmpoweredSkill(true) && ready.usesEmpoweredSkill(false), "One ready charge routes either key to a free cast");
		check(ring.usesEmpoweredSkill(true) && !ring.usesEmpoweredSkill(false), "Active ring stops with primary but permits normal secondary");
		check(both.usesEmpoweredSkill(true) && both.usesEmpoweredSkill(false), "New charge during ring remains independent");
		check(!ready.consume().usesEmpoweredSkill(false), "Consuming charge does not route next secondary to free skill");
		for (int duration : new int[]{92, 112, 139, 168, 197, 232, 238, 280}) {
			KillEmpowerState timed = new KillEmpowerState(0, duration);
			for (int remaining = duration; remaining >= 0; remaining--) {
				timed = timed.tickFromRingEffect(remaining);
				check(timed.ringTicks() == remaining, "Ring timer must use actual effect duration without a second decrement");
				int pixels = KillEmpowerState.countdownHeight(KillEmpowerState.countdownFraction(remaining, duration), 24);
				check((pixels > 0) == (remaining > 0), "Countdown must not become empty before the last tick");
				if (remaining == duration) check(pixels == 24, "Ring bar starts full for actual duration");
			}
		}
		check(new KillEmpowerState(20, 150).tickFromRingEffect(0).equals(new KillEmpowerState(19, 0)), "Effect removal clears ring only");
		check(new KillEmpowerState(20, 0).tickFromRingEffect(200000).ringTicks() == 0, "Normal effect cannot create empowered ring");
		for (int flags = 0; flags <= 3; flags++) {
			check(KillEmpowerState.allowsNormalSkill(flags, true) == (flags == 0), "Primary input gate");
			check(KillEmpowerState.allowsNormalSkill(flags, false) == (flags == 0 || flags == 2), "Secondary usable during empowered ring");
		}
		JsonObject resources = power("form_empower_state");
		check(resources.getAsJsonObject("state").get("max").getAsInt() == 3, "Combined flags fit resource");
		check(resources.getAsJsonObject("ring_ticks").get("max").getAsInt() >= 280, "Ring duration fits resource");
		check(resources.getAsJsonObject("ring_duration").get("max").getAsInt() >= 280, "Actual total duration fits resource");
		for (String form : new String[]{"sp", "red"}) {
			JsonObject ringPower = power("form_familiar_fox_" + form + "_blue_fire_ring");
			JsonObject activation = ringPower.getAsJsonObject("key_activation");
			check(activation.get("type").getAsString().equals("ssc_addon:familiar_skill")
					&& activation.get("primary").getAsBoolean(), "Primary uses single server-authoritative entry");
			check(activation.getAsJsonObject("key").get("key").getAsString().equals("key.ssc_addon.sp_primary"),
					"Primary keeps native key routing");
			JsonObject particles = ringPower.getAsJsonObject("particle_loop");
			check(particles.get("interval").getAsInt() == (form.equals("red") ? 1 : 4), "Normal particle cadence preserved");
			JsonObject empowerGate = particles.getAsJsonObject("condition").getAsJsonArray("conditions").get(1).getAsJsonObject();
			check(empowerGate.get("type").getAsString().equals("apoli:and"), "Empowered particle branch is amulet-gated");
			JsonObject particleGate = empowerGate.getAsJsonArray("conditions").get(0).getAsJsonObject();
			check(particleGate.get("resource").getAsString().equals("my_addon:form_empower_state_ring_ticks"), "Particle gate uses real independent subresource");
			check(particleGate.get("comparison").getAsString().equals(">") && particleGate.get("compare_to").getAsInt() == 0,
					"Particles remain enabled after earning another charge");
			check(empowerGate.getAsJsonArray("conditions").get(1).getAsJsonObject().get("type").getAsString().equals("ssc_addon:has_blue_fire_amulet"),
					"Full-size empowered particles require no amulet");
			JsonObject amuletLoop = ringPower.getAsJsonObject("particle_loop_amulet");
			check(amuletLoop.getAsJsonObject("condition").getAsJsonArray("conditions").get(0).getAsJsonObject()
						.get("resource").getAsString().equals("my_addon:form_empower_state_ring_ticks")
						&& amuletLoop.getAsJsonObject("condition").getAsJsonArray("conditions").get(1).getAsJsonObject()
						.get("type").getAsString().equals("ssc_addon:has_blue_fire_amulet"),
					"Compact empowered particles run only with the amulet");
			JsonObject inputGate = ringPower.getAsJsonObject("key_activation").getAsJsonObject("condition").getAsJsonArray("conditions").get(2).getAsJsonObject();
			check(inputGate.get("primary").getAsBoolean(), "Ring input does not fall through during empowerment");
			JsonObject manaGate = ringPower.getAsJsonObject("key_activation").getAsJsonObject("condition")
					.getAsJsonArray("conditions").get(3).getAsJsonObject();
			check(manaGate.get("type").getAsString().equals("apoli:or"), "Low mana must not consume activation cooldown");
			check(manaGate.getAsJsonArray("conditions").get(0).getAsJsonObject().get("resource").getAsString()
					.equals("my_addon:form_familiar_fox_" + form + "_blue_fire_ring_toggle_state"), "Stopping normal ring ignores mana");
			check(manaGate.getAsJsonArray("conditions").get(1).getAsJsonObject().get("mana").getAsDouble() == 99.0,
					"Normal ring mana requirement stays unchanged");
			JsonObject secondary = power("form_familiar_fox_" + form + "_fox_fire_breath");
			check(secondary.get("type").getAsString().equals("ssc_addon:familiar_skill"), "Secondary uses same server-authoritative entry");
			check(secondary.getAsJsonObject("key").get("key").getAsString().equals("key.ssc_addon.sp_secondary"),
					"Secondary keeps native key routing");
			check(secondary.get("cooldown").getAsInt() == (form.equals("red") ? 100 : 20), "Normal secondary cooldown unchanged");
			JsonObject amulet = power("form_familiar_fox_" + form + "_blue_fire_ring_amulet");
			check(amulet.getAsJsonObject("cooldown_timer").get("max").getAsInt() == 560, "Amulet ring has its own cooldown");
			JsonObject amuletActivation = amulet.getAsJsonObject("key_activation");
			check(amuletActivation.get("cooldown").getAsInt() == 20, "Amulet activation cooldown stays distinct");
			JsonObject amuletCooldown = amuletActivation.getAsJsonObject("condition").getAsJsonArray("conditions").get(0).getAsJsonObject();
			check(amuletCooldown.get("resource").getAsString().equals("my_addon:form_familiar_fox_" + form + "_blue_fire_ring_amulet_cooldown_timer"),
					"Amulet input uses amulet cooldown resource");
			boolean amuletEmpowerGate = false;
			for (var gateEl : amuletActivation.getAsJsonObject("condition").getAsJsonArray("conditions")) {
				JsonObject gate = gateEl.getAsJsonObject();
				if (gate.has("type") && "ssc_addon:not_empowered".equals(gate.get("type").getAsString())
						&& gate.get("primary").getAsBoolean()) amuletEmpowerGate = true;
			}
			check(amuletEmpowerGate, "Amulet ring input does not double-fire during empowerment");
			double amuletMana = amuletActivation.getAsJsonObject("entity_action").getAsJsonArray("actions").get(0).getAsJsonObject()
					.getAsJsonObject("else_action").getAsJsonObject("condition").get("mana").getAsDouble();
			check(amuletMana == (form.equals("red") ? 119.0 : 99.0), "Amulet HUD mana thresholds match original powers");
		}
		check(power("form_kill_empower").get("type").getAsString().equals("apoli:simple"), "No unrestricted on-kill callback");
		System.out.println("Kill empower state checks passed (independent timers, no cooldown, no refresh, one charge).");
		System.out.println("Kill empower resource checks passed (both input gates, independent ring particles, original cooldowns).");
		System.out.println("Ring timing/HUD checks passed (actual effect clock, 4 durations, every tick, last pixel, interruption).");
		System.out.println("Familiar input routing checks passed (normal/empowered selection, single entry, mana guard, unchanged keybinds).");
	}

	private static JsonObject power(String name) throws Exception {
		try (var stream = KillEmpowerStateTest.class.getResourceAsStream("/data/my_addon/powers/" + name + ".json")) {
			if (stream == null) throw new AssertionError("Missing power " + name);
			return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
		}
	}

	private static void check(boolean passed, String message) {
		if (!passed) throw new AssertionError(message);
	}
}