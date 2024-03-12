package co.casterlabs.jcup.bundler;

import java.io.File;

public class Utils {

    public static void deleteRecursively(File file) {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            for (File sub : file.listFiles()) {
                deleteRecursively(sub);
            }
        }
        file.delete();
    }

}
