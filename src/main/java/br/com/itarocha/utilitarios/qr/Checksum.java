package br.com.itarocha.utilitarios.qr;

import java.util.zip.CRC32;

public final class Checksum {

    private Checksum() {
    }

    public static long checksum(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return crc.getValue();
    }

    public static boolean verify(byte[] data, long expected) {
        return checksum(data) == expected;
    }
}
