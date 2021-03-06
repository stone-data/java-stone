package org.beat.producers;

import org.beat.errors.BeatException;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface ValueProducer extends Producer {

    Object newInstance(Object[] arguments);

    static ValueProducer of(Function<Object, ?> fn) {
        return args -> {
            if (args.length == 1) {
                return fn.apply(args[0]);
            }
            else {
                throw new BeatException("Unexpected number of arguments: " + args.length);
            }
        };
    }

    static ValueProducer of(BiFunction<Object, Object, ?> fn) {
        return args -> {
            if (args.length == 2) {
                return fn.apply(args[0], args[1]);
            }
            else {
                throw new BeatException("Unexpected number of arguments: " + args.length);
            }
        };
    }

}
