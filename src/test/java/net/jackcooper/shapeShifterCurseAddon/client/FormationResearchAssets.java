package net.jackcooper.shapeShifterCurseAddon.client;

import net.jackcooper.shapeShifterCurseAddon.spell.research.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

public final class FormationResearchAssets {
    private static final Color GOLD=new Color(246,199,82),LIGHT=new Color(255,243,190),TEAL=new Color(105,185,174);
    private FormationResearchAssets(){}
    public static void main(String[] args)throws Exception{
        Path directory=Path.of(args[0]);Files.createDirectories(directory);
        for(int level=1;level<=5;level++){
            BufferedImage image=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_ARGB);Graphics2D pen=graphics(image);pen.scale(2,2);
            int[] counts=SlottedFormation.layers(level);var points=SlottedFormation.positions(level);
            int offset=0;
            for(int layer=0;layer<counts.length;layer++){
                double radius=Math.hypot(points.get(offset).x()-256,points.get(offset).y()-256);
                if(layer==0){
                    int step=counts[0]==5?2:counts[0]==6?2:1;
                    for(int pass=0;pass<(counts[0]==6?2:1);pass++){
                        Path2D star=new Path2D.Double();int count=counts[0]==6?3:counts[0];
                        for(int vertex=0;vertex<=count;vertex++){var point=points.get((pass+vertex*step)%counts[0]);if(vertex==0)star.moveTo(point.x(),point.y());else star.lineTo(point.x(),point.y());}
                        pen.setColor(new Color(255,187,53,36));pen.setStroke(new BasicStroke(9));pen.draw(star);
                        pen.setColor(GOLD);pen.setStroke(new BasicStroke(2));pen.draw(star);
                        AffineTransform saved=pen.getTransform();pen.translate(256,256);pen.scale(.91,.91);pen.translate(-256,-256);pen.setColor(LIGHT);pen.setStroke(new BasicStroke(.7f));pen.draw(star);pen.setTransform(saved);
                    }
                    circle(pen,Math.max(22,radius*.36),LIGHT,.8f);
                    circle(pen,Math.max(27,radius*.43),GOLD,.8f);
                }else{
                    circle(pen,radius-8,GOLD,1.3f);circle(pen,radius+8,LIGHT,1);circle(pen,radius+12,GOLD,.6f);
                    int ticks=counts[layer]*12;
                    for(int index=0;index<ticks;index++){
                        double angle=index*Math.PI*2/ticks;
                        radial(pen,angle,radius+13,radius+(index%3==0?18:15),GOLD,.6f);
                        if(index%3==1){
                            AffineTransform saved=pen.getTransform();pen.translate(256+Math.cos(angle)*(radius-15),256+Math.sin(angle)*(radius-15));pen.rotate(angle+Math.PI/2);
                            glyph(pen,(index+layer*3)%17,0,0,2.2,LIGHT);pen.setTransform(saved);
                        }
                    }
                }
                offset+=counts[layer];
            }
            for(int petal=0;petal<12;petal++){
                double angle=petal*Math.PI/6;
                pen.setColor(TEAL);pen.setStroke(new BasicStroke(.7f));
                pen.draw(new QuadCurve2D.Double(256,256,256+Math.cos(angle+.5)*25,256+Math.sin(angle+.5)*25,256+Math.cos(angle)*20,256+Math.sin(angle)*20));
            }
            for(var point:points){
                pen.setComposite(AlphaComposite.Clear);pen.fill(new Ellipse2D.Double(point.x()-23,point.y()-23,46,46));pen.setComposite(AlphaComposite.SrcOver);
                pen.setColor(new Color(19,27,29,235));pen.fill(new Ellipse2D.Double(point.x()-22,point.y()-22,44,44));
                pen.setColor(GOLD);pen.setStroke(new BasicStroke(1));pen.draw(new Ellipse2D.Double(point.x()-22,point.y()-22,44,44));
                pen.setColor(new Color(134,110,62));pen.draw(new Ellipse2D.Double(point.x()-19,point.y()-19,38,38));
            }
            pen.dispose();save(directory,"tier_"+level,image);
            BufferedImage glow=new BufferedImage(1024,1024,BufferedImage.TYPE_INT_ARGB);Graphics2D glowPen=graphics(glow);glowPen.drawImage(image,0,0,null);glowPen.setComposite(AlphaComposite.SrcIn);glowPen.setColor(new Color(255,236,158,90));glowPen.fillRect(0,0,1024,1024);glowPen.dispose();save(directory,"tier_"+level+"_glow",glow);
            if(level==5){BufferedImage product=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);Graphics2D small=graphics(product);small.drawImage(image,0,0,64,64,null);small.dispose();save(directory,"product",product);}
        }
        BufferedImage runes=new BufferedImage(17*64,64,BufferedImage.TYPE_INT_ARGB);Graphics2D runePen=graphics(runes);
        for(int glyph=0;glyph<17;glyph++){
            for(int spread=4;spread>=1;spread--)for(int offsetX=-spread;offsetX<=spread;offsetX++)for(int offsetY=-spread;offsetY<=spread;offsetY++){
                if(offsetX*offsetX+offsetY*offsetY>spread*spread)continue;
                glyph(runePen,glyph,glyph*64+32+offsetX,32+offsetY,18,new Color(24,146,255,spread==1?18:5));
            }
            glyph(runePen,glyph,glyph*64+32,32,18,new Color(87,215,255));
        }
        runePen.dispose();save(directory,"runes",runes);
        BufferedImage sockets=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);Graphics2D socketPen=graphics(sockets);
        socketPen.setColor(GOLD);socketPen.setStroke(new BasicStroke(3));socketPen.drawOval(4,4,56,56);socketPen.setStroke(new BasicStroke(1));socketPen.drawOval(9,9,46,46);
        socketPen.dispose();save(directory,"sockets",sockets);
        BufferedImage background=new BufferedImage(312,212,BufferedImage.TYPE_INT_ARGB);Graphics2D back=graphics(background);
        back.setColor(new Color(198,198,198));back.fillRect(0,0,312,212);back.setColor(Color.WHITE);back.drawLine(1,1,310,1);back.drawLine(1,1,1,210);back.setColor(new Color(70,70,70));back.drawLine(311,1,311,211);back.drawLine(1,211,311,211);
        back.setPaint(new GradientPaint(64,20,new Color(33,43,45),220,182,new Color(12,20,25)));back.fillRect(62,18,174,174);
        back.setColor(new Color(116,92,45));back.drawRect(61,17,175,175);back.setColor(new Color(231,207,147));back.drawRect(59,15,179,179);
        for(int corner=0;corner<4;corner++){int left=corner%2==0?60:234,top=corner<2?16:190;back.setColor(GOLD);back.fill(new Polygon(new int[]{left,left+3,left,left-3},new int[]{top-3,top,top+3,top},4));}
        back.setPaint(new GradientPaint(6,23,new Color(14,25,36),56,146,new Color(23,39,51)));back.fillRect(6,24,52,122);
        back.setColor(new Color(80,110,130));back.drawRect(5,23,53,123);back.setColor(new Color(10,18,27));back.drawRect(6,24,51,121);
        back.dispose();save(directory,"background",background);
        BufferedImage scrollbar=new BufferedImage(8,32,BufferedImage.TYPE_INT_ARGB);Graphics2D scrollPen=graphics(scrollbar);
        scrollPen.setColor(new Color(7,16,24));scrollPen.fillRect(0,0,4,32);scrollPen.setColor(new Color(54,75,88));scrollPen.drawRect(0,0,3,31);
        scrollPen.setColor(new Color(56,159,198));scrollPen.fillRect(4,0,4,32);scrollPen.setColor(new Color(142,223,247));scrollPen.drawRect(4,0,3,31);
        scrollPen.dispose();save(directory,"palette_scroll",scrollbar);
        BufferedImage widgets=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);Graphics2D icons=graphics(widgets);
        icons.setColor(new Color(220,54,54));icons.setStroke(new BasicStroke(4,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        icons.drawLine(8,8,24,24);icons.drawLine(24,8,8,24);
        icons.dispose();save(directory,"widgets",widgets);
        BufferedImage progress=new BufferedImage(78,24,BufferedImage.TYPE_INT_ARGB);Graphics2D progressPen=graphics(progress);
        progressPen.setColor(new Color(43,53,57));progressPen.fillRect(0,0,78,12);progressPen.setColor(new Color(118,124,124));progressPen.drawRect(0,0,77,11);
        progressPen.setPaint(new GradientPaint(0,12,new Color(61,158,199),78,24,new Color(139,234,255)));progressPen.fillRect(0,12,78,12);progressPen.dispose();save(directory,"analysis_progress",progress);
        BufferedImage diagram=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);Graphics2D paperPen=graphics(diagram);
        paperPen.setColor(new Color(81,54,29));paperPen.fillRect(5,2,21,28);paperPen.setColor(new Color(232,205,143));paperPen.fillRect(6,3,19,26);
        paperPen.setColor(new Color(253,239,187));paperPen.fillRect(7,4,17,24);paperPen.setColor(new Color(169,130,65));paperPen.fillRect(3,2,24,3);paperPen.fillRect(5,27,24,3);
        paperPen.setColor(new Color(52,135,165));paperPen.setStroke(new BasicStroke(1.4f));paperPen.drawOval(9,8,12,12);paperPen.drawPolygon(new int[]{15,10,20},new int[]{9,18,18},3);paperPen.drawLine(10,23,21,23);
        paperPen.dispose();Path itemDirectory=directory.getParent().getParent().resolve("item");Files.createDirectories(itemDirectory);save(itemDirectory,"analyzed_spell_diagram",diagram);
        System.out.println("PNG assets generated: five 1024px arrays, glow masks, transparent glyphs, sockets, native-size research background and icons.");
    }
    private static Graphics2D graphics(BufferedImage image){Graphics2D pen=image.createGraphics();pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);pen.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);return pen;}
    private static void circle(Graphics2D pen,double radius,Color color,float width){pen.setColor(color);pen.setStroke(new BasicStroke(width));pen.draw(new Ellipse2D.Double(256-radius,256-radius,radius*2,radius*2));}
    private static void radial(Graphics2D pen,double angle,double inner,double outer,Color color,float width){pen.setColor(color);pen.setStroke(new BasicStroke(width));pen.draw(new Line2D.Double(256+Math.cos(angle)*inner,256+Math.sin(angle)*inner,256+Math.cos(angle)*outer,256+Math.sin(angle)*outer));}
    private static void glyph(Graphics2D pen,int glyph,double centerX,double centerY,double size,Color color){
        var points=RuneGlyphs.shape(glyph);Path2D path=new Path2D.Double();
        for(int index=0;index<points.size();index++){var point=points.get(index);double px=centerX+point.x()*size,py=centerY+point.y()*size;if(index==0)path.moveTo(px,py);else path.lineTo(px,py);}
        pen.setColor(color);pen.setStroke(new BasicStroke((float)Math.max(.6,size*.13),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));pen.draw(path);
    }
    private static void save(Path directory,String name,BufferedImage image)throws Exception{ImageIO.write(image,"PNG",directory.resolve(name+".png").toFile());}
}