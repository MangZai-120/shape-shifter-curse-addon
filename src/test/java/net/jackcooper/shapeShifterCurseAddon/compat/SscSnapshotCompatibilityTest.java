package net.jackcooper.shapeShifterCurseAddon.compat;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/** Same displayed SSC version must not hide incompatible snapshot fields. No world is created. */
public final class SscSnapshotCompatibilityTest {
    public static void main(String[] args) throws Exception {
        check(!SscSnapshotCompatibility.supportsMultiEye(controller("EYE_ROOT_BONE", "Ljava/lang/String;", Opcodes.ACC_PUBLIC)), "Old single-eye snapshot rejected");
        check(!SscSnapshotCompatibility.supportsMultiEye(controller("EYE_ROOT_BONES", "Ljava/lang/String;", Opcodes.ACC_PUBLIC)), "Wrong field type rejected");
        check(!SscSnapshotCompatibility.supportsMultiEye(controller("EYE_ROOT_BONES", "Ljava/util/List;", Opcodes.ACC_PRIVATE)), "Inaccessible API rejected");
        check(SscSnapshotCompatibility.supportsMultiEye(controller("EYE_ROOT_BONES", "Ljava/util/List;", Opcodes.ACC_PUBLIC)), "Multi-eye API accepted");
        try (var stream = SscSnapshotCompatibilityTest.class.getResourceAsStream(
                "/net/onixary/shapeShifterCurseFabric/render/form_render/sub_controller/FormEyeBlinkController.class")) {
            check(stream != null && SscSnapshotCompatibility.supportsMultiEye(stream.readAllBytes()), "Actual compile dependency provides multi-eye API");
        }
        System.out.println("SSC snapshot compatibility: 5 bytecode/API checks passed; no world started.");
    }

    private static byte[] controller(String field, String descriptor, int access) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "Controller", null, "java/lang/Object", null);
        writer.visitField(access, field, descriptor, null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void check(boolean condition, String why) {
        if (!condition) throw new AssertionError(why);
    }
}
