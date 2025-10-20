package dev.thomashanson.wizards.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;

/**
 * Modern utility class for file and directory operations using java.nio.
 */
public final class FileUtil {

    private FileUtil() {
        // Private constructor to prevent instantiation
    }

    /**
     * Recursively copies a file or directory.
     *
     * @param source The source path to copy from.
     * @param target The destination path to copy to.
     * @throws IOException If an I/O error occurs.
     */
    public static void copy(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Recursively deletes a file or directory.
     *
     * @param path The path to the file or directory to delete.
     * @throws IOException If an I/O error occurs.
     */
    public static void delete(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }

        // To delete a directory, we must first delete its contents.
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    // It's often useful to wrap this in a RuntimeException if you
                    // don't want to handle the checked exception in the lambda.
                    throw new RuntimeException("Failed to delete path: " + p, e);
                }
            });
    }

    /**
     * Convenience overload for {@link #copy(Path, Path)} that accepts {@link File} objects.
     */
    public static void copy(File source, File target) throws IOException {
        copy(source.toPath(), target.toPath());
    }

    /**
     * Convenience overload for {@link #delete(Path)} that accepts a {@link File} object.
     */
    public static void delete(File file) throws IOException {
        delete(file.toPath());
    }
}