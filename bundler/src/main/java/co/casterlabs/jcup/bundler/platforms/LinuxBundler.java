package co.casterlabs.jcup.bundler.platforms;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Map.Entry;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.commons.platform.OSDistribution;
import co.casterlabs.commons.platform.Platform;
import co.casterlabs.jcup.bundler.Adoptium;
import co.casterlabs.jcup.bundler.JCup;
import co.casterlabs.jcup.bundler.JCupAbortException;
import co.casterlabs.jcup.bundler.Main;
import co.casterlabs.jcup.bundler.Utils;
import co.casterlabs.jcup.bundler.archive.ArchiveCreator;
import co.casterlabs.jcup.bundler.archive.ArchiveExtractor;
import co.casterlabs.jcup.bundler.archive.Archives;
import co.casterlabs.jcup.bundler.archive.Archives.Format;
import co.casterlabs.jcup.bundler.config.Architecture;
import co.casterlabs.jcup.bundler.config.Config;
import co.casterlabs.jcup.bundler.config.Config.OSSpecificConfig;
import co.casterlabs.jcup.bundler.config.OperatingSystem;
import co.casterlabs.jcup.bundler.icons.AppIcon;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
class LinuxBundler implements Bundler {
    static final Bundler INSTANCE_GLIBC = new LinuxBundler(OperatingSystem.linux_glibc);
    static final Bundler INSTANCE_MUSL = new LinuxBundler(OperatingSystem.linux_musl);
    private static final FastLogger LOGGER = Bundler.LOGGER.createChild("Linux");

    private final OperatingSystem os;

    @Override
    public void bundle(@NonNull Config config, @Nullable AppIcon icon, @NonNull OSSpecificConfig ossc, @NonNull Architecture arch) throws JCupAbortException {
        File buildFolder = JCup.createBuildFolder(this.os, arch);
        Utils.deleteRecursively(buildFolder); // Empty it out.

        // Download the JRE and extract it to the runtime/ folder.
        {
            File runtimeFolder = new File(buildFolder, "runtime");
            File archive;
            try {
                archive = Adoptium.download(config.javaVersion, arch, this.os);
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Unsupported build target, ignoring.\n%s", e);
                return;
            } catch (IOException | InterruptedException e) {
                LOGGER.fatal("Unable to download JRE, aborting.\n%s", e);
                throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
            }

            try {
                ArchiveExtractor.extract(
                    Archives.probeFormat(archive),
                    archive,
                    runtimeFolder
                );
            } catch (IOException e) {
                LOGGER.fatal("Unable to extract JRE, aborting.\n%s", e);
                throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
            }

            // Clean it all up.
            try {
                if (buildFolder.list().length == 1) {
                    // It's nested. Let's fix that.
                    File nestedFolder = runtimeFolder.listFiles()[0];
                    LOGGER.debug("Reorganizing the VM files.");
                    for (File nestedFolderChild : nestedFolder.listFiles()) {
                        Files.move(nestedFolderChild.toPath(), new File(runtimeFolder, nestedFolderChild.getName()).toPath());
                    }
                    nestedFolder.delete();
                }
            } catch (IOException e) {
                LOGGER.fatal("Unable to move JRE files, aborting.\n%s", e);
                throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
            }

            Utils.deleteRecursively(new File(runtimeFolder, "man")); // Delete any manpages.
            Utils.deleteRecursively(new File(runtimeFolder, "docs")); // Delete any manpages.
        }

        // Includes.
        try {
            for (Entry<String, String> entry : config.mainInclude.entrySet()) {
                File toIncludeFile = new File(entry.getKey());
                File includedFile = new File(buildFolder, entry.getValue());
                Files.copy(toIncludeFile.toPath(), includedFile.toPath());
            }
            for (Entry<String, String> entry : ossc.extraInclude.entrySet()) {
                File toIncludeFile = new File(entry.getKey());
                File includedFile = new File(buildFolder, entry.getValue());
                Files.copy(toIncludeFile.toPath(), includedFile.toPath());
            }
        } catch (IOException e) {
            LOGGER.fatal("Unable to copy `include`'d files, aborting.\n%s", e);
            throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
        }

        // Create the VM args file.
        try {
            String vmArgs;
            if (ossc.extraVmArgs == null || ossc.extraVmArgs.isEmpty()) {
                vmArgs = config.vmArgs;
            } else {
                vmArgs = ossc.extraVmArgs + ' ' + config.vmArgs;
            }
            Files.writeString(new File(buildFolder, "vmargs.txt").toPath(), vmArgs);
        } catch (IOException e) {
            LOGGER.fatal("Unable to write vmargs.txt, aborting.\n%s", e);
            throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
        }

        // Add the launcher executable.
        try (InputStream in = Main.class.getResourceAsStream("/unix-launcher");
            OutputStream out = new FileOutputStream(new File(buildFolder, config.executableName))) {
            in.transferTo(out);
        } catch (IOException e) {
            LOGGER.fatal("Unable to copy native executable, aborting.\n%s", e);
            throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
        }

        if (icon != null) {
            try {
                Files.write(
                    new File(buildFolder, config.executableName + ".png").toPath(),
                    icon.toPng()
                );
            } catch (IOException e) {
                LOGGER.warn("Unable to write image icon, ignoring.\n%s", e);
            }
        }

        // Mark files as executable.
        final String[] NEED_TO_MARK_EXEC = {
                config.executableName,
                "runtime/bin/java"
        };

        if (Platform.osDistribution == OSDistribution.WINDOWS_NT) {
            LOGGER.warn(
                "Windows doesn't support marking files (%s) as executable, this will likely cause problems for your users. I hope you know what you are doing.",
                String.join(", ", NEED_TO_MARK_EXEC)
            );
        } else {
            for (String path : NEED_TO_MARK_EXEC) {
                File file = new File(buildFolder, path);
                if (!file.setExecutable(true)) {
                    LOGGER.fatal("Unable to mark %s as executable, aborting.", file);
                    throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
                }
            }
        }

        try {
            File archiveFile = new File(JCup.ARTIFACTS_FOLDER, String.format("%s-%s-%s.tar.gz", config.executableName, this.os, arch));
            ArchiveCreator.create(Format.TAR_GZ, buildFolder, archiveFile);
            LOGGER.info("Produced artifact: %s", archiveFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.fatal("Unable to create .tar.gz file, aborting.\n%s", e);
            throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
        }

        // TODO .AppImage
        LOGGER.info("Done!");
    }

}
