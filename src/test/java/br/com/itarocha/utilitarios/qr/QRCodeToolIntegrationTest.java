package br.com.itarocha.utilitarios.qr;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QRCodeToolIntegrationTest {

    private static final Path ARQUIVOS = Paths.get("src/test/resources/arquivos");
    private static final Random RANDOM = new Random(42L);

    private static final Pattern GENERATED = Pattern.compile(
            "qr_\\d+\\.png|arquivo_\\d+\\.txt|arquivo_png_\\d+\\.png|.*_traduzido\\..*");

    private static final Map<String, Integer> MIN_QR_BY_FOLDER = Map.of(
            "pasta_02", 5,
            "pasta_03", 4
    );

    @ParameterizedTest(name = "cenário completo: {0}")
    @MethodSource("pastas")
    void cenarioCompleto(Path pasta) throws Exception {
        cleanGenerated(pasta);

        Path original = findOriginal(pasta);
        byte[] originalBytes = Files.readAllBytes(original);
        assertTrue(originalBytes.length > 0, "Arquivo original vazio: " + original);

        int minQr = MIN_QR_BY_FOLDER.getOrDefault(pasta.getFileName().toString(), 1);

        // 2) Gera os QR Codes a partir do arquivo original
        Encoder.encode(original.toString(), pasta.toString());
        int n = countQrFiles(pasta);
        assertTrue(n >= minQr,
                "Esperado >= " + minQr + " QR Codes em " + pasta + ", gerado: " + n);

        // 3) Lê cada imagem e grava o conteúdo (Base64) em arquivo_KKK.txt
        List<byte[]> payloads = new ArrayList<>(n);
        for (int k = 1; k <= n; k++) {
            Path qrFile = pasta.resolve(Constants.qrFileName(k));
            byte[] payload = QrCodeImage.read(qrFile);
            payloads.add(payload);

            String content = Base64.getEncoder().encodeToString(payload);
            Path txtFile = pasta.resolve(txtFileName(k));
            Files.writeString(txtFile, content, StandardCharsets.UTF_8);

            assertTrue(Files.exists(txtFile), "Arquivo texto não gerado: " + txtFile);
            assertEquals(content, Files.readString(txtFile, StandardCharsets.UTF_8));
            assertArrayEquals(payload, Base64.getDecoder().decode(content),
                    "Conteúdo do %s não é o Base64 do %s".formatted(txtFileName(k), Constants.qrFileName(k)));
        }

        // 4) Regenera os PNGs a partir dos arquivos texto (make_png)
        Path makePngOut = pasta.resolve("make_png");
        ImageMaker.makePng(pasta.toString(), makePngOut.toString());
        for (int k = 1; k <= n; k++) {
            Path pngFile = makePngOut.resolve(Constants.qrFileName(k));
            assertTrue(Files.exists(pngFile), "PNG não gerado: " + pngFile);
            assertArrayEquals(payloads.get(k - 1), QrCodeImage.read(pngFile),
                    "make_png/%s deveria decodificar para o payload de %s"
                            .formatted(Constants.qrFileName(k), Constants.qrFileName(k)));
        }

        // 5) Reconstrói o arquivo a partir dos qr_*.png
        Path traduzido = traduzidoPath(original);
        Decoder.decode(pasta.toString(), traduzido.toString());
        assertTrue(Files.exists(traduzido), "Arquivo traduzido não gerado: " + traduzido);
        assertArrayEquals(originalBytes, Files.readAllBytes(traduzido),
                "Conteúdo traduzido difere do original em " + pasta);

        // 6) MD5 do traduzido deve ser igual ao do original
        assertEquals(md5(originalBytes), md5(Files.readAllBytes(traduzido)),
                "MD5 do arquivo traduzido deve ser igual ao do original em " + pasta);
    }

    @Test
    void roundTripSmallFile(@TempDir Path tempDir) throws Exception {
        byte[] original = randomBytes(100);

        Path input = tempDir.resolve("pequeno.bin");
        Files.write(input, original);

        Path outDir = tempDir.resolve("qrs_small");
        Path recovered = tempDir.resolve("pequeno_recuperado.bin");

        Encoder.encode(input.toString(), outDir.toString());
        assertEquals(1, countQrFiles(outDir));

        Decoder.decode(outDir.toString(), recovered.toString());
        assertArrayEquals(original, Files.readAllBytes(recovered));
    }

    @Test
    void roundTripMultipleChunks(@TempDir Path tempDir) throws Exception {
        byte[] original = randomBytes(6000);

        Path input = tempDir.resolve("grande.bin");
        Files.write(input, original);

        Path outDir = tempDir.resolve("qrs_multi");
        Path recovered = tempDir.resolve("grande_recuperado.bin");

        Encoder.encode(input.toString(), outDir.toString());
        int qrCount = countQrFiles(outDir);
        assertTrue(qrCount >= 2, "Esperado múltiplos QR Codes, gerado: " + qrCount);

        Decoder.decode(outDir.toString(), recovered.toString());
        assertArrayEquals(original, Files.readAllBytes(recovered));
    }

    @Test
    void roundTripBinaryDataWithHighBytes(@TempDir Path tempDir) throws Exception {
        byte[] original = randomBytes(3000);

        Path input = tempDir.resolve("binario_alto.bin");
        Files.write(input, original);

        Path outDir = tempDir.resolve("qrs_high");
        Path recovered = tempDir.resolve("binario_alto_recuperado.bin");

        Encoder.encode(input.toString(), outDir.toString());
        Decoder.decode(outDir.toString(), recovered.toString());
        assertArrayEquals(original, Files.readAllBytes(recovered));
    }

    private static byte[] randomBytes(int size) {
        byte[] data = new byte[size];
        RANDOM.nextBytes(data);
        return data;
    }

    private static Stream<Path> pastas() throws IOException {
        List<Path> pastas = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(ARQUIVOS, "pasta_*")) {
            for (Path pasta : stream) {
                pastas.add(pasta);
            }
        }
        pastas.sort(Comparator.comparing(p -> p.getFileName().toString()));
        assertFalse(pastas.isEmpty(), "Nenhuma pasta_* encontrada em " + ARQUIVOS);
        return pastas.stream();
    }

    private static void cleanGenerated(Path pasta) throws IOException {
        if (!Files.isDirectory(pasta)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pasta)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteTree(entry);
                } else if (GENERATED.matcher(entry.getFileName().toString()).matches()) {
                    Files.delete(entry);
                }
            }
        }
    }

    private static void deleteTree(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    deleteTree(entry);
                } else {
                    Files.delete(entry);
                }
            }
        }
        Files.delete(dir);
    }

    private static Path findOriginal(Path pasta) throws IOException {
        List<Path> remaining = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pasta)) {
            for (Path entry : stream) {
                remaining.add(entry);
            }
        }
        assertEquals(1, remaining.size(),
                "Esperado exatamente 1 arquivo original em " + pasta + ", encontrado: " + remaining);
        return remaining.get(0);
    }

    private static int countQrFiles(Path dir) throws IOException {
        int count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, Constants.QR_PREFIX + "*.png")) {
            for (Path ignored : stream) {
                count++;
            }
        }
        return count;
    }

    private static String txtFileName(int index) {
        return String.format("%s%0" + Constants.FILE_NAME_DIGITS + "d.txt", Constants.TXT_PREFIX, index);
    }

    private static Path traduzidoPath(Path original) {
        String name = original.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String stem = dot >= 0 ? name.substring(0, dot) : name;
        String ext = dot >= 0 ? name.substring(dot) : "";
        return original.resolveSibling(stem + "_traduzido" + ext);
    }

    private static String md5(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(data);
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
