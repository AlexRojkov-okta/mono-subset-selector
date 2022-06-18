package com.okta.mono.ij.plug;

import com.intellij.openapi.module.ModuleDescription;
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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Module selection dialog. Here we are filtering out modules based on the prefix 'runtime.'
 * <p>
 * The modules are presented in a table with two check boxes that can select module and facets.
 * <p>
 * isApi and isSelenium are facets. When either is selected the module is added to the list of modules to keep
 */
public class SelectSubsetDialog extends DialogWrapper {
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
    protected @Nullable JComponent createCenterPanel() {
        final JPanel centerPanel = new JPanel();

        // setup ui - a table in a scroll pane. table will contain a short module list.
        JBScrollPane scrollPane = new JBScrollPane();
        System.getProperties().list(System.out);
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
        System.out.println("SelectSubsetDialog.initData project crappies " + projects);
    }
/*

    class Crappy {
        final MavenProject project;

        public Crappy(MavenProject project) {
            this.project = project;
        }

        boolean isChildOfAny(Set<MavenId> projects) {
            MavenProject project = this.project;
            while ((project = mavenProjectsManager.findProject(project.getMavenId())) != null) {
                if (projects.contains(project.getMavenId())) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public String toString() {
            return "Crappy [" + project + ']';
        }
    }
*/

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
     * recursively add module and it's dependencies to the keep list
     */
    private void keep(Graph<ModuleDescription> graph, ModuleDescription module, Set<String> keep) {
        if (!keep.add(module.getName())) {
            return;
        }

        Iterator<ModuleDescription> in = graph.getIn(module);
        while (in.hasNext()) {
            ModuleDescription d = in.next();
            keep(graph, d, keep);
        }
    }

    /**
     * Build unload list of modules and unload them
     */
    private void load(final MavenProject project, boolean isApiTest, boolean isSelenium) {
        final Graph<MavenProject> projectGraph = GraphAlgorithms.getInstance().invertEdgeDirections(buildProjectGraph());

        Set<MavenProject> loadList = new HashSet<>();
        loadList.add(project);

        Optional<MavenProject> api = projects.stream().filter(p -> p.getMavenId().getArtifactId().equals(project.getMavenId().getArtifactId() + ".api")).findFirst();
        if (api.isPresent()) {
            loadList.add(api.get());
        }

        Optional<MavenProject> web = projects.stream().filter(p -> p.getMavenId().getArtifactId().equals(project.getMavenId().getArtifactId() + ".web")).findFirst();
        if (web.isPresent()) {
            loadList.add(web.get());
        }

        /*
        final String baseName = project.getMavenId().getArtifactId().substring("runtimes.".length());

        if (isApiTest) {
            "tests.api-" + baseName + ".client-test";
            projects.stream().filter(p->p.getMavenId().getArtifactId().equals())
        }
        if (project.getName().startsWith("runtimes.")) {//test doesn't have runtime
            String baseName = project.getName().substring("runtimes.".length());
            if (isApiTest) {
                loadApi(projectGraph, loadList, baseName);
            }

            if (isSelenium) {
                loadSelenium(projectGraph, loadList, baseName);
            }
        }
        */

        // MavenProject doesn't override equals/hashCode - relying on identity
        Set<MavenProject> collect = new HashSet<>();
        for (MavenProject p : loadList) {
            GraphAlgorithms.getInstance().collectOutsRecursively(projectGraph, p, collect);
        }

        

        System.out.println("load list " + collect);
/**
 List<MavenProject> rootProjects = mavenProjectsManager.getRootProjects();

 System.out.println("root-projects " + rootProjects);

 for (ModuleDescription moduleDescription : moduleDescriptions) {
 if (moduleDescription instanceof UnloadedModuleDescription) {
 List<VirtualFilePointer> contentRoots = ((UnloadedModuleDescription) moduleDescription).getContentRoots();
 List<VirtualFile> roots = contentRoots.stream().map(p -> p.getFile()).collect(Collectors.toList());
 mavenProjectsManager.addManagedFilesOrUnignore(roots);
 }
 }
 */
    }
/*

    private void loadApi(Graph<ModuleDescription> graph, Set<String> keep, String baseName) {
        String name = "tests.api-" + baseName + ".client-test";
        if (moduleDescriptionMap.get(name) != null) {
            keep(graph, moduleDescriptionMap.get(name), keep);
        }
    }

    private void loadSelenium(Graph<ModuleDescription> graph, Set<String> keep, String baseName) {
        String name = "tests.selenium-" + baseName;

        if (moduleDescriptionMap.get(name) != null) {
            keep(graph, moduleDescriptionMap.get(name), keep);
        }

        name = name + ".client-test";

        if (moduleDescriptionMap.get(name) != null) {
            keep(graph, moduleDescriptionMap.get(name), keep);
        }
    }
*/

    private Graph<MavenProject> buildProjectGraph() {
        InboundSemiGraph<MavenProject> descriptionsGraph = new InboundSemiGraph<>() {
            @Override
            public @NotNull Collection<MavenProject> getNodes() {
                return projects;
            }

            @Override
            public @NotNull Iterator<MavenProject> getIn(MavenProject project) {
                System.out.println(" project " + project + " -> " + project.getDependencies());
                List<MavenProject> dependencies = project.getDependencies().stream().filter(a -> projectIds.contains(a.getMavenId())).map(a -> mavenProjectsManager.findProject(a)).collect(Collectors.toList());

                System.out.println("SelectSubsetDialog.getIn " + dependencies);

                return dependencies.iterator();
            }
        };

        Graph<MavenProject> projectGraph = GraphGenerator.generate(CachingSemiGraph.cache(descriptionsGraph));

        return projectGraph;
    }
}
