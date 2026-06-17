package com.deltaproto.deltaodbpp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DecompressorTest {

    private Decompressor decompressor;

    @BeforeEach
    void setUp() {
        decompressor = new Decompressor();
    }

    @Test
    void testDecompressorExists() {
        // Basic test to ensure the Decompressor class is properly instantiated
        assertNotNull(decompressor);
    }

    @Test
    void testDecompressInvalidFileThrowsException(@TempDir Path tempDir) throws IOException {
        // Create a file with invalid .Z content
        Path invalidFile = tempDir.resolve("invalid.Z");
        Files.writeString(invalidFile, "This is not a valid compressed file");

        Path targetFile = tempDir.resolve("output");

        // Should throw IOException when trying to decompress invalid data
        assertThrows(IOException.class, () -> decompressor.decompress(invalidFile, targetFile));
    }

    @Test
    void testDecompressNonExistentFileThrowsException(@TempDir Path tempDir) {
        Path nonExistent = tempDir.resolve("nonexistent.Z");
        Path targetFile = tempDir.resolve("output");

        assertThrows(IOException.class, () -> decompressor.decompress(nonExistent, targetFile));
    }
}
