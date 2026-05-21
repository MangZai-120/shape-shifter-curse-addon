package net.onixary.shapeShifterCurseFabric.ssc_addon.client.palette;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 客户端配色预设存储。
 * <p>
 * 存储策略（受 {@link #isGlobalSync()} 控制）：
 *   - globalSync=true（默认）：所有数据写入 config/ssca/palette_presets_global.json，跨存档共享
 *   - globalSync=false：按当前 scope（多人=服务器地址，单机=世界目录名）拆分文件
 *
 * 全局开关本身始终保存在 global.json 内，无论 scope 如何都能读到。
 */
public final class PalettePresetStore {
    public static final int SLOT_COUNT = 20;
    private static final String DIR = "ssca";
    private static final String FILE_GLOBAL = "palette_presets_global.json";
    private static final String FILE_SCOPE_PREFIX = "palette_presets_scope_";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._-]");

    /** 单个槽位数据。code 为空表示该槽为空。 */
    public static class Slot {
        public String name = "";
        public String code = "";
        public boolean isEmpty() { return code == null || code.isEmpty(); }
        public void clear() { this.name = ""; this.code = ""; }
    }

    /** 持久化文件结构（按 scope 各存一份；同步开关只取 global 里的值）。 */
    public static class PersistedData {
        public boolean globalSync = true; // 仅 global.json 中的此字段被认作有效
        public List<Slot> slots = new ArrayList<>();
    }

    private static PalettePresetStore instance;
    public static PalettePresetStore get() {
        if (instance == null) instance = new PalettePresetStore();
        return instance;
    }

    private final List<Slot> slots = new ArrayList<>(SLOT_COUNT);
    private boolean globalSync = true;
    private String activeScopeKey = "global"; // 实际生效的 scope（globalSync=true 时强制 global）

    private PalettePresetStore() {
        for (int i = 0; i < SLOT_COUNT; i++) slots.add(new Slot());
        // 启动时只读取 global.json 内的 globalSync 开关；正式数据等 reload 时按当前环境再读
        loadGlobalSwitchOnly();
    }

    public List<Slot> getSlots() { return slots; }
    public boolean isGlobalSync() { return globalSync; }
    public String getActiveScopeKey() { return activeScopeKey; }

    /** 切换全局同步开关并立即重载对应数据。 */
    public void setGlobalSync(boolean value) {
        this.globalSync = value;
        // 同步开关变化要写回 global.json
        saveGlobalSwitch();
        reloadForCurrentScope();
    }

    /** 根据当前是否连接服务器/单机环境，重新加载对应 scope 的槽数据。 */
    public void reloadForCurrentScope() {
        activeScopeKey = globalSync ? "global" : detectCurrentScopeKey();
        Path file = resolveScopeFile(activeScopeKey);
        // 清空 → 读
        for (Slot s : slots) s.clear();
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Type t = new TypeToken<PersistedData>(){}.getType();
            PersistedData data = GSON.fromJson(json, t);
            if (data != null && data.slots != null) {
                for (int i = 0; i < SLOT_COUNT && i < data.slots.size(); i++) {
                    Slot src = data.slots.get(i);
                    if (src == null) continue;
                    slots.get(i).name = src.name == null ? "" : src.name;
                    slots.get(i).code = src.code == null ? "" : src.code;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // 文件损坏时静默重置，避免阻塞游戏启动
        }
    }

    /** 写当前 scope 的数据文件。 */
    public void save() {
        Path file = resolveScopeFile(activeScopeKey);
        ensureDir();
        PersistedData data = new PersistedData();
        data.globalSync = globalSync; // 仅 global.json 中会被回读
        data.slots = slots;
        try {
            Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }

    /**
     * 将当前 UI 里的全部槽位（含名称+代码）导出为一份可跨设备分享的文件。
     * 调用者（UI 按钮）需负责提示用户。
     *
     * @return 生成的文件绝对路径；IO 失败返回 null
     */
    public Path exportAllToSharedFile() {
        ensureDir();
        Path file = FabricLoader.getInstance().getConfigDir().resolve(DIR).resolve("palette_presets_export.json");
        PersistedData data = new PersistedData();
        data.globalSync = globalSync;
        data.slots = slots;
        try {
            Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
            return file.toAbsolutePath();
        } catch (IOException e) {
            return null;
        }
    }

    private void loadGlobalSwitchOnly() {
        Path file = resolveScopeFile("global");
        if (!Files.exists(file)) return;
        try {
            PersistedData data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), PersistedData.class);
            if (data != null) this.globalSync = data.globalSync;
        } catch (IOException | RuntimeException ignored) {}
    }

    private void saveGlobalSwitch() {
        Path file = resolveScopeFile("global");
        ensureDir();
        try {
            PersistedData data;
            if (Files.exists(file)) {
                data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), PersistedData.class);
                if (data == null) data = new PersistedData();
            } else {
                data = new PersistedData();
                data.slots = new ArrayList<>();
                for (int i = 0; i < SLOT_COUNT; i++) data.slots.add(new Slot());
            }
            data.globalSync = globalSync;
            Files.writeString(file, GSON.toJson(data), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {}
    }

    private Path resolveScopeFile(String key) {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve(DIR);
        if ("global".equals(key)) return dir.resolve(FILE_GLOBAL);
        return dir.resolve(FILE_SCOPE_PREFIX + sanitize(key) + ".json");
    }

    private void ensureDir() {
        try { Files.createDirectories(FabricLoader.getInstance().getConfigDir().resolve(DIR)); }
        catch (IOException ignored) {}
    }

    /** 多人取服务器地址；单机取世界目录名；无法判断时退回 "default"。 */
    private String detectCurrentScopeKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo si = mc.getCurrentServerEntry();
        if (si != null && si.address != null && !si.address.isEmpty()) return "server_" + si.address;
        if (mc.isInSingleplayer() && mc.getServer() != null) {
            return "world_" + mc.getServer().getSaveProperties().getLevelName();
        }
        return "default";
    }

    private static String sanitize(String s) {
        return UNSAFE.matcher(s).replaceAll("_");
    }
}
