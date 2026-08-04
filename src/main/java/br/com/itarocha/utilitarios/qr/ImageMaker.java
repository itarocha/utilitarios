package br.com.itarocha.utilitarios.qr;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

public final class ImageMaker {

    private ImageMaker() {
    }

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

        IntStream.range(0, textFiles.size())
                .mapToObj(i -> new TextPngFile(textFiles.get(i), out.resolve(Constants.qrFileName(i + 1))))
                .forEach(Unchecked.unchecked(TextPngFile::write));

        System.out.println(Messages.PNGS_GENERATED.formatted(textFiles.size(), out));
    }

    private record TextPngFile(Path source, Path target) {
        void write() throws Exception {
            QrCodeImage.writeFromBase64(Files.readString(source, StandardCharsets.UTF_8), target);
        }
    }

    private static List<Path> listTextFiles(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, Constants.TXT_PREFIX + "*.txt")) {
            return StreamSupport.stream(stream.spliterator(), false)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }
}
