package com.cwdil.archi.genai.ui.explain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;

import com.archimatetool.editor.diagram.DiagramEditorInput;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelConnection;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.IDiagramModelObject;

public class ExplainContextBuilder {

    private static final int MAX_LIST_ITEMS = 30;
    private static final int MAX_REL_ITEMS = 30;

    public ExplainContext buildContext(IWorkbenchPage page, IWorkbenchPart activePart, ISelection selection) {
        IDiagramModel diagram = getActiveDiagram(activePart, page);
        if(diagram == null && selection == null) {
            return null;
        }

        ExplainContext context = new ExplainContext();
        if(diagram != null) {
            context.viewName = safeValue(diagram.getName(), "Untitled view");
            context.viewType = diagram.eClass().getName();
        }
        else {
            context.viewName = "No active view";
            context.viewType = "Unknown";
        }

        List<String> selectedItems = collectSelectionItems(selection);
        context.selectedItems.addAll(selectedItems);
        context.selectedCount = getSelectionCount(selection);
        context.hasSelection = !selectedItems.isEmpty();

        if(diagram != null && !context.hasSelection) {
            collectViewContext(diagram, context);
        }

        return context;
    }

    private IDiagramModel getActiveDiagram(IWorkbenchPart part, IWorkbenchPage page) {
        if(part != null) {
            IDiagramModel model = part.getAdapter(IDiagramModel.class);
            if(model != null) {
                return model;
            }
        }

        IEditorPart editor = page != null ? page.getActiveEditor() : null;
        if(editor != null) {
            IEditorInput input = editor.getEditorInput();
            if(input instanceof DiagramEditorInput dei) {
                return dei.getDiagramModel();
            }
        }

        return null;
    }

    private int getSelectionCount(ISelection selection) {
        if(selection instanceof IStructuredSelection structured) {
            return structured.size();
        }
        return selection != null && !selection.isEmpty() ? 1 : 0;
    }

    private List<String> collectSelectionItems(ISelection selection) {
        List<String> items = new ArrayList<>();
        if(selection instanceof IStructuredSelection structured) {
            for(Object element : structured.toArray()) {
                String item = describeSelectionItem(element);
                if(item != null && !item.isBlank()) {
                    items.add(item);
                }
            }
        }
        return items;
    }

    private String describeSelectionItem(Object element) {
        if(element instanceof IDiagramModelArchimateObject archiObj) {
            IArchimateElement archiElement = archiObj.getArchimateElement();
            return describeArchimateElement(archiElement);
        }
        if(element instanceof IDiagramModelArchimateConnection connection) {
            return describeRelationship(connection.getArchimateRelationship(), connection.getSource(), connection.getTarget());
        }
        if(element instanceof IArchimateRelationship relationship) {
            return describeRelationship(relationship, null, null);
        }
        if(element instanceof IArchimateElement archiElement) {
            return describeArchimateElement(archiElement);
        }
        if(element instanceof IDiagramModelNote note) {
            return "Note: " + compact(note.getContent());
        }
        if(element instanceof IDiagramModelGroup group) {
            return "Group: " + safeValue(group.getName(), "(unnamed)");
        }
        if(element instanceof IDiagramModelObject dmo) {
            return dmo.eClass().getName();
        }
        return null;
    }

    private String describeArchimateElement(IArchimateElement element) {
        if(element == null) {
            return null;
        }
        String name = safeValue(element.getName(), "(unnamed)");
        String type = element.eClass().getName();
        String doc = compact(element.getDocumentation());
        if(doc.isBlank()) {
            return type + ": " + name;
        }
        return type + ": " + name + " | " + doc;
    }

    private String describeRelationship(IArchimateRelationship relationship, Object source, Object target) {
        if(relationship == null) {
            return null;
        }
        String type = relationship.eClass().getName();
        String name = safeValue(relationship.getName(), "(unnamed)");
        String from = describeConnectionEnd(source, relationship.getSource());
        String to = describeConnectionEnd(target, relationship.getTarget());
        if(from != null && to != null) {
            return type + ": " + from + " -> " + to + " (" + name + ")";
        }
        return type + ": " + name;
    }

    private String describeConnectionEnd(Object diagramEnd, IArchimateConcept modelEnd) {
        if(diagramEnd instanceof IDiagramModelArchimateObject archiObj) {
            return safeValue(archiObj.getArchimateElement().getName(), "(unnamed)");
        }
        if(modelEnd instanceof IArchimateElement element) {
            return safeValue(element.getName(), "(unnamed)");
        }
        return null;
    }

    private void collectViewContext(IDiagramModel diagram, ExplainContext context) {
        List<IDiagramModelObject> objects = new ArrayList<>();
        collectDiagramObjects(diagram, objects);

        Set<String> elementDescriptions = new LinkedHashSet<>();
        Set<IDiagramModelConnection> connections = new LinkedHashSet<>();

        for(IDiagramModelObject dmo : objects) {
            if(dmo instanceof IDiagramModelArchimateObject archiObj) {
                IArchimateElement element = archiObj.getArchimateElement();
                if(element != null) {
                    elementDescriptions.add(describeArchimateElement(element));
                }
            }
            if(dmo instanceof IDiagramModelNote note) {
                String content = compact(note.getContent());
                if(!content.isBlank()) {
                    elementDescriptions.add("Note: " + content);
                }
            }
            if(dmo instanceof IDiagramModelGroup group) {
                elementDescriptions.add("Group: " + safeValue(group.getName(), "(unnamed)"));
            }
            connections.addAll(dmo.getSourceConnections());
        }

        context.viewElementCount = elementDescriptions.size();
        context.viewItems.addAll(limitList(elementDescriptions, MAX_LIST_ITEMS));

        List<String> relDescriptions = new ArrayList<>();
        for(IDiagramModelConnection connection : connections) {
            if(connection instanceof IDiagramModelArchimateConnection archiConnection) {
                String desc = describeRelationship(
                        archiConnection.getArchimateRelationship(),
                        archiConnection.getSource(),
                        archiConnection.getTarget());
                if(desc != null) {
                    relDescriptions.add(desc);
                }
            }
        }

        context.relationshipCount = relDescriptions.size();
        context.relationships.addAll(limitList(relDescriptions, MAX_REL_ITEMS));
    }

    private void collectDiagramObjects(IDiagramModelContainer container, List<IDiagramModelObject> result) {
        for(Object child : container.getChildren()) {
            if(child instanceof IDiagramModelObject dmo) {
                result.add(dmo);
                if(dmo instanceof IDiagramModelContainer nested) {
                    collectDiagramObjects(nested, result);
                }
            }
        }
    }

    private List<String> limitList(Iterable<String> items, int limit) {
        List<String> list = new ArrayList<>();
        for(String item : items) {
            if(item == null || item.isBlank()) {
                continue;
            }
            list.add(item);
            if(list.size() >= limit) {
                break;
            }
        }
        return list;
    }

    private String safeValue(String value, String fallback) {
        if(value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String compact(String text) {
        if(text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }
}
