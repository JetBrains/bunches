package org.jetbrains.bunches.ideaPlugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CleanupDialog extends DialogWrapper {
    protected JPanel contentPane;
    protected JComboBox<String> comboCleanup;
    protected JCheckBox checkNoCommit;
    protected JPanel panelCommitMessage;
    protected JTextField textFieldCommitMessage;

    public CleanupDialog(Project project) {
        super(project, false);
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return contentPane;
    }
}
