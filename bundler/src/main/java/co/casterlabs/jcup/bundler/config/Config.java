package co.casterlabs.jcup.bundler.config;

import java.util.Map;

import co.casterlabs.rakurai.json.annotating.JsonClass;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@JsonClass(exposeAll = true)
public class Config {
    public String executableName = "MyApp";
    public String appIconPath = null;

    public String vmArgs = "-Xms1M -jar MyApp.jar";
    public int javaVersion = 17;

    public Map<String, String> mainInclude = Map.of(
        "MyApp.jar", "MyApp.jar"
    );

    public OSSpecificConfig[] toCreate = {
            new OSSpecificConfig(
                OperatingSystem.values(),
                Architecture.values(),
                Map.of(
                    "MyApp.jar", "MyApp.jar"
                )
            )
    };

    @NoArgsConstructor
    @AllArgsConstructor
    @JsonClass(exposeAll = true)
    public static class OSSpecificConfig {
        public OperatingSystem[] operatingSystems;
        public Architecture[] architectures;

        public Map<String, String> extraInclude;
    }

}
