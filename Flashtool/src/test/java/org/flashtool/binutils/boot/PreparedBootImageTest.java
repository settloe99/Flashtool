package org.flashtool.binutils.boot;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.testng.Assert.*;

public class PreparedBootImageTest {

    private static final int PAGE = 4096;

    @DataProvider(name = "headerVersions")
    public Object[][] headerVersions() {
        return new Object[][] {
            {0}, {1}, {2}, {3}, {4}
        };
    }

    private byte[] createBootImage(int version) {
        int pages = switch (version) {
            case 0, 1, 4 -> 4;
            case 2 -> 5;
            default -> 3;
        };

        byte[] image = new byte[pages * PAGE];

        ByteBuffer h = ByteBuffer.wrap(image)
                .order(ByteOrder.LITTLE_ENDIAN);

        h.put("ANDROID!".getBytes());

        if (version <= 2) {
            h.putInt(8, 64);
            h.putInt(12, 0x8000);
            h.putInt(16, 64);
            h.putInt(20, 0x02000000);
            h.putInt(36, PAGE);

            // Version 0 uses the legacy DT size field.
            h.putInt(40, version == 0 ? 64 : version);

            if (version >= 1) {
                h.putInt(1632, 64);
                h.putLong(1636, 3L * PAGE);
                h.putInt(1644, version == 1 ? 1648 : 1660);
            }

            if (version == 2) {
                h.putInt(1648, 64);
                h.putLong(1652, 0x01e00000L);
            }

        } else {
            h.putInt(8, 64);
            h.putInt(12, 64);
            h.putInt(20, version == 3 ? 1580 : 1584);
            h.putInt(40, version);

            if (version == 4) {
                h.putInt(1580, 64);
            }
        }

        return image;
    }

    private void expectRejected(Path input) throws Exception {
        try (PreparedBootImage ignored =
                     PreparedBootImage.from(input)) {

            fail("Invalid image was accepted: " + input);

        } catch (IOException expected) {
            // Rejection is the expected behaviour.
        }
    }

    @Test(dataProvider = "headerVersions")
    public void acceptsSupportedHeaders(int version)
            throws Exception {

        Path file = Files.createTempFile("boot-v" + version, ".img");

        try {
            Files.write(file, createBootImage(version));

            try (PreparedBootImage prepared =
                         PreparedBootImage.from(file)) {

                assertEquals(prepared.path(), file);
            }

            assertTrue(Files.exists(file));

        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test(dataProvider = "headerVersions")
    public void rejectsTruncatedImages(int version)
            throws Exception {

        Path file = Files.createTempFile("truncated-v" + version, ".img");

        try {
            byte[] image = createBootImage(version);

            Files.write(file, Arrays.copyOf(
                    image, image.length - 1));

            expectRejected(file);

        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsInvalidMagic() throws Exception {
        Path file = Files.createTempFile("invalid-magic", ".img");

        try {
            byte[] image = createBootImage(0);
            image[0] = 'X';

            Files.write(file, image);
            expectRejected(file);

        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsInvalidPageSize() throws Exception {
        Path file = Files.createTempFile("invalid-page", ".img");

        try {
            byte[] image = createBootImage(0);

            ByteBuffer.wrap(image)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(36, 1234);

            Files.write(file, image);
            expectRejected(file);

        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsUnsupportedFormats() throws Exception {
        Path file = Files.createTempFile("unsupported", ".txt");

        try {
            Files.writeString(file, "Not an Android image");
            expectRejected(file);

        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void rejectsInvalidSonyElf() throws Exception {
        Path file = Files.createTempFile("invalid-sony", ".elf");

        try {
            Files.writeString(file, "Not a Sony ELF");
            expectRejected(file);

        } finally {
            Files.deleteIfExists(file);
        }
    }
}
