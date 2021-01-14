/*
 * Copyright (c) 2017-2021 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.bytecodes;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.trufflesqueak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.trufflesqueak.exceptions.Returns.NonLocalReturn;
import de.hpi.swa.trufflesqueak.exceptions.Returns.NonVirtualReturn;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.AbstractPointersObject;
import de.hpi.swa.trufflesqueak.model.ClassObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.CONTEXT;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.POINT;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectReadNode;
import de.hpi.swa.trufflesqueak.nodes.context.ArgumentNodes.AbstractArgumentNode;
import de.hpi.swa.trufflesqueak.nodes.context.frame.FrameStackPushNode;
import de.hpi.swa.trufflesqueak.nodes.context.frame.FrameStackReadNode;
import de.hpi.swa.trufflesqueak.nodes.context.frame.FrameStackWriteNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.DispatchLookupResultNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.DispatchSuperSendNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.LookupClassNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.LookupSelectorNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveNodeFactory;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.ControlPrimitives;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.ControlPrimitives.PrimExitToDebuggerNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.ControlPrimitivesFactory;
import de.hpi.swa.trufflesqueak.util.FrameAccess;

public final class SendBytecodes {
    public abstract static class AbstractSendNode extends AbstractInstrumentableBytecodeNode {
        protected final int argumentCount;
        @CompilationFinal private FrameSlot stackPointerSlot;
        @CompilationFinal private int stackPointer;

        @Child private FrameStackPushNode pushNode;

        private final ConditionProfile nlrProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile nvrProfile = ConditionProfile.createBinaryProfile();

        private AbstractSendNode(final CompiledCodeObject code, final int index, final int numBytecodes, final int numArgs) {
            super(code, index, numBytecodes);
            argumentCount = numArgs;
        }

        protected AbstractSendNode(final AbstractSendNode original) {
            this(original.code, original.index, original.getNumBytecodes(), original.argumentCount);
        }

        @Override
        public final void executeVoid(final VirtualFrame frame) {
            try {
                decrementStackPointerByNumReceiverAndArguments(frame);
                final Object result = dispatchSend(frame);
                assert result != null : "Result of a message send should not be null";
                getPushNode().execute(frame, result);
            } catch (final NonLocalReturn nlr) {
                if (nlrProfile.profile(nlr.getTargetContextOrMarker() == getMarker(frame) || nlr.getTargetContextOrMarker() == getContext(frame))) {
                    getPushNode().execute(frame, nlr.getReturnValue());
                } else {
                    throw nlr;
                }
            } catch (final NonVirtualReturn nvr) {
                if (nvrProfile.profile(nvr.getTargetContext() == getContext(frame))) {
                    getPushNode().execute(frame, nvr.getReturnValue());
                } else {
                    throw nvr;
                }
            }
        }

        private void decrementStackPointerByNumReceiverAndArguments(final VirtualFrame frame) {
            if (stackPointerSlot == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                stackPointerSlot = FrameAccess.findStackPointerSlot(frame);
                stackPointer = FrameAccess.getStackPointer(frame, stackPointerSlot) - (1 + argumentCount);
                assert stackPointer >= 0 : "Bad stack pointer";
            }
            FrameAccess.setStackPointer(frame, stackPointerSlot, stackPointer);
        }

        protected abstract Object dispatchSend(VirtualFrame frame);

        private FrameStackPushNode getPushNode() {
            if (pushNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pushNode = insert(FrameStackPushNode.create());
            }
            return pushNode;
        }

        public abstract NativeObject getSelector();

        @Override
        public final boolean hasTag(final Class<? extends Tag> tag) {
            if (tag == StandardTags.CallTag.class) {
                return true;
            }
            if (tag == DebuggerTags.AlwaysHalt.class) {
                return PrimExitToDebuggerNode.SELECTOR_NAME.equals(getSelector().asStringUnsafe());
            }
            return super.hasTag(tag);
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return "send: " + getSelector().asStringUnsafe();
        }
    }

    public static final class SelfSendNode extends AbstractSendNode {
        public static final int INLINE_CACHE_SIZE = 6;

        @Child private FrameStackReadNode peekAtReceiverNode;
        @Child private LookupClassNode lookupClassNode = LookupClassNode.create();
        @Child private LookupSelectorNode lookupSelectorNode;
        @Child private DispatchLookupResultNode dispatchNode;

        private SelfSendNode(final CompiledCodeObject code, final int index, final int numBytecodes, final NativeObject selector, final int numArgs) {
            super(code, index, numBytecodes, numArgs);
            lookupSelectorNode = LookupSelectorNode.create(selector);
            dispatchNode = DispatchLookupResultNode.create(selector, numArgs);
        }

        public static SelfSendNode create(final CompiledCodeObject code, final int index, final int numBytecodes, final NativeObject selector, final int numArgs) {
            return new SelfSendNode(code, index, numBytecodes, selector, numArgs);
        }

        @Override
        protected Object dispatchSend(final VirtualFrame frame) {
            final Object receiver = peekAtReceiver(frame);
            final ClassObject receiverClass = lookupClassNode.execute(receiver);
            final Object lookupResult = lookupSelectorNode.execute(receiverClass);
            return dispatchNode.execute(frame, receiver, receiverClass, lookupResult);
        }

        @Override
        public NativeObject getSelector() {
            return dispatchNode.getSelector();
        }

        private Object peekAtReceiver(final VirtualFrame frame) {
            if (peekAtReceiverNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                final int stackPointer = FrameAccess.getStackPointer(frame, code);
                peekAtReceiverNode = insert(FrameStackReadNode.create(frame, stackPointer, false));
            }
            return peekAtReceiverNode.executeRead(frame);
        }
    }

    public static final class SuperSendNode extends AbstractSendNode {
        @Child private DispatchSuperSendNode dispatchNode;

        public SuperSendNode(final CompiledCodeObject code, final int index, final int numBytecodes, final byte param) {
            this(code, index, numBytecodes, param & 31, Byte.toUnsignedInt(param) >> 5);
        }

        public SuperSendNode(final CompiledCodeObject code, final int index, final int numBytecodes, final int literalIndex, final int numArgs) {
            super(code, index, numBytecodes, numArgs);
            final NativeObject selector = (NativeObject) code.getLiteral(literalIndex);
            dispatchNode = DispatchSuperSendNode.create(code, selector, numArgs);
        }

        @Override
        protected Object dispatchSend(final VirtualFrame frame) {
            return dispatchNode.execute(frame);
        }

        @Override
        public NativeObject getSelector() {
            return dispatchNode.getSelector();
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return "sendSuper: " + getSelector().asStringUnsafe();
        }
    }

    public static final class DirectedSuperSendNode extends AbstractSendNode {

        @CompilationFinal private FrameSlot stackPointerSlot;
        @CompilationFinal private int stackPointer;

        @Child private FrameStackReadNode peekAtReceiverNode;
        @Child private FrameStackReadNode readDirectedClassNode;
        @Child private LookupSelectorNode lookupSelectorNode;
        @Child private DispatchLookupResultNode dispatchNode;

        public DirectedSuperSendNode(final CompiledCodeObject code, final int index, final int numBytecodes, final int selectorLiteralIndex, final int numArgs) {
            super(code, index, numBytecodes, numArgs);
            assert 0 <= selectorLiteralIndex && selectorLiteralIndex < 65535 : "selectorLiteralIndex out of range";
            assert 0 <= numArgs && numArgs <= 31 : "numArgs out of range";
            final NativeObject selector = (NativeObject) code.getLiteral(selectorLiteralIndex);
            lookupSelectorNode = LookupSelectorNode.create(selector);
            dispatchNode = DispatchLookupResultNode.create(selector, numArgs);
        }

        @Override
        protected Object dispatchSend(final VirtualFrame frame) {
            final ClassObject superclass = popDirectedClass(frame).getSuperclassOrNull();
            assert superclass != null;
            final Object lookupResult = lookupSelectorNode.execute(superclass);
            final Object receiver = peekAtReceiver(frame);
            return dispatchNode.execute(frame, receiver, superclass, lookupResult);
        }

        @Override
        public NativeObject getSelector() {
            return dispatchNode.getSelector();
        }

        protected Object peekAtReceiver(final VirtualFrame frame) {
            if (peekAtReceiverNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                final int sp = FrameAccess.getStackPointer(frame, code);
                peekAtReceiverNode = insert(FrameStackReadNode.create(frame, sp, false));
            }
            return peekAtReceiverNode.executeRead(frame);
        }

        protected ClassObject popDirectedClass(final VirtualFrame frame) {
            if (readDirectedClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                stackPointerSlot = FrameAccess.findStackPointerSlot(frame);
                /* Decrement sp to pop directed class. */
                stackPointer = FrameAccess.getStackPointer(frame, stackPointerSlot) - 1;
                /*
                 * Read and clear directed class. Add receiver and argumentCount (sp already
                 * decremented in AbstractSendNode).
                 */
                readDirectedClassNode = insert(FrameStackReadNode.create(frame, stackPointer + 1 + argumentCount, true));
            }
            FrameAccess.setStackPointer(frame, stackPointerSlot, stackPointer);
            return (ClassObject) readDirectedClassNode.executeRead(frame);
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return "directedSuperSend: " + getSelector().asStringUnsafe();
        }
    }

    /*
     * Try to execute primitive for special selectors. Replaces itself with a normal send on
     * primitive failure. Modifies the stack only once, primitives read their arguments directly
     * from the stack.
     */
    public abstract static class AbstractSendSpecialSelectorQuickNode extends AbstractInstrumentableBytecodeNode {
        protected final int selectorIndex;

        @Child protected FrameStackWriteNode writeNode;

        protected AbstractSendSpecialSelectorQuickNode(final CompiledCodeObject code, final int index, final int selectorIndex) {
            super(code, index, 1);
            this.selectorIndex = selectorIndex;
        }

        protected static final AbstractArgumentNode[] createArgumentNodes(final int numArguments) {
            final int numReceiverAndArguments = 1 + numArguments;
            final AbstractArgumentNode[] argumentNodes = new AbstractArgumentNode[numReceiverAndArguments];
            for (int i = 0; i < numReceiverAndArguments; i++) {
                argumentNodes[i] = AbstractArgumentNode.create(i - numReceiverAndArguments, true);
            }
            return argumentNodes;
        }

        public static AbstractBytecodeNode create(final CompiledCodeObject code, final int index, final int selectorIndex) {
            NodeFactory<? extends AbstractPrimitiveNode> nodeFactory = null;
            final SqueakImageContext image = code.getSqueakClass().getImage();
            final NativeObject specialSelector = image.getSpecialSelector(selectorIndex);
            final int numArguments = image.getSpecialSelectorNumArgs(selectorIndex);
            if (0 <= selectorIndex && selectorIndex <= 15) { // arithmetic primitives
                /* Look up specialSelector in SmallInteger class. */
                final CompiledCodeObject method = (CompiledCodeObject) image.smallIntegerClass.lookupInMethodDictSlow(specialSelector);
                assert method.hasPrimitive() && method.getNumArgs() == numArguments;
                nodeFactory = PrimitiveNodeFactory.getNodeFactory(method.primitiveIndex(), 1 + numArguments);
                assert nodeFactory != null;
            } else if (selectorIndex == 16 || selectorIndex == 17) { // #at:, #at:put:
                return new SendSpecialSelectorQuickWithClassCheck1OrMoreArgumentsNode(code, index, selectorIndex);
            } else if (selectorIndex == 18) { // #size
                return new SendSpecialSelectorQuickWithClassCheck0ArgumentsNode(code, index, selectorIndex);
            } else if (selectorIndex == 22) { // #==
                nodeFactory = ControlPrimitivesFactory.PrimIdentical2NodeFactory.getInstance();
            } else if (selectorIndex == 23) { // #class
                nodeFactory = ControlPrimitivesFactory.PrimClass1NodeFactory.getInstance();
            } else if (selectorIndex == 24) { // #~~
                nodeFactory = ControlPrimitivesFactory.PrimNotIdenticalNodeFactory.getInstance();
            } else if (selectorIndex == 25 || selectorIndex == 26) { // #value, #value:
                /*
                 * Closure primitives must go through the normal send infrastructure. This node does
                 * not handle NonLocalReturn and NonVirtualReturn.
                 */
            } else if (selectorIndex == 28) { // #new
                return new SendSpecialSelectorQuickWithClassCheck0ArgumentsNode(code, index, selectorIndex);
            } else if (selectorIndex == 29) { // #new:
                return new SendSpecialSelectorQuickWithClassCheck1OrMoreArgumentsNode(code, index, selectorIndex);
            } else if (selectorIndex == 30) { // #x
                return new SendSpecialSelectorQuickPointXNode(code, index, selectorIndex);
            } else if (selectorIndex == 31) { // #y
                return new SendSpecialSelectorQuickPointYNode(code, index, selectorIndex);
            }
            if (nodeFactory != null) {
                if (numArguments == 0) {
                    return new SendSpecialSelectorQuick0ArgumentsNode(code, index, selectorIndex, nodeFactory, numArguments);
                } else {
                    return new SendSpecialSelectorQuick1OrMoreArgumentsNode(code, index, selectorIndex, nodeFactory, numArguments);
                }
            } else {
                return new SelfSendNode(code, index, 1, specialSelector, numArguments);
            }
        }

        protected final void replaceWithSend(final VirtualFrame frame) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // Replace with normal send (pc needs to be written)
            FrameAccess.setInstructionPointer(frame, code, getSuccessorIndex());
            // Lookup specialSelector and replace with normal send
            final SqueakImageContext image = code.getSqueakClass().getImage(); // TODO: Refactor
            final NativeObject specialSelector = image.getSpecialSelector(selectorIndex);
            final int numArguments = image.getSpecialSelectorNumArgs(selectorIndex);
            replace(new SelfSendNode(code, index - code.getInitialPC(), 1, specialSelector, numArguments)).executeVoid(frame);
        }

        protected final int findNewStackPointer(final VirtualFrame frame) {
            final int numArguments = findNumArguments();
            final int stackPointer = FrameAccess.getStackPointer(frame, code) - (1 + numArguments) + 1;
            assert stackPointer <= CONTEXT.MAX_STACK_SIZE : "Bad stack pointer";
            return stackPointer;
        }

        protected final FrameStackWriteNode createFrameSlotWriteNode(final VirtualFrame frame) {
            return createFrameSlotWriteNode(frame, findNewStackPointer(frame));
        }

        protected static final FrameStackWriteNode createFrameSlotWriteNode(final VirtualFrame frame, final int newStackPointer) {
            return FrameStackWriteNode.create(frame, newStackPointer - 1);
        }

        protected final NativeObject findSelector() {
            return code.getSqueakClass().getImage().getSpecialSelector(selectorIndex);
        }

        protected final int findNumArguments() {
            return code.getSqueakClass().getImage().getSpecialSelectorNumArgs(selectorIndex);
        }

        @Override
        public final String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return "send: " + findSelector().asStringUnsafe();
        }
    }

    private abstract static class SendSpecialSelectorQuickNode extends AbstractSendSpecialSelectorQuickNode {
        @Child protected AbstractPrimitiveNode primitiveNode;

        private SendSpecialSelectorQuickNode(final CompiledCodeObject code, final int index, final int selectorIndex, final NodeFactory<? extends AbstractPrimitiveNode> nodeFactory,
                        final int numArguments) {
            super(code, index, selectorIndex);
            primitiveNode = nodeFactory.createNode((Object) createArgumentNodes(numArguments));
        }

        @Override
        public void executeVoid(final VirtualFrame frame) {
            try {
                popArgumentAndPush(frame, primitiveNode.execute(frame));
            } catch (final PrimitiveFailed pf) {
                replaceWithSend(frame);
            }
        }

        protected abstract void popArgumentAndPush(VirtualFrame frame, Object result);
    }

    private static final class SendSpecialSelectorQuick0ArgumentsNode extends SendSpecialSelectorQuickNode {
        private SendSpecialSelectorQuick0ArgumentsNode(final CompiledCodeObject code, final int index, final int selectorIndex, final NodeFactory<? extends AbstractPrimitiveNode> nodeFactory,
                        final int numArguments) {
            super(code, index, selectorIndex, nodeFactory, numArguments);
        }

        @Override
        protected void popArgumentAndPush(final VirtualFrame frame, final Object result) {
            if (writeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                assert findNumArguments() == 0;
                writeNode = insert(createFrameSlotWriteNode(frame));
            }
            writeNode.executeWrite(frame, result);
        }
    }

    private static final class SendSpecialSelectorQuick1OrMoreArgumentsNode extends SendSpecialSelectorQuickNode {
        @CompilationFinal private int stackPointer;

        private SendSpecialSelectorQuick1OrMoreArgumentsNode(final CompiledCodeObject code, final int index, final int selectorIndex, final NodeFactory<? extends AbstractPrimitiveNode> nodeFactory,
                        final int numArguments) {
            super(code, index, selectorIndex, nodeFactory, numArguments);
        }

        @Override
        protected void popArgumentAndPush(final VirtualFrame frame, final Object result) {
            if (writeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                assert findNumArguments() > 0;
                stackPointer = findNewStackPointer(frame);
                writeNode = insert(createFrameSlotWriteNode(frame, stackPointer));
            }
            FrameAccess.setStackPointer(frame, code, stackPointer);
            writeNode.executeWrite(frame, result);
        }
    }

    private abstract static class SendSpecialSelectorQuickWithClassCheckNode extends AbstractSendSpecialSelectorQuickNode {
        @CompilationFinal private ClassObject cachedReceiverClass;
        @CompilationFinal private Assumption cachedCallTargetStableAssumption;

        @Child protected AbstractPrimitiveNode primitiveNode;
        @Child private FrameStackReadNode peekAtReceiverNode;
        @Child private LookupClassNode lookupClassNode = LookupClassNode.create();

        private SendSpecialSelectorQuickWithClassCheckNode(final CompiledCodeObject code, final int index, final int selectorIndex) {
            super(code, index, selectorIndex);
        }

        @Override
        public void executeVoid(final VirtualFrame frame) {
            final Object receiver = peekAtReceiver(frame);
            if (doesNotMatchClassOrMethodInvalidated(lookupClassNode.execute(receiver))) {
                replaceWithSend(frame);
                return;
            }
            try {
                popArgumentsAndPush(frame, primitiveNode.execute(frame));
            } catch (final PrimitiveFailed pf) {
                replaceWithSend(frame);
            }
        }

        protected abstract void popArgumentsAndPush(VirtualFrame frame, Object result);

        private boolean doesNotMatchClassOrMethodInvalidated(final ClassObject actualClass) {
            if (primitiveNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                assert actualClass != null;
                final Object lookupResult = actualClass.lookupInMethodDictSlow(findSelector());
                if (lookupResult instanceof CompiledCodeObject && ((CompiledCodeObject) lookupResult).hasPrimitive()) {
                    final CompiledCodeObject primitiveMethod = (CompiledCodeObject) lookupResult;
                    final int primitiveIndex = primitiveMethod.primitiveIndex();
                    assert primitiveMethod.getNumArgs() == findNumArguments();
                    final int numArguments = primitiveMethod.getNumArgs();
                    final AbstractPrimitiveNode node;
                    if (PrimitiveNodeFactory.isLoadInstVarPrimitive(primitiveIndex)) {
                        assert numArguments == 0;
                        node = ControlPrimitives.PrimLoadInstVarNode.create(primitiveIndex - PrimitiveNodeFactory.PRIMITIVE_LOAD_INST_VAR_LOWER_INDEX,
                                        new AbstractArgumentNode[]{AbstractArgumentNode.create(-1, true)});
                    } else {
                        final NodeFactory<? extends AbstractPrimitiveNode> nodeFactory;
                        if (primitiveIndex == 117) {
                            nodeFactory = PrimitiveNodeFactory.getNodeFactory(primitiveMethod, 1 + numArguments);
                        } else {
                            nodeFactory = PrimitiveNodeFactory.getNodeFactory(primitiveIndex, 1 + numArguments);
                        }
                        if (nodeFactory == null) {
                            return true; // primitive not found / supported
                        }
                        node = nodeFactory.createNode((Object) createArgumentNodes(numArguments));
                    }
                    primitiveNode = insert(node);
                    cachedReceiverClass = actualClass;
                    cachedCallTargetStableAssumption = primitiveMethod.getCallTargetStable();
                } else {
                    return true;
                }
            }
            return actualClass != cachedReceiverClass || !cachedCallTargetStableAssumption.isValid();
        }

        private Object peekAtReceiver(final VirtualFrame frame) {
            if (peekAtReceiverNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                final int currentStackPointer = FrameAccess.getStackPointer(frame, code) - (1 + findNumArguments());
                peekAtReceiverNode = insert(FrameStackReadNode.create(frame, currentStackPointer, false));
            }
            return peekAtReceiverNode.executeRead(frame);
        }
    }

    private static final class SendSpecialSelectorQuickWithClassCheck0ArgumentsNode extends SendSpecialSelectorQuickWithClassCheckNode {
        private SendSpecialSelectorQuickWithClassCheck0ArgumentsNode(final CompiledCodeObject code, final int index, final int selectorIndex) {
            super(code, index, selectorIndex);
        }

        @Override
        protected void popArgumentsAndPush(final VirtualFrame frame, final Object result) {
            if (writeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                assert findNumArguments() == 0;
                writeNode = insert(createFrameSlotWriteNode(frame));
            }
            writeNode.executeWrite(frame, result);
        }
    }

    private static final class SendSpecialSelectorQuickWithClassCheck1OrMoreArgumentsNode extends SendSpecialSelectorQuickWithClassCheckNode {
        @CompilationFinal private int stackPointer;

        private SendSpecialSelectorQuickWithClassCheck1OrMoreArgumentsNode(final CompiledCodeObject code, final int index, final int selectorIndex) {
            super(code, index, selectorIndex);
        }

        @Override
        protected void popArgumentsAndPush(final VirtualFrame frame, final Object result) {
            if (writeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                assert findNumArguments() > 0;
                stackPointer = findNewStackPointer(frame);
                writeNode = insert(createFrameSlotWriteNode(frame, stackPointer));
            }
            FrameAccess.setStackPointer(frame, code, stackPointer);
            writeNode.executeWrite(frame, result);
        }
    }

    private abstract static class AbstractSendSpecialSelectorQuickPointXYNode extends AbstractSendSpecialSelectorQuickNode {
        @Child private AbstractPointersObjectReadNode readNode = AbstractPointersObjectReadNode.create();
        @Child private FrameStackReadNode peekAtReceiverNode;
        @Child private LookupClassNode lookupClassNode = LookupClassNode.create();
        @CompilationFinal private ClassObject pointClass;

        protected AbstractSendSpecialSelectorQuickPointXYNode(final CompiledCodeObject code, final int index, final int selectorIndex) {
            super(code, index, selectorIndex);
        }

        @Override
        public void executeVoid(final VirtualFrame frame) {
            final Object receiver = peekAtReceiver(frame);
            if (lookupClassNode.execute(receiver) != getPointClass()) {
                replaceWithSend(frame);
                return;
            }
            try {
                popArgumentAndPush(frame, readNode.execute((AbstractPointersObject) receiver, getPointInstVarIndex()));
            } catch (final PrimitiveFailed pf) {
                replaceWithSend(frame);
            }
        }

        private ClassObject getPointClass() {
            if (pointClass == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                pointClass = lookupContext().pointClass;
            }
            return pointClass;
        }

        protected abstract int getPointInstVarIndex();

        private void popArgumentAndPush(final VirtualFrame frame, final Object value) {
            if (writeNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                assert findNumArguments() == 0;
                writeNode = insert(createFrameSlotWriteNode(frame));
            }
            writeNode.executeWrite(frame, value);
        }

        private Object peekAtReceiver(final VirtualFrame frame) {
            if (peekAtReceiverNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                final int currentStackPointer = FrameAccess.getStackPointer(frame, code) - 1;
                peekAtReceiverNode = insert(FrameStackReadNode.create(frame, currentStackPointer, false));
            }
            return peekAtReceiverNode.executeRead(frame);
        }
    }

    private static final class SendSpecialSelectorQuickPointXNode extends AbstractSendSpecialSelectorQuickPointXYNode {
        protected SendSpecialSelectorQuickPointXNode(final CompiledCodeObject code, final int index, final int selectorIndex) {
            super(code, index, selectorIndex);
        }

        @Override
        protected int getPointInstVarIndex() {
            return POINT.X;
        }
    }

    private static final class SendSpecialSelectorQuickPointYNode extends AbstractSendSpecialSelectorQuickPointXYNode {
        protected SendSpecialSelectorQuickPointYNode(final CompiledCodeObject code, final int index, final int selectorIndex) {
            super(code, index, selectorIndex);
        }

        @Override
        protected int getPointInstVarIndex() {
            return POINT.Y;
        }
    }
}
