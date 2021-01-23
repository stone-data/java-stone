package org.stonedata.coding.text;

import org.junit.jupiter.api.Test;
import org.stonedata.errors.CyclicDocumentException;
import org.stonedata.errors.UnsupportedValueException;
import org.stonedata.examiners.Examiner;
import org.stonedata.examiners.Examiners;
import org.stonedata.examiners.standard.array.ArrayInstanceExaminer;
import org.stonedata.examiners.standard.array.ListExaminer;
import org.stonedata.examiners.standard.object.ClassObjectExaminer;
import org.stonedata.examiners.standard.object.MapExaminer;
import org.stonedata.examiners.standard.value.DefaultTypedValueExaminer;
import org.stonedata.examiners.standard.value.ValueIdentityExaminer;
import org.stonedata.references.ReferenceProvider;
import org.stonedata.references.impl.StandardReferenceProvider;
import org.stonedata.repositories.standard.StandardExaminerRepository;
import org.stonedata.types.array.DefaultTypedListImpl;
import org.stonedata.types.object.DefaultTypedObjectImpl;
import org.stonedata.types.value.DefaultTypedValueImpl;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static util.CustomAssertions.assertContains;
import static util.CustomAssertions.assertException;

class StoneTextEncoderTest {

    public static class CyclicNode { public CyclicNode inner; }

    @Test
    void testSearchCustomExaminer() {
        var repository = new StandardExaminerRepository()
                .register(Examiners.value(Object::toString), Class.class);
        var encoder = new StoneTextEncoder(repository);
        var value = List.class;
        var text = encoder.write(value);

        assertEquals("\"interface java.util.List\"", text);
    }

    @Test
    void testSearchDefaultAtomicExaminers() {
        var encoder = new StoneTextEncoder();

        // null
        assertEquals("null", encoder.write(null));

        // String / Character
        assertEquals("\"!@#\"", encoder.write("!@#"));
        assertEquals("\"?\"", encoder.write('?'));

        // Boolean
        assertEquals("true", encoder.write(true));
        assertEquals("false", encoder.write(false));

        // Number
        assertEquals("1", encoder.write(1));
        assertEquals("1.5", encoder.write(1.5));
    }

    @Test
    void testSearchDefaultTypedExaminers() {
        var encoder = new StoneTextEncoder();

        // DefaultTypedObject
        assertEquals("T{}", encoder.write(new DefaultTypedObjectImpl("T")));
        assertEquals("{}", encoder.write(new DefaultTypedObjectImpl()));

        // DefaultTypedList
        assertEquals("T[]", encoder.write(new DefaultTypedListImpl("T")));
        assertEquals("[]", encoder.write(new DefaultTypedListImpl()));

        // DefaultTypedValue
        assertEquals("T()", encoder.write(new DefaultTypedValueImpl("T")));
        assertEquals("()", encoder.write(new DefaultTypedValueImpl()));
    }

    @Test
    void testSearchDefaultUntypedExaminers() {
        var encoder = new StoneTextEncoder();

        // Map
        assertEquals("{}", encoder.write(Map.of()));

        // List
        assertEquals("[]", encoder.write(List.of()));
    }

    @Test
    void testSearchDefaultExaminersByClass() {
        var encoder = new StoneTextEncoder();

        // Array
        assertEquals("[1,2,3]", encoder.write(new int[]{1,2,3}));

        // Enum
        assertEquals("HOURS", encoder.write(TimeUnit.HOURS));

        // Other
        assertEquals("{}", encoder.write(new Object()));
    }

    @Test
    void testWriteContent() {
        var encoder = new StoneTextEncoder();

        assertEquals("{}", encoder.write(new Object()));
        assertEquals("[]", encoder.write(new Object[0]));
        assertEquals("0", encoder.write(0));
    }

    @Test
    void testUnsupportedExaminer() {
        Examiner invalidExaminer = (() -> null);

        var repository = new StandardExaminerRepository()
                .register(invalidExaminer, Object.class);
        var encoder = new StoneTextEncoder(repository);
        var e = assertException(
                UnsupportedValueException.class,
                () -> encoder.write(new Object()));

        assertContains(invalidExaminer.getClass().getName(), e.getMessage());
    }

    @Test
    void testWriteTypeAndContent() {
        var repository = new StandardExaminerRepository();
        var encoder = new StoneTextEncoder(repository);

        repository.register(new MapExaminer("T"), Map.class);
        repository.register(new ListExaminer("U"), List.class);
        repository.register(new DefaultTypedValueExaminer("V"), Integer.class);

        assertEquals("T{}", encoder.write(Map.of()));
        assertEquals("U[]", encoder.write(List.of()));
        assertEquals("V(0)", encoder.write(0));
    }

    @Test
    void testWriteTypeReferenceAndContent() {
        var repository = new StandardExaminerRepository();
        var references = new StandardReferenceProvider();
        var encoder = new StoneTextEncoder(repository, references);

        repository.register(new MapExaminer("T"), Map.class);
        repository.register(new ArrayInstanceExaminer("U"), Object[].class);
        repository.register(new DefaultTypedValueExaminer("V"), Integer.class);

        var someObject = Map.of();
        var someArray = new Object[0];
        var someValue = 0;

        references.setReference(someObject, "1");
        references.setReference(someArray, "2");
        references.setReference(someValue, "3");

        assertEquals("[T<1>{},<1>]", encoder.write(List.of(someObject, someObject)));
        assertEquals("[U<2>[],<2>]", encoder.write(List.of(someArray, someArray)));
        assertEquals("[V<3>(0),<3>]", encoder.write(List.of(someValue, someValue)));
    }

    @Test
    void testWriteReferenceAndContent() {
        var references = new StandardReferenceProvider();
        var encoder = new StoneTextEncoder(references);

        var someObject = new Object();
        var someArray = new Object[0];
        var someValue = 0;

        references.setReference(someObject, "1");
        references.setReference(someArray, "2");
        references.setReference(someValue, "3");

        assertEquals("[<1>{},<1>]", encoder.write(List.of(someObject, someObject)));
        assertEquals("[<2>[],<2>]", encoder.write(List.of(someArray, someArray)));
        assertEquals("[<3>(0),<3>]", encoder.write(List.of(someValue, someValue)));
    }

    @Test
    void testCyclicException() {
        var node = new CyclicNode();
        node.inner = node;

        var repository = new StandardExaminerRepository();
        repository.register(
                new ClassObjectExaminer(CyclicNode.class),
                CyclicNode.class
        );

        var encoder = new StoneTextEncoder(repository);
        var e = assertException(
                CyclicDocumentException.class,
                () -> encoder.write(node));

        assertContains(CyclicNode.class.getName(), e.getMessage());
    }

    @Test
    void testWriteObject() {
        var encoder = new StoneTextEncoder();

        var mapEmpty = Map.of();
        var mapSingleKey = Map.of("a", 1);

        var mapSortedKeys = new LinkedHashMap<>();
        mapSortedKeys.put("a", 1);
        mapSortedKeys.put("b", 2);

        var mapWithNull = new HashMap<>();
        mapWithNull.put("x", null);

        assertEquals("{}", encoder.write(mapEmpty));
        assertEquals("{a:1}", encoder.write(mapSingleKey));
        assertEquals("{a:1,b:2}", encoder.write(mapSortedKeys));
        assertEquals("{x:null}", encoder.write(mapWithNull));
    }

    @Test
    void testWriteObjectWithSkipNullFields() {
        var encoder = new StoneTextEncoder();
        encoder.setSkipNullFields(true);

        var mapWithNull = new LinkedHashMap<String, Object>();
        mapWithNull.put("a", 1);
        mapWithNull.put("b", null);
        mapWithNull.put("c", 2);

        assertTrue(encoder.isSkipNullFields());
        assertEquals("{a:1,c:2}", encoder.write(mapWithNull));
    }

    @Test
    void testWriteArray() {
        var encoder = new StoneTextEncoder();

        var listEmpty = List.of();
        var listMultiItems = List.of(1, 2, 3);

        assertEquals("[]", encoder.write(listEmpty));
        assertEquals("[1,2,3]", encoder.write(listMultiItems));
    }

    @Test
    void testUnsupportedValue() {
        var repository = new StandardExaminerRepository();
        var encoder = new StoneTextEncoder(repository);
        repository.register(ValueIdentityExaminer.INSTANCE, Object.class);

        var e = assertException(
                UnsupportedValueException.class,
                () -> encoder.write(new Object()));

        assertContains(Object.class.getName(), e.getMessage());
    }

    @Test
    void testWriteAtomicValues() {
        var encoder = new StoneTextEncoder();

        assertEquals("null", encoder.write(null));
        assertEquals("true", encoder.write(true));
        assertEquals("false", encoder.write(false));
        assertEquals("abc", encoder.write("abc"));
        assertEquals("\"x\"", encoder.write('x'));
    }

    @Test
    void testWriteAtomicValuesWrapped() {
        var encoder = new StoneTextEncoder((ReferenceProvider)(value -> "1"));

        assertEquals("<1>(null)", encoder.write(null));
        assertEquals("<1>(true)", encoder.write(true));
        assertEquals("<1>(false)", encoder.write(false));
        assertEquals("<1>(abc)", encoder.write("abc"));
        assertEquals("<1>(\"x\")", encoder.write('x'));
    }

    @Test
    void testWriteArguments() {
        var repository = new StandardExaminerRepository();
        var encoder = new StoneTextEncoder(repository);
        repository.register(ValueIdentityExaminer.INSTANCE, List.class);

        assertEquals("()", encoder.write(List.of()));
        assertEquals("1", encoder.write(List.of(1)));
        assertEquals("(1,2)", encoder.write(List.of(1,2)));
        assertEquals("(1,2,3)", encoder.write(List.of(1,2,3)));
    }

    @Test
    void testEscapedChars() {
        var encoder = new StoneTextEncoder();
        var text = encoder.write("\t\r\n\"");

        assertEquals("\"\\t\\r\\n\\\"\"", text);
    }

}
