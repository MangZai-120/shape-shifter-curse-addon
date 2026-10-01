package net.jackcooper.shapeShifterCurseAddon.spell.research;

import net.minecraft.nbt.*;
import java.util.*;

/** Lives inside the existing always-copied player knowledge component, with a separate NBT key. */
public final class ResearchProgress {
    public int rank;
    public int knownGlyphs, attuned, knownRules;
    public boolean migrated;
    public UUID world;
    public final Set<String> legacy = new HashSet<>(), drawn = new HashSet<>();
    public final Map<String, NbtCompound> references = new LinkedHashMap<>();
    public NbtCompound draft = new NbtCompound();
    public NbtCompound spellDraft = new NbtCompound();
    public String spellTarget = "";
    public final Map<Integer,int[]> slotDrafts = new HashMap<>();
    public final Map<String,int[]> slotReferences = new TreeMap<>();
    public final Set<String> slotCompleted = new HashSet<>();
    public UUID completionToken = UUID.randomUUID();
    public boolean knows(int glyph) { return glyph >= 0 && glyph < 17 && (knownGlyphs & 1 << glyph) != 0; }
    public void reveal(int glyph) { if (glyph >= 0 && glyph < 17) knownGlyphs |= 1 << glyph; }
    public boolean attuned(int family) { return (attuned & 1 << family) != 0; }
    public void bind(UUID id) {
        if(world!=null&&!world.equals(id)){slotDrafts.clear();slotReferences.clear();slotCompleted.clear();completionToken=UUID.randomUUID();}
        if (world != null && !world.equals(id)) { knownGlyphs = 0; knownRules = 0; references.clear(); draft = new NbtCompound(); spellDraft = new NbtCompound(); spellTarget = ""; }
        world = id;
    }
    public NbtCompound write() {
        NbtCompound n = new NbtCompound(); n.putInt("Version", 1); n.putInt("Rank", rank); n.putInt("Glyphs", knownGlyphs);
        n.putInt("Attuned", attuned); n.putInt("Rules", knownRules); n.putBoolean("Migrated", migrated);
        if (world != null) n.putUuid("World", world);
        NbtList old = new NbtList(), completed = new NbtList(); legacy.stream().sorted().forEach(v -> old.add(NbtString.of(v))); drawn.stream().sorted().forEach(v -> completed.add(NbtString.of(v)));
        n.put("Legacy", old); n.put("Drawn", completed);
        NbtCompound refs = new NbtCompound(); references.forEach((key, diagram) -> refs.put(key, diagram.copy())); n.put("References", refs); n.put("Draft", draft.copy());
        n.put("SpellDraft", spellDraft.copy());n.putString("SpellTarget", spellTarget);
        NbtCompound slots=new NbtCompound(),patterns=new NbtCompound();
        slotDrafts.forEach((level,values)->slots.putIntArray(String.valueOf(level),values));
        slotReferences.forEach(patterns::putIntArray);n.put("SlotDrafts",slots);n.put("SlotReferences",patterns);
        NbtList done=new NbtList();slotCompleted.stream().sorted().forEach(key->done.add(NbtString.of(key)));n.put("SlotCompleted",done);
        n.putUuid("CompletionToken",completionToken);return n;
    }
    public void read(NbtCompound n) {
        rank = Math.max(0, Math.min(5, n.getInt("Rank"))); knownGlyphs = n.getInt("Glyphs") & 0x1ffff;
        attuned = n.getInt("Attuned") & 0x7ff; knownRules = n.getInt("Rules") & 0x7ff; migrated = n.getBoolean("Migrated");
        world = n.containsUuid("World") ? n.getUuid("World") : null; legacy.clear(); drawn.clear(); references.clear();
        var old = n.getList("Legacy", NbtElement.STRING_TYPE); for (int i = 0; i < Math.min(55, old.size()); i++) legacy.add(old.getString(i));
        var completed = n.getList("Drawn", NbtElement.STRING_TYPE); for (int i = 0; i < Math.min(55, completed.size()); i++) drawn.add(completed.getString(i));
        var refs = n.getCompound("References");
        for (int f = 0; f < 11; f++) for (int l = 1; l <= 5; l++) { var t = new ResearchTarget(f, l); if (refs.contains(t.id())) {
            references.put(t.id(), refs.getCompound(t.id()).copy());
        } }
        draft = n.getCompound("Draft").copy();spellDraft = n.getCompound("SpellDraft").copy();
        spellTarget = n.getString("SpellTarget");if(spellTarget.length()>128)spellTarget="";
        slotDrafts.clear();slotReferences.clear();slotCompleted.clear();
        for(int level=1;level<=5;level++){int[] values=n.getCompound("SlotDrafts").getIntArray(String.valueOf(level));if(SlottedFormation.validDraft(level,values))slotDrafts.put(level,values);}
        var patterns=n.getCompound("SlotReferences");var done=n.getList("SlotCompleted",NbtElement.STRING_TYPE);
        for(var recipe:SlottedSpellRecipes.all())for(int level=1;level<=5;level++){
            String key=recipe.spell()+":"+level;int[] values=patterns.getIntArray(key);
            if(SlottedFormation.validDraft(level,values))slotReferences.put(key,values);
            for(int index=0;index<Math.min(128,done.size());index++)if(done.getString(index).equals(key))slotCompleted.add(key);
        }
        completionToken=n.containsUuid("CompletionToken")?n.getUuid("CompletionToken"):UUID.randomUUID();
    }
}
