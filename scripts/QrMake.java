import com.google.zxing.*;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

public class QrMake {
    public static void main(String[] a) throws Exception {
        String url = a[0], out = a[1];
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.MARGIN, 4);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        BitMatrix m = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0, hints);
        int n = m.getWidth();
        StringBuilder svg = new StringBuilder();
        svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(n).append(' ').append(n)
           .append("\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR code: ").append(url).append("\">")
           .append("<rect width=\"").append(n).append("\" height=\"").append(n).append("\" fill=\"#fff\"/><path fill=\"#1b0b3a\" d=\"");
        for (int y = 0; y < n; y++) for (int x = 0; x < n; x++) if (m.get(x, y)) svg.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
        svg.append("\"/></svg>");
        Files.writeString(Path.of(out + ".svg"), svg.toString());
        int scale = 12;
        BufferedImage img = new BufferedImage(n * scale, n * scale, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < n * scale; y++) for (int x = 0; x < n * scale; x++) img.setRGB(x, y, m.get(x / scale, y / scale) ? 0x1b0b3a : 0xffffff);
        ImageIO.write(img, "png", new File(out + ".png"));
        System.out.println("QR " + n + "x" + n + " for " + url);
    }
}
