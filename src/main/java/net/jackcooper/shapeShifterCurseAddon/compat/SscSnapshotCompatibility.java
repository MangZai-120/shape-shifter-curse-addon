package net.jackcooper.shapeShifterCurseAddon.compat;

import java.io.IOException;
import java.nio.file.Files;
import net.fabricmc.loader.api.ModContainer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/** Snapshot API check without loading Minecraft or any SSC gameplay class during pre-launch. */
public final class SscSnapshotCompatibility {
    private static final String CONTROLLER =
            "net/onixary/shapeShifterCurseFabric/render/form_render/sub_controller/FormEyeBlinkController.class";

    private SscSnapshotCompatibility() {}

    public static boolean supportsMultiEye(byte[] bytecode) {
        boolean[] found = {false};
        new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override public FieldVisitor visitField(int access, String name, String descriptor,
                                                     String signature, Object value) {
                if (name.equals("EYE_ROOT_BONES") && descriptor.equals("Ljava/util/List;")
                        && (access & Opcodes.ACC_PUBLIC) != 0) found[0] = true;
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    public static void requireSupportedSnapshot(ModContainer ssc) {
        boolean supported;
        try {
            var path = ssc.findPath(CONTROLLER);
            supported = path.isPresent() && supportsMultiEye(Files.readAllBytes(path.get()));
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("[SSCA] 无法检查 SSC 快照接口 / Cannot inspect SSC snapshot API", exception);
        }
        if (!supported) throw new IllegalStateException(
                "[SSCA] 当前 SSC 1.10.0 快照缺少多眼骨接口。请使用本次 SSCA 构建附带的 SSC 主包"
                + "（2026-11-04 多眼骨更新或兼容更新版本）；仅相同版本号不足以判断兼容。\n"
                + "[SSCA] This SSC snapshot lacks the multi-eye API. Install the SSC JAR supplied with this SSCA build"
                + " (the 2026-11-04 multi-eye update or a compatible newer build).");
    }
}
