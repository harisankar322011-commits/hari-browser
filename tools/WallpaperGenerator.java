import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Random;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Creates the bundled offline home-screen wallpaper pack during the Android build.
 * These are real selectable backgrounds, not filler files. The generator runs on
 * the build machine; no desktop graphics APIs are included in the Android APK.
 */
public final class WallpaperGenerator {
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1200;

    private static final Scene[] SCENES = new Scene[] {
        new Scene("aurora",  "Aurora",  new int[][]{{5,18,48},{10,78,111},{104,57,157}}, new int[]{61,242,201}, 0.18,0.23, 11L),
        new Scene("ocean",   "Ocean",   new int[][]{{3,22,49},{0,101,139},{35,202,190}}, new int[]{53,211,255}, 0.78,0.30, 23L),
        new Scene("sunset",  "Sunset",  new int[][]{{48,20,62},{191,63,77},{255,170,74}}, new int[]{255,214,128}, 0.72,0.38, 37L),
        new Scene("forest",  "Forest",  new int[][]{{7,30,31},{18,91,72},{151,164,81}}, new int[]{121,220,149}, 0.23,0.35, 41L),
        new Scene("midnight","Midnight",new int[][]{{6,11,31},{25,37,84},{70,56,143}}, new int[]{136,159,255}, 0.79,0.20, 53L),
        new Scene("violet",  "Violet",  new int[][]{{24,15,58},{96,44,147},{220,87,170}}, new int[]{255,145,218}, 0.33,0.28, 67L),
        new Scene("desert",  "Desert",  new int[][]{{61,35,35},{171,92,58},{244,190,112}}, new int[]{255,220,163}, 0.74,0.27, 79L),
        new Scene("coral",   "Coral",   new int[][]{{42,18,48},{156,47,97},{255,125,126}}, new int[]{255,183,166}, 0.26,0.40, 83L),
        new Scene("glacier", "Glacier", new int[][]{{8,35,63},{45,121,162},{175,222,236}}, new int[]{190,255,255}, 0.78,0.18, 97L),
        new Scene("nebula",  "Nebula",  new int[][]{{12,9,34},{77,43,120},{30,123,154}}, new int[]{244,103,198}, 0.40,0.30, 109L)
    };

    private static final class Scene {
        final String file;
        final String label;
        final int[][] stops;
        final int[] glow;
        final double glowX;
        final double glowY;
        final long seed;

        Scene(String file, String label, int[][] stops, int[] glow,
              double glowX, double glowY, long seed) {
            this.file=file; this.label=label; this.stops=stops; this.glow=glow;
            this.glowX=glowX; this.glowY=glowY; this.seed=seed;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Expected output directory");
        File output = new File(args[0]);
        if (!output.exists() && !output.mkdirs()) throw new IOException("Cannot create " + output);
        for (Scene scene : SCENES) {
            File destination = new File(output, scene.file + ".jpg");
            render(scene, destination);
            System.out.println("Generated offline wallpaper: " + destination.getName()
                    + " (" + destination.length() + " bytes)");
        }
    }

    private static void render(Scene scene, File destination) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(scene.seed);
        double[][] glowX = new double[2][WIDTH];
        double[][] glowY = new double[2][HEIGHT];
        for (int x=0; x<WIDTH; x++) {
            double nx=x/(double)(WIDTH-1);
            double dx=nx-scene.glowX;
            glowX[0][x]=dx*dx*1.75;
            double dx2=nx-(1.0-scene.glowX*0.55);
            glowX[1][x]=dx2*dx2*2.2;
        }
        for (int y=0; y<HEIGHT; y++) {
            double ny=y/(double)(HEIGHT-1);
            double dy=ny-scene.glowY;
            glowY[0][y]=dy*dy*2.15;
            double dy2=ny-(1.0-scene.glowY*0.65);
            glowY[1][y]=dy2*dy2*2.4;
        }

        for (int y=0; y<HEIGHT; y++) {
            double ny=y/(double)(HEIGHT-1);
            int segment=ny < 0.52 ? 0 : 1;
            double blend=segment==0 ? ny/0.52 : (ny-0.52)/0.48;
            double eased=blend*blend*(3.0-2.0*blend);
            for (int x=0; x<WIDTH; x++) {
                double nx=x/(double)(WIDTH-1);
                double radial1=Math.exp(-(glowX[0][x]+glowY[0][y]));
                double radial2=Math.exp(-(glowX[1][x]+glowY[1][y]));
                double wave=Math.sin(x*0.0068+y*0.0034+scene.seed)*12.0
                        +Math.sin(x*0.0027-y*0.0061+scene.seed*0.37)*8.0
                        +Math.sin((nx+ny)*18.0+scene.seed)*5.0;
                int grain=random.nextInt(17)-8;
                int r=channel(scene.stops[segment][0],scene.stops[segment+1][0],eased,
                        scene.glow[0],radial1,0.24,grain,wave);
                int g=channel(scene.stops[segment][1],scene.stops[segment+1][1],eased,
                        scene.glow[1],radial1,0.24,grain,wave);
                int b=channel(scene.stops[segment][2],scene.stops[segment+1][2],eased,
                        scene.glow[2],radial1,0.24,grain,wave);
                // A second, faint cool glow adds depth without introducing external imagery.
                r=clamp(r+(int)((scene.glow[0]-r)*radial2*0.07));
                g=clamp(g+(int)((scene.glow[1]-g)*radial2*0.07));
                b=clamp(b+(int)((scene.glow[2]-b)*radial2*0.07));
                image.setRGB(x,y,(r<<16)|(g<<8)|b);
            }
        }
        writeJpeg(image,destination,0.94f);
        image.flush();
    }

    private static int channel(int a,int b,double t,int glow,double radial,double amount,
                               int grain,double wave) {
        double base=a+(b-a)*t;
        return clamp((int)Math.round(base+(glow-base)*radial*amount+wave*0.34+grain));
    }

    private static int clamp(int value) { return Math.max(0,Math.min(255,value)); }

    private static void writeJpeg(BufferedImage image, File file, float quality) throws IOException {
        Iterator<ImageWriter> writers=ImageIO.getImageWritersByFormatName("jpeg");
        if(!writers.hasNext()) throw new IOException("No JPEG encoder available");
        ImageWriter writer=writers.next();
        try (ImageOutputStream stream=ImageIO.createImageOutputStream(file)) {
            writer.setOutput(stream);
            ImageWriteParam parameter=writer.getDefaultWriteParam();
            parameter.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameter.setCompressionQuality(quality);
            writer.write(null,new IIOImage(image,null,null),parameter);
        } finally {
            writer.dispose();
        }
    }
}
