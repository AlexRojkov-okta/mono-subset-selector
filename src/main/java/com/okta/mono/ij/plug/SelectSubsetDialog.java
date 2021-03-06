package com.okta.mono.ij.plug;

import com.intellij.openapi.diagnostic.Logger;
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
import org.jetbrains.idea.maven.model.MavenCoordinate;
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
import java.util.Map;
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
    private static final Logger LOG = Logger.getInstance(SelectSubsetDialog.class);

    public static final String RUNTIMES_PREFIX = "runtimes.";

    //data
    private final ModuleManager moduleManager;
    private final MavenProjectsManager mavenProjectsManager;
    private List<MavenProject> rootProjects; //root projects in workspace
    private Set<MavenId> rootProjectIds; //MavenIds of root projects
    private List<MavenProject> projects; //all projects in workspace - same list as in the Maven plugin
    private Map<MavenId, MavenProject> projectsMap;
    //matches names on runtime.* prefix
    private ModuleNameMatcher moduleNameMatcher;
    //ui - user module / api / selenium selection
    private TableModel tableModel;
    private JBTable table;

    public SelectSubsetDialog(Project project) {
        super(project);

        setTitle("Select and load subset");

        moduleManager = ModuleManager.getInstance(project);

        mavenProjectsManager = MavenProjectsManager.getInstance(project);

        moduleNameMatcher = new ModuleNameMatcher();

        initData();

        //initializes DialogWrapper; method super.init() invokes createCenterPanel below which sets up the UI
        init();
    }

    void initData() {
        rootProjects = mavenProjectsManager.getRootProjects();
        rootProjectIds = rootProjects.stream().map(MavenProject::getMavenId).collect(Collectors.toSet());

        projects = mavenProjectsManager.getProjects();
        projectsMap = projects.stream().collect(Collectors.toMap(MavenProject::getMavenId, p -> p));

        LOG.warn("SelectSubsetDialog.initData root projects " + rootProjects);
        LOG.warn("SelectSubsetDialog.initData projects " + projects);
    }

    @Override
    protected @Nullable
    JComponent createCenterPanel() {
        final JPanel centerPanel = new JPanel();

        // setup ui - a table in a scroll pane - table will contain a short list of modules
        JBScrollPane scrollPane = new JBScrollPane();

        centerPanel.setLayout(new BorderLayout());
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // prepare data for table model
        // each Object[] is a tuple of {MavenProject, boolean, boolean}
        List<Object[]> data = projects.stream().filter(p -> moduleNameMatcher.matches(p.getMavenId().getArtifactId()))
                .map(p -> new Object[]{p, isApiPresent(p.getMavenId()), isSeleniumPresent(p.getMavenId())})
                .collect(Collectors.toList());

        //  table has 3 columns - 1) readonly project 2) editable boolean column called 'api' 3) editable boolean column called selenium
        tableModel = new DefaultTableModel(data.toArray(new Object[][]{{}}), new Object[3]);
        final TableColumnModel tblCols = new DefaultTableColumnModel();
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

        table = new JBTable(tableModel, tblCols);
        table.setRowSelectionAllowed(true);
        scrollPane.getViewport().add(table);

        return centerPanel;
    }

    @Override
    protected void doOKAction() {
        final int selectedRow = table.getSelectedRow();
        final MavenProject project = (MavenProject) tableModel.getValueAt(selectedRow, 0);
        final boolean isApi = (boolean) tableModel.getValueAt(selectedRow, 1);
        final boolean isSelenium = (boolean) tableModel.getValueAt(selectedRow, 2);

        // we are only loading one module at this point - the first we encounter when checking values for facets ( api and selenium )
        if (project != null) {
            load(project, isApi, isSelenium);
        }

        super.doOKAction();
    }

    private boolean isApiPresent(MavenCoordinate mavenId) {
        final String artifactId = mavenId.getArtifactId();
        if (!artifactId.startsWith(RUNTIMES_PREFIX)) {
            return false;
        }

        final String name = makeApiArtifactId(artifactId);

        return projects.stream().filter(p -> p.getMavenId().getArtifactId().equals(name))
                .findFirst().isPresent();
    }

    private boolean isSeleniumPresent(MavenCoordinate mavenId) {
        final String artifactId = mavenId.getArtifactId();
        if (!artifactId.startsWith(RUNTIMES_PREFIX)) {
            return false;
        }

        final String name = makeSeleniumArtifactId(artifactId);

        return projects.stream().filter(p -> p.getMavenId().getArtifactId().equals(name))
                .findFirst().isPresent();
    }

    private String makeApiArtifactId(String artifactId) {
        final String baseName = artifactId.substring(RUNTIMES_PREFIX.length());

        final String name = "tests.api-" + baseName + ".client-test";

        return name;
    }

    private String makeSeleniumArtifactId(String artifactId) {
        final String baseName = artifactId.substring(RUNTIMES_PREFIX.length());

        final String name = "tests.selenium-" + baseName + ".client-test";

        return name;
    }

    /**
     * Build unload list of modules and unload them
     */
    private void load(final MavenProject project, boolean isApiTest, boolean isSelenium) {
        final Set<MavenProject> selected = new HashSet<>();
        selected.add(project);

        final String artifactId = project.getMavenId().getArtifactId(); // artifact id of a subset e.g. runtimes.login

        final Optional<MavenProject> api = projects.stream()
                .filter(p -> p.getMavenId().getArtifactId().equals(artifactId + ".api"))
                .findFirst();
        if (api.isPresent()) {
            selected.add(api.get());
        }

        final Optional<MavenProject> web = projects.stream()
                .filter(p -> p.getMavenId().getArtifactId()
                        .equals(artifactId + ".web")).findFirst();
        if (web.isPresent()) {
            selected.add(web.get());
        }

        // the if is for a test project that does not have proper okta-core structure
        if (artifactId.startsWith(RUNTIMES_PREFIX)) {
            if (isApiTest) {
                final String name = makeApiArtifactId(artifactId);
                projects.stream().filter(p -> p.getMavenId().getArtifactId().equals(name))
                        .findFirst()
                        .ifPresent(p -> selected.add(p));
            }
            if (isSelenium) {
                final String name = makeSeleniumArtifactId(artifactId);

                projects.stream().filter(p -> p.getMavenId().getArtifactId().equals(name))
                        .findFirst()
                        .ifPresent((p) -> selected.add(p));
            }
        }

        // MavenProject doesn't override equals/hashCode - relying on identity is sufficient for this use
        final Set<MavenProject> dependencies = new HashSet<>();
        final Graph<MavenProject> projectGraph = GraphAlgorithms.getInstance().invertEdgeDirections(buildProjectGraph());
        for (MavenProject p : selected) {
            // collectOutRecursively adds each p in selected
            GraphAlgorithms.getInstance().collectOutsRecursively(projectGraph, p, dependencies);
        }
        // trace to the root project and add all nodes in path to root project

        final Set<MavenProject> parents = new HashSet<>();
        for (MavenProject p : dependencies) {
            while ((p = projectsMap.get(p.getParentId())) != null) {
                if (parents.contains(p)) {
                    break; // we already processed this p
                }

                parents.add(p);

                if (rootProjectIds.contains(p.getMavenId())) {
                    break; //we found root project
                }
            }
        }

        dependencies.addAll(parents);

        LOG.warn(String.format("keep list for %s has %d elements -> %s", project, dependencies.size(),
                dependencies.stream().map((p) -> p.getMavenId().getArtifactId()).collect(Collectors.toList())
        ));

        final List<String> ignoreFiles = new ArrayList<>();
        final List<String> ignoreModules = new ArrayList<>();
        for (final MavenProject p : projects) {
            if (!dependencies.contains(p)) {
                ignoreFiles.add(p.getFile().getPath());
                ignoreModules.add(p.getMavenId().getArtifactId());
            }
        }

        LOG.warn(String.format("ignore list for %s has %d elements", project, ignoreFiles.size()));

        if (unloadMode() == UnloadMode.MAVEN) {
            final Set<String> ignoredFilesPaths = new HashSet<>(mavenProjectsManager.getIgnoredFilesPaths());

            mavenProjectsManager.setIgnoredFilesPaths(ignoreFiles);
            if (forceUpdate()) {
                //lets force update ignored projects that now should be un-ignored
                final List<MavenProject> forceUpdate = dependencies.stream()
                        .filter(p -> ignoredFilesPaths.contains(p.getFile().getPath())).collect(Collectors.toList());

                LOG.warn(String.format("force update list for %s has %d elements -> %s", project, forceUpdate.size(),
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
                List<MavenProject> dependencies = project.getDependencies().stream().filter(a -> projectsMap.containsKey(a.getMavenId())).map(a -> mavenProjectsManager.findProject(a)).collect(Collectors.toList());

                LOG.warn(String.format("project %s depends on %s", project.getMavenId().getArtifactId(), dependencies.stream().map(p -> p.getMavenId().getArtifactId()).collect(Collectors.toList())));

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