package org.stonedata.types.object;

import org.stonedata.errors.StoneException;

import java.util.LinkedHashMap;

public class UntypedObject extends LinkedHashMap<String, Object> {

    public void set(String key, Object value) {
        if (containsKey(key)) {
            throw new StoneException();
        }
        put(key, value);
    }

}