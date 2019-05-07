package org.jetbrains.bunches.ideaPlugin;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;

public class SwitchDialog extends DialogWrapper {
    protected JPanel contentPane;
    protected JComboBox<String> comboSwitch;
    protected JCheckBox checkStepByStep;
    protected JCheckBox checkCleanup;
    protected JTextField textFieldCommitMessage;

    public SwitchDialog(Project project) {
        super(project, false);
        init();
    }

    public JComponent createCenterPanel() {
        return contentPane;
    }
}
