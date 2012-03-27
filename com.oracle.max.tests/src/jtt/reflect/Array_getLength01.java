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
/*
 * @Harness: java
 * @Runs: 0 = 3; 1 = 4; 2 = 1; 3 = !java.lang.NullPointerException
 */
package jtt.reflect;

import java.lang.reflect.*;

public class Array_getLength01 {
    private static final int[] array0 = {11, 21, 42 };
    private static final boolean[] array1 = {true, true, false, false };
    private static final String[] array2 = {"String" };
    public static int test(int i) {
        Object array = null;
        if (i == 0) {
            array = array0;
        } else if (i == 1) {
            array = array1;
        } else if (i == 2) {
            array = array2;
        }
        return Array.getLength(array);
    }
}
