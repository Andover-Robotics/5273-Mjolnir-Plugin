package com.plugin;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextBrowseFolderListener;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

class GetTemplate extends DialogWrapper {
    public final TextFieldWithBrowseButton pathChooser = new TextFieldWithBrowseButton();
    public final TextFieldWithBrowseButton templateChooser = new TextFieldWithBrowseButton();
    public GetTemplate() {
        super(true);
        setTitle("Use Template");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        // createSingleFileDescriptor / createSingleFolderDescriptor are considered deprecated / obsolete in IDEA versions starting from 251.23536.24
        // However, the preferred method is not available for IDEA versions below it, so there's not really a better way other than dropping support for those versions
        // which is not really a good idea since the FTC SDK still works with those versions
        pathChooser.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFolderDescriptor()));
        templateChooser.addBrowseFolderListener(new TextBrowseFolderListener(FileChooserDescriptorFactory.createSingleFileDescriptor()));
        JPanel pathChoose = new JPanel();
        pathChoose.add(new JLabel("Choose the base directory for the template:"));
        pathChoose.add(pathChooser);
        JPanel templateChoose = new JPanel();
        templateChoose.add(new JLabel("Select the template: "));
        templateChoose.add(templateChooser);
        panel.add(pathChoose);
        panel.add(templateChoose);
        return panel;
    }
}

class ParseNode {
    public final int id;
    public ParseNode(int _id) {
        id = _id;
    }
}

class AddParseNode extends ParseNode {
    public final String file;
    public final String content;
    public AddParseNode(String f, String c) {
        super(0);
        file = f;
        content = c;
    }
}

class ParseException extends Exception {
    public final int idx;
    public ParseException(int i, String message) {
        super(message);
        idx = i;
    }
}

class TemplateParser {
    private final String WHITESPACE = " \t";
    private final String source;
    private int idx = 0;
    public TemplateParser(String src) {
        source = src;
    }

    private String current() {
        return String.valueOf(source.charAt(idx));
    }

    private void skipSpace() {
        while (idx < source.length() && WHITESPACE.contains(current())) idx++;
    }

    private boolean ended() {
        return idx >= source.length();
    }

    private boolean take(String seq) {
        if (source.startsWith(seq, idx)) {
            idx += seq.length();
            skipSpace();
            return true;
        }
        return false;
    }

    private void require(String seq) throws ParseException {
        if (!take(seq)) throw new ParseException(idx, "Expected '" + seq + "'");
    }

    private String takeUntilSpace() {
        StringBuilder res = new StringBuilder();
        while (idx < source.length() && !WHITESPACE.contains(current())) {
            res.append(current());
            idx++;
        }
        skipSpace();
        return res.toString();
    }

    private String takeUntilEnd() throws ParseException {
        StringBuilder res = new StringBuilder();
        while (!source.startsWith("\nEndFileContent\n", idx)) {
            if (idx >= source.length()) throw new ParseException(idx, "Expected end of file content");
            res.append(current());
            idx++;
        }
        idx += "\nEndFileContent\n".length();
        skipSpace();
        return res.toString();
    }

    public ArrayList<ParseNode> parse() throws ParseException {
        ArrayList<ParseNode> parseNodes = new ArrayList<>();
        skipSpace();
        while (!ended()) {
            if (take("Add")) {
                String name = takeUntilSpace();
                require("BeginFileContent\n");
                String content = takeUntilEnd();
                parseNodes.add(new AddParseNode(name, content));
            } else throw new ParseException(idx, "Expected an Add node");
        }
        return parseNodes;
    }
}

public class UseTemplate extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project proj = e.getProject();
        if (proj == null) return;
        GetTemplate getTemplate = new GetTemplate();
        boolean result = getTemplate.showAndGet();
        if (!result) return;
        String path = getTemplate.templateChooser.getText();
        String content;
        try {
            content = Files.readString(Path.of(path)).replaceAll("\\r\\n?", "\n");
        } catch (IOException ex) {
            Messages.showErrorDialog("Failed to read template file: " + ex.getLocalizedMessage(), "Failed to Use Template");
            return;
        }

        ArrayList<ParseNode> parseNodes;
        try {
            parseNodes = new TemplateParser(content).parse();
        } catch (ParseException ex) {
            Messages.showErrorDialog("Failed to parse template at character " + ex.idx + ": " + ex.getMessage(), "Failed to Use Template");
            return;
        }

        String absPath = getTemplate.pathChooser.getText();
        for (ParseNode node: parseNodes) {
            if (node.id == 0) {
                AddParseNode n = (AddParseNode) node;
                try {
                    Files.writeString(Path.of(absPath, n.file), n.content);
                } catch (IOException ex) {
                    // do nothing
                }
            }
        }
    }
}
