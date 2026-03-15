package gg.modl.backend.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ByteFormatUtil {

    private static final long KB = 1024L;
    private static final long MB = KB * 1024;
    private static final long GB = MB * 1024;

    public String format(long bytes) {
        if (bytes < KB) {
            return bytes + " B";
        } else if (bytes < MB) {
            return String.format("%.2f KB", bytes / (double) KB);
        } else if (bytes < GB) {
            return String.format("%.2f MB", bytes / (double) MB);
        } else {
            return String.format("%.2f GB", bytes / (double) GB);
        }
    }

    public String formatCompact(long bytes) {
        if (bytes < MB) {
            return String.format("%.0f KB", bytes / (double) KB);
        } else if (bytes < GB) {
            return String.format("%.0f MB", bytes / (double) MB);
        } else {
            return String.format("%.1f GB", bytes / (double) GB);
        }
    }
}
