package com.okta.mono.ij.plug;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class SelectSubsetAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getData(CommonDataKeys.PROJECT);

        SelectSubsetDialog dialog = new SelectSubsetDialog(project);

        dialog.show();
    }

    @Override
    public boolean isDumbAware() {
        return false;
    }
}