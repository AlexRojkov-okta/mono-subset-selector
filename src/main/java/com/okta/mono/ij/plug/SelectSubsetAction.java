package com.okta.mono.ij.plug;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class SelectSubsetAction extends com.intellij.openapi.actionSystem.AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        SelectSubsetDialog dialog = new SelectSubsetDialog();
        dialog.pack();
        dialog.setLocation(200, 200);
        dialog.setVisible(true);
    }

    @Override
    public boolean isDumbAware() {
        return false;
    }
}
