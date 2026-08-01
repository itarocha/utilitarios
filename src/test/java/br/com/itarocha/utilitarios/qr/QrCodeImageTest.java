package br.com.itarocha.utilitarios.qr;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QrCodeImageTest {

    @TempDir
    Path tempDir;

    private static final Random RANDOM = new Random(11L);

    @Test
    void writeProducesImageAtLeast500x500() throws Exception {
        Path file = tempDir.resolve("qr.png");

        QrCodeImage.write(randomBytes(500), file);

        BufferedImage image = ImageIO.read(file.toFile());
        assertTrue(image.getWidth() >= 500, "Largura insuficiente: " + image.getWidth());
        assertTrue(image.getHeight() >= 500, "Altura insuficiente: " + image.getHeight());
    }

    @Test
    void readReturnsSamePayload() throws Exception {
        byte[] payload = randomBytes(800);
        Path file = tempDir.resolve("qr.png");

        QrCodeImage.write(payload, file);
        byte[] recovered = QrCodeImage.read(file);

        assertArrayEquals(payload, recovered);
    }

    @Test
    void contentIsReadableBase64() throws Exception {
        Path file = tempDir.resolve("qr.png");
        QrCodeImage.write(randomBytes(500), file);

        BufferedImage image = ImageIO.read(file.toFile());
        BinaryBitmap bitmap = new BinaryBitmap(
                new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Map<DecodeHintType, Object> hints = new HashMap<>();
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        Result result = new MultiFormatReader().decode(bitmap, hints);

        Pattern base64Pattern = Pattern.compile("^[A-Za-z0-9+/]*={0,2}$");
        assertTrue(base64Pattern.matcher(result.getText()).matches(),
                "Conteúdo não é Base64: " + result.getText());
        assertTrue(Base64.getDecoder().decode(result.getText()).length > 0);
    }

    @Test
    void readCorruptFileThrows() throws Exception {
        Path file = tempDir.resolve("qr.png");
        QrCodeImage.write(randomBytes(500), file);

        BufferedImage image = ImageIO.read(file.toFile());
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = height / 4; y < 3 * height / 4; y++) {
            for (int x = width / 4; x < 3 * width / 4; x++) {
                image.setRGB(x, y, image.getRGB(x, y) ^ 0xFFFFFF);
            }
        }
        ImageIO.write(image, "PNG", file.toFile());

        assertThrows(Exception.class, () -> QrCodeImage.read(file));
    }

    @Test
    void writeFromBase64RoundTrip() throws Exception {
        byte[] payload = randomBytes(600);
        String base64 = Base64.getEncoder().encodeToString(payload);
        Path file = tempDir.resolve("from_base64.png");

        QrCodeImage.writeFromBase64(base64, file);

        assertArrayEquals(payload, QrCodeImage.read(file));
    }

    @Test
    void writeFromBase64AcceptsWrappedLines() throws Exception {
        byte[] payload = randomBytes(600);
        String base64 = Base64.getEncoder().encodeToString(payload);
        String wrapped = String.join("\n", base64.split("(?<=\\G.{40})"));
        Path file = tempDir.resolve("wrapped.png");

        QrCodeImage.writeFromBase64(wrapped + "\n", file);

        assertArrayEquals(payload, QrCodeImage.read(file));
    }

    @Test
    void writeFromBase64InvalidThrows() {
        Path file = tempDir.resolve("invalid.png");

        assertThrows(IOException.class, () -> QrCodeImage.writeFromBase64("!!!não é base64!!!", file));
    }

    @Test
    void writeFromBase64EmptyThrows() {
        Path file = tempDir.resolve("empty.png");

        assertThrows(IllegalArgumentException.class, () -> QrCodeImage.writeFromBase64("   \n\t ", file));
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        RANDOM.nextBytes(data);
        return data;
    }
}
