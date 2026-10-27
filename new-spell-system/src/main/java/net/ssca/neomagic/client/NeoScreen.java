package net.ssca.neomagic.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.*;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.*;
import net.ssca.neomagic.*;
import java.util.*;
import static net.ssca.neomagic.NeoModel.*;
import static net.ssca.neomagic.client.NeoClient.*;

/** Four real server-backed pages. Narrow layouts switch panels instead of clipping sidebars. */
public final class NeoScreen extends Screen {
    private int page=1,panel,scroll,recordA,recordB=1;
    private Blueprint edit;
    private TextFieldWidget name,hypothesis,conclusion;
    private int top=70,bottom;
    private final List<Text> lines=new ArrayList<>();
    public NeoScreen() {super(NeoMagic.text("title"));edit=data.draft;}
    private Text t(String key,Object... args){return NeoMagic.text(key,args);}
    private String label(Enum<?> e){return t(e instanceof Core?"core."+e.name().toLowerCase(Locale.ROOT):e instanceof Carrier?"carrier."+e.name().toLowerCase(Locale.ROOT):"control."+e.name().toLowerCase(Locale.ROOT)).getString();}
    private void button(String key,int x,int y,int w,Runnable action) {
        addDrawableChild(ButtonWidget.builder(t(key),b->action.run()).dimensions(x,y,w,20).build());
    }
    private ButtonWidget rawButton(Text text,int x,int y,int w,Runnable action,boolean active) {
        var b=ButtonWidget.builder(text,widget->action.run()).dimensions(x,y,w,20).build();b.active=active;addDrawableChild(b);return b;
    }
    public void updated(){recordA=Math.min(recordA,Math.max(0,data.records.size()-1));recordB=Math.min(recordB,Math.max(0,data.records.size()-1));}
    private void rebuild(){scroll=0;clearAndInit();}
    private void rememberName(){if(name!=null)edit=new Blueprint(name.getText(),edit.core(),edit.carrier(),edit.controls(),edit.conversion(),edit.scale(),edit.prep(),edit.range());}
    @Override protected void init() {
        bottom=height-64;int tabW=(width-24)/4;
        for(int i=0;i<4;i++){int target=i;button("page."+i,12+i*tabW,27,tabW-3,()->{rememberName();page=target;rebuild();});}
        button("close",width-62,4,50,this::close);
        if(page==1)initCraft();else if(page==2)initExperiment();else if(page==3)initNotes();
        else {button("to_craft",12,height-52,(width-28)/2,()->{page=1;rebuild();});button("to_records",width/2+2,height-52,(width-28)/2,()->{page=2;rebuild();});}
    }
    private void initCraft() {
        int w=(width-28)/4;
        for(int i=0;i<4;i++){int target=i;button("panel."+i,12+i*w,50,w-3,()->{rememberName();panel=target;rebuild();});}
        int x=16,y=74,wide=width-32;
        if(panel==0) {
            rawButton(t("select_core",label(edit.core())),x,y,wide,()->{
                var available=Arrays.stream(Core.values()).filter(c->data.known.contains("core:"+c)).toList();
                if(available.isEmpty())return;
                Core core=available.get((available.indexOf(edit.core())+1)%available.size());
                Carrier carrier=Arrays.stream(Carrier.values()).filter(c->supports(core,c)&&data.known.contains("carrier:"+c)).findFirst().orElse(Carrier.PROJECTILE);
                edit=new Blueprint(edit.name(),core,carrier,List.of(),core==Core.FROST?Core.THERMAL:core==Core.LUNAR?Core.VOID:Core.FROST,edit.scale(),edit.prep(),Math.min(32,edit.range()));rebuild();
            },table);y+=24;
            rawButton(t("select_carrier",label(edit.carrier())),x,y,wide,()->{
                var choices=Arrays.stream(Carrier.values()).filter(c->supports(edit.core(),c)&&data.known.contains("carrier:"+c)).toList();
                if(choices.isEmpty())return;
                edit=new Blueprint(edit.name(),edit.core(),choices.get((choices.indexOf(edit.carrier())+1)%choices.size()),edit.controls(),edit.conversion(),edit.scale(),edit.prep(),edit.range());rebuild();
            },table);y+=25;
            int index=0;
            for(Control c:Control.values()) {
                var proposed=new ArrayList<>(edit.controls());proposed.add(c);
                var next=new Blueprint(edit.name(),edit.core(),edit.carrier(),proposed,edit.conversion(),edit.scale(),edit.prep(),edit.range());
                var diagnostics=errors(next);
                rawButton(t("add_control",label(c)),x+(index%3)*(wide/3),y+(index/3)*22,wide/3-3,()->{
                    if(errors(next).isEmpty()){edit=next;rebuild();}
                },table&&data.known.contains("control:"+c)&&diagnostics.isEmpty()).setTooltip(Tooltip.of(diagnostics.isEmpty()?t("meaning."+c.name().toLowerCase(Locale.ROOT)):t("error."+diagnostics.get(0))));index++;
            }
            button("undo",x,y+44,wide/2-2,()->{var list=new ArrayList<>(edit.controls());if(!list.isEmpty())list.remove(list.size()-1);edit=new Blueprint(edit.name(),edit.core(),edit.carrier(),list,edit.conversion(),edit.scale(),edit.prep(),Math.min(edit.range(),list.contains(Control.BRIDGE)?64:32));rebuild();});
            button("clear_controls",width/2+2,y+44,wide/2-2,()->{edit=new Blueprint(edit.name(),edit.core(),edit.carrier(),List.of(),edit.conversion(),edit.scale(),edit.prep(),Math.min(32,edit.range()));rebuild();});
        } else if(panel==2) {
            name=new TextFieldWidget(textRenderer,x,y,wide,20,t("blueprint_name"));name.setMaxLength(32);name.setText(edit.name());addDrawableChild(name);y+=25;
            parameter("scale",x,y,wide,()->change(-.5,0,0),()->change(.5,0,0));y+=25;
            parameter("prep",x,y,wide,()->change(0,-5,0),()->change(0,5,0));y+=25;
            parameter("range",x,y,wide,()->change(0,0,-4),()->change(0,0,4));
        }
        int half=(width-28)/2;
        rawButton(t("save_baseline"),12,height-52,half,()->send("baseline"),table&&errors(edit).isEmpty());
        rawButton(t("save_variant"),width/2+2,height-52,half,()->send("draft"),table&&errors(edit).isEmpty());
        rawButton(t("craft"),12,height-28,half,()->send("craft"),table&&errors(edit).isEmpty());
        rawButton(t("equip"),width/2+2,height-28,half,()->send("equip"),table&&errors(edit).isEmpty());
    }
    private void parameter(String key,int x,int y,int w,Runnable minus,Runnable plus) {
        rawButton(Text.literal("−"),x,y,30,minus,table);rawButton(Text.literal("+"),x+w-30,y,30,plus,table);
    }
    private void change(double scale,int prep,int range) {
        rememberName();edit=new Blueprint(edit.name(),edit.core(),edit.carrier(),edit.controls(),edit.conversion(),clamp(edit.scale()+scale,.5,4),(int)clamp(edit.prep()+prep,0,80),clamp(edit.range()+range,1,edit.controls().contains(Control.BRIDGE)?64:32));rebuild();
    }
    private void send(String action){rememberName();NeoClient.action(action,edit,null,null,null);}
    private void initExperiment() {
        int half=(width-28)/2;
        button("next_a",12,52,half,()->{if(!data.records.isEmpty())recordA=(recordA+1)%data.records.size();scroll=0;});
        button("next_b",width/2+2,52,half,()->{if(!data.records.isEmpty())recordB=(recordB+1)%data.records.size();scroll=0;});
        button("cite",12,height-52,width-24,()->{page=3;rebuild();});
    }
    private void initNotes() {
        hypothesis=new TextFieldWidget(textRenderer,16,91,width-32,20,t("hypothesis"));hypothesis.setMaxLength(1000);hypothesis.setText(data.hypothesis);addDrawableChild(hypothesis);
        conclusion=new TextFieldWidget(textRenderer,16,137,width-32,20,t("conclusion"));conclusion.setMaxLength(1000);conclusion.setText(data.conclusion);addDrawableChild(conclusion);
        button("save_notes",12,height-40,width-24,()->{
            List<String> ids=new ArrayList<>();if(!data.records.isEmpty()){ids.add(data.records.get(recordA).id);ids.add(data.records.get(recordB).id);}
            NeoClient.action("notes",null,hypothesis.getText(),conclusion.getText(),String.join(",",new LinkedHashSet<>(ids)));
        });
    }
    private void line(String key,Object... args){lines.add(t(key,args));}
    private void blueprintLines(Blueprint b) {
        line("structure",label(b.core()),label(b.carrier()),b.controls().isEmpty()?t("plain").getString():String.join(" → ",b.controls().stream().map(this::label).toList()));
        line("parameters",f(b.scale()),b.prep(),f(b.range()));
    }
    @Override public void render(DrawContext c,int mx,int my,float delta) {
        renderBackground(c);c.fill(4,2,width-4,height-2,0xF0121D2A);c.fill(5,22,width-5,23,0xFFD1B66E);
        c.drawText(textRenderer,t("title"),12,9,0xFFE6D7B7,false);lines.clear();
        if(page==0) {
            line("observation_intro");line("known_count",data.known.size());
            for(String piece:new TreeSet<>(data.known))lines.add(NeoNetwork.pieceText(piece));
            line("latest");if(!data.records.isEmpty())experimentLines(data.records.get(0));else line("no_records");
            line("sample_help");
        } else if(page==1) {
            if(panel==0) {
                top=196;blueprintLines(edit);for(Control control:edit.controls())line("meaning."+control.name().toLowerCase(Locale.ROOT));
            } else if(panel==1) {
                top=155;diagram(c,edit,Math.max(80,width/2),110,Math.min(60,width/4));blueprintLines(edit);
                Quote q=preview(edit);
                line("budget",f(9*edit.scale()*q.plan().retention()),q.plan().branches(),f(q.plan().load()));
                line("static_valid",errors(edit).isEmpty()?t("valid").getString():t("error."+errors(edit).get(0)).getString());
            } else if(panel==2) {
                top=183;c.drawCenteredTextWithShadow(textRenderer,t("scale_value",f(edit.scale())),width/2,109,0xFFDBC58E);
                c.drawCenteredTextWithShadow(textRenderer,t("prep_value",edit.prep()),width/2,134,0xFFDBC58E);
                c.drawCenteredTextWithShadow(textRenderer,t("range_value",f(edit.range())),width/2,159,0xFFDBC58E);
            } else {
                top=80;
                Quote q=preview(edit);
                Risk risk=risk(edit,data.burden,q.actual(),profile);
                line("quote",f(q.normal()),f(q.actual()),pc(bodyMastery(edit)));
                line("body",f(data.burden),f(profile.safety()),f(risk.excess()));
                line("current_risk",pc(risk.failure()),pc(risk.offset()));line("form",formName(data.form));
                line("safe_guarantee");if(profile.reference())line("reference_profile");
            }
            if(!table)line("need_table");
        } else if(page==2) {
            top=78;
            if(data.records.isEmpty())line("no_records");else {
                var a=data.records.get(recordA);var b=data.records.get(recordB);
                line("record_pair",recordA+1,recordB+1);line("comparison_hint");
                if(!a.form.equals(b.form))line("different_form");if(a.initial!=b.initial)line("different_burden");
                if(a.blueprint.scale()!=b.blueprint.scale())line("different_scale");if(a.blueprint.prep()!=b.blueprint.prep())line("different_prep");
                if(!a.target.equals(b.target)||a.blueprint.range()!=b.blueprint.range())line("different_target");if(!a.version.equals(b.version))line("different_version");
                statistics(a);statistics(b);
                line("record_a");experimentLines(a);line("record_b");experimentLines(b);
            }
        } else {
            top=170;c.drawText(textRenderer,t("hypothesis"),16,77,0xFFE2C77E,false);c.drawText(textRenderer,t("conclusion"),16,123,0xFFE2C77E,false);
            line("notes_hint");line("citations",String.join(", ",data.citations.stream().map(id->id.substring(0,8)).toList()));
            line("shared_help");
        }
        if(page==0)top=58;
        c.enableScissor(10,Math.min(top,bottom),width-10,bottom);
        int y=top-scroll;
        for(Text text:lines)for(OrderedText wrapped:textRenderer.wrapLines(text,width-32)){c.drawText(textRenderer,wrapped,16,y,0xFFC5D3D8,false);y+=12;}
        c.disableScissor();
        super.render(c,mx,my,delta);
        if(y>bottom&&top<bottom)c.drawText(textRenderer,t("scroll"),width-68,height-64,0xFF92AAA9,false);
    }
    private void experimentLines(NeoState.Experiment e) {
        blueprintLines(e.blueprint);line("record_id",e.id.substring(0,8),e.version);line("result",t("result."+e.result).getString());
        line("record_cost",f(e.initial),f(e.actual),f(e.paid));line("measured",f(e.arrived),f(e.damage),f(e.healing),e.hits,e.summons);
        line("absorption",f(e.absorbed));
        for(Checkpoint cp:e.checks)line("checkpoint",cp.tick(),f(cp.burden()),f(cp.risk().excess()),pc(cp.risk().failure()),pc(cp.risk().offset()),cp.failed()?t("failed").getString():cp.shifted()?t("shifted").getString():t("passed").getString(),f(cp.yaw()),f(cp.pitch()));
    }
    private void statistics(NeoState.Experiment reference) {
        var samples=data.records.stream().filter(e->e.form.equals(reference.form)&&e.version.equals(reference.version)
                &&e.initial==reference.initial&&e.target.equals(reference.target)&&e.blueprint.identity().equals(reference.blueprint.identity())
                &&e.blueprint.scale()==reference.blueprint.scale()&&e.blueprint.prep()==reference.blueprint.prep()&&e.blueprint.range()==reference.blueprint.range()
                &&Set.of("released","resolved","failed","missed").contains(e.result)).toList();
        line("sample_summary",label(reference.blueprint.core()),samples.size(),samples.stream().filter(e->e.result.equals("failed")).count());
        int[] bins=new int[4];for(var e:samples){double angle=Math.hypot(e.yaw,e.pitch);bins[angle<1e-8?0:angle<=5?1:angle<=15?2:3]++;}
        for(int i=0;i<4;i++)line("histogram",t("bin."+i).getString(),"▮".repeat(Math.min(20,bins[i])),bins[i]);
    }
    private String formName(String id) {
        String[] parts=id.split(":",2);if(parts.length!=2)return id;
        String key="origin."+parts[0]+".form_"+parts[1]+".name";
        if(net.minecraft.client.resource.language.I18n.hasTranslation(key))return Text.translatable(key).getString();
        key="codex.form."+parts[0]+"."+parts[1]+".name";
        return net.minecraft.client.resource.language.I18n.hasTranslation(key)?Text.translatable(key).getString():t("unnamed_form").getString();
    }
    private void diagram(DrawContext c,Blueprint b,int cx,int cy,int radius) {
        int color=0xFFC9AE65;
        for(int i=0;i<72;i++){double a=i*Math.PI/36;int x=cx+(int)(Math.cos(a)*radius),y=cy+(int)(Math.sin(a)*radius*.6);c.fill(x,y,x+2,y+2,color);}
        c.drawCenteredTextWithShadow(textRenderer,Text.literal(label(b.core())),cx,cy-4,color);
        int count=Math.max(1,b.controls().size());
        for(int i=0;i<count;i++) {
            double angle=-Math.PI/2+2*Math.PI*i/count;int x=cx+(int)(Math.cos(angle)*radius),y=cy+(int)(Math.sin(angle)*radius*.6);
            c.fill(x-17,y-8,x+18,y+9,0xFF283949);c.drawCenteredTextWithShadow(textRenderer,b.controls().isEmpty()?t("plain"):Text.literal(label(b.controls().get(i))),x,y-4,0xFFE3CD91);
        }
    }
    @Override public boolean mouseScrolled(double x,double y,double amount){scroll=Math.max(0,scroll-(int)(amount*24));return true;}
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){NeoClient.action("close",null,null,null,null);super.close();}
}
