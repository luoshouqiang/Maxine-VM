/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.asm.target.armv7;

import com.oracle.max.asm.AsmOptions;
import com.oracle.max.asm.Label;
import com.sun.cri.ci.*;
import com.sun.cri.ri.RiRegisterConfig;

import static com.oracle.max.asm.target.armv7.ARMV7.r1;
import static com.oracle.max.asm.target.armv7.ARMV7.r12;

/**
 * This class implements commonly used ARM!!!!! code patterns.
 */
public class ARMV7MacroAssembler extends ARMV7Assembler {

    public ARMV7MacroAssembler(CiTarget target, RiRegisterConfig registerConfig) {
        super(target, registerConfig);
    }

    public void pushptr(CiAddress src) {
        //stm(0xE,0,0,1,1,r13,src)
        // APN obvously we need to figure out how to resolve the CiAddress issue which is explained below.
        // in the next method.
        pushq(src);


        /** APN
         * A CiAddress Represents an address in target machine memory, specified via some combination of a base register, an index register,
         * a displacement and a scale. Note that the base and index registers may be {@link CiVariable variable}, that is as yet
         * unassigned to target machine registers.
         *
         * APN ok so this is problematic, Im not sure when the CiAddress might be lowered to an
         * address that lies in memory, or in a target machine register that we can
         * push onto the stack.
         *
         * prefixq(src)
         * emitByte(0xFF)
         * emitOperandHelper(rsi,src);
         *
         * A CiAddress can represent addresses of the form base + index*scale + displacement.
         * Base & index would be registers.
         *
         */


    }

    public void popptr(CiAddress src) {
        popq(src);
    }
    public final void cmpswapInt(CiRegister newValue, CiAddress address) {
	/* we will use ldrex strex to implement the compare and swap
        */
	setUpScratch(address);
        ldrex(ConditionFlag.Always,ARMV7.r8,ARMV7.r12);
	strex(ConditionFlag.Always,ARMV7.r8,newValue,ARMV7.r12);
	System.out.println("cmpswapInt not implemented");
        
        
    }
    public final void cmpswapLong(CiRegister newValue, CiAddress address) {
	/*
        largely the same as above except we will use ldrexd (dual) and strexd (dual)
        to be able to compare and swap the new value!
        */
	setUpScratch(address);
	System.out.println("cmpswapLong not implemented");
        
    }

    public void xorptr(CiRegister dst, CiRegister src) {
        xorq(dst, src);
    }

    public void xorptr(CiRegister dst, CiAddress src) {
        // APN I have assumed we do not need to load the CiAddress?
        // is this incorrect?
        xorq(dst, src);
    }

    // 64 bit versions

    public void decrementq(CiRegister reg, int value) {
        if (value == Integer.MIN_VALUE) {
            subq(reg, value);
            return;
        }
        if (value < 0) {
            incrementq(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && AsmOptions.UseIncDec) {
            decq(reg);
        } else {
            subq(reg, value);
        }
    }

    public void incrementq(CiRegister reg, int value) {
        if (value == Integer.MIN_VALUE) {
            addq(reg, value);
            return;
        }
        if (value < 0) {
            decrementq(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && AsmOptions.UseIncDec) {
            incq(reg);
        } else {
            addq(reg, value);
        }
    }

    // These are mostly for initializing null
    public void movptr(CiAddress dst, int src) {
        movslq(dst, src);
    }

    public final void cmp32(CiRegister src1, int imm) {
        cmpl(src1, imm);
    }

    public final void cmp32(CiRegister src1, CiAddress src2) {
        cmpl(src1, src2);
    }

    public void cmpsd2int(CiRegister opr1, CiRegister opr2, CiRegister dst, boolean unorderedIsLess) {
        assert opr1.isFpu() && opr2.isFpu();
        ucomisd(opr1, opr2);
       // assert !opr1.isFpu(); //force crash as not implemented yet.
        // get condition codes. don't set
        // FPSCR register  flags
        // [31] N Set to 1 if a comparison operation produces a less than result.
        // 30] Z Set to 1 if a comparison operation produces an equal result.
        //[29] C Set to 1 if a comparison operation produces an equal, greater than, or unordered result.
        //[28] V Set to 1 if a comparison operation produces an unordered result. SAME as parity flag?
        // insert the statement to do this
        // do appropriate conditional moves in ARM ...


        // dont need it in ARM?
        Label l = new Label();
        /*
        APN please bear in mind I am a novice x86er
        My reading is that this code jumps to the label if the conditions are true
        dst is given the value -1 iff opr1 <- opr2
        0 -ff opr1 == opr2
        1 iff opr1 > opr2
        */
        if (unorderedIsLess) {
            mov32BitConstant(dst, -1);
            //jcc(ARMV7Assembler.ConditionFlag.parity, l);
            //jcc(ARMV7Assembler.ConditionFlag.below, l);
            jcc(ConditionFlag.SignedOverflow,l);
            mov32BitConstant(dst, 0);
            //jcc(ARMV7Assembler.ConditionFlag.equal, l);
            incrementl(dst, 1);
        } else { // unordered is greater
            mov32BitConstant(dst, 1);
            //jcc(ARMV7Assembler.ConditionFlag.parity, l);
            //jcc(ARMV7Assembler.ConditionFlag.above, l);
            jcc(ConditionFlag.SignedOverflow, l);
            mov32BitConstant(dst, 0);
            //jcc(ARMV7Assembler.ConditionFlag.equal, l);

            decrementl(dst, 1);
        }

        // don't need it in ARM
        bind(l);
    }

    public void cmpss2int(CiRegister opr1, CiRegister opr2, CiRegister dst, boolean unorderedIsLess) {
        assert opr1.isFpu();
        assert opr2.isFpu();
        System.out.println("ARMV7MAcroAssembler cmpss2int not tested");
        ucomisd(opr1, opr2);
        //assert !opr1.isFpu(); // APN force crash as not yet implemented
        Label l = new Label();
        if (unorderedIsLess) {
            mov32BitConstant(dst, -1);
            //jcc(ARMV7Assembler.ConditionFlag.parity, l);
            //jcc(ARMV7Assembler.ConditionFlag.below, l);
            jcc(ConditionFlag.SignedOverflow,l);
            mov32BitConstant(dst, 0);
            //jcc(ARMV7Assembler.ConditionFlag.equal, l);
            incrementl(dst, 1);
        } else { // unordered is greater
            mov32BitConstant(dst, 1);
            //jcc(ARMV7Assembler.ConditionFlag.parity, l);
            //jcc(ARMV7Assembler.ConditionFlag.above, l);
            jcc(ConditionFlag.SignedOverflow, l);
            mov32BitConstant(dst, 0);
            //jcc(ARMV7Assembler.ConditionFlag.equal, l);

            decrementl(dst, 1);
        }
        bind(l);
        /*ucomiss(opr1, opr2);

        Label l = new Label();
        if (unorderedIsLess) {
            movl(dst, -1);
            jcc(ARMV7Assembler.ConditionFlag.parity, l);
            jcc(ARMV7Assembler.ConditionFlag.below, l);
            movl(dst, 0);
            jcc(ARMV7Assembler.ConditionFlag.equal, l);
            incrementl(dst, 1);
        } else { // unordered is greater
            movl(dst, 1);
            jcc(ARMV7Assembler.ConditionFlag.parity, l);
            jcc(ARMV7Assembler.ConditionFlag.above, l);
            movl(dst, 0);
            jcc(ARMV7Assembler.ConditionFlag.equal, l);
            decrementl(dst, 1);
        }
        bind(l);
        */
    }
    public void cmpq(CiRegister src1,CiRegister src2) {
        cmp(ConditionFlag.Always,src1,src2,0,0);
    }
    public void cmpptr(CiRegister src1, CiRegister src2) {
        //cmpq(src1, src2);
        cmp(ConditionFlag.Always,src1,src2,0,0);
    }

    public void cmpptr(CiRegister src1, CiAddress src2) {
        setUpScratch(src2);
        assert(ARMV7.r12.number != src1.number);
        ldr(ConditionFlag.Always,ARMV7.r12,ARMV7.r12,0); // TODO is this necessary or is the address the pointer?
        cmp(ConditionFlag.Always,src1,ARMV7.r12,0,0);
        //cmpq(src1, src2);
    }

    public void cmpptr(CiRegister src1, int src2) {
        //cmpq(src1, src2);
        assert(ARMV7.r12.number != src1.number);
        mov32BitConstant(ARMV7.r12,src2);
        cmp(ConditionFlag.Always,src1,ARMV7.r12,0,0);
    }

    public void cmpptr(CiAddress src1, int src2) {
        setUpScratch(src1);
        mov32BitConstant(ARMV7.r8,src2);
        cmp(ConditionFlag.Always,ARMV7.r12,ARMV7.r8,0,0);
        //cmpq(src1, src2);
    }

    public void decrementl(CiRegister reg, int value) {
        CiRegister tmp;
        if(reg == ARMV7.r12) {
            tmp = ARMV7.r8;
        } else {
            tmp = ARMV7.r9;
        }
        mov32BitConstant(tmp,value);
        sub(ConditionFlag.Always,false,reg,tmp,0,0);
        /*if (value == Integer.MIN_VALUE) {
            subl(reg, value);
            return;
        }
        if (value < 0) {
            incrementl(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && AsmOptions.UseIncDec) {
            decl(reg);
        } else {
            subl(reg, value);
        } */
    }

    public void decrementl(CiAddress dst, int value) {
        if(value == 0) {
            return;
        }
        setUpScratch(dst);
        ldr(ConditionFlag.Always,ARMV7.r8,ARMV7.r12,0);
        mov32BitConstant(ARMV7.r9,value);
        sub(ConditionFlag.Always,false,ARMV7.r8,ARMV7.r9,0,0);
        strImmediate(ConditionFlag.Always,0,0,0,ARMV7.r8,ARMV7.r12,0);

        /*if (value == Integer.MIN_VALUE) {
            subl(dst, value);
            return;
        }
        if (value < 0) {
            incrementl(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && AsmOptions.UseIncDec) {
            decl(dst);
        } else {
            subl(dst, value);
        } */
    }

    public void incrementl(CiRegister reg, int value) {
        if (value == 0) { return; }

        addq(reg, value);
        /*if (value == Integer.MIN_VALUE) {
            addl(reg, value);
            return;
        }
        if (value < 0) {
            decrementl(reg, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && AsmOptions.UseIncDec) {
            incl(reg);
        } else {
            addl(reg, value);
        } */
    }

    public void incrementl(CiAddress dst, int value) {
        if (value == 0) { return; }
        setUpScratch(dst);
        mov32BitConstant(ARMV7.r8,value);
        ldr(ConditionFlag.Always,ARMV7.r9,r12,0);
        addRegisters(ConditionFlag.Always,false,ARMV7.r9,ARMV7.r9,ARMV7.r8,0,0);
        str(ConditionFlag.Always,ARMV7.r9,r12,0);

        /*if (value == Integer.MIN_VALUE) {
            addl(dst, value);
            return;
        }
        if (value < 0) {
            decrementl(dst, -value);
            return;
        }
        if (value == 0) {
            return;
        }
        if (value == 1 && AsmOptions.UseIncDec) {
            incl(dst);
        } else {
            addl(dst, value);
        } */
    }

    public void signExtendByte(CiRegister dest, CiRegister reg) {
        ishl(dest, reg, 24);
        iushr(dest, dest, 24);
    }

    public void signExtendShort(CiRegister dest, CiRegister reg) {
        ishl(dest, reg, 16);
        iushr(dest, dest, 16);
    }

    // Support optimal SSE move instructions.
    public void movflt(CiRegister dst, CiRegister src) {
        //System.out.println("movflt dst " + dst.number + " src " + src.number);
        vmov(ConditionFlag.Always,dst,src);
       /* assert dst.isFpu() && src.isFpu();
        if (AsmOptions.UseXmmRegToRegMoveAll) {
            movaps(dst, src);
            ,
        } else {
            movss(dst, src);
        }
        */
    }

    public void movflt(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        /*movss(dst, src);
        */
        setUpScratch(src);
        vldr(ConditionFlag.Always,dst,r12,0);

    }

    public void movflt(CiAddress dst, CiRegister src) {
        assert src.isFpu();
        /*movss(dst, src);
        */
        setUpScratch(dst);
        vstr(ConditionFlag.Always,src,ARMV7.r12,0);
    }

    public void movdbl(CiRegister dst, CiRegister src) {
        assert dst.isFpu() && src.isFpu();
        vmov(ConditionFlag.Always,dst,src);
        if(AsmOptions.UseXmmRegToRegMoveAll) {
            System.out.println("MOVE ALL XMM");
        }
        /*if (AsmOptions.UseXmmRegToRegMoveAll) {
            movapd(dst, src);
        } else {
            movsd(dst, src);
        }
        */
    }

    public void movdbl(CiRegister dst, CiAddress src) {
        assert dst.isFpu();
        setUpScratch(src);
        vldr(ConditionFlag.Always,dst,r12,0);
        /*if (AsmOptions.UseXmmLoadAndClearUpper) {
            movsd(dst, src);
        } else {
            movlpd(dst, src);
        } */
    }

    public void movdbl(CiAddress dst, CiRegister src) {
       /* assert src.isFpu();
        movsd(dst, src); */
        setUpScratch(dst);
        if(src.number > 15) {
            vstr(ConditionFlag.Always, src, r12,  0);
        } else {

        }

    }

    public void movlong(CiRegister dst, long src) {
        if(dst.number < 16) { // move to ARM CORE register
            //System.out.println("Register: " + dst.encoding);
            mov64BitConstant(dst, ARMV7.cpuRegisters[dst.encoding+1], src);
        } else {
            assert dst.number < 32 : "ERROR movlong in ARMV7MacroAssembler movlong to FPRegs";
            System.out.println("movlong " + Long.toHexString(src));
            mov32BitConstant(ARMV7.r8, (int) (0xffffffffL & src));
            mov32BitConstant(ARMV7.r9, (int) ((src >> 32) & 0xffffffffL));
            vmov(ConditionFlag.Always,dst,ARMV7.r8);
        }
    }
    /**
     * Non-atomic write of a 64-bit constant to memory. Do not use
     * if the address might be a volatile field!
     */
    public void movlong(CiAddress dst, long src) {
        // APN ARM layout and endianness?
        // might be the wrong layout of values in addresses
        // this all seems a  bit tricky with ARM
        // AS a hack suggestion is to push a couple of registers onto the stack
        // load them with the constanv values

        push(ConditionFlag.Always, (1 << 12) | 1 | 2); // r13 is the stack pointer
        setUpScratch(dst);
        mov32BitConstant(ARMV7.r0, (int) (0xffffffffL & src));
        mov32BitConstant(ARMV7.r1, (int) ((src >> 32) & 0xffffffffL));
        str(ConditionFlag.Always, 0, 0, 0, ARMV7.r0, ARMV7.r12, ARMV7.r0, 0, 0);
        add(ConditionFlag.Always, false, ARMV7.r12, ARMV7.r12, 4, 0); // add 4 to the address
        str(ConditionFlag.Always, 0, 0, 0, ARMV7.r1, ARMV7.r12, ARMV7.r1, 0, 0);
        pop(ConditionFlag.Always, (1 << 12) | 1 | 2); //restore all
        //CiAddress high = new CiAddress(dst.kind, dst.base, dst.index, dst.scale, dst.displacement + 4);

        /*
        movl(dst, (int) (src & 0xFFFFFFFF));
        movl(high, (int) (src >> 32));*/
    }

    public void xchgptr(CiRegister src1, CiRegister src2) {
        //xchgq(src1, src2);
    }

    public void flog(CiRegister dest, CiRegister value, boolean base10) {
        /*assert value.spillSlotSize == dest.spillSlotSize;

        CiAddress tmp = new CiAddress(CiKind.Double, ARMV7.RSP);
        if (base10) {
            fldlg2();
        } else {
            fldln2();
        }
        subq(ARMV7.rsp, value.spillSlotSize);
        movsd(tmp, value);
        fld(tmp);
        fyl2x();
        fstp(tmp);
        movsd(dest, tmp);
        addq(ARMV7.rsp, dest.spillSlotSize);
        */
    }

    public void fsin(CiRegister dest, CiRegister value) {
        //ftrig(dest, value, 's');
    }

    public void fcos(CiRegister dest, CiRegister value) {
       // ftrig(dest, value, 'c');
    }

    public void ftan(CiRegister dest, CiRegister value) {
        //ftrig(dest, value, 't');
    }

    private void ftrig(CiRegister dest, CiRegister value, char op) {
        /*assert value.spillSlotSize == dest.spillSlotSize;

        CiAddress tmp = new CiAddress(CiKind.Double, ARMV7.RSP);
        subq(ARMV7.rsp, value.spillSlotSize);
        movsd(tmp, value);
        fld(tmp);
        if (op == 's') {
            fsin();
        } else if (op == 'c') {
            fcos();
        } else if (op == 't') {
            fptan();
            fstp(0); // ftan pushes 1.0 in addition to the actual result, pop
        } else {
            throw new InternalError("should not reach here");
        }
        fstp(tmp);
        movsd(dest, tmp);
        addq(ARMV7.rsp, dest.spillSlotSize);
        */
    }

    /**
     * Emit code to save a given set of callee save registers in the
     * {@linkplain CiCalleeSaveLayout CSA} within the frame.
     * @param csl the description of the CSA
     * @param frameToCSA offset from the frame pointer to the CSA
     */
    public void save(CiCalleeSaveLayout csl, int frameToCSA) {
        CiRegisterValue frame = frameRegister.asValue();
        for (CiRegister r : csl.registers) {
            int offset = csl.offsetOf(r);
            //movq(new CiAddress(target.wordKind, frame, frameToCSA + offset), r);
            setUpScratch(new CiAddress(target.wordKind, frame, frameToCSA + offset));
            if(r.number < 16) {
                strImmediate(ConditionFlag.Always,1,0,0,r,r12,0);
            }else {
                vstr(ConditionFlag.Always,r,r12,0);
            }
        }
    }

    public void restore(CiCalleeSaveLayout csl, int frameToCSA) {
        CiRegisterValue frame = frameRegister.asValue();
        for (CiRegister r : csl.registers) {
            int offset = csl.offsetOf(r);
            // movq(r, new CiAddress(target.wordKind, frame, frameToCSA + offset));
            setUpScratch(new CiAddress(target.wordKind, frame, frameToCSA + offset));
            if (r.number < 16) {
                ldrImmediate(ConditionFlag.Always, 0, 0, 0, r, r12, 0);
            } else {
                vldr(ConditionFlag.Always, r, r12, 0);
            }
        }
    }

    public void imul(CiRegister dest, CiRegister left, CiRegister right) {
        mul(ConditionFlag.Always, true, dest, left, right);
    }

    public void isub(CiRegister dest, CiRegister left, CiRegister right) {
        sub(ConditionFlag.Always, true, dest, left, right, 0, 0);
    }

    public void iadd(CiRegister dest, CiRegister left, CiRegister right) {
        addRegisters(ConditionFlag.Always, true, dest, left, right, 0, 0);
    }

    public void iadd(CiRegister dest, CiRegister left, CiAddress right) {
        setUpScratch(right);
        ldrImmediate(ConditionFlag.Always, 0, 0, 0, r1, r12, 0);
        addRegisters(ConditionFlag.Always, true, dest, left, r1, 0, 0);
    }

    public void isub(CiRegister dest, CiRegister left, CiAddress right) {
        setUpScratch(right);
        ldrImmediate(ConditionFlag.Always, 0, 0, 0, r1, r12, 0);
        sub(ConditionFlag.Always, true, dest, left, r1, 0, 0);
    }

    public void ineg(CiRegister dest, CiRegister left) {
        neg(ConditionFlag.Always, true, dest, left, 0);
    }

    public void lneg(CiRegister dest, CiRegister left) {
        rsb(ConditionFlag.Always, true, dest, left, 0, 0);
        rsc(ConditionFlag.Always, false, registerConfig.getAllocatableRegisters()[dest.number + 1], registerConfig.getAllocatableRegisters()[left.number + 1], 0);
    }

    public void ior(CiRegister dest, CiRegister left, CiRegister right) {
        orr(ConditionFlag.Always, true, dest, left, right, 0, 0);
    }

    public void lor(CiRegister dest, CiRegister left, CiRegister right) {
        orr(ConditionFlag.Always, true, dest, left, right, 0, 0);
        orr(ConditionFlag.Always, true, registerConfig.getAllocatableRegisters()[dest.number + 1], registerConfig.getAllocatableRegisters()[left.number + 1],
                        registerConfig.getAllocatableRegisters()[right.number + 1], 0, 0);
    }

    public void ixor(CiRegister dest, CiRegister left, CiRegister right) {
        eor(ConditionFlag.Always, true, dest, left, right, 0, 0);
    }

    public void lxor(CiRegister dest, CiRegister left, CiRegister right) {
        eor(ConditionFlag.Always, true, dest, left, right, 0, 0);
        eor(ConditionFlag.Always, true, registerConfig.getAllocatableRegisters()[dest.number + 1], registerConfig.getAllocatableRegisters()[left.number + 1],
                        registerConfig.getAllocatableRegisters()[right.number + 1], 0, 0);
    }

    public void iand(CiRegister dest, CiRegister left, CiRegister right) {
        and(ConditionFlag.Always, true, dest, left, right, 0, 0);
    }

    public void land(CiRegister dest, CiRegister left, CiRegister right) {
        and(ConditionFlag.Always, true, dest, left, right, 0, 0);
        and(ConditionFlag.Always, true, registerConfig.getAllocatableRegisters()[dest.number + 1], registerConfig.getAllocatableRegisters()[left.number + 1],
                        registerConfig.getAllocatableRegisters()[right.number + 1], 0, 0);
    }

    public void ishl(CiRegister dest, CiRegister left, int amount) {
        lsl(ConditionFlag.Always, true, dest, left, amount);
    }

    public void ishl(CiRegister dest, CiRegister left, CiRegister right) {
        lsl(ConditionFlag.Always, true, dest, right, left);
    }

    public void lshl(CiRegister dest, CiRegister left, CiRegister right) {
        sub(ConditionFlag.Always, false, registerConfig.getAllocatableRegisters()[9], right, 32, 0);
        rsb(ConditionFlag.Always, false, registerConfig.getAllocatableRegisters()[10], right, 32, 0);
        lsl(ConditionFlag.Always, false, registerConfig.getAllocatableRegisters()[dest.number + 1], right, registerConfig.getAllocatableRegisters()[left.number + 1]);
        orsr(ConditionFlag.Always, false, registerConfig.getAllocatableRegisters()[dest.number + 1], registerConfig.getAllocatableRegisters()[dest.number + 1], left,
                        registerConfig.getAllocatableRegisters()[9], 0);
        orsr(ConditionFlag.Always, false, registerConfig.getAllocatableRegisters()[dest.number + 1], registerConfig.getAllocatableRegisters()[dest.number + 1], left,
                        registerConfig.getAllocatableRegisters()[10], 1);
        lsl(ConditionFlag.Always, false, dest, right, registerConfig.getAllocatableRegisters()[left.number]);
    }

    public void ishr(CiRegister dest, CiRegister left, int amount) {
        lsr(ConditionFlag.Always, true, dest, left, amount);
    }

    public void ishr(CiRegister dest, CiRegister left, CiRegister right) {
        lsr(ConditionFlag.Always, true, dest, right, left);
    }

    public void lshr(CiRegister dest, CiRegister left, CiRegister right) {
        rsb(ConditionFlag.Always, false, registerConfig.getAllocatableRegisters()[10], right, 32, 0);
        sub(ConditionFlag.Always, false, registerConfig.getAllocatableRegisters()[9], right, 32, 0);

        lsr(ConditionFlag.Always, false, dest, right, registerConfig.getAllocatableRegisters()[left.number]);
        orsr(ConditionFlag.Always, false, dest, left, registerConfig.getAllocatableRegisters()[left.number + 1],
                        registerConfig.getAllocatableRegisters()[10], 0);
        orsr(ConditionFlag.Always, false, dest, left, registerConfig.getAllocatableRegisters()[left.number + 1],
                        registerConfig.getAllocatableRegisters()[9], 1);
        lsr(ConditionFlag.Always, false, registerConfig.getAllocatableRegisters()[dest.number + 1], right, registerConfig.getAllocatableRegisters()[left.number + 1]);
    }

    public void iushr(CiRegister dest, CiRegister left, int amount) {
        lusr(ConditionFlag.Always, true, dest, left, amount);
    }

    public void iushr(CiRegister dest, CiRegister left, CiRegister right) {
        lusr(ConditionFlag.Always, true, dest, right, left);
    }

    public void lushr(CiRegister dest, CiRegister left, CiRegister right) {
        Label l = new Label();
        rsb(ConditionFlag.Always, false, registerConfig.getAllocatableRegisters()[10], right, 32, 0);
        sub(ConditionFlag.Always, true, registerConfig.getAllocatableRegisters()[9], right, 32, 0);

        lsr(ConditionFlag.Always, false, registerConfig.getAllocatableRegisters()[dest.number + 1], right, registerConfig.getAllocatableRegisters()[left.number + 1]);
        orsr(ConditionFlag.Always, false, registerConfig.getAllocatableRegisters()[dest.number + 1], registerConfig.getAllocatableRegisters()[dest.number + 1], left,
                        registerConfig.getAllocatableRegisters()[9], 0);
        jcc(ConditionFlag.Minus, l);
        orsr(ConditionFlag.Always, false, registerConfig.getAllocatableRegisters()[dest.number + 1], registerConfig.getAllocatableRegisters()[dest.number + 1], left,
                        registerConfig.getAllocatableRegisters()[9], 2);
        bind(l);
        asrr(ConditionFlag.Always, false, dest, right, registerConfig.getAllocatableRegisters()[left.number]);
    }
}
