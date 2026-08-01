package br.com.itarocha.utilitarios.qr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class QRCodeTool {

    public static final String QR_PREFIX = "qr_";
    public static final String QR_SUFFIX = ".png";

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Uso:");
            System.err.println("  Codificar: java QRCodeTool -encode <arquivo_entrada> <diretorio_saida>");
            System.err.println("  Decodificar: java QRCodeTool -decode <diretorio_entrada> <arquivo_saida>");
            System.err.println("  Gerar PNG a partir de texto Base64: java QRCodeTool make_png <arquivo_texto> <arquivo_png_saida>");
            System.exit(1);
        }

        String mode = args[0];
        if ("-encode".equalsIgnoreCase(mode)) {
            encode(args[1], args[2]);
        } else if ("-decode".equalsIgnoreCase(mode)) {
            decode(args[1], args[2]);
        } else if ("make_png".equalsIgnoreCase(mode)) {
            makePng(args[1], args[2]);
        } else {
            System.err.println("Modo inválido. Use -encode, -decode ou make_png.");
            System.exit(1);
        }
    }

    // ------------------------------------------------------------
    //  CODIFICAÇÃO
    // ------------------------------------------------------------
    public static void encode(String inputFile, String outputDir) throws Exception {
        byte[] originalData = Files.readAllBytes(Paths.get(inputFile));

        if (originalData.length == 0) {
            throw new IllegalArgumentException("Arquivo de entrada vazio: " + inputFile);
        }

        long checksum = Checksum.checksum(originalData);
        byte[] compressed = Compression.compress(originalData);
        List<byte[]> chunks = ChunkCodec.split(compressed, ChunkCodec.MAX_QR_BYTES, checksum);

        Path outPath = Paths.get(outputDir);
        if (!Files.exists(outPath)) {
            Files.createDirectories(outPath);
        }

        for (int i = 0; i < chunks.size(); i++) {
            String fileName = String.format("%s%03d%s", QR_PREFIX, i + 1, QR_SUFFIX);
            QrCodeImage.write(chunks.get(i), outPath.resolve(fileName));
        }

        System.out.println("Codificação concluída. " + chunks.size() + " QR Codes gerados em: " + outputDir);
    }

    // ------------------------------------------------------------
    //  DECODIFICAÇÃO
    // ------------------------------------------------------------
    public static void decode(String inputDir, String outputFile) throws Exception {
        Path dir = Paths.get(inputDir);
        List<Path> qrFiles = listQrFiles(dir);

        if (qrFiles.isEmpty()) {
            System.err.println("Nenhum arquivo QR Code encontrado em: " + inputDir);
            return;
        }

        qrFiles.sort(Comparator.comparingInt(QRCodeTool::extractChunkNumber));

        List<byte[]> payloads = new ArrayList<>(qrFiles.size());
        for (Path qrFile : qrFiles) {
            payloads.add(QrCodeImage.read(qrFile));
        }

        ChunkCodec.Reassembled reassembled = ChunkCodec.reassemble(payloads);
        byte[] original = Compression.decompress(reassembled.compressed());

        if (!Checksum.verify(original, reassembled.storedChecksum())) {
            throw new IOException("Checksum inválido! Dados corrompidos.");
        }

        Files.write(Paths.get(outputFile), original);
        System.out.println("Decodificação concluída. Arquivo recuperado: " + outputFile);
    }

    // ------------------------------------------------------------
    //  GERAÇÃO DE PNG A PARTIR DE TEXTO BASE64
    // ------------------------------------------------------------
    public static void makePng(String inputTextFile, String outputPngFile) throws Exception {
        String base64 = Files.readString(Paths.get(inputTextFile), StandardCharsets.UTF_8);
        QrCodeImage.writeFromBase64(base64, Paths.get(outputPngFile));
        System.out.println("PNG gerado: " + outputPngFile);
    }

    private static List<Path> listQrFiles(Path dir) throws IOException {
        List<Path> qrFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, QR_PREFIX + "*" + QR_SUFFIX)) {
            for (Path entry : stream) {
                qrFiles.add(entry);
            }
        }
        return qrFiles;
    }

    private static int extractChunkNumber(Path file) {
        String name = file.getFileName().toString();
        String num = name.substring(QR_PREFIX.length(), name.length() - QR_SUFFIX.length());
        return Integer.parseInt(num);
    }
}
