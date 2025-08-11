package org.FrostyFlippper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class FileUtil {
    /**
     * Unzips a ZIP file to a specified directory.
     *
     * @param fileZip the path to the ZIP file
     * @param dstDir  the destination directory where the contents will be extracted
     * @throws IOException if an error occurs during unzipping
     */
    public static void unzipFile(Path fileZip, Path dstDir) throws IOException {
        try (var zipInputStream = new ZipInputStream(Files.newInputStream(fileZip))) {
            ZipEntry zipEntry = zipInputStream.getNextEntry();
            while (zipEntry != null) {
                Path newFile = resolveZipEntryPath(dstDir, zipEntry);
                if (zipEntry.isDirectory()) {
                    Files.createDirectories(newFile);
                    if (!Files.isDirectory(newFile))
                        throw new IOException("Failed to create directory " + newFile);
                } else {
                    Path parent = newFile.getParent();
                    Files.createDirectories(parent);
                    if (!Files.isDirectory(parent))
                        throw new IOException("Failed to create directory " + parent);

                    Files.copy(zipInputStream, newFile, StandardCopyOption.REPLACE_EXISTING);
                }
                zipEntry = zipInputStream.getNextEntry();
            }
            zipInputStream.closeEntry();
        }
    }

    /**
     * Resolves the path for a ZIP entry to ensure it does not escape the destination directory.
     *
     * @param destinationDir the destination directory
     * @param zipEntry       the ZIP entry to resolve
     * @return the resolved path for the ZIP entry
     * @throws IOException if the resolved path is outside the destination directory
     */
    public static Path resolveZipEntryPath(Path destinationDir, ZipEntry zipEntry) throws IOException {
        var destFile = Path.of(destinationDir.toString(), zipEntry.getName());

        if (!destFile.normalize().startsWith(destinationDir))
            throw new IOException("Bad zip entry: " + zipEntry.getName());

        return destFile;
    }
}
