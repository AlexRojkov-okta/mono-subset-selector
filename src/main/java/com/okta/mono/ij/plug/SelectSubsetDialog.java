package com.okta.mono.ij.plug;

import com.intellij.openapi.module.ModuleDescription;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.util.graph.CachingSemiGraph;
import com.intellij.util.graph.Graph;
import com.intellij.util.graph.GraphGenerator;
import com.intellij.util.graph.InboundSemiGraph;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SelectSubsetDialog extends JDialog {
    private JPanel contentPane;
    private JButton buttonOK;
    private JButton buttonCancel;
    private JPanel content;
    private final ButtonGroup modulesGroup;
    private Project project;
    private ModuleManager moduleManager;

    public SelectSubsetDialog() {
        setContentPane(contentPane);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);

        buttonOK.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        //

        modulesGroup = new ButtonGroup();
        content.setLayout(new BoxLayout(content, 1));

        project = ProjectManager.getInstance().getOpenProjects()[0];

        moduleManager = ModuleManager.getInstance(project);

        for (ModuleDescription description : moduleManager.getAllModuleDescriptions()) {
            final String module = description.getName();

            if (module.matches("runtimes\\.[a-zA-Z-]+\\.web$") || module.contains("foo") || module.contains("bar")) {

                JCheckBox moduleCheckBox = new JCheckBox(module);

                moduleCheckBox.putClientProperty("module", module);

                modulesGroup.add(moduleCheckBox);

                content.add(moduleCheckBox);
            }
        }
    }

    private void onOK() {
        // add your code here\
        Enumeration<AbstractButton> elements = modulesGroup.getElements();
        String module = null;
        while (elements.hasMoreElements()) {
            AbstractButton abstractButton = elements.nextElement();
            if (abstractButton.isSelected()) {
                module = (String) abstractButton.getClientProperty("module");
            }
        }

        if (module != null) {
            loadModule(module);
        }

        dispose();
    }

    private void loadModule(final String module) {
        Optional<ModuleDescription> first = moduleManager.getAllModuleDescriptions().stream().filter(m -> module.equals(m.getName())).findFirst();

        ModuleDescription moduleDescription = first.get();

        Graph<ModuleDescription> graph = buildGraph();

        Iterator<ModuleDescription> in = graph.getIn(moduleDescription);
        List<String> keep = new ArrayList<>();
        keep.add(module);
        keep.add(module.replace(".web", ".api"));
        while (in.hasNext()) {
            keep.add(in.next().getName());
        }

        System.out.println("keep list " + keep);

        List<String> all = moduleManager.getAllModuleDescriptions().stream().map(m -> m.getName()).collect(Collectors.toList());

        List<String> remove = new ArrayList<>();
        for (String candidate : all) {
            if (!keep.contains(candidate)) {
                remove.add(candidate);
            }
        }

        System.out.println("remove list " + remove);

        moduleManager.setUnloadedModules(remove);
    }

    private void onCancel() {
        // add your code here if necessary
        dispose();
    }

    public Graph<ModuleDescription> buildGraph() {
        Collection<ModuleDescription> descriptions = moduleManager.getAllModuleDescriptions();

        Map<String, ModuleDescription> descriptionsMap = descriptions.stream().collect(Collectors.toMap((v) -> v.getName(), v -> v));

        InboundSemiGraph<ModuleDescription> descriptionsGraph = new InboundSemiGraph<>() {
            @Override
            public @NotNull Collection<ModuleDescription> getNodes() {
                return descriptions;
            }

            @Override
            public @NotNull Iterator<ModuleDescription> getIn(ModuleDescription description) {
                return description.getDependencyModuleNames().stream().map(s -> descriptionsMap.get(s)).collect(Collectors.toList()).iterator();
            }
        };

        Graph<ModuleDescription> modulesGraph = GraphGenerator.generate(CachingSemiGraph.cache(descriptionsGraph));

        return modulesGraph;
    }

}
