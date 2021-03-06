/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.max.collect;

import com.sun.max.*;

/**
 * Defines the semantics of key comparison in a {@linkplain HashMapping hash based data structure} that delegate to
 * {@link Object#equals(Object)} and {@link Object#hashCode()}. There is a {@linkplain #instance(Class) singleton}
 * instance of this class.
 */
public final class HashEquality<T> implements HashEquivalence<T> {

    private HashEquality() {
    }

    public boolean equivalent(T object1, T object2) {
        if (object1 == null) {
            return object2 == null;
        }
        return object1.equals(object2);
    }

    public int hashCode(T object) {
        if (object == null) {
            return 0;
        }
        return object.hashCode();
    }

    private static final HashEquality equality = new HashEquality<Object>();

    public static <T> HashEquality<T> instance(Class<HashEquality<T>> type) {
        return Utils.cast(type, equality);
    }
}
