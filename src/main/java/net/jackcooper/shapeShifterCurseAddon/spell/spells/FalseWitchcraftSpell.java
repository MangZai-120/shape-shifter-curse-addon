package net.jackcooper.shapeShifterCurseAddon.spell.spells;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public final class FalseWitchcraftSpell extends Spell {
    private static final int TICKS_PER_SHARE = 10 * 20;

    public FalseWitchcraftSpell() {
        super(new Identifier("ssc_addon", "false_witchcraft"), SpellRarity.WHITE);
    }

    @Override public Identifier getIconTexture() {
        return new Identifier("ssc_addon", "textures/item/scroll_mask_base.png");
    }

    private Map<Spell, Long> remainingCooldowns(ServerPlayerEntity caster, ItemStack book) {
        var remaining = new LinkedHashMap<Spell, Long>();
        if (book != null && !book.isEmpty()) {
            long now = caster.getWorld().getTime();
            for (int slot = 0; slot < SpellbookData.getSlotCount(book); slot++) {
                ItemStack scroll = SpellbookData.getScroll(book, slot);
                Spell spell = ScrollData.getSpell(scroll);
                if (spell != null && spell != this && !ArcaneAnalysis.isUnanalyzed(scroll)) {
                    long ticks = SharedSpellCooldowns.getEffectiveCooldownEnd(caster, scroll) - now;
                    if (ticks > 0) remaining.merge(spell, ticks, Math::max);
                }
            }
        }
        return remaining;
    }

    @Override public boolean canCast(ServerPlayerEntity caster) {
        return !remainingCooldowns(caster, SpellCastManager.getEquippedBook(caster)).isEmpty();
    }

    @Override public Text getCastFailureMessage(ServerPlayerEntity caster) {
        return Text.translatable("message.ssc_addon.false_witchcraft.no_spells");
    }

    @Override public void cast(ServerPlayerEntity caster, float power, boolean solo) {
        cast(caster, power, solo, 1);
    }

    @Override public void cast(ServerPlayerEntity caster, float power, boolean solo, int level) {
        ItemStack book = SpellCastManager.getEquippedBook(caster);
        var remaining = remainingCooldowns(caster, book);
        var pool = new ArrayList<>(remaining.keySet());
        if (pool.isEmpty()) return;
        int spellLevel = Math.max(1, Math.min(level, getMaxLevel()));
        int shares = Math.max(1, spellLevel - 1);
        int count = Math.min(shares, pool.size());
        var selected = new ArrayList<Spell>(count);
        for (int i = 0; i < count; i++) {
            selected.add(pool.remove(caster.getRandom().nextInt(pool.size())));
        }

        // Settle the shortest cooldown first so its unused share goes to the remaining spells.
        selected.sort(Comparator.comparingLong(remaining::get));
        int budget = shares * TICKS_PER_SHARE;
        var reductions = new HashMap<Spell, Integer>();
        for (int i = 0; i < count; i++) {
            Spell spell = selected.get(i);
            int spellsLeft = count - i;
            int share = (budget + spellsLeft - 1) / spellsLeft;
            int used = (int) Math.min(share, remaining.get(spell));
            reductions.put(spell, used);
            budget -= used;
        }

        long now = caster.getWorld().getTime();
        for (int slot = 0; slot < SpellbookData.getSlotCount(book); slot++) {
            ItemStack scroll = SpellbookData.getScroll(book, slot);
            Integer reduction = reductions.get(ScrollData.getSpell(scroll));
            long end = ScrollData.getCooldownEnd(scroll);
            if (reduction != null && end > now) {
                long reducedEnd = end - reduction;
                ScrollData.setCooldownEnd(scroll, reducedEnd > now ? reducedEnd : 0);
                if (reducedEnd <= now) scroll.getOrCreateNbt().remove("RuneCdTotal");
                SpellbookData.setScroll(book, slot, scroll);
            }
        }
        SharedSpellCooldowns.reduceFor(caster, reductions);
        caster.getServerWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SscAddon.FALSE_WITCHCRAFT_REFRESH_EVENT, SoundCategory.PLAYERS, 0.8F, 1.0F);
    }
}
