package e.edit;

import java.util.*;
import e.ptextarea.*;
import e.util.*;

public class RubyDocumentationResearcher implements WorkspaceResearcher {
    /**
     * Look for something in a JTextComponent. Returns an HTML string
     * containing information about what it found. Should return
     * the empty string (not null) if it has nothing to say.
     */
    public String research(PTextArea text, String string) {
        String ri = getRi();
        if (ri == null) {
            return "";
        }
        ArrayList<String> lines = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(null, new String[] { "ruby", ri, "-T", "-f", "html", string }, lines, errors);
        String result = StringUtilities.join(lines, "\n");
        // Rewrite IO#puts as a link to ri:IO#puts and Zlib::GzipWriter#puts similarly.
        // An interesting case is the link to IO#printf in the IO#puts page, which is embedded in TT tags.
        result = result.replaceAll("(([A-Za-z0-9_?]+)((#|::)[A-Za-z0-9_?]+)+)(, )?", "<a href=\"ri:$1\">$1</a><br>");
        return (result.contains("<error>") ? "" : result);
    }
    
    private String getRi() {
        ArrayList<String> availableRis = new ArrayList<String>();
        ArrayList<String> errors = new ArrayList<String>();
        int status = ProcessUtilities.backQuote(null, new String[] { "which", "ri" }, availableRis, errors);
        if (status != 0 || availableRis.size() == 0) {
            return null;
        }
        return availableRis.get(0);
    }
    
    /** Returns true for Ruby files. */
    public boolean isSuitable(ETextWindow textWindow) {
        return textWindow.isRuby();
    }
}
