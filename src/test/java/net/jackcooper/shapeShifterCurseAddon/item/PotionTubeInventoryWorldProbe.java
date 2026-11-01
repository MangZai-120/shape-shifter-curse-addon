package net.jackcooper.shapeShifterCurseAddon.item;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;

/** 由世界探针调用，验证真实玩家库存插入、返还和掉落物拾取。 */
public final class PotionTubeInventoryWorldProbe {
    private static int checks;

    private PotionTubeInventoryWorldProbe() {}

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static int verify(TestContext context) {
        checks = 0;
        var player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        var inventory = player.getInventory();
        var holder = PowerHolderComponent.KEY.get(player);
        var power = PowerTypeRegistry.get(new Identifier("shape-shifter-curse", "modify_potion_stack_8"));
        var source = new Identifier("ssc_addon", "potion_tube_inventory_probe");
        check(power != null, "实际使魔药水堆叠power已加载");
        try {
            for (var template : new ItemStack[]{
                    PotionTestTubes.divide(PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.HEALING)),
                    new ItemStack(MoonDustReliefItem.VIAL), new ItemStack(MoonDustReliefItem.BOTTLE),
                    new ItemStack(MoonDustReliefItem.SPLASH)}) {
                int base = PotionTestTubes.isFilledTube(template) ? 3 : 1;
                int boosted = PotionTestTubes.isFilledTube(template) ? 16 : 8;
                inventory.clear();
                check(PotionTestTubes.stackLimit(template, player) == base, "无power时保持对应剂型基础上限");
                var ordinary = template.copyWithCount(base * 2 + 1);
                check(inventory.insertStack(ordinary) && ordinary.isEmpty(), "真实insertStack容纳全部剂量");
                check(inventory.getStack(0).getCount() == base && inventory.getStack(1).getCount() == base
                        && inventory.getStack(2).getCount() == 1, "普通玩家按基础上限拆入空槽");
                check(maxMatching(inventory, template) == base, "普通玩家没有超过基础上限的新堆栈");

                inventory.clear();
                holder.addPower(power, source);
                check(PotionTestTubes.stackLimit(template, player) == boosted, "同一power按剂型提升到八瓶或十六管");
                var powered = template.copyWithCount(boosted + 1);
                check(inventory.insertStack(powered) && powered.isEmpty(), "能力玩家真实insertStack容纳全部剂量");
                check(inventory.getStack(0).getCount() == boosted && inventory.getStack(1).getCount() == 1,
                        "空槽插入正确识别零数量接收栈，并按能力上限拆分");

                inventory.clear();
                inventory.setStack(0, template.copyWithCount(base));
                var merge = template.copyWithCount(boosted - base - 1);
                check(inventory.getOccupiedSlotWithRoomForStack(merge) == 0, "能力玩家能找到可继续合并的槽位");
                check(inventory.insertStack(merge) && inventory.getStack(0).getCount() == boosted - 1,
                        "真实插入优先合并到已有剂量");
                var dropped = new ItemEntity(context.getWorld(), player.getX(), player.getY(), player.getZ(), template.copyWithCount(1));
                dropped.setPickupDelay(0);
                try {
                    dropped.onPlayerCollision(player);
                    check(inventory.getStack(0).getCount() == boosted && dropped.isRemoved(),
                            "真实掉落物碰撞拾取可合并到形态上限");
                } finally {
                    dropped.discard();
                }

                inventory.clear();
                inventory.setStack(0, template.copyWithCount(boosted - 1));
                var offered = template.copyWithCount(1);
                inventory.offer(offered, false);
                check(offered.isEmpty() && inventory.getStack(0).getCount() == boosted,
                        "offer返还路径按能力上限分割并合并，不产生负分割量");

                holder.removePower(power, source);
                var intoOld = template.copyWithCount(1);
                check(!inventory.insertStack(0, intoOld) && intoOld.getCount() == 1
                        && inventory.getStack(0).getCount() == boosted,
                        "失去power后的旧十六管栈保留，显式插槽不会复制物品或倒扣");
                var afterLoss = template.copyWithCount(base + 1);
                inventory.offer(afterLoss, false);
                check(afterLoss.isEmpty() && inventory.getStack(0).getCount() == boosted
                        && inventory.getStack(1).getCount() == base && inventory.getStack(2).getCount() == 1,
                        "失去power后返还物跳过旧超量栈，按三管放入新槽");

                inventory.clear();
                var colored = template.copyWithCount(1);
                colored.getOrCreateNbt().putInt("CustomPotionColor", 0xFF3344);
                var otherColor = template.copyWithCount(1);
                otherColor.getOrCreateNbt().putInt("CustomPotionColor", 0x44FF33);
                inventory.setStack(0, colored);
                check(!inventory.insertStack(0, otherColor) && otherColor.getCount() == 1
                        && inventory.getStack(0).getCount() == 1, "显式插槽不合并不同NBT的药液");
                check(inventory.insertStack(otherColor) && inventory.getStack(1).getCount() == 1,
                        "自动插入将不同NBT保留在独立槽位");
            }

            inventory.clear();
            var emptyTubes = new ItemStack(PotionTestTubes.EMPTY, 64);
            check(inventory.insertStack(emptyTubes) && inventory.getStack(0).getCount() == 64,
                    "空试管仍按原材料六十四个堆叠");
            System.out.println("POTION_TUBE_INVENTORY_WORLD_PASS checks=" + checks);
            return checks;
        } finally {
            holder.removePower(power, source);
            inventory.clear();
            player.discard();
        }
    }

    private static int maxMatching(PlayerInventory inventory, ItemStack template) {
        int maximum = 0;
        for (var stack : inventory.main) {
            if (ItemStack.canCombine(stack, template)) maximum = Math.max(maximum, stack.getCount());
        }
        return maximum;
    }
}
