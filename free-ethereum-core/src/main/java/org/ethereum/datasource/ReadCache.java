/*
 * The MIT License (MIT)
 *
 * Copyright 2017 Alexander Orlov <alexander.orlov@loxal.net>. All rights reserved.
 * Copyright (c) [2016] [ <ether.camp> ]
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package org.ethereum.datasource;

import org.apache.commons.collections4.map.LRUMap;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.ByteArrayMap;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Caches entries get/updated and use LRU algo to purge them if the number
 * of entries exceeds threshold.
 *
 * This implementation could extended to estimate cached data size for
 * more accurate size restriction, but if entries are more or less
 * of the same size the entries count would be good enough
 *
 * Another implementation idea is heap sensitive read cache based on
 * SoftReferences, when the cache occupies all the available heap
 * but get shrink when low heap
 *
 * Created by Anton Nashatyrev on 05.10.2016.
 */
public class ReadCache<Key, Value> extends AbstractCachedSource<Key, Value> {

    private final Value NULL = (Value) new Object();

    private Map<Key, Value> cache;
    private boolean byteKeyMap;
    // the guard against incorrect Map implementation for byte[] keys
    private boolean checked = false;

    public ReadCache(final Source<Key, Value> src) {
        super(src);
        withCache(new HashMap<>());
    }

    /**
     * Installs the specific cache Map implementation
     */
    ReadCache<Key, Value> withCache(final Map<Key, Value> cache) {
        byteKeyMap = cache instanceof ByteArrayMap;
        this.cache = Collections.synchronizedMap(cache);
        return this;
    }

    /**
     * Sets the max number of entries to cache
     */
    public ReadCache<Key, Value> withMaxCapacity(final int maxCapacity) {
        return withCache(new LRUMap<Key, Value>(maxCapacity) {
            @Override
            protected boolean removeLRU(final LinkEntry<Key, Value> entry) {
                cacheRemoved(entry.getKey(), entry.getValue());
                return super.removeLRU(entry);
            }
        });
    }

    private void checkByteArrKey(final Key key) {
        if (checked) return;

        if (key instanceof byte[]) {
            if (!byteKeyMap) {
                throw new RuntimeException("Wrong map/set for byte[] key");
            }
        }
        checked = true;
    }

    @Override
    public void put(final Key key, final Value val) {
        checkByteArrKey(key);
        if (val == null) {
            delete(key);
        } else {
            cache.put(key, val);
            cacheAdded(key, val);
            getSource().put(key, val);
        }
    }

    @Override
    public Value get(final Key key) {
        checkByteArrKey(key);
        Value ret = cache.get(key);
        if (ret == NULL) {
            return null;
        }
        if (ret == null) {
            ret = getSource().get(key);
            cache.put(key, ret == null ? NULL : ret);
            cacheAdded(key, ret);
        }
        return ret;
    }

    @Override
    public void delete(final Key key) {
        checkByteArrKey(key);
        final Value value = cache.remove(key);
        cacheRemoved(key, value);
        getSource().delete(key);
    }

    @Override
    protected boolean flushImpl() {
        return false;
    }

    public synchronized Collection<Key> getModified() {
        return Collections.emptyList();
    }

    @Override
    public boolean hasModified() {
        return false;
    }

    @Override
    public synchronized Entry<Value> getCached(final Key key) {
        final Value value = cache.get(key);
        return value == null ? null : new SimpleEntry<>(value == NULL ? null : value);
    }

    /**
     * Shortcut for ReadCache with byte[] keys. Also prevents accidental
     * usage of regular Map implementation (non byte[])
     */
    public static class BytesKey<V> extends ReadCache<byte[], V> implements CachedSource.BytesKey<V> {

        public BytesKey(final Source<byte[], V> src) {
            super(src);
            withCache(new ByteArrayMap<>());
        }

        public ReadCache.BytesKey<V> withMaxCapacity(final int maxCapacity) {
            withCache(new ByteArrayMap<>(new LRUMap<ByteArrayWrapper, V>(maxCapacity) {
                @Override
                protected boolean removeLRU(final LinkEntry<ByteArrayWrapper, V> entry) {
                    cacheRemoved(entry.getKey().getData(), entry.getValue());
                    return super.removeLRU(entry);
                }
            }));
            return this;
        }
    }
}
