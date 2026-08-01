package br.com.itarocha.utilitarios.qr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QRCodeToolTest {

    @TempDir
    Path tempDir;

    private static final Random RANDOM = new Random(42L);

    @Test
    void roundTripSmallFile() throws Exception {
        byte[] original = randomBytes(100);

        Path input = tempDir.resolve("pequeno.bin");
        Files.write(input, original);

        Path outDir = tempDir.resolve("qrs_small");
        Path recovered = tempDir.resolve("pequeno_recuperado.bin");

        QRCodeTool.encode(input.toString(), outDir.toString());
        assertEquals(1, countQrFiles(outDir));

        QRCodeTool.decode(outDir.toString(), recovered.toString());
        assertArrayEquals(original, Files.readAllBytes(recovered));
    }

    @Test
    void roundTripMultipleChunks() throws Exception {
        byte[] original = randomBytes(6000);

        Path input = tempDir.resolve("grande.bin");
        Files.write(input, original);

        Path outDir = tempDir.resolve("qrs_multi");
        Path recovered = tempDir.resolve("grande_recuperado.bin");

        QRCodeTool.encode(input.toString(), outDir.toString());
        int qrCount = countQrFiles(outDir);
        assertTrue(qrCount >= 2, "Esperado múltiplos QR Codes, gerado: " + qrCount);

        QRCodeTool.decode(outDir.toString(), recovered.toString());
        assertArrayEquals(original, Files.readAllBytes(recovered));
    }

    @Test
    void roundTripBinaryDataWithHighBytes() throws Exception {
        byte[] original = randomBytes(3000);

        Path input = tempDir.resolve("binario_alto.bin");
        Files.write(input, original);

        Path outDir = tempDir.resolve("qrs_high");
        Path recovered = tempDir.resolve("binario_alto_recuperado.bin");

        QRCodeTool.encode(input.toString(), outDir.toString());
        QRCodeTool.decode(outDir.toString(), recovered.toString());
        assertArrayEquals(original, Files.readAllBytes(recovered));
    }

    @Test
    void encodeEmptyFileThrows() throws Exception {
        Path input = tempDir.resolve("vazio.bin");
        Files.write(input, new byte[0]);

        Path outDir = tempDir.resolve("qrs_empty");

        assertThrows(IllegalArgumentException.class,
                () -> QRCodeTool.encode(input.toString(), outDir.toString()));
    }

    @Test
    void decodeMissingChunkThrows() throws Exception {
        Path input = tempDir.resolve("multi.bin");
        Files.write(input, randomBytes(6000));

        Path outDir = tempDir.resolve("qrs_missing");
        QRCodeTool.encode(input.toString(), outDir.toString());

        deleteOneQrFile(outDir);

        Path recovered = tempDir.resolve("multi_recuperado.bin");
        assertThrows(IOException.class,
                () -> QRCodeTool.decode(outDir.toString(), recovered.toString()));
    }

    @Test
    void decodeCorruptedQrFails() throws Exception {
        Path input = tempDir.resolve("corromper.bin");
        Files.write(input, randomBytes(6000));

        Path outDir = tempDir.resolve("qrs_corrupt");
        QRCodeTool.encode(input.toString(), outDir.toString());

        corruptFirstQrFile(outDir);

        Path recovered = tempDir.resolve("corromper_recuperado.bin");
        assertThrows(Exception.class,
                () -> QRCodeTool.decode(outDir.toString(), recovered.toString()));
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        RANDOM.nextBytes(data);
        return data;
    }

    private static int countQrFiles(Path dir) throws IOException {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "qr_*.png")) {
            for (Path entry : stream) {
                files.add(entry);
            }
        }
        return files.size();
    }

    private static void deleteOneQrFile(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "qr_*.png")) {
            for (Path entry : stream) {
                Files.delete(entry);
                return;
            }
        }
    }

    private static void corruptFirstQrFile(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "qr_*.png")) {
            for (Path entry : stream) {
                BufferedImage image = ImageIO.read(entry.toFile());
                int width = image.getWidth();
                int height = image.getHeight();
                for (int y = height / 4; y < 3 * height / 4; y++) {
                    for (int x = width / 4; x < 3 * width / 4; x++) {
                        image.setRGB(x, y, image.getRGB(x, y) ^ 0xFFFFFF);
                    }
                }
                ImageIO.write(image, "PNG", entry.toFile());
                return;
            }
        }
    }
}
