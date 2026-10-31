package net.jackcooper.shapeShifterCurseAddon.item;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.screen.PotionBagScreenHandler;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import net.ssca.neomagic.NeoRuntime;

/** 药水袋真实槽位、shift、嵌套NBT与饮用入口回归。 */
public final class PotionTubeBagWorldProbe {
    private static int checks;
    private static final Identifier SOURCE = new Identifier("ssc_addon", "tube_bag_probe");
    private static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    private static void near(double value, double expected, String why) { check(Math.abs(value - expected) < 1e-5, why + ": " + value); }

    public static int verify(TestContext t) {
        checks = 0;
        var p = t.createMockCreativeServerPlayerInWorld(); p.changeGameMode(GameMode.SURVIVAL); p.setNoGravity(true);
        var pos = t.getAbsolutePos(new BlockPos(10, 3, 10)).toCenterPos(); p.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0, 0);
        var normal = PotionTestTubes.divide(PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.REGENERATION));
        var moon = new ItemStack(MoonDustReliefItem.VIAL);
        check(PotionBagItem.isStorable(normal) && PotionBagItem.isStorable(moon), "普通与月尘试管均可入袋");
        check(!PotionBagItem.isStorable(new ItemStack(PotionTestTubes.EMPTY)), "空管不能当药水存入药水袋");
        check(PotionBagItem.maxPerSlot(normal, p) == 3 && PotionBagItem.maxPerSlot(moon, p) == 3, "无power时袋内两类试管上限三管");
        check(PotionBagItem.maxPerSlot(new ItemStack(Items.POTION), p) == 8, "普通瓶装药水仍每格八瓶");
        check(PotionBagItem.maxPerSlot(new ItemStack(SscAddon.INFINITE_ENERGY_POTION), p) == 1, "无限药水仍每格一瓶");
        shiftIntoBag(p, normal, 3);
        shiftIntoBag(p, moon, 3);

        var type = PowerTypeRegistry.get(new Identifier("shape-shifter-curse", "modify_potion_stack_8"));
        check(type != null, "使用真实原版药水堆叠power");
        var powers = PowerHolderComponent.KEY.get(p); powers.addPower(type, SOURCE);
        check(PotionBagItem.maxPerSlot(normal, p) == 16 && PotionBagItem.maxPerSlot(moon, p) == 16, "符合power时袋内两类试管上限十六");
        shiftIntoBag(p, normal, 16);
        shiftIntoBag(p, moon, 16);
        var quickBag = new ItemStack(SscAddon.POTION_BAG); var source = normal.copyWithCount(32);
        check(PotionBagItem.insertIntoBag(quickBag, source, p) == 32 && source.isEmpty(), "玩家感知快捷存入支持每格十六管");
        check(PotionBagScreenHandler.getStoredStack(quickBag, 1).getCount() == 16
                && PotionBagScreenHandler.getStoredStack(quickBag, 2).getCount() == 16, "快捷存入优先非快捷栏并使用相同容量");
        powers.removePower(type, SOURCE);
        var legacyBag = new ItemStack(SscAddon.POTION_BAG);
        PotionBagScreenHandler.setStoredStack(legacyBag, 0, moon.copyWithCount(16));
        var named = normal.copyWithCount(16); named.setCustomName(net.minecraft.text.Text.literal("saved tube"));
        PotionBagScreenHandler.setStoredStack(legacyBag, 7, named);
        var screen = new PotionBagScreenHandler(81, p.getInventory(), legacyBag);
        check(screen.getSlot(0).getStack().getCount() == 16 && screen.getSlot(7).getStack().getCount() == 16,
                "无power加载旧十六管存档也不截断或覆盖后续槽位");
        check(screen.getSlot(0).getMaxItemCount(moon) == 3, "保存旧数量的同时，新插入仍限制为三管");
        screen.onClosed(p);
        var saved = ItemStack.fromNbt(legacyBag.writeNbt(new NbtCompound()));
        check(PotionBagScreenHandler.getStoredStack(saved, 0).getCount() == 16
                && PotionBagScreenHandler.getStoredStack(saved, 7).getCount() == 16
                && PotionBagScreenHandler.getStoredStack(saved, 7).getName().getString().equals("saved tube"), "袋子存盘完整保留数量和自定义NBT");

        var oldCall = new ItemStack(SscAddon.POTION_BAG); source = normal.copyWithCount(6);
        check(PotionBagItem.insertIntoBag(oldCall, source) == 6
                && PotionBagScreenHandler.getStoredStack(oldCall, 1).getCount() == 3
                && PotionBagScreenHandler.getStoredStack(oldCall, 2).getCount() == 3, "旧双参快捷入口保持默认三管容量");

        var bag = equippedBag(p, normal.copyWithCount(3));
        var item = (PotionBagItem) bag.getItem();
        check(item.getMaxUseTime(bag) == 15 && item.getUseAction(bag) == UseAction.DRINK, "普通试管13tick经药水袋15%开销向上取整为15tick");
        check(item.use(t.getWorld(), p, Hand.MAIN_HAND).getResult().isAccepted(), "普通试管走真实药水袋右键入口");
        p.stopUsingItem(); p.clearStatusEffects(); item.finishUsing(bag, t.getWorld(), p);
        check(p.getStatusEffect(StatusEffects.REGENERATION) != null && p.getStatusEffect(StatusEffects.REGENERATION).getDuration() == 300,
                "药水袋施加三分之一再生时长，而非全瓶900tick");
        check(PotionBagScreenHandler.getStoredStack(bag, 0).getCount() == 2, "袋内生存饮用只消耗一管");
        check(p.getInventory().count(PotionTestTubes.EMPTY) == 1 && p.getInventory().count(Items.GLASS_BOTTLE) == 0, "仅返还一个空管，不返玻璃瓶或重复返还");

        bag = equippedBag(p, moon); NeoRuntime.refreshForm(p); NeoRuntime.data(p).burden = 100;
        check(item.getMaxUseTime(bag) == 19, "月尘试管16tick经15%开销向上取整为19tick");
        item.finishUsing(bag, t.getWorld(), p);
        near(NeoRuntime.data(p).burden, 80, "月尘试管走自身真实入口降低二十崩解度");
        check(PotionBagScreenHandler.getStoredStack(bag, 0).isEmpty() && p.getInventory().count(PotionTestTubes.EMPTY) == 1,
                "最后一管清空袋内槽位并将空管返还玩家");

        p.changeGameMode(GameMode.CREATIVE); bag = equippedBag(p, moon.copyWithCount(2)); NeoRuntime.data(p).burden = 100;
        item.finishUsing(bag, t.getWorld(), p);
        near(NeoRuntime.data(p).burden, 80, "创造饮用仍正常产生药效");
        check(PotionBagScreenHandler.getStoredStack(bag, 0).getCount() == 2 && p.getInventory().count(PotionTestTubes.EMPTY) == 0,
                "创造饮用不消耗袋内试管也不生成空管");
        bag = equippedBag(p, PotionUtil.setPotion(new ItemStack(Items.POTION, 2), Potions.HEALING));
        check(item.getMaxUseTime(bag) == 37, "原普通药水袋饮用时长仍为三十七tick");
        item.finishUsing(bag, t.getWorld(), p);
        check(PotionBagScreenHandler.getStoredStack(bag, 0).getCount() == 2 && p.getInventory().count(Items.GLASS_BOTTLE) == 0,
                "普通瓶装药水创造饮用也不消耗或生成瓶子");

        p.changeGameMode(GameMode.SURVIVAL); bag = equippedBag(p, new ItemStack(PotionTestTubes.EMPTY));
        check(item.getMaxUseTime(bag) == 0 && item.getUseAction(bag) == UseAction.NONE
                && item.use(t.getWorld(), p, Hand.MAIN_HAND).getResult() == ActionResult.PASS, "旧NBT中的空管也不能触发饮用");

        bag = equippedBag(p, moon);
        for (int i = 0; i < 36; i++) if (i != 8) p.getInventory().setStack(i, new ItemStack(Items.STONE, 64));
        int drops = t.getWorld().getEntitiesByClass(ItemEntity.class, p.getBoundingBox().expand(4), e -> e.getStack().isOf(PotionTestTubes.EMPTY)).size();
        item.finishUsing(bag, t.getWorld(), p);
        check(p.getInventory().count(PotionTestTubes.EMPTY) == 0 && t.getWorld().getEntitiesByClass(ItemEntity.class,
                p.getBoundingBox().expand(4), e -> e.getStack().isOf(PotionTestTubes.EMPTY)).size() == drops + 1, "满背包只掉落一个返还空管");
        bag = equippedBag(p, moon.copyWithCount(3));
        var ownedScreen = new PotionBagScreenHandler(82, p.getInventory(), bag);
        check(ownedScreen.canUse(p), "玩家仍持有原袋子时允许操作真实界面");
        p.getInventory().setStack(8, ItemStack.EMPTY);
        check(!ownedScreen.canUse(p), "袋子被形态回收后旧界面立即拒绝操作，防止重复取出副本");
        p.getInventory().setStack(8, bag.copy());
        check(!ownedScreen.canUse(p), "相同NBT的另一只袋子不能继续操作原袋子界面");
        ownedScreen.onClosed(p);
        System.out.println("POTION_TUBE_BAG_WORLD_PASS checks=" + checks);
        return checks;
    }

    private static ItemStack equippedBag(ServerPlayerEntity p, ItemStack potion) {
        p.getInventory().clear(); p.currentScreenHandler = p.playerScreenHandler;
        var bag = new ItemStack(SscAddon.POTION_BAG); PotionBagScreenHandler.setStoredStack(bag, 0, potion.copy());
        p.getInventory().selectedSlot = 8; p.getInventory().setStack(8, bag);
        return bag;
    }

    private static void shiftIntoBag(ServerPlayerEntity p, ItemStack tube, int cap) {
        var bag = equippedBag(p, ItemStack.EMPTY);
        p.getInventory().setStack(9, tube.copyWithCount(20));
        var handler = new PotionBagScreenHandler(80, p.getInventory(), bag); p.currentScreenHandler = handler;
        handler.onSlotClick(9, 0, SlotActionType.QUICK_MOVE, p);
        int count = 0;
        for (int i = 0; i < 9; i++) {
            var stored = handler.getSlot(i).getStack(); count += stored.getCount();
            check(stored.isEmpty() || stored.getCount() <= cap, "shift进袋每个槽位遵守试管容量" + cap);
        }
        check(count == 20 && p.getInventory().getStack(9).isEmpty(), "shift进袋不吞管且不漏管");
        handler.onClosed(p); p.currentScreenHandler = p.playerScreenHandler;
    }
}
