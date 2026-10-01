package net.jackcooper.shapeShifterCurseAddon.spell.research;

import java.util.Arrays;

public final class SlottedFormationChecks {
    private SlottedFormationChecks() {}
    public static void main(String[] args) throws Exception {
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
        }
        for (int seed = 0; seed < 128; seed++) {
            RuneLanguage language = RuneLanguage.generate(seed);
            int fire = language.glyph(RuneRole.FIRE), ice = language.glyph(RuneRole.ICE), normal = language.glyph(RuneRole.STABLE);
            int[] ink = SlottedFormation.ink(language, new int[]{fire, fire, fire, ice, ice, ice, normal, normal});
            require(ink[0] == 2 && ink[1] == 2 && ink[7] == 1 && Arrays.stream(ink).sum() == 5, "分系取整");
            require(Arrays.stream(SlottedFormation.ink(language, SlottedFormation.empty(5))).sum() == 0, "空槽费用");
        }
        require(!SlottedFormation.validDraft(1, new int[]{0, 1, 17}), "非法符文");
        require(!SlottedFormation.validDraft(5, new int[32]), "额外槽位");
        for(int level=1;level<=5;level++){
            java.util.Set<String> signatures=new java.util.HashSet<>();
            for(var recipe:SlottedSpellRecipes.all()){
                var roles=SlottedSpellRecipes.roles(recipe,level);
                require(roles.size()==SlottedFormation.size(level),"配方槽位数");
                require(signatures.add(roles.toString()),"配方不唯一");
            }
        }
        for(int level=1;level<=5;level++){
            try(var input=SlottedFormationChecks.class.getResourceAsStream("/assets/ssc_addon/textures/gui/formation_research/tier_"+level+".png")){
                require(input!=null,"法阵PNG缺失");var image=javax.imageio.ImageIO.read(input);
                require(image.getWidth()==1024&&image.getHeight()==1024,"法阵PNG尺寸");
                require(image.getColorModel().hasAlpha()&&(image.getRGB(0,0)>>>24)==0,"法阵透明边界");
                require((image.getRGB(512,60)>>>24)!=255,"没有整页不透明底色");
            }
        }
        var runes=guiImage("runes");
        require(runes.getWidth()==17*64&&runes.getHeight()==64,"符文图集尺寸");
        for(int glyph=0;glyph<17;glyph++){
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
        require(socket.getWidth()==64&&socket.getHeight()==64,"槽位仅保留高亮状态");
        require(socket.getColorModel().hasAlpha()&&(socket.getRGB(32,32)>>>24)==0&&(socket.getRGB(32,4)>>>24)>200,"槽位透明中心和高亮边缘");
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
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}