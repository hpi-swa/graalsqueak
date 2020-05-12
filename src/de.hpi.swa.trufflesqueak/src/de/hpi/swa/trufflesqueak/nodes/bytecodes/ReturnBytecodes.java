/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.bytecodes;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.exceptions.Returns.NonLocalReturn;
import de.hpi.swa.trufflesqueak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.CompiledBlockObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.ContextObject;
import de.hpi.swa.trufflesqueak.model.NilObject;
import de.hpi.swa.trufflesqueak.nodes.SendSelectorNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectReadNode;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.ReturnBytecodesFactory.ReturnConstantNodeGen;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.ReturnBytecodesFactory.ReturnReceiverNodeGen;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.ReturnBytecodesFactory.ReturnTopFromBlockNodeGen;
import de.hpi.swa.trufflesqueak.nodes.bytecodes.ReturnBytecodesFactory.ReturnTopFromMethodNodeGen;
import de.hpi.swa.trufflesqueak.nodes.context.frame.FrameStackPopNode;
import de.hpi.swa.trufflesqueak.nodes.context.frame.GetOrCreateContextNode;
import de.hpi.swa.trufflesqueak.util.FrameAccess;

public final class ReturnBytecodes {

    public abstract static class AbstractReturnNode extends AbstractBytecodeNode {
        protected AbstractReturnNode(final CompiledCodeObject code, final int index) {
            super(code, index);
        }

        protected final boolean hasModifiedSender(final VirtualFrame frame) {
            final ContextObject context = getContext(frame);
            return context != null && context.hasModifiedSender();
        }

        @Override
        public final void executeVoid(final VirtualFrame frame) {
            throw SqueakException.create("executeReturn() should be called instead");
        }

        public final Object executeReturn(final VirtualFrame frame) {
            return executeReturnSpecialized(frame);
        }

        protected abstract Object executeReturnSpecialized(VirtualFrame frame);

        @SuppressWarnings("unused")
        protected Object getReturnValue(final VirtualFrame frame) {
            throw SqueakException.create("Needs to be overriden");
        }
    }

    protected abstract static class AbstractReturnWithSpecializationsNode extends AbstractReturnNode {
        @Child private SendSelectorNode cannotReturnNode;
        @Child private GetOrCreateContextNode getOrCreateContextNode;

        protected AbstractReturnWithSpecializationsNode(final CompiledCodeObject code, final int index) {
            super(code, index);
        }

        @Specialization(guards = {"isCompiledMethodObject(code)", "!hasModifiedSender(frame)"})
        protected final Object doLocalReturn(final VirtualFrame frame) {
            return getReturnValue(frame);
        }

        @Specialization(guards = {"isCompiledMethodObject(code)", "hasModifiedSender(frame)"})
        protected final Object doNonLocalReturn(final VirtualFrame frame) {
            assert FrameAccess.getSender(frame) instanceof ContextObject : "Sender must be a materialized ContextObject";
            throw new NonLocalReturn(getReturnValue(frame), FrameAccess.getSender(frame));
        }

        @Specialization(guards = {"isCompiledBlockObject(code)"})
        protected final Object doClosureReturnFromMaterialized(final VirtualFrame frame,
                        @Cached final AbstractPointersObjectReadNode readNode,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            // Target is sender of closure's home context.
            final ContextObject homeContext = FrameAccess.getClosure(frame).getHomeContext();
            assert homeContext.getProcess() != null;
            final Object caller = homeContext.getFrameSender();
            final boolean homeContextNotOnTheStack = homeContext.getProcess() != image.getActiveProcess(readNode);
            if (caller == NilObject.SINGLETON || homeContextNotOnTheStack) {
                /** {@link getCannotReturnNode()} acts as {@link BranchProfile} */
                getCannotReturnNode(image).executeSend(frame, getGetOrCreateContextNode().executeGet(frame), getReturnValue(frame));
                throw SqueakException.create("Should not reach");
            }
            throw new NonLocalReturn(getReturnValue(frame), caller);
        }

        private GetOrCreateContextNode getGetOrCreateContextNode() {
            if (getOrCreateContextNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getOrCreateContextNode = insert(GetOrCreateContextNode.create(true));
            }
            return getOrCreateContextNode;
        }

        private SendSelectorNode getCannotReturnNode(final SqueakImageContext image) {
            if (cannotReturnNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cannotReturnNode = insert(SendSelectorNode.create(image.cannotReturn));
            }
            return cannotReturnNode;
        }
    }

    public abstract static class ReturnConstantNode extends AbstractReturnWithSpecializationsNode {
        public final Object constant;

        protected ReturnConstantNode(final CompiledCodeObject code, final int index, final Object obj) {
            super(code, index);
            constant = obj;
        }

        public static ReturnConstantNode create(final CompiledCodeObject code, final int index, final Object value) {
            return ReturnConstantNodeGen.create(code, index, value);
        }

        @Override
        protected final Object getReturnValue(final VirtualFrame frame) {
            return constant;
        }

        @Override
        public final String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return "return: " + constant.toString();
        }
    }

    public abstract static class ReturnReceiverNode extends AbstractReturnWithSpecializationsNode {

        protected ReturnReceiverNode(final CompiledCodeObject code, final int index) {
            super(code, index);
        }

        public static ReturnReceiverNode create(final CompiledCodeObject code, final int index) {
            return ReturnReceiverNodeGen.create(code, index);
        }

        @Override
        protected final Object getReturnValue(final VirtualFrame frame) {
            return FrameAccess.getReceiver(frame);
        }

        @Override
        public final String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return "returnSelf";
        }
    }

    public abstract static class ReturnTopFromBlockNode extends AbstractReturnNode {
        @Child private FrameStackPopNode popNode = FrameStackPopNode.create();
        @Child private SendSelectorNode cannotReturnNode;

        protected ReturnTopFromBlockNode(final CompiledCodeObject code, final int index) {
            super(code, index);
            assert code instanceof CompiledBlockObject : "blockReturn can only occure in CompiledBlockObject";
        }

        public static ReturnTopFromBlockNode create(final CompiledCodeObject code, final int index) {
            return ReturnTopFromBlockNodeGen.create(code, index);
        }

        @Specialization(guards = {"!hasModifiedSender(frame)"})
        protected final Object doLocalReturn(final VirtualFrame frame) {
            return getReturnValue(frame);
        }

        @Specialization(guards = {"hasModifiedSender(frame)"})
        protected final Object doNonLocalReturnClosure(final VirtualFrame frame,
                        @Cached final AbstractPointersObjectReadNode readNode,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            // Target is sender of closure's home context.
            final ContextObject homeContext = FrameAccess.getClosure(frame).getHomeContext();
            assert homeContext.getProcess() != null;
            final boolean homeContextNotOnTheStack = homeContext.getProcess() != image.getActiveProcess(readNode);
            final Object caller = homeContext.getFrameSender();
            if (caller == NilObject.SINGLETON || homeContextNotOnTheStack) {
                final ContextObject currentContext = FrameAccess.getContext(frame, code);
                assert currentContext != null;
                getCannotReturnNode(image).executeSend(frame, currentContext, getReturnValue(frame));
                throw SqueakException.create("Should not reach");
            }
            throw new NonLocalReturn(getReturnValue(frame), caller);
        }

        private SendSelectorNode getCannotReturnNode(final SqueakImageContext image) {
            if (cannotReturnNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                cannotReturnNode = insert(SendSelectorNode.create(image.cannotReturn));
            }
            return cannotReturnNode;
        }

        @Override
        protected final Object getReturnValue(final VirtualFrame frame) {
            return popNode.execute(frame);
        }

        @Override
        public final String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return "blockReturn";
        }
    }

    public abstract static class ReturnTopFromMethodNode extends AbstractReturnWithSpecializationsNode {
        @Child private FrameStackPopNode popNode = FrameStackPopNode.create();

        protected ReturnTopFromMethodNode(final CompiledCodeObject code, final int index) {
            super(code, index);
        }

        public static ReturnTopFromMethodNode create(final CompiledCodeObject code, final int index) {
            return ReturnTopFromMethodNodeGen.create(code, index);
        }

        @Override
        protected final Object getReturnValue(final VirtualFrame frame) {
            return popNode.execute(frame);
        }

        @Override
        public String toString() {
            CompilerAsserts.neverPartOfCompilation();
            return "returnTop";
        }
    }
}
