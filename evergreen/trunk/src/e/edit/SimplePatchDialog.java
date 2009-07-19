package e.edit;

import e.forms.*;
import e.ptextarea.*;
import e.util.*;
import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;

public class SimplePatchDialog {
    /** Highlight color for intraline removals. */
    private static final Color DARK_RED = Color.decode("#ee9999");
    
    /** Highlight color for - lines. */
    private static final Color LIGHT_RED = Color.decode("#ffdddd");
    
    /** Highlight color for intraline additions. */
    private static final Color DARK_GREEN = Color.decode("#99ee99");
    
    /** Highlight color for + lines. */
    private static final Color LIGHT_GREEN = Color.decode("#ddffdd");
    
    /** Highlight color for the @@ lines. */
    private static final Color VERY_LIGHT_GRAY = Color.decode("#eeeeee");
    
    private SimplePatchDialog() {
    }
    
    private static JComponent makeScrollablePatchView(Font font, Diffable from, Diffable to) {
        return new JScrollPane(makePatchView(font, from, to));
    }
    
    private static List<String> runDiff(Diffable from, Diffable to) {
        final String[] command = new String[] { "diff", "-u", "-b", "-B", "-L", from.label(), from.filename(), "-L", to.label(), to.filename() };
        //final String[] command = new String[] { Evergreen.getResourceFilename("lib", "scripts", "ediff.py"), from.label(), from.filename(), to.label(), to.filename() };
        final ArrayList<String> lines = new ArrayList<String>();
        final ArrayList<String> errors = new ArrayList<String>();
        final int status = ProcessUtilities.backQuote(null, command, lines, errors);
        // Output on stderr is not expected, and worth showing to the user.
        if (lines.size() == 0) {
            lines.addAll(errors);
        }
        // POSIX says:
        //   0 => no differences were found.
        //   1 => differences were found.
        //  >1 => an error occurred.
        if (status == 0) {
            lines.add("(No non-whitespace differences.)");
        } else if (status > 1) {
            lines.add("diff(1) failed.");
        }
        
        // Clean up any temporary files.
        from.dispose();
        to.dispose();
        
        return lines;
    }
    
    private static JComponent makePatchView(Font font, Diffable from, Diffable to) {
        final PTextArea textArea = new PTextArea(20, 80);
        textArea.setEditable(false);
        textArea.setFont(font);
        
        // Try to configure the text area appropriately for the specific content.
        final String probableFilename = from.filename().indexOf(File.separatorChar) != -1 ? from.filename() : to.filename();
        final String probableContent = from.content().length() > to.content().length() ? from.content() : to.content();
        FileType.guessFileType(probableFilename, probableContent).configureTextArea(textArea);
        // FIXME: BugDatabaseHighlighter?
        // FIXME: spelling exceptions?
        
        final List<HighlightInfo> highlights = new ArrayList<HighlightInfo>();
        Color color = null;
        int lineNumber = 0; // Lines beginning with '?' don't count!
        for (String line : runDiff(from, to)) {
            if (line.startsWith("?")) {
                // A '?' line always follows a '+' or '-' line, so choose the dark color corresponding to the last color we used.
                highlightDifferencesInLine(highlights, textArea, (color == LIGHT_GREEN) ? DARK_GREEN : DARK_RED, lineNumber - 1, line);
                continue;
            } else if (line.startsWith("+++")) {
                color = DARK_GREEN;
            } else if (line.startsWith("+")) {
                color = LIGHT_GREEN;
            } else if (line.startsWith("---")) {
                color = DARK_RED;
            } else if (line.startsWith("-")) {
                color = LIGHT_RED;
            } else if (line.startsWith("@@ ")) {
                color = VERY_LIGHT_GRAY;
            } else {
                color = null;
            }
            textArea.append(line + "\n");
            if (color != null) {
                final int lineStart = textArea.getLineStartOffset(lineNumber);
                final int lineEnd = textArea.getLineEndOffsetBeforeTerminator(lineNumber) + 1;
                highlights.add(new HighlightInfo(lineStart, lineEnd, color));
            }
            ++lineNumber;
        }
        
        // Apply the collected highlights.
        // Doing this in a second pass avoids the problems inherent in changing the text and its highlighting at the same time.
        for (HighlightInfo highlight : highlights) {
            highlight.apply(textArea);
        }
        
        return textArea;
    }
    
    private static void highlightDifferencesInLine(List<HighlightInfo> highlights, PTextArea textArea, Color color, int lineNumber, String pattern) {
        final int lineStart = textArea.getLineStartOffset(lineNumber);
        for (int i = 1; i < pattern.length();) {
            if (pattern.charAt(i) == ' ') {
                ++i;
            } else {
                final int highlightStart = lineStart + i;
                while (i < pattern.length() && pattern.charAt(i) != ' ') {
                    ++i;
                }
                final int highlightEnd = lineStart + i;
                highlights.add(new HighlightInfo(highlightStart, highlightEnd, color));
            }
        }
    }
    
    static class HighlightInfo {
        int start;
        int end;
        Color color;
        
        HighlightInfo(int start, int end, Color color) {
            this.start = start;
            this.end = end;
            this.color = color;
        }
        
        void apply(PTextArea textArea) {
            textArea.addHighlight(new PPatchTextStyler.PatchHighlight(textArea, start, end, color));
        }
    }
    
    public static void showPatchBetween(String title, Diffable from, Diffable to) {
        makeDialog(Evergreen.getInstance().getFrame(), ChangeFontAction.getConfiguredFixedFont(), title, null, from, to).showNonModal();
    }
    
    public static boolean showPatchBetween(Frame parent, Font font, String title, String question, String buttonLabel, Diffable from, Diffable to) {
        return makeDialog(parent, font, title, question, from, to).show(buttonLabel);
    }
    
    private static FormBuilder makeDialog(Frame parent, Font font, String title, String question, Diffable from, Diffable to) {
        FormBuilder form = new FormBuilder(parent, title);
        if (question != null) {
            form.getFormPanel().addRow("", new JLabel(question));
        }
        form.getFormPanel().addRow("Patch:", makeScrollablePatchView(font, from, to));
        return form;
    }
    
    // For testing from the command line.
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("usage: SimplePatchDialog FILE1 FILE2");
            System.exit(1);
        }
        final File file1 = FileUtilities.fileFromString(args[0]);
        final File file2 = FileUtilities.fileFromString(args[1]);
        GuiUtilities.initLookAndFeel();
        final Font font = new Font(GuiUtilities.getMonospacedFontName(), Font.PLAIN, 12);
        final String title = "Patch between '" + file1 + "' and '" + file2 + "'";
        showPatchBetween(null, font, title, null, "Close", new Diffable(file1.toString(), file1), new Diffable(file2.toString(), file2));
    }
}
