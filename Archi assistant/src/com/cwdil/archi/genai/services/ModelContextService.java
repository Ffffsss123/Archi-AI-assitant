package com.cwdil.archi.genai.services;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.opengroup.archimate.xmlexchange.XMLTypeMapper;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.model.IArchimateConcept;
import com.archimatetool.model.IArchimateDiagramModel;
import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateModel;
import com.archimatetool.model.IArchimateModelObject;
import com.archimatetool.model.IArchimateRelationship;
import com.archimatetool.model.IIdentifier;
import com.archimatetool.model.IFolder;
import com.archimatetool.model.IFolderContainer;

public class ModelContextService {

    public IArchimateModel getModelFromContext(IWorkbenchPart part, ISelection selection) {
        if(part instanceof IAdaptable) {
            IArchimateModel model = ((IAdaptable)part).getAdapter(IArchimateModel.class);
            if(model != null) {
                return model;
            }
        }

        if(selection instanceof IStructuredSelection) {
            for(Object element : ((IStructuredSelection)selection).toArray()) {
                if(element instanceof IArchimateModel) {
                    return (IArchimateModel)element;
                }
                if(element instanceof IArchimateModelObject) {
                    return ((IArchimateModelObject)element).getArchimateModel();
                }
                if(element instanceof IAdaptable) {
                    IArchimateModel model = ((IAdaptable)element).getAdapter(IArchimateModel.class);
                    if(model != null) {
                        return model;
                    }
                }
            }
        }
        IArchimateModel activeEditorModel = getModelFromActiveEditor();
        if(activeEditorModel != null) {
            return activeEditorModel;
        }
        return getSingleOpenModel();
    }

    public int getSelectionCount(ISelection selection) {
        if(selection instanceof IStructuredSelection) {
            return ((IStructuredSelection)selection).size();
        }
        return selection != null && !selection.isEmpty() ? 1 : 0;
    }

    public String buildContextSummary(IWorkbenchPart part, ISelection selection) {
        String viewName = part != null ? part.getTitle() : "None";
        int selected = getSelectionCount(selection);
        IArchimateModel model = getModelFromContext(part, selection);
        String modelName = model != null ? model.getName() : "None";
        return "ActiveView=" + viewName + ", SelectedCount=" + selected + ", ActiveModel=" + modelName;
    }

    public String buildModelInventory(IArchimateModel model, int maxElements) {
        if(model == null) {
            return "ModelInventory: none";
        }
        List<IArchimateConcept> concepts = getAllConcepts(model);
        List<IArchimateElement> elements = new ArrayList<>();
        List<IArchimateRelationship> relationships = new ArrayList<>();
        for(IArchimateConcept concept : concepts) {
            if(concept instanceof IArchimateElement) {
                elements.add((IArchimateElement)concept);
            } else if(concept instanceof IArchimateRelationship) {
                relationships.add((IArchimateRelationship)concept);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("ModelInventory:\n");
        sb.append("ModelName=").append(model.getName()).append("\n");
        sb.append("ElementsCount=").append(elements.size()).append("\n");
        sb.append("RelationshipsCount=").append(relationships.size()).append("\n");
        sb.append("ViewsCount=").append(model.getDiagramModels().size()).append("\n");

        sb.append("Views=[");
        int viewCount = 0;
        for(Object diagramObj : model.getDiagramModels()) {
            if(!(diagramObj instanceof IArchimateDiagramModel)) {
                continue;
            }
            IArchimateDiagramModel diagramModel = (IArchimateDiagramModel)diagramObj;
            if(viewCount++ > 0) {
                sb.append(",");
            }
            sb.append("{id:").append(safeId(diagramModel))
              .append(",name:\"").append(safe(diagramModel.getName())).append("\"}");
        }
        sb.append("]\n");

        sb.append("Elements=[");
        int elementLimit = Math.min(maxElements, elements.size());
        for(int i = 0; i < elementLimit; i++) {
            IArchimateElement element = elements.get(i);
            if(i > 0) {
                sb.append(",");
            }
            sb.append("{id:").append(safeId(element))
              .append(",type:\"").append(safeType(element))
              .append("\",name:\"").append(safe(element.getName())).append("\"}");
        }
        sb.append("]\n");

        sb.append("Relationships=[");
        int relLimit = Math.min(maxElements, relationships.size());
        for(int i = 0; i < relLimit; i++) {
            IArchimateRelationship rel = relationships.get(i);
            if(i > 0) {
                sb.append(",");
            }
            sb.append("{id:").append(safeId(rel))
              .append(",type:\"").append(safeType(rel))
              .append("\",source:").append(safeId(rel.getSource()))
              .append(",target:").append(safeId(rel.getTarget()))
              .append(",name:\"").append(safe(rel.getName())).append("\"}");
        }
        sb.append("]\n");

        if(elements.size() > maxElements || relationships.size() > maxElements) {
            sb.append("InventoryTruncated=true\n");
        }
        return sb.toString();
    }

    private IArchimateModel getModelFromActiveEditor() {
        if(!PlatformUI.isWorkbenchRunning()) {
            return null;
        }
        IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        if(window == null) {
            return null;
        }
        IWorkbenchPage page = window.getActivePage();
        if(page == null) {
            return null;
        }
        IEditorPart editor = page.getActiveEditor();
        if(editor == null) {
            return null;
        }
        return editor.getAdapter(IArchimateModel.class);
    }

    private IArchimateModel getSingleOpenModel() {
        if(!PlatformUI.isWorkbenchRunning()) {
            return null;
        }
        if(IEditorModelManager.INSTANCE.getModels().size() == 1) {
            return IEditorModelManager.INSTANCE.getModels().get(0);
        }
        return null;
    }

    private List<IArchimateConcept> getAllConcepts(IFolderContainer container) {
        List<IArchimateConcept> concepts = new ArrayList<>();
        if(container == null) {
            return concepts;
        }
        for(IFolder folder : container.getFolders()) {
            for(EObject element : folder.getElements()) {
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

    private String safe(String value) {
        if(value == null) {
            return "";
        }
        return value.replace("\"", "'");
    }

    private String safeId(Object obj) {
        if(obj instanceof IIdentifier) {
            String id = ((IIdentifier)obj).getId();
            return id != null ? "\"" + id + "\"" : "\"\"";
        }
        return "\"\"";
    }

    private String safeType(IArchimateConcept concept) {
        String type = XMLTypeMapper.getArchimateConceptName(concept);
        return type != null ? type : concept.eClass().getName();
    }
}
