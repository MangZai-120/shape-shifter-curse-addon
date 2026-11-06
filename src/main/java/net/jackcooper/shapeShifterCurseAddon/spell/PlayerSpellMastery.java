package net.jackcooper.shapeShifterCurseAddon.spell;

import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.ssca.neomagic.NeoRuntime;
import net.ssca.neomagic.NeoState;

/** 玩家施法成长存 UUID 档案；书等级与升级经验独立保留，不提供承载上限。 */
public final class PlayerSpellMastery {
    /** Experience is stored in tenths; every 600 XP grants another 50 safety. */
    public static final int EXPERIENCE_PER_LEVEL_TEN = 6000;
    private static int clientExpTen;
    private PlayerSpellMastery() {}
    public static int clientExpTen(){return clientExpTen;}
    public static void acceptClientExperience(int experience){clientExpTen=Math.max(0,experience);}

    public static int tier(NeoState.PlayerData data) {
        return tier(data.spellbookMasteryExpTen);
    }
    public static int tier(int experience) {
        experience=Math.max(0,experience);
        return experience / EXPERIENCE_PER_LEVEL_TEN;
    }
    public static int thresholdTen(int level){return EXPERIENCE_PER_LEVEL_TEN;}
    public static int progressTen(int experience){
        experience=Math.max(0,experience);
        return experience % EXPERIENCE_PER_LEVEL_TEN;
    }
    public static int bonus(NeoState.PlayerData data) {
        return bonus(data.spellbookMasteryExpTen);
    }
    public static int bonus(int experience){return tier(experience)*50;}
    /** 旧书取最高经验迁移，不叠加复制品；清空书中旧经验，交易后不会再次转移。 */
    public static void migrate(ServerPlayerEntity player,ItemStack book) {
        if(book==null||book.isEmpty()||SpellbookData.getLevel(book)<SpellbookData.MAX_LEVEL)return;
        int legacy=SpellbookData.getExpTen(book);
        if(legacy<=0)return;
        var data=NeoRuntime.data(player);
        data.spellbookMasteryExpTen=Math.max(data.spellbookMasteryExpTen,legacy);
        SpellbookData.setExpTen(book,0);
        NeoRuntime.dirty(player);
    }
    /** 玩家总能获得成功施法经验；未满级书另外保留原来的装备升级进度。 */
    public static void grant(ServerPlayerEntity player,ItemStack book,int amountTen) {
        if(player==null||amountTen<=0)return;
        if(book!=null&&!book.isEmpty()&&SpellbookData.getLevel(book)<SpellbookData.MAX_LEVEL){
            SpellbookData.addExpTen(book,amountTen);
        }
        migrate(player,book);
        var data=NeoRuntime.data(player);
        data.spellbookMasteryExpTen=(int)Math.min(Integer.MAX_VALUE,(long)data.spellbookMasteryExpTen+SpellbookData.boostedExpTen(amountTen));
        NeoRuntime.dirty(player);
    }
}
