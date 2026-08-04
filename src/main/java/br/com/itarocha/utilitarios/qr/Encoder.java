package br.com.itarocha.utilitarios.qr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.IntStream;

public final class Encoder {

    private Encoder() {
    }

    public static void encode(String inputFile, String outputDir) throws Exception {
        Path outPath = createOutputDir(outputDir);

        List<byte[]> chunks = readAndSplit(Paths.get(inputFile));

        IntStream.range(0, chunks.size())
                .mapToObj(i -> new ChunkFile(chunks.get(i), outPath.resolve(Constants.qrFileName(i + 1))))
                .forEach(Unchecked.unchecked(ChunkFile::write));

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

    private record ChunkFile(byte[] payload, Path target) {
        void write() throws Exception {
            QrCodeImage.write(payload, target);
        }
    }
}
