package co.casterlabs.jcup.bundler.config;

import java.util.Collections;
import java.util.Map;

import co.casterlabs.rakurai.json.annotating.JsonClass;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@JsonClass(exposeAll = true)
public class Config {
    public String executableName = "MyApp";
    public String executableId = "co.casterlabs.jcup.example_app";
    public String appIconPath = null;

    public String vmArgs = "-Xms1M -jar jcup-example-app.jar";
    public int javaVersion = 17;

    public Map<String, String> mainInclude = Map.of(
        "jcup-example-app.jar", "jcup-example-app.jar"
    );

    public OSSpecificConfig[] toCreate = {
            new OSSpecificConfig(
                new OperatingSystem[] {
                        OperatingSystem.windows,
                        OperatingSystem.macosx
                },
                new Architecture[] {
                        Architecture.x86
                },
                null,
                Collections.emptyMap()
            ),
            new OSSpecificConfig(
                new OperatingSystem[] {
                        OperatingSystem.linux_glibc,
                        OperatingSystem.linux_musl,
                        OperatingSystem.windows,
                        OperatingSystem.macosx
                },
                new Architecture[] {
                        Architecture.x86_64
                },
                null,
                Collections.emptyMap()
            ),
            new OSSpecificConfig(
                new OperatingSystem[] {
                        OperatingSystem.linux_glibc,
                        OperatingSystem.linux_musl,
                        OperatingSystem.macosx
                },
                new Architecture[] {
                        Architecture.aarch64
                },
                null,
                Collections.emptyMap()
            ),
            new OSSpecificConfig(
                new OperatingSystem[] {
                        OperatingSystem.linux_glibc
                },
                new Architecture[] {
                        Architecture.arm
                },
                null,
                Collections.emptyMap()
            )
    };

    @NoArgsConstructor
    @AllArgsConstructor
    @JsonClass(exposeAll = true)
    public static class OSSpecificConfig {
        public OperatingSystem[] operatingSystems;
        public Architecture[] architectures;

        public String extraVmArgs;
        public Map<String, String> extraInclude;
    }

}
