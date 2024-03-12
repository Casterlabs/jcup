package co.casterlabs.jcup.bundler.config;

import java.util.HashMap;
import java.util.Map;

import co.casterlabs.rakurai.json.annotating.JsonClass;

@JsonClass(exposeAll = true)
public class Config {
    public String executableName = "MyApp";
    public String vmArgs = "-Xms1M -jar MyApp.jar";
    public String[] mainInclude = {
            "MyApp.jar"
    };
    public String appIconPath = null;

    public Map<OperatingSystem, OSSpecificConfig> toCreate = new HashMap<>();

    @JsonClass(exposeAll = true)
    public static class OSSpecificConfig {
        public Architecture[] architectures = {};
        public String[] extraInclude = {};
    }

}
