package com.rap.generator.ui.widgets;

import com.rap.generator.model.EntityDefinition;
import com.rap.generator.model.RapApplication;

import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import java.util.function.Consumer;

public class EntityTreeViewer {

    private TreeViewer viewer;
    private RapApplication model;
    private Consumer<EntityDefinition> selectionListener;

    public EntityTreeViewer(Composite parent, RapApplication model) {
        this.model = model;

        viewer = new TreeViewer(parent, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL);
        viewer.setContentProvider(new EntityContentProvider());
        viewer.setLabelProvider(new EntityLabelProvider());
        viewer.setInput(model);

        viewer.addSelectionChangedListener(event -> {
            IStructuredSelection selection = (IStructuredSelection) event.getSelection();
            EntityDefinition entity = (EntityDefinition) selection.getFirstElement();
            if (selectionListener != null && entity != null) {
                selectionListener.accept(entity);
            }
        });
    }

    public Control getControl() {
        return viewer.getTree();
    }

    public void refresh() {
        viewer.refresh();
        viewer.expandAll();
    }

    public void addSelectionListener(Consumer<EntityDefinition> listener) {
        this.selectionListener = listener;
    }

    public EntityDefinition getSelectedEntity() {
        IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
        return (EntityDefinition) selection.getFirstElement();
    }

    // Content provider for entity tree
    private class EntityContentProvider implements ITreeContentProvider {

        @Override
        public Object[] getElements(Object inputElement) {
            // Return only root entities at the top level
            RapApplication app = (RapApplication) inputElement;
            return app.getEntities().stream()
                .filter(EntityDefinition::isRoot)
                .toArray();
        }

        @Override
        public Object[] getChildren(Object parentElement) {
            EntityDefinition parent = (EntityDefinition) parentElement;
            return parent.getChildIds().stream()
                .map(id -> model.getEntityById(id).orElse(null))
                .filter(e -> e != null)
                .toArray();
        }

        @Override
        public Object getParent(Object element) {
            EntityDefinition entity = (EntityDefinition) element;
            if (entity.getParentId() != null) {
                return model.getEntityById(entity.getParentId()).orElse(null);
            }
            return null;
        }

        @Override
        public boolean hasChildren(Object element) {
            EntityDefinition entity = (EntityDefinition) element;
            return !entity.getChildIds().isEmpty();
        }
    }

    // Label provider
    private class EntityLabelProvider extends LabelProvider {
        @Override
        public String getText(Object element) {
            EntityDefinition entity = (EntityDefinition) element;
            String label = entity.getName();
            if (entity.isRoot()) {
                label += " [root]";
            }
            return label;
        }
    }
}
