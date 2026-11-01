package net.jackcooper.shapeShifterCurseAddon.client.tutorial;

import java.util.UUID;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.tutorial.SpellTutorialManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

/** 等待世界、加载遮罩与其它界面就绪；断线即丢弃本连接的待播请求。 */
public final class SpellTutorialClient {
    private record Offer(ClientPlayNetworkHandler connection,UUID nonce,UUID world,int[] glyphs) {}
    private static Offer pending;
    private static int delay;
    private SpellTutorialClient() {}
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SpellTutorialManager.OFFER,(client,handler,buf,sender)->{
            if(buf.readableBytes()!=36)return;
            UUID nonce=buf.readUuid(),world=buf.readUuid();boolean automatic=buf.readBoolean();
            int[] glyphs={buf.readUnsignedByte(),buf.readUnsignedByte(),buf.readUnsignedByte()};
            for(int glyph:glyphs)if(glyph>=18)return;
            client.execute(()->{pending=new Offer(handler,nonce,world,glyphs);delay=automatic?60:0;});
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->{pending=null;delay=0;});
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(pending==null)return;
            if(client.getNetworkHandler()!=pending.connection){pending=null;return;}
            if(client.world==null||client.player==null||!client.player.isAlive()||client.getOverlay()!=null||client.currentScreen!=null)return;
            if(delay>0){delay--;return;}
            Offer shown=pending;pending=null;
            client.setScreen(new SpellTutorialScreen(shown.glyphs));
            var ack=PacketByteBufs.create();ack.writeByte(1);ack.writeUuid(shown.nonce);
            ClientPlayNetworking.send(SpellTutorialManager.ACTION,ack);
        });
    }
    public static void replay() {
        MinecraftClient client=MinecraftClient.getInstance();
        if(client.player==null||!ClientPlayNetworking.canSend(SpellTutorialManager.ACTION))return;
        client.player.closeHandledScreen();
        var packet=PacketByteBufs.create();packet.writeByte(0);ClientPlayNetworking.send(SpellTutorialManager.ACTION,packet);
    }
}
