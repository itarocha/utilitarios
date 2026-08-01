package br.com.itarocha.utilitarios.qr;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChecksumTest {

    @Test
    void checksumMatchesStandardCrc32Vector() {
        // Vetor padrão CRC-32 (check value): "123456789" -> 0xCBF43926
        byte[] data = "123456789".getBytes();

        assertEquals(0xCBF43926L, Checksum.checksum(data));
    }

    @Test
    void verifyReturnsTrueForMatchingChecksum() {
        byte[] data = new byte[1024];
        new Random(7).nextBytes(data);

        long checksum = Checksum.checksum(data);

        assertTrue(Checksum.verify(data, checksum));
    }

    @Test
    void verifyReturnsFalseForDifferentChecksum() {
        byte[] data = new byte[1024];
        new Random(7).nextBytes(data);

        assertFalse(Checksum.verify(data, Checksum.checksum(data) + 1));
    }

    @Test
    void checksumOfEmptyData() {
        assertEquals(0L, Checksum.checksum(new byte[0]));
    }
}
