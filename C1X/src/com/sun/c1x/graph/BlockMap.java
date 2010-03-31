/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.c1x.graph;

import static com.sun.c1x.bytecode.Bytecodes.*;

import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.bytecode.*;
import com.sun.c1x.ir.*;
import com.sun.c1x.ri.*;
import com.sun.c1x.util.*;

/**
 * Builds a mapping between bytecodes and basic blocks and builds a conservative control flow
 * graph. Note that this class serves a similar role to C1's {@code BlockListBuilder}, but makes fewer assumptions about
 * what the compiler interface provides. It builds all basic blocks for the control flow graph without requiring the
 * compiler interface to provide a bitmap of the beginning of basic blocks. It makes two linear passes; one over the
 * bytecodes to build block starts and successor lists, and one pass over the block map to build the CFG.
 *
 * Note that the CFG built by this class is <i>not</i> connected to the actual {@code BlockBegin} instances; this class
 * does, however, compute and assign the reverse postorder number of the blocks. This comment needs refinement. (MJJ)
 *
 * <H2>More Details on {@link BlockMap#build}</H2>
 *
 * If the method has any exception handlers the {@link #exceptionMap} will be created (TBD).
 *
 * A {@link BlockBegin} node with the {@link com.sun.c1x.ir.BlockBegin.BlockFlag#StandardEntry} flag is created with bytecode index 0.
 * Note this is distinct from the similar {@code BlockBegin} node assigned to {@code startBlock} by
 * {@link com.sun.c1x.graph.GraphBuilder}.
 *
 * The bytecodes are then scanned linearly looking for bytecodes that contain control transfers, e.g., {@code GOTO},
 * {@code RETURN}, {@code IFGE}, and creating the corresponding entries in {@link #successorMap} and {@link #blockMap}.
 * In addition, if {@link #exceptionMap} is not null, entries are made for any bytecode that can cause an exception.
 * More TBD.
 *
 * Observe that this process finds bytecodes that terminate basic blocks, so the {@link #moveSuccessorLists} method is
 * called to reassign the successors to the {@code BlockBegin} node that actually starts the block.
 *
 * <H3>Example</H3>
 *
 * Consider the following source code:
 *
 * <pre>
 * <code>
 *     public static int test(int arg1, int arg2) {
 *         int x = 0;
 *         while (arg2 > 0) {
 *             if (arg1 > 0) {
 *                 x += 1;
 *             } else if (arg1 < 0) {
 *                 x -= 1;
 *             }
 *         }
 *         return x;
 *     }
 * </code>
 * </pre>
 *
 * This is translated by javac to the following bytecode:
 *
 * <pre>
 * <code>
 *    0:   iconst_0
 *    1:   istore_2
 *    2:   goto    22
 *    5:   iload_0
 *    6:   ifle    15
 *    9:   iinc    2, 1
 *    12:  goto    22
 *    15:  iload_0
 *    16:  ifge    22
 *    19:  iinc    2, -1
 *    22:  iload_1
 *    23:  ifgt    5
 *    26:  iload_2
 *    27: ireturn
 *    </code>
 * </pre>
 *
 * There are seven basic blocks in this method, 0..2, 5..6, 9..12, 15..16, 19..19, 22..23 and 26..27. Therefore, before
 * the call to {@code moveSuccessorLists}, the {@code blockMap} array has {@code BlockBegin} nodes at indices 0, 5, 9,
 * 15, 19, 22 and 26. The {@code successorMap} array has entries at 2, 6, 12, 16, 23, 27 corresponding to the control
 * transfer bytecodes. The entry at index 6, for example, is a length two array of {@code BlockBegin} nodes for indices
 * 9 and 15, which are the successors for the basic block 5..6. After the call to {@code moveSuccessors}, {@code
 * successorMap} has entries at 0, 5, 9, 15, 19, 22 and 26, i.e, matching {@code blockMap}.
 * <p>
 * Next the blocks are numbered using <a href="http://en.wikipedia.org/wiki/Depth-first_search#Vertex_orderings">reverse
 * post-order</a>. For the above example this results in the numbering 2, 4, 7, 5, 6, 3, 8. Also loop header blocks are
 * detected during the traversal by detecting a repeat visit to a block that is still being processed. This causes the
 * block to be flagged as a loop header and also added to the {@link #loopBlocks} list. The {@code loopBlocks} list
 * contains the blocks at 0, 5, 9, 15, 19, 22, with 22 as the loop header. (N.B. the loop header block is added multiple
 * (4) times to this list). (Should 0 be in? It's not inside the loop).
 *
 * If the {@code computeStoresInLoops} argument to {@code build} is true, the {@code loopBlocks} list is processed to
 * mark all local variables that are stored in the blocks in the list.
 *
 *
 * @author Ben L. Titzer
 */
public class BlockMap {

    private static final BlockBegin[] NONE = new BlockBegin[0];
    private static final List<BlockBegin> NONE_LIST = Util.uncheckedCast(Collections.EMPTY_LIST);

    /**
     * The {@code ExceptionMap} class is used internally to track exception handlers
     * while iterating over the bytecode and the control flow graph. Since methods with
     * exception handlers are much less frequent than those without, the common case
     * does not need to construct an exception map.
     */
    private class ExceptionMap {
        private final BitMap canTrap;
        private final boolean isObjectInit;
        private final List<RiExceptionHandler> allHandlers;
        private final ArrayMap<HashSet<BlockBegin>> handlerMap;

        ExceptionMap(RiMethod method, byte[] code) {
            canTrap = new BitMap(code.length);
            isObjectInit = C1XOptions.GenFinalizerRegistration && C1XIntrinsic.getIntrinsic(method) == C1XIntrinsic.java_lang_Object$init;
            allHandlers = method.exceptionHandlers();
            handlerMap = new ArrayMap<HashSet<BlockBegin>>(firstBlock, firstBlock + code.length / 5);
        }

        void setCanTrap(int bci) {
            canTrap.set(bci);
        }

        void addHandlers(BlockBegin block, int bci) {
            if (canTrap.get(bci)) {
                // XXX: replace with faster algorithm (sort exception handlers by start and end)
                for (RiExceptionHandler h : allHandlers) {
                    if (h.startBCI() <= bci && bci < h.endBCI()) {
                        addHandler(block, get(h.handlerBCI()));
                        if (h.isCatchAll()) {
                            break;
                        }
                    }
                }
            }
        }

        Iterable<BlockBegin> getHandlers(BlockBegin block) {
            // lookup handlers for the basic block
            HashSet<BlockBegin> set = handlerMap.get(block.blockID);
            return set == null ? NONE_LIST : set;
        }

        void setHandlerEntrypoints() {
            // start basic blocks at all exception handler blocks and mark them as exception entries
            for (RiExceptionHandler h : allHandlers) {
                addEntrypoint(h.handlerBCI(), BlockBegin.BlockFlag.ExceptionEntry);
            }
        }

        void addHandler(BlockBegin block, BlockBegin handler) {
            // add a handler to a basic block, creating the set if necessary
            HashSet<BlockBegin> set = handlerMap.get(block.blockID);
            if (set == null) {
                set = new HashSet<BlockBegin>();
                handlerMap.put(block.blockID, set);
            }
            set.add(handler);
        }
    }

    /** The bytecodes for the associated method. */
    private final byte[] code;
    /**
     * Every {@link BlockBegin} node created by {@link BlockMap#build} has an entry in this
     * array at the corresponding bytecode index. Length is same as {@link BlockMap#code}.
     */
    private final BlockBegin[] blockMap;
    /**
     * TBD.
     */
    private final BitMap storesInLoops;
    /**
     * Every bytecode instruction that has zero, one or more successor nodes (e.g. {@link Bytecodes#GOTO} has one) has
     * an entry in this array at the corresponding bytecode index. The value is another array of {@code BlockBegin} nodes,
     * with length equal to the number of successors, whose entries are the {@code BlockBegin} nodes for the successor
     * blocks. Length is same as {@link BlockMap#code}.
     */
    private BlockBegin[][] successorMap;
    /** List of {@code BlockBegin} nodes that are inside loops. */
    private ArrayList<BlockBegin> loopBlocks;
    private ExceptionMap exceptionMap;
    private final int firstBlock;
    /** Used for initial block ID (count up) and post-order number (count down) */
    private int blockNum; //

    /**
     * Creates a new BlockMap instance from bytecode of the given method .
     * @param method the compiler interface method containing the code
     * @param firstBlockNum the first block number to use when creating {@link BlockBegin} nodes
     */
    public BlockMap(RiMethod method, int firstBlockNum) {
        byte[] code = method.code();
        this.code = code;
        firstBlock = firstBlockNum;
        blockNum = firstBlockNum;
        blockMap = new BlockBegin[code.length];
        successorMap = new BlockBegin[code.length][];
        storesInLoops = new BitMap(method.maxLocals());
        if (method.hasExceptionHandlers()) {
            exceptionMap = new ExceptionMap(method, code);
        }
    }

    /**
     * Add an entrypoint to this BlockMap. The resulting block will be marked
     * with the specified block flags.
     * @param bci the bytecode index of the start of the block
     * @param entryFlag the entry flag to mark the block with
     */
    public void addEntrypoint(int bci, BlockBegin.BlockFlag entryFlag) {
        make(bci).setBlockFlag(entryFlag);
    }

    /**
     * Gets the block that begins at the specified bytecode index.
     * @param bci the bytecode index of the start of the block
     * @return the block starting at the specified index, if it exists; {@code null} otherwise
     */
    public BlockBegin get(int bci) {
        if (bci < blockMap.length) {
            return blockMap[bci];
        }
        return null;
    }

    BlockBegin make(int bci) {
        BlockBegin block = blockMap[bci];
        if (block == null) {
            block = new BlockBegin(bci, blockNum++);
            blockMap[bci] = block;
        }
        return block;
    }

    /**
     * Gets a conservative approximation of the successors of a given block.
     * @param block the block for which to get the successors
     * @return an array of the successors of the specified block
     */
    public BlockBegin[] getSuccessors(BlockBegin block) {
        return successorMap[block.bci()];
    }

    /**
     * Gets the exception handlers for a specified block. Note that this
     * set of exception handlers takes into account whether the block contains
     * bytecodes that can cause traps or not.
     * @param block the block for which to get the exception handlers
     * @return an array of the blocks which represent exception handlers; a zero-length
     * array of blocks if there are no handlers that cover any potentially trapping
     * instruction in the specified block
     */
    public Iterable<BlockBegin> getHandlers(BlockBegin block) {
        if (exceptionMap == null) {
            return NONE_LIST;
        }
        return exceptionMap.getHandlers(block);
    }

    /**
     * Builds the block map and conservative CFG and numbers blocks.
     * @param computeStoresInLoops {@code true} if the block map builder should
     * make a second pass over the bytecodes for blocks in loops
     * @return {@code true} if the block map was built successfully; {@code false} otherwise
     */
    public boolean build(boolean computeStoresInLoops) {
        if (exceptionMap != null) {
            exceptionMap.setHandlerEntrypoints();
        }
        iterateOverBytecodes();
        moveSuccessorLists();
        computeBlockNumbers();
        if (computeStoresInLoops) {
            // process any blocks in loops to compute their stores
            // (requires another pass, but produces fewer phi's and ultimately better code)
            processLoopBlocks();
        } else {
            // be conservative and assume all locals are potentially stored in loops
            // (does not require another pass, but produces more phi's and worse code)
            storesInLoops.setAll();
        }
        return true; // XXX: what bailout conditions should the BlockMap check?
    }

    /**
     * Cleans up any internal state not necessary after the initial pass. Note that
     * this method discards the conservative CFG edges and only retains the block mapping
     * and stores in loops.
     */
    public void cleanup() {
        // discard internal state no longer needed
        successorMap = null;
        loopBlocks = null;
        exceptionMap = null;
    }

    /**
     * Gets the number of blocks in this block map.
     * @return the number of blocks
     */
    public int numberOfBlocks() {
        return blockNum - firstBlock;
    }

    public int numberOfBytes() {
        return code.length;
    }

    /**
     * Gets the bitmap that indicates which local variables are assigned in loops.
     * @return a bitmap which indicates the locals stored in loops
     */
    public BitMap getStoresInLoops() {
        return storesInLoops;
    }

    void iterateOverBytecodes() {
        // iterate over the bytecodes top to bottom.
        // mark the entrypoints of basic blocks and build lists of successors for
        // all bytecodes that end basic blocks (i.e. goto, ifs, switches, throw, jsr, returns, ret)
        int bci = 0;
        ExceptionMap exceptionMap = this.exceptionMap;
        byte[] code = this.code;
        make(0).setStandardEntry();
        while (bci < code.length) {
            int opcode = Bytes.beU1(code, bci);
            switch (opcode) {
                case ATHROW:
                    if (exceptionMap != null) {
                        exceptionMap.setCanTrap(bci);
                    }
                    // fall through
                case IRETURN: // fall through
                case LRETURN: // fall through
                case FRETURN: // fall through
                case DRETURN: // fall through
                case ARETURN: // fall through
                case WRETURN: // fall through
                case RETURN:
                    if (exceptionMap != null && exceptionMap.isObjectInit) {
                        exceptionMap.setCanTrap(bci);
                    }
                    successorMap[bci] = NONE; // end of control flow
                    bci += 1; // these are all 1 byte opcodes
                    break;

                case RET:
                    successorMap[bci] = NONE; // end of control flow
                    bci += 2; // ret is 2 bytes
                    break;

                case IFEQ:      // fall through
                case IFNE:      // fall through
                case IFLT:      // fall through
                case IFGE:      // fall through
                case IFGT:      // fall through
                case IFLE:      // fall through
                case IF_ICMPEQ: // fall through
                case IF_ICMPNE: // fall through
                case IF_ICMPLT: // fall through
                case IF_ICMPGE: // fall through
                case IF_ICMPGT: // fall through
                case IF_ICMPLE: // fall through
                case IF_ACMPEQ: // fall through
                case IF_ACMPNE: // fall through
                case IFNULL:    // fall through
                case IFNONNULL: {
                    succ2(bci, bci + 3, bci + Bytes.beS2(code, bci + 1));
                    bci += 3; // these are all 3 byte opcodes
                    break;
                }

                case GOTO: {
                    succ1(bci, bci + Bytes.beS2(code, bci + 1));
                    bci += 3; // goto is 3 bytes
                    break;
                }

                case GOTO_W: {
                    succ1(bci, bci + Bytes.beS4(code, bci + 1));
                    bci += 5; // goto_w is 5 bytes
                    break;
                }

                case JSR: {
                    int target = bci + Bytes.beS2(code, bci + 1);
                    succ2(bci, bci + 3, target); // make JSR's a successor or not?
                    addEntrypoint(target, BlockBegin.BlockFlag.SubroutineEntry);
                    bci += 3; // jsr is 3 bytes
                    break;
                }

                case JSR_W: {
                    int target = bci + Bytes.beS4(code, bci + 1);
                    succ2(bci, bci + 5, target);
                    addEntrypoint(target, BlockBegin.BlockFlag.SubroutineEntry);
                    bci += 5; // jsr_w is 5 bytes
                    break;
                }

                case TABLESWITCH: {
                    BytecodeSwitch sw = new BytecodeTableSwitch(code, bci);
                    makeSwitchSuccessors(bci, sw);
                    bci += sw.size();
                    break;
                }

                case LOOKUPSWITCH: {
                    BytecodeSwitch sw = new BytecodeLookupSwitch(code, bci);
                    makeSwitchSuccessors(bci, sw);
                    bci += sw.size();
                    break;
                }
                case WIDE: {
                    bci += lengthOf(code, bci);
                    break;
                }

                default: {
                    if (exceptionMap != null && canTrap(opcode)) {
                        exceptionMap.setCanTrap(bci);
                    }
                    bci += lengthOf(opcode); // all variable length instructions are handled above
                }
            }
        }
    }

    private void makeSwitchSuccessors(int bci, BytecodeSwitch tswitch) {
        // make a list of all the successors of a switch
        int max = tswitch.numberOfCases();
        ArrayList<BlockBegin> list = new ArrayList<BlockBegin>(max + 1);
        for (int i = 0; i < max; i++) {
            list.add(make(tswitch.targetAt(i)));
        }
        list.add(make(tswitch.defaultTarget()));
        successorMap[bci] = list.toArray(new BlockBegin[list.size()]);
    }

    void moveSuccessorLists() {
        // move successor lists from the block-ending bytecodes that created them
        // to the basic blocks which they end.
        // also handle fall-through cases from backwards branches into the middle of a block
        // add exception handlers to basic blocks
        BlockBegin current = get(0);
        ExceptionMap exceptionMap = this.exceptionMap;
        for (int bci = 0; bci < blockMap.length; bci++) {
            BlockBegin next = blockMap[bci];
            if (next != null && next != current) {
                if (current != null) {
                    // add fall through successor to current block
                    successorMap[current.bci()] = new BlockBegin[] {next};
                }
                current = next;
            }
            if (exceptionMap != null) {
                exceptionMap.addHandlers(current, bci);
            }
            BlockBegin[] succ = successorMap[bci];
            if (succ != null && current != null) {
                // move this successor list to current block
                successorMap[bci] = null;
                successorMap[current.bci()] = succ;
                current = null;
            }
        }
        assert current == null : "fell off end of code, should end with successor list";
    }

    void computeBlockNumbers() {
        // compute the block number for all blocks
        int blockNum = this.blockNum;
        int numBlocks = blockNum - firstBlock;
        numberBlock(get(0), new BitMap(numBlocks), new BitMap(numBlocks));
        this.blockNum = blockNum; // _blockNum is used to compute the number of blocks later
    }

    boolean numberBlock(BlockBegin block, BitMap visited, BitMap active) {
        // number a block with its reverse post-order traversal number
        int blockIndex = block.blockID - firstBlock;

        if (visited.get(blockIndex)) {
            if (active.get(blockIndex)) {
                // reached block via backward branch
                block.setParserLoopHeader(true);
                addLoopBlock(block);
                return true;
            }
            // return whether the block is already a loop header
            return block.isParserLoopHeader();
        }

        visited.set(blockIndex);
        active.set(blockIndex);

        boolean inLoop = false;
        for (BlockBegin succ : getSuccessors(block)) {
            // recursively process successors
            inLoop |= numberBlock(succ, visited, active);
        }
        if (exceptionMap != null) {
            for (BlockBegin succ : exceptionMap.getHandlers(block)) {
                // process exception handler blocks
                inLoop |= numberBlock(succ, visited, active);
            }
        }
        // clear active bit after successors are processed
        active.clear(blockIndex);
        block.setDepthFirstNumber(blockNum--);
        if (inLoop) {
            addLoopBlock(block);
        }

        return inLoop;
    }

    private void addLoopBlock(BlockBegin block) {
        if (loopBlocks == null) {
            loopBlocks = new ArrayList<BlockBegin>();
        }
        loopBlocks.add(block);
    }

    void processLoopBlocks() {
        if (loopBlocks == null) {
            return;
        }
        for (BlockBegin block : loopBlocks) {
            // process all the stores in this block
            int bci = block.bci();
            byte[] code = this.code;
            while (true) {
                // iterate over the bytecodes in this block
                int opcode = code[bci] & 0xff;
                if (opcode == WIDE) {
                    bci += processWideStore(code[bci + 1] & 0xff, code, bci);
                } else if (isStore(opcode)) {
                    bci += processStore(opcode, code, bci);
                } else {
                    bci += lengthOf(code, bci);
                }
                if (bci >= code.length || blockMap[bci] != null) {
                    // stop when we reach the next block
                    break;
                }
            }
        }
    }

    int processWideStore(int opcode, byte[] code, int bci) {
        switch (opcode) {
            case IINC:     storeOne(Bytes.beU2(code, bci + 2)); return 6;
            case ISTORE:   storeOne(Bytes.beU2(code, bci + 2)); return 3;
            case LSTORE:   storeTwo(Bytes.beU2(code, bci + 2)); return 3;
            case FSTORE:   storeOne(Bytes.beU2(code, bci + 2)); return 3;
            case DSTORE:   storeTwo(Bytes.beU2(code, bci + 2)); return 3;
            case ASTORE:   storeOne(Bytes.beU2(code, bci + 2)); return 3;
        }
        return lengthOf(code, bci);
    }

    int processStore(int opcode, byte[] code, int bci) {
        switch (opcode) {
            case IINC:     storeOne(code[bci + 1] & 0xff); return 3;
            case ISTORE:   storeOne(code[bci + 1] & 0xff); return 2;
            case LSTORE:   storeTwo(code[bci + 1] & 0xff); return 2;
            case FSTORE:   storeOne(code[bci + 1] & 0xff); return 2;
            case DSTORE:   storeTwo(code[bci + 1] & 0xff); return 2;
            case ASTORE:   storeOne(code[bci + 1] & 0xff); return 2;
            case ISTORE_0: storeOne(0); return 1;
            case ISTORE_1: storeOne(1); return 1;
            case ISTORE_2: storeOne(2); return 1;
            case ISTORE_3: storeOne(3); return 1;
            case LSTORE_0: storeTwo(0); return 1;
            case LSTORE_1: storeTwo(1); return 1;
            case LSTORE_2: storeTwo(2); return 1;
            case LSTORE_3: storeTwo(3); return 1;
            case FSTORE_0: storeOne(0); return 1;
            case FSTORE_1: storeOne(1); return 1;
            case FSTORE_2: storeOne(2); return 1;
            case FSTORE_3: storeOne(3); return 1;
            case DSTORE_0: storeTwo(0); return 1;
            case DSTORE_1: storeTwo(1); return 1;
            case DSTORE_2: storeTwo(2); return 1;
            case DSTORE_3: storeTwo(3); return 1;
            case ASTORE_0: storeOne(0); return 1;
            case ASTORE_1: storeOne(1); return 1;
            case ASTORE_2: storeOne(2); return 1;
            case ASTORE_3: storeOne(3); return 1;
        }
        throw Util.shouldNotReachHere();
    }

    void storeOne(int local) {
        storesInLoops.set(local);
    }

    void storeTwo(int local) {
        storesInLoops.set(local);
        storesInLoops.set(local + 1);
    }

    void succ2(int bci, int s1, int s2) {
        successorMap[bci] = new BlockBegin[] {make(s1), make(s2)};
    }

    void succ1(int bci, int s1) {
        successorMap[bci] = new BlockBegin[] {make(s1)};
    }
}
