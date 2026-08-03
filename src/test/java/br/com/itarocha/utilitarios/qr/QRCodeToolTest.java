package br.com.itarocha.utilitarios.qr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @Test
    void makePngFromBase64Text() throws Exception {
        byte[] payload1 = randomBytes(800);
        byte[] payload2 = randomBytes(400);
        Path textDir = tempDir.resolve("textos");
        Files.createDirectories(textDir);
        Files.writeString(textDir.resolve("arquivo_01.txt"),
                Base64.getEncoder().encodeToString(payload1), StandardCharsets.UTF_8);
        Files.writeString(textDir.resolve("arquivo_02.txt"),
                Base64.getEncoder().encodeToString(payload2), StandardCharsets.UTF_8);

        Path outDir = tempDir.resolve("pngs");
        QRCodeTool.makePng(textDir.toString(), outDir.toString());

        Path png1 = outDir.resolve("qr_001.png");
        Path png2 = outDir.resolve("qr_002.png");
        assertTrue(Files.exists(png1), "Esperado qr_001.png");
        assertTrue(Files.exists(png2), "Esperado qr_002.png");
        assertArrayEquals(payload1, QrCodeImage.read(png1));
        assertArrayEquals(payload2, QrCodeImage.read(png2));
    }

    @Test
    void makePngInvalidBase64Throws() throws Exception {
        Path textDir = tempDir.resolve("invalido");
        Files.createDirectories(textDir);
        Files.writeString(textDir.resolve("arquivo_01.txt"), "isto não é base64 válido!@@@");

        Path outDir = tempDir.resolve("pngs_invalidos");
        assertThrows(IOException.class,
                () -> QRCodeTool.makePng(textDir.toString(), outDir.toString()));
    }

    @Test
    void makePngOmittedOutputDirUsesInputDir() throws Exception {
        byte[] payload = randomBytes(300);
        Path textDir = tempDir.resolve("mesma_pasta");
        Files.createDirectories(textDir);
        Files.writeString(textDir.resolve("arquivo_01.txt"),
                Base64.getEncoder().encodeToString(payload), StandardCharsets.UTF_8);

        QRCodeTool.makePng(textDir.toString(), null);

        Path png = textDir.resolve("qr_001.png");
        assertTrue(Files.exists(png), "Esperado PNG na mesma pasta de entrada");
        assertArrayEquals(payload, QrCodeImage.read(png));
    }

    @Test
    void makePngNoTextFilesReturns() throws Exception {
        Path textDir = tempDir.resolve("sem_txt");
        Files.createDirectories(textDir);
        Files.writeString(textDir.resolve("notas.txt"), "qualquer coisa");

        Path outDir = tempDir.resolve("pngs_sem_txt");
        assertDoesNotThrow(() -> QRCodeTool.makePng(textDir.toString(), outDir.toString()));
        assertEquals(0, countQrFiles(outDir));
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
