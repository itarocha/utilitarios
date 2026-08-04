package br.com.itarocha.utilitarios.qr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DecoderTest {

    @TempDir
    Path tempDir;

    private static final Random RANDOM = new Random(42L);

    @Test
    void decodeMissingChunkThrows() throws Exception {
        Path input = tempDir.resolve("multi.bin");
        Files.write(input, randomBytes(6000));

        Path outDir = tempDir.resolve("qrs_missing");
        Encoder.encode(input.toString(), outDir.toString());

        deleteOneQrFile(outDir);

        Path recovered = tempDir.resolve("multi_recuperado.bin");
        assertThrows(IOException.class,
                () -> Decoder.decode(outDir.toString(), recovered.toString()));
    }

    @Test
    void decodeCorruptedQrFails() throws Exception {
        Path input = tempDir.resolve("corromper.bin");
        Files.write(input, randomBytes(6000));

        Path outDir = tempDir.resolve("qrs_corrupt");
        Encoder.encode(input.toString(), outDir.toString());

        corruptFirstQrFile(outDir);

        Path recovered = tempDir.resolve("corromper_recuperado.bin");
        assertThrows(Exception.class,
                () -> Decoder.decode(outDir.toString(), recovered.toString()));
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        RANDOM.nextBytes(data);
        return data;
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
