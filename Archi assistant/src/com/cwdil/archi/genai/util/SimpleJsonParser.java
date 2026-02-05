package com.cwdil.archi.genai.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SimpleJsonParser {

    public static final class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }

    private final String input;
    private int index;

    private SimpleJsonParser(String input) {
        this.input = input;
    }

    public static Object parse(String json) throws ParseException {
        if(json == null) {
            throw new ParseException("JSON input was null.");
        }
        SimpleJsonParser parser = new SimpleJsonParser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if(parser.index != parser.input.length()) {
            throw new ParseException("Unexpected trailing characters.");
        }
        return value;
    }

    private Object parseValue() throws ParseException {
        skipWhitespace();
        if(index >= input.length()) {
            throw new ParseException("Unexpected end of input.");
        }
        char c = input.charAt(index);
        if(c == '{') {
            return parseObject();
        }
        if(c == '[') {
            return parseArray();
        }
        if(c == '"') {
            return parseString();
        }
        if(c == 't' || c == 'f') {
            return parseBoolean();
        }
        if(c == 'n') {
            return parseNull();
        }
        if(c == '-' || (c >= '0' && c <= '9')) {
            return parseNumber();
        }
        throw new ParseException("Unexpected character: " + c);
    }

    private Map<String, Object> parseObject() throws ParseException {
        Map<String, Object> map = new LinkedHashMap<>();
        index++; // {
        skipWhitespace();
        if(peek('}')) {
            index++;
            return map;
        }
        while(true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            expect(':');
            Object value = parseValue();
            map.put(key, value);
            skipWhitespace();
            if(peek(',')) {
                index++;
                continue;
            }
            if(peek('}')) {
                index++;
                break;
            }
            throw new ParseException("Expected ',' or '}' in object.");
        }
        return map;
    }

    private List<Object> parseArray() throws ParseException {
        List<Object> list = new ArrayList<>();
        index++; // [
        skipWhitespace();
        if(peek(']')) {
            index++;
            return list;
        }
        while(true) {
            Object value = parseValue();
            list.add(value);
            skipWhitespace();
            if(peek(',')) {
                index++;
                continue;
            }
            if(peek(']')) {
                index++;
                break;
            }
            throw new ParseException("Expected ',' or ']' in array.");
        }
        return list;
    }

    private String parseString() throws ParseException {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while(index < input.length()) {
            char c = input.charAt(index++);
            if(c == '"') {
                return sb.toString();
            }
            if(c == '\\') {
                if(index >= input.length()) {
                    throw new ParseException("Unexpected end of input in string escape.");
                }
                char esc = input.charAt(index++);
                switch(esc) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case '/': sb.append('/'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'u': sb.append(parseUnicode()); break;
                    default:
                        throw new ParseException("Invalid escape: \\" + esc);
                }
                continue;
            }
            sb.append(c);
        }
        throw new ParseException("Unterminated string.");
    }

    private char parseUnicode() throws ParseException {
        if(index + 4 > input.length()) {
            throw new ParseException("Invalid unicode escape.");
        }
        String hex = input.substring(index, index + 4);
        index += 4;
        try {
            return (char)Integer.parseInt(hex, 16);
        } catch(NumberFormatException ex) {
            throw new ParseException("Invalid unicode escape: " + hex);
        }
    }

    private Boolean parseBoolean() throws ParseException {
        if(input.startsWith("true", index)) {
            index += 4;
            return Boolean.TRUE;
        }
        if(input.startsWith("false", index)) {
            index += 5;
            return Boolean.FALSE;
        }
        throw new ParseException("Invalid boolean value.");
    }

    private Object parseNull() throws ParseException {
        if(input.startsWith("null", index)) {
            index += 4;
            return null;
        }
        throw new ParseException("Invalid null value.");
    }

    private Number parseNumber() throws ParseException {
        int start = index;
        if(peek('-')) {
            index++;
        }
        while(index < input.length() && Character.isDigit(input.charAt(index))) {
            index++;
        }
        if(peek('.')) {
            index++;
            while(index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
        }
        if(peek('e') || peek('E')) {
            index++;
            if(peek('+') || peek('-')) {
                index++;
            }
            while(index < input.length() && Character.isDigit(input.charAt(index))) {
                index++;
            }
        }
        String number = input.substring(start, index);
        try {
            if(number.indexOf('.') >= 0 || number.indexOf('e') >= 0 || number.indexOf('E') >= 0) {
                return Double.valueOf(number);
            }
            return Long.valueOf(number);
        } catch(NumberFormatException ex) {
            throw new ParseException("Invalid number: " + number);
        }
    }

    private void skipWhitespace() {
        while(index < input.length()) {
            char c = input.charAt(index);
            if(c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                index++;
            } else {
                break;
            }
        }
    }

    private void expect(char c) throws ParseException {
        if(index >= input.length() || input.charAt(index) != c) {
            throw new ParseException("Expected '" + c + "'.");
        }
        index++;
    }

    private boolean peek(char c) {
        return index < input.length() && input.charAt(index) == c;
    }
}
