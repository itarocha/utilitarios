package br.com.itarocha.utilitarios.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public final class QrCodeImage {

    public static final int QR_PIXEL_SCALE = 2;
    public static final int MIN_IMAGE_SIZE = 500;

    private QrCodeImage() {
    }

    public static void write(byte[] payload, Path file) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        // Converte os bytes para Base64: conteúdo vira texto ASCII legível por leitores móveis
        String content = Base64.getEncoder().encodeToString(payload);
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 0, 0, hints);
        int scale = Math.max(QR_PIXEL_SCALE,
                (int) Math.ceil((double) MIN_IMAGE_SIZE / matrix.getWidth()));
        int size = matrix.getWidth() * scale;
        BitMatrix scaled = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

        MatrixToImageWriter.writeToPath(scaled, "PNG", file);
    }

    public static byte[] read(Path file) throws IOException, NotFoundException {
        BufferedImage image = ImageIO.read(file.toFile());
        BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

        MultiFormatReader reader = new MultiFormatReader();
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        Result result = reader.decode(bitmap, hints);

        // O texto lido é Base64; converte de volta para os bytes do payload
        try {
            return Base64.getDecoder().decode(result.getText());
        } catch (IllegalArgumentException e) {
            throw new IOException("Conteúdo do QR Code não é Base64 válido: " + file, e);
        }
    }
}
