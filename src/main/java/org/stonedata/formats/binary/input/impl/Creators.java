package org.stonedata.formats.binary.input.impl;

public interface Creators {

    static <T> CreatorBuilder<T> builder(Class<T> typeClass) {
        return new CreatorBuilder<>(typeClass);
    }

}