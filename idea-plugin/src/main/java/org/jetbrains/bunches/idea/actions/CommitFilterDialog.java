package org.jetbrains.bunches.idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class CommitFilterDialog extends DialogWrapper {
    protected JCheckBox amountCheckBox;
    protected JCheckBox dateCheckBox;
    protected JComboBox dateTypeComboBox;
    protected JPanel contentPane;
    protected JSpinner amountValue;
    protected JSpinner dateValue;

    public CommitFilterDialog(Project project) {
        super(project, false);
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return contentPane;
    }
}
