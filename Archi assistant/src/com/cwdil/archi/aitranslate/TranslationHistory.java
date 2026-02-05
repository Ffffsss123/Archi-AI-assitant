package com.cwdil.archi.aitranslate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.emf.ecore.EObject;

import com.archimatetool.model.IArchimateElement;
import com.archimatetool.model.IArchimateRelationship; // <--- 必须新增：用于连线
import com.archimatetool.model.IDiagramModel;
import com.archimatetool.model.IDiagramModelGroup;
import com.archimatetool.model.IDiagramModelNote;
import com.archimatetool.model.IDiagramModelObject;   // <--- 建议新增：用于通用对象判断
import com.archimatetool.model.IFeatures;
import com.archimatetool.model.IFeature;
import com.cwdil.archi.aitranslate.commands.TranslateViewHandler;

public final class TranslationHistory {

    public static final class Change {
        private final EObject owner;
        private final TranslateViewHandler.PropertyKind propertyKind;
        private final String original;
        private final String translated;

        public Change(EObject owner,
                      TranslateViewHandler.PropertyKind propertyKind,
                      String original,
                      String translated) {
            this.owner = owner;
            this.propertyKind = propertyKind;
            this.original = original;
            this.translated = translated;
        }
    }

    private static List<Change> lastChanges = Collections.emptyList();

    private TranslationHistory() {
    }

    public static void record(List<TranslateViewHandler.TranslatableItem> items) {
        List<Change> changes = new ArrayList<>();
        for (TranslateViewHandler.TranslatableItem item : items) {
            if (item.getTranslated() == null
                    || item.getTranslated().isBlank()
                    || item.getOriginal().equals(item.getTranslated())) {
                continue;
            }
            changes.add(new Change(
                    item.getOwner(),
                    item.getPropertyKind(),
                    item.getOriginal(),
                    item.getTranslated()));
        }
        lastChanges = changes.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(changes);
    }

    public static boolean canUndo() {
        return !lastChanges.isEmpty();
    }

    public static void undoLast() {
        for (Change change : lastChanges) {
            // 如果原始值为null，防止空指针，统一转为空字符串
            String valToRestore = change.original == null ? "" : change.original;

            switch (change.propertyKind) {
                case ELEMENT_NAME -> {
                    // --- 修复重点：同时支持 节点(Element) 和 连线(Relationship) ---
                    if (change.owner instanceof IArchimateElement el) {
                        el.setName(valToRestore);
                    } else if (change.owner instanceof IArchimateRelationship rel) {
                        rel.setName(valToRestore);
                    }
                }
                
                // --- 新增：支持文档撤销 (对应 Handler 里的收集逻辑) ---
                case DOCUMENTATION -> {
                    if (change.owner instanceof IArchimateElement el) {
                        el.setDocumentation(valToRestore);
                    } else if (change.owner instanceof IArchimateRelationship rel) {
                        rel.setDocumentation(valToRestore);
                    } else if (change.owner instanceof IDiagramModelGroup group) {
                        group.setDocumentation(valToRestore);
                    }
                }

                case NOTE_CONTENT -> {
                    if (change.owner instanceof IDiagramModelNote note) {
                        note.setContent(valToRestore);
                    }
                }
                
                case GROUP_NAME -> {
                    if (change.owner instanceof IDiagramModelGroup group) {
                        group.setName(valToRestore);
                    }
                }
                
                case DIAGRAM_NAME -> {
                    if (change.owner instanceof IDiagramModel diagram) {
                        diagram.setName(valToRestore);
                    }
                }

                case LABEL_EXPRESSION -> {
                    if (change.owner instanceof IFeatures featuresProvider) {
                        for (IFeature feature : featuresProvider.getFeatures()) {
                            if ("labelExpression".equals(feature.getName())) {
                                feature.setValue(valToRestore);
                                break;
                            }
                        }
                    }
                }
            }
        }
        // Clear history to prevent repeated undo.
        lastChanges = Collections.emptyList();
    }
}
