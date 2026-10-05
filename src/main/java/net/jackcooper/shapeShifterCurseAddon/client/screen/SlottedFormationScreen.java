package net.jackcooper.shapeShifterCurseAddon.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.jackcooper.shapeShifterCurseAddon.SscAddon;
import net.jackcooper.shapeShifterCurseAddon.item.BlankFormationPaperItem;
import net.jackcooper.shapeShifterCurseAddon.item.FormationInkItem;
import net.jackcooper.shapeShifterCurseAddon.item.AnalyzedSpellDiagramItem;
import com.mojang.blaze3d.systems.RenderSystem;
import net.onixary.shapeShifterCurseFabric.items.RegCustomItem;
import net.jackcooper.shapeShifterCurseAddon.screen.SpellResearchTableScreenHandler;
import net.jackcooper.shapeShifterCurseAddon.block.SpellResearchTableBlockEntity;
import net.jackcooper.shapeShifterCurseAddon.spell.*;
import net.jackcooper.shapeShifterCurseAddon.spell.research.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.tooltip.BundleTooltipComponent;
import net.minecraft.client.item.BundleTooltipData;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class SlottedFormationScreen extends HandledScreen<SpellResearchTableScreenHandler> {
    private static final int PALETTE_LEFT=8,PALETTE_TOP=26,PALETTE_CELL=22,PALETTE_HEIGHT=118;
    private static final int PALETTE_TRACK_X=53,PALETTE_TRACK_WIDTH=4;
    private static final int PALETTE_ROWS=(18+1)/2,PALETTE_MAX_SCROLL=PALETTE_ROWS*PALETTE_CELL-PALETTE_HEIGHT;
    private static final int PALETTE_THUMB_HEIGHT=PALETTE_HEIGHT*PALETTE_HEIGHT/(PALETTE_ROWS*PALETTE_CELL);
    private static final int REFERENCE_COLUMNS=4,REFERENCE_CAPACITY=15,REFERENCE_LEFT=64,REFERENCE_TOP=94;
    private final SpellResearchTableScreen parent;
    private final PlayerInventory inventory;
    private final List<ButtonWidget> pageButtons=new ArrayList<>();
    private final List<ButtonWidget> studyButtons=new ArrayList<>();
    private final Map<Integer,int[]> drafts=new HashMap<>();
    private final BitSet touched=new BitSet();
    private final FeedbackState feedbackState=new FeedbackState();
    private ResearchProgress progress=new ResearchProgress();
    private int level=1,selectedGlyph,revision,paletteScroll,notesScroll,referenceScroll;
    private int[] slots=RuneLayout.empty(1),meanings=new int[18],schools=new int[18],reference=new int[0];
    private int[] diagnosis=new int[0];
    private int stability=100;private boolean runeValid;private int evaluatedRevision=-1;
    private RuneModifiers modifiers=RuneModifiers.NONE;
    private NbtCompound runeTooltipState=new NbtCompound();
    private final BitSet problems=new BitSet(),inactive=new BitSet(),synergy=new BitSet(),suppressed=new BitSet();
    private final List<Integer> referenceInventorySlots=new ArrayList<>();
    private int selectedReferenceSlot=-1;
    private List<FormationDiagram.Point> positions=RuneLayout.positions(1);
    private UUID world,operation;
    private boolean notes,referenceMenu,referenceVisible,handoff,busy,dragging,erasing;
    private boolean dirty,loading,paletteDragging,paletteScrolling;
    private double paletteGrabOffset;
    private long sentAt,animation,editedAt;
    private Text feedback=text("ready");

    public SlottedFormationScreen(SpellResearchTableScreenHandler handler,PlayerInventory inventory,SpellResearchTableScreen parent){
        super(handler,inventory,text("title"));this.parent=parent;this.inventory=inventory;
        backgroundWidth=312;backgroundHeight=212;Arrays.fill(meanings,-1);Arrays.fill(schools,7);
    }
    private static Identifier image(String name){return new Identifier("ssc_addon","textures/gui/formation_research/"+name+".png");}
    private static Text text(String key,Object...args){return SlottedResearchManager.text(key,args);}
    @Override protected void init(){
        super.init();y=(height-backgroundHeight-28)/2+28;handoff=false;pageButtons.clear();studyButtons.clear();dragging=false;paletteScrolling=false;paletteDragging=false;
        handler.setActivePage(ResearchTableTabs.RESEARCH);
        if(client.interactionManager!=null)client.interactionManager.clickButton(handler.syncId,ResearchTableTabs.RESEARCH);
        positions=RuneLayout.positions(level);
        for(int index=0;index<ResearchTableTabs.COUNT;index++){final int tab=index;addDrawableChild(ResearchTableTabs.create(x,y,index,()->tab==ResearchTableTabs.RESEARCH,()->{if(tab!=ResearchTableTabs.RESEARCH)back(tab);}));}
        for(int rank=1;rank<=5;rank++){final int tier=rank;pageButtons.add(button(244+(rank-1)*12,18,12,18,Text.literal(String.valueOf(rank)),()->switchLevel(tier)));}
        pageButtons.add(button(244,44,60,18,text("notes"),()->{notes=!notes;referenceMenu=false;notesScroll=0;visibility();}));
        studyButtons.add(button(244,86,60,18,text("identify"),()->send(SlottedResearchManager.INSPECT)));
        studyButtons.add(button(244,108,60,18,text("train"),()->send(SlottedResearchManager.TRAIN)));
        pageButtons.add(button(244,152,60,18,text("test"),()->send(SlottedResearchManager.TEST)));
        pageButtons.add(button(244,174,60,18,text("finish"),()->send(SlottedResearchManager.COMPLETE)));
        pageButtons.add(icon(20,153,false,"reference",()->{
            if(referenceVisible){referenceVisible=false;}
            referenceMenu=!referenceMenu;referenceScroll=0;refreshReferenceList();
        }));
        pageButtons.add(icon(20,176,true,"clear",this::clearCurrent));
        visibility();
        if(world==null)send(SlottedResearchManager.OPEN);
    }
    private ButtonWidget button(int left,int top,int width,int height,Text title,Runnable action){
        var button=addDrawableChild(ButtonWidget.builder(Text.literal(textRenderer.trimToWidth(title.getString(),Math.max(1,width-4))),ignored->action.run()).dimensions(x+left,y+top,width,height).build());
        button.setTooltip(Tooltip.of(title));return button;
    }
    private ButtonWidget icon(int left,int top,boolean clearButton,String key,Runnable action){
        return addDrawableChild(new ButtonWidget(x+left,y+top,20,20,text(key),ignored->action.run(),supplier->supplier.get()){
            @Override public void renderButton(DrawContext context,int mouseX,int mouseY,float delta){
                if(clearButton){
                    active=!busy&&!loading&&!notes&&Arrays.stream(slots).anyMatch(value->value>=0);
                    RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
                    context.setShaderColor(1,1,1,active?1:.35f);
                    context.drawTexture(image("widgets"),getX()+2,getY()+2,16,16,0,0,32,32,32,32);
                    context.setShaderColor(1,1,1,1);
                }else context.drawItem(new ItemStack(Items.BUNDLE),getX()+2,getY()+2);
                if(isHovered()&&!referenceMenu)context.drawTooltip(textRenderer,getMessage(),mouseX,mouseY);
            }
        });
    }
    private void visibility(){for(var button:studyButtons)button.visible=notes;for(int tier=0;tier<5;tier++)pageButtons.get(tier).active=tier+1!=level;}
    private void switchLevel(int next){
        if(next==level||busy)return;save();level=next;slots=drafts.getOrDefault(level,RuneLayout.empty(level)).clone();
        revision++;reference=new int[0];referenceVisible=false;selectedReferenceSlot=-1;notesScroll=0;positions=RuneLayout.positions(level);visibility();send(SlottedResearchManager.OPEN);
    }
    private void save(){drafts.put(level,slots.clone());send(SlottedResearchManager.SAVE);}
    private void send(int action){
        if(client==null||client.player==null||client.player.currentScreenHandler!=handler||!ClientPlayNetworking.canSend(FormationResearchNetworking.ACTION))return;
        if(world==null)action=SlottedResearchManager.OPEN;
        if(busy&&action!=SlottedResearchManager.SAVE)return;
        NbtCompound request=new NbtCompound();request.putInt("Action",action);request.putInt("Level",level);request.putInt("Revision",revision);
        request.putInt("Version",RuneLayout.VERSION);request.putIntArray("Slots",slots);request.putInt("Glyph",selectedGlyph);
        if(world!=null)request.putUuid("World",world);if(operation!=null)request.putUuid("Operation",operation);
        if(action!=SlottedResearchManager.SAVE)request.putUuid("Request",feedbackState.begin(action));
        var packet=PacketByteBufs.create();packet.writeVarInt(handler.syncId);packet.writeNbt(request);ClientPlayNetworking.send(FormationResearchNetworking.ACTION,packet);
        if(action==SlottedResearchManager.TEST)playSound(SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE,.25f,.85f);
        if(action==SlottedResearchManager.SAVE)dirty=false;
        if(action==SlottedResearchManager.OPEN)loading=true;
        if(action!=SlottedResearchManager.SAVE){busy=true;sentAt=System.currentTimeMillis();}
    }
    public void accept(NbtCompound state,Text message,boolean valid){
        if(!state.containsUuid("World")||world!=null&&!world.equals(state.getUuid("World")))return;
        int action=state.getInt("Action");
        if(action!=SlottedResearchManager.SAVE&&(!state.containsUuid("Request")||!feedbackState.finish(state.getUuid("Request"),action)))return;
        world=state.getUuid("World");progress.read(state.getCompound("Progress"));
        if(state.containsUuid("Operation"))operation=state.getUuid("Operation");
        int[] received=state.getIntArray("Meanings");if(received.length==18)meanings=received;
        received=state.getIntArray("Schools");if(received.length==18)schools=received;
        if(state.getInt("Level")==level&&state.getInt("Revision")==revision){
            stability=state.getInt("Stability");runeValid=state.getBoolean("RuneValid");evaluatedRevision=revision;
            modifiers=RuneModifiers.read(state.getCompound("Modifiers"));
            runeTooltipState=state.getCompound("RuneTooltipState").copy();
            for(var entry:Map.of("Problems",problems,"Inactive",inactive,"Synergy",synergy,"Suppressed",suppressed).entrySet()){entry.getValue().clear();for(int p:state.getIntArray(entry.getKey()))entry.getValue().set(p);}}
        if(action==SlottedResearchManager.SAVE)return;
        busy=false;
        if(state.getBoolean("Slotted")&&(state.getInt("Level")!=level||state.getInt("Revision")!=revision))return;
        feedback=message;
        if(action==SlottedResearchManager.OPEN){
            loading=false;
            received=state.getIntArray("Slots");if(RuneLayout.validDraft(level,received))slots=received;
        }
        // 阶段B：接收试运行诊断；失败时用分层诊断文本替代笼统的 invalid 提示
        diagnosis=state.getIntArray("Diagnosis");
        if(action==SlottedResearchManager.TEST&&diagnosis.length>0)feedback=FormationDiagnosis.message(diagnosis);
        if(valid)animation=System.currentTimeMillis();
        if(action==SlottedResearchManager.TEST){
            playSound(valid?SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME:SoundEvents.BLOCK_FIRE_EXTINGUISH,valid?.4f:.18f,valid?1.25f:.8f);
        }else if(action==SlottedResearchManager.COMPLETE&&!valid)playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH,.18f,.8f);
    }
    @Override protected void handledScreenTick(){
        long now=System.currentTimeMillis();
        if(busy&&now-sentAt>8000){busy=false;loading=false;feedbackState.cancel();feedback=text("timeout");}
        if(dirty&&!busy&&world!=null&&now-editedAt>600)save();
        if(referenceVisible&&(selectedReferenceSlot<0||!AnalyzedSpellDiagramItem.valid(inventory.getStack(selectedReferenceSlot),world)
            ||!Arrays.equals(reference,foundationReference(inventory.getStack(selectedReferenceSlot).getNbt())))){referenceVisible=false;reference=new int[0];}
    }
    @Override protected void drawBackground(DrawContext context,float delta,int mouseX,int mouseY){
        context.drawTexture(image("background"),x,y,0,0,312,212,312,212);
    }
    @Override protected void drawForeground(DrawContext context,int mouseX,int mouseY){}
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta){
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();context.setShaderColor(1,1,1,1);
        renderBackground(context);drawBackground(context,delta,mouseX,mouseY);
        context.drawText(textRenderer,text("filled",Arrays.stream(slots).filter(value->value>=0).count(),slots.length),x+65,y+6,0xff414141,false);
        context.drawText(textRenderer,text("rank_short",progress.rank),x+244,y+6,0xff414141,false);
        if(!notes)context.drawText(textRenderer,Text.translatable("research.ssc_addon.runes.stability",stability),x+244,y+68,stability<20?0xffaa2222:0xff414141,false);
        pageButtons.get(7).active=!busy&&!loading&&evaluatedRevision==revision&&runeValid
                &&handler.getSlot(SpellResearchTableBlockEntity.SLOT_RESEARCH_OUTPUT).getStack().isEmpty();
        int hovered=-1;context.enableScissor(x+PALETTE_LEFT,y+PALETTE_TOP,x+PALETTE_LEFT+PALETTE_CELL*2,y+PALETTE_TOP+PALETTE_HEIGHT);
        for(int glyph=0;glyph<18;glyph++){
            int left=x+PALETTE_LEFT+glyph%2*PALETTE_CELL,top=y+PALETTE_TOP+glyph/2*PALETTE_CELL-paletteScroll;
            if(glyph==selectedGlyph)context.drawTexture(image("sockets"),left,top,PALETTE_CELL,PALETTE_CELL,0,0,64,64,128,64);
            drawGlyph(context,glyph,left+2,top+2,18);
            if(mouseX>=left&&mouseX<left+PALETTE_CELL&&mouseY>=Math.max(top,y+PALETTE_TOP)&&mouseY<Math.min(top+PALETTE_CELL,y+PALETTE_TOP+PALETTE_HEIGHT))hovered=glyph;
        }
        context.disableScissor();
        context.drawTexture(image("palette_scroll"),x+PALETTE_TRACK_X,y+PALETTE_TOP,PALETTE_TRACK_WIDTH,PALETTE_HEIGHT,0,0,4,32,8,32);
        context.drawTexture(image("palette_scroll"),x+PALETTE_TRACK_X,paletteThumbY(),PALETTE_TRACK_WIDTH,PALETTE_THUMB_HEIGHT,4,0,4,32,8,32);
        if(notes)renderNotes(context);else{
            context.drawTexture(image("tier_"+level),x+63,y+19,172,172,0,0,1024,1024,1024,1024);
            if(animation>0&&System.currentTimeMillis()-animation<1500)context.drawTexture(image("tier_"+level+"_glow"),x+63,y+19,172,172,0,0,1024,1024,1024,1024);
            int hoveredSlot=slotAt(mouseX,mouseY);
            for(int index=0;index<slots.length;index++){
                var point=positions.get(index);float centerX=(float)(x+63+point.x()*172/512),centerY=(float)(y+19+point.y()*172/512);
                context.getMatrices().push();context.getMatrices().translate(centerX,centerY,0);
                if(slots[index]>=0)drawGlyph(context,slots[index],-8,-8,16);
                else if(referenceVisible&&reference.length==slots.length&&reference[index]>=0){context.setShaderColor(1,1,1,.55f);drawGlyph(context,reference[index],-8,-8,16);context.setShaderColor(1,1,1,1);}
                if(hoveredSlot==index)context.drawTexture(image("sockets"),-8,-8,16,16,RuneLayout.enhancement(level,index)?64:0,0,64,64,128,64);
                context.getMatrices().pop();
            }
            // 阶段B：问题环高亮——诊断报出问题的环，其槽位外圈染淡红（仅视觉提示，不影响判定）
            if(diagnosis.length>=6){
                int layers=Math.min(diagnosis[0],(diagnosis.length-2)/4);
                int[] counts=RuneLayout.layers(level);int offset=0;
                for(int layer=0;layer<layers;layer++){
                    int empty=diagnosis[1+layer*4],structural=diagnosis[1+layer*4+1],semantic=diagnosis[1+layer*4+2];
                    if(empty>0||structural>0||semantic>0){
                        for(int p=offset;p<offset+counts[layer];p++){
                            var point=positions.get(p);
                            context.drawBorder(
                                (int)(x+63+point.x()*172/512)-9,
                                (int)(y+19+point.y()*172/512)-9,
                                18,18,0x66ff5544);
                        }
                    }
                    offset+=counts[layer];
                }
            }
        }
        int[] costs=inkCosts();int ink=Arrays.stream(costs).sum(),dust=(int)Arrays.stream(slots).filter(value->value>=0).count();
        if(!notes){
            context.drawItem(new ItemStack(SscAddon.BLANK_FORMATION_PAPER),x+246,y+89);context.drawText(textRenderer,"1",x+270,y+93,0xff414141,false);
            context.drawItem(new ItemStack(RegCustomItem.UNTREATED_MOONDUST),x+246,y+109);context.drawText(textRenderer,String.valueOf(dust),x+270,y+113,0xff414141,false);
            context.drawItem(new ItemStack(SscAddon.FORMATION_INK_NORMAL),x+246,y+129);context.drawText(textRenderer,String.valueOf(ink),x+270,y+133,0xff414141,false);
            int left=x+SpellResearchTableScreenHandler.RESEARCH_OUTPUT_X,top=y+SpellResearchTableScreenHandler.RESEARCH_OUTPUT_Y;
            context.drawText(textRenderer,Text.translatable("research.ssc_addon.runes.output"),left,top-12,0xff414141,false);
            context.fill(left-1,top-1,left+17,top+17,0xff8b8b8b);
            context.fill(left-1,top-1,left+17,top,0xff373737);context.fill(left-1,top,left,top+17,0xff373737);
            context.fill(left,top+16,left+17,top+17,0xffffffff);context.fill(left+16,top,left+17,top+16,0xffffffff);
            ItemStack product=handler.getSlot(SpellResearchTableBlockEntity.SLOT_RESEARCH_OUTPUT).getStack();
            context.drawItem(product,left,top);context.drawItemInSlot(textRenderer,product,left,top);
            if(overOutput(mouseX,mouseY))context.fill(left,top,left+16,top+16,0x50ffffff);
        }
        String status=textRenderer.trimToWidth(feedback.getString(),292);
        context.drawText(textRenderer,status,x+(312-textRenderer.getWidth(status))/2,y+198,0xff414141,false);
        for(Element child:children())if(child instanceof Drawable drawable)drawable.render(context,mouseX,mouseY,delta);
        if(paletteDragging&&!notes){context.setShaderColor(1,1,1,.75f);drawGlyph(context,selectedGlyph,mouseX-8,mouseY-8,16);context.setShaderColor(1,1,1,1);}
        if(referenceMenu){
            refreshReferenceList();DefaultedList<ItemStack> contents=DefaultedList.ofSize(REFERENCE_CAPACITY,ItemStack.EMPTY);
            int first=referenceScroll*REFERENCE_COLUMNS;
            for(int index=0;index<REFERENCE_CAPACITY&&first+index<referenceInventorySlots.size();index++)contents.set(index,inventory.getStack(referenceInventorySlots.get(first+index)));
            var bundle=new BundleTooltipComponent(new BundleTooltipData(contents,0));
            context.getMatrices().push();context.getMatrices().translate(0,0,300);
            bundle.drawItems(textRenderer,x+REFERENCE_LEFT,y+REFERENCE_TOP,context);
            int hoveredReference=referenceAt(mouseX,mouseY);
            if(hoveredReference>=0){
                int local=hoveredReference-first;
                context.drawBorder(x+REFERENCE_LEFT+1+local%REFERENCE_COLUMNS*18,y+REFERENCE_TOP+1+local/REFERENCE_COLUMNS*20,18,20,0xffffdd88);
            }
            context.draw();
            if(hoveredReference>=0)context.drawItemTooltip(textRenderer,inventory.getStack(referenceInventorySlots.get(hoveredReference)),mouseX,mouseY);
            context.draw();context.getMatrices().pop();
        }else if(overOutput(mouseX,mouseY)){
            ItemStack product=handler.getSlot(SpellResearchTableBlockEntity.SLOT_RESEARCH_OUTPUT).getStack();
            if(product.isEmpty())context.drawTooltip(textRenderer,Text.translatable("research.ssc_addon.runes.output"),mouseX,mouseY);
            else context.drawItemTooltip(textRenderer,product,mouseX,mouseY);
        }else if(hovered>=0)context.drawTooltip(textRenderer,glyphName(hovered),mouseX,mouseY);
        else if(!notes&&slotAt(mouseX,mouseY)>=0){
            int at=slotAt(mouseX,mouseY);
            List<net.minecraft.text.OrderedText> wrapped=new ArrayList<>();
            for(Text line:RuneSlotTooltips.slot(level,at,slots,meanings,runeTooltipState,evaluatedRevision==revision))
                wrapped.addAll(textRenderer.wrapLines(line,Math.max(1,Math.min(260,width-24))));
            context.drawOrderedTooltip(textRenderer,wrapped,mouseX,mouseY);
        }
        else if(!notes&&mouseX>=x+244&&mouseX<x+305&&mouseY>=y+65&&mouseY<y+84){List<Text> lines=new ArrayList<>();lines.add(Text.translatable("research.ssc_addon.runes.stability_detail",stability));for(var stat:RuneModifiers.Stat.values())if(modifiers.get(stat)!=0)lines.add(RuneTooltips.stat(stat,modifiers.get(stat)));context.drawTooltip(textRenderer,lines,mouseX,mouseY);}
        else if(!notes&&mouseX>=x+244&&mouseX<x+305&&mouseY>=y+125&&mouseY<y+148)context.drawTooltip(textRenderer,inkLines(costs),mouseX,mouseY);
        else if(!notes&&mouseX>=x+244&&mouseX<x+305&&mouseY>=y+105&&mouseY<y+125)context.drawTooltip(textRenderer,text("dust_cost",dust,SlottedFormationTransaction.count(handler.getInventory(),inventory,2,stack->stack.isOf(RegCustomItem.UNTREATED_MOONDUST))),mouseX,mouseY);
        else if(!notes&&mouseX>=x+244&&mouseX<x+305&&mouseY>=y+85&&mouseY<y+105)context.drawTooltip(textRenderer,text("paper_cost",SlottedFormationTransaction.count(handler.getInventory(),inventory,0,stack->stack.getItem() instanceof BlankFormationPaperItem)),mouseX,mouseY);
        else if(mouseX>=x+10&&mouseX<x+302&&mouseY>=y+195&&mouseY<y+210)context.drawTooltip(textRenderer,feedback,mouseX,mouseY);
    }
    private void drawGlyph(DrawContext context,int glyph,int left,int top,int size){
        if(glyph>=0&&glyph<18){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();context.drawTexture(image("runes"),left,top,size,size,glyph*64,0,64,64,18*64,64);}
    }
    private Text glyphName(int glyph){return meanings[glyph]<0?text("unknown",glyph+1):text("role."+meanings[glyph]);}
    private int[] inkCosts(){int[] counts=new int[8];for(int glyph:slots)if(glyph>=0)counts[schools[glyph]]++;for(int school=0;school<8;school++)counts[school]=(counts[school]+1)/2;return counts;}
    private List<Text> inkLines(int[] costs){
        List<Text> lines=new ArrayList<>();var table=handler.getInventory();lines.add(text("ink_total",Arrays.stream(costs).sum()));
        for(int school=0;school<8;school++)if(costs[school]>0){final int type=school;int owned=SlottedFormationTransaction.count(table,inventory,1,stack->stack.getItem() instanceof FormationInkItem ink&&SlottedFormationTransaction.school(ink)==type);
            FormationElement element=school==7?FormationElement.UNIVERSAL:FormationElement.byId(ResearchTarget.FAMILIES.get(school));
            lines.add(text("ink_cost",Text.translatable(element.getNameKey()),costs[school],owned));}
        lines.add(text("cost_tip"));return lines;
    }
    private int paletteThumbY(){return y+PALETTE_TOP+(int)Math.round((double)paletteScroll/PALETTE_MAX_SCROLL*(PALETTE_HEIGHT-PALETTE_THUMB_HEIGHT));}
    private boolean inPalette(double mouseX,double mouseY){return mouseX>=x+6&&mouseX<x+58&&mouseY>=y+PALETTE_TOP&&mouseY<y+PALETTE_TOP+PALETTE_HEIGHT;}
    private void dragPaletteScroll(double mouseY){
        double fraction=(mouseY-y-PALETTE_TOP-paletteGrabOffset)/(PALETTE_HEIGHT-PALETTE_THUMB_HEIGHT);
        paletteScroll=(int)Math.round(Math.max(0,Math.min(1,fraction))*PALETTE_MAX_SCROLL);
    }
    private void renderNotes(DrawContext context){
        List<Text> lines=new ArrayList<>();lines.add(text("discovery_rules"));lines.add(Text.translatable("research.ssc_addon.runes.rules"));
        lines.add(glyphName(selectedGlyph));
        int total=0;for(Text line:lines)total+=textRenderer.wrapLines(line,158).size()*11+5;
        notesScroll=Math.max(0,Math.min(notesScroll,Math.max(0,total-160)));
        context.enableScissor(x+66,y+23,x+231,y+189);int top=y+24-notesScroll;
        for(Text text:lines){for(var line:textRenderer.wrapLines(text,158)){context.drawText(textRenderer,line,x+69,top,0xffece0bc,false);top+=11;}top+=5;}
        context.disableScissor();
    }
    private int slotAt(double mouseX,double mouseY){
        if(notes||referenceMenu)return -1;
        for(int index=0;index<positions.size();index++)if(Math.hypot(mouseX-(x+63+positions.get(index).x()*172/512),mouseY-(y+19+positions.get(index).y()*172/512))<8)return index;
        return -1;
    }
    private boolean overOutput(double mouseX,double mouseY){
        return !notes&&!referenceMenu&&mouseX>=x+SpellResearchTableScreenHandler.RESEARCH_OUTPUT_X-1
                &&mouseX<x+SpellResearchTableScreenHandler.RESEARCH_OUTPUT_X+17
                &&mouseY>=y+SpellResearchTableScreenHandler.RESEARCH_OUTPUT_Y-1
                &&mouseY<y+SpellResearchTableScreenHandler.RESEARCH_OUTPUT_Y+17;
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button){
        if(referenceMenu){
            refreshReferenceList();int chosen=referenceAt(mouseX,mouseY);
            if(button==0&&chosen>=0){
                selectedReferenceSlot=referenceInventorySlots.get(chosen);var data=inventory.getStack(selectedReferenceSlot).getNbt();
                if(data.getInt("Level")==level){reference=foundationReference(data);referenceVisible=true;notes=false;visibility();}
                else{feedback=text("diagram_level",data.getInt("Level"));referenceVisible=false;}
            }
            referenceMenu=false;return true;
        }
        for(Element child:children())if(child.mouseClicked(mouseX,mouseY,button)){setFocused(child);return true;}
        if(button==0&&overOutput(mouseX,mouseY)){
            if(client!=null&&client.player!=null&&client.interactionManager!=null)
                client.interactionManager.clickSlot(handler.syncId,SpellResearchTableBlockEntity.SLOT_RESEARCH_OUTPUT,0,SlotActionType.QUICK_MOVE,client.player);
            return true;
        }
        if(button==0&&inPalette(mouseX,mouseY)&&mouseX>=x+PALETTE_TRACK_X){
            int thumb=paletteThumbY();paletteGrabOffset=mouseY>=thumb&&mouseY<thumb+PALETTE_THUMB_HEIGHT?mouseY-thumb:PALETTE_THUMB_HEIGHT/2.0;
            paletteScrolling=true;paletteDragging=false;dragging=false;dragPaletteScroll(mouseY);return true;
        }
        if(world==null||loading)return true;
        if(button==0&&mouseX>=x+PALETTE_LEFT&&mouseX<x+PALETTE_LEFT+PALETTE_CELL*2&&inPalette(mouseX,mouseY)){
            int glyph=((int)(mouseY-y-PALETTE_TOP)+paletteScroll)/PALETTE_CELL*2+(int)(mouseX-x-PALETTE_LEFT)/PALETTE_CELL;
            if(glyph>=0&&glyph<18){selectedGlyph=glyph;paletteDragging=true;}return true;
        }
        int slot=slotAt(mouseX,mouseY);
        if(slot>=0&&(button==0||button==1)){dragging=true;erasing=button==1;touched.clear();paint(slot);return true;}
        return false;
    }
    private void paint(int slot){
        if(slot<0||touched.get(slot))return;touched.set(slot);int value=erasing?-1:selectedGlyph;
        setSlot(slot,value);
    }
    private void setSlot(int slot,int value){
        if(slots[slot]==value)return;slots[slot]=value;edited();
        if(feedbackState.allowEdit(System.nanoTime()))playSound(value<0?SoundEvents.ITEM_BOOK_PAGE_TURN:SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,value<0?.18f:.22f,value<0?1.2f:1.45f+slot%3*.1f);
    }
    private void playSound(SoundEvent sound,float volume,float pitch){
        if(client!=null)client.getSoundManager().play(PositionedSoundInstance.master(sound,pitch,volume));
    }
    @Override public boolean mouseDragged(double mouseX,double mouseY,int button,double deltaX,double deltaY){
        if(paletteScrolling&&button==0){dragPaletteScroll(mouseY);return true;}
        if(paletteDragging)return true;
        if(!dragging)return false;
        int steps=Math.max(1,(int)Math.ceil(Math.hypot(deltaX,deltaY)/3));
        for(int step=1;step<=steps;step++)paint(slotAt(mouseX-deltaX+deltaX*step/steps,mouseY-deltaY+deltaY*step/steps));return true;
    }
    @Override public boolean mouseReleased(double mouseX,double mouseY,int button){
        if(paletteScrolling){paletteScrolling=false;return true;}
        if(paletteDragging&&button==0){int slot=slotAt(mouseX,mouseY);if(slot>=0)setSlot(slot,selectedGlyph);}
        paletteDragging=false;dragging=false;return true;
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double amount){
        if(referenceMenu){referenceScroll=Math.max(0,Math.min(maxReferenceScroll(),referenceScroll-(int)amount));return true;}
        if(notes&&mouseX>=x+63&&mouseX<x+236){notesScroll-=(int)(amount*22);return true;}
        if(inPalette(mouseX,mouseY)){paletteScroll=Math.max(0,Math.min(PALETTE_MAX_SCROLL,paletteScroll-(int)(amount*PALETTE_CELL)));return true;}return false;
    }
    private void edited(){revision++;animation=0;dirty=true;editedAt=System.currentTimeMillis();feedback=text("edited");diagnosis=new int[0];drafts.put(level,slots.clone());}
    private void clearCurrent(){
        if(busy||loading||notes||Arrays.stream(slots).noneMatch(value->value>=0))return;
        dragging=false;erasing=false;paletteDragging=false;paletteScrolling=false;touched.clear();
        referenceVisible=false;referenceMenu=false;slots=RuneLayout.empty(level);edited();feedback=text("cleared");
        playSound(SoundEvents.ITEM_BOOK_PAGE_TURN,.25f,.8f);
    }
    private int[] foundationReference(NbtCompound data){return AnalyzedSpellDiagramItem.referenceSlots(data);}
    private void refreshReferenceList(){
        referenceInventorySlots.clear();if(world!=null)for(int slot=0;slot<Math.min(36,inventory.size());slot++)if(AnalyzedSpellDiagramItem.valid(inventory.getStack(slot),world))referenceInventorySlots.add(slot);
        referenceScroll=Math.max(0,Math.min(referenceScroll,maxReferenceScroll()));
    }
    private int maxReferenceScroll(){return (Math.max(0,referenceInventorySlots.size()-REFERENCE_CAPACITY)+REFERENCE_COLUMNS-1)/REFERENCE_COLUMNS;}
    private int referenceAt(double mouseX,double mouseY){
        double localX=mouseX-x-REFERENCE_LEFT-1,localY=mouseY-y-REFERENCE_TOP-1;
        if(localX<0||localY<0||localX>=REFERENCE_COLUMNS*18||localY>=80)return -1;
        int local=(int)localY/20*REFERENCE_COLUMNS+(int)localX/18,index=referenceScroll*REFERENCE_COLUMNS+local;
        return local<REFERENCE_CAPACITY&&index<referenceInventorySlots.size()?index:-1;
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ESCAPE){if(referenceMenu){referenceMenu=false;return true;}if(notes){notes=false;visibility();return true;}back(0);return true;}
        if(client!=null&&client.options.inventoryKey.matchesKey(key,scan)){back(0);return true;}
        return false;
    }
    private void back(int tab){save();handoff=true;parent.openResearchTab(tab);}
    @Override public void close(){back(0);}
    @Override public void removed(){if(!handoff){save();super.removed();}}

    public static final class FeedbackState {
        private UUID pending;
        private int action;
        private long lastEdit;
        private boolean playedEdit;

        public UUID begin(int nextAction){action=nextAction;pending=UUID.randomUUID();return pending;}
        public boolean finish(UUID request,int replyAction){
            if(pending==null||!pending.equals(request)||replyAction!=action)return false;
            pending=null;return true;
        }
        public void cancel(){pending=null;}
        public boolean allowEdit(long now){
            if(playedEdit&&now-lastEdit<70_000_000L)return false;
            playedEdit=true;lastEdit=now;return true;
        }
    }
}
