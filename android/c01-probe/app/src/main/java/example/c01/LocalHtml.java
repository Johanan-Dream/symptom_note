package example.c01;
/** Only alter the document boundary, never HTML strings inside embedded JavaScript. */
public final class LocalHtml {
    public static String appendBeforeEnd(String html, String extra) {
        int end = html.lastIndexOf("</html>");
        return end < 0 ? html + extra : html.substring(0,end) + extra + html.substring(end);
    }
}
