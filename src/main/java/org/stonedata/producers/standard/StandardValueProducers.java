package org.stonedata.producers.standard;

import org.stonedata.producers.ValueProducer;
import org.stonedata.producers.standard.value.ClassEnumProducer;
import org.stonedata.producers.standard.value.DefaultTypedValueProducer;
import org.stonedata.producers.standard.value.DefaultValueProducer;
import org.stonedata.producers.standard.value.IntegerProducer;

import java.lang.reflect.Type;


public class StandardValueProducers {
    private StandardValueProducers() {}

    public static ValueProducer create(Type type, String name) {
        return create(type, name, false);
    }

    public static ValueProducer create(Type typeHint, String typeName, boolean useCleanDefaultTypes) {
        if (typeHint instanceof Class) {
            var typeClass = (Class<?>)typeHint;

            if (typeClass == Integer.class) {
                return new IntegerProducer();
            }
            else if (typeClass.isEnum()) {
                return new ClassEnumProducer(typeClass);
            }
        }

        if (typeName == null || useCleanDefaultTypes) {
            return DefaultValueProducer.INSTANCE;
        }
        return new DefaultTypedValueProducer(typeName);
    }
}
