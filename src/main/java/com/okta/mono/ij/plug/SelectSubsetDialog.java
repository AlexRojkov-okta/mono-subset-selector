package com.okta.mono.ij.plug;

import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.BooleanTableCellEditor;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphAlgorithms;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.graph.InboundSemiGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.*;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Module selection dialog. Here we are filtering out modules based on the prefix 'runtime.'
 * <p>
 * The modules are presented in a table with two check boxes that can select module and facets.
 * <p>
 * isApi and isSelenium are facets. When either is selected the module is added to the list of modules to keep
 */
public class SelectSubsetDialog extends DialogWrapper {
    public static final String RUNTIMES_PREFIX = "runtimes.";

    //data
    private final ModuleManager moduleManager;
    private final MavenProjectsManager mavenProjectsManager;
    private List<MavenProject> rootProjects; //root projects in workspace
    private Set<MavenId> rootProjectIds; //MavenIds of root projects
    private List<MavenProject> projects; //all projects in workspace - same list as in the Maven plugin
    private Set<MavenId> projectIds; //MavenIds of all projects from projects list
    //matches names on runtime.* prefix
    private ModuleNameMatcher moduleNameMatcher;
    //ui - user module / api / selenium selection
    private TableModel tableModel;

    public SelectSubsetDialog(Project project) {
        super(project);

        setTitle("Select and load subset");

        moduleManager = ModuleManager.getInstance(project);

        mavenProjectsManager = MavenProjectsManager.getInstance(project);

        moduleNameMatcher = new ModuleNameMatcher();

        initData();

        init();
    }

    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        final JPanel centerPanel = new JPanel();

        // setup ui - a table in a scroll pane. table will contain a short module list.
        JBScrollPane scrollPane = new JBScrollPane();

        centerPanel.setLayout(new BorderLayout());
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // prepare data for table model
        // each Object[] is a tuple of {MavenProject, boolean, boolean}
        List<Object[]> data = projects.stream().filter(p -> moduleNameMatcher.isIncluded(p.getMavenId().getArtifactId()))
                .map(p -> new Object[]{p, false, false})
                .collect(Collectors.toList());

        //  table has 3 columns - 1) readonly project 2) editable boolean column called 'api' 3) editable boolean column called selenium
        tableModel = new DefaultTableModel(data.toArray(new Object[][]{{}}), new Object[]{"module", "api", "selenium"});
        TableColumnModel tblCols = new DefaultTableColumnModel();
        {
            // table column for the module name; readonly
            TableColumn moduleColumn = new TableColumn(0, 100);
            moduleColumn.setPreferredWidth(100);
            moduleColumn.setHeaderValue("Module");
            moduleColumn.setCellRenderer(new ColoredTableCellRenderer() {
                @Override
                protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
                    MavenProject project = (MavenProject) value;
                    append(project.getDisplayName());
                }
            });
            tblCols.addColumn(moduleColumn);
        }
        {
            // table column for api facet - editable
            TableColumn moduleColumn = new TableColumn(1, 3);
            moduleColumn.setPreferredWidth(3);
            moduleColumn.setHeaderValue("Api");
            moduleColumn.setCellRenderer(new BooleanTableCellRenderer());
            moduleColumn.setCellEditor(new BooleanTableCellEditor());
            tblCols.addColumn(moduleColumn);
        }
        {
            // table column for selenium facet - editable
            TableColumn moduleColumn = new TableColumn(2, 3);
            moduleColumn.setPreferredWidth(3);
            moduleColumn.setHeaderValue("Selenium");
            moduleColumn.setCellRenderer(new BooleanTableCellRenderer());
            moduleColumn.setCellEditor(new BooleanTableCellEditor());
            tblCols.addColumn(moduleColumn);
        }

        final JBTable table = new JBTable(tableModel, tblCols);

        scrollPane.getViewport().add(table);

        return centerPanel;
    }

    void initData() {
        rootProjects = mavenProjectsManager.getRootProjects();
        rootProjectIds = rootProjects.stream().map(MavenProject::getMavenId).collect(Collectors.toSet());

        projects = mavenProjectsManager.getProjects();
        projectIds = projects.stream().map(MavenProject::getMavenId).collect(Collectors.toSet());

        System.out.println("SelectSubsetDialog.initData root projects " + rootProjects);
        System.out.println("SelectSubsetDialog.initData project " + projects);
    }

    @Override
    protected void doOKAction() {
        MavenProject project = null;
        boolean isApi = false;
        boolean isSelenium = false;
        // find one module with either api or selenium value of 'true'
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            isApi = (boolean) tableModel.getValueAt(i, 1);
            isSelenium = (boolean) tableModel.getValueAt(i, 2);

            if (isApi || isSelenium) {
                project = (MavenProject) tableModel.getValueAt(i, 0);

                break;
            }
        }

        // we are only loading one module at this point - the first we encounter when checking values for facets ( api and selenium )
        if (project != null) {
            load(project, isApi, isSelenium);
        }

        super.doOKAction();
    }

    /**
     * Build unload list of modules and unload them
     */
    private void load(final MavenProject project, boolean isApiTest, boolean isSelenium) {
        final Set<MavenProject> selected = new HashSet<>();
        selected.add(project);

        Optional<MavenProject> api = projects.stream().filter(p -> p.getMavenId().getArtifactId().equals(project.getMavenId().getArtifactId() + ".api")).findFirst();
        if (api.isPresent()) {
            selected.add(api.get());
        }

        Optional<MavenProject> web = projects.stream().filter(p -> p.getMavenId().getArtifactId().equals(project.getMavenId().getArtifactId() + ".web")).findFirst();
        if (web.isPresent()) {
            selected.add(web.get());
        }

        // the if is for a test project that does not have proper okta-core structure
        if (project.getMavenId().getArtifactId().startsWith(RUNTIMES_PREFIX)) {
            final String baseName = project.getMavenId().getArtifactId().substring("runtimes.".length());
            if (isApiTest) {
                final String name = "tests.api-" + baseName + ".client-test";
                projects.stream().filter(p -> p.getMavenId().getArtifactId().equals(name))
                        .findFirst()
                        .ifPresent((p) -> {
                            selected.add(p);
                        });
            }
            if (isSelenium) {
                final String name = "tests.selenium-" + baseName + ".client-test";
                projects.stream().filter(p -> p.getMavenId().getArtifactId().equals(name))
                        .findFirst()
                        .ifPresent((p) -> {
                            selected.add(p);
                        });
            }
        }

        // MavenProject doesn't override equals/hashCode - relying on identity
        final Set<MavenProject> selectedWithDependencies = new HashSet<>();
        final Graph<MavenProject> projectGraph = GraphAlgorithms.getInstance().invertEdgeDirections(buildProjectGraph());
        for (MavenProject p : selected) {
            GraphAlgorithms.getInstance().collectOutsRecursively(projectGraph, p, selectedWithDependencies);
        }

        final Set<MavenProject> parents = new HashSet<>();
        for (MavenProject p : selectedWithDependencies) {
            MavenProject parent = p;

            while ((parent = mavenProjectsManager.findProject(parent.getParentId())) != null) {
                if (parents.contains(parent)) {
                    break;
                }

                parents.add(parent);

                if (rootProjectIds.contains(parent.getMavenId())) {
                    break;
                }
            }
        }

        selectedWithDependencies.addAll(parents);

        System.out.println(String.format("keep list for %s has %d elements -> %s", project, selectedWithDependencies.size(),
                selectedWithDependencies.stream().map((p) -> p.getMavenId().getArtifactId()).collect(Collectors.toList())
        ));

        List<String> ignoreFiles = new ArrayList<>();
        List<String> ignoreModules = new ArrayList<>();
        for (MavenProject p : projects) {
            if (!selectedWithDependencies.contains(p)) {
                ignoreFiles.add(p.getFile().getPath());
                ignoreModules.add(p.getMavenId().getArtifactId());
            }
        }

        System.out.println(String.format("ignore list for %s has %d elements", project, ignoreFiles.size()));

        if (unloadMode() == UnloadMode.MAVEN) {
            Set<String> ignoredFilesPaths = new HashSet<>(mavenProjectsManager.getIgnoredFilesPaths());

            mavenProjectsManager.setIgnoredFilesPaths(ignoreFiles);
            if (forceUpdate()) {
                //lets force update ignored projects that now should be un-ignored
                List<MavenProject> forceUpdate = selectedWithDependencies.stream().filter(p -> ignoredFilesPaths.contains(p.getFile().getPath())).collect(Collectors.toList());

                System.out.println(String.format("force update list for %s has %d elements -> %s", project, forceUpdate.size(),
                        forceUpdate.stream().map((p) -> p.getMavenId().getArtifactId()).collect(Collectors.toList())
                ));
                //Q for idea: do we want to force update projects or setting ignore list is sufficient?
                mavenProjectsManager.forceUpdateProjects(forceUpdate);
            }
            //
        } else {
            moduleManager.setUnloadedModules(ignoreModules);
        }
    }

    private Graph<MavenProject> buildProjectGraph() {
        InboundSemiGraph<MavenProject> descriptionsGraph = new InboundSemiGraph<>() {
            @Override
            public @NotNull
            Collection<MavenProject> getNodes() {
                return projects;
            }

            @Override
            public @NotNull
            Iterator<MavenProject> getIn(MavenProject project) {
                List<MavenProject> dependencies = project.getDependencies().stream().filter(a -> projectIds.contains(a.getMavenId())).map(a -> mavenProjectsManager.findProject(a)).collect(Collectors.toList());

                System.out.println(String.format("project %s depends on %s", project.getMavenId().getArtifactId(), dependencies.stream().map(p->p.getMavenId().getArtifactId()).collect(Collectors.toList())));

                return dependencies.iterator();
            }
        };

        Graph<MavenProject> projectGraph = GraphGenerator.generate(CachingSemiGraph.cache(descriptionsGraph));

        return projectGraph;
    }

    public static UnloadMode unloadMode() {
        return new File("/tmp/subset-unload-idea").exists() ? UnloadMode.IDEA : UnloadMode.MAVEN;
    }

    public static boolean forceUpdate() {
        return new File("/tmp/subset-force-update").exists();
    }
}

enum UnloadMode {
    MAVEN,
    IDEA
}