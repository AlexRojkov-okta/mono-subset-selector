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
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.graph.InboundSemiGraph;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SelectSubsetDialog extends DialogWrapper {
    private final ModuleManager moduleManager;
    private final Collection<ModuleDescription> moduleDescriptions;
    private final Map<String, ModuleDescription> moduleDescriptionMap;
    private TableModel tblMdl;
    private ModuleNameMatcher moduleNameMatcher;

    public SelectSubsetDialog(Project project) {
        super(project);

        setTitle("Select and load subset");

        moduleManager = ModuleManager.getInstance(project);

        moduleDescriptions = moduleManager.getAllModuleDescriptions();

        moduleDescriptionMap = moduleDescriptions.stream().collect(Collectors.toMap((v) -> v.getName(), v -> v));

        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBScrollPane scrollPane = new JBScrollPane();
        System.getProperties().list(System.out);

        final JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(scrollPane, BorderLayout.CENTER);
        //
        final ModuleDescription[] modules = moduleManager.getAllModuleDescriptions().stream().collect(Collectors.toList()).toArray(new ModuleDescription[0]);
        final List<Object[]> data = new ArrayList<>();
        for (int i = 0; i < modules.length; i++) {
            ModuleDescription module = modules[i];
            if (isIncluded(module)) {
                data.add(new Object[]{module, false, false});
            }
        }
        //
        tblMdl = new DefaultTableModel(data.toArray(new Object[][]{{}}), new Object[]{"module", "api", "selenium"});
        TableColumnModel tblCols = new DefaultTableColumnModel();
        {
            // table column for the module
            TableColumn moduleColumn = new TableColumn(0, 100);
            moduleColumn.setPreferredWidth(100);
            moduleColumn.setHeaderValue("Module");
            moduleColumn.setCellRenderer(new ColoredTableCellRenderer() {
                @Override
                protected void customizeCellRenderer(@NotNull JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
                    ModuleDescription module = (ModuleDescription) value;
                    append(module.getName());
                }
            });
            tblCols.addColumn(moduleColumn);
        }
        {
            // table column for api tests
            TableColumn moduleColumn = new TableColumn(1, 3);
            moduleColumn.setPreferredWidth(3);
            moduleColumn.setHeaderValue("Api");
            moduleColumn.setCellRenderer(new BooleanTableCellRenderer());
            moduleColumn.setCellEditor(new BooleanTableCellEditor());
            tblCols.addColumn(moduleColumn);
        }
        {
            // table column for selenium tests
            TableColumn moduleColumn = new TableColumn(2, 3);
            moduleColumn.setPreferredWidth(3);
            moduleColumn.setHeaderValue("Selenium");
            moduleColumn.setCellRenderer(new BooleanTableCellRenderer());
            moduleColumn.setCellEditor(new BooleanTableCellEditor());
            tblCols.addColumn(moduleColumn);
        }

        final JBTable table = new JBTable(tblMdl, tblCols);

        scrollPane.getViewport().add(table);

        return panel;
    }

    private boolean isIncluded(ModuleDescription module) {
        moduleNameMatcher = new ModuleNameMatcher();

        return moduleNameMatcher.isIncluded(module.getName());
    }

    @Override
    protected void doOKAction() {
        ModuleDescription module = null;
        boolean isApi = false;
        boolean isSelenium = false;
        for (int i = 0; i < tblMdl.getRowCount(); i++) {
            isApi = (boolean) tblMdl.getValueAt(i, 1);
            isSelenium = (boolean) tblMdl.getValueAt(i, 2);

            if (isApi || isSelenium) {
                module = (ModuleDescription) tblMdl.getValueAt(i, 0);
                break;
            }
        }

        if (module != null) {
            loadModule(module, isApi, isSelenium);
        }

        super.doOKAction();
    }

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

    private void loadModule(final ModuleDescription module, boolean isApi, boolean isSelenium) {
        Graph<ModuleDescription> graph = buildGraph();

        Set<String> keep = new HashSet<>();

        keep(graph, module, keep);

        if (moduleDescriptionMap.get(module.getName() + ".api") != null) {
            keep(graph, moduleDescriptionMap.get(module.getName() + ".api"), keep);
        }

        if (moduleDescriptionMap.get(module.getName() + ".web") != null) {
            keep(graph, moduleDescriptionMap.get(module.getName() + ".web"), keep);
        }

        if (module.getName().startsWith("runtimes.")) {//test doesn't have runtime
            String baseName = module.getName().substring("runtimes.".length());
            if (isApi) {
                loadApi(graph, keep, baseName);
            }

            if (isSelenium) {
                loadSelenium(graph, keep, baseName);
            }
        }

        System.out.println("keep list " + keep);

        List<String> all = moduleDescriptions.stream().map(m -> m.getName()).collect(Collectors.toList());

        List<String> remove = new ArrayList<>();
        for (String candidate : all) {
            if (!keep.contains(candidate)) {
                remove.add(candidate);
            }
        }

        System.out.println("remove list " + remove);

        moduleManager.setUnloadedModules(remove);
    }

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

    private Graph<ModuleDescription> buildGraph() {
        InboundSemiGraph<ModuleDescription> descriptionsGraph = new InboundSemiGraph<>() {
            @Override
            public @NotNull Collection<ModuleDescription> getNodes() {
                return moduleDescriptions;
            }

            @Override
            public @NotNull Iterator<ModuleDescription> getIn(ModuleDescription description) {
                return description.getDependencyModuleNames().stream().map(s -> moduleDescriptionMap.get(s)).collect(Collectors.toList()).iterator();
            }
        };

        Graph<ModuleDescription> modulesGraph = GraphGenerator.generate(CachingSemiGraph.cache(descriptionsGraph));

        return modulesGraph;
    }


}