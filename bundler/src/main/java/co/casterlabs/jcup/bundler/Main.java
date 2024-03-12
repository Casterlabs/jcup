package co.casterlabs.jcup.bundler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import co.casterlabs.jcup.bundler.archive.ArchiveExtractor;
import co.casterlabs.jcup.bundler.archive.Archives;
import co.casterlabs.jcup.bundler.config.Architecture;
import co.casterlabs.jcup.bundler.config.Config;
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
                    Rson.DEFAULT.toJson(new Config()).toString()
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
                Rson.DEFAULT.toJson(config).toString()
            );
            LOGGER.debug("Rewrote config with any missing parameters.");
        } catch (IOException e) {
            LOGGER.warn("Unable to rewrite config. Do we have permission to write? Ignoring.\n%s", e);
        }

        try {
            Path archive = Adoptium.download(Path.of("jcup/download-cache"), 8, Architecture.x86_64, OperatingSystem.windows);

            ArchiveExtractor.extract(
                Archives.probeFormat(archive.toString()),
                archive.toFile(),
                new File("jcup/test/vm")
            );
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

}
