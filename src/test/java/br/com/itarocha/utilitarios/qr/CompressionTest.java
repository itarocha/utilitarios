package br.com.itarocha.utilitarios.qr;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompressionTest {

    @Test
    void compressDecompressSmallData() throws Exception {
        byte[] original = "dados de teste".getBytes();

        byte[] compressed = Compression.compress(original);
        byte[] recovered = Compression.decompress(compressed);

        assertArrayEquals(original, recovered);
    }

    @Test
    void compressDecompressLargeData() throws Exception {
        byte[] original = new byte[100_000];
        new Random(1).nextBytes(original);

        byte[] compressed = Compression.compress(original);

        assertArrayEquals(original, Compression.decompress(compressed));
    }

    @Test
    void compressDecompressEmptyData() throws Exception {
        byte[] original = new byte[0];

        byte[] recovered = Compression.decompress(Compression.compress(original));

        assertArrayEquals(original, recovered);
    }

    @Test
    void decompressInvalidDataThrows() {
        byte[] invalid = new byte[]{1, 2, 3, 4, 5};

        assertThrows(IOException.class, () -> Compression.decompress(invalid));
    }
}
