package com.kiangkuang.progress;

import android.support.annotation.NonNull;
import android.support.v4.util.LongSparseArray;

public enum Mode {
  DESTINATION(1),
  DISTANCE(2),

  NONE(0);

  private final long id;

  Mode(long id) {
    this.id = id;
  }

  public long getId() {
    return id;
  }

  private static final LongSparseArray<Mode> idModeMap = new LongSparseArray<>();

  static {
    for (Mode mode : Mode.values()) {
      idModeMap.put(mode.getId(), mode);
    }
  }

  @NonNull
  public static Mode fromId(long id) {
    Mode match = idModeMap.get(id);
    return match == null ? Mode.NONE : match;
  }
}
