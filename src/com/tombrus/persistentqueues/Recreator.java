package com.tombrus.persistentqueues;

import java.util.List;
import java.util.function.Function;

public interface Recreator<T> extends Function<List<String>, T> {
}
