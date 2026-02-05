package com.cwdil.archi.genai.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.cwdil.archi.genai.util.SimpleJsonParser;
import com.cwdil.archi.genai.util.SimpleJsonParser.ParseException;

public final class GenAIResponse {

    public static final class ModelSpec {
        public final boolean create;
        public final String name;

        private ModelSpec(boolean create, String name) {
            this.create = create;
            this.name = name;
        }
    }

    public static final class ElementSpec {
        public final String id;
        public final String type;
        public final String name;
        public final String documentation;

        private ElementSpec(String id, String type, String name, String documentation) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.documentation = documentation;
        }
    }

    public static final class RelationshipSpec {
        public final String id;
        public final String type;
        public final String name;
        public final String source;
        public final String target;
        public final String documentation;

        private RelationshipSpec(String id, String type, String name, String source, String target, String documentation) {
            this.id = id;
            this.type = type;
            this.name = name;
            this.source = source;
            this.target = target;
            this.documentation = documentation;
        }
    }

    public static final class ViewSpec {
        public final boolean create;
        public final String name;
        public final List<String> include;

        private ViewSpec(boolean create, String name, List<String> include) {
            this.create = create;
            this.name = name;
            this.include = include;
        }
    }

    public final String assistantReply;
    public final String followUpQuestion;
    public final ModelSpec model;
    public final List<ElementSpec> elements;
    public final List<RelationshipSpec> relationships;
    public final ViewSpec view;

    private GenAIResponse(String assistantReply, String followUpQuestion, ModelSpec model,
                          List<ElementSpec> elements, List<RelationshipSpec> relationships, ViewSpec view) {
        this.assistantReply = assistantReply;
        this.followUpQuestion = followUpQuestion;
        this.model = model;
        this.elements = elements;
        this.relationships = relationships;
        this.view = view;
    }

    public static GenAIResponse parse(String json) throws ParseException {
        Object root = SimpleJsonParser.parse(json);
        if(!(root instanceof Map)) {
            throw new ParseException("Root JSON value must be an object.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>)root;

        String assistantReply = asString(map.get("assistant_reply"));
        String followUpQuestion = asString(map.get("follow_up_question"));
        ModelSpec model = parseModel(map.get("model"));
        List<ElementSpec> elements = parseElements(map.get("elements"));
        List<RelationshipSpec> relationships = parseRelationships(map.get("relationships"));
        ViewSpec view = parseView(map.get("view"));

        return new GenAIResponse(assistantReply, followUpQuestion, model, elements, relationships, view);
    }

    private static ModelSpec parseModel(Object value) {
        if(!(value instanceof Map)) {
            return new ModelSpec(false, null);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>)value;
        boolean create = asBoolean(map.get("create"));
        String name = asString(map.get("name"));
        return new ModelSpec(create, name);
    }

    private static List<ElementSpec> parseElements(Object value) {
        if(!(value instanceof List)) {
            return Collections.emptyList();
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>)value;
        List<ElementSpec> out = new ArrayList<>();
        for(Object item : list) {
            if(!(item instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>)item;
            out.add(new ElementSpec(
                    asString(map.get("id")),
                    asString(map.get("type")),
                    asString(map.get("name")),
                    asString(map.get("documentation"))
            ));
        }
        return out;
    }

    private static List<RelationshipSpec> parseRelationships(Object value) {
        if(!(value instanceof List)) {
            return Collections.emptyList();
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>)value;
        List<RelationshipSpec> out = new ArrayList<>();
        for(Object item : list) {
            if(!(item instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>)item;
            out.add(new RelationshipSpec(
                    asString(map.get("id")),
                    asString(map.get("type")),
                    asString(map.get("name")),
                    asString(map.get("source")),
                    asString(map.get("target")),
                    asString(map.get("documentation"))
            ));
        }
        return out;
    }

    private static ViewSpec parseView(Object value) {
        if(!(value instanceof Map)) {
            return new ViewSpec(false, null, Collections.emptyList());
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>)value;
        boolean create = asBoolean(map.get("create"));
        String name = asString(map.get("name"));
        List<String> include = parseStringList(map.get("include"));
        return new ViewSpec(create, name, include);
    }

    private static List<String> parseStringList(Object value) {
        if(!(value instanceof List)) {
            return Collections.emptyList();
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>)value;
        List<String> out = new ArrayList<>();
        for(Object item : list) {
            String text = asString(item);
            if(text != null && !text.isBlank()) {
                out.add(text);
            }
        }
        return out;
    }

    private static String asString(Object value) {
        if(value instanceof String) {
            return (String)value;
        }
        if(value == null) {
            return null;
        }
        return String.valueOf(value);
    }

    private static boolean asBoolean(Object value) {
        if(value instanceof Boolean) {
            return (Boolean)value;
        }
        if(value instanceof String) {
            return Boolean.parseBoolean((String)value);
        }
        return false;
    }
}
