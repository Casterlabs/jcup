package co.casterlabs.jcup.bundler;

import java.io.File;

import co.casterlabs.jcup.bundler.config.Architecture;
import co.casterlabs.jcup.bundler.config.OperatingSystem;
import lombok.NonNull;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class JCup {
    public static final int EXIT_CODE_ERROR = 255;
    public static final int EXIT_CODE_OTHER = 2;
    public static final int EXIT_CODE_SUCCESS = 0;

    public static final File BASE_FOLDER = new File("./jcup/");
    public static final File BUILD_FOLDER = new File(BASE_FOLDER, "build");
    public static final File ARTIFACTS_FOLDER = new File(BASE_FOLDER, "artifacts");
    public static final File DOWNLOAD_CACHE_FOLDER = new File(BASE_FOLDER, "download-cache");

    static {
        BASE_FOLDER.mkdirs();
        BUILD_FOLDER.mkdirs();
        ARTIFACTS_FOLDER.mkdirs();
        DOWNLOAD_CACHE_FOLDER.mkdirs();
    }

    public static final FastLogger LOGGER = new FastLogger("JCup");

    public static File createBuildFolder(@NonNull OperatingSystem os, @NonNull Architecture arch) {
        File folder = new File(BUILD_FOLDER, String.format("%s-%s", os, arch));
        folder.mkdirs();
        return folder;
    }

}
