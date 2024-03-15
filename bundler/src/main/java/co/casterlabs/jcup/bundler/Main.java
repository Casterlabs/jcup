package co.casterlabs.jcup.bundler;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import co.casterlabs.jcup.bundler.config.Architecture;
import co.casterlabs.jcup.bundler.config.Config;
import co.casterlabs.jcup.bundler.config.Config.OSSpecificConfig;
import co.casterlabs.jcup.bundler.config.OperatingSystem;
import co.casterlabs.jcup.bundler.icons.AppIcon;
import co.casterlabs.jcup.bundler.platforms.Bundler;
import co.casterlabs.rakurai.json.Rson;
import lombok.Getter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import xyz.e3ndr.fastloggingframework.FastLoggingFramework;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

@Getter
@Command(name = "bundle", mixinStandardHelpOptions = true, version = "yes", description = "Bundles your app all up <3")
public class Main implements Runnable {
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

    public static void main(String[] args) throws Exception {
        new CommandLine(new Main()).execute(args); // Calls #run()
    }

    @Override
    public void run() {
        if (this.disableColor) {
            FastLoggingFramework.setColorEnabled(false);
            JCup.LOGGER.info("Disabled colored log output.");
        } else {
            FastLoggingFramework.setColorEnabled(true);
            JCup.LOGGER.info("Enabled colored log output.");
        }

        if (this.enableTraceLogging) {
            JCup.LOGGER.setCurrentLevel(LogLevel.TRACE);
            JCup.LOGGER.trace("Enabled trace logging.");
        } else if (this.enableTraceLogging) {
            JCup.LOGGER.setCurrentLevel(LogLevel.DEBUG);
            JCup.LOGGER.debug("Enabled debug logging.");
        }

        File configFile = new File(JCup.BASE_FOLDER, "config.json");
        if (!configFile.exists()) {
            try {
                // Config file doesn't exist. Write out some defaults.
                configFile.getParentFile().mkdirs();
                Files.writeString(
                    configFile.toPath(),
                    Rson.DEFAULT.toJson(new Config()).toString(true)
                );
                Files.writeString(
                    configFile.toPath().resolveSibling(".gitignore"),
                    "*\n"
                        + "!.gitignore\n"
                        + "!config.json\n"
                );
                JCup.LOGGER.info("Wrote config defaults. Edit %s and re-run this tool.", configFile.getAbsolutePath());
                System.exit(JCup.EXIT_CODE_OTHER);
                return;
            } catch (IOException e) {
                JCup.LOGGER.severe("Unable to write config defaults. Do we have permission to write?\n%s", e);
                System.exit(JCup.EXIT_CODE_ERROR);
                return;
            }
        }

        // Read-in the config.
        Config config;
        try {
            config = Rson.DEFAULT.fromJson(Files.readString(configFile.toPath()), Config.class);
        } catch (IOException e) {
            JCup.LOGGER.severe("Unable to read config. Do we have permission to read?\n%s", e);
            System.exit(JCup.EXIT_CODE_ERROR);
            return;
        }

        try {
            // Update the config.json with any new values/defaults.
            Files.writeString(
                configFile.toPath(),
                Rson.DEFAULT.toJson(config).toString(true)
            );
            JCup.LOGGER.debug("Rewrote config with any missing parameters.");
        } catch (IOException e) {
            JCup.LOGGER.warn("Unable to rewrite config. Do we have permission to write? Ignoring.\n%s", e);
        }

        AppIcon icon = null;
        if (config.appIconPath != null) {
            try {
                icon = AppIcon.from(new File(config.appIconPath));
            } catch (IOException e) {
                JCup.LOGGER.warn("Unable to read app icon, ignoring.\n%s", e);
            }
        }

        for (OSSpecificConfig ossc : config.toCreate) {
            for (OperatingSystem os : ossc.operatingSystems) {
                Bundler bundler = Bundler.getBundler(os);
                for (Architecture arch : ossc.architectures) {
                    try {
                        bundler.bundle(config, icon, ossc, arch);
                    } catch (JCupAbortException e) {
                        System.exit(e.desiredExitCode);
                        return;
                    }
                }
            }
        }
    }

}
