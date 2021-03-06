package org.beat.formats.text;

import org.beat.errors.InvalidSyntaxException;
import org.beat.io.CharInput;
import org.beat.io.standard.SequenceInput;
import org.beat.producers.ArrayProducer;
import org.beat.producers.ObjectProducer;
import org.beat.producers.ValueProducer;
import org.beat.producers.standard.StandardArrayProducers;
import org.beat.producers.standard.StandardObjectProducers;
import org.beat.producers.standard.StandardValueProducers;
import org.beat.references.ReferenceTracker;
import org.beat.references.impl.StandardReferenceTracker;
import org.beat.repositories.ProducerRepository;
import org.beat.util.PP;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class TextDecoder {

    private final ReferenceTracker references;
    private final ProducerRepository producers;

    private boolean useCleanDefaultTypes;

    public TextDecoder() {
        this(null, null);
    }

    public TextDecoder(ReferenceTracker references) {
        this(null, references);
    }

    public TextDecoder(ProducerRepository producers) {
        this(producers, null);
    }

    public TextDecoder(ProducerRepository producers, ReferenceTracker references) {
        this.references = (references != null ? references : new StandardReferenceTracker());
        this.producers = producers;
    }

    public boolean getUseCleanDefaultTypes() {
        return useCleanDefaultTypes;
    }

    public void setUseCleanDefaultTypes(boolean useCleanDefaultTypes) {
        this.useCleanDefaultTypes = useCleanDefaultTypes;
    }

    public Object read(CharSequence text) {
        return read(new SequenceInput(text));
    }

    public <T> T read(CharSequence text, Class<T> typeClass) {
        return read(new SequenceInput(text), typeClass);
    }

    public Object read(CharInput input) {
        return readWithHint(input, null);
    }

    public <T> T read(CharInput input, Class<T> typeClass) {
        var result = readWithHint(input, typeClass);

        return typeClass.cast(result);
    }

    // PRIVATE

    private Object readWithHint(CharInput input, Type typeHint) {
        skipWhitespace(input);

        var c = input.peek();

        if (c == '{') {
            return readObject(input, null, typeHint);
        }
        else if (c == '[') {
            return readArray(input, null, typeHint);
        }
        else if (c == '(') {
            return readValue(input, null, typeHint);
        }
        else if (c == '<') {
            return readReference(input, null, typeHint);
        }

        Object atomic;

        if (isTokenChar(c)) {
            atomic = evalToken(continueToken(input));
        }
        else if(isStringDelimiter(c)) {
            atomic = continueString(input);
        }
        else {
            throw new InvalidSyntaxException("Expected to read a value.", input.getLocation());
        }

        if (atomic instanceof String) {
            skipWhitespace(input);

            c = input.peek();

            if (c == '{') {
                return readObject(input, (String)atomic, typeHint);
            }
            else if (c == '[') {
                return readArray(input, (String)atomic, typeHint);
            }
            else if (c == '(') {
                return readValue(input, (String)atomic, typeHint);
            }
            else if (c == '<') {
                return readReference(input, (String)atomic, typeHint);
            }
        }

        return evalValue(atomic, typeHint);
    }

    private Object evalValue(Object value, Type typeHint) {
        var producer = searchValueProducer(null, typeHint);

        return producer.newInstance(new Object[]{value});
    }

    private Object readReference(CharInput input, String typeName, Type typeHint) {
        var reference = readReference(input);

        skipWhitespace(input);

        var c = input.peek();

        if (c == '{') {
            return store(reference, readObject(input, typeName, typeHint));
        }
        else if (c == '[') {
            return store(reference, readArray(input, typeName, typeHint));
        }
        else if (c == '(') {
            return store(reference, readValue(input, typeName, typeHint));
        }
        else if (typeName == null) {
            // If there is no type name, it can be just a reference
            return references.retrieve(reference);
        }
        else {
            throw new InvalidSyntaxException("Expected value for " + typeName + "<" + reference + ">.", input.getLocation());
        }
    }

    private Object readObject(CharInput input, String typeName, Type typeHint) {
        var producer = searchObjectProducer(typeName, typeHint);
        var obj = producer.beginInstance();

        input.expect('{');

        do {
            skipWhitespace(input);

            var key = readKeyOrNull(input);

            if (key == null) {
                break;
            }

            skipWhitespace(input);

            input.expect(':');

            skipWhitespace(input);

            var keyTypeHint = producer.getTypeHint(key);
            var value = readWithHint(input, keyTypeHint);

            producer.set(obj, key, value);

            skipWhitespace(input);
        }
        while (input.tryPull(','));

        input.expect('}');

        return producer.endInstance(obj);
    }

    private Object readArray(CharInput input, String typeName, Type typeHint) {
        var producer = searchArrayProducer(typeName, typeHint);
        var componentTypeHint = producer.getComponentTypeHint();
        var arr = producer.beginInstance();

        input.expect('[');

        do {
            skipWhitespace(input);

            if (input.peek(']')) {
                break;
            }

            var value = readWithHint(input, componentTypeHint);

            producer.add(arr, value);

            skipWhitespace(input);
        }
        while (input.tryPull(','));

        input.expect(']');

        return producer.endInstance(arr);
    }

    private Object readValue(CharInput input, String typeName, Type typeHint) {
        var producer = searchValueProducer(typeName, typeHint);
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

        return producer.newInstance(arguments.toArray());
    }

    private ObjectProducer searchObjectProducer(String typeName, Type typeHint) {
        if (producers != null) {
            // Try to get it with the type name
            if (typeName != null) {
                var producer = producers.getObjectProducer(typeName);
                if (producer != null) {
                    return producer;
                }
            }

            // Try to get it with the type hint
            if (typeHint != null) {
                var producer = producers.getObjectProducer(typeHint);
                if (producer != null) {
                    return producer;
                }
            }
        }

        return StandardObjectProducers.create(typeHint, typeName, useCleanDefaultTypes);
    }

    private ArrayProducer searchArrayProducer(String typeName, Type typeHint) {
        if (producers != null) {
            // Try to get it with the type name
            if (typeName != null) {
                var producer = producers.getArrayProducer(typeName);
                if (producer != null) {
                    return producer;
                }
            }

            // Try to get it with the type hint
            if (typeHint != null) {
                var producer = producers.getArrayProducer(typeHint);
                if (producer != null) {
                    return producer;
                }
            }
        }

        return StandardArrayProducers.create(typeHint, typeName, useCleanDefaultTypes);
    }

    private ValueProducer searchValueProducer(String typeName, Type typeHint) {
        if (producers != null) {
            if (typeName != null) {
                var producer = producers.getValueProducer(typeName);
                if (producer != null) {
                    return producer;
                }
            }

            if (typeHint != null) {
                var  producer = producers.getValueProducer(typeHint);
                if (producer != null) {
                    return producer;
                }
            }
        }

        return StandardValueProducers.create(typeHint, typeName, useCleanDefaultTypes);
    }

    private Object store(String reference, Object value) {
        references.store(reference, value);
        return value;
    }

    // STATIC

    private static final Pattern INTEGER_PATTERN = Pattern.compile("[+-]?[0-9]+");

    private static final Pattern DECIMAL_PATTERN = Pattern.compile("[+-]?[0-9]*\\.[0-9]+([eE][+-]?[0-9]+)?");

    private static boolean isInteger(String str) {
        return INTEGER_PATTERN.matcher(str).matches();
    }

    private static boolean isDecimal(String str) {
        return DECIMAL_PATTERN.matcher(str).matches();
    }

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

    private static void skipWhitespace(CharInput input) {
        while (isWhitespace(input.peek())) {
            input.pull();
        }
    }

    private static String readKeyOrNull(CharInput input) {
        var c = input.peek();
        if (isTokenChar(c)) {
            return continueToken(input);
        }
        else if (isStringDelimiter(c)) {
            return continueString(input);
        }
        return null;
    }

    private static String continueToken(CharInput input) {
        var token = new StringBuilder();

        do {
            var c = input.pull();

            token.append(c);
        }
        while (isTokenChar(input.peek()));

        return token.toString();
    }

    private static String continueString(CharInput input) {
        var delimiter = input.pull();
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
                    throw new InvalidSyntaxException(
                            String.format("Invalid escaped char: %s", PP.str(chr)), input.getLocation());
                }
            }
            else {
                buffer.append(chr);
            }
        }

        return buffer.toString();
    }

    private static String readReference(CharInput input) {
        input.expect('<');

        var reference = readKeyOrNull(input);

        if (reference == null) {
            throw new InvalidSyntaxException("Expected a reference key.", input.getLocation());
        }

        skipWhitespace(input);

        input.expect('>');

        return reference;
    }

    private static Object evalToken(String token) {
        if ("null".equals(token)) {
            return null;
        }
        else if ("true".equals(token)) {
            return true;
        }
        else if ("false".equals(token)) {
            return false;
        }
        else if (isInteger(token)) {
            return new BigInteger(token);
        }
        else if (isDecimal(token)) {
            return new BigDecimal(token);
        }
        else {
            return token;
        }
    }

}
