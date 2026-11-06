package net.jackcooper.shapeShifterCurseAddon.client.palette;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger("ssca-palette");

    /** 单个槽位数据。code 为空表示该槽为空。 */
    public static class Slot {
        public String name = "";
        public String code = "";
        public boolean isEmpty() { return code == null || code.isEmpty(); }
        public void clear() { this.name = ""; this.code = ""; }
    }

    /** 持久化文件结构（按 scope 各存一份；同步开关只取 global 里的值）。 */
    public static class PersistedData {
        public boolean globalSync = false; // 默认按存档；仅 global.json 中的此字段被认作有效
        public String scope = "";
        public List<Slot> slots = new ArrayList<>();
    }

    private static PalettePresetStore instance;
    public static PalettePresetStore get() {
        if (instance == null) instance = new PalettePresetStore();
        return instance;
    }

    private final List<Slot> slots = new ArrayList<>(SLOT_COUNT);
    // 默认「按存档」：每个存档/服务器各自独立，新世界默认为空，避免跨存档污染。
    private boolean globalSync = false;
    private String activeScopeKey = "global"; // 实际生效的 scope（globalSync=true 时强制 global）
    private boolean pendingChanges;
    private final Path directory;
    private final java.util.function.Supplier<String> scopeDetector;
    private final java.util.function.Supplier<String> legacyScopeDetector;

    private PalettePresetStore() {
        this(FabricLoader.getInstance().getConfigDir().resolve(DIR), PalettePresetStore::detectCurrentScopeKey,
                PalettePresetStore::detectLegacyScopeKey);
    }

    PalettePresetStore(Path directory, java.util.function.Supplier<String> scopeDetector,
                       java.util.function.Supplier<String> legacyScopeDetector) {
        this.directory = directory;
        this.scopeDetector = scopeDetector;
        this.legacyScopeDetector = legacyScopeDetector;
        for (int i = 0; i < SLOT_COUNT; i++) slots.add(new Slot());
        // 启动时只读取 global.json 内的 globalSync 开关；正式数据等 reload 时按当前环境再读
        loadGlobalSwitchOnly();
    }

    public List<Slot> getSlots() { return slots; }
    public boolean isGlobalSync() { return globalSync; }
    public void markDirty() { pendingChanges = true; }

    /** 切换全局同步开关并立即重载对应数据。 */
    public boolean setGlobalSync(boolean value) {
        if (pendingChanges && !save()) return false;
        boolean previous = this.globalSync;
        this.globalSync = value;
        // 同步开关变化要写回 global.json
        if (!saveGlobalSwitch()) {
            this.globalSync = previous;
            return false;
        }
        reloadForCurrentScope();
        return true;
    }

    /** 根据当前是否连接服务器/单机环境，重新加载对应 scope 的槽数据。 */
    public void reloadForCurrentScope() {
        if (pendingChanges && !save()) return;
        activeScopeKey = globalSync ? "global" : scopeDetector.get();
        Path file = resolveScopeFile(activeScopeKey);
        // Read the old filename once when upgrading; retain it because old names could collide.
        boolean legacy = !Files.exists(file) && !globalSync;
        if (legacy) file = resolveScopeFile(legacyScopeDetector.get());
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
            if (legacy) {
                pendingChanges = true;
                save();
            }
        } catch (IOException | RuntimeException ignored) {
            preserveCorruptFile(file);
            LOGGER.error("Failed to read palette presets {}", file, ignored);
        }
    }

    /** 写当前 scope 的数据文件。 */
    public boolean save() {
        Path file = resolveScopeFile(activeScopeKey);
        PersistedData data = new PersistedData();
        data.globalSync = globalSync; // 仅 global.json 中会被回读
        data.scope = activeScopeKey;
        // 浅拷贝：避免 GSON 序列化期间 UI 回调变动 list 结构导致 ConcurrentModification
        data.slots = new java.util.ArrayList<>(slots);
        boolean saved = writeAtomically(file, GSON.toJson(data));
        if (saved) pendingChanges = false;
        return saved;
    }

    /**
     * 将当前 UI 里的全部槽位（含名称+代码）导出为一份可跨设备分享的文件。
     * 调用者（UI 按钮）需负责提示用户。
     *
     * @return 生成的文件绝对路径；IO 失败返回 null
     */
    public Path exportAllToSharedFile() {
        ensureDir();
        Path file = directory.resolve("palette_presets_export.json");
        PersistedData data = new PersistedData();
        data.globalSync = globalSync;
        data.slots = new java.util.ArrayList<>(slots);
        return writeAtomically(file, GSON.toJson(data)) ? file.toAbsolutePath() : null;
    }

    private void loadGlobalSwitchOnly() {
        Path file = resolveScopeFile("global");
        if (!Files.exists(file)) return;
        try {
            PersistedData data = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), PersistedData.class);
            if (data != null) this.globalSync = data.globalSync;
        } catch (IOException | RuntimeException invalid) {
            preserveCorruptFile(file);
            LOGGER.error("Failed to read palette settings {}", file, invalid);
        }
    }

    private boolean saveGlobalSwitch() {
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
            return writeAtomically(file, GSON.toJson(data));
        } catch (IOException | RuntimeException invalid) {
            LOGGER.error("Failed to save palette settings {}", file, invalid);
            return false;
        }
    }

    private Path resolveScopeFile(String key) {
        Path dir = directory;
        if ("global".equals(key)) return dir.resolve(FILE_GLOBAL);
        return dir.resolve(FILE_SCOPE_PREFIX + sanitize(key) + ".json");
    }

    private void ensureDir() {
        try { Files.createDirectories(directory); }
        catch (IOException ignored) {}
    }

    /** 多人取服务器地址；单机取世界目录名；无法判断时退回 "default"。 */
    private static String detectCurrentScopeKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo si = mc.getCurrentServerEntry();
        if (si != null && si.address != null && !si.address.isEmpty()) {
            var address = net.minecraft.client.network.ServerAddress.parse(si.address.trim());
            return scopeKey("server", address.getAddress().toLowerCase(Locale.ROOT) + ":" + address.getPort());
        }
        if (mc.isInSingleplayer() && mc.getServer() != null) {
            return scopeKey("world", mc.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize().toString());
        }
        return "default";
    }

    private static String sanitize(String s) {
        return UNSAFE.matcher(s).replaceAll("_");
    }

    private static String detectLegacyScopeKey() {
        MinecraftClient mc = MinecraftClient.getInstance();
        ServerInfo si = mc.getCurrentServerEntry();
        if (si != null && si.address != null && !si.address.isEmpty()) return "server_" + si.address;
        if (mc.isInSingleplayer() && mc.getServer() != null)
            return "world_" + mc.getServer().getSaveProperties().getLevelName();
        return "default";
    }

    static String scopeKey(String kind, String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            return kind + "_" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    static boolean writeAtomically(Path file, String contents) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException failure) {
            LOGGER.error("Failed to save palette presets {}", file, failure);
            return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
        }
    }

    private static void preserveCorruptFile(Path file) {
        try {
            if (Files.exists(file)) Files.copy(file, file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis()),
                    StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException backupFailure) {
            LOGGER.error("Failed to preserve damaged palette file {}", file, backupFailure);
        }
    }
}
