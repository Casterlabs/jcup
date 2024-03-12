package co.casterlabs.jcup.bundler.config;

import java.util.Map;

import co.casterlabs.rakurai.json.annotating.JsonClass;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@JsonClass(exposeAll = true)
public class Config {
    public String executableName = "MyApp";
    public String vmArgs = "-Xms1M -jar MyApp.jar";
    public String[] mainInclude = {
            "MyApp.jar"
    };
    public String appIconPath = null;

    public Map<OperatingSystem, OSSpecificConfig> toCreate = Map.of(
        OperatingSystem.windows,
        new OSSpecificConfig(
            new Architecture[] {
                    Architecture.x86_64
            },
            new String[0]
        )
    );

    @NoArgsConstructor
    @AllArgsConstructor
    @JsonClass(exposeAll = true)
    public static class OSSpecificConfig {
        public Architecture[] architectures = {};
        public String[] extraInclude = {};
    }

}
