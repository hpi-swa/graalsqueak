package de.hpi.swa.graal.squeak.model;

import java.util.Arrays;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.utilities.CyclicAssumption;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.EnterCodeNode;
import de.hpi.swa.graal.squeak.shared.SqueakLanguageConfig;
import de.hpi.swa.graal.squeak.util.CompiledCodeObjectPrinter;
import de.hpi.swa.graal.squeak.util.MiscUtils;

public abstract class CompiledCodeObject extends AbstractSqueakObjectWithHash {
    @CompilationFinal(dimensions = 1) private static final int[] HEADER_SPLIT_PATTERN = new int[]{15, 1, 1, 1, 6, 4, 2, 1};

    public enum SLOT_IDENTIFIER {
        THIS_MARKER,
        THIS_CONTEXT,
        INSTRUCTION_POINTER,
        STACK_POINTER,
    }

    // frame info
    private final FrameDescriptor frameDescriptor;
    private final FrameSlot thisMarkerSlot;
    private final FrameSlot thisContextSlot;
    private final FrameSlot instructionPointerSlot;
    private final FrameSlot stackPointerSlot;
    @CompilationFinal(dimensions = 1) protected FrameSlot[] stackSlots;
    // header info and data
    @CompilationFinal(dimensions = 1) protected Object[] literals;
    @CompilationFinal(dimensions = 1) protected byte[] bytes;
    @CompilationFinal protected int numArgs;
    @CompilationFinal protected int numLiterals;
    @CompilationFinal protected boolean hasPrimitive;
    @CompilationFinal protected boolean needsLargeFrame = false;
    @CompilationFinal protected int numTemps;

    @CompilationFinal(dimensions = 1) private CompiledBlockObject[] innerBlocks;

    private final int numCopiedValues; // for block closures

    private Source source;

    @CompilationFinal private RootCallTarget callTarget;
    private final CyclicAssumption callTargetStable = new CyclicAssumption("CompiledCodeObject assumption");
    private final Assumption doesNotNeedSender = Truffle.getRuntime().createAssumption("CompiledCodeObject doesNotNeedSender assumption");

    protected CompiledCodeObject(final SqueakImageContext image, final int hash, final int numCopiedValues) {
        super(image, hash);
        this.numCopiedValues = numCopiedValues;

        frameDescriptor = new FrameDescriptor();
        thisMarkerSlot = frameDescriptor.addFrameSlot(SLOT_IDENTIFIER.THIS_MARKER, FrameSlotKind.Object);
        thisContextSlot = frameDescriptor.addFrameSlot(SLOT_IDENTIFIER.THIS_CONTEXT, FrameSlotKind.Illegal);
        instructionPointerSlot = frameDescriptor.addFrameSlot(SLOT_IDENTIFIER.INSTRUCTION_POINTER, FrameSlotKind.Int);
        stackPointerSlot = frameDescriptor.addFrameSlot(SLOT_IDENTIFIER.STACK_POINTER, FrameSlotKind.Int);
    }

    protected CompiledCodeObject(final CompiledCodeObject original) {
        super(original.image);
        numCopiedValues = original.numCopiedValues;
        frameDescriptor = original.frameDescriptor;
        thisMarkerSlot = original.thisMarkerSlot;
        thisContextSlot = original.thisContextSlot;
        instructionPointerSlot = original.instructionPointerSlot;
        stackPointerSlot = original.stackPointerSlot;
        stackSlots = original.stackSlots;
        setLiteralsAndBytes(original.literals.clone(), original.bytes.clone());
    }

    private void setLiteralsAndBytes(final Object[] literals, final byte[] bytes) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.literals = literals;
        decodeHeader();
        this.bytes = bytes;
        innerBlocks = null; // Remove any inner blocks.
        renewCallTarget();
    }

    public final Source getSource() {
        if (source == null) {
            source = Source.newBuilder(SqueakLanguageConfig.ID, CompiledCodeObjectPrinter.getString(this), toString()).build();
        }
        return source;
    }

    public final int getSqueakContextSize() {
        return needsLargeFrame ? CONTEXT.LARGE_FRAMESIZE : CONTEXT.SMALL_FRAMESIZE;
    }

    public final RootCallTarget getCallTarget() {
        if (callTarget == null) {
            renewCallTarget();
        }
        return callTarget;
    }

    private void renewCallTarget() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        callTargetStable.invalidate();
        initializeCallTargetUnsafe();
    }

    protected final void initializeCallTargetUnsafe() {
        callTarget = Truffle.getRuntime().createCallTarget(EnterCodeNode.create(image.getLanguage(), this));
    }

    public final Assumption getCallTargetStable() {
        return callTargetStable.getAssumption();
    }

    public final Assumption getDoesNotNeedSenderAssumption() {
        return doesNotNeedSender;
    }

    public final FrameDescriptor getFrameDescriptor() {
        return frameDescriptor;
    }

    public final FrameSlot getThisMarkerSlot() {
        return thisMarkerSlot;
    }

    public final FrameSlot getThisContextSlot() {
        return thisContextSlot;
    }

    public final FrameSlot getInstructionPointerSlot() {
        return instructionPointerSlot;
    }

    public final FrameSlot getStackPointerSlot() {
        return stackPointerSlot;
    }

    public final int getNumArgs() {
        return numArgs;
    }

    public final int getNumArgsAndCopied() {
        return numArgs + numCopiedValues;
    }

    public final int getNumTemps() {
        return numTemps;
    }

    public final int getNumLiterals() {
        return numLiterals;
    }

    public final FrameSlot getStackSlot(final int i) {
        assert 0 <= i && i < stackSlots.length : "Bad stack access";
        if (stackSlots[i] == null) {
            // Lazily add frame slots.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stackSlots[i] = frameDescriptor.addFrameSlot(i + 1, FrameSlotKind.Illegal);
        }
        return stackSlots[i];
    }

    public final FrameSlot[] getStackSlotsUnsafe() {
        return stackSlots;
    }

    public final int getNumStackSlots() {
        /**
         * Arguments and copied values are also pushed onto the stack in {@link EnterCodeNode},
         * therefore there must be enough slots for all these values as well as the Squeak stack.
         */
        return getNumArgsAndCopied() + getSqueakContextSize();
    }

    @Override
    public final ClassObject getSqueakClass() {
        return image.compiledMethodClass;
    }

    @Override
    public final void fillin(final SqueakImageChunk chunk) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        final long[] words = chunk.getWords();
        // header is a tagged small integer
        final long header = words[0] >> (image.flags.is64bit() ? 3 : 1);
        final int numberOfLiterals = (int) (header & 0x7fff);
        final Object[] ptrs = chunk.getPointers(numberOfLiterals + 1);
        assert literals == null;
        literals = ptrs;
        decodeHeader();
        assert bytes == null;
        bytes = chunk.getBytes(ptrs.length * image.flags.wordSize());
        assert innerBlocks == null : "Should not have any inner blocks yet";
    }

    private int getHeader() {
        return (int) (long) literals[0];
    }

    protected final void decodeHeader() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        final int header = getHeader();
        final int[] splitHeader = MiscUtils.bitSplitter(header, HEADER_SPLIT_PATTERN);
        numLiterals = splitHeader[0];
        hasPrimitive = splitHeader[2] == 1;
        needsLargeFrame = splitHeader[3] == 1;
        numTemps = splitHeader[4];
        numArgs = splitHeader[5];
        /** Jit without counters - reserved for methods that have been optimized by Sista. */
        // isOptimized = splitHeader[1] == 1;
        /**
         * reserved for an access modifier (00-unused, 01-private, 10-protected, 11-public),
         * although accessors for bit 29 exist (see #flag).
         */
        // accessModifier = splitHeader[6];
        /** selects the instruction set, >= 0 Primary, < 0 Secondary (#signFlag). */
        // altInstructionSet = splitHeader[7] == 1;
        ensureCorrectNumberOfStackSlots();
    }

    protected void ensureCorrectNumberOfStackSlots() {
        final int requiredNumberOfStackSlots = getNumStackSlots();
        if (stackSlots == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stackSlots = new FrameSlot[requiredNumberOfStackSlots];
            return;
        }
        final int currentNumberOfStackSlots = stackSlots.length;
        if (currentNumberOfStackSlots < requiredNumberOfStackSlots) {
            // Grow number of stack slots.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stackSlots = Arrays.copyOf(stackSlots, requiredNumberOfStackSlots);
        } else if (currentNumberOfStackSlots > requiredNumberOfStackSlots) {
            // Shrink number of stack slots.
            CompilerDirectives.transferToInterpreterAndInvalidate();
            for (int i = requiredNumberOfStackSlots; i < currentNumberOfStackSlots; i++) {
                frameDescriptor.removeFrameSlot(i);
            }
            stackSlots = Arrays.copyOf(stackSlots, requiredNumberOfStackSlots);
        }
    }

    public final void become(final CompiledCodeObject other) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        final Object[] literals2 = other.literals;
        final byte[] bytes2 = other.bytes;
        other.setLiteralsAndBytes(literals, bytes);
        setLiteralsAndBytes(literals2, bytes2);
        other.callTargetStable.invalidate();
        callTargetStable.invalidate();
    }

    public final int getBytecodeOffset() {
        return (1 + numLiterals) * image.flags.wordSize(); // header plus numLiterals
    }

    public final void atput0(final long longIndex, final Object obj) {
        final int index = (int) longIndex;
        assert index >= 0;
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (index < getBytecodeOffset()) {
            assert index % image.flags.wordSize() == 0;
            setLiteral(index / image.flags.wordSize(), obj);
        } else {
            final int realIndex = index - getBytecodeOffset();
            assert realIndex < bytes.length;
            if (obj instanceof Integer) {
                bytes[realIndex] = (byte) (int) obj;
            } else if (obj instanceof Long) {
                bytes[realIndex] = (byte) (long) obj;
            } else {
                bytes[realIndex] = (byte) obj;
            }
        }
    }

    public final Object getLiteral(final long longIndex) {
        return literals[(int) (1 + longIndex)]; // +1 for skipping header.
    }

    public final void setLiteral(final long longIndex, final Object obj) {
        final int index = (int) longIndex;
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (index == 0) {
            assert obj instanceof Long;
            final int oldNumLiterals = numLiterals;
            literals[0] = obj;
            decodeHeader();
            assert numLiterals == oldNumLiterals;
        } else {
            literals[index] = obj;
        }
    }

    public final boolean hasPrimitive() {
        return hasPrimitive;
    }

    public final int primitiveIndex() {
        assert hasPrimitive() && bytes.length >= 3;
        return (Byte.toUnsignedInt(bytes[2]) << 8) + Byte.toUnsignedInt(bytes[1]);
    }

    public final boolean isUnwindMarked() {
        return hasPrimitive() && primitiveIndex() == 198;
    }

    @Override
    public final int instsize() {
        return 0;
    }

    public final Object[] getLiterals() {
        return literals;
    }

    public final byte[] getBytes() {
        return bytes;
    }

    public static final long makeHeader(final int numArgs, final int numTemps, final int numLiterals, final boolean hasPrimitive, final boolean needsLargeFrame) {
        return (numArgs & 0x0F) << 24 | (numTemps & 0x3F) << 18 | numLiterals & 0x7FFF | (needsLargeFrame ? 0x20000 : 0) | (hasPrimitive ? 0x10000 : 0);
    }

    public CompiledBlockObject[] getInnerBlocks() {
        return innerBlocks;
    }

    public CompiledBlockObject addInnerBlock(final CompiledBlockObject innerBlock) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        if (innerBlocks == null) {
            innerBlocks = new CompiledBlockObject[]{innerBlock};
        } else {
            innerBlocks = Arrays.copyOf(innerBlocks, innerBlocks.length + 1);
            innerBlocks[innerBlocks.length - 1] = innerBlock;
        }
        return innerBlock;
    }
}
