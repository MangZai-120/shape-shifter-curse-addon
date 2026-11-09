package net.jackcooper.shapeShifterCurseAddon.spell;

import java.util.*;
import net.minecraft.item.*;
import net.minecraft.block.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;

/** 已退役实验内容的测试替身，使用原版物品，不注册任何游戏内容。 */
public final class NeoTestFixtures {
    public static final Item WAND=Items.STICK, BLUEPRINT=Items.MAP;
    public static final Block TABLE=Blocks.ENCHANTING_TABLE, TARGET=Blocks.TARGET;
    public static final Map<String,Item> SAMPLES=new LinkedHashMap<>();
    static {
        for(var core:NeoModel.Core.values())SAMPLES.put("core:"+core,Items.PAPER);
        for(var carrier:NeoModel.Carrier.values())SAMPLES.put("carrier:"+carrier,Items.PAPER);
        for(var control:NeoModel.Control.values())SAMPLES.put("control:"+control,Items.PAPER);
    }
    public static void start(ServerPlayerEntity player,Hand hand){
        NeoRuntime.start(player,hand,NeoResearchFixtures.bound(player.getStackInHand(hand)));
    }
    public static NeoRuntime.Active active(NeoModel.Session session,NeoState.Experiment record,ItemStack wand,
                                         Hand hand,NeoEffects.Anchor anchor,Random random) {
        return new NeoRuntime.Active(session,record,wand,hand,anchor,random);
    }
}
