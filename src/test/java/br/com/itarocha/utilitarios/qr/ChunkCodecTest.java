package br.com.itarocha.utilitarios.qr;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChunkCodecTest {

    @Test
    void splitProducesExpectedChunks() throws Exception {
        byte[] compressed = new byte[5000];
        new Random(3).nextBytes(compressed);

        List<byte[]> chunks = ChunkCodec.split(compressed, 2100, 42L);

        assertEquals(3, chunks.size());
        assertEquals(2100 + 8, chunks.get(0).length);
        assertEquals(2100 + 4, chunks.get(1).length);
        assertEquals(800 + 4, chunks.get(2).length);
    }

    @Test
    void splitReassembleRoundTrip() throws Exception {
        byte[] compressed = new byte[5000];
        new Random(3).nextBytes(compressed);
        long checksum = Checksum.checksum(compressed);

        List<byte[]> chunks = ChunkCodec.split(compressed, 2100, checksum);
        ChunkCodec.Reassembled result = ChunkCodec.reassemble(chunks);

        assertArrayEquals(compressed, result.compressed());
        assertEquals(checksum, result.storedChecksum());
    }

    @Test
    void reassembleSingleChunkCarriesChecksum() throws Exception {
        byte[] compressed = new byte[100];
        new Random(5).nextBytes(compressed);
        long checksum = Checksum.checksum(compressed);

        List<byte[]> chunks = ChunkCodec.split(compressed, 2100, checksum);
        ChunkCodec.Reassembled result = ChunkCodec.reassemble(chunks);

        assertEquals(checksum, result.storedChecksum());
    }

    @Test
    void reassembleMissingChunkThrows() throws Exception {
        byte[] compressed = new byte[5000];
        new Random(3).nextBytes(compressed);

        List<byte[]> chunks = ChunkCodec.split(compressed, 2100, 0L);
        List<byte[]> incomplete = new ArrayList<>(chunks);
        incomplete.remove(1);

        assertThrows(IOException.class, () -> ChunkCodec.reassemble(incomplete));
    }

    @Test
    void reassembleDuplicateChunkThrows() throws Exception {
        byte[] compressed = new byte[5000];
        new Random(3).nextBytes(compressed);

        List<byte[]> chunks = ChunkCodec.split(compressed, 2100, 0L);
        List<byte[]> duplicated = new ArrayList<>(chunks);
        duplicated.add(Arrays.copyOf(chunks.get(0), chunks.get(0).length));

        assertThrows(IOException.class, () -> ChunkCodec.reassemble(duplicated));
    }

    @Test
    void reassembleInconsistentTotalThrows() throws Exception {
        byte[] dataA = new byte[100];
        byte[] dataB = new byte[200];

        List<byte[]> payloads = new ArrayList<>();
        payloads.add(ChunkCodec.buildChunk(0, 2, null, dataA));
        payloads.add(ChunkCodec.buildChunk(1, 3, null, dataB));

        assertThrows(IOException.class, () -> ChunkCodec.reassemble(payloads));
    }
}
