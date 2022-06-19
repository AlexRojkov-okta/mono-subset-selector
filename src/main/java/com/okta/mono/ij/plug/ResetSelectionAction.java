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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reset the modules to their default state - no modules should remain unloaded after this action complets
 */
public class ResetSelectionAction extends AnAction {
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getData(CommonDataKeys.PROJECT);

        if (SelectSubsetDialog.unloadMode == UnloadMode.IDEA) {
            ModuleManager moduleManager = ModuleManager.getInstance(project);
            moduleManager.setUnloadedModules(Collections.emptyList());
        } else {

            MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);
            List<MavenProject> allProjects = mavenProjectsManager.getProjects();
            Set<MavenProject> nonIgnoredProjects = mavenProjectsManager.getNonIgnoredProjects().stream().collect(Collectors.toSet());
            List<MavenProject> forceUpdateProjects = allProjects.stream().filter((p) -> nonIgnoredProjects.contains(p)).collect(Collectors.toList());
            mavenProjectsManager.setIgnoredFilesPaths(Collections.emptyList());
            mavenProjectsManager.forceUpdateProjects(forceUpdateProjects);
        }
    }

    @Override
    public boolean isDumbAware() {
        return false;
    }
}
