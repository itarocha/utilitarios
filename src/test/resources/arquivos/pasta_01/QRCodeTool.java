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
import java.util.function.Consumer;
import java.util.stream.IntStream;

public class QRCodeTool {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            usage();
            System.exit(1);
        }

        String mode = args[0];
        if ("-encode".equalsIgnoreCase(mode)) {
            if (args.length < 3) {
                usage();
                System.exit(1);
            }
            encode(args[1], args[2]);
        } else if ("-decode".equalsIgnoreCase(mode)) {
            if (args.length < 3) {
                usage();
                System.exit(1);
            }
            decode(args[1], args[2]);
        } else if ("-make_png".equalsIgnoreCase(mode)) {
            makePng(args[1], args.length >= 3 ? args[2] : null);
        } else {
            System.err.println(Messages.INVALID_MODE);
            System.exit(1);
        }
    }

    private static void usage() {
        System.err.println(Messages.USAGE_HEADER);
        System.err.println(Messages.USAGE_ENCODE);
        System.err.println(Messages.USAGE_DECODE);
        System.err.println(Messages.USAGE_MAKE_PNG_TITLE);
        System.err.println(Messages.USAGE_MAKE_PNG_CMD);
        System.err.println(Messages.USAGE_MAKE_PNG_HINT);
    }

    // ------------------------------------------------------------
    //  CODIFICAÇÃO
    // ------------------------------------------------------------
    public static void encode(String inputFile, String outputDir) throws Exception {
        Path outPath = createOutputDir(outputDir);

        List<byte[]> chunks = readAndSplit(Paths.get(inputFile));

        IntStream.range(0, chunks.size())
                .mapToObj(i -> new ChunkFile(chunks.get(i), outPath.resolve(fileName(i + 1))))
                .forEach(unchecked(ChunkFile::write));

        System.out.println(Messages.ENCODE_DONE.formatted(chunks.size(), outputDir));
    }

    private static Path createOutputDir(String outputDir) throws IOException {
        Path outPath = Paths.get(outputDir);
        if (!Files.exists(outPath)) {
            Files.createDirectories(outPath);
        }
        return outPath;
    }

    private static List<byte[]> readAndSplit(Path input) throws IOException {
        byte[] data = Files.readAllBytes(input);
        if (data.length == 0) {
            throw new IllegalArgumentException(Messages.EMPTY_INPUT_FILE.formatted(input));
        }
        return ChunkCodec.split(Compression.compress(data), ChunkCodec.MAX_QR_BYTES, Checksum.checksum(data));
    }

    private static String fileName(int index) {
        return ("%s%0" + Constants.FILE_NAME_DIGITS + "d.png")
                .formatted(Constants.QR_PREFIX, index);
    }

    private record ChunkFile(byte[] payload, Path target) {
        void write() throws Exception {
            QrCodeImage.write(payload, target);
        }
    }

    @FunctionalInterface
    private interface ThrowingConsumer<T> {
        void accept(T t) throws Exception;
    }

    private static <T> Consumer<T> unchecked(ThrowingConsumer<T> consumer) {
        return t -> {
            try {
                consumer.accept(t);
            } catch (Exception e) {
                sneakyThrow(e);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable throwable) throws E {
        throw (E) throwable;
    }

    // ------------------------------------------------------------
    //  DECODIFICAÇÃO
    // ------------------------------------------------------------
    public static void decode(String inputDir, String outputFile) throws Exception {
        Path dir = Paths.get(inputDir);
        List<Path> qrFiles = listQrFiles(dir);

        if (qrFiles.isEmpty()) {
            System.err.println(Messages.NO_QR_FOUND.formatted(inputDir));
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
            throw new IOException(Messages.BAD_CHECKSUM);
        }

        Files.write(Paths.get(outputFile), original);
        System.out.println(Messages.DECODE_DONE.formatted(outputFile));
    }

    // ------------------------------------------------------------
    //  GERAÇÃO DE PNG A PARTIR DE TEXTO BASE64
    // ------------------------------------------------------------
    public static void makePng(String inputDir, String outputDir) throws Exception {
        Path in = Paths.get(inputDir);
        if (!Files.isDirectory(in)) {
            throw new IllegalArgumentException(Messages.INPUT_DIR_NOT_FOUND.formatted(inputDir));
        }

        Path out = (outputDir == null || outputDir.isBlank()) ? in : Paths.get(outputDir);
        if (!Files.exists(out)) {
            Files.createDirectories(out);
        }

        List<Path> textFiles = listTextFiles(in);
        if (textFiles.isEmpty()) {
            System.err.println(Messages.NO_TEXT_FILES.formatted(Constants.TXT_PREFIX, inputDir));
            return;
        }

        for (int i = 0; i < textFiles.size(); i++) {
            String base64 = Files.readString(textFiles.get(i), StandardCharsets.UTF_8);
            String pngName = ("%s%0" + Constants.FILE_NAME_DIGITS + "d.png")
                    .formatted(Constants.QR_PREFIX, i + 1);
            QrCodeImage.writeFromBase64(base64, out.resolve(pngName));
        }

        System.out.println(Messages.PNGS_GENERATED.formatted(textFiles.size(), out));
    }

    private static List<Path> listTextFiles(Path dir) throws IOException {
        List<Path> textFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, Constants.TXT_PREFIX + "*.txt")) {
            for (Path entry : stream) {
                textFiles.add(entry);
            }
        }
        textFiles.sort(Comparator.comparing(path -> path.getFileName().toString()));
        return textFiles;
    }

    private static List<Path> listQrFiles(Path dir) throws IOException {
        List<Path> qrFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, Constants.QR_PREFIX + "*.png")) {
            for (Path entry : stream) {
                qrFiles.add(entry);
            }
        }
        return qrFiles;
    }

    private static int extractChunkNumber(Path file) {
        String name = file.getFileName().toString();
        String num = name.substring(Constants.QR_PREFIX.length(), name.length() - ".png".length());
        return Integer.parseInt(num);
    }
}
