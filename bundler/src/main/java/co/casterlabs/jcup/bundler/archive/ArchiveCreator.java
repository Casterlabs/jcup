package co.casterlabs.jcup.bundler.archive;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import co.casterlabs.jcup.bundler.Main;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class ArchiveCreator {
    private static final FastLogger LOGGER = Main.LOGGER.createChild("ArchiveCreator");

    public static void create(Archives.Format format, File inputDir, File destFile) throws FileNotFoundException, IOException {
        switch (format) {
            case ZIP: {
                try (
                    OutputStream fileOut = new FileOutputStream(destFile);
                    ArchiveOutputStream out = new ZipArchiveOutputStream(fileOut)) {
                    compress(inputDir, inputDir, out);
                    out.finish();
                }
                return;
            }

            case TAR_GZ: {
                try (
                    OutputStream fileOut = new FileOutputStream(destFile);
                    OutputStream gzipOut = new GzipCompressorOutputStream(fileOut);
                    ArchiveOutputStream out = new TarArchiveOutputStream(gzipOut)) {
                    compress(inputDir, inputDir, out);
                    out.finish();
                }
                return;
            }

            default:
                throw new IOException("Unsupported compression format: " + format);
        }
    }

    private static void compress(File inputDir, File file, ArchiveOutputStream out) throws IOException, FileNotFoundException {
        if (file.isDirectory()) {
            for (File sub : file.listFiles()) {
                compress(inputDir, sub, out);
            }
            return;
        }

        String entryPath = file.getAbsolutePath().substring(inputDir.getAbsolutePath().length() + 1);
        LOGGER.trace("Compressing: %s", entryPath);

        ArchiveEntry entry = out.createArchiveEntry(file, entryPath);
        out.putArchiveEntry(entry);
        try (InputStream in = new FileInputStream(file)) {
            in.transferTo(out);
        }
        out.closeArchiveEntry();
    }

}
