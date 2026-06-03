package net.onixary.shapeShifterCurseFabric.ssc_addon.power;

import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.factory.PowerFactory;
import io.github.apace100.calio.data.SerializableData;
import io.github.apace100.calio.data.SerializableDataTypes;
import net.minecraft.block.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// 寄生果蝠被动：周期性给身周农作物随机一次催生 randomTick
// 跨玩家不叠加：同一坐标在一个 tickInterval 周期内只会被任一玩家判定一次
public class FruitBatPlantGrowthPower extends Power {

    // <维度Key, <packedBlockPos, 上次判定时的 server tick>>
    private static final Map<RegistryKey<World>, Map<Long, Long>> RECENT_PROCESSED = new ConcurrentHashMap<>();

    private final int tickInterval;
    private final int radius;
    private final float chance;

    public FruitBatPlantGrowthPower(PowerType<?> type, LivingEntity entity, int tickInterval, int radius, float chance) {
        super(type, entity);
        this.tickInterval = Math.max(1, tickInterval);
        this.radius = Math.max(0, radius);
        this.chance = Math.max(0f, Math.min(1f, chance));
        this.setTicking(true);
    }

    public static PowerFactory<Power> createFactory() {
        return new PowerFactory<>(
                new Identifier("my_addon", "fruit_bat_plant_growth"),
                new SerializableData()
                        .add("tick_interval", SerializableDataTypes.INT, 20)
                        .add("radius", SerializableDataTypes.INT, 5)
                        .add("chance", SerializableDataTypes.FLOAT, 0.05f),
                data -> (type, entity) -> new FruitBatPlantGrowthPower(type, entity,
                        data.getInt("tick_interval"),
                        data.getInt("radius"),
                        data.getFloat("chance"))
        ).allowCondition();
    }

    @Override
    public void tick() {
        World world = entity.getWorld();
        if (world.isClient()) return;
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (entity.age % tickInterval != 0) return;

        long now = serverWorld.getTime();
        Map<Long, Long> dimMap = RECENT_PROCESSED.computeIfAbsent(
                serverWorld.getRegistryKey(), k -> new ConcurrentHashMap<>());

        // 顺手清理过老条目（5 倍周期之外）防止长期累积内存
        long expire = now - (long) tickInterval * 5L;
        dimMap.entrySet().removeIf(e -> e.getValue() < expire);

        BlockPos center = entity.getBlockPos();
        int r = radius;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = serverWorld.getBlockState(cursor);
                    if (!isFarmable(state)) continue;

                    long packed = cursor.asLong();
                    Long last = dimMap.get(packed);
                    // 同一周期内已被任一玩家判定过 -> 跳过，避免叠加
                    if (last != null && now - last < tickInterval) continue;

                    // 标记本格已被本周期判定（无论 5% 是否命中都标记，否则其他玩家会再判一次造成叠加）
                    dimMap.put(packed, now);

                    if (serverWorld.random.nextFloat() >= chance) continue;
                    BlockPos immutable = cursor.toImmutable();
                    state.randomTick(serverWorld, immutable, serverWorld.random);
                }
            }
        }
    }

    // 涵盖 1.20.1 中的农作物方块类型
    private static boolean isFarmable(BlockState state) {
        Block b = state.getBlock();
        return b instanceof CropBlock           // 小麦/胡萝卜/土豆/甜菜/火炬花/陶罐豆
                || b instanceof StemBlock        // 西瓜/南瓜茎
                || b instanceof CocoaBlock       // 可可豆
                || b instanceof SugarCaneBlock   // 甘蔗
                || b instanceof BambooBlock      // 竹子
                || b instanceof BambooSaplingBlock
                || b instanceof NetherWartBlock  // 地狱疣
                || b instanceof SaplingBlock     // 树苗
                || b instanceof SweetBerryBushBlock // 甜浆果丛
                || b instanceof CaveVinesBodyBlock  // 洞穴藤蔓主体
                || b instanceof CaveVinesHeadBlock; // 洞穴藤蔓头（光浆果生长）
    }
}
