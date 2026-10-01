package net.jackcooper.shapeShifterCurseAddon.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.jackcooper.shapeShifterCurseAddon.client.screen.SlottedFormationScreen;
import net.jackcooper.shapeShifterCurseAddon.spell.research.FormationResearchNetworking;

public final class FormationResearchClient {
    public static void register(){
        ClientPlayNetworking.registerGlobalReceiver(FormationResearchNetworking.STATE,(client,handler,buf,sender)->{
            int syncId=buf.readVarInt();var state=buf.readNbt();var message=buf.readText();boolean valid=buf.readBoolean();
            // 协议尾部 3 个 VarInt（node/family/level，服务端固定写 -1/-1/0）为旧版预留字段，读完丢弃以保持 buf 对齐
            buf.readVarInt();buf.readVarInt();buf.readVarInt();
            client.execute(()->{
                if(state!=null&&client.currentScreen instanceof SlottedFormationScreen screen&&screen.getScreenHandler().syncId==syncId)screen.accept(state,message,valid);
            });
        });
    }
}
