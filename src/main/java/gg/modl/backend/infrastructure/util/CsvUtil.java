package gg.modl.backend.infrastructure.util;

public final class CsvUtil {
    private static final String FORMULA_TRIGGERS = "=+-@\t\r\n";
    private static final String RECORD_TERMINATOR = "\n";

    private CsvUtil() {
    }

    public static String escapeCell(Object value) {
        String text = value == null ? "" : value.toString();
        if (!text.isEmpty() && FORMULA_TRIGGERS.indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    public static String row(Object... cells) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(escapeCell(cells[index]));
        }
        return builder.append(RECORD_TERMINATOR).toString();
    }
}
