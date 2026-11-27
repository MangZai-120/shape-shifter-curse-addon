package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;

import java.util.ArrayList;
import java.util.List;

public final class MerchantSpellTrades {
	private MerchantSpellTrades() {}

	public interface LibrarianOffers {
		void sscAddon$rollLibrarianScrolls(Random random);
	}

	private record Tier(SpellRarity rarity, int weight, int emeralds, int crystals, int singleStock) {
		Tier withWeight(int weight) {
			return new Tier(rarity, weight, emeralds, crystals, singleStock);
		}
	}

	private static final Tier BLUE = new Tier(SpellRarity.BLUE, 10, 8, 0, 2);
	private static final Tier PURPLE = new Tier(SpellRarity.PURPLE, 60, 16, 2, 2);
	private static final Tier ORANGE = new Tier(SpellRarity.ORANGE, 25, 24, 4, 1);
	private static final Tier RED = new Tier(SpellRarity.RED, 5, 32, 8, 1);
	private static final List<Tier> WANDERING_TIERS = List.of(BLUE, PURPLE, ORANGE, RED);
	// Out of 200: white 30%, green 25%, blue 20%, purple 15%, orange 7.5%, red 2.5%.
	private static final List<Tier> LIBRARIAN_TIERS = List.of(
			new Tier(SpellRarity.WHITE, 60, 2, 0, 2),
			new Tier(SpellRarity.GREEN, 50, 4, 0, 2),
			BLUE.withWeight(40), PURPLE.withWeight(30), ORANGE.withWeight(15), RED.withWeight(5));

	private record Scroll(Spell spell, int level, Tier tier) {
		TradeOffer offer(Random random, int stock) {
			ItemStack emeralds = new ItemStack(Items.EMERALD, price(tier.emeralds(), random));
			ItemStack crystals = tier.crystals() == 0 ? ItemStack.EMPTY :
					new ItemStack(RegCustomItem.MOONDUST_CRYSTAL_SHARD, price(tier.crystals(), random));
			ItemStack scroll = ScrollData.create(spell.getId().getPath(), level);
			return new TradeOffer(emeralds, crystals, scroll, stock, 0, 0.0F);
		}
	}

	static void addWanderingOffers(TradeOfferList offers, Random random) {
		addOffers(offers, random, WANDERING_TIERS);
	}

	public static void addLibrarianOffers(TradeOfferList offers, Random random) {
		addOffers(offers, random, LIBRARIAN_TIERS);
	}

	private static void addOffers(TradeOfferList offers, Random random, List<Tier> tiers) {
		if (!random.nextBoolean()) return;
		Scroll first = draw(random, null, tiers);
		if (first == null) return;
		Scroll second = random.nextBoolean() ? draw(random, first.spell(), tiers) : null;
		offers.add(first.offer(random, second == null ? first.tier().singleStock() : 1));
		if (second != null) offers.add(second.offer(random, 1));
	}

	private static int price(int base, Random random) {
		int spread = base * 15 / 100;
		return spread == 0 ? base : base + random.nextBetween(-spread, spread);
	}

	private static Scroll draw(Random random, Spell excluded, List<Tier> pool) {
		// A data pack can change level rarities. Retry among remaining tiers if it removed all candidates.
		List<Tier> tiers = new ArrayList<>(pool);
		int weight = tiers.stream().mapToInt(Tier::weight).sum();
		while (!tiers.isEmpty()) {
			int roll = random.nextInt(weight);
			int index = 0;
			while (roll >= tiers.get(index).weight()) {
				roll -= tiers.get(index).weight();
				index++;
			}
			Tier tier = tiers.remove(index);
			List<Scroll> candidates = new ArrayList<>();
			for (Spell spell : SpellRegistry.all()) {
				if (spell == excluded) continue;
				// One entry per spell even if a data pack assigns the same rarity to several levels.
				int chosenLevel = 0;
				int matches = 0;
				for (int level = 1; level <= spell.getMaxLevel(); level++) {
					if (spell.getRarity(level) != tier.rarity()) continue;
					matches++;
					if (matches == 1 || random.nextInt(matches) == 0) {
						chosenLevel = level;
					}
				}
				if (chosenLevel != 0) candidates.add(new Scroll(spell, chosenLevel, tier));
			}
			if (!candidates.isEmpty()) return candidates.get(random.nextInt(candidates.size()));
			weight -= tier.weight();
		}
		return null;
	}
}
