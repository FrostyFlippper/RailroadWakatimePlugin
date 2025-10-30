package org.FrostyFlippper.Util;

import org.FrostyFlippper.WakatimePlugin;

import java.util.Arrays;

public class PlatformUtil {

    public static void checkMissingPlatformSupport() {
        String osname = osname();
        String arch = architecture();

        String[] validCombinations = {
                "darwin-amd64",
                "darwin-arm64",
                "freebsd-386",
                "freebsd-amd64",
                "freebsd-arm",
                "linux-386",
                "linux-amd64",
                "linux-arm",
                "linux-arm64",
                "netbsd-386",
                "netbsd-amd64",
                "netbsd-arm",
                "openbsd-386",
                "openbsd-amd64",
                "openbsd-arm",
                "openbsd-arm64",
                "windows-386",
                "windows-amd64",
                "windows-arm64",
        };

        if (!Arrays.asList(validCombinations).contains(osname + "-" + arch))
            WakatimePlugin.getLogger().error("OS not supported!");
    }

    public static String osname() {
        if (isWindows())
            return "windows";

        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac") || os.contains("darwin"))
            return "darwin";

        if (os.contains("linux"))
            return "linux";

        return os;
    }

    public static boolean isWindows() {
        return System.getProperty("os.name").contains("Windows");
    }

    public static String architecture() {
        String arch = System.getProperty("os.arch");
        if (arch.contains("386") || arch.contains("32"))
            return "386";

        if (arch.equals("aarch64"))
            return "arm64";

        if (osname().equals("darwin") && arch.contains("arm"))
            return "arm64";

        if (arch.contains("64"))
            return "amd64";

        return arch;
    }
}
