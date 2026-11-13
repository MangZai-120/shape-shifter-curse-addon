package net.jackcooper.shapeShifterCurseAddon.compat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.event.SSCEvent;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import net.onixary.shapeShifterCurseFabric.util.Accessory.AccessoryUtils;
import net.onixary.shapeShifterCurseFabric.util.TrinketUtils;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/** One effective equipment view, with physical inventory addresses retained for writes. */
public final class DualAccessoryEquipment {
    private static final String ADDRESS_PREFIX="@ssca/";
    private static final Map<ServerPlayerEntity,State> STATES=new WeakHashMap<>();
    // An integrated server and its client must never share mutable equipment snapshots.
    // Values contain stacks, not their owning entity, so weak keys can be collected.
    private static final ThreadLocal<Map<LivingEntity,View>> VIEWS =
            ThreadLocal.withInitial(WeakHashMap::new);
    private static final Set<ServerPlayerEntity> DIRTY = Collections.newSetFromMap(new IdentityHashMap<>());
    private static boolean initialized;
    private DualAccessoryEquipment() {}
    public static boolean initialized() {return initialized;}
    public static boolean wrappedTrinkets() {
        return Environment.WRAPPED_TRINKETS;
    }
    private static final class Environment {
        static final boolean WRAPPED_TRINKETS = FabricLoader.getInstance().getModContainer("trinkets")
                .map(mod -> mod.getMetadata().getId().equals("tclayer")).orElse(false);
    }

    public record Slot(String backend,@Nullable String group,String name,int index,ItemStack stack) {
        String address() {return backend+"/"+group+"/"+name+"/"+index;}
        public AccessoryItem.SlotData data() {
            return new AccessoryItem.SlotData(new Identifier(backend.equals("trinkets")?"trinket":backend,
                    group==null?name:group+"/"+name),index);
        }
        Slot copy() {return new Slot(backend,group,name,index,stack.copy());}
    }
    private static final class State {
        Map<String,Slot> equipped=Map.of();
        Set<String> conflicts=Set.of();
    }
    private record Observed(ItemStack stack, boolean empty, String effect) {
        Observed(ItemStack stack) {this(stack,stack.isEmpty(),effectId(stack));}
        boolean matches(ItemStack current) {
            return stack==current && empty==current.isEmpty() && effect.equals(effectId(current));
        }
    }
    private static final class View {
        final List<Slot> physical;
        final Map<String,Observed> observed = new HashMap<>();
        final Set<String> preferred;
        final List<Slot> effective;
        final Map<String,Slot> equipped = new LinkedHashMap<>();
        final Set<String> conflicts = new TreeSet<>();
        Map<Pair<String,String>,List<ItemStack>> merged;
        List<Pair<AccessoryItem.SlotData,ItemStack>> all;

        View(List<Slot> physical) {
            this.physical = physical;
            this.preferred = preferred(physical);
            var effective = new ArrayList<Slot>();
            for (int i = 0; i < physical.size(); i++) {
                Slot slot = physical.get(i);
                observed.put(slot.address(),new Observed(slot.stack));
                if (slot.stack.isEmpty()) continue;
                if (suppressed(slot, preferred)) conflicts.add(duplicateKey(slot));
                else {
                    effective.add(slot);
                    if (manages(slot.stack)) equipped.put(slot.address(), slot);
                }
            }
            this.effective = List.copyOf(effective);
        }
    }
    private static View view(LivingEntity entity) {
        if (entity == null) return new View(List.of());
        var cache = VIEWS.get();
        View previous = cache.get(entity);
        if (previous != null) return previous;
        View current = new View(slots(entity));
        cache.put(entity, current);
        return current;
    }
    public static void invalidate(LivingEntity entity) {VIEWS.get().remove(entity);}
    public static void clearCache() {VIEWS.remove();OptionalAccessoryBridges.clearObservers();}
    public static void init() {
        if(initialized)return;
        initialized=true;
        ServerLifecycleEvents.SERVER_STARTING.register(server->OptionalAccessoryBridges.register());
        // Drain only notified players. There is no timer, expiry or periodic inventory scan.
        ServerTickEvents.END_SERVER_TICK.register(server->flushPendingChanges());
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->changed(handler.player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer,newPlayer,alive)->{
            STATES.remove(oldPlayer);DIRTY.remove(oldPlayer);invalidate(oldPlayer);changed(newPlayer);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player,origin,destination)->changed(player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{
            STATES.remove(handler.player);
            DIRTY.remove(handler.player);
            invalidate(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{STATES.clear();DIRTY.clear();clearCache();});
    }
    public static boolean manages(ItemStack stack) {
        if(stack.isEmpty()||!(stack.getItem() instanceof AccessoryItem))return false;
        String namespace=Registries.ITEM.getId(stack.getItem()).getNamespace();
        return namespace.equals("shape-shifter-curse")||namespace.equals("ssc_addon");
    }
    public static boolean manages(Identifier id) {
        return (id.getNamespace().equals("shape-shifter-curse") || id.getNamespace().equals("ssc_addon"))
                && Registries.ITEM.get(id) instanceof AccessoryItem;
    }

    public static List<Slot> slots(LivingEntity entity) {
        if(entity==null)return List.of();
        OptionalAccessoryBridges.watchAccessories(entity);
        var result=new ArrayList<Slot>();
        var backends=new LinkedHashSet<String>();
        backends.add("trinkets");backends.add("accessories");backends.add("curios");
        backends.addAll(AccessoryUtils.activeAccessoryModInterfaces.keySet());
        boolean wrapped=wrappedTrinkets()&&AccessoryUtils.activeAccessoryModInterfaces.containsKey("accessories");
        for(String backend:backends) {
            if(wrapped&&backend.equals("trinkets"))continue; // tclayer exposes the same physical inventory.
            var io=AccessoryUtils.activeAccessoryModInterfaces.get(backend);
            if(io==null)continue;
            // Read Curios only once, and only its functional inventory.
            Map<Pair<String,String>,List<ItemStack>> inventory=backend.equals("curios")
                    ? OptionalAccessoryBridges.curiosSlots(entity) : null;
            if(inventory==null)inventory=io.getEntitySlots(entity);
            if(inventory==null)continue;
            for(var entry:inventory.entrySet()) {
                if(entry.getValue()==null)continue;
                for(int n=0;n<entry.getValue().size();n++) {
                    ItemStack stack=entry.getValue().get(n);
                    result.add(new Slot(backend,entry.getKey().getLeft(),entry.getKey().getRight(),n,stack==null?ItemStack.EMPTY:stack));
                }
            }
        }
        return result;
    }
    private static String type(Slot slot) {
        String name=slot.name;
        if(name.startsWith("trinket_group_"))name=name.substring("trinket_group_".length());
        if(name.contains("-"))name=name.substring(name.lastIndexOf('-')+1);
        return switch(name) {
            case "anklet","bracelet"->"aglet";
            case "hand","hands"->"glove";
            case "head"->"hat";
            case "body"->"cape";
            case "charm"->"face";
            default->name;
        };
    }
    private static String duplicateKey(Slot slot) {return type(slot)+"|"+Registries.ITEM.getId(slot.stack.getItem());}
    private static Set<String> preferred(List<Slot> slots) {
        var keys=new HashSet<String>();
        boolean wrapped=wrappedTrinkets();
        for(var slot:slots)if(manages(slot.stack)&&(slot.backend.equals("trinkets")||wrapped&&slot.backend.equals("accessories")))keys.add(duplicateKey(slot));
        return keys;
    }
    private static boolean suppressed(Slot slot,Set<String> preferred) {
        return slot.backend.equals("curios")&&manages(slot.stack)&&preferred.contains(duplicateKey(slot));
    }
    public static List<Slot> effective(LivingEntity entity) {
        return view(entity).effective;
    }
    public static boolean effective(LivingEntity entity,String backend,AccessoryItem.SlotData data,ItemStack stack) {
        if(!backend.equals("curios"))return true;
        var candidate=new Slot(backend,null,data.slot().getPath(),data.index(),stack);
        return !suppressed(candidate,view(entity).preferred);
    }
    /** Encoded groups prevent a later get/set from silently targeting another backend. */
    public static Map<Pair<String,String>,List<ItemStack>> merged(LivingEntity entity) {
        View view=view(entity);
        if(view.merged!=null)return view.merged;
        var slots=view.physical;var preferred=view.preferred;
        var grouped=new LinkedHashMap<String,List<Slot>>();
        for(var slot:slots)grouped.computeIfAbsent(ADDRESS_PREFIX+slot.backend+"/"+(slot.group==null?"":slot.group)+"|"+slot.name,k->new ArrayList<>()).add(slot);
        var result=new LinkedHashMap<Pair<String,String>,List<ItemStack>>();
        for(var entry:grouped.entrySet()) {
            var list=entry.getValue();var first=list.get(0);
            result.put(new Pair<>(ADDRESS_PREFIX+first.backend+"/"+(first.group==null?"":first.group),first.name),
                    list.stream().map(s->suppressed(s,preferred)?ItemStack.EMPTY:s.stack).toList());
        }
        return view.merged=Collections.unmodifiableMap(result);
    }
    public record Address(String backend,@Nullable String group) {}
    public static @Nullable Address address(@Nullable String group) {
        if(group==null||!group.startsWith(ADDRESS_PREFIX))return null;
        String[] parts=group.substring(ADDRESS_PREFIX.length()).split("/",2);
        return parts.length==2?new Address(parts[0],parts[1].isEmpty()?null:parts[1]):null;
    }
    public static @Nullable Slot find(LivingEntity entity,@Nullable String group,String name,int index) {
        Address address=address(group);Slot empty=null;
        for(var slot:slots(entity)) {
            if(!slot.name.equals(name)||slot.index!=index)continue;
            if(address!=null) {if(slot.backend.equals(address.backend)&&Objects.equals(slot.group,address.group))return slot;}
            else if(Objects.equals(slot.group,group)) {if(!slot.stack.isEmpty())return slot;if(empty==null)empty=slot;}
        }
        return empty;
    }
    public static void tick(AccessoryItem item,ItemStack stack,LivingEntity entity,AccessoryItem.SlotData data,String backend) {
        if(effective(entity,backend,data,stack))item.accessoryTick(stack,entity,data);
    }
    public static void changed(LivingEntity entity) {
        if(entity==null)return;
        if(entity instanceof ServerPlayerEntity player && player.getServer()!=null && !player.getServer().isOnThread()) {
            player.getServer().execute(()->changed(player));return;
        }
        invalidate(entity);
        if(entity instanceof ServerPlayerEntity player)DIRTY.add(player);
    }
    /** Native callbacks also report mana/durability NBT writes: those do not change equipment. */
    public static void changed(LivingEntity entity,String backend,AccessoryItem.SlotData data,ItemStack current) {
        String path=data.slot().getPath();int split=path.indexOf('/');
        String group=backend.equals("trinkets")&&split>=0?path.substring(0,split):null;
        String name=group==null?path:path.substring(split+1);
        View previous=VIEWS.get().get(entity);
        Observed observed=previous==null?null:previous.observed.get(backend+"/"+group+"/"+name+"/"+data.index());
        if(observed==null||!observed.matches(current))changed(entity);
    }
    public static void flushPendingChanges() {
        if(DIRTY.isEmpty())return;
        var pending=DIRTY.toArray(ServerPlayerEntity[]::new);DIRTY.clear();
        for(var player:pending) {
            // A callback may run before its backend commits the write.
            invalidate(player);reconcile(player);
        }
    }
    public static void reconcile(ServerPlayerEntity player) {
        if(!initialized||player.isRemoved())return;
        View view=view(player);
        var equipped=view.equipped;var conflicts=view.conflicts;
        State state=STATES.computeIfAbsent(player,p->new State());var old=state.equipped;
        if(unchanged(old,equipped)&&conflicts.equals(state.conflicts))return;
        var saved=new LinkedHashMap<String,Slot>();equipped.forEach((key,slot)->saved.put(key,slot.copy()));state.equipped=saved;
        Set<Identifier> before=ids(old.values()),after=ids(equipped.values());
        for(var slot:old.values())if(equipped.values().stream().noneMatch(current->sameEffect(slot.stack,current.stack)))
            ((AccessoryItem)slot.stack.getItem()).onUnequip(slot.stack,player,slot.data());
        for(var id:before)if(!after.contains(id)) {
            if(AccessoryUtils.CanAutoExecute(id,"ssca"))TrinketUtils.ApplyAccessoryPowerOnUnEquip(player,id);
            SSCEvent.ACCESSORY_UNEQUIP.invoker().onEvent(player,id,"ssca");
        }
        for(var slot:equipped.values()) {
            var previous=old.get(slot.address());
            if(previous==null||!sameEffect(previous.stack,slot.stack))
                ((AccessoryItem)slot.stack.getItem()).onEquip(slot.stack,player,slot.data());
        }
        for(var id:after)if(!before.contains(id)) {
            if(AccessoryUtils.CanAutoExecute(id,"ssca"))TrinketUtils.ApplyAccessoryPowerOnEquip(player,id);
            SSCEvent.ACCESSORY_EQUIP.invoker().onEvent(player,id,"ssca");
        }
        if(!conflicts.isEmpty()&&!conflicts.equals(state.conflicts)) {
            // Match the small red form-restriction warning used by AddonAccessoryGuard.
            player.sendMessage(Text.translatable("msg.my_addon.accessory_duplicate_detail")
                    .formatted(Formatting.RED), true);
        }
        state.conflicts=conflicts;
    }
    private static boolean unchanged(Map<String,Slot> previous,Map<String,Slot> current) {
        if(previous.size()!=current.size())return false;
        for(var entry:current.entrySet()) {
            Slot before=previous.get(entry.getKey());
            if(before==null||!sameEffect(before.stack,entry.getValue().stack))return false;
        }
        return true;
    }
    private static Set<Identifier> ids(Collection<Slot> slots) {
        var ids=new LinkedHashSet<Identifier>();for(var slot:slots)ids.add(Registries.ITEM.getId(slot.stack.getItem()));return ids;
    }
    private static boolean sameEffect(ItemStack first,ItemStack second) {
        if(first.getItem()!=second.getItem())return false;
        return effectId(first).equals(effectId(second));
    }
    private static String effectId(ItemStack stack) {
        return stack.getItem() instanceof net.onixary.shapeShifterCurseFabric.items.trinkets.CustomTrinket && stack.hasNbt()
                ? stack.getNbt().getString("custom_accessory_id") : "";
    }
    public static List<Pair<AccessoryItem.SlotData,ItemStack>> all(PlayerEntity player) {
        View view=view(player);
        if(view.all==null)view.all=view.effective.stream().map(s->new Pair<>(s.data(),s.stack)).toList();
        return view.all;
    }
}
