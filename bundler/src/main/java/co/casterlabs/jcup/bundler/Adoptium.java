package co.casterlabs.jcup.bundler;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import co.casterlabs.jcup.bundler.config.Architecture;
import co.casterlabs.jcup.bundler.config.OperatingSystem;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class Adoptium {
    private static final FastLogger LOGGER = JCup.LOGGER.createChild("AdoptiumDownloader");

    private static final HttpClient httpClient = HttpClient
        .newBuilder()
        .followRedirects(Redirect.ALWAYS)
        .build();

    private static final Map<Architecture, String> ARCH_MAPPING = Map.of(
        Architecture.x86, "x86",
        Architecture.x86_64, "x64",
        Architecture.arm, "arm",
        Architecture.aarch64, "aarch64",
        Architecture.riscv64, "riscv64"
    );

    private static final Map<OperatingSystem, String> OS_MAPPING = Map.of(
        OperatingSystem.linux_glibc, "linux",
        OperatingSystem.linux_musl, "alpine-linux",
        OperatingSystem.macosx, "mac",
        OperatingSystem.windows, "windows"
    );

    public static File download(int version, Architecture arch, OperatingSystem os) throws IOException, InterruptedException {
        LOGGER.info("Looking for build (%d:%s:%s)", version, arch, os);
        JsonArray json = Rson.DEFAULT.fromJson(
            httpClient.send(
                HttpRequest.newBuilder()
                    .uri(
                        URI.create(
                            "https://api.adoptium.net/v3/assets/latest/{version}/hotspot?architecture={arch}&image_type=jre&os={os}&vendor=eclipse"
                                .replace("{version}", String.valueOf(version))
                                .replace("{arch}", ARCH_MAPPING.get(arch))
                                .replace("{os}", OS_MAPPING.get(os))
                        )
                    )
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            ).body(), JsonArray.class
        );
        LOGGER.trace("Loaded build data: %s", json);

        String binaryUrl = null;
        String binaryName = null;
        for (JsonElement e : json) {
            JsonObject object = e.getAsObject();
            if (object.containsKey("binary")) {
                JsonObject binaryObject = object.getObject("binary");
                if (binaryObject.containsKey("package")) {
                    JsonObject packageObject = binaryObject.getObject("package");
                    binaryUrl = packageObject.getString("link");
                    binaryName = packageObject.getString("name");
                    break;
                }
            }
        }

        if (binaryUrl == null || binaryName == null) {
            throw new IllegalArgumentException("Unable to find download for " + version + ":" + arch + ":" + os);
        }

        File binaryArchive = new File(JCup.DOWNLOAD_CACHE_FOLDER, binaryName);
        LOGGER.debug("Url: %s, Path: %s", binaryUrl, binaryArchive);

        if (binaryArchive.exists()) {
            LOGGER.info("This JRE build is cached. Using that instead.");
        } else {
            LOGGER.info("Found a link. Downloading...");

            httpClient.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(binaryUrl))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofFile(binaryArchive.toPath())
            );
            LOGGER.info("Finished downloading...");
        }
        return binaryArchive;
    }

}
