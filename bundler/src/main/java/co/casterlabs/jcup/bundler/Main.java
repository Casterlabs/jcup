package co.casterlabs.jcup.bundler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map.Entry;

import co.casterlabs.commons.platform.OSDistribution;
import co.casterlabs.commons.platform.Platform;
import co.casterlabs.jcup.bundler.archive.ArchiveCreator;
import co.casterlabs.jcup.bundler.archive.ArchiveExtractor;
import co.casterlabs.jcup.bundler.archive.Archives;
import co.casterlabs.jcup.bundler.archive.Archives.Format;
import co.casterlabs.jcup.bundler.config.Architecture;
import co.casterlabs.jcup.bundler.config.Config;
import co.casterlabs.jcup.bundler.config.Config.OSSpecificConfig;
import co.casterlabs.jcup.bundler.config.OperatingSystem;
import co.casterlabs.rakurai.json.Rson;
import lombok.Getter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import xyz.e3ndr.fastloggingframework.FastLoggingFramework;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

@Getter
@Command(name = "bundle", mixinStandardHelpOptions = true, version = "yes", description = "Bundles your app all up <3")
public class Main implements Runnable {
    public static final int EXIT_CODE_ERROR = 255;
    public static final int EXIT_CODE_OTHER = 2;
    public static final int EXIT_CODE_SUCCESS = 0;

    public static final FastLogger LOGGER = new FastLogger("JCup");

    @Option(names = {
            "-t",
            "--trace"
    }, description = "Enables trace logging.")
    private boolean enableTraceLogging;

    @Option(names = {
            "-d",
            "--debug"
    }, description = "Enables debug logging.")
    private boolean enableDebugLogging;

    @Option(names = {
            "-nc",
            "--no-color"
    }, description = "Disables colored output.")
    private boolean disableColor = false;

    @Option(names = {
            "-c",
            "--config"
    }, description = "Allows you to specify a config file path")
    private String configPath = "jcup/config.json";

    public static void main(String[] args) throws Exception {
        new CommandLine(new Main()).execute(args); // Calls #run()
    }

    @Override
    public void run() {
        if (this.disableColor) {
            FastLoggingFramework.setColorEnabled(false);
            LOGGER.info("Disabled colored log output.");
        } else {
            FastLoggingFramework.setColorEnabled(true);
            LOGGER.info("Enabled colored log output.");
        }

        if (this.enableTraceLogging) {
            LOGGER.setCurrentLevel(LogLevel.TRACE);
            LOGGER.trace("Enabled trace logging.");
        } else if (this.enableTraceLogging) {
            LOGGER.setCurrentLevel(LogLevel.DEBUG);
            LOGGER.debug("Enabled debug logging.");
        }

        Path configFile = Path.of(this.configPath);
        if (!configFile.toFile().exists()) {
            try {
                // Config file doesn't exist. Write out some defaults.
                configFile.toFile().getParentFile().mkdirs();
                Files.writeString(
                    configFile,
                    Rson.DEFAULT.toJson(new Config()).toString(true)
                );
                Files.writeString(
                    configFile.resolveSibling(".gitignore"),
                    "*\n"
                        + "!.gitignore\n"
                        + "!config.json\n"
                );
                LOGGER.info("Wrote config defaults. Edit %s and re-run this tool.", configFile.toAbsolutePath());
                System.exit(EXIT_CODE_OTHER);
                return;
            } catch (IOException e) {
                LOGGER.severe("Unable to write config defaults. Do we have permission to write?\n%s", e);
                System.exit(EXIT_CODE_ERROR);
                return;
            }
        }

        // Read-in the config.
        Config config;
        try {
            config = Rson.DEFAULT.fromJson(Files.readString(configFile), Config.class);
        } catch (IOException e) {
            LOGGER.severe("Unable to read config. Do we have permission to read?\n%s", e);
            System.exit(EXIT_CODE_ERROR);
            return;
        }

        try {
            // Update the config.json with any new values/defaults.
            Files.writeString(
                configFile,
                Rson.DEFAULT.toJson(config).toString(true)
            );
            LOGGER.debug("Rewrote config with any missing parameters.");
        } catch (IOException e) {
            LOGGER.warn("Unable to rewrite config. Do we have permission to write? Ignoring.\n%s", e);
        }

        // Empty these out.
        Utils.deleteRecursively(new File("jcup/build"));
        new File("jcup/build").mkdirs();
        Utils.deleteRecursively(new File("jcup/artifacts"));
        new File("jcup/artifacts").mkdirs();

        for (OSSpecificConfig ossc : config.toCreate) {
            for (OperatingSystem os : ossc.operatingSystems) {
                for (Architecture arch : ossc.architectures) {
                    Path archive;
                    try {
                        archive = Adoptium.download(Path.of("jcup/download-cache"), config.javaVersion, arch, os);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("Unsupported build target, ignoring.\n%s", e);
                        continue;
                    } catch (IOException | InterruptedException e) {
                        LOGGER.fatal("Unable to download JRE, aborting.\n%s", e);
                        System.exit(EXIT_CODE_ERROR);
                        return;
                    }

                    File buildFolder = new File(String.format("jcup/build/%s-%s/%s", os, arch, os == OperatingSystem.macosx ? config.executableName + ".app" : "")); // Also add .app extension if for macos.
                    File runtimeFolder = os == OperatingSystem.macosx ? buildFolder : new File(buildFolder, "runtime");
                    Utils.deleteRecursively(buildFolder); // Empty it out.

                    try {
                        ArchiveExtractor.extract(
                            Archives.probeFormat(archive.toString()),
                            archive.toFile(),
                            runtimeFolder
                        );
                    } catch (IOException e) {
                        LOGGER.fatal("Unable to extract JRE, aborting.\n%s", e);
                        System.exit(EXIT_CODE_ERROR);
                        return;
                    }

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

                        if (os == OperatingSystem.macosx) {
                            // We need to rearrange some files.
                            File newBuildFolder = new File(buildFolder, "Contents/Resources");
                            Files.createDirectories(newBuildFolder.toPath());
                            Files.move(new File(buildFolder, "Contents/Home").toPath(), new File(buildFolder, "Contents/Resources/runtime").toPath());
                            buildFolder = newBuildFolder;
                        }
                    } catch (IOException e) {
                        LOGGER.fatal("Unable to move JRE files, aborting.\n%s", e);
                        System.exit(EXIT_CODE_ERROR);
                        return;
                    }

                    Utils.deleteRecursively(new File(runtimeFolder, "man")); // Delete any manpages.
                    Utils.deleteRecursively(new File(runtimeFolder, "Contents/Resources/man")); // Delete any manpages.
                    Utils.deleteRecursively(new File(runtimeFolder, "Contents/_CodeSignature")); // Delete any code signatures.
                    Utils.deleteRecursively(new File(runtimeFolder, "Contents/Info.plist")); // Delete any manifests.

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
                        System.exit(EXIT_CODE_ERROR);
                        return;
                    }

                    // Create the VM args file.
                    try {
                        String vmArgs = config.vmArgs;
                        if (ossc.extraVmArgs != null && !ossc.extraVmArgs.isEmpty()) {
                            vmArgs += ' ';
                            vmArgs += ossc.extraVmArgs;
                        }
                        Files.writeString(new File(buildFolder, "vmargs.txt").toPath(), vmArgs);
                    } catch (IOException e) {
                        LOGGER.fatal("Unable to write vmargs.txt, aborting.\n%s", e);
                        System.exit(EXIT_CODE_ERROR);
                        return;
                    }

                    switch (os) {
                        case linux_glibc:
                        case linux_musl: {
                            // Add the launcher executable.
                            try (InputStream in = Main.class.getResourceAsStream("/unix-launcher");
                                OutputStream out = new FileOutputStream(new File(buildFolder, config.executableName))) {
                                in.transferTo(out);
                            } catch (IOException e) {
                                LOGGER.fatal("Unable to copy native executable, aborting.\n%s", e);
                                System.exit(EXIT_CODE_ERROR);
                                return;
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
                                    if (!file.exists()) continue; // Not applicable.
                                    if (!file.setExecutable(true)) {
                                        LOGGER.fatal("Unable to mark %s as executable, aborting.", file);
                                        System.exit(EXIT_CODE_ERROR);
                                        return;
                                    }
                                }
                            }

                            try {
                                File archiveFile = new File(String.format("jcup/artifacts/%s-%s-%s.tar.gz", config.executableName, os, arch));
                                ArchiveCreator.create(Format.TAR_GZ, buildFolder, archiveFile);
                                LOGGER.info("Produced artifact: %s", archiveFile.getAbsolutePath());
                            } catch (IOException e) {
                                LOGGER.fatal("Unable to create .tar.gz file, aborting.\n%s", e);
                                System.exit(EXIT_CODE_ERROR);
                                return;
                            }

                            // TODO .AppImage
                            break;
                        }

                        case macosx: {
                            // Add the launcher executable.
                            try (InputStream in = Main.class.getResourceAsStream("/macosx-launcher");
                                OutputStream out = new FileOutputStream(new File(buildFolder, "../MacOS/" + config.executableName))) {
                                in.transferTo(out);
                            } catch (IOException e) {
                                LOGGER.fatal("Unable to copy native executable, aborting.\n%s", e);
                                System.exit(EXIT_CODE_ERROR);
                                return;
                            }

                            try {
                                Files.writeString(
                                    new File(buildFolder, "../Info.plist").toPath(),
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
                                System.exit(EXIT_CODE_ERROR);
                                return;
                            }

                            // Mark files as executable.
                            final String[] NEED_TO_MARK_EXEC = {
                                    "Contents/MacOS" + config.executableName,
                                    "Contents/Resources/runtime/bin/java"
                            };

                            if (Platform.osDistribution == OSDistribution.WINDOWS_NT) {
                                LOGGER.warn(
                                    "Windows doesn't support marking files (%s) as executable, this will likely cause problems for your users. I hope you know what you are doing.",
                                    String.join(", ", NEED_TO_MARK_EXEC)
                                );
                            } else {
                                for (String path : NEED_TO_MARK_EXEC) {
                                    File file = new File(buildFolder, path);
                                    if (!file.exists()) continue; // Not applicable.
                                    if (!file.setExecutable(true)) {
                                        LOGGER.fatal("Unable to mark %s as executable, aborting.", file);
                                        System.exit(EXIT_CODE_ERROR);
                                        return;
                                    }
                                }
                            }

                            try {
                                File archiveFile = new File(String.format("jcup/artifacts/%s-%s-%s.app.tar.gz", config.executableName, os, arch));
                                ArchiveCreator.create(Format.ZIP, new File(String.format("jcup/build/%s-%s", os, arch)), archiveFile);
                                LOGGER.info("Produced artifact: %s", archiveFile.getAbsolutePath());
                            } catch (IOException e) {
                                LOGGER.fatal("Unable to create .zip file, aborting.\n%s", e);
                                System.exit(EXIT_CODE_ERROR);
                                return;
                            }

                            // TODO .pkg installer.
                            break;
                        }

                        case windows: {
                            // Add the launcher exe.
                            try (InputStream in = Main.class.getResourceAsStream("/windows-launcher.exe");
                                OutputStream out = new FileOutputStream(new File(buildFolder, config.executableName + ".exe"))) {
                                in.transferTo(out);
                            } catch (IOException e) {
                                LOGGER.fatal("Unable to copy native executable, aborting.\n%s", e);
                                System.exit(EXIT_CODE_ERROR);
                                return;
                            }

                            try {
                                File archiveFile = new File(String.format("jcup/artifacts/%s-%s-%s.tar.gz", config.executableName, os, arch));
                                ArchiveCreator.create(Format.ZIP, buildFolder, archiveFile);
                                LOGGER.info("Produced artifact: %s", archiveFile.getAbsolutePath());
                            } catch (IOException e) {
                                LOGGER.fatal("Unable to create .zip file, aborting.\n%s", e);
                                System.exit(EXIT_CODE_ERROR);
                                return;
                            }

                            // TODO msi installer.
                            break;
                        }

                    }
                }
            }
        }
    }

}
