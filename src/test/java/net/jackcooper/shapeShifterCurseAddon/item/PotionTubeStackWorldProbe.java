package net.jackcooper.shapeShifterCurseAddon.item;

import com.google.gson.JsonObject;
import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.PotionUtil;
import net.minecraft.potion.Potions;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.TestContext;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameMode;
import net.onixary.shapeShifterCurseFabric.additional_power.ModifyPotionStackPower;

/** 真实服务端槽位和点击路径；不以直接调用容量helper代替物品移动验证。 */
public final class PotionTubeStackWorldProbe {
    private static int checks;
    private static final Identifier SOURCE = new Identifier("ssc_addon", "tube_stack_probe");

    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }

    public static int verify(TestContext t) {
        checks = 0;
        if (io.github.apace100.apoli.Apoli.config == null)
            io.github.apace100.apoli.Apoli.config = new io.github.apace100.apoli.util.ApoliConfig();
        var p = t.createMockCreativeServerPlayerInWorld();
        p.changeGameMode(GameMode.SURVIVAL);
        var holder = PowerHolderComponent.KEY.get(p);
        var familiar = PowerTypeRegistry.get(new Identifier("shape-shifter-curse", "modify_potion_stack_8"));
        var water = PowerTypeRegistry.get(new Identifier("shape-shifter-curse", "modify_water_potion_stack_16"));
        var ordinary = PotionTestTubes.FILLED.getDefaultStack();
        var moon = new ItemStack(MoonDustReliefItem.VIAL);
        check(ordinary.getMaxCount() == 3 && moon.getMaxCount() == 3, "两类装药试管的物品基础上限都是三管");
        check(new ItemStack(PotionTestTubes.EMPTY).getMaxCount() == 64, "空管保持六十四个材料堆叠");
        check(MoonDustReliefItem.BOTTLE.getMaxCount() == 16 && MoonDustReliefItem.SPLASH.getMaxCount() == 16,
                "不改变瓶装与支援月尘药水原有上限");
        for (var tube : new ItemStack[]{ordinary, moon}) {
            clickChecks(p, tube, 3);
            shiftChecks(p, tube, 3);
        }
        holder.addPower(familiar, SOURCE);
        for (var tube : new ItemStack[]{ordinary, moon}) {
            check(p.playerScreenHandler.getSlot(36).getMaxItemCount(tube) == 16, "现有使魔power把试管槽位提升为十六管");
            clickChecks(p, tube, 16);
            shiftChecks(p, tube, 16);
        }
        check(p.playerScreenHandler.getSlot(36).getMaxItemCount(new ItemStack(Items.POTION)) == 8,
                "普通药水仍遵循原power的八瓶规则");
        holder.removePower(familiar, SOURCE);
        holder.addPower(water, SOURCE);
        var waterTube = PotionTestTubes.divide(PotionUtil.setPotion(new ItemStack(Items.POTION), Potions.WATER));
        check(PotionTestTubes.stackLimit(waterTube, p) == 16, "水瓶限定power仅给装水试管资格");
        check(PotionTestTubes.stackLimit(ordinary, p) == 3 && PotionTestTubes.stackLimit(moon, p) == 3,
                "水瓶限定power不提高治疗或月尘试管容量");
        holder.removePower(water, SOURCE);
        var id = new Identifier("ssc_addon", "tube_stack_probe_sixty_four");
        PowerFactory<ModifyPotionStackPower> factory = new PowerFactory<>(id, new SerializableData(),
                data -> (type, entity) -> new ModifyPotionStackPower(type, entity, 64, false));
        var large = new PowerType<>(id, factory.read(new JsonObject()));
        PowerTypeRegistry.register(id, large);
        holder.addPower(large, SOURCE);
        check(p.playerScreenHandler.getSlot(36).getMaxItemCount(ordinary) == 16,
                "即使其它数据包power的瓶数更高，试管仍固定最多十六管");
        holder.removePower(large, SOURCE);
        shiftChecks(p, ordinary, 3);
        check(p.playerScreenHandler.getSlot(36).getMaxItemCount(ordinary) == 3,
                "失去power后槽位立即恢复三管，不靠客户端缓存");
        clear(p);
        System.out.println("POTION_TUBE_STACK_WORLD_PASS checks=" + checks);
        return checks;
    }

    private static void clear(ServerPlayerEntity p) {
        p.getInventory().clear();
        p.playerScreenHandler.setCursorStack(ItemStack.EMPTY);
    }

    private static void clickChecks(ServerPlayerEntity p, ItemStack tube, int limit) {
        clear(p);
        p.playerScreenHandler.setCursorStack(tube.copyWithCount(20));
        p.playerScreenHandler.onSlotClick(36, 0, SlotActionType.PICKUP, p);
        check(p.getInventory().getStack(0).getCount() == limit
                        && p.playerScreenHandler.getCursorStack().getCount() == 20 - limit,
                "真实左键放置按资格限流且不丢弃余量：" + limit);
        clear(p);
        p.getInventory().setStack(0, tube.copyWithCount(limit - 1));
        p.getInventory().setStack(1, tube.copyWithCount(2));
        p.playerScreenHandler.setCursorStack(tube.copyWithCount(1));
        p.playerScreenHandler.onSlotClick(37, 0, SlotActionType.PICKUP_ALL, p);
        check(p.playerScreenHandler.getCursorStack().getCount() == limit
                        && p.getInventory().count(tube.getItem()) == 2,
                "真实双击合并按资格封顶，物品总数守恒：" + limit);
        clear(p);
        var different = tube.copyWithCount(2);
        different.getOrCreateNbt().putInt("CustomPotionColor", 0xC040FF);
        p.getInventory().setStack(0, different);
        p.playerScreenHandler.setCursorStack(tube.copyWithCount(1));
        p.playerScreenHandler.onSlotClick(36, 0, SlotActionType.PICKUP_ALL, p);
        check(p.playerScreenHandler.getCursorStack().getCount() == 1
                        && p.getInventory().getStack(0).getCount() == 2,
                "不同药液NBT不可双击混堆");
    }

    private static void shiftChecks(ServerPlayerEntity p, ItemStack tube, int limit) {
        clear(p);
        var chest = GenericContainerScreenHandler.createGeneric9x1(91, p.getInventory());
        var inventory = chest.getInventory();
        inventory.setStack(0, tube.copyWithCount(16));
        chest.onSlotClick(0, 0, SlotActionType.QUICK_MOVE, p);
        check(inventory.getStack(0).isEmpty() && p.getInventory().count(tube.getItem()) == 16,
                "真实Shift取出十六管不丢失数量");
        for (int i = 0; i < 36; i++) {
            var actual = p.getInventory().getStack(i);
            check(actual.isEmpty() || actual.getCount() <= limit, "Shift进入玩家槽位不越过资格上限：" + limit);
        }
        chest.onClosed(p);
    }
}
