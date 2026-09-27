import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Deterministic native pixel materials. Opaque, low contrast, no animation or alpha shimmer. */
public class GenerateWindTextures {
    static final String OUT="src/main/resources/assets/homelink_energy/textures/block/";
    static BufferedImage material(int color) {
        var image=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<32;y++)for(int x=0;x<32;x++) {
            int delta=((x/4+y/8)%3)-1;
            int r=((color>>16)&255)+delta,g=((color>>8)&255)+delta,b=(color&255)+delta;
            image.setRGB(x,y,0xff000000|(r<<16)|(g<<8)|b);
        }
        return image;
    }
    static void rect(BufferedImage image,int x0,int y0,int x1,int y1,int color) {
        for(int y=y0;y<y1;y++)for(int x=x0;x<x1;x++)image.setRGB(x,y,0xff000000|color);
    }
    static void write(String name,BufferedImage image) throws Exception { ImageIO.write(image,"png",new File(OUT+name+".png")); }
    public static void main(String[] args) throws Exception {
        new File(OUT).mkdirs();
        var paint=material(0xdce0de);
        rect(paint,0,0,2,32,0xcbd1cf);rect(paint,29,0,32,32,0xd3d8d6);
        write("wind_painted_steel",paint);
        var dark=material(0x353c40);rect(dark,0,0,32,2,0x41484b);write("wind_graphite",dark);
        var copper=material(0xba805a);rect(copper,0,0,32,3,0xcf9770);rect(copper,0,29,32,32,0xa7704e);write("wind_copper",copper);
        var gold=material(0xb4a175);rect(gold,0,0,32,3,0xccba8c);write("wind_brass",gold);
        var blade=material(0xe4e6e0);rect(blade,0,0,5,32,0xc9ceca);rect(blade,5,0,7,32,0xd5d9d3);write("wind_blade",blade);
        var vent=material(0x525d61);rect(vent,2,2,30,30,0x30383d);
        for(int y=6;y<27;y+=5) {rect(vent,5,y,27,y+2,0x687276);rect(vent,5,y+2,27,y+4,0x3e494e);}write("wind_vent",vent);
        for(int tier=1;tier<=3;tier++) {
            var panel=material(0x414a4e);rect(panel,2,2,30,4,0x78817f);
            rect(panel,4,7,28,9,0xb4c3bf);rect(panel,4,10,19,12,0x8d9c99);
            for(int i=0;i<tier;i++)rect(panel,6+i*7,17,10+i*7,26,0xd0986d);
            write("wind_label_"+tier,panel);
        }
    }
}
