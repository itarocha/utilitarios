package br.com.itarocha.utilitarios.qr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

class ImageMakerTest {

    @TempDir
    Path tempDir;

    private static final Random RANDOM = new Random(42L);

    @Test
    void makePngFromBase64Text() throws Exception {
        byte[] payload1 = randomBytes(800);
        byte[] payload2 = randomBytes(400);
        Path textDir = tempDir.resolve("textos");
        Files.createDirectories(textDir);
        Files.writeString(textDir.resolve("arquivo_001.txt"),
                Base64.getEncoder().encodeToString(payload1), StandardCharsets.UTF_8);
        Files.writeString(textDir.resolve("arquivo_002.txt"),
                Base64.getEncoder().encodeToString(payload2), StandardCharsets.UTF_8);

        Path outDir = tempDir.resolve("pngs");
        ImageMaker.makePng(textDir.toString(), outDir.toString());

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
        Files.writeString(textDir.resolve("arquivo_001.txt"), "isto não é base64 válido!@@@");

        Path outDir = tempDir.resolve("pngs_invalidos");
        assertThrows(IOException.class,
                () -> ImageMaker.makePng(textDir.toString(), outDir.toString()));
    }

    @Test
    void makePngOmittedOutputDirUsesInputDir() throws Exception {
        byte[] payload = randomBytes(300);
        Path textDir = tempDir.resolve("mesma_pasta");
        Files.createDirectories(textDir);
        Files.writeString(textDir.resolve("arquivo_001.txt"),
                Base64.getEncoder().encodeToString(payload), StandardCharsets.UTF_8);

        ImageMaker.makePng(textDir.toString(), null);

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
        assertDoesNotThrow(() -> ImageMaker.makePng(textDir.toString(), outDir.toString()));
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
}
