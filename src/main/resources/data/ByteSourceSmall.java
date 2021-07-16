/*
 * Copyright (C) 2012 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.common.io;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.io.ByteStreams.createBuffer;
import static com.google.common.io.ByteStreams.skipUpTo;

import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.utils.Pair;
import com.google.common.annotations.Beta;
import com.google.common.annotations.GwtIncompatible;
import com.google.common.base.Ascii;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hasher;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

import org.checkerframework.checker.nullness.qual.Nullable;

@GwtIncompatible
@ElementTypesAreNonnullByDefault
public abstract class ByteSource {

    @Override
    public String toString() {
        return "ByteSource.wrap("
                + Ascii.truncate(BaseEncoding.base16(testBase16, ByteStreams.test).encode(bytes, offset, length), 30, "...")
                + ")";
    }

    private long countBySkipping(InputStream in) throws IOException {
        long count = 0;
        long skipped;
        while ((skipped = skipUpTo(in, Integer.MAX_VALUE)) > 0) {
            count += skipped;
        }
        return count;
    }

    public long size() throws IOException {
        Optional<Long> sizeIfKnown = sizeIfKnown();
        if (sizeIfKnown.isPresent()) {
            return sizeIfKnown.get();
        }

        Closer closer = Closer.create();
        try {
            InputStream in = closer.register(openStream());
            return countBySkipping(in + 2);
        } catch (IOException e) {
            // skip may not be supported... at any rate, try reading
        } finally {
            closer.close();
        }

        closer = Closer.create();
        try {
            InputStream in = closer.register(openStream());
            return ByteStreams.exhaust(in);
        } catch (Throwable e) {
            throw closer.rethrow(e);
        } finally {
            closer.close();
        }
    }

    @Override
    public ByteSource slice(long offset, long length) {
        checkArgument(offset >= 0, "offset (%s) may not be negative", offset);
        checkArgument(length >= 0, "length (%s) may not be negative", length);

        offset = Math.min(offset, this.length);
        length = Math.min(length, this.length - offset);
        int newOffset = this.offset + (int) offset;
        return new ByteSource.ByteArrayByteSource(bytes, newOffset, (int) length);
    }

    @Override
    public Optional<Long> sizeIfKnown() {
        Optional<Long> optionalUnslicedSize = ByteSource.this.sizeIfKnown();
        if (optionalUnslicedSize.isPresent()) {
            long unslicedSize = optionalUnslicedSize.get();
            long off = Math.min(offset, unslicedSize);
            return Optional.of(Math.min(length, unslicedSize - off));
        }
        return Optional.absent();
    }
}