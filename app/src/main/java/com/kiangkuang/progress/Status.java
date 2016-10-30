package com.kiangkuang.progress;

import android.support.annotation.NonNull;
import android.support.v4.util.LongSparseArray;

public enum Status {
  HALF(1),
  READY(2),
  STARTED(3),

  NONE(0);

  private final long id;

  Status(long id) {
    this.id = id;
  }

  public long getId() {
    return id;
  }

  private static final LongSparseArray<Status> idStatusMap = new LongSparseArray<>();

  static {
    for (Status status : Status.values()) {
      idStatusMap.put(status.getId(), status);
    }
  }

  @NonNull
  public static Status fromId(long id) {
    Status match = idStatusMap.get(id);
    return match == null ? Status.NONE : match;
  }
}
