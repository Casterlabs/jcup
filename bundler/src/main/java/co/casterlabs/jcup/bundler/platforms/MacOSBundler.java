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
import lombok.NonNull;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

class MacOSBundler implements Bundler {
    static final Bundler INSTANCE = new MacOSBundler();
    private static final FastLogger LOGGER = Bundler.LOGGER.createChild("macOS");

    @Override
    public void bundle(@NonNull Config config, @Nullable AppIcon icon, @NonNull OSSpecificConfig ossc, @NonNull Architecture arch) throws JCupAbortException {
        final File buildFolder = new File(JCup.createBuildFolder(OperatingSystem.macosx, arch), config.executableName + ".app");
        Utils.deleteRecursively(buildFolder); // Empty it out.

        // Download the JRE and extract it to the runtime/ folder.
        {
            File archive;
            try {
                archive = Adoptium.download(config.javaVersion, arch, OperatingSystem.macosx);
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
                    buildFolder
                );
            } catch (IOException e) {
                LOGGER.fatal("Unable to extract JRE, aborting.\n%s", e);
                throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
            }

            // Clean it all up.
            try {
                if (buildFolder.list().length == 1) {
                    // It's nested. Let's fix that.
                    File nestedFolder = buildFolder.listFiles()[0];
                    LOGGER.debug("Reorganizing the VM files.");
                    for (File nestedFolderChild : nestedFolder.listFiles()) {
                        Files.move(nestedFolderChild.toPath(), new File(buildFolder, nestedFolderChild.getName()).toPath());
                    }
                    nestedFolder.delete();
                }

                // We need to rearrange some files.
                new File(buildFolder, "Contents/MacOS").mkdirs();
                new File(buildFolder, "Contents/Resources").mkdirs();
                Files.move(new File(buildFolder, "Contents/Home").toPath(), new File(buildFolder, "Contents/Resources/runtime").toPath());
            } catch (IOException e) {
                LOGGER.fatal("Unable to move JRE files, aborting.\n%s", e);
                throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
            }

            Utils.deleteRecursively(new File(buildFolder, "Contents/Resources/man")); // Delete any manpages.
            Utils.deleteRecursively(new File(buildFolder, "Contents/_CodeSignature")); // Delete any code signatures.
            Utils.deleteRecursively(new File(buildFolder, "Contents/Info.plist")); // Delete any manifests.
        }

        // Includes.
        try {
            for (Entry<String, String> entry : config.mainInclude.entrySet()) {
                File toIncludeFile = new File(entry.getKey());
                File includedFile = new File(buildFolder, "Contents/Resources/" + entry.getValue());
                Files.copy(toIncludeFile.toPath(), includedFile.toPath());
            }
            for (Entry<String, String> entry : ossc.extraInclude.entrySet()) {
                File toIncludeFile = new File(entry.getKey());
                File includedFile = new File(buildFolder, "Contents/Resources/" + entry.getValue());
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
            Files.writeString(new File(buildFolder, "Contents/Resources/vmargs.txt").toPath(), vmArgs);
        } catch (IOException e) {
            LOGGER.fatal("Unable to write vmargs.txt, aborting.\n%s", e);
            throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
        }

        // Add the launcher executable.
        try (InputStream in = Main.class.getResourceAsStream("/macosx-launcher");
            OutputStream out = new FileOutputStream(new File(buildFolder, "Contents/MacOS/" + config.executableName))) {
            in.transferTo(out);
        } catch (IOException e) {
            LOGGER.fatal("Unable to copy native executable, aborting.\n%s", e);
            throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
        }

        try {
            Files.writeString(
                new File(buildFolder, "Contents/Info.plist").toPath(),
                ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<!DOCTYPE plist PUBLIC \"-//Apple Computer//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n"
                    + "<plist version=\"1.0\">\n"
                    + "<dict>\n"
                    + "  <key>CFBundleGetInfoString</key>\n"
                    + "  <string>{name}</string>\n"
                    + "  <key>CFBundleExecutable</key>\n"
                    + "  <string>{name}</string>\n"
                    + "  <key>CFBundleIdentifier</key>\n"
                    + "  <string>{id}</string>\n"
                    + "  <key>CFBundleName</key>\n"
                    + "  <string>{name}</string>\n"
                    + "  <key>CFBundleIconFile</key>\n"
                    + "  <string>icons.icns</string>\n"
                    + "  <key>CFBundleShortVersionString</key>\n"
                    + "  <string>1.0</string>\n"
                    + "  <key>CFBundleInfoDictionaryVersion</key>\n"
                    + "  <string>6.0</string>\n"
                    + "  <key>CFBundlePackageType</key>\n"
                    + "  <string>APPL</string>\n"
                    + "  <key>IFMajorVersion</key>\n"
                    + "  <integer>0</integer>\n"
                    + "  <key>IFMinorVersion</key>\n"
                    + "  <integer>1</integer>\n"
                    + "  <key>NSHighResolutionCapable</key>\n"
                    + "  <true/>\n"
                    + "</dict>\n"
                    + "</plist>")
                        .replace("{name}", config.executableName)
                        .replace("{id}", config.executableId)
            );
        } catch (IOException e) {
            LOGGER.fatal("Unable to write Info.plist, aborting.\n%s", e);
            throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
        }

        if (icon != null) {
            try {
                Files.write(
                    new File(buildFolder, "Contents/Resources/icon.icns").toPath(),
                    icon.toIcns()
                );
            } catch (IOException e) {
                LOGGER.warn("Unable to write image icon, ignoring.\n%s", e);
            }
        }

        // Mark files as executable.
        final String[] NEED_TO_MARK_EXEC = {
                "Contents/MacOS/" + config.executableName,
                "Contents/Resources/runtime/bin/java",
                "../"
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
            File archiveFile = new File(JCup.ARTIFACTS_FOLDER, String.format("%s-%s-%s.tar.gz", config.executableName, OperatingSystem.macosx, arch));
            ArchiveCreator.create(Format.TAR_GZ, buildFolder.getParentFile(), archiveFile);
            LOGGER.info("Produced artifact: %s", archiveFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.fatal("Unable to create .tar.gz file, aborting.\n%s", e);
            throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
        }

        // TODO .pkg installer.
    }

}
