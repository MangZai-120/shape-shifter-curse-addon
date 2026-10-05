package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.Arrays;

public final class SlottedFormationChecks {
    private SlottedFormationChecks() {}
    public static void main(String[] args) throws Exception {
        RuneEnhancementChecks.run();
        RandomRuneFormationChecks.run();
        int[] totals = {3, 8, 10, 18, 31};
        for (int level = 1; level <= 5; level++) {
            require(SlottedFormation.size(level) == totals[level - 1], "槽位总数");
            require(SlottedFormation.validDraft(level, SlottedFormation.empty(level)), "空草稿");
            var points = SlottedFormation.positions(level);
            for (int index = 0; index < points.size(); index++) {
                var point = points.get(index);
                require(point.x() > 24 && point.x() < 488 && point.y() > 24 && point.y() < 488, "槽位边界");
                for (int prior = 0; prior < index; prior++) require(RuneGlyphs.distance(point, points.get(prior)) >= 48, "槽位命中区重叠");
            }
            var current=RuneLayout.positions(level);
            require(current.size()==(level==4?19:totals[level-1]),"新版法阵总槽数");
            for(int index=0;index<current.size();index++){
                var point=current.get(index);
                require(point.x()>24&&point.x()<488&&point.y()>24&&point.y()<488,"新版槽位边界");
                for(int prior=0;prior<index;prior++)require(RuneGlyphs.distance(point,current.get(prior))>=48,"新版槽位命中区重叠");
            }
        }
        require(java.util.Arrays.equals(RuneLayout.layers(4),new int[]{6,5,8})&&RuneLayout.baseSize(4)==11,"四级六芒星和小环基础，大环自由");
        for (int seed = 0; seed < 128; seed++) {
            RuneLanguage language = RuneLanguage.generate(seed);
            int fire = language.glyph(RuneRole.FIRE), ice = language.glyph(RuneRole.ICE), normal = language.glyph(RuneRole.STABLE);
            int[] ink = SlottedFormation.ink(language, new int[]{fire, fire, fire, ice, ice, ice, normal, normal});
            require(ink[0] == 2 && ink[1] == 2 && ink[7] == 1 && Arrays.stream(ink).sum() == 5, "分系取整");
            require(Arrays.stream(SlottedFormation.ink(language, SlottedFormation.empty(5))).sum() == 0, "空槽费用");
        }
        require(!SlottedFormation.validDraft(1, new int[]{0, 1, 18}), "非法符文");
        require(!SlottedFormation.validDraft(5, new int[32]), "额外槽位");
        for(int level=1;level<=5;level++){
            java.util.Set<String> signatures=new java.util.HashSet<>();
            for(var recipe:SlottedSpellRecipes.all()){
                var roles=SlottedSpellRecipes.roles(recipe,level);
                require(roles.size()==SlottedFormation.size(level),"配方槽位数");
                require(signatures.add(roles.toString()),"配方不唯一");
            }
        }
        // 阶段A语义规则表校验（2026-10-01 获批方案）：法术→(行为,修饰) 必须与规则表完全一致，
        // 防止未来新增/修改法术时随手破坏可推导性。判定标准见 SlottedSpellRecipes 头部注释。
        java.util.Map<String,RuneRole[]> semanticRules=new java.util.LinkedHashMap<>();
        semanticRules.put("fire_bolt",new RuneRole[]{RuneRole.SOURCE,RuneRole.STORE});          // 单体直投+点燃延时
        semanticRules.put("flame_nova",new RuneRole[]{RuneRole.GAIN,RuneRole.STORE});           // 自身扩散+着火
        semanticRules.put("meteor",new RuneRole[]{RuneRole.CONVERT,RuneRole.STORE});            // 锁定落点蓄力+点燃
        semanticRules.put("frost_spike",new RuneRole[]{RuneRole.SOURCE,RuneRole.GAIN});         // 单体直投+纯直伤
        semanticRules.put("ice_barrage",new RuneRole[]{RuneRole.SPLIT,RuneRole.GAIN});          // 一次三路+直伤
        semanticRules.put("frost_nova",new RuneRole[]{RuneRole.GAIN,RuneRole.CONVERT});         // 自身扩散+缓速
        semanticRules.put("frost_armor",new RuneRole[]{RuneRole.STABLE,RuneRole.STORE});        // 凝驻自身+封存化解
        semanticRules.put("moonlight_arrow",new RuneRole[]{RuneRole.SOURCE,RuneRole.GAIN});     // 单体直投+亡灵特攻
        semanticRules.put("lunar_mend",new RuneRole[]{RuneRole.RECOVER,RuneRole.CONVERT});      // 能量回流+净化负面
        semanticRules.put("lunar_veil",new RuneRole[]{RuneRole.STABLE,RuneRole.MERGE});        // 凝驻周身+联结友方
        semanticRules.put("lunar_phase",new RuneRole[]{RuneRole.CONVERT,RuneRole.STORE});       // 锁定蓄力+百分比封顶
        semanticRules.put("curse_mark",new RuneRole[]{RuneRole.INSIGHT,RuneRole.GAIN});         // 指向印记+易伤
        semanticRules.put("dread_whisper",new RuneRole[]{RuneRole.SPLIT,RuneRole.CONVERT});     // 锥形多路+虚弱缓速
        semanticRules.put("corrupt_mist",new RuneRole[]{RuneRole.STABLE,RuneRole.STORE});       // 雾驻周身+中毒延时
        semanticRules.put("summon_lunar_spirit",new RuneRole[]{RuneRole.MERGE,RuneRole.STABLE});// 聚合实体+驻留持续
        semanticRules.put("companion_resonance",new RuneRole[]{RuneRole.MERGE,RuneRole.GAIN});  // 联结宠物+伤害提升
        semanticRules.put("beep_sheep",new RuneRole[]{RuneRole.SOURCE,RuneRole.CONVERT});       // 单体直投+命中变羊
        semanticRules.put("void_devour",new RuneRole[]{RuneRole.CONVERT,RuneRole.STORE});       // 锁定蓄力+致盲封存
        semanticRules.put("void_erosion",new RuneRole[]{RuneRole.GAIN,RuneRole.CONVERT});       // 自身扩散+虚弱疲劳
        semanticRules.put("space_blink",new RuneRole[]{RuneRole.BRIDGE,RuneRole.SOURCE});       // 贯通两点+立即生效
        semanticRules.put("space_stride",new RuneRole[]{RuneRole.BRIDGE,RuneRole.STABLE});      // 贯通两点+持续生效
        semanticRules.put("space_recall",new RuneRole[]{RuneRole.BRIDGE,RuneRole.INSIGHT});     // 贯通两点+锚定重生点
        semanticRules.put("pocket_space",new RuneRole[]{RuneRole.BRIDGE,RuneRole.STORE});       // 贯通两点+容纳空间
        require(semanticRules.size()==SlottedSpellRecipes.all().size(),"规则表覆盖全部法术");
        java.util.Map<String,Integer> perFamily=new java.util.HashMap<>();
        for(var recipe:SlottedSpellRecipes.all()){
            RuneRole[] expected=semanticRules.get(recipe.spell());
            require(expected!=null,"规则表缺 "+recipe.spell());
            require(recipe.behavior()==expected[0]&&recipe.modifier()==expected[1],
                    "签名不符合语义规则表: "+recipe.spell());
            // 系内唯一性：同系法术的(行为,修饰)组合不得撞车（identify 按全系逐格判定，撞车即歧义）
            String signature=recipe.family()+":"+recipe.behavior()+"/"+recipe.modifier();
            require(perFamily.putIfAbsent(signature,recipe.family())==null,"系内签名冲突: "+recipe.spell());
        }
        // legacy 对照表校验：只收录真实变更过的法术，且键必须都在现表内（阶段E迁移依据）
        require(SlottedSpellRecipes.LEGACY_SIGNATURES.size()==13,"legacy 表恰好13项");
        // 阶段B：试运行分级诊断纯逻辑校验（2026-10-01 获批方案）
        // 注：纯 JVM 环境下 SpellRegistry 未初始化，available() 恒 false，故此处直接用全配方表
        //（与上方唯一性校验同风格）；diagnose 纯逻辑与配方过滤无关，运行时入口另由集成测试覆盖。
        for(int seed=0;seed<16;seed++){
            RuneLanguage language=RuneLanguage.generate(seed);
            for(int level=1;level<=5;level++){
                java.util.List<SlottedSpellRecipes.Recipe> recipes=SlottedSpellRecipes.all();
                int size=SlottedFormation.size(level);
                // 用例1：合法解（图纸照抄路径）→ 无诊断（valid，不下发 Diagnosis）
                var target=recipes.get(0);
                int[] legal=SlottedSpellRecipes.glyphs(target,level,language);
                int[] flat=FormationDiagnosis.diagnose(level,legal,language,recipes);
                require(flat.length>0&&flat[flat.length-1]==1,"合法解组合应一致");
                for(int layer=0;layer<flat[0];layer++)require(flat[1+layer*4]==0&&flat[1+layer*4+1]==0&&flat[1+layer*4+2]==0,"合法解各环无问题");
                // 用例2：空槽 → 每个含空槽的环报空槽数
                int[] partial=legal.clone();partial[0]=-1;partial[size-1]=-1;
                flat=FormationDiagnosis.diagnose(level,partial,language,recipes);
                require(flat[1]>0,"首环应报空槽");
                // 用例3：结构槽错位 → 报结构错位数
                int[] broken=legal.clone();broken[1]=language.glyph(RuneRole.FIRE);
                flat=FormationDiagnosis.diagnose(level,broken,language,recipes);
                require(flat.length>0,"结构错位应有诊断");
                // 用例4：非法输入 → 空数组，回退 invalid
                require(FormationDiagnosis.diagnose(level,new int[]{99},language,recipes).length==0,"非法输入回退");
            }
        }
        // 阶段D：外环整体旋转（2026-10-01 获批）——L4 大环与 L5 超大环可整体旋转，内层仍精确。
        for(int seed=0;seed<8;seed++){
            RuneLanguage language=RuneLanguage.generate(seed);
            // D1：轨道唯一性——任意两法术的 L4/L5 最外环角色序列互不为旋转关系（否则 identify 会歧义）
            for(int level: new int[]{4,5}){
                java.util.List<int[]> rings=new java.util.ArrayList<>();java.util.List<String> owners=new java.util.ArrayList<>();
                int[] counts=SlottedFormation.layers(level);int from=SlottedFormation.size(level)-counts[counts.length-1];
                for(var recipe:SlottedSpellRecipes.all()){
                    int[] roles=SlottedSpellRecipes.roles(recipe,level).stream().mapToInt(Enum::ordinal).toArray();
                    int[] ring=java.util.Arrays.copyOfRange(roles,from,roles.length);
                    for(int prior=0;prior<rings.size();prior++)
                        require(!isRotation(ring,rings.get(prior)),"外环旋转轨道冲突: "+owners.get(prior)+" vs "+recipe.spell());
                    rings.add(ring);owners.add(recipe.spell());
                }
            }
            // D2：canonical 的全部非平凡旋转均 matches 原配方，且不 matches 任何其它配方
            //（纯 JVM 下 SpellRegistry 未初始化、available() 恒 false，故绕过 identify 直接测 matches）
            for(var recipe:SlottedSpellRecipes.all()){
                for(int level: new int[]{4,5}){
                    int[] canonical=SlottedSpellRecipes.glyphs(recipe,level,language);
                    int[] counts=SlottedFormation.layers(level);int from=SlottedFormation.size(level)-counts[counts.length-1];int len=canonical.length-from;
                    for(int shift=1;shift<len;shift++){
                        int[] rotated=canonical.clone();
                        for(int i=0;i<len;i++)rotated[from+i]=canonical[from+(i+shift)%len];
                        require(SlottedSpellRecipes.matches(recipe,level,rotated,language),
                                "旋转后仍匹配原配方: "+recipe.spell()+" L"+level);
                        for(var other:SlottedSpellRecipes.all())if(other!=recipe)
                            require(!SlottedSpellRecipes.matches(other,level,rotated,language),
                                    "旋转后不得误匹配其它配方: "+recipe.spell()+" -> "+other.spell());
                    }
                }
            }
            // D3：非旋转的乱序（交换首两元素）不成立
            for(var recipe:SlottedSpellRecipes.all()){
                int[] canonical=SlottedSpellRecipes.glyphs(recipe,1,language);
                int[] swapped=canonical.clone();int t=swapped[0];swapped[0]=swapped[1];swapped[1]=t;
                require(!SlottedSpellRecipes.matches(recipe,1,swapped,language),"乱序不应成立");
            }
            // 阶段E：旧序列惰性迁移——旧 glyphs 命中 migrateIfLegacy 则返回新序列且 matches 成立
            for(var entry:SlottedSpellRecipes.LEGACY_SIGNATURES.entrySet()){
                var recipe=SlottedSpellRecipes.get(entry.getKey());
                for(int level=1;level<=5;level++){
                    int[] legacy=SlottedSpellRecipes.legacyGlyphs(entry.getKey(),level,language);
                    if(legacy==null)continue;
                    int[] migrated=SlottedSpellRecipes.migrateIfLegacy(entry.getKey(),level,legacy,language);
                    require(migrated!=null&&SlottedSpellRecipes.matches(recipe,level,migrated,language),
                            "旧序列迁移后应成立: "+entry.getKey()+" L"+level);
                    // 幂等：新序列再迁移应返回 null（不匹配旧序列）
                    require(SlottedSpellRecipes.migrateIfLegacy(entry.getKey(),level,migrated,language)==null,
                            "迁移幂等: "+entry.getKey()+" L"+level);
                }
            }
        }
        for(var entry:SlottedSpellRecipes.LEGACY_SIGNATURES.entrySet()){
            var current=SlottedSpellRecipes.get(entry.getKey());
            require(current!=null,"legacy 表包含未知法术: "+entry.getKey());
            require(current.behavior()!=entry.getValue()[0]||current.modifier()!=entry.getValue()[1],
                    "legacy 表混入未变更签名: "+entry.getKey());
        }
        for(int level=1;level<=5;level++){
            try(var input=SlottedFormationChecks.class.getResourceAsStream("/assets/ssc_addon/textures/gui/formation_research/tier_"+level+".png")){
                require(input!=null,"法阵PNG缺失");var image=javax.imageio.ImageIO.read(input);
                require(image.getWidth()==1024&&image.getHeight()==1024,"法阵PNG尺寸");
                require(image.getColorModel().hasAlpha()&&(image.getRGB(0,0)>>>24)==0,"法阵透明边界");
                require((image.getRGB(512,60)>>>24)!=255,"没有整页不透明底色");
                var points=RuneLayout.positions(level);
                for(int slot=0;slot<points.size();slot++){
                    var point=points.get(slot);
                    int pixel=image.getRGB((int)Math.round(point.x()*2),(int)Math.round((point.y()-22)*2));
                    int red=(pixel>>16)&255,blue=pixel&255;
                    require(RuneLayout.enhancement(level,slot)?blue>red+80:red>blue+80,"基础圆圈金色，自定义圆圈蓝色");
                }
            }
        }
        var runes=guiImage("runes");
        require(runes.getWidth()==18*64&&runes.getHeight()==64,"符文图集尺寸");
        for(int glyph=0;glyph<18;glyph++){
            int bluePixels=0,glowPixels=0;
            for(int row=0;row<64;row++)for(int column=0;column<64;column++){
                int pixel=runes.getRGB(glyph*64+column,row),alpha=pixel>>>24;
                int red=(pixel>>16)&255,green=(pixel>>8)&255,blue=pixel&255;
                if(alpha>200&&blue>220&&green>160&&blue-red>80)bluePixels++;
                if(alpha>0&&alpha<200&&blue>red+80)glowPixels++;
            }
            require(bluePixels>20&&glowPixels>20,"符文亮蓝笔画和柔光");
            require((runes.getRGB(glyph*64,0)>>>24)==0,"符文透明边距");
        }
        var background=guiImage("background");int panel=background.getRGB(16,40);
        require(((panel>>16)&255)<50&&((panel>>8)&255)<70&&(panel&255)<80,"符文栏深色底板");
        require(background.getRGB(5,40)!=panel,"符文栏可见边框");
        var scrollbar=guiImage("palette_scroll");
        require(scrollbar.getWidth()==8&&scrollbar.getHeight()==32,"滚动条图集尺寸");
        require((scrollbar.getRGB(5,16)&255)>(scrollbar.getRGB(1,16)&255)+80,"滚动滑块与轨道对比");
        var clear=guiImage("widgets");
        require(clear.getWidth()==32&&clear.getHeight()==32,"清空图标仅保留红叉");
        require(clear.getColorModel().hasAlpha()&&(clear.getRGB(0,0)>>>24)==0,"清空图标透明边界");
        int cross=clear.getRGB(16,16);
        require((cross>>>24)>200&&((cross>>16)&255)>180&&((cross>>8)&255)<100,"红叉笔画保留");
        var socket=guiImage("sockets");
        require(socket.getWidth()==128&&socket.getHeight()==64,"槽位保留金色和蓝色圆形高亮");
        require(socket.getColorModel().hasAlpha()&&(socket.getRGB(32,32)>>>24)==0&&(socket.getRGB(32,4)>>>24)>200,"槽位透明中心和高亮边缘");
        int outer=socket.getRGB(96,4);
        require((outer&255)>((outer>>16)&255)+80&&(socket.getRGB(96,32)>>>24)==0,"外圈高亮为蓝色且中心透明");
        try(var input=SlottedFormationChecks.class.getResourceAsStream("/assets/ssc_addon/models/item/spell_formation.json")){
            require(input!=null,"研究产物模型缺失");
            var model=com.google.gson.JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            require(model.get("parent").getAsString().equals("ssc_addon:item/magic_scroll")&&!model.has("overrides"),"研究产物直接复用卷轴模型，不按旧等级切换自绘图标");
        }
        try(var input=SlottedFormationChecks.class.getResourceAsStream("/assets/ssc_addon/models/item/magic_scroll.json")){
            var model=com.google.gson.JsonParser.parseString(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for(var layer:model.getAsJsonObject("textures").entrySet()){
                String texture=layer.getValue().getAsString();
                require(SlottedFormationChecks.class.getResource("/assets/ssc_addon/textures/"+texture.substring("ssc_addon:".length())+".png")!=null,"卷轴两层贴图存在");
            }
        }
        for(String name:java.util.List.of("spell_formation","spell_formation_1","spell_formation_2","spell_formation_3","spell_formation_4","spell_formation_5")){
            require(SlottedFormationChecks.class.getResource("/assets/ssc_addon/textures/item/"+name+".png")==null,"不保留自绘法阵产物贴图");
            if(!name.equals("spell_formation"))require(SlottedFormationChecks.class.getResource("/assets/ssc_addon/models/item/"+name+".json")==null,"不保留旧等级模型");
        }
        var analysis=guiImage("analysis_progress");require(analysis.getWidth()==78&&analysis.getHeight()==24,"解析进度条尺寸");
        for(String removed:java.util.List.of("tabs","reference_panel")){
            require(SlottedFormationChecks.class.getResource("/assets/ssc_addon/textures/gui/formation_research/"+removed+".png")==null,"废弃贴图仍在资源中："+removed);
        }
        System.out.println("Slotted formation: geometry, costs, 128 languages, recipes, five RGBA templates, cyan rune glow, dark palette, scrollbar and compact PNG assets PASS.");
    }
    private static java.awt.image.BufferedImage guiImage(String name)throws java.io.IOException{
        try(var input=SlottedFormationChecks.class.getResourceAsStream("/assets/ssc_addon/textures/gui/formation_research/"+name+".png")){
            require(input!=null,"界面PNG缺失："+name);var image=javax.imageio.ImageIO.read(input);require(image!=null,"界面PNG无法解码："+name);return image;
        }
    }
    /** 环形序列旋转等价判定：b 是否为 a 的整体旋转。 */
    private static boolean isRotation(int[] a,int[] b){
        if(a.length!=b.length)return false;int n=a.length;
        for(int shift=0;shift<n;shift++){
            boolean same=true;
            for(int i=0;i<n;i++)if(a[i]!=b[(i+shift)%n]){same=false;break;}
            if(same)return true;
        }
        return false;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
