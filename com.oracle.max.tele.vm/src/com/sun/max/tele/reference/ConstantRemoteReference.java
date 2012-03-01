/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tele.reference;

import com.sun.max.tele.*;
import com.sun.max.unsafe.*;

/**
 * A fixed, remote {@link Address} in VM memory, wrapped as if it were a {@link Reference}
 * so that it can be manipulated with standard VM code.
 * <p>
 * Equality for instances is defined as {@link Address} equality with other instances of the class.
 *
 * @see Reference
 * @see VmReferenceManager
 */
public abstract class ConstantRemoteReference extends RemoteReference {

    private final Address raw;

    protected ConstantRemoteReference(TeleVM vm, Address rawRef) {
        super(vm);
        raw = rawRef;
    }

    /**
     * {@inheritDoc}
     * <p>
     * In this implementation, the remote location does not change, so this method is constant.
     */
    @Override
    public final Address raw() {
        return raw;
    }

    @Override
    public final boolean equals(Object other) {
        if (other instanceof ConstantRemoteReference) {
            final ConstantRemoteReference constantTeleRef = (ConstantRemoteReference) other;
            return raw.equals(constantTeleRef.raw);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return raw.toInt();
    }

    @Override
    public String toString() {
        return raw().toString();
    }

}