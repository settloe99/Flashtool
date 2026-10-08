package org.flashtool.binutils.boot;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.flashtool.parsers.sin.SinFile;
import org.flashtool.parsers.sin.SinFileException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Converts the verified Xperia E1 legacy Sony ELF layout
 * into an Android boot image (legacy header v0).
 *
 * Does not flash devices or alter its input file.
 */
public final class SonyBootImageConverter {

    private static final int PAGE_SIZE = 4096;
    private static final int HEADER_SIZE = 1660;
    private static final byte[] MAGIC =
            "ANDROID!".getBytes(StandardCharsets.US_ASCII);

    private SonyBootImageConverter() {}

    private record Segment(byte[] data, long address) {}

    private static long u32(ByteBuffer b, int offset) {
        return Integer.toUnsignedLong(b.getInt(offset));
    }

    private static void checkRange(byte[] data, long offset, long size)
            throws IOException {
        if (offset < 0 || size < 0 || offset > data.length ||
                size > data.length - offset) {
            throw new IOException("Invalid ELF segment or section bounds");
        }
    }

    private static Segment segment(byte[] elf, ByteBuffer b,
                                   long phoff, int index) throws IOException {
        long entry = phoff + (long) index * 32;
        checkRange(elf, entry, 32);

        int pos = (int) entry;

        if (u32(b, pos) != 1) {
            throw new IOException("Unsupported ELF program header type");
        }

        long offset = u32(b, pos + 4);
        long address = u32(b, pos + 12);
        long size = u32(b, pos + 16);

        if (size == 0) {
            throw new IOException("Empty ELF segment");
        }

        checkRange(elf, offset, size);

        return new Segment(
                Arrays.copyOfRange(elf, (int) offset,
                        (int) (offset + size)),
                address
        );
    }

    private static String commandLine(byte[] elf, ByteBuffer b)
            throws IOException {
        long shoff = u32(b, 32);
        int shentsize = Short.toUnsignedInt(b.getShort(46));
        int shnum = Short.toUnsignedInt(b.getShort(48));

        if (shnum != 1 || shentsize != 40) {
            throw new IOException("Unsupported Sony ELF section layout");
        }

        checkRange(elf, shoff, 40);

        long offset = u32(b, (int) shoff + 16);
        long size = u32(b, (int) shoff + 20);

        if (size <= 8 || size > 4096) {
            throw new IOException("Invalid Sony command-line section");
        }

        checkRange(elf, offset, size);

        // Legacy Sony ELF metadata has an 8-byte prefix.
        int start = (int) offset + 8;
        int end = (int) (offset + size);

        for (int i = start; i < end; i++) {
            if (elf[i] == 0 || elf[i] == '\n') {
                end = i;
                break;
            }
            if (elf[i] < 32 || elf[i] > 126) {
                throw new IOException("Invalid kernel command line");
            }
        }

        if (end <= start || end - start >= 512) {
            throw new IOException("Invalid kernel command-line length");
        }

        return new String(elf, start, end - start,
                StandardCharsets.US_ASCII);
    }

    private static void hashSize(MessageDigest digest, int size) {
        byte[] value = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(size)
                .array();

        digest.update(value);
    }

    private static void pad(OutputStream out, int size)
            throws IOException {
        int remaining = (PAGE_SIZE - size % PAGE_SIZE) % PAGE_SIZE;

        if (remaining > 0) {
            out.write(new byte[remaining]);
        }
    }

    public static void convert(Path input, Path output)
            throws IOException {

        if (Files.size(input) > 64L * 1024 * 1024) {
            throw new IOException("ELF exceeds supported size limit");
        }

        byte[] elf = Files.readAllBytes(input);

        if (elf.length < 52 ||
                elf[0] != 0x7f ||
                elf[1] != 'E' ||
                elf[2] != 'L' ||
                elf[3] != 'F' ||
                elf[4] != 1 ||
                elf[5] != 1) {
            throw new IOException("Expected 32-bit little-endian ELF");
        }

        ByteBuffer b = ByteBuffer.wrap(elf)
                .order(ByteOrder.LITTLE_ENDIAN);

        if (Short.toUnsignedInt(b.getShort(18)) != 40 ||
                Short.toUnsignedInt(b.getShort(42)) != 32 ||
                Short.toUnsignedInt(b.getShort(44)) != 3) {
            throw new IOException("Unsupported Sony ARM ELF layout");
        }

        long phoff = u32(b, 28);

        Segment kernel = segment(elf, b, phoff, 0);
        Segment ramdisk = segment(elf, b, phoff, 1);
        Segment dt = segment(elf, b, phoff, 2);

        String cmdline = commandLine(elf, b);

        // Restrict this initial implementation to the
        // Xperia E1 layout validated against a working image.
        if (kernel.address != 0x00008000L ||
                ramdisk.address != 0x02000000L ||
                dt.address != 0x01e00000L ||
                !cmdline.equals(
                  "androidboot.hardware=qcom user_debug=31 msm_rtb.filter=0x37")) {
            throw new IOException(
                    "Unsupported Sony ELF boot parameters");
        }

        if (output.toAbsolutePath().normalize()
                .equals(input.toAbsolutePath().normalize())) {
            throw new IOException("Input and output must differ");
        }

        byte[] header = new byte[HEADER_SIZE];
        ByteBuffer h = ByteBuffer.wrap(header)
                .order(ByteOrder.LITTLE_ENDIAN);

        h.put(MAGIC);
        h.putInt(kernel.data.length);
        h.putInt((int) kernel.address);
        h.putInt(ramdisk.data.length);
        h.putInt((int) ramdisk.address);
        h.putInt(0);                  // second stage size
        h.putInt(0x00f00000);         // default second address
        h.putInt((int) dt.address);   // tags address
        h.putInt(PAGE_SIZE);
        h.putInt(dt.data.length);
        h.putInt(0);                  // unused/header version

        // Board name remains empty. Command line begins at 64.
        byte[] cmdBytes =
                cmdline.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(cmdBytes, 0, header, 64, cmdBytes.length);

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");

            digest.update(kernel.data);
            hashSize(digest, kernel.data.length);

            digest.update(ramdisk.data);
            hashSize(digest, ramdisk.data.length);

            hashSize(digest, 0); // Empty second stage

            digest.update(dt.data);
            hashSize(digest, dt.data.length);

            byte[] id = digest.digest();

            // Legacy Android boot image SHA-1 identifier.
            System.arraycopy(id, 0, header, 576, id.length);

        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 unavailable", e);
        }

        try (OutputStream out = Files.newOutputStream(output)) {
            out.write(header);
            pad(out, header.length);

            out.write(kernel.data);
            pad(out, kernel.data.length);

            out.write(ramdisk.data);
            pad(out, ramdisk.data.length);

            out.write(dt.data);
            pad(out, dt.data.length);
        }

        System.out.println("Created: " + output);
        System.out.println("Kernel: " + kernel.data.length + " bytes");
        System.out.println("Ramdisk: " + ramdisk.data.length + " bytes");
        System.out.println("DT: " + dt.data.length + " bytes");
        System.out.println("Command line: " + cmdline);
    }


    /**
     * Extracts a Sony ELF kernel from a SIN file and converts it
     * into a legacy Android boot image.
     *
     * All intermediate files are kept in a private temporary directory.
     */
    public static void convertSin(Path input, Path output)
            throws IOException {

        if (input.toAbsolutePath().normalize()
                .equals(output.toAbsolutePath().normalize())) {
            throw new IOException("Input and output must differ");
        }

        Path tempDir = Files.createTempDirectory("flashtool-sin-");
        Path tempSin = tempDir.resolve("kernel.sin");
        Path tempElf = tempDir.resolve("kernel.elf");

        try {
            Files.copy(input, tempSin);

            SinFile sin = new SinFile(tempSin.toFile());

            if (!"elf".equalsIgnoreCase(sin.getDataType())) {
                throw new IOException(
                        "SIN does not contain a supported Sony ELF kernel");
            }

            sin.dumpImage();

            if (!Files.isRegularFile(tempElf)
                    || Files.size(tempElf) < 52) {
                throw new IOException(
                        "SIN extraction failed or produced an invalid ELF");
            }

            convert(tempElf, output);

        } catch (SinFileException e) {
            throw new IOException("Unable to parse Sony SIN file", e);
        } finally {
            Files.deleteIfExists(tempElf);
            Files.deleteIfExists(tempSin);
            Files.deleteIfExists(tempDir);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println(
                    "Usage: SonyBootImageConverter <kernel.elf> <boot.img>");
            System.exit(1);
        }

        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);

        if (input.getFileName().toString()
                .toLowerCase(java.util.Locale.ROOT).endsWith(".sin")) {
            convertSin(input, output);
        } else {
            convert(input, output);
        }
    }
}
