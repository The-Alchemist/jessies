package e.edit;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;
import javax.swing.Timer;
import e.ptextarea.*;
import e.util.*;

public class Advisor extends JPanel {
    
    private static final ExecutorService executorService = ThreadUtilities.newSingleThreadExecutor("Advisor");
    
    private ArrayList<WorkspaceResearcher> researchers = new ArrayList<WorkspaceResearcher>();
    
    /** The advice window. */
    private AdvisorHtmlPane advicePane = new AdvisorHtmlPane();
    
    /** Counts down until the next research event. */
    private Timer timer;
    
    /** The text area on whose behalf the next research will be done. */
    private PTextArea currentComponent;
    
    public Advisor() {
        setLayout(new BorderLayout());
        add(advicePane, BorderLayout.CENTER);
        //FIXME
        /*
        final JTextField textField = new JTextField();
        textField.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // If we select the text, the researchers will be invoked.
                textField.selectAll();
            }
        });
        registerTextComponent(textField);
        add(textField, BorderLayout.SOUTH);
        */
        setDelay(500);
        new Thread(new Runnable() {
            public void run() {
                addResearcher(JavaResearcher.getSharedInstance());
                addResearcher(new ManPageResearcher());
                addResearcher(new NumberResearcher());
                addResearcher(new RubyDocumentationResearcher());
            }
        }).start();
    }
    
    /** Sets the time to wait before a lookup gets done, in milliseconds. */
    public void setDelay(int ms) {
        timer = new Timer(ms, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (currentComponent != null) {
                    executorService.execute(new Runnable() {
                        public void run() {
                            doResearch();
                        }
                    });
                }
            }
        });
        timer.setRepeats(false);
        timer.start();
    }
    
    public String getLookupString() {
        if (currentComponent == null) {
            return "";
        }
        
        // We give the researchers the selection, if there is one.
        // If there isn't, we give them the current line up to the caret.
        String string = currentComponent.getSelectedText();
        if (string == null) {
            string = "";
        }
        if (string.length() == 0) {
            if (currentComponent instanceof ETextArea == false) {
                return "";
            }
            ETextArea textArea = (ETextArea) currentComponent;
            int dot = textArea.getSelectionStart();
            int lineNumber = textArea.getLineOfOffset(dot);
            int lineStart = textArea.getLineStartOffset(lineNumber);
            string = textArea.getTextBuffer().subSequence(lineStart, dot).toString();
        }
        
        // If the user's selected more than a line, don't tell the researchers.
        if (string.matches(".*\n.*\n")) {
            return "";
        }
        
        // Remove whitespace, because no researcher is going to tell us anything about whitespace.
        return string.trim();
    }
    
    private void doResearch() {
        research(getLookupString());
    }
    
    public void research(String text) {
        // If there's nothing to look at, don't wake the researchers.
        if (text.length() == 0) {
            return;
        }
        
        ETextWindow textWindow = (ETextWindow) SwingUtilities.getAncestorOfClass(ETextWindow.class, currentComponent);
        
        StringBuilder newText = new StringBuilder("<html><head><title></title></head><body bgcolor=#FFFFFF>");
        for (WorkspaceResearcher researcher : researchers) {
            if (textWindow == null || researcher.isSuitable(textWindow)) {
                String result = researcher.research(currentComponent, text);
                if (result != null && result.length() > 0) {
                    newText.append(result);
                }
            }
        }
        newText.append("</body></html>");
        String result = newText.toString();

        // Deliberately ignore the advisors if they're just babbling.
        int lineCount = 0;
        for (int i = 0; i < result.length(); ++i) {
            if (result.charAt(i) == '\n') {
                ++lineCount;
            }
        }
        if (lineCount > 100) {
            result = "(Too much output.)";
        }

        advicePane.setText(result);
    }
    
    public void addResearcher(WorkspaceResearcher researcher) {
        researchers.add(researcher);
    }
    
    /** Registers a text component for advice tips. */
    public void registerTextComponent(PTextArea textArea) {
        if (textArea == null) {
            return;
        }
        unregisterTextComponent(textArea); // Make sure each component is only registered once.
        textArea.addCaretListener(caretWatcher);
    }
    
    /** Unregisters a text component for advice tips. */
    public void unregisterTextComponent(PTextArea textArea) {
        textArea.addCaretListener(caretWatcher);
    }
    
    /** Watches the carets in all the registered text components. */
    private PCaretListener caretWatcher = new PCaretListener() {
        public void caretMoved(PTextArea textArea, int selectionStart, int selectionEnd) {
            currentComponent = textArea;
            timer.restart();
        }
    };
    
    public void linkClicked(String link) {
        // Offer the link to each researcher.
        for (WorkspaceResearcher researcher : researchers) {
            if (researcher.handleLink(link)) {
                return;
            }
        }
        // Hand it on to Edit to work out what to do with it.
        Edit.getInstance().openFile(link);
    }
}
