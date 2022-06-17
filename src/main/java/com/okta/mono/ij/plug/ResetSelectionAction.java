package com.okta.mono.ij.plug;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class ResetSelectionAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getData(CommonDataKeys.PROJECT);

        final ModuleManager moduleManager = ModuleManager.getInstance(project);

        moduleManager.setUnloadedModules(Collections.emptyList());
    }

    @Override
    public boolean isDumbAware() {
        return false;
    }
}
