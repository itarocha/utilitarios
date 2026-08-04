package br.com.itarocha.utilitarios.qr;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.StreamSupport;

public final class Decoder {

    private Decoder() {
    }

    public static void decode(String inputDir, String outputFile) throws Exception {
        List<Path> qrFiles = listQrFiles(Paths.get(inputDir));
        if (qrFiles.isEmpty()) {
            System.err.println(Messages.NO_QR_FOUND.formatted(inputDir));
            return;
        }

        byte[] original = reassembleAndVerify(
                qrFiles.stream()
                        .sorted(Comparator.comparingInt(Decoder::extractChunkNumber))
                        .map(Unchecked.unchecked(QrCodeImage::read))
                        .toList());

        Files.write(Paths.get(outputFile), original);
        System.out.println(Messages.DECODE_DONE.formatted(outputFile));
    }

    private static byte[] reassembleAndVerify(List<byte[]> payloads) throws IOException {
        ChunkCodec.Reassembled reassembled = ChunkCodec.reassemble(payloads);
        byte[] original = Compression.decompress(reassembled.compressed());
        if (!Checksum.verify(original, reassembled.storedChecksum())) {
            throw new IOException(Messages.BAD_CHECKSUM);
        }
        return original;
    }

    private static List<Path> listQrFiles(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, Constants.QR_PREFIX + "*.png")) {
            return StreamSupport.stream(stream.spliterator(), false).toList();
        }
    }

    private static int extractChunkNumber(Path file) {
        String name = file.getFileName().toString();
        String num = name.substring(Constants.QR_PREFIX.length(), name.length() - ".png".length());
        return Integer.parseInt(num);
    }
}
