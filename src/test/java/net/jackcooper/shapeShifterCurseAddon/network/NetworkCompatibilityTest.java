package net.jackcooper.shapeShifterCurseAddon.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketByteBuf;

public final class NetworkCompatibilityTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static PacketByteBuf buffer() { return new PacketByteBuf(Unpooled.buffer()); }

    public static void main(String[] args) {
        var buffer = buffer();
        try {
            NetworkCompatibility.writeRevision(buffer);
            check(buffer.readableBytes() == 1, "One-byte revision per login direction");
            check(NetworkCompatibility.accepts(true, buffer), "Matching layout accepted");
        } finally { buffer.release(); }
        check(!NetworkCompatibility.accepts(false, null), "Old client missing query responder is rejected without reading a null body");
        for (int revision : new int[]{-1,0,1,3,Integer.MAX_VALUE}) {
            buffer=buffer();
            try { buffer.writeVarInt(revision);check(!NetworkCompatibility.accepts(true,buffer),"Different/invalid layout rejected: "+revision); }
            finally { buffer.release(); }
        }
        for (int[] bytes : new int[][]{{},{0x80},{2,0},{0x80,0x80,0x80,0x80,0x80,0x80}}) {
            buffer=buffer();
            try { for(int b:bytes)buffer.writeByte(b);check(!NetworkCompatibility.accepts(true,buffer),"Malformed/trailing/oversized login frame rejected"); }
            finally { buffer.release(); }
        }
        check(!NetworkCompatibility.rejectUnconfirmedServer(false,false),"Vanilla or non-SSCA server remains allowed");
        check(NetworkCompatibility.rejectUnconfirmedServer(false,true),"Known SSCA server without login confirmation rejected");
        check(!NetworkCompatibility.rejectUnconfirmedServer(true,true),"Matching SSCA server allowed");
        check(!NetworkCompatibility.isSscaNamespace("minecraft")&&!NetworkCompatibility.isSscaNamespace("other_mod"),"Unrelated namespaces never imply SSCA presence");
        check(NetworkCompatibility.isSscaNamespace("ssc_addon")&&NetworkCompatibility.isSscaNamespace("my_addon")
                &&NetworkCompatibility.isSscaNamespace("ssca_neomagic"),"All SSCA-owned namespaces recognized");
        System.out.println("SSCA entity wire compatibility PASS: "+checks+" checks; login lifecycle and native peers are separate acceptance.");
    }
}
