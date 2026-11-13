package net.jackcooper.shapeShifterCurseAddon.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.onixary.shapeShifterCurseFabric.items.accessory.AccessoryItem;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.event.Event;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;

/** Optional public APIs are linked only when installed; no Forge or Accessories dependency is required. */
public final class OptionalAccessoryBridges {
    private static boolean registered;
    private static final ThreadLocal<Map<Object,WeakReference<LivingEntity>>> OWNERS = ThreadLocal.withInitial(WeakHashMap::new);
    private static final Set<String> CALLBACKS=Set.of("curioTick","tick","onEquip","onUnequip","canEquip","canUnequip","curioBreak","onBreak","getDropRule");
    private static final ClassValue<Map<String,Method[]>> METHODS=new ClassValue<>() {
        @Override protected Map<String,Method[]> computeValue(Class<?> type) {
            var methods=new HashMap<String,Method[]>();
            for(var method:type.getMethods()) {
                int count=method.getParameterCount();
                Method[] overloads=methods.get(method.getName());
                if(overloads==null)overloads=new Method[count+1];
                else if(overloads.length<=count)overloads=Arrays.copyOf(overloads,count+1);
                overloads[count]=method;methods.put(method.getName(),overloads);
            }
            return methods;
        }
    };
    private static final class CuriosInventoryMethod {
        static final Method GET=lookup();
        private static Method lookup() {
            try {
                return Class.forName("top.theillusivec4.curios.api.CuriosApi")
                        .getMethod("getCuriosInventory",LivingEntity.class);
            } catch(ReflectiveOperationException e) {
                throw new IllegalStateException("[SSCA] Unable to resolve Curios inventory API",e);
            }
        }
    }
    private OptionalAccessoryBridges() {}
    public static void clearObservers() {OWNERS.remove();}
    public static void inventoryChanged(Object inventory) {
        var reference=OWNERS.get().get(inventory);
        if(reference!=null)DualAccessoryEquipment.changed(reference.get());
    }
    public static void accessoriesContainerChanged(Object container) {
        try {
            Object capability=invoke(container,"capability");
            if(capability!=null)DualAccessoryEquipment.changed((LivingEntity)invoke(capability,"entity"));
        } catch(ReflectiveOperationException e) {throw new IllegalStateException("[SSCA] Accessories change notification failed",e);}
    }
    public static void watchAccessories(LivingEntity entity) {
        if(!FabricLoader.getInstance().isModLoaded("accessories"))return;
        try {
            Object capability=AccessoriesCapabilityMethod.GET.invoke(null,entity);
            if(capability!=null)OWNERS.get().put(invoke(capability,"getHolder"),new WeakReference<>(entity));
        } catch(ReflectiveOperationException e) {throw new IllegalStateException("[SSCA] Accessories observer registration failed",e);}
    }
    private static final class AccessoriesCapabilityMethod {
        static final Method GET=lookup();
        private static Method lookup() {
            try {return Class.forName("io.wispforest.accessories.api.AccessoriesCapability").getMethod("get",LivingEntity.class);}
            catch(ReflectiveOperationException e) {throw new IllegalStateException(e);}
        }
    }
    private static Object invoke(Object receiver,String name,Object... args) throws ReflectiveOperationException {
        Method[] overloads=METHODS.get(receiver.getClass()).get(name);
        Method method=overloads==null||overloads.length<=args.length?null:overloads[args.length];
        if(method==null)throw new NoSuchMethodException(receiver.getClass().getName()+"."+name);
        return method.invoke(receiver,args);
    }
    public static void register() {
        if(registered)return;
        try {
            if(FabricLoader.getInstance().isModLoaded("trinkets")&&!DualAccessoryEquipment.wrappedTrinkets())TrinketsEquipmentBridge.register();
            if(FabricLoader.getInstance().isModLoaded("curios"))register("curios","top.theillusivec4.curios.api.CuriosApi","top.theillusivec4.curios.api.type.capability.ICurioItem","registerCurio");
            if(FabricLoader.getInstance().isModLoaded("accessories")) {
                register("accessories","io.wispforest.accessories.api.AccessoriesAPI","io.wispforest.accessories.api.Accessory","registerAccessory");
                registerAccessoriesChanges();
            }
            registered=true;
        } catch(ReflectiveOperationException|LinkageError e) {
            throw new IllegalStateException("[SSCA] Unable to register accessory effect bridges",e);
        }
    }
    @SuppressWarnings("unchecked")
    private static void registerAccessoriesChanges() throws ReflectiveOperationException {
        Class<?> contract=Class.forName("io.wispforest.accessories.api.events.AccessoryChangeCallback");
        Object callback=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{
            if(method.getDeclaringClass()==Object.class)return switch(method.getName()) {
                case "equals"->proxy==args[0];case "hashCode"->System.identityHashCode(proxy);default->"SSCA equipment change observer";
            };
            Object reference=args[2];
            LivingEntity entity=(LivingEntity)invoke(reference,"entity");
            var data=new AccessoryItem.SlotData(new Identifier("accessories",(String)invoke(reference,"slotName")),(int)invoke(reference,"slot"));
            DualAccessoryEquipment.changed(entity,"accessories",data,(ItemStack)args[1]);
            return null;
        });
        ((Event<Object>)contract.getField("EVENT").get(null)).register(callback);
    }
    private static void register(String backend,String apiName,String contractName,String registration) throws ReflectiveOperationException {
        Class<?> api=Class.forName(apiName),contract=Class.forName(contractName);
        Method register=api.getMethod(registration,Item.class,contract);
        for(Item item:Registries.ITEM) {
            if(!DualAccessoryEquipment.manages(new ItemStack(item)))continue;
            Object bridge=Proxy.newProxyInstance(contract.getClassLoader(),new Class<?>[]{contract},(proxy,method,args)->{
                if(method.getDeclaringClass()==Object.class)return switch(method.getName()) {
                    case "equals"->proxy==args[0];case "hashCode"->System.identityHashCode(proxy);default->"SSCA "+backend+" bridge "+item;
                };
                String name=method.getName();
                boolean curios=backend.equals("curios");
                if(CALLBACKS.contains(name)&&args!=null&&args.length>=2&&(curios?method.getParameterTypes()[0].getName().equals("top.theillusivec4.curios.api.SlotContext"):method.getParameterTypes()[1].getName().equals("io.wispforest.accessories.api.slot.SlotReference"))) {
                    Object reference=args[curios?0:1];ItemStack stack=(ItemStack)args[curios?(name.equals("getDropRule")?args.length-1:1):0];
                    LivingEntity entity=(LivingEntity)invoke(reference,"entity");
                    String slot=(String)invoke(reference,curios?"identifier":"slotName");
                    int index=(int)invoke(reference,curios?"index":"slot");
                    var data=new AccessoryItem.SlotData(new Identifier(backend,slot),index);
                    var accessory=(AccessoryItem)item;
                    if(curios&&Boolean.TRUE.equals(invoke(reference,"cosmetic"))) {
                        if(name.equals("curioTick")||name.equals("onEquip")||name.equals("onUnequip"))return null;
                    }
                    switch(name) {
                        case "curioTick","tick"->{DualAccessoryEquipment.tick(accessory,stack,entity,data,backend);return null;}
                        case "onEquip","onUnequip"->{
                            // Curios: (context, previous/new stack, equipped/removed stack).
                            if(curios)DualAccessoryEquipment.changed(entity,backend,data,(ItemStack)args[name.equals("onEquip")?2:1]);
                            else if(name.equals("onEquip"))DualAccessoryEquipment.changed(entity,backend,data,stack);
                            else DualAccessoryEquipment.changed(entity);
                            return null;
                        }
                        case "canEquip"->{return accessory.canEquip(stack,entity,data);}
                        case "canUnequip"->{return accessory.canUnequip(stack,entity,data);}
                        case "curioBreak","onBreak"->{accessory.onBreak(stack,entity,data);return null;}
                        case "getDropRule"->{
                            // Curios carries the stack last, after damage/looting arguments.
                            if(curios)stack=(ItemStack)args[args.length-1];
                            String rule=accessory.getDropRule(stack,entity,data).name();
                            if(curios)rule=switch(rule){case "KEEP"->"ALWAYS_KEEP";case "DROP"->"ALWAYS_DROP";default->rule;};
                            @SuppressWarnings({"rawtypes","unchecked"}) Object result=Enum.valueOf((Class)method.getReturnType(),rule);return result;
                        }
                    }
                }
                if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args==null?new Object[0]:args);
                throw new UnsupportedOperationException(method.toString());
            });
            register.invoke(null,item,bridge);
        }
        LoggerFactory.getLogger("ssc-addon").info("[SSCA] Registered simultaneous {} accessory effects",backend);
    }
    public static @Nullable Map<Pair<String,String>,List<ItemStack>> curiosSlots(LivingEntity entity) {
        if(!FabricLoader.getInstance().isModLoaded("curios"))return null;
        try {
            Object holder=CuriosInventoryMethod.GET.invoke(null,entity);
            if(holder==null)return Map.of();
            Object resolved=holder instanceof Optional<?>?holder:invoke(holder,"resolve");
            if(!(resolved instanceof Optional<?> optional)||optional.isEmpty())return Map.of();
            Map<?,?> inventories=(Map<?,?>)invoke(optional.get(),"getCurios");
            var result=new LinkedHashMap<Pair<String,String>,List<ItemStack>>();
            for(var entry:inventories.entrySet()) {
                Object stacks=invoke(entry.getValue(),"getStacks"); // Functional inventory only; never cosmetic.
                OWNERS.get().put(stacks,new WeakReference<>(entity));
                int size=(int)invoke(stacks,"getSlots");var items=new ArrayList<ItemStack>();
                for(int n=0;n<size;n++)items.add((ItemStack)invoke(stacks,"getStackInSlot",n));
                result.put(new Pair<>(null,entry.getKey().toString()),items);
            }
            return result;
        } catch(ReflectiveOperationException e) {throw new IllegalStateException("[SSCA] Unable to read functional Curios inventory",e);}
    }
}
