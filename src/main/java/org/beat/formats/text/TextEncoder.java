package org.beat.formats.text;

import org.beat.errors.CyclicDocumentException;
import org.beat.errors.UnsupportedValueException;
import org.beat.examiners.ArrayExaminer;
import org.beat.examiners.Examiner;
import org.beat.examiners.standard.StandardExaminers;
import org.beat.io.standard.AppendableOutput;
import org.beat.repositories.ExaminerRepository;
import org.beat.examiners.ObjectExaminer;
import org.beat.examiners.ValueExaminer;
import org.beat.references.ReferenceProvider;
import org.beat.io.CharOutput;
import org.beat.util.PP;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TextEncoder {

    private final ReferenceProvider references;
    private final ExaminerRepository examiners;

    private boolean skipNullFields;

    private final Deque<Object> cycleStack;

    public TextEncoder() {
        this(null, null);
    }

    public TextEncoder(ExaminerRepository examiners) {
        this(examiners, null);
    }

    public TextEncoder(ReferenceProvider references) {
        this(null, references);
    }

    public TextEncoder(ExaminerRepository examiners, ReferenceProvider references) {
        this.examiners = examiners;
        this.references = references;
        this.cycleStack = new ArrayDeque<>();
    }

    public boolean getSkipNullFields() {
        return skipNullFields;
    }

    public void setSkipNullFields(boolean skipNullFields) {
        this.skipNullFields = skipNullFields;
    }

    public String write(Object value) {
        var buffer = new StringBuilder();
        var output = new AppendableOutput(buffer);

        write(value, output);

        return buffer.toString();
    }

    public void write(Object value, CharOutput output) {
        write(output, new HashSet<>(), value);
    }

    private void write(CharOutput output, Set<Object> writtenRefs, Object value) {
        var reference = references != null ? references.getReference(value) : null;
        if (reference == null) {
            var examiner = searchExaminer(value);
            var typeName = examiner.getTypeName();
            if (typeName == null) {
                writeContent(output, writtenRefs, value, examiner, false);
            }
            else {
                writeString(output, typeName);
                output.space();
                writeContent(output, writtenRefs, value, examiner, true);
            }
        }
        else if (writtenRefs.add(reference)) {
            var examiner = searchExaminer(value);
            var typeName = examiner.getTypeName();
            if (typeName != null) {
                writeString(output, typeName);
            }
            writeReference(output, reference);
            output.space();
            writeContent(output, writtenRefs, value, examiner, true);
        }
        else {
            writeReference(output, reference);
        }
    }

    private Examiner searchExaminer(Object value) {
        if (examiners != null) {
            var examiner = examiners.getExaminer(value);

            if (examiner != null) {
                return examiner;
            }
        }
        return StandardExaminers.create(value);
    }

    private void writeContent(CharOutput output, Set<Object> writtenRefs, Object value, Examiner examiner, boolean wrap) {
        if (examiner instanceof ValueExaminer) {
            writeValue(output, writtenRefs, value, (ValueExaminer) examiner, wrap);
        }
        else {
            if (cycleStack.contains(value)) {
                throw new CyclicDocumentException(value, examiner);
            }

            cycleStack.push(value);

            if (examiner instanceof ObjectExaminer) {
                writeObject(output, writtenRefs, value, (ObjectExaminer) examiner);
            }
            else if (examiner instanceof ArrayExaminer) {
                writeArray(output, writtenRefs, value, (ArrayExaminer) examiner);
            }
            else {
                throw new UnsupportedValueException("Unsupported examiner: " + PP.typeOf(examiner));
            }

            cycleStack.pop();
        }
    }

    private void writeObject(CharOutput output, Set<Object> writtenRefs, Object value, ObjectExaminer examiner) {
        var entryKeys = examiner.getKeys(value);

        if (entryKeys.isEmpty()) {
            output.write("{}");
            return;
        }

        output.write('{');
        output.indent(+1);
        output.line();

        var i = 0;
        for (var entryKey : entryKeys) {
            var entryValue = examiner.getValue(value, entryKey);

            if (entryValue != null || !skipNullFields) {
                if (i > 0) {
                    output.write(',');
                    output.line();
                }

                writeString(output, entryKey);

                output.write(':');
                output.space();

                write(output, writtenRefs, entryValue);
                i++;
            }
        }

        output.indent(-1);
        output.line();
        output.write('}');
    }

    private void writeArray(CharOutput output, Set<Object> writtenRefs, Object value, ArrayExaminer examiner) {
        var size = examiner.getSizeOf(value);

        if (size == 0) {
            output.write("[]");
            return;
        }

        output.write('[');
        output.indent(+1);
        output.line();

        for (var i = 0; i < size; i++) {
            var item = examiner.getValueAt(i, value);

            if (i > 0) {
                output.write(',');
                output.line();
            }

            write(output, writtenRefs, item);
        }

        output.indent(-1);
        output.line();
        output.write(']');
    }

    private void writeValue(CharOutput output, Set<Object> writtenRefs, Object value, ValueExaminer examiner, boolean wrap) {
        var argument = examiner.extractArgument(value);

        if (argument == null) {
            writeNull(output, wrap);
        }
        else if (argument instanceof String) {
            writeString(output, (String) argument, wrap);
        }
        else if (argument instanceof Boolean) {
            writeBoolean((Boolean) argument, output, wrap);
        }
        else if (argument instanceof Number) {
            writeNumber((Number) argument, output, wrap);
        }
        else if (argument instanceof Character) {
            writeChar((Character) argument, output, wrap);
        }
        else if (argument instanceof List) {
            writeArguments(output, writtenRefs, (List<?>)argument, wrap);
        } else {
            throw new UnsupportedValueException("Unsupported value: " + PP.typeOf(value));
        }
    }

    private void writeArguments(CharOutput output, Set<Object> writtenRefs, List<?> args, boolean wrap) {
        if (args.size() != 1 || wrap) {
            output.write('(');

            for (var i = 0; i < args.size(); i++) {
                if (i > 0) {
                    output.write(',');
                    output.space();
                }

                write(output, writtenRefs, args.get(i));
            }

            output.write(')');
        }
        else {
            write(output, writtenRefs, args.get(0));
        }
    }

    private static void writeReference(CharOutput output, String reference) {
        output.write('<');
        writeString(output, reference);
        output.write('>');
    }

    private static void writeChar(char value, CharOutput output, boolean wrap) {
        if (wrap) {
            output.write("(\"");
            writeUnquotedChar(value, output);
            output.write("\")");
        }
        else {
            output.write('"');
            writeUnquotedChar(value, output);
            output.write('"');
        }
    }

    private static void writeNumber(Number value, CharOutput output, boolean wrap) {
        var code = String.valueOf(value);
        if (wrap) {
            output.write('(');
            output.write(code);
            output.write(')');
        }
        else {
            output.write(code);
        }
    }

    private static void writeBoolean(boolean value, CharOutput output, boolean wrap) {
        if (wrap) {
            output.write(value ? "(true)" : "(false)");
        }
        else {
            output.write(value ? "true" : "false");
        }
    }

    private static void writeString(CharOutput output, String value, boolean wrap) {
        if (wrap) {
            output.write('(');
            writeString(output, value);
            output.write(')');
        }
        else {
            writeString(output, value);
        }
    }

    private static void writeString(CharOutput output, String value) {
        if (value.matches("[a-zA-Z0-9_./+-]+")) {
            output.write(value);
        }
        else {
            var length = value.length();

            output.write('"');

            for (var i = 0; i < length; i++) {
                var chr = value.charAt(i);

                writeUnquotedChar(chr, output);
            }

            output.write('"');
        }
    }

    private static void writeNull(CharOutput output, boolean wrap) {
        if (wrap) {
            output.write("(null)");
        }
        else {
            output.write("null");
        }
    }

    private static void writeUnquotedChar(Character chr, CharOutput output) {
        if (chr == '\"' || chr == '\\') {
            output.write('\\');
            output.write(chr);
        }
        else if (chr == '\t') {
            output.write("\\t");
        }
        else if (chr == '\r') {
            output.write("\\r");
        }
        else if (chr == '\n') {
            output.write("\\n");
        }
        // TODO \\uHHHH
        else {
            output.write(chr);
        }
    }
}
