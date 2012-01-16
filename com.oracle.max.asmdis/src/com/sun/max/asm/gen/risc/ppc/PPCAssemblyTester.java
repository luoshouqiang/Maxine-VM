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
package com.sun.max.asm.gen.risc.ppc;

import java.io.*;
import java.util.*;

import com.sun.max.asm.*;
import com.sun.max.asm.gen.*;
import com.sun.max.asm.gen.risc.*;
import com.sun.max.io.*;
import com.sun.max.lang.*;

/**
 */
public abstract class PPCAssemblyTester extends RiscAssemblyTester<RiscTemplate> {

    public PPCAssemblyTester(PPCAssembly assembly, WordWidth addressWidth, EnumSet<AssemblyTestComponent> components) {
        super(assembly, addressWidth, components);
    }

    @Override
    public PPCAssembly assembly() {
        return (PPCAssembly) super.assembly();
    }

    @Override
    protected String assemblerCommand() {
        return "as -force_cpusubtype_ALL";
    }

    @Override
    protected void assembleExternally(IndentWriter writer, RiscTemplate template, List<Argument> argumentList, String label) {
        final RiscExternalInstruction instruction = new RiscExternalInstruction(template, argumentList);
        writer.println(instruction.toString());
    }

    @Override
    protected boolean readNop(InputStream stream) throws IOException {
        final int instruction = Endianness.BIG.readInt(stream);
        return instruction == 0x60000000;
    }

    @Override
    protected byte[] readExternalInstruction(PushbackInputStream externalInputStream, RiscTemplate template, byte[] internalBytes) throws IOException {
        final byte[] result = super.readExternalInstruction(externalInputStream, template, internalBytes);

        // Work-around for bug in Apple's version of the GNU 'as' assembler (see javadoc for 'isExternalMTCRFEncodingBroken' for more details)
        if (assembly().isExternalMTCRFEncodingBroken()) {
            if (template.externalName().equals("mtcrf")) {
                // Force 11th bit to 0
                final int bit11Mask = Integer.parseInt("11101111", 2);
                result[1] &= bit11Mask;
            } else if (template.externalName().equals("mtocrf")) {
                // Force 11th bit to 1
                final int bit11Mask = Integer.parseInt("00010000", 2);
                result[1] |= bit11Mask;
            }
        }
        return result;
    }
}
