package net.jackcooper.shapeShifterCurseAddon.screen;

import net.jackcooper.shapeShifterCurseAddon.block.InfusionAltarBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.item.MoonDustSpellbookItem;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationData;
import net.jackcooper.shapeShifterCurseAddon.spell.FormationElement;
import net.jackcooper.shapeShifterCurseAddon.spell.SpellbookData;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/** Actual Minecraft item/slot/NBT checks under Fabric; does not create a game window or world. */
public final class InfusionAltarIntegrationTest {
	public static void main(String[] args) throws Exception {
		System.setProperty("java.class.path", java.nio.file.Files.readString(
				java.nio.file.Path.of(System.getProperty("altar.testClasspathFile"))));
		var knot = new net.fabricmc.loader.impl.launch.knot.Knot(net.fabricmc.api.EnvType.CLIENT);
		var loader = knot.init(new String[]{"--version", "altar-test", "--accessToken", "0"});
		Thread.currentThread().setContextClassLoader(loader);
		Class.forName("net.minecraft.SharedConstants", true, loader).getMethod("createGameVersion").invoke(null);
		Class.forName("net.minecraft.Bootstrap", true, loader).getMethod("initialize").invoke(null);
		Class.forName(InfusionAltarIntegrationTest.class.getName() + "$Probe", true, loader).getMethod("run").invoke(null);
	}

	public static final class Probe {
		public static void run() {
			Item bookItem = Registry.register(Registries.ITEM, new Identifier("altar_test", "book"),
					new MoonDustSpellbookItem(new Item.Settings().maxCount(1)));
			ItemStack book = new ItemStack(bookItem);
			for (int level = 1; level <= 3; level++) {
				SpellbookData.setLevel(book, level);
				int expected = new int[]{1, 3, 6}[level - 1];
				check(SpellbookData.getFormationSlotCount(book) == expected, "level unlock capacity");
				for (int slot = 0; slot < 7; slot++) {
					check(SpellbookData.isFormationSlotUnlocked(book, slot) == (slot < expected), "level unlock boundary");
				}
			}
			// Simulate an old five-slot book, append slot 5, then round-trip its actual ItemStack NBT.
			for (int i = 0; i < 5; i++) SpellbookData.setFormation(book, i, formation(i + 1));
			NbtCompound legacyBook = book.writeNbt(new NbtCompound());
			ItemStack upgraded = ItemStack.fromNbt(legacyBook);
			check(SpellbookData.getFormation(upgraded, 5).isEmpty(), "old book gains an empty sixth slot");
			SpellbookData.setFormation(upgraded, 5, formation(1));
			ItemStack restored = ItemStack.fromNbt(upgraded.writeNbt(new NbtCompound()));
			check(SpellbookData.getFormations(restored).size() == 6, "six formations survive item NBT");
			for (int i = 0; i < 5; i++) {
				check(FormationData.getLevel(SpellbookData.getFormation(restored, i)) == i + 1, "old slot retained");
			}
			check(Math.abs(FormationData.sumDamageMultiplier(restored, FormationElement.FIRE) - 2.92f) < 0.0001f,
					"sixth formation participates in spell modifiers");
			SimpleInventory inventory = new SimpleInventory(InfusionAltarScreenHandler.ALTAR_SLOT_COUNT);
			PlayerInventory player = new PlayerInventory(null);
			InfusionAltarScreenHandler handler = new InfusionAltarScreenHandler(1, player, inventory);
			check(handler.slots.size() == 45, "nine altar slots and 36 inventory slots");
			check(!handler.getSlot(8).canInsert(formation(1)), "sixth slot locked without book");
			inventory.setStack(0, restored);
			SpellbookData.setLevel(restored, 2);
			check(!handler.getSlot(8).canInsert(formation(1)), "sixth slot locked at level two");
			SpellbookData.setLevel(restored, 3);
			check(handler.getSlot(8).canInsert(formation(1)), "sixth slot enabled at level three");
			for (int i = 3; i < 8; i++) inventory.setStack(i, formation(1));
			player.setStack(9, formation(2));
			check(!handler.quickMove(null, 9).isEmpty(), "shift-click from first inventory slot");
			check(FormationData.getLevel(inventory.getStack(8)) == 2, "shift-click reaches sixth slot");
			check(player.getStack(9).isEmpty(), "shift-click moves rather than copies");
			check(!handler.quickMove(null, 8).isEmpty(), "shift-click from sixth altar slot");
			check(inventory.getStack(8).isEmpty(), "sixth slot empties after extraction");
			check(FormationData.getLevel(player.getStack(8)) == 2, "extracted formation reaches hotbar");
			for (int i = 0; i < handler.slots.size(); i++) {
				var slot = handler.getSlot(i);
				check(slot.x >= 1 && slot.y >= 1 && slot.x + 17 < 312 && slot.y + 17 < 232, "slot inside GUI");
				for (int j = i + 1; j < handler.slots.size(); j++) {
					var other = handler.getSlot(j);
					check(Math.abs(slot.x - other.x) >= 18 || Math.abs(slot.y - other.y) >= 18, "slots never overlap");
				}
			}
			// Block-entity NBT includes the appended ninth inventory entry; old eight-entry saves still load.
			InfusionAltarBlockEntity altar = new InfusionAltarBlockEntity(BlockPos.ORIGIN, Blocks.STONE.getDefaultState());
			altar.setStack(0, restored.copy());
			for (int i = 0; i < 6; i++) altar.setStack(3 + i, formation(i % 5 + 1));
			NbtCompound save = new NbtCompound();
			altar.writeNbt(save);
			InfusionAltarBlockEntity reloaded = new InfusionAltarBlockEntity(BlockPos.ORIGIN, Blocks.STONE.getDefaultState());
			reloaded.readNbt(save);
			check(reloaded.size() == 9 && !reloaded.getStack(8).isEmpty(), "ninth inventory entry survives save/load");
			altar.removeStack(8);
			NbtCompound oldSave = new NbtCompound();
			altar.writeNbt(oldSave);
			reloaded.readNbt(oldSave);
			check(reloaded.getStack(8).isEmpty() && !reloaded.getStack(7).isEmpty(), "old save retains original five formations");
			System.out.println("Infusion altar: 1/3/6 unlocks, legacy and six-slot NBT, sixth-slot modifiers, both shift-click directions, 45 slot bounds and separation passed.");
		}

		private static ItemStack formation(int level) {
			ItemStack stack = new ItemStack(Items.PAPER);
			stack.getOrCreateNbt().putString(FormationData.NBT_ELEMENT, "fire");
			FormationData.setLevel(stack, level);
			return stack;
		}

		private static void check(boolean condition, String description) {
			if (!condition) throw new AssertionError(description);
		}
	}
}
