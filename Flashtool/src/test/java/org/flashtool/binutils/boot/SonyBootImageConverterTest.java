package org.flashtool.binutils.boot;

import org.testng.annotations.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.testng.Assert.*;

public class SonyBootImageConverterTest {

    private static final String CMDLINE =
        "androidboot.hardware=qcom user_debug=31 msm_rtb.filter=0x37";

    // Independently calculated reference for the synthetic fixture.
    private static final String EXPECTED_SHA256 =
        "ac9f057b0d80fa305266c0f5d506aea04218be59d76f312f9feab4e17217ee22";

    private static void segment(
            ByteBuffer b, int index, int offset,
            int address, byte[] data) {

        int ph = 52 + index * 32;

        b.putInt(ph, 1);                  // PT_LOAD
        b.putInt(ph + 4, offset);
        b.putInt(ph + 12, address);
        b.putInt(ph + 16, data.length);
        b.putInt(ph + 20, data.length);

        System.arraycopy(
            data, 0, b.array(), offset, data.length);
    }

    private static byte[] syntheticElf() {

        byte[] elf = new byte[0x600];

        elf[0] = 0x7f;
        elf[1] = 'E';
        elf[2] = 'L';
        elf[3] = 'F';
        elf[4] = 1;  // ELF32
        elf[5] = 1;  // Little endian
        elf[6] = 1;  // ELF version

        ByteBuffer b = ByteBuffer.wrap(elf)
            .order(ByteOrder.LITTLE_ENDIAN);

        b.putShort(18, (short) 40);  // ARM
        b.putInt(28, 52);           // Program headers
        b.putInt(32, 0xc0);         // Section header
        b.putShort(42, (short) 32);
        b.putShort(44, (short) 3);
        b.putShort(46, (short) 40);
        b.putShort(48, (short) 1);

        segment(b, 0, 0x200, 0x00008000,
            "KERNEL-FIXTURE-1".getBytes(StandardCharsets.US_ASCII));

        segment(b, 1, 0x300, 0x02000000,
            "RAMDISK-TEST".getBytes(StandardCharsets.US_ASCII));

        segment(b, 2, 0x400, 0x01e00000,
            "DT-12345".getBytes(StandardCharsets.US_ASCII));

        byte[] command =
            CMDLINE.getBytes(StandardCharsets.US_ASCII);

        // Sony command-line section, including its 8-byte prefix.
        b.putInt(0xc0 + 16, 0x500);
        b.putInt(0xc0 + 20, command.length + 9);

        System.arraycopy(
            command, 0, elf, 0x508, command.length);

        return elf;
    }

    @Test
    public void convertsSonyElfToExpectedBootImage()
            throws Exception {

        Path input = Files.createTempFile(
            "sony-test-", ".elf");

        Path output = Files.createTempFile(
            "sony-test-", ".img");

        try {
            Files.write(input, syntheticElf());

            SonyBootImageConverter.convert(input, output);

            byte[] image = Files.readAllBytes(output);

            assertEquals(image.length, 16384);

            assertEquals(
                new String(image, 0, 8,
                    StandardCharsets.US_ASCII),
                "ANDROID!"
            );

            MessageDigest sha256 =
                MessageDigest.getInstance("SHA-256");

            String actualHash = HexFormat.of().formatHex(
                sha256.digest(image)
            );

            assertEquals(actualHash, EXPECTED_SHA256);

            // Also verify our image-preparation helper accepts it.
            try (PreparedBootImage prepared =
                    PreparedBootImage.from(output)) {

                assertEquals(prepared.path(), output);
            }

        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }
}
