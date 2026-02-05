package com.cwdil.archi.sequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.gef.EditPart;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IBounds;
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IDiagramModelContainer;
import com.archimatetool.model.IDiagramModelObject;

public class SequenceDiagramBuilder {

    public static final class Result {
        public final boolean success;
        public final String message;
        public final String plantUml;
        public final int nodeCount;
        public final int relationshipCount;

        private Result(boolean success, String message, String plantUml, int nodeCount, int relationshipCount) {
            this.success = success;
            this.message = message;
            this.plantUml = plantUml;
            this.nodeCount = nodeCount;
            this.relationshipCount = relationshipCount;
        }

        public static Result error(String message) {
            return new Result(false, message, "", 0, 0);
        }

        public static Result success(String plantUml, int nodeCount, int relationshipCount) {
            return new Result(true, "", plantUml, nodeCount, relationshipCount);
        }
    }

    private static final class SelectedNode {
        final IArchimateElement element;
        final IDiagramModelArchimateObject diagramObject;
        final String label;
        final int x;
        final int y;
        String alias;

        SelectedNode(IArchimateElement element, IDiagramModelArchimateObject diagramObject, String label, int x, int y) {
            this.element = element;
            this.diagramObject = diagramObject;
            this.label = label;
            this.x = x;
            this.y = y;
        }
    }

    private static final class TriggerEdge {
        final IArchimateElement source;
        final IArchimateElement target;
        final IArchimateRelationship relationship;

        TriggerEdge(IArchimateElement source, IArchimateElement target, IArchimateRelationship relationship) {
            this.source = source;
            this.target = target;
            this.relationship = relationship;
        }
    }

    public Result build(IDiagramModel diagram, ISelection selection) {
        if(diagram == null) {
            return Result.error("No active diagram.");
        }

        if(!(selection instanceof IStructuredSelection structured) || structured.isEmpty()) {
            return Result.error("Select two or more nodes in the current view.");
        }

        LinkedHashSet<IArchimateElement> selectedElements = new LinkedHashSet<>();
        LinkedHashMap<IArchimateElement, IDiagramModelArchimateObject> selectedDiagramObjects = new LinkedHashMap<>();

        for(Object item : structured.toArray()) {
            IDiagramModelArchimateObject diagramObject = resolveDiagramObject(item);
            IArchimateElement element = resolveArchimateElement(item, diagramObject);
            if(element == null) {
                continue;
            }
            selectedElements.add(element);
            if(diagramObject != null && !selectedDiagramObjects.containsKey(element)) {
                selectedDiagramObjects.put(element, diagramObject);
            }
        }

        if(selectedElements.size() < 2) {
            return Result.error("Select at least two ArchiMate elements.");
        }

        LinkedHashMap<IArchimateElement, IDiagramModelArchimateObject> elementToDiagramObject =
                new LinkedHashMap<>(selectedDiagramObjects);
        List<IDiagramModelArchimateObject> diagramMatches = findDiagramObjects(diagram, selectedElements);
        for(IDiagramModelArchimateObject diagramObject : diagramMatches) {
            IArchimateElement element = diagramObject.getArchimateElement();
            if(element == null) {
                continue;
            }
            elementToDiagramObject.putIfAbsent(element, diagramObject);
        }

        if(elementToDiagramObject.isEmpty()) {
            return Result.error("Selected elements are not diagram nodes in the current view.");
        }

        LinkedHashMap<IArchimateElement, SelectedNode> nodes = new LinkedHashMap<>();
        for(IArchimateElement element : selectedElements) {
            IDiagramModelArchimateObject diagramObject = elementToDiagramObject.get(element);
            nodes.put(element, createNode(element, diagramObject));
        }

        List<TriggerEdge> edges = collectTriggerEdges(diagram, selectedElements);
        if(edges.isEmpty()) {
            return Result.error("No Triggering relationships found between selected nodes.");
        }

        List<SelectedNode> orderedNodes = new ArrayList<>(nodes.values());
        boolean hasLayout = orderedNodes.stream().allMatch(node -> node.diagramObject != null);
        if(hasLayout) {
            orderedNodes.sort(Comparator
                    .comparingInt((SelectedNode node) -> node.x)
                    .thenComparingInt(node -> node.y)
                    .thenComparing(node -> node.label, String.CASE_INSENSITIVE_ORDER));
        }

        if(hasLayout) {
            edges.sort(Comparator
                    .comparingInt((TriggerEdge edge) -> edgeOrderKey(edge, nodes))
                    .thenComparing((TriggerEdge edge) -> safeValue(edgeLabel(edge.relationship), "")));
        }

        String plantUml = buildPlantUml(orderedNodes, edges, nodes);
        return Result.success(plantUml, orderedNodes.size(), edges.size());
    }

    private SelectedNode createNode(IArchimateElement element, IDiagramModelArchimateObject diagramObject) {
        String label = safeValue(compact(element.getName()), element.eClass().getName());
        int x = Integer.MAX_VALUE;
        int y = Integer.MAX_VALUE;
        if(diagramObject != null) {
            IBounds bounds = diagramObject.getBounds();
            if(bounds != null) {
                x = bounds.getX();
                y = bounds.getY();
            }
        }
        return new SelectedNode(element, diagramObject, label, x, y);
    }

    private List<TriggerEdge> collectTriggerEdges(IDiagramModel diagram, Set<IArchimateElement> selectedElements) {
        Set<IArchimateRelationship> seen = new LinkedHashSet<>();
        List<TriggerEdge> edges = new ArrayList<>();

        if(diagram == null || selectedElements == null || selectedElements.isEmpty()) {
            return edges;
        }

        List<IDiagramModelObject> all = new ArrayList<>();
        collectDiagramObjects(diagram, all);
        for(IDiagramModelObject dmo : all) {
            for(Object connection : dmo.getSourceConnections()) {
                if(!(connection instanceof IDiagramModelArchimateConnection archiConnection)) {
                    continue;
                }
                IArchimateRelationship relationship = archiConnection.getArchimateRelationship();
                if(!isEligibleRelationship(relationship)) {
                    continue;
                }
                IArchimateElement sourceElement = resolveRelationshipEnd(relationship != null ? relationship.getSource() : null);
                IArchimateElement targetElement = resolveRelationshipEnd(relationship != null ? relationship.getTarget() : null);
                if(sourceElement == null || targetElement == null) {
                    sourceElement = resolveDiagramElement(archiConnection.getSource());
                    targetElement = resolveDiagramElement(archiConnection.getTarget());
                }
                if(sourceElement == null || targetElement == null) {
                    continue;
                }
                if(!selectedElements.contains(sourceElement) || !selectedElements.contains(targetElement)) {
                    continue;
                }
                if(seen.add(relationship)) {
                    edges.add(new TriggerEdge(sourceElement, targetElement, relationship));
                }
            }
        }

        return edges;
    }

    private List<IDiagramModelArchimateObject> findDiagramObjects(IDiagramModel diagram, Set<IArchimateElement> elements) {
        List<IDiagramModelArchimateObject> matches = new ArrayList<>();
        if(diagram == null || elements == null || elements.isEmpty()) {
            return matches;
        }
        List<IDiagramModelObject> all = new ArrayList<>();
        collectDiagramObjects(diagram, all);
        for(IDiagramModelObject obj : all) {
            if(obj instanceof IDiagramModelArchimateObject archiObj) {
                IArchimateElement element = archiObj.getArchimateElement();
                if(element != null && elements.contains(element)) {
                    matches.add(archiObj);
                }
            }
        }
        return matches;
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

    private IDiagramModelArchimateObject resolveDiagramObject(Object item) {
        if(item instanceof IDiagramModelArchimateObject diagramObject) {
            return diagramObject;
        }
        if(item instanceof EditPart editPart) {
            Object model = editPart.getModel();
            if(model instanceof IDiagramModelArchimateObject diagramObject) {
                return diagramObject;
            }
        }
        if(item instanceof IAdaptable adaptable) {
            Object adapted = adaptable.getAdapter(IDiagramModelArchimateObject.class);
            if(adapted instanceof IDiagramModelArchimateObject diagramObject) {
                return diagramObject;
            }
        }
        return null;
    }

    private IArchimateElement resolveArchimateElement(Object item, IDiagramModelArchimateObject diagramObject) {
        if(diagramObject != null) {
            return diagramObject.getArchimateElement();
        }
        if(item instanceof IDiagramModelArchimateObject archiObj) {
            return archiObj.getArchimateElement();
        }
        if(item instanceof IArchimateElement element) {
            return element;
        }
        if(item instanceof EditPart editPart) {
            Object model = editPart.getModel();
            if(model instanceof IDiagramModelArchimateObject archiObj) {
                return archiObj.getArchimateElement();
            }
            if(model instanceof IArchimateElement element) {
                return element;
            }
        }
        if(item instanceof IAdaptable adaptable) {
            IArchimateElement element = adaptable.getAdapter(IArchimateElement.class);
            if(element != null) {
                return element;
            }
            Object adapted = adaptable.getAdapter(IDiagramModelArchimateObject.class);
            if(adapted instanceof IDiagramModelArchimateObject archiObj) {
                return archiObj.getArchimateElement();
            }
        }
        return null;
    }

    private IArchimateElement resolveRelationshipEnd(Object value) {
        if(value instanceof IArchimateElement element) {
            return element;
        }
        return null;
    }

    private IArchimateElement resolveDiagramElement(Object target) {
        if(target instanceof IDiagramModelArchimateObject diagramTarget) {
            return diagramTarget.getArchimateElement();
        }
        if(target instanceof IArchimateElement element) {
            return element;
        }
        return null;
    }

    private String buildPlantUml(List<SelectedNode> orderedNodes,
                                 List<TriggerEdge> edges,
                                 Map<IArchimateElement, SelectedNode> nodes) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("left to right direction\n");
        sb.append("skinparam shadowing false\n");

        int index = 1;
        for(SelectedNode node : orderedNodes) {
            node.alias = "p" + index++;
            sb.append("rectangle \"").append(escapePlantUml(node.label)).append("\" as ")
                    .append(node.alias).append("\n");
        }

        for(TriggerEdge edge : edges) {
            SelectedNode sourceNode = nodes.get(edge.source);
            SelectedNode targetNode = nodes.get(edge.target);
            if(sourceNode == null || targetNode == null) {
                continue;
            }
            sb.append(sourceNode.alias).append(" --> ").append(targetNode.alias);
            String label = edgeLabel(edge.relationship);
            if(label != null && !label.isBlank()) {
                sb.append(" : ").append(escapePlantUml(label));
            }
            sb.append("\n");
        }

        sb.append("@enduml\n");
        return sb.toString();
    }

    private String edgeLabel(IArchimateRelationship relationship) {
        if(relationship == null) {
            return "";
        }
        String name = compact(relationship.getName());
        if(!name.isBlank()) {
            return name;
        }
        String type = relationship.eClass().getName();
        if(type.endsWith("Relationship")) {
            type = type.substring(0, type.length() - "Relationship".length());
        }
        return type;
    }

    private int edgeOrderKey(TriggerEdge edge, Map<IArchimateElement, SelectedNode> nodes) {
        SelectedNode source = nodes.get(edge.source);
        SelectedNode target = nodes.get(edge.target);
        int sourceY = source != null ? source.y : Integer.MAX_VALUE;
        int targetY = target != null ? target.y : Integer.MAX_VALUE;
        return Math.min(sourceY, targetY);
    }

    private boolean isEligibleRelationship(IArchimateRelationship relationship) {
        return relationship != null;
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

    private String escapePlantUml(String text) {
        if(text == null) {
            return "";
        }
        return compact(text).replace("\"", "\\\"");
    }
}
