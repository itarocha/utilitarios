package br.com.itarocha.utilitarios.qr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EncoderTest {

    @TempDir
    Path tempDir;

    private static final Random RANDOM = new Random(42L);

    @Test
    void encodeSmallFileGeneratesOneQr() throws Exception {
        Path input = tempDir.resolve("pequeno.bin");
        Files.write(input, randomBytes(100));

        Path outDir = tempDir.resolve("qrs_small");
        Encoder.encode(input.toString(), outDir.toString());

        assertEquals(1, countQrFiles(outDir));
        assertTrue(decodeable(outDir, 1));
    }

    @Test
    void encodeLargeFileGeneratesMultipleQrs() throws Exception {
        Path input = tempDir.resolve("grande.bin");
        Files.write(input, randomBytes(6000));

        Path outDir = tempDir.resolve("qrs_multi");
        Encoder.encode(input.toString(), outDir.toString());

        int qrCount = countQrFiles(outDir);
        assertTrue(qrCount >= 2, "Esperado múltiplos QR Codes, gerado: " + qrCount);
        assertTrue(decodeable(outDir, qrCount));
    }

    @Test
    void encodeBinaryWithHighBytesGeneratesQrs() throws Exception {
        Path input = tempDir.resolve("binario_alto.bin");
        Files.write(input, randomBytes(3000));

        Path outDir = tempDir.resolve("qrs_high");
        Encoder.encode(input.toString(), outDir.toString());

        int qrCount = countQrFiles(outDir);
        assertTrue(qrCount >= 1, "Esperado ao menos 1 QR Code, gerado: " + qrCount);
        assertTrue(decodeable(outDir, qrCount));
    }

    @Test
    void encodeEmptyFileThrows() throws Exception {
        Path input = tempDir.resolve("vazio.bin");
        Files.write(input, new byte[0]);

        Path outDir = tempDir.resolve("qrs_empty");

        assertThrows(IllegalArgumentException.class,
                () -> Encoder.encode(input.toString(), outDir.toString()));
    }

    private static boolean decodeable(Path dir, int count) throws Exception {
        for (int k = 1; k <= count; k++) {
            Path qrFile = dir.resolve(Constants.qrFileName(k));
            assertTrue(Files.exists(qrFile), "Esperado " + Constants.qrFileName(k));
            QrCodeImage.read(qrFile);
        }
        return true;
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
