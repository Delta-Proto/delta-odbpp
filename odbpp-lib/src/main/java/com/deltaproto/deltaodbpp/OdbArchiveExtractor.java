package com.deltaproto.deltaodbpp;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;

/**
 * Extracts ODB++ archives from various formats (zip, tgz, tar.gz).
 */
public class OdbArchiveExtractor {

    /**
     * Extracts an ODB++ archive to a target directory.
     *
     * @param archivePath Path to the archive (zip, tgz, or tar.gz)
     * @param targetDir   Directory to extract to (will be created if needed)
     * @return Path to the extracted ODB++ root directory
     * @throws IOException If extraction fails
     */
    public Path extract(Path archivePath, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        String fileName = archivePath.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".zip")) {
            return extractZip(archivePath, targetDir);
        } else if (fileName.endsWith(".tgz") || fileName.endsWith(".tar.gz")) {
            return extractTarGz(archivePath, targetDir);
        } else if (fileName.endsWith(".tar")) {
            return extractTar(archivePath, targetDir);
        } else {
            throw new IOException("Unsupported archive format: " + fileName);
        }
    }

    private Path extractZip(Path archivePath, Path targetDir) throws IOException {
        String rootDir = null;

        // Use ZipFile (random-access via central directory) rather than
        // ZipArchiveInputStream — the latter can't read entries that use ZIP
        // data descriptors (size/CRC trailer), which Java's own ZipOutputStream
        // and many CAD tools emit. Some real archives hit exactly this case.
        try (ZipFile zipFile = ZipFile.builder().setSeekableByteChannel(
                java.nio.channels.FileChannel.open(archivePath, java.nio.file.StandardOpenOption.READ)).get()) {

            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String name = entry.getName();

                // Track root directory
                if (rootDir == null && name.contains("/")) {
                    rootDir = name.substring(0, name.indexOf('/'));
                }

                Path targetPath = targetDir.resolve(name);

                // Security check for path traversal
                if (!targetPath.normalize().startsWith(targetDir.normalize())) {
                    throw new IOException("Bad zip entry: " + name);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }

        return findOdbRoot(targetDir, rootDir);
    }

    private Path extractTarGz(Path archivePath, Path targetDir) throws IOException {
        String rootDir;

        try (InputStream fis = Files.newInputStream(archivePath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             GzipCompressorInputStream gis = new GzipCompressorInputStream(bis);
             TarArchiveInputStream tis = new TarArchiveInputStream(gis)) {

            rootDir = extractTarEntries(tis, targetDir);
        }

        return findOdbRoot(targetDir, rootDir);
    }

    private Path extractTar(Path archivePath, Path targetDir) throws IOException {
        String rootDir;

        try (InputStream fis = Files.newInputStream(archivePath);
             BufferedInputStream bis = new BufferedInputStream(fis);
             TarArchiveInputStream tis = new TarArchiveInputStream(bis)) {

            rootDir = extractTarEntries(tis, targetDir);
        }

        return findOdbRoot(targetDir, rootDir);
    }

    private String extractTarEntries(TarArchiveInputStream tis, Path targetDir) throws IOException {
        String rootDir = null;

        ArchiveEntry entry;
        while ((entry = tis.getNextEntry()) != null) {
            String name = entry.getName();

            // Track root directory
            if (rootDir == null && name.contains("/")) {
                rootDir = name.substring(0, name.indexOf('/'));
            }

            Path targetPath = targetDir.resolve(name);

            // Security check for path traversal
            if (!targetPath.normalize().startsWith(targetDir.normalize())) {
                throw new IOException("Bad tar entry: " + name);
            }

            if (entry.isDirectory()) {
                Files.createDirectories(targetPath);
            } else {
                Files.createDirectories(targetPath.getParent());
                Files.copy(tis, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        return rootDir;
    }

    /**
     * Find the ODB++ root directory (containing the matrix directory).
     */
    private Path findOdbRoot(Path extractDir, String rootDir) throws IOException {
        // First check if the root dir itself is an ODB root
        if (rootDir != null) {
            Path rootPath = extractDir.resolve(rootDir);
            if (isOdbRoot(rootPath)) {
                return rootPath;
            }
        }

        // Search for matrix directory to find ODB root
        try (var stream = Files.walk(extractDir, 5)) {
            return stream
                    .filter(p -> Files.isDirectory(p) && p.getFileName().toString().equals("matrix"))
                    .map(Path::getParent)
                    .findFirst()
                    .orElse(extractDir);
        }
    }

    private boolean isOdbRoot(Path path) {
        return Files.isDirectory(path.resolve("matrix")) ||
               Files.isDirectory(path.resolve("steps")) ||
               Files.exists(path.resolve("misc/info"));
    }

    /**
     * Extracts an archive to a temporary directory.
     *
     * @param archivePath Path to the archive
     * @return Path to the extracted ODB++ root directory (in a temp folder)
     * @throws IOException If extraction fails
     */
    public Path extractToTemp(Path archivePath) throws IOException {
        String baseName = archivePath.getFileName().toString()
                .replaceAll("\\.(zip|tgz|tar\\.gz|tar)$", "");
        Path tempDir = Files.createTempDirectory("odbpp_" + baseName);
        return extract(archivePath, tempDir);
    }
}
