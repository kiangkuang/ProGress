package com.kiangkuang.progress;

import java.util.HashMap;

public enum Mode {
    DESTINATION(0),
    DISTANCE(1);

    private static HashMap<Integer, Mode> hMap = new HashMap<Integer, Mode>();

    static {
        for (Mode mode : Mode.values()) {
            hMap.put(mode.value, mode);
        }
    }

    public final int value;

    Mode(int value) {
        this.value = value;
    }

    public static Mode valueOf(int value) {
        return hMap.get(value);
    }
}
