package com.cwdil.archi.genai.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.gef.commands.CommandStack;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchPart;
import org.opengroup.archimate.xmlexchange.XMLTypeMapper;

import com.archimatetool.editor.diagram.ArchimateDiagramModelFactory;
import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.model.commands.AddListMemberCommand;
import com.archimatetool.editor.ui.services.EditorManager;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateFactory;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IDiagramModelArchimateConnection;
import com.archimatetool.model.IDiagramModelArchimateObject;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IFolderContainer;
import com.archimatetool.model.IFolder;

public class ModelGenerationService {

    public static final class ApplyResult {
        public final boolean applied;
        public final String message;
        public final IArchimateModel model;

        private ApplyResult(boolean applied, String message, IArchimateModel model) {
            this.applied = applied;
            this.message = message;
            this.model = model;
        }
    }

    private final ModelContextService contextService;

    public ModelGenerationService(ModelContextService contextService) {
        this.contextService = contextService;
    }

    public ApplyResult apply(GenAIResponse response, IWorkbenchPart part, ISelection selection) {
        if(response == null) {
            return new ApplyResult(false, "No response to apply.", null);
        }

        IArchimateModel model = contextService.getModelFromContext(part, selection);
        boolean createdModel = false;

        if(response.model != null && response.model.create) {
            model = IEditorModelManager.INSTANCE.createNewModel();
            createdModel = true;
            if(model != null && response.model.name != null && !response.model.name.isBlank()) {
                model.setName(response.model.name.trim());
            }
        }

        if(model == null) {
            return new ApplyResult(false, "No active model. Ask me to create a new model first.", null);
        }

        int elementCount = 0;
        int relationshipCount = 0;
        int viewCount = 0;

        Map<String, IArchimateConcept> conceptMap = new HashMap<>();
        CommandStack stack = (CommandStack)model.getAdapter(CommandStack.class);

        for(GenAIResponse.ElementSpec elementSpec : response.elements) {
            String normalizedType = normalizeType(elementSpec.type);
            if(normalizedType == null || normalizedType.isBlank()) {
                continue;
            }
            IArchimateElement existingElement = findExistingElement(model, normalizedType, elementSpec.name);
            if(existingElement != null) {
                conceptMap.put(elementSpec.id, existingElement);
                continue;
            }
            IArchimateConcept concept = XMLTypeMapper.createArchimateConcept(normalizedType);
            if(!(concept instanceof IArchimateElement)) {
                continue;
            }
            if(elementSpec.name != null && !elementSpec.name.isBlank()) {
                ((IArchimateElement)concept).setName(elementSpec.name.trim());
            }
            if(elementSpec.documentation != null && !elementSpec.documentation.isBlank()) {
                concept.setDocumentation(elementSpec.documentation.trim());
            }
            addToModel(model, concept, stack);
            elementCount++;
            if(elementSpec.id != null && !elementSpec.id.isBlank()) {
                conceptMap.put(elementSpec.id, concept);
            }
        }

        for(GenAIResponse.RelationshipSpec relSpec : response.relationships) {
            String normalizedType = normalizeType(relSpec.type);
            if(normalizedType == null || normalizedType.isBlank()) {
                continue;
            }
            IArchimateConcept source = conceptMap.get(relSpec.source);
            IArchimateConcept target = conceptMap.get(relSpec.target);
            IArchimateRelationship existingRel = findExistingRelationship(model, normalizedType, source, target, relSpec.name);
            if(existingRel != null) {
                if(relSpec.id != null && !relSpec.id.isBlank()) {
                    conceptMap.put(relSpec.id, existingRel);
                }
                continue;
            }
            IArchimateConcept concept = XMLTypeMapper.createArchimateConcept(normalizedType);
            if(!(concept instanceof IArchimateRelationship)) {
                continue;
            }
            if(!(source instanceof IArchimateElement) || !(target instanceof IArchimateElement)) {
                continue;
            }
            IArchimateRelationship relation = (IArchimateRelationship)concept;
            relation.setSource(source);
            relation.setTarget(target);
            if(relSpec.name != null && !relSpec.name.isBlank()) {
                relation.setName(relSpec.name.trim());
            }
            if(relSpec.documentation != null && !relSpec.documentation.isBlank()) {
                relation.setDocumentation(relSpec.documentation.trim());
            }
            addToModel(model, relation, stack);
            relationshipCount++;
            if(relSpec.id != null && !relSpec.id.isBlank()) {
                conceptMap.put(relSpec.id, relation);
            }
        }

        if(response.view != null) {
            String viewName = response.view.name != null && !response.view.name.isBlank()
                    ? response.view.name.trim()
                    : "AI Generated View";
            List<String> includeIds = response.view.include;
            if(includeIds == null || includeIds.isEmpty()) {
                includeIds = conceptMap.keySet().stream().collect(java.util.stream.Collectors.toList());
            }
            if(response.view.create) {
                IArchimateDiagramModel diagramModel = IArchimateFactory.eINSTANCE.createArchimateDiagramModel();
                diagramModel.setName(viewName);
                addDiagramToModel(model, diagramModel, stack);
                addToView(diagramModel, includeIds, conceptMap);
                EditorManager.openDiagramEditor(diagramModel);
                viewCount++;
            } else if(!viewName.isBlank()) {
                IArchimateDiagramModel existing = findDiagramModelByName(model, viewName);
                if(existing != null) {
                    addToView(existing, includeIds, conceptMap);
                    viewCount++;
                }
            }
        }

        if(elementCount == 0 && relationshipCount == 0 && viewCount == 0 && !createdModel) {
            return new ApplyResult(false, "No model changes were applied.", model);
        }

        StringBuilder summary = new StringBuilder();
        if(createdModel) {
            summary.append("Created model '").append(model.getName()).append("'. ");
        }
        summary.append("Added ").append(elementCount).append(" elements, ")
                .append(relationshipCount).append(" relationships");
        if(viewCount > 0) {
            summary.append(", updated ").append(viewCount).append(" view");
        }
        summary.append(".");

        return new ApplyResult(true, summary.toString(), model);
    }

    private void addToModel(IArchimateModel model, IArchimateConcept concept, CommandStack stack) {
        if(model == null || concept == null) {
            return;
        }
        IFolder folder = model.getDefaultFolderForObject(concept);
        if(folder == null) {
            return;
        }
        if(stack != null) {
            stack.execute(new AddListMemberCommand<>(folder.getElements(), concept));
        } else {
            folder.getElements().add(concept);
        }
    }

    private void addDiagramToModel(IArchimateModel model, IArchimateDiagramModel diagramModel, CommandStack stack) {
        if(model == null || diagramModel == null) {
            return;
        }
        IFolder folder = model.getDefaultFolderForObject(diagramModel);
        if(folder == null) {
            return;
        }
        if(stack != null) {
            stack.execute(new AddListMemberCommand<>(folder.getElements(), diagramModel));
        } else {
            folder.getElements().add(diagramModel);
        }
    }

    private void addToView(IArchimateDiagramModel diagramModel, List<String> includeIds,
                           Map<String, IArchimateConcept> conceptMap) {
        int x = 20;
        int y = 20;
        int col = 0;
        Map<IArchimateElement, IDiagramModelArchimateObject> nodeMap = new HashMap<>();

        for(Object child : diagramModel.getChildren()) {
            if(child instanceof IDiagramModelArchimateObject existing) {
                nodeMap.put(existing.getArchimateElement(), existing);
            }
        }

        for(String id : includeIds) {
            IArchimateConcept concept = conceptMap.get(id);
            if(!(concept instanceof IArchimateElement)) {
                continue;
            }
            IArchimateElement element = (IArchimateElement)concept;
            if(nodeMap.containsKey(element)) {
                continue;
            }
            IDiagramModelArchimateObject dmo = ArchimateDiagramModelFactory.createDiagramModelArchimateObject(element);
            diagramModel.getChildren().add(dmo);
            dmo.getBounds().setLocation(x, y);
            nodeMap.put(element, dmo);

            y += 100;
            col++;
            if(col % 6 == 0) {
                col = 0;
                y = 20;
                x += 180;
            }
        }

        for(String id : includeIds) {
            IArchimateConcept concept = conceptMap.get(id);
            if(!(concept instanceof IArchimateRelationship)) {
                continue;
            }
            IArchimateRelationship relation = (IArchimateRelationship)concept;
            if(!(relation.getSource() instanceof IArchimateElement) || !(relation.getTarget() instanceof IArchimateElement)) {
                continue;
            }
            IDiagramModelArchimateObject sourceNode = nodeMap.get(relation.getSource());
            IDiagramModelArchimateObject targetNode = nodeMap.get(relation.getTarget());
            if(sourceNode == null || targetNode == null) {
                continue;
            }
            if(connectionExists(sourceNode, targetNode, relation)) {
                continue;
            }
            IDiagramModelArchimateConnection connection = ArchimateDiagramModelFactory.createDiagramModelArchimateConnection(relation);
            connection.connect(sourceNode, targetNode);
        }

        if(!hasRelationshipIncludes(includeIds, conceptMap)) {
            for(IArchimateConcept concept : conceptMap.values()) {
                if(!(concept instanceof IArchimateRelationship)) {
                    continue;
                }
                IArchimateRelationship relation = (IArchimateRelationship)concept;
                if(!(relation.getSource() instanceof IArchimateElement) || !(relation.getTarget() instanceof IArchimateElement)) {
                    continue;
                }
                IDiagramModelArchimateObject sourceNode = nodeMap.get(relation.getSource());
                IDiagramModelArchimateObject targetNode = nodeMap.get(relation.getTarget());
                if(sourceNode == null || targetNode == null) {
                    continue;
                }
                if(connectionExists(sourceNode, targetNode, relation)) {
                    continue;
                }
                IDiagramModelArchimateConnection connection = ArchimateDiagramModelFactory.createDiagramModelArchimateConnection(relation);
                connection.connect(sourceNode, targetNode);
            }
        }
    }

    private IArchimateDiagramModel findDiagramModelByName(IArchimateModel model, String name) {
        if(model == null || name == null || name.isBlank()) {
            return null;
        }
        for(com.archimatetool.model.IDiagramModel diagramModel : model.getDiagramModels()) {
            if(diagramModel instanceof IArchimateDiagramModel archiDiagramModel) {
                if(name.equals(archiDiagramModel.getName())) {
                    return archiDiagramModel;
                }
            }
        }
        for(com.archimatetool.model.IDiagramModel diagramModel : model.getDiagramModels()) {
            if(diagramModel instanceof IArchimateDiagramModel archiDiagramModel) {
                if(name.equalsIgnoreCase(archiDiagramModel.getName())) {
                    return archiDiagramModel;
                }
            }
        }
        return null;
    }

    private boolean connectionExists(IDiagramModelArchimateObject source, IDiagramModelArchimateObject target,
                                     IArchimateRelationship relationship) {
        if(source == null || target == null || relationship == null) {
            return false;
        }
        for(Object obj : source.getSourceConnections()) {
            if(obj instanceof IDiagramModelArchimateConnection existing) {
                if(existing.getArchimateRelationship() == relationship &&
                        existing.getTarget() == target) {
                    return true;
                }
            }
        }
        return false;
    }

    private String normalizeType(String rawType) {
        if(rawType == null) {
            return null;
        }
        String type = rawType.trim();
        if(type.isEmpty()) {
            return type;
        }
        if("Interface".equalsIgnoreCase(type)) {
            return "ApplicationInterface";
        }
        return type;
    }

    private boolean hasRelationshipIncludes(List<String> includeIds, Map<String, IArchimateConcept> conceptMap) {
        for(String id : includeIds) {
            IArchimateConcept concept = conceptMap.get(id);
            if(concept instanceof IArchimateRelationship) {
                return true;
            }
        }
        return false;
    }

    private IArchimateElement findExistingElement(IArchimateModel model, String type, String name) {
        if(model == null || type == null || type.isBlank() || name == null || name.isBlank()) {
            return null;
        }
        for(IArchimateConcept concept : getAllConcepts(model)) {
            if(!(concept instanceof IArchimateElement)) {
                continue;
            }
            String existingType = XMLTypeMapper.getArchimateConceptName(concept);
            if(existingType == null || !existingType.equalsIgnoreCase(type)) {
                continue;
            }
            IArchimateElement element = (IArchimateElement)concept;
            if(name.trim().equalsIgnoreCase(element.getName())) {
                return element;
            }
        }
        return null;
    }

    private IArchimateRelationship findExistingRelationship(IArchimateModel model, String type,
                                                           IArchimateConcept source, IArchimateConcept target,
                                                           String name) {
        if(model == null || type == null || type.isBlank()) {
            return null;
        }
        if(!(source instanceof IArchimateElement) || !(target instanceof IArchimateElement)) {
            return null;
        }
        for(IArchimateConcept concept : getAllConcepts(model)) {
            if(!(concept instanceof IArchimateRelationship)) {
                continue;
            }
            IArchimateRelationship rel = (IArchimateRelationship)concept;
            String existingType = XMLTypeMapper.getArchimateConceptName(rel);
            if(existingType == null || !existingType.equalsIgnoreCase(type)) {
                continue;
            }
            if(rel.getSource() != source || rel.getTarget() != target) {
                continue;
            }
            if(name == null || name.isBlank()) {
                return rel;
            }
            if(name.trim().equalsIgnoreCase(rel.getName())) {
                return rel;
            }
        }
        return null;
    }

    private List<IArchimateConcept> getAllConcepts(IFolderContainer container) {
        List<IArchimateConcept> concepts = new ArrayList<>();
        if(container == null) {
            return concepts;
        }
        for(IFolder folder : container.getFolders()) {
            for(Object element : folder.getElements()) {
                if(element instanceof IArchimateConcept concept) {
                    concepts.add(concept);
                }
                if(element instanceof IFolderContainer childContainer) {
                    concepts.addAll(getAllConcepts(childContainer));
                }
            }
        }
        return concepts;
    }
}
