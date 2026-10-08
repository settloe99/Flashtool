package org.flashtool.binutils.boot;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

public final class PreparedBootImage implements AutoCloseable {

    private static final byte[] MAGIC =
            "ANDROID!".getBytes(StandardCharsets.US_ASCII);

    private final Path path;
    private final boolean temporary;

    private PreparedBootImage(Path path, boolean temporary) {
        this.path = path;
        this.temporary = temporary;
    }

    public Path path() {
        return path;
    }

    public static PreparedBootImage from(Path input)
            throws IOException {

        if (!Files.isRegularFile(input)) {
            throw new IOException("Boot image not found: " + input);
        }

        String name = input.getFileName().toString()
                .toLowerCase(Locale.ROOT);

        // Existing Android boot images need no conversion.
        if (name.endsWith(".img")) {
            validate(input);
            return new PreparedBootImage(input, false);
        }

        if (!name.endsWith(".sin") && !name.endsWith(".elf")) {
            throw new IOException("Unsupported boot image format");
        }

        Path output = Files.createTempFile(
                "flashtool-fastboot-", ".img");

        try {
            if (name.endsWith(".sin")) {
                SonyBootImageConverter.convertSin(input, output);
            } else {
                SonyBootImageConverter.convert(input, output);
            }

            validate(output);

            return new PreparedBootImage(output, true);

        } catch (IOException | RuntimeException e) {
            try {
                Files.deleteIfExists(output);
            } catch (IOException cleanupError) {
                e.addSuppressed(cleanupError);
            }
            throw e;
        }
    }

    private static long padded(long size, long pageSize) {
        return ((size + pageSize - 1) / pageSize) * pageSize;
    }

    private static long uint32(ByteBuffer b, int offset) {
        return Integer.toUnsignedLong(b.getInt(offset));
    }

    private static void validate(Path image) throws IOException {
        long fileSize = Files.size(image);

        if (fileSize < 1580) {
            throw new IOException("Android boot image header is incomplete");
        }

        byte[] header;
        try (InputStream in = Files.newInputStream(image)) {
            header = in.readNBytes((int) Math.min(fileSize, 1660L));
        }

        if (!Arrays.equals(
                Arrays.copyOfRange(header, 0, 8), MAGIC)) {
            throw new IOException("Missing ANDROID! boot image signature");
        }

        ByteBuffer h = ByteBuffer.wrap(header)
                .order(ByteOrder.LITTLE_ENDIAN);

        long marker = uint32(h, 40);
        long requiredSize;

        if (marker == 3 || marker == 4) {
            // Modern GKI boot image, header v3 or v4.
            int version = (int) marker;
            int expectedHeaderSize = version == 3 ? 1580 : 1584;

            if (header.length < expectedHeaderSize ||
                    uint32(h, 20) != expectedHeaderSize) {
                throw new IOException("Invalid modern boot header size");
            }

            long kernelSize = uint32(h, 8);
            long ramdiskSize = uint32(h, 12);
            long signatureSize =
                    version == 4 ? uint32(h, 1580) : 0;

            if (kernelSize == 0) {
                throw new IOException("Boot image has no kernel");
            }

            requiredSize = 4096
                    + padded(kernelSize, 4096)
                    + padded(ramdiskSize, 4096)
                    + padded(signatureSize, 4096);

        } else {
            // Legacy Android boot image, header v0, v1 or v2.
            if (header.length < 1632) {
                throw new IOException("Legacy boot header is incomplete");
            }

            int version = marker <= 2 ? (int) marker : 0;

            // Older Sony/Android headers use this field for
            // the legacy device-tree size instead of a version.
            long legacyDtSize = marker > 4 ? marker : 0;

            long kernelSize = uint32(h, 8);
            long ramdiskSize = uint32(h, 16);
            long secondSize = uint32(h, 24);
            long pageSize = uint32(h, 36);

            if (kernelSize == 0) {
                throw new IOException("Boot image has no kernel");
            }

            if (pageSize < 2048 || pageSize > 65536 ||
                    (pageSize & (pageSize - 1)) != 0) {
                throw new IOException("Invalid boot image page size");
            }

            requiredSize = pageSize
                    + padded(kernelSize, pageSize)
                    + padded(ramdiskSize, pageSize)
                    + padded(secondSize, pageSize);

            if (version == 0) {
                requiredSize += padded(legacyDtSize, pageSize);

            } else {
                int expectedHeaderSize =
                        version == 1 ? 1648 : 1660;

                if (header.length < expectedHeaderSize ||
                        uint32(h, 1644) != expectedHeaderSize) {
                    throw new IOException(
                            "Invalid legacy boot header size");
                }

                long recoverySize = uint32(h, 1632);
                long recoveryOffset = h.getLong(1636);

                if (recoverySize > 0) {
                    if (recoveryOffset < requiredSize ||
                            recoveryOffset > fileSize ||
                            recoveryOffset % pageSize != 0) {
                        throw new IOException(
                                "Invalid recovery DTBO offset");
                    }

                    long recoveryEnd = Math.addExact(
                            recoveryOffset,
                            padded(recoverySize, pageSize));

                    requiredSize = Math.max(
                            requiredSize, recoveryEnd);
                }

                if (version == 2) {
                    long dtbSize = uint32(h, 1648);
                    requiredSize += padded(dtbSize, pageSize);
                }
            }
        }

        if (fileSize < requiredSize) {
            throw new IOException(
                    "Boot image is truncated: expected at least "
                    + requiredSize + " bytes, found " + fileSize);
        }
    }

    @Override
    public void close() throws IOException {
        if (temporary) {
            Files.deleteIfExists(path);
        }
    }
}
