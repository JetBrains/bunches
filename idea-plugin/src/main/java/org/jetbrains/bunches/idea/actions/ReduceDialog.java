package org.jetbrains.bunches.idea.actions;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.*;

public class ReduceDialog extends DialogWrapper {
    protected JPanel contentPane;
    protected JList<String> filesList;
    protected JLabel label;
    protected JCheckBox checkCommit;

    protected ReduceDialog(@Nullable Project project) {
        super(project);
        init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return contentPane;
    }
}
