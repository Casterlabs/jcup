package co.casterlabs.jcup.bundler.platforms;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.jetbrains.annotations.Nullable;

import com.kichik.pecoff4j.PE;
import com.kichik.pecoff4j.ResourceDirectory;
import com.kichik.pecoff4j.ResourceDirectoryTable;
import com.kichik.pecoff4j.ResourceEntry;
import com.kichik.pecoff4j.constant.ResourceType;
import com.kichik.pecoff4j.io.DataReader;
import com.kichik.pecoff4j.io.DataWriter;
import com.kichik.pecoff4j.io.PEParser;
import com.kichik.pecoff4j.resources.GroupIconDirectory;
import com.kichik.pecoff4j.resources.GroupIconDirectoryEntry;
import com.kichik.pecoff4j.resources.IconImage;
import com.kichik.pecoff4j.util.IconFile;
import com.kichik.pecoff4j.util.PaddingType;

import co.casterlabs.jcup.bundler.Adoptium;
import co.casterlabs.jcup.bundler.JCup;
import co.casterlabs.jcup.bundler.JCupAbortException;
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

class WindowsBundler implements Bundler {
    static final Bundler INSTANCE = new WindowsBundler();
    private static final FastLogger LOGGER = Bundler.LOGGER.createChild("Windows");

    @SuppressWarnings("deprecation")
    @Override
    public void bundle(@NonNull Config config, @Nullable AppIcon icon, @NonNull OSSpecificConfig ossc, @NonNull Architecture arch) throws JCupAbortException {
        File buildFolder = JCup.createBuildFolder(OperatingSystem.windows, arch);
        Utils.deleteRecursively(buildFolder); // Empty it out.

        // Download the JRE and extract it to the runtime/ folder.
        {
            File runtimeFolder = new File(buildFolder, "runtime");
            File archive;
            try {
                archive = Adoptium.download(config.javaVersion, arch, OperatingSystem.windows);
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
            String vmArgs = config.vmArgs;
            if (ossc.extraVmArgs != null && !ossc.extraVmArgs.isEmpty()) {
                vmArgs += ' ';
                vmArgs += ossc.extraVmArgs;
            }
            Files.writeString(new File(buildFolder, "vmargs.txt").toPath(), vmArgs);
        } catch (IOException e) {
            LOGGER.fatal("Unable to write vmargs.txt, aborting.\n%s", e);
            throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
        }

        // Add the launcher exe.
        File executableFile = new File(buildFolder, config.executableName + ".exe");
        try (InputStream in = JCup.class.getResourceAsStream("/windows-launcher.exe");
            OutputStream out = new FileOutputStream(executableFile)) {
            in.transferTo(out);
        } catch (IOException e) {
            LOGGER.fatal("Unable to copy native executable, aborting.\n%s", e);
            throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
        }

        // Add the icon
        // TODO modify the .exe with this icon instead.
        if (icon != null) {
            try {
                // https://github.com/kichik/pecoff4j/blob/master/src/test/java/com/kichik/pecoff4j/ResourceDirectoryTest.java
                PE pe = PEParser.parse(executableFile);

                ResourceDirectory directory = pe.getImageData().getResourceTable();

                // add icon from ico file
                IconFile iconFile = IconFile.read(new DataReader(icon.toIco()));
                List<ResourceEntry> iconEntries = new ArrayList<>();
                for (IconImage iconImage : iconFile.getImages()) {
                    iconEntries.add(
                        entry(
                            iconEntries.size() + 1,
                            directory(entry(2057, iconImage.toByteArray()))
                        )
                    );
                }
                directory.getEntries().add(
                    entry(
                        ResourceType.ICON,
                        directory(iconEntries.toArray(new ResourceEntry[0]))
                    )
                );

                // add icon directory
                byte[] iconDirData = createIconDirectory(iconFile.getImages()).toByteArray();
                directory.getEntries().add(
                    entry(
                        ResourceType.GROUP_ICON,
                        directory(entry(1, directory(entry(2057, iconDirData))))
                    )
                );

                pe.rebuild(PaddingType.PATTERN);
                pe.write(new DataWriter(executableFile));
            } catch (IOException e) {
                LOGGER.warn("Unable to write image icon, ignoring.\n%s", e);
            }
        }

        // Create the build artifact.
        try {
            File archiveFile = new File(JCup.ARTIFACTS_FOLDER, String.format("%s-%s-%s.zip", config.executableName, OperatingSystem.windows, arch));
            ArchiveCreator.create(Format.ZIP, buildFolder, archiveFile);
            LOGGER.info("Produced artifact: %s", archiveFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.fatal("Unable to create .zip file, aborting.\n%s", e);
            throw new JCupAbortException(JCup.EXIT_CODE_ERROR);
        }

        // TODO msi installer.
        LOGGER.info("Done!");
    }

    private GroupIconDirectory createIconDirectory(IconImage[] icons) {
        GroupIconDirectory directory = new GroupIconDirectory();
        directory.setReserved(0);
        directory.setType(1);

        int id = 1;
        for (IconImage icon : icons) {
            GroupIconDirectoryEntry entry = new GroupIconDirectoryEntry();
            entry.setWidth(icon.getHeader() != null ? icon.getHeader().getWidth() : 0);
            entry.setHeight(icon.getHeader() != null ? icon.getHeader().getHeight() / 2 : 0);
            entry.setColorCount(0);
            entry.setReserved(0);
            entry.setPlanes(icon.getHeader() != null ? icon.getHeader().getPlanes() : 1);
            entry.setBitCount(icon.getHeader() != null ? icon.getHeader().getBitCount() : 32);
            entry.setBytesInRes(icon.sizeOf());
            entry.setId(id++);
            directory.getEntries().add(entry);
        }

        return directory;
    }

    private ResourceEntry entry(int id, ResourceDirectory directory) {
        ResourceEntry entry = new ResourceEntry();
        entry.setId(id);
        entry.setDirectory(directory);
        return entry;
    }

    private ResourceEntry entry(int id, byte[] data) {
        ResourceEntry entry = new ResourceEntry();
        entry.setId(id);
        entry.setCodePage(1252);
        entry.setData(data);
        return entry;
    }

    private ResourceDirectory directory(ResourceEntry... entries) {
        ResourceDirectory dir = new ResourceDirectory();
        ResourceDirectoryTable table = new ResourceDirectoryTable();
        table.setMajorVersion(4);
        dir.setTable(table);
        for (ResourceEntry entry : entries) {
            dir.getEntries().add(entry);
        }
        return dir;
    }
}
