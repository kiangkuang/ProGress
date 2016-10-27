package com.kiangkuang.progress;

import java.util.HashMap;

public enum Status {
    NONE(0),
    HALF(1),
    READY(2),
    STARTED(3);

    private static HashMap<Integer, Status> hMap = new HashMap<Integer, Status>();

    static {
        for (Status status : Status.values()) {
            hMap.put(status.value, status);
        }
    }

    public final int value;

    Status(int value) {
        this.value = value;
    }

    public static Status valueOf(int value) {
        return hMap.get(value);
    }
}
