package com.okta.mono.ij.plug;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.module.ModuleDescription;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ResetSelectionAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getData(CommonDataKeys.PROJECT);

        final ModuleManager moduleManager = ModuleManager.getInstance(project);

        moduleManager.setUnloadedModules(Collections.emptyList());

        MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);
        List<String> ignoredFilesPaths = mavenProjectsManager.getIgnoredFilesPaths();
        mavenProjectsManager.removeIgnoredFilesPaths(ignoredFilesPaths);
        List<VirtualFile> projectsFiles = mavenProjectsManager.getProjectsFiles();
        mavenProjectsManager.addManagedFilesOrUnignore(projectsFiles);
        mavenProjectsManager.doSave();
    }

    @Override
    public boolean isDumbAware() {
        return false;
    }
}
