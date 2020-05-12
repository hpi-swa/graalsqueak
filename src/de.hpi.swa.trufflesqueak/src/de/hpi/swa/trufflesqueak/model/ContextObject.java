/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.model;

import java.util.Arrays;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameUtil;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.exceptions.ProcessSwitch;
import de.hpi.swa.trufflesqueak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.trufflesqueak.image.SqueakImageChunk;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.image.SqueakImageReader;
import de.hpi.swa.trufflesqueak.image.SqueakImageWriter;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.CONTEXT;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.PROCESS;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.PROCESS_SCHEDULER;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectReadNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.MiscellaneousBytecodes.CallPrimitiveNode;
import de.hpi.swa.trufflesqueak.util.FrameAccess;
import de.hpi.swa.trufflesqueak.util.ObjectGraphUtils.ObjectTracer;

public final class ContextObject extends AbstractSqueakObjectWithHash {
    private static final int NIL_PC_VALUE = -1;

    @CompilationFinal private MaterializedFrame truffleFrame;
    @CompilationFinal private PointersObject process;
    @CompilationFinal private int size;
    private boolean hasModifiedSender = false;
    private boolean escaped = false;

    private ContextObject(final SqueakImageContext image, final long hash) {
        super(image, hash);
        truffleFrame = null;
    }

    private ContextObject(final SqueakImageContext image, final int size) {
        super(image);
        truffleFrame = null;
        this.size = size;
    }

    private ContextObject(final SqueakImageContext image, final MaterializedFrame truffleFrame, final int size) {
        super(image);
        assert FrameAccess.getSender(truffleFrame) != null;
        assert FrameAccess.getContext(truffleFrame) == null;
        assert FrameAccess.getBlockOrMethod(truffleFrame).getSqueakContextSize() == size;
        this.truffleFrame = truffleFrame;
        this.size = size;
    }

    private ContextObject(final ContextObject original) {
        super(original);
        final CompiledCodeObject code = FrameAccess.getBlockOrMethod(original.truffleFrame);
        hasModifiedSender = original.hasModifiedSender();
        escaped = original.escaped;
        size = original.size;
        // Create shallow copy of Truffle frame
        truffleFrame = Truffle.getRuntime().createMaterializedFrame(original.truffleFrame.getArguments().clone(), code.getFrameDescriptor());
        // Copy frame slot values
        FrameAccess.initializeMarker(truffleFrame, code);
        FrameAccess.setContext(truffleFrame, code, this);
        FrameAccess.setInstructionPointer(truffleFrame, code, FrameAccess.getInstructionPointer(original.truffleFrame, code));
        FrameAccess.setStackPointer(truffleFrame, code, FrameAccess.getStackPointer(original.truffleFrame, code));
        // Copy stack
        final int numStackSlots = code.getNumStackSlots();
        for (int i = 0; i < numStackSlots; i++) {
            final FrameSlot slot = code.getStackSlot(i);
            final Object value = original.truffleFrame.getValue(slot);
            if (value != null) {
                FrameAccess.setStackSlot(truffleFrame, slot, value);
            } else {
                break; // This and all following slots are not in use.
            }
        }
    }

    public static ContextObject create(final SqueakImageContext image, final int size) {
        return new ContextObject(image, size);
    }

    public static ContextObject createWithHash(final SqueakImageContext image, final long hash) {
        return new ContextObject(image, hash);
    }

    public static ContextObject create(final FrameInstance frameInstance) {
        final Frame frame = frameInstance.getFrame(FrameInstance.FrameAccess.MATERIALIZE);
        return create(frame.materialize(), FrameAccess.getBlockOrMethod(frame));
    }

    public static ContextObject create(final MaterializedFrame frame, final CompiledCodeObject blockOrMethod) {
        final ContextObject context = new ContextObject(blockOrMethod.image, frame, blockOrMethod.getSqueakContextSize());
        FrameAccess.setContext(frame, blockOrMethod, context);
        return context;
    }

    @Override
    public ClassObject getSqueakClass() {
        return image.methodContextClass;
    }

    /**
     * {@link ContextObject}s are filled in at a later stage by a
     * {@link SqueakImageReader#fillInContextObjects}.
     */
    @Override
    public void fillin(final SqueakImageChunk chunk) {
        // Do nothing.
    }

    public void fillinContext(final SqueakImageChunk chunk) {
        final Object[] pointers = chunk.getPointers();
        size = pointers.length;
        assert size > CONTEXT.TEMP_FRAME_START;
        final CompiledMethodObject method = (CompiledMethodObject) pointers[CONTEXT.METHOD];
        final AbstractSqueakObject sender = (AbstractSqueakObject) pointers[CONTEXT.SENDER_OR_NIL];
        assert sender != null : "sender should not be null";
        final Object closureOrNil = pointers[CONTEXT.CLOSURE_OR_NIL];
        final BlockClosureObject closure;
        final CompiledCodeObject code;
        if (closureOrNil == NilObject.SINGLETON) {
            closure = null;
            code = method;
        } else {
            closure = (BlockClosureObject) closureOrNil;
            code = closure.getCompiledBlock(method);
        }
        final int endArguments = CONTEXT.TEMP_FRAME_START + method.getNumArgsAndCopied();
        final Object[] arguments = Arrays.copyOfRange(pointers, CONTEXT.RECEIVER, endArguments);
        final Object[] frameArguments = FrameAccess.newWith(method, sender, closure, arguments);
        CompilerDirectives.transferToInterpreterAndInvalidate();
        truffleFrame = Truffle.getRuntime().createMaterializedFrame(frameArguments, code.getFrameDescriptor());
        FrameAccess.initializeMarker(truffleFrame, code);
        FrameAccess.setContext(truffleFrame, code, this);
        final Object pc = pointers[CONTEXT.INSTRUCTION_POINTER];
        if (pc == NilObject.SINGLETON) {
            removeInstructionPointer();
        } else {
            setInstructionPointer((int) (long) pc);
        }
        setStackPointer((int) (long) pointers[CONTEXT.STACKPOINTER]);
        for (int i = CONTEXT.TEMP_FRAME_START; i < pointers.length; i++) {
            atTempPut(i - CONTEXT.TEMP_FRAME_START, pointers[i]);
        }
    }

    public CallTarget getCallTarget() {
        return getBlockOrMethod().getResumptionCallTarget(this);
    }

    public MaterializedFrame getOrCreateTruffleFrame() {
        if (truffleFrame == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // Method is unknown, use dummy frame instead
            final Object[] dummyArguments = FrameAccess.newDummyWith(null, NilObject.SINGLETON, null, new Object[2]);
            final CompiledMethodObject dummyMethod = SqueakLanguage.getContext().dummyMethod;
            truffleFrame = Truffle.getRuntime().createMaterializedFrame(dummyArguments, dummyMethod.getFrameDescriptor());
            FrameAccess.setInstructionPointer(truffleFrame, dummyMethod, 0);
            FrameAccess.setStackPointer(truffleFrame, dummyMethod, 1);
        }
        return truffleFrame;
    }

    private MaterializedFrame getOrCreateTruffleFrame(final CompiledMethodObject method) {
        if (truffleFrame == null || FrameAccess.getMethod(truffleFrame) == null) {
            final Object[] frameArguments;
            final int instructionPointer;
            final int stackPointer;
            if (truffleFrame != null) {
                assert FrameAccess.getSender(truffleFrame) != null : "Sender should not be null";
                FrameAccess.assertReceiverNotNull(truffleFrame);

                final Object[] dummyArguments = truffleFrame.getArguments();
                final int expectedArgumentSize = FrameAccess.expectedArgumentSize(method.getNumArgs());
                if (dummyArguments.length != expectedArgumentSize) {
                    // Adjust arguments.
                    frameArguments = Arrays.copyOf(dummyArguments, expectedArgumentSize);
                } else {
                    frameArguments = truffleFrame.getArguments();
                }
                assert truffleFrame.getFrameDescriptor().getSize() > 0;
                instructionPointer = FrameAccess.getInstructionPointer(truffleFrame, method);
                stackPointer = FrameAccess.getStackPointer(truffleFrame, method);
            } else {
                // Receiver plus arguments.
                final Object[] squeakArguments = new Object[1 + method.getNumArgs()];
                frameArguments = FrameAccess.newDummyWith(method, NilObject.SINGLETON, null, squeakArguments);
                instructionPointer = 0;
                stackPointer = method.getNumTemps();
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            truffleFrame = Truffle.getRuntime().createMaterializedFrame(frameArguments, method.getFrameDescriptor());
            FrameAccess.initializeMarker(truffleFrame, method);
            FrameAccess.setContext(truffleFrame, method, this);
            FrameAccess.setInstructionPointer(truffleFrame, method, instructionPointer);
            FrameAccess.setStackPointer(truffleFrame, method, stackPointer);
        }
        return truffleFrame;
    }

    private MaterializedFrame getOrCreateTruffleFrame(final BlockClosureObject closure) {
        if (truffleFrame == null || FrameAccess.getClosure(truffleFrame) != closure) {
            final Object[] frameArguments;
            final CompiledBlockObject compiledBlock = closure.getCompiledBlock();
            final int instructionPointer;
            final int stackPointer;
            if (truffleFrame != null) {
                // FIXME: Assuming here this context is not active, add check?
                assert FrameAccess.getSender(truffleFrame) != null : "Sender should not be null";

                final Object[] dummyArguments = truffleFrame.getArguments();
                final int expectedArgumentSize = FrameAccess.expectedArgumentSize(compiledBlock.getNumArgsAndCopied());
                if (dummyArguments.length != expectedArgumentSize) {
                    // Adjust arguments.
                    frameArguments = Arrays.copyOf(dummyArguments, expectedArgumentSize);
                } else {
                    frameArguments = truffleFrame.getArguments();
                }
                if (truffleFrame.getFrameDescriptor().getSize() > 0) {
                    instructionPointer = FrameAccess.getInstructionPointer(truffleFrame, compiledBlock);
                    stackPointer = FrameAccess.getStackPointer(truffleFrame, compiledBlock);
                } else { // Frame slots unknown, so initialize PC and SP;
                    instructionPointer = 0;
                    stackPointer = compiledBlock.getNumArgsAndCopied();
                }
            } else {
                // Receiver plus arguments.
                final Object[] squeakArguments = new Object[1 + compiledBlock.getNumArgsAndCopied()];
                frameArguments = FrameAccess.newDummyWith(compiledBlock, NilObject.SINGLETON, closure, squeakArguments);
                instructionPointer = 0;
                stackPointer = compiledBlock.getNumArgsAndCopied();
            }
            CompilerDirectives.transferToInterpreterAndInvalidate();
            truffleFrame = Truffle.getRuntime().createMaterializedFrame(frameArguments, compiledBlock.getFrameDescriptor());
            FrameAccess.assertSenderNotNull(truffleFrame);
            FrameAccess.assertReceiverNotNull(truffleFrame);
            FrameAccess.initializeMarker(truffleFrame, compiledBlock);
            FrameAccess.setContext(truffleFrame, compiledBlock, this);
            FrameAccess.setInstructionPointer(truffleFrame, compiledBlock, instructionPointer);
            FrameAccess.setStackPointer(truffleFrame, compiledBlock, stackPointer);
        }
        return truffleFrame;
    }

    public Object getFrameSender() {
        return FrameAccess.getSender(truffleFrame);
    }

    public AbstractSqueakObject getSender() {
        final Object value = FrameAccess.getSender(truffleFrame);
        if (value instanceof FrameMarker) {
            getBlockOrMethod().getDoesNotNeedSenderAssumption().invalidate("Sender requested");
            final ContextObject previousContext = ((FrameMarker) value).getMaterializedContext();
            if (process != null && !(getReceiver() instanceof ContextObject)) {
                previousContext.setProcess(process);
            }
            FrameAccess.setSender(truffleFrame, previousContext);
            return previousContext;
        } else {
            return (AbstractSqueakObject) value;
        }
    }

    // should only be used when sender is not nil
    public ContextObject getNotNilSender() {
        return (ContextObject) getSender();
    }

    /**
     * Sets the sender of a ContextObject.
     */
    public void setSender(final ContextObject value) {
        if (!hasModifiedSender && truffleFrame != null && FrameAccess.getSender(truffleFrame) != value.getFrameMarker()) {
            hasModifiedSender = true;
        }
        FrameAccess.setSender(getOrCreateTruffleFrame(), value);
    }

    public void removeSender() {
        if (hasModifiedSender) {
            hasModifiedSender = false;
        }
        FrameAccess.setSender(getOrCreateTruffleFrame(), NilObject.SINGLETON);
    }

    public Object getInstructionPointer(final ConditionProfile nilProfile) {
        final BlockClosureObject closure = getClosure();
        if (closure != null) {
            final CompiledBlockObject block = closure.getCompiledBlock();
            final int pc = FrameAccess.getInstructionPointer(truffleFrame, block);
            if (nilProfile.profile(pc == NIL_PC_VALUE)) {
                return NilObject.SINGLETON;
            } else {
                return (long) pc + block.getInitialPC(); // Must be a long.
            }
        } else {
            final CompiledMethodObject method = getMethod();
            final int pc = FrameAccess.getInstructionPointer(truffleFrame, method);
            if (nilProfile.profile(pc == NIL_PC_VALUE)) {
                return NilObject.SINGLETON;
            } else {
                return (long) pc + method.getInitialPC(); // Must be a long.
            }
        }
    }

    public int getInstructionPointerForBytecodeLoop() {
        return FrameAccess.getInstructionPointer(truffleFrame, getBlockOrMethod());
    }

    public void setInstructionPointer(final int value) {
        final BlockClosureObject closure = getClosure();
        if (closure != null) {
            final CompiledBlockObject block = closure.getCompiledBlock();
            FrameAccess.setInstructionPointer(truffleFrame, block, value - block.getInitialPC());
        } else {
            final CompiledMethodObject method = getMethod();
            FrameAccess.setInstructionPointer(truffleFrame, method, value - method.getInitialPC());
        }
    }

    public void removeInstructionPointer() {
        FrameAccess.setInstructionPointer(truffleFrame, getBlockOrMethod(), NIL_PC_VALUE);
    }

    public int getStackPointer() {
        return FrameAccess.getStackPointer(truffleFrame, getBlockOrMethod());
    }

    public void setStackPointer(final int value) {
        assert 0 <= value && value <= getBlockOrMethod().getSqueakContextSize() : value + " not between 0 and " + getBlockOrMethod().getSqueakContextSize() + " in " + toString();
        FrameAccess.setStackPointer(getOrCreateTruffleFrame(), getBlockOrMethod(), value);
    }

    private boolean hasMethod() {
        return hasTruffleFrame() && getMethod() != null;
    }

    public CompiledMethodObject getMethod() {
        return FrameAccess.getMethod(truffleFrame);
    }

    public void setMethod(final CompiledMethodObject value) {
        FrameAccess.setMethod(getOrCreateTruffleFrame(value), value);
    }

    public BlockClosureObject getClosure() {
        return FrameAccess.getClosure(truffleFrame);
    }

    public boolean hasClosure() {
        return FrameAccess.getClosure(truffleFrame) != null;
    }

    public void setClosure(final BlockClosureObject value) {
        FrameAccess.setClosure(getOrCreateTruffleFrame(value), value);
    }

    public CompiledCodeObject getBlockOrMethod() {
        final BlockClosureObject closure = getClosure();
        if (closure != null) {
            return closure.getCompiledBlock();
        } else {
            return getMethod();
        }
    }

    public Object getReceiver() {
        return FrameAccess.getReceiver(truffleFrame);
    }

    public void setReceiver(final Object value) {
        FrameAccess.setReceiver(getOrCreateTruffleFrame(), value);
    }

    @TruffleBoundary
    public Object atTemp(final int index) {
        return NilObject.nullToNil(truffleFrame.getValue(getBlockOrMethod().getStackSlot(index)));
    }

    @TruffleBoundary
    public void atTempPut(final int index, final Object value) {
        FrameAccess.setArgumentIfInRange(getOrCreateTruffleFrame(), index, value);
        FrameAccess.setStackSlot(truffleFrame, getBlockOrMethod().getStackSlot(index), value);
    }

    public void terminate() {
        removeInstructionPointer();
        removeSender();
    }

    public boolean isTerminated() {
        return getInstructionPointerForBytecodeLoop() < 0 && getFrameSender() == NilObject.SINGLETON;
    }

    public ContextObject shallowCopy() {
        return new ContextObject(this);
    }

    public boolean hasEscaped() {
        return escaped;
    }

    public void markEscaped() {
        escaped = true;
    }

    public boolean hasModifiedSender() {
        return hasModifiedSender;
    }

    public void push(final Object value) {
        assert value != null : "Unexpected `null` value";
        final int currentStackPointer = getStackPointer();
        assert currentStackPointer < CONTEXT.MAX_STACK_SIZE;
        setStackPointer(currentStackPointer + 1);
        atTempPut(currentStackPointer, value);
    }

    public Object pop() {
        final int newStackPointer = getStackPointer() - 1;
        assert 0 <= newStackPointer;
        final Object value = atTemp(newStackPointer);
        assert value != null : "Unexpected `null` value";
        atTempPut(newStackPointer, NilObject.SINGLETON);
        setStackPointer(newStackPointer);
        return value;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (hasMethod()) {
            final BlockClosureObject closure = getClosure();
            if (closure != null) {
                return "CTX [] in " + getMethod() + " @" + Integer.toHexString(hashCode());
            } else {
                return "CTX " + getMethod() + " @" + Integer.toHexString(hashCode());
            }
        } else {
            return "CTX without method @" + Integer.toHexString(hashCode());
        }
    }

    @Override
    public int getNumSlots() {
        return CONTEXT.INST_SIZE + getMethod().getSqueakContextSize();
    }

    @Override
    public int instsize() {
        return CONTEXT.INST_SIZE;
    }

    @Override
    public int size() {
        return size;
    }

    public int getStackSize() {
        return getBlockOrMethod().getSqueakContextSize();
    }

    public void become(final ContextObject other) {
        final MaterializedFrame otherTruffleFrame = other.truffleFrame;
        final int otherSize = other.size;
        final boolean otherHasModifiedSender = other.hasModifiedSender;
        final boolean otherEscaped = other.escaped;
        other.setFields(truffleFrame, size, hasModifiedSender, escaped);
        setFields(otherTruffleFrame, otherSize, otherHasModifiedSender, otherEscaped);
    }

    private void setFields(final MaterializedFrame otherTruffleFrame, final int otherSize, final boolean otherHasModifiedSender, final boolean otherEscaped) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        truffleFrame = otherTruffleFrame;
        size = otherSize;
        hasModifiedSender = otherHasModifiedSender;
        escaped = otherEscaped;
    }

    public Object[] getReceiverAndNArguments() {
        CompilerAsserts.neverPartOfCompilation("For debugging purposes only");
        return getReceiverAndNArguments(getBlockOrMethod().getNumArgsAndCopied());
    }

    private Object[] getReceiverAndNArguments(final int numArgs) {
        final Object[] arguments = new Object[1 + numArgs];
        arguments[0] = getReceiver();
        for (int i = 0; i < numArgs; i++) {
            arguments[1 + i] = atTemp(i);
        }
        return arguments;
    }

    public void transferTo(final AbstractPointersObjectReadNode readNode, final AbstractPointersObjectWriteNode writeNode, final PointersObject newProcess) {
        // Record a process to be awakened on the next interpreter cycle.
        final PointersObject scheduler = newProcess.image.getScheduler();
        assert newProcess != newProcess.image.getActiveProcess(readNode) : "trying to switch to already active process";
        // overwritten in next line.
        final PointersObject currentProcess = newProcess.image.getActiveProcess(readNode);
        writeNode.execute(scheduler, PROCESS_SCHEDULER.ACTIVE_PROCESS, newProcess);
        writeNode.execute(currentProcess, PROCESS.SUSPENDED_CONTEXT, this);
        final ContextObject newActiveContext = (ContextObject) readNode.execute(newProcess, PROCESS.SUSPENDED_CONTEXT);
        newActiveContext.setProcess(newProcess);
        writeNode.execute(newProcess, PROCESS.SUSPENDED_CONTEXT, NilObject.SINGLETON);
        if (CompilerDirectives.isPartialEvaluationConstant(newActiveContext)) {
            throw ProcessSwitch.create(newActiveContext);
        } else {
            // Avoid further PE if newActiveContext is not a PE constant.
            throw ProcessSwitch.createWithBoundary(newActiveContext);
        }
    }

    public MaterializedFrame getTruffleFrame() {
        return truffleFrame;
    }

    public boolean hasTruffleFrame() {
        return truffleFrame != null;
    }

    public boolean hasMaterializedSender() {
        return !(FrameAccess.getSender(truffleFrame) instanceof FrameMarker);
    }

    @TruffleBoundary
    public FrameMarker getFrameMarker() {
        return FrameAccess.getMarker(truffleFrame);
    }

    // The context represents primitive call which needs to be skipped when unwinding call stack.
    public boolean isPrimitiveContext() {
        return !hasClosure() && getMethod().hasPrimitive() && getInstructionPointerForBytecodeLoop() <= CallPrimitiveNode.NUM_BYTECODES;
    }

    @TruffleBoundary
    public boolean pointsTo(final Object thang) {
        // TODO: make sure this works correctly
        if (truffleFrame != null) {
            if (getSender() == thang || thang.equals(getInstructionPointer(ConditionProfile.getUncached())) || thang.equals(getStackPointer()) || getMethod() == thang || getClosure() == thang ||
                            getReceiver() == thang) {
                return true;
            }
            for (int i = 0; i < getBlockOrMethod().getNumStackSlots(); i++) {
                if (atTemp(i) == thang) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void pointersBecomeOneWay(final Object[] from, final Object[] to, final boolean copyHash) {
        if (hasTruffleFrame()) {
            for (int i = 0; i < from.length; i++) {
                final Object fromPointer = from[i];
                if (fromPointer == getFrameSender() && to[i] instanceof ContextObject) {
                    setSender((ContextObject) to[i]);
                    copyHash(fromPointer, to[i], copyHash);
                }
                if (fromPointer == getMethod() && to[i] instanceof CompiledMethodObject) {
                    setMethod((CompiledMethodObject) to[i]);
                    copyHash(fromPointer, to[i], copyHash);
                }
                if (fromPointer == getClosure() && to[i] instanceof BlockClosureObject) {
                    setClosure((BlockClosureObject) to[i]);
                    copyHash(fromPointer, to[i], copyHash);
                }
                if (fromPointer == getReceiver()) {
                    setReceiver(to[i]);
                    copyHash(fromPointer, to[i], copyHash);
                }
                assert getBlockOrMethod().getStackSlotsUnsafe().length == getBlockOrMethod().getNumStackSlots();
                for (final FrameSlot slot : getBlockOrMethod().getStackSlotsUnsafe()) {
                    if (slot == null) {
                        break; /* Done, this and all following slots have not (yet) been used. */
                    } else if (truffleFrame.isObject(slot)) {
                        final Object stackValue = FrameUtil.getObjectSafe(truffleFrame, slot);
                        if (fromPointer == stackValue) {
                            truffleFrame.setObject(slot, to[i]);
                            copyHash(fromPointer, to[i], copyHash);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void tracePointers(final ObjectTracer tracer) {
        if (hasTruffleFrame()) {
            tracer.addIfUnmarked(getFrameSender());
            tracer.addIfUnmarked(getMethod());
            tracer.addIfUnmarked(getClosure());
            tracer.addIfUnmarked(getReceiver());
            assert getBlockOrMethod().getStackSlotsUnsafe().length == getBlockOrMethod().getNumStackSlots();
            for (final FrameSlot slot : getBlockOrMethod().getStackSlotsUnsafe()) {
                if (slot == null) {
                    break; /* Done, this and all following slots have not (yet) been used. */
                } else if (truffleFrame.isObject(slot)) {
                    tracer.addIfUnmarked(FrameUtil.getObjectSafe(truffleFrame, slot));
                }
            }
        }
    }

    @Override
    public void trace(final SqueakImageWriter writer) {
        super.trace(writer);
        if (hasTruffleFrame()) {
            writer.traceIfNecessary(getSender()); /* May materialize sender. */
            writer.traceIfNecessary(getMethod());
            writer.traceIfNecessary(getClosure());
            writer.traceIfNecessary(getReceiver());
            assert getBlockOrMethod().getStackSlotsUnsafe().length == getBlockOrMethod().getNumStackSlots();
            for (final FrameSlot slot : getBlockOrMethod().getStackSlotsUnsafe()) {
                if (slot == null) {
                    break; /* Done, this and all following slots have not (yet) been used. */
                } else if (truffleFrame.isObject(slot)) {
                    writer.traceIfNecessary(FrameUtil.getObjectSafe(truffleFrame, slot));
                }
            }
        }
    }

    @Override
    public void write(final SqueakImageWriter writer) {
        if (!writeHeader(writer)) {
            throw SqueakException.create("ContextObject must have slots:", this);
        }
        writer.writeObject(getSender());
        writer.writeObject(getInstructionPointer(ConditionProfile.getUncached()));
        writer.writeSmallInteger(getStackPointer());
        writer.writeObject(getMethod());
        writer.writeObject(NilObject.nullToNil(getClosure()));
        writer.writeObject(getReceiver());
        assert getBlockOrMethod().getStackSlotsUnsafe().length == getBlockOrMethod().getNumStackSlots();
        final FrameSlot[] stackSlots = getBlockOrMethod().getStackSlotsUnsafe();
        for (int i = 0; i < stackSlots.length; i++) {
            final FrameSlot slot = stackSlots[i];
            if (slot == null) {
                writer.writeNil();
            } else {
                final Object stackValue = truffleFrame.getValue(slot);
                if (stackValue == null) {
                    writer.writeNil();
                } else {
                    writer.writeObject(stackValue);
                }
            }
        }
    }

    public PointersObject getProcess() {
        return process;
    }

    public void setProcess(final PointersObject process) {
        assert process != null && (this.process == null || this.process == process);
        this.process = process;
    }
}
