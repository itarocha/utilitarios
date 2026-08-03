package br.com.itarocha.utilitarios.qr;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ChunkCodec {

    public static final int MAX_QR_BYTES = 2100;

    public record Reassembled(byte[] compressed, long storedChecksum) {
    }

    private ChunkCodec() {
    }

    public static List<byte[]> split(byte[] compressed, int chunkSize, long checksum) throws IOException {
        int totalChunks = (compressed.length + chunkSize - 1) / chunkSize;
        List<byte[]> chunks = new ArrayList<>(totalChunks);
        for (int i = 0; i < totalChunks; i++) {
            int start = i * chunkSize;
            int len = Math.min(chunkSize, compressed.length - start);
            byte[] data = Arrays.copyOfRange(compressed, start, start + len);
            chunks.add(buildChunk(i, totalChunks, i == 0 ? checksum : null, data));
        }
        return chunks;
    }

    public static byte[] buildChunk(int index, int total, Long checksum, byte[] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            dos.writeShort(index);
            dos.writeShort(total);
            if (checksum != null) {
                dos.writeInt(checksum.intValue());
            }
            dos.write(data);
        }
        return baos.toByteArray();
    }

    public static Reassembled reassemble(List<byte[]> payloads) throws IOException {
        Map<Integer, byte[]> chunksMap = new HashMap<>();
        int totalChunks = -1;
        long storedChecksum = -1;

        for (byte[] payload : payloads) {
            try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(payload))) {
                int idx = dis.readShort();
                int total = dis.readShort();
                if (totalChunks == -1) {
                    totalChunks = total;
                } else if (totalChunks != total) {
                    throw new IOException(Messages.CHUNK_TOTAL_MISMATCH.formatted(totalChunks, total));
                }

                if (idx == 0) {
                    storedChecksum = dis.readInt() & 0xFFFFFFFFL;
                }

                byte[] data = new byte[dis.available()];
                dis.readFully(data);

                if (chunksMap.containsKey(idx)) {
                    throw new IOException(Messages.CHUNK_DUPLICATED.formatted(idx));
                }
                chunksMap.put(idx, data);
            }
        }

        if (chunksMap.size() != totalChunks) {
            throw new IOException(Messages.CHUNK_MISSING.formatted(totalChunks, chunksMap.size()));
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < totalChunks; i++) {
            byte[] chunk = chunksMap.get(i);
            if (chunk == null) {
                throw new IOException(Messages.CHUNK_NOT_FOUND.formatted(i));
            }
            baos.write(chunk);
        }
        return new Reassembled(baos.toByteArray(), storedChecksum);
    }
}
