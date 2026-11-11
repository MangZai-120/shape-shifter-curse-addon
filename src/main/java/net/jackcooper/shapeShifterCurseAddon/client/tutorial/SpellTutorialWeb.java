package net.jackcooper.shapeShifterCurseAddon.client.tutorial;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.jackcooper.shapeShifterCurseAddon.client.SpellcastKeybindings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import org.slf4j.LoggerFactory;

/** The research table uses the same synchronized video player as form tutorials. */
public final class SpellTutorialWeb {
    private static final String PAGE="spell-research";
    private static final String ONLINE="https://shape-shifter-curse-addon.readthedocs.io/zh-cn/latest/tutorials/"+PAGE+"/";
    private SpellTutorialWeb(){}

    static URI url(MinecraftClient client){
        String page=System.getProperty("ssca.audit.spellTutorialWebPage");
        if(page==null){
            page=ONLINE;
            var root=client.runDirectory.toPath().resolve("ssca-tutorials");
            if(Files.isRegularFile(root.resolve(PAGE).resolve("index.html"))){
                try{page=FormTutorialLocalServer.page(root,PAGE).toString();}
                catch(java.io.IOException|LinkageError|RuntimeException failure){
                    LoggerFactory.getLogger("SSCA-Tutorial").warn("Cannot serve synchronized spell tutorial; using online page",failure);
                }
            }
        }
        return URI.create(page+(page.contains("?")?'&':'?')+"lang="+(client.options.language.startsWith("zh_")?"zh_cn":"en_us")
                +"&cast="+key(SpellcastKeybindings.KEY_CAST)+"&switch="+key(SpellcastKeybindings.KEY_SWITCH)
                +"&autoplay=1&v=20261111-spell-r1");
    }
    private static String key(KeyBinding binding){
        Text name=binding==null||binding.isUnbound()?Text.translatable("tutorial.ssc_addon.unbound"):binding.getBoundKeyLocalizedText();
        return URLEncoder.encode(name.getString(),StandardCharsets.UTF_8);
    }
    public static void open(MinecraftClient client,Runnable onDispatched){
        FormTutorialWeb.openExternal(client,url(client),onDispatched);
    }
}
