package org.stonedata.coding.text;

import org.stonedata.errors.UnknownReferenceException;
import org.stonedata.producers.ObjectProducer;
import org.stonedata.references.StoneReferenceStore;
import org.stonedata.errors.StoneException;
import org.stonedata.coding.StoneCharDecoder;
import org.stonedata.io.StoneCharInput;
import org.stonedata.producers.ProducerRepository;
import org.stonedata.references.impl.StandardReferenceStore;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public class StoneTextDecoder implements StoneCharDecoder {

    private final StoneReferenceStore references;
    private final ProducerRepository producers;

    public StoneTextDecoder(ProducerRepository producers) {
        this(new StandardReferenceStore(), producers);
    }

    public StoneTextDecoder(StoneReferenceStore references, ProducerRepository producers) {
        this.references = references;
        this.producers = producers;
    }

    @Override
    public Object read(StoneCharInput input) throws IOException {
        return read(input, null);
    }

    public Object read(StoneCharInput input, Type typeHint) throws IOException {
        String strValue;
        Object refValue;
        boolean strToken;

        skipWhitespace(input);

        var c = input.peek();
        if (isTokenChar(c)) {
            strValue = readToken(input);
            strToken = true;
        }
        else if (isStringDelimiter(c)) {
            strValue = readString(input);
            strToken = false;
        }
        else {
            strValue = null;
            strToken = false;
        }

        skipWhitespace(input);

        if (input.tryPull('<')) {
            refValue = read(input);

            skipWhitespace(input);

            input.expect('>');

            skipWhitespace(input);
        }
        else {
            refValue = null;
        }

        if (input.peek() == '{') {
            return readObject(input, strValue, refValue, typeHint);
        }
        else if (input.peek() == '[') {
            return readArray(input, strValue, refValue, typeHint);
        }
        else if (input.peek() == '(') {
            return readValue(input, strValue, refValue, typeHint);
        }
        else if (refValue != null) {
            if (!references.containsReference(strValue, refValue)) {
                 throw new UnknownReferenceException(strValue, refValue);
            }
            return references.getValue(strValue, refValue);
        }
        else {
            Object result;

            if (strToken) {
                result = evalToken(strValue);
            }
            else if (strValue != null) {
                result = strValue;
            }
            else {
                throw new RuntimeException();
            }

            if (typeHint == null || typeHint == String.class) {
                return result;
            }

            return evalValue(result, typeHint);
        }
    }

    private Object evalValue(Object result, Type typeHint) {
        var producer = producers.findValueProducer(null, typeHint);

        return producer.newInstance(null, List.of(result));
    }

    public Object readObject(StoneCharInput input, String type, Type typeHint) throws IOException {
        var producer = producers.findObjectProducer(type, typeHint);

        return readObject(input, producer, type);
    }

    public Object readObject(StoneCharInput input, ObjectProducer producer, String type) throws IOException {
        var obj = producer.beginInstance(type);

        input.expect('{');

        do {
            skipWhitespace(input);

            var key = tryStringOrToken(input);

            if (key == null) {
                break;
            }

            skipWhitespace(input);

            input.expect(':');

            skipWhitespace(input);

            var typeHint = producer.getTypeHint(key);
            var value = read(input, typeHint);

            producer.set(obj, key, value);

            skipWhitespace(input);
        }
        while (input.tryPull(','));

        input.expect('}');

        return producer.endInstance(obj);
    }

    private Object readArray(StoneCharInput input, String type, Type typeHint) throws IOException {
        var producer = producers.findArrayProducer(type, typeHint);
        var componentTypeHint = producer.getComponentTypeHint();
        var arr = producer.beginInstance(type);

        input.expect('[');

        do {
            skipWhitespace(input);

            if (input.peek(']')) {
                break;
            }

            var value = read(input, componentTypeHint);

            producer.add(arr, value);

            skipWhitespace(input);
        }
        while (input.tryPull(','));

        input.expect(']');

        return producer.endInstance(arr);
    }

    private Object readValue(StoneCharInput input, String type, Type typeHint) throws IOException {
        var maker = producers.findValueProducer(type, typeHint);
        var arguments = new ArrayList<>();

        input.expect('(');

        do {
            skipWhitespace(input);

            if (input.peek(')')) {
                break;
            }

            var value = read(input);

            arguments.add(value);

            skipWhitespace(input);
        }
        while (input.tryPull(','));

        input.expect(')');

        return maker.newInstance(type, arguments);
    }

    private Object readValue(StoneCharInput input, String type, Object reference, Type typeHint) throws IOException {
        var value = readValue(input, type, typeHint);

        if (reference != null) {
            references.set(type, value, reference);
        }

        return value;
    }

    private Object readArray(StoneCharInput input, String type, Object reference, Type typeHint) throws IOException {
        var value = readArray(input, type, typeHint);

        if (reference != null) {
            references.set(type, value, reference);
        }

        return value;
    }

    private Object readObject(StoneCharInput input, String type, Object reference, Type typeHint) throws IOException {
        var value = readObject(input, type, typeHint);

        if (reference != null) {
            references.set(type, value, reference);
        }

        return value;
    }

    private Object evalToken(String strValue) {
        if (Objects.equals(strValue, "null")) {
            return null;
        }
        else if (Objects.equals(strValue, "true")) {
            return true;
        }
        else if (Objects.equals(strValue, "false")) {
            return false;
        }
        else if (isInteger(strValue)) {
            return new BigInteger(strValue);
        }
        else if (isDecimal(strValue)) {
            return new BigDecimal(strValue);
        }
        else {
            return strValue;
        }
    }

    // STATIC

    private static final Pattern INTEGER_PATTERN = Pattern.compile("[+-]?[0-9]+");
    private static final Pattern DECIMAL_PATTERN = Pattern.compile("[+-]?[0-9]+\\.[0-9]+([eE][+-][0-9]+)?");

    private static boolean isTokenChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '_' || c == '.' || c == '/' || c == '-' || c == '+';
    }

    private static boolean isStringDelimiter(char c) {
        return c == '\"' || c == '\'';
    }

    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\n' || c == '\t'  || c == '\r';
    }

    private static String readToken(StoneCharInput input) throws IOException {
        var c = input.peek();

        if (!isTokenChar(c)) {
            throw new RuntimeException("expected token");
        }

        var token = new StringBuilder();

        do {
            c = input.pull();

            token.append(c);
        }
        while (isTokenChar(input.peek()));

        return token.toString();
    }

    private static String readString(StoneCharInput input) throws IOException {
        var delimiter = input.pull();

        if (!isStringDelimiter(delimiter)) {
            throw new RuntimeException();
        }

        var buffer = new StringBuilder();

        while(input.isAlive()) {
            var chr = input.pull();

            if (chr == delimiter) {
                break;
            }
            else if (chr == '\\') {
                chr = input.pull();

                if (chr == '\\' || chr == '\"' || chr == '\'') {
                    buffer.append(chr);
                }
                else if (chr == 's') {
                    buffer.append(' ');
                }
                else if (chr == 't') {
                    buffer.append('\t');
                }
                else if (chr == 'n') {
                    buffer.append('\n');
                }
                else if (chr == 'r') {
                    buffer.append('\r');
                }
                else if (chr == 'u') {
                    var code = (char)Integer.parseInt(new String(new char[] {
                            input.pull(), input.pull(), input.pull(), input.pull(),
                    }), 16);

                    buffer.append(code);
                }
                else {
                    throw new StoneException();
                }
            }
            else {
                buffer.append(chr);
            }
        }

        return buffer.toString();
    }

    private static String tryStringOrToken(StoneCharInput input) throws IOException {
        var c = input.peek();
        if (isTokenChar(c)) {
            return readToken(input);
        }
        else if (isStringDelimiter(c)) {
            return readString(input);
        }
        return null;
    }

    private static void skipWhitespace(StoneCharInput input) throws IOException {
        while (isWhitespace(input.peek())) {
            input.pull();
        }
    }

    private static boolean isInteger(String str) {
        return INTEGER_PATTERN.matcher(str).matches();
    }

    private static boolean isDecimal(String str) {
        return DECIMAL_PATTERN.matcher(str).matches();
    }

}
