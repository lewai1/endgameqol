package endgame.plugin.utils;

/**
 * Shared HTML utility methods for HyUI page builders.
 */
public final class HtmlUtil {

    private HtmlUtil() {}

    /**
     * Escape HTML special characters to prevent injection in HyUI pages.
     * Returns "Unknown" for null input.
     */
    public static String escape(String text) {
        if (text == null) return "Unknown";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
