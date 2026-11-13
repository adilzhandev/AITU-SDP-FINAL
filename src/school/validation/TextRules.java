package school.validation;

/** Shared input limits keep valid domain data representable in a saved snapshot. */
public final class TextRules {
    public static final int ID_MAX = 64;
    public static final int NAME_MAX = 200;
    public static final int EMAIL_MAX = 254;
    public static final int TITLE_MAX = 200;
    public static final int SCHEDULE_MAX = 200;
    public static final int CATEGORY_MAX = 64;
    public static final int COMMENT_MAX = 4000;
    public static final int MAX_WEIGHTS = 1000;
    public static final int MAX_RECORDS = 100000;
    public static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

    private TextRules() { }

    public static String required(String value, String label, int maximum) {
        String result = optional(value, label, maximum);
        if (result.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank.");
        }
        return result;
    }

    public static String optional(String value, String label, int maximum) {
        if (value == null) return "";
        validateUnicode(value, label);
        String result = value.strip();
        if (result.length() > maximum) {
            throw new IllegalArgumentException(label + " must not exceed " + maximum + " characters.");
        }
        return result;
    }

    /** Rejects unpaired UTF-16 surrogates, which cannot be written as valid UTF-8. */
    public static void validateUnicode(String value, String label) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (Character.isHighSurrogate(current)) {
                if (++i >= value.length() || !Character.isLowSurrogate(value.charAt(i))) {
                    throw new IllegalArgumentException(label + " contains invalid Unicode.");
                }
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException(label + " contains invalid Unicode.");
            }
        }
    }
}
