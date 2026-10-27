package net.ssca.neomagic;

import java.io.*;
import java.util.zip.*;

/** Bounded compression for research snapshots; persistence keeps its existing NBT format. */
public final class NeoPayload {
    public static final int MAX_DECODED=64*1024*1024;
    public static byte[] pack(byte[] raw) {
        if(raw.length>MAX_DECODED)throw new IllegalArgumentException("Research snapshot exceeds decoding budget");
        try {
            var output=new ByteArrayOutputStream();
            try(var gzip=new GZIPOutputStream(output)){gzip.write(raw);}
            return output.toByteArray();
        }catch(IOException error){throw new IllegalArgumentException("Cannot encode research snapshot",error);}
    }
    public static byte[] unpack(byte[] packed){return unpack(packed,MAX_DECODED);}
    public static byte[] unpack(byte[] packed,int limit) {
        try(var input=new GZIPInputStream(new ByteArrayInputStream(packed));var output=new ByteArrayOutputStream()) {
            byte[] block=new byte[8192];int count,total=0;
            while((count=input.read(block))!=-1){total+=count;if(total>limit)throw new IllegalArgumentException("Research snapshot exceeds decoding budget");output.write(block,0,count);}
            return output.toByteArray();
        }catch(IOException error){throw new IllegalArgumentException("Malformed research snapshot",error);}
    }
    private NeoPayload(){}
}
