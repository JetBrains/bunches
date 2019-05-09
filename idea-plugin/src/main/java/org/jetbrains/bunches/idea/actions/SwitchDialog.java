package org.jetbrains.bunches.idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.annotation.Nullable;
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

    @Nullable
    @Override
    public JComponent createCenterPanel() {
        return contentPane;
    }
}
