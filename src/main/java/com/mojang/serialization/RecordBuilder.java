// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT license.
package com.mojang.serialization;

import com.google.common.collect.ImmutableMap;

import java.util.function.Function;

public interface RecordBuilder<T> {
    DynamicOps<T> ops();

    RecordBuilder<T> add(T key, T value);

    RecordBuilder<T> add(T key, DataResult<T> value);

    RecordBuilder<T> add(DataResult<T> key, DataResult<T> value);

    RecordBuilder<T> withErrorsFrom(final DataResult<?> result);

    RecordBuilder<T> setLifecycle(Lifecycle lifecycle);

    RecordBuilder<T> mapError(Function<String, String> onError);

    DataResult<T> build(T prefix);

    default DataResult<T> build(final DataResult<T> prefix) {
        return prefix.flatMap(this::build);
    }

    default RecordBuilder<T> add(final String key, final T value) {
        return add(ops().createString(key), value);
    }

    default RecordBuilder<T> add(final String key, final DataResult<T> value) {
        return add(ops().createString(key), value);
    }

    default RecordBuilder<T> add(final String key, final Serializable value) {
        return add(key, value, ops().empty());
    }

    default RecordBuilder<T> add(final String key, final Serializable value, final T elementPrefix) {
        return add(key, value.serialize(ops(), elementPrefix));
    }

    default <E> RecordBuilder<T> add(final String key, final E value, final Encoder<E> encoder) {
        return add(key, encoder.encodeStart(ops(), value));
    }

    abstract class AbstractStringBuilder<T, R> implements RecordBuilder<T> {
        private final DynamicOps<T> ops;
        protected DataResult<R> builder = DataResult.success(initBuilder());

        protected AbstractStringBuilder(final DynamicOps<T> ops) {
            this.ops = ops;
        }

        @Override
        public DynamicOps<T> ops() {
            return ops;
        }

        protected abstract R initBuilder();

        protected abstract DataResult<T> build(final R builder, final T prefix);

        protected abstract R append(String key, T value, R builder);

        @Override
        public RecordBuilder<T> add(final String key, final T value) {
            builder = builder.map(b -> append(key, value, b));
            return this;
        }

        @Override
        public RecordBuilder<T> add(final String key, final DataResult<T> value) {
            builder = builder.apply2((b, v) -> append(key, v, b), value);
            return this;
        }

        @Override
        public DataResult<T> build(final T prefix) {
            final DataResult<T> result = builder.flatMap(b -> build(b, prefix));
            builder = DataResult.success(initBuilder());
            return result;
        }

        @Override
        public RecordBuilder<T> add(final T key, final T value) {
            builder = ops.getStringValue(key).flatMap(k -> {
                add(k, value);
                return builder;
            });
            return this;
        }

        @Override
        public RecordBuilder<T> add(final T key, final DataResult<T> value) {
            builder = ops.getStringValue(key).flatMap(k -> {
                add(k, value);
                return builder;
            });
            return this;
        }

        @Override
        public RecordBuilder<T> add(final DataResult<T> key, final DataResult<T> value) {
            builder = key.flatMap(ops::getStringValue).flatMap(k -> {
                add(k, value);
                return builder;
            });
            return this;
        }

        @Override
        public RecordBuilder<T> withErrorsFrom(final DataResult<?> result) {
            builder = builder.flatMap(v -> result.map(r -> v));
            return this;
        }

        @Override
        public RecordBuilder<T> setLifecycle(final Lifecycle lifecycle) {
            builder = builder.setLifecycle(lifecycle);
            return this;
        }

        @Override
        public RecordBuilder<T> mapError(final Function<String, String> onError) {
            builder = builder.mapError(onError);
            return this;
        }
    }

    abstract class AbstractBuilder<T, R> extends AbstractStringBuilder<T, R> {
        protected AbstractBuilder(final DynamicOps<T> ops) {
            super(ops);
        }

        protected abstract R append(T key, T value, R builder);

        @Override
        protected R append(final String key, final T value, final R builder) {
            return append(ops().createString(key), value, builder);
        }

        @Override
        public RecordBuilder<T> add(final T key, final T value) {
            builder = builder.map(b -> append(key, value, b));
            return this;
        }

        @Override
        public RecordBuilder<T> add(final T key, final DataResult<T> value) {
            builder = builder.apply2((b, v) -> append(key, v, b), value);
            return this;
        }

        @Override
        public RecordBuilder<T> add(final DataResult<T> key, final DataResult<T> value) {
            builder = builder.ap(key.apply2((k, v) -> b -> append(k, v, b), value));
            return this;
        }
    }

    final class MapBuilder<T> extends AbstractBuilder<T, ImmutableMap.Builder<T, T>> {
        public MapBuilder(final DynamicOps<T> ops) {
            super(ops);
        }

        @Override
        protected ImmutableMap.Builder<T, T> initBuilder() {
            return ImmutableMap.builder();
        }

        @Override
        protected ImmutableMap.Builder<T, T> append(final T key, final T value, final ImmutableMap.Builder<T, T> builder) {
            return builder.put(key, value);
        }

        @Override
        protected DataResult<T> build(final ImmutableMap.Builder<T, T> builder, final T prefix) {
            return ops().mergeToMap(prefix, builder.build());
        }
    }
}
