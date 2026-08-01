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
import java.awt.Color;
import java.awt.Graphics2D;
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

    public static void writeFromBase64(String base64, Path file) throws WriterException, IOException {
        String cleaned = base64.replaceAll("\\s+", "");
        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Conteúdo Base64 vazio.");
        }
        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(cleaned);
        } catch (IllegalArgumentException e) {
            throw new IOException("Conteúdo não é Base64 válido.", e);
        }
        write(payload, file);
    }

    public static void write(byte[] payload, Path file) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

        // Converte os bytes para Base64: conteúdo vira texto ASCII legível por leitores móveis
        String content = Base64.getEncoder().encodeToString(payload);
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 0, 0, hints);

        // Escala fixa de 2px por módulo (escalas maiores reduzem a robustez da decodificação)
        int size = matrix.getWidth() * QR_PIXEL_SCALE;
        BitMatrix scaled = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

        // Margem branca para atingir o tamanho mínimo físico, sem aumentar o tamanho do módulo
        int canvas = Math.max(size, MIN_IMAGE_SIZE);
        BufferedImage qr = MatrixToImageWriter.toBufferedImage(scaled);
        BufferedImage image = new BufferedImage(canvas, canvas, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, canvas, canvas);
        graphics.drawImage(qr, (canvas - size) / 2, (canvas - size) / 2, null);
        graphics.dispose();

        ImageIO.write(image, "PNG", file.toFile());
    }

    public static byte[] read(Path file) throws IOException, NotFoundException {
        BufferedImage image = ImageIO.read(file.toFile());
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(DecodeHintType.TRY_HARDER, true);

        byte[] payload;
        try {
            payload = decode(image, hints);
        } catch (NotFoundException e) {
            hints.remove(DecodeHintType.TRY_HARDER);
            hints.put(DecodeHintType.PURE_BARCODE, true);
            payload = decode(image, hints);
        }
        return payload;
    }

    private static byte[] decode(BufferedImage image, Map<DecodeHintType, Object> hints)
            throws IOException, NotFoundException {
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Result result = new MultiFormatReader().decode(bitmap, hints);

        // O texto lido é Base64; converte de volta para os bytes do payload
        try {
            return Base64.getDecoder().decode(result.getText());
        } catch (IllegalArgumentException e) {
            throw new IOException("Conteúdo do QR Code não é Base64 válido.", e);
        }
    }
}
