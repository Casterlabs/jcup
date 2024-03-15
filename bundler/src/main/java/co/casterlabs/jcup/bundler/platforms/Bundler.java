package co.casterlabs.jcup.bundler.platforms;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.jcup.bundler.JCup;
import co.casterlabs.jcup.bundler.JCupAbortException;
import co.casterlabs.jcup.bundler.config.Architecture;
import co.casterlabs.jcup.bundler.config.Config;
import co.casterlabs.jcup.bundler.config.Config.OSSpecificConfig;
import co.casterlabs.jcup.bundler.config.OperatingSystem;
import co.casterlabs.jcup.bundler.icons.AppIcon;
import lombok.NonNull;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public interface Bundler {
    static final FastLogger LOGGER = JCup.LOGGER.createChild("Bundler");

    public void bundle(@NonNull Config config, @Nullable AppIcon icon, @NonNull OSSpecificConfig ossc, @NonNull Architecture arch) throws JCupAbortException;

    public static Bundler getBundler(OperatingSystem os) {
        return switch (os) {
            case linux_glibc -> LinuxBundler.INSTANCE_GLIBC;
            case linux_musl -> LinuxBundler.INSTANCE_MUSL;
            case macosx -> MacOSBundler.INSTANCE;
            case windows -> WindowsBundler.INSTANCE;
        };
    }

}
