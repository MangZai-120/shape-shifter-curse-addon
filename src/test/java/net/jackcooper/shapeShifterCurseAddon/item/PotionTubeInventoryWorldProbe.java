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
                    new ItemStack(MoonDustReliefItem.VIAL)}) {
                inventory.clear();
                check(PotionTestTubes.stackLimit(template, player) == 3, "无power试管上限为三");
                var ordinary = template.copyWithCount(7);
                check(inventory.insertStack(ordinary) && ordinary.isEmpty(), "真实insertStack容纳七管");
                check(inventory.getStack(0).getCount() == 3 && inventory.getStack(1).getCount() == 3
                        && inventory.getStack(2).getCount() == 1, "普通玩家按三、三、一拆入空槽");
                check(maxMatching(inventory, template) == 3, "普通玩家没有超过三管的新堆栈");

                inventory.clear();
                holder.addPower(power, source);
                check(PotionTestTubes.stackLimit(template, player) == 16, "原count8能力在试管上对应十六");
                var powered = template.copyWithCount(17);
                check(inventory.insertStack(powered) && powered.isEmpty(), "能力玩家真实insertStack容纳十七管");
                check(inventory.getStack(0).getCount() == 16 && inventory.getStack(1).getCount() == 1,
                        "空槽插入正确识别零数量接收栈，形成十六加一");

                inventory.clear();
                inventory.setStack(0, template.copyWithCount(3));
                var merge = template.copyWithCount(12);
                check(inventory.getOccupiedSlotWithRoomForStack(merge) == 0, "能力玩家可找到三管以上的合并槽");
                check(inventory.insertStack(merge) && inventory.getStack(0).getCount() == 15,
                        "真实插入可将已有三管合并到十五管");
                var dropped = new ItemEntity(context.getWorld(), player.getX(), player.getY(), player.getZ(), template.copyWithCount(1));
                dropped.setPickupDelay(0);
                try {
                    dropped.onPlayerCollision(player);
                    check(inventory.getStack(0).getCount() == 16 && dropped.isRemoved(),
                            "真实掉落物碰撞拾取可合并到十六管");
                } finally {
                    dropped.discard();
                }

                inventory.clear();
                inventory.setStack(0, template.copyWithCount(15));
                var offered = template.copyWithCount(1);
                inventory.offer(offered, false);
                check(offered.isEmpty() && inventory.getStack(0).getCount() == 16,
                        "offer返还路径按能力上限分割并合并，不产生负分割量");

                holder.removePower(power, source);
                var intoOld = template.copyWithCount(1);
                check(!inventory.insertStack(0, intoOld) && intoOld.getCount() == 1
                        && inventory.getStack(0).getCount() == 16,
                        "失去power后的旧十六管栈保留，显式插槽不会复制物品或倒扣");
                var afterLoss = template.copyWithCount(4);
                inventory.offer(afterLoss, false);
                check(afterLoss.isEmpty() && inventory.getStack(0).getCount() == 16
                        && inventory.getStack(1).getCount() == 3 && inventory.getStack(2).getCount() == 1,
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
