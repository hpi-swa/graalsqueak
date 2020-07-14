/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.dispatch;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.trufflesqueak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.ClassObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.nodes.AbstractNode;
import de.hpi.swa.trufflesqueak.nodes.LookupMethodNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.SqueakObjectClassNode;
import de.hpi.swa.trufflesqueak.nodes.context.frame.FrameSlotReadNode;
import de.hpi.swa.trufflesqueak.nodes.context.frame.GetContextOrMarkerNode;
import de.hpi.swa.trufflesqueak.nodes.context.frame.GetOrCreateContextNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.CreateFrameArgumentNodes.CreateFrameArgumentsForDNUNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.CreateFrameArgumentNodes.CreateFrameArgumentsForOAMNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.CreateFrameArgumentNodes.UpdateStackPointerNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveNodeFactory;
import de.hpi.swa.trufflesqueak.util.FrameAccess;
import de.hpi.swa.trufflesqueak.util.PrimitiveFailedCounter;

public abstract class CachedDispatchNode extends AbstractNode {
    protected final int argumentCount;
    protected final CompiledCodeObject method;

    public CachedDispatchNode(final int argumentCount, final CompiledCodeObject method) {
        this.argumentCount = argumentCount;
        this.method = method;
        assert getCallTargetStable().isValid() : "callTargetStable must be valid";
    }

    protected static final CachedDispatchNode create(final int argumentCount, final ClassObject receiverClass, final Object lookupResult) {
        final SqueakImageContext image = SqueakLanguage.getContext();
        if (lookupResult == null) {
            return createDNUNode(image, argumentCount, receiverClass);
        } else if (lookupResult instanceof CompiledCodeObject) {
            final CompiledCodeObject lookupMethod = (CompiledCodeObject) lookupResult;
            if (lookupMethod.hasPrimitive()) {
                final AbstractPrimitiveNode primitiveNode = PrimitiveNodeFactory.forIndex(lookupMethod, true, lookupMethod.primitiveIndex());
                if (primitiveNode != null) {
                    return new CachedDispatchPrimitiveNode(argumentCount, lookupMethod, primitiveNode);
                }
            }
            return AbstractCachedDispatchMethodNode.create(argumentCount, lookupMethod);
        } else {
            final ClassObject lookupResultClass = SqueakObjectClassNode.getUncached().executeLookup(lookupResult);
            final Object runWithInMethod = LookupMethodNode.getUncached().executeLookup(lookupResultClass, image.runWithInSelector);
            if (runWithInMethod instanceof CompiledCodeObject) {
                return AbstractCachedDispatchObjectAsMethodNode.create(argumentCount, lookupResult, (CompiledCodeObject) runWithInMethod);
            } else {
                assert runWithInMethod == null : "runWithInMethod should not be another Object";
                return createDNUNode(image, argumentCount, lookupResultClass);
            }
        }
    }

    private static CachedDispatchNode createDNUNode(final SqueakImageContext image, final int argumentCount, final ClassObject receiverClass) {
        final Object dnuMethod = LookupMethodNode.getUncached().executeLookup(receiverClass, image.doesNotUnderstand);
        if (dnuMethod instanceof CompiledCodeObject) {
            return AbstractCachedDispatchDoesNotUnderstandNode.create(argumentCount, (CompiledCodeObject) dnuMethod);
        } else {
            throw SqueakException.create("Unable to find DNU method in", receiverClass);
        }
    }

    protected final Assumption getCallTargetStable() {
        return method.getCallTargetStable();
    }

    protected boolean isPrimitive() {
        return false;
    }

    protected abstract Object execute(VirtualFrame frame, NativeObject cachedSelector, FrameSlotReadNode[] receiverAndArgumentsNodes, UpdateStackPointerNode updateStackPointerNode);

    protected abstract static class CachedDispatchWithDirectCallNode extends CachedDispatchNode {
        @Child protected DirectCallNode callNode;

        public CachedDispatchWithDirectCallNode(final int argumentCount, final CompiledCodeObject method) {
            super(argumentCount, method);
            callNode = DirectCallNode.create(method.getCallTarget());
        }
    }

    protected static final class CachedDispatchPrimitiveNode extends CachedDispatchNode {
        private final PrimitiveFailedCounter failureCounter = new PrimitiveFailedCounter();

        @Child private AbstractPrimitiveNode primitiveNode;

        private CachedDispatchPrimitiveNode(final int argumentCount, final CompiledCodeObject method, final AbstractPrimitiveNode primitiveNode) {
            super(argumentCount, method);
            this.primitiveNode = primitiveNode;
        }

        @Override
        protected boolean isPrimitive() {
            return true;
        }

        @Override
        public Object execute(final VirtualFrame frame, final NativeObject cachedSelector, final FrameSlotReadNode[] receiverAndArgumentsNodes, final UpdateStackPointerNode updateStackPointerNode) {
            /**
             * Pretend that values are popped off the stack. Primitive nodes will read them using
             * ArgumentOnStackNodes.
             */
            updateStackPointerNode.execute(frame, argumentCount);
            try {
                return primitiveNode.executePrimitive(frame);
            } catch (final PrimitiveFailed pf) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (failureCounter.shouldNoLongerSendEagerly()) {
                    updateStackPointerNode.executeRestore(frame, argumentCount);
                    if (receiverAndArgumentsNodes != null) {
                        return replace(AbstractCachedDispatchMethodNode.create(argumentCount, method)).execute(frame, cachedSelector, receiverAndArgumentsNodes, updateStackPointerNode);
                    } else {
                        throw pf;
                    }
                } else {
                    // Slow path send to fallback code.
                    final FrameSlotReadNode[] nodes;
                    if (receiverAndArgumentsNodes != null) {
                        nodes = receiverAndArgumentsNodes;
                    } else {
                        updateStackPointerNode.executeRestore(frame, argumentCount);
                        nodes = AbstractDispatchNode.createReceiverAndArgumentsNodes(frame, argumentCount);
                    }
                    return IndirectCallNode.getUncached().call(method.getCallTarget(),
                                    FrameAccess.newWith(frame, method, FrameAccess.getContextOrMarkerSlow(frame), nodes));
                }
            }
        }
    }

    protected abstract static class AbstractCachedDispatchMethodNode extends CachedDispatchWithDirectCallNode {
        private AbstractCachedDispatchMethodNode(final int argumentCount, final CompiledCodeObject method) {
            super(argumentCount, method);
        }

        protected static AbstractCachedDispatchMethodNode create(final int argumentCount, final CompiledCodeObject method) {
            if (method.getDoesNotNeedSenderAssumption().isValid()) {
                return new CachedDispatchMethodWithoutSenderNode(argumentCount, method);
            } else {
                return new CachedDispatchMethodWithSenderNode(argumentCount, method);
            }
        }

        protected final Object[] createFrameArguments(final VirtualFrame frame, final FrameSlotReadNode[] receiverAndArgumentsNodes, final UpdateStackPointerNode updateStackPointerNode,
                        final Object sender) {
            updateStackPointerNode.execute(frame, receiverAndArgumentsNodes);
            return FrameAccess.newWith(frame, method, sender, receiverAndArgumentsNodes);
        }
    }

    protected static final class CachedDispatchMethodWithoutSenderNode extends AbstractCachedDispatchMethodNode {
        @Child private GetContextOrMarkerNode getContextOrMarkerNode = GetContextOrMarkerNode.create();

        private CachedDispatchMethodWithoutSenderNode(final int argumentCount, final CompiledCodeObject method) {
            super(argumentCount, method);
        }

        @Override
        public Object execute(final VirtualFrame frame, final NativeObject cachedSelector, final FrameSlotReadNode[] receiverAndArgumentsNodes, final UpdateStackPointerNode updateStackPointerNode) {
            try {
                method.getDoesNotNeedSenderAssumption().check();
            } catch (final InvalidAssumptionException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return replace(new CachedDispatchMethodWithSenderNode(argumentCount, method)).execute(frame, cachedSelector, receiverAndArgumentsNodes, updateStackPointerNode);
            }
            return callNode.call(createFrameArguments(frame, receiverAndArgumentsNodes, updateStackPointerNode, getContextOrMarkerNode.execute(frame)));
        }
    }

    protected static final class CachedDispatchMethodWithSenderNode extends AbstractCachedDispatchMethodNode {
        @Child private GetOrCreateContextNode getOrCreateContextNode = GetOrCreateContextNode.create(true);

        private CachedDispatchMethodWithSenderNode(final int argumentCount, final CompiledCodeObject method) {
            super(argumentCount, method);
        }

        @Override
        public Object execute(final VirtualFrame frame, final NativeObject cachedSelector, final FrameSlotReadNode[] receiverAndArgumentsNodes, final UpdateStackPointerNode updateStackPointerNode) {
            return callNode.call(createFrameArguments(frame, receiverAndArgumentsNodes, updateStackPointerNode, getOrCreateContextNode.executeGet(frame)));
        }
    }

    protected abstract static class AbstractCachedDispatchDoesNotUnderstandNode extends CachedDispatchWithDirectCallNode {
        @Child protected CreateFrameArgumentsForDNUNode createFrameArgumentsForDNUNode = CreateFrameArgumentsForDNUNode.create();

        private AbstractCachedDispatchDoesNotUnderstandNode(final int argumentCount, final CompiledCodeObject method) {
            super(argumentCount, method);
        }

        protected static AbstractCachedDispatchDoesNotUnderstandNode create(final int argumentCount, final CompiledCodeObject method) {
            if (method.getDoesNotNeedSenderAssumption().isValid()) {
                return new CachedDispatchDoesNotUnderstandWithoutSenderNode(argumentCount, method);
            } else {
                return new CachedDispatchDoesNotUnderstandWithSenderNode(argumentCount, method);
            }
        }
    }

    protected static final class CachedDispatchDoesNotUnderstandWithoutSenderNode extends AbstractCachedDispatchDoesNotUnderstandNode {
        @Child private GetContextOrMarkerNode getContextOrMarkerNode = GetContextOrMarkerNode.create();

        private CachedDispatchDoesNotUnderstandWithoutSenderNode(final int argumentCount, final CompiledCodeObject method) {
            super(argumentCount, method);
        }

        @Override
        public Object execute(final VirtualFrame frame, final NativeObject cachedSelector, final FrameSlotReadNode[] receiverAndArgumentsNodes, final UpdateStackPointerNode updateStackPointerNode) {
            try {
                method.getDoesNotNeedSenderAssumption().check();
            } catch (final InvalidAssumptionException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return replace(new CachedDispatchDoesNotUnderstandWithSenderNode(argumentCount, method)).execute(frame, cachedSelector, receiverAndArgumentsNodes, updateStackPointerNode);
            }
            return callNode.call(createFrameArgumentsForDNUNode.execute(frame, cachedSelector, method, getContextOrMarkerNode.execute(frame), receiverAndArgumentsNodes, updateStackPointerNode));
        }
    }

    protected static final class CachedDispatchDoesNotUnderstandWithSenderNode extends AbstractCachedDispatchDoesNotUnderstandNode {
        @Child private GetOrCreateContextNode getOrCreateContextNode = GetOrCreateContextNode.create(true);

        private CachedDispatchDoesNotUnderstandWithSenderNode(final int argumentCount, final CompiledCodeObject method) {
            super(argumentCount, method);
        }

        @Override
        public Object execute(final VirtualFrame frame, final NativeObject cachedSelector, final FrameSlotReadNode[] receiverAndArgumentsNodes, final UpdateStackPointerNode updateStackPointerNode) {
            return callNode.call(createFrameArgumentsForDNUNode.execute(frame, cachedSelector, method, getOrCreateContextNode.executeGet(frame), receiverAndArgumentsNodes, updateStackPointerNode));
        }
    }

    protected abstract static class AbstractCachedDispatchObjectAsMethodNode extends CachedDispatchWithDirectCallNode {
        protected final Object object;

        @Child protected CreateFrameArgumentsForOAMNode createFrameArgumentsForOAMNode = CreateFrameArgumentsForOAMNode.create();

        private AbstractCachedDispatchObjectAsMethodNode(final int argumentCount, final Object object, final CompiledCodeObject method) {
            super(argumentCount, method);
            this.object = object;
        }

        public static CachedDispatchNode create(final int argumentCount, final Object lookupResult, final CompiledCodeObject runWithInMethod) {
            if (runWithInMethod.getDoesNotNeedSenderAssumption().isValid()) {
                return new CachedDispatchObjectAsMethodWithoutSenderNode(argumentCount, lookupResult, runWithInMethod);
            } else {
                return new CachedDispatchObjectAsMethodWithSenderNode(argumentCount, lookupResult, runWithInMethod);
            }
        }
    }

    protected static final class CachedDispatchObjectAsMethodWithoutSenderNode extends AbstractCachedDispatchObjectAsMethodNode {
        @Child private GetContextOrMarkerNode getContextOrMarkerNode = GetContextOrMarkerNode.create();

        private CachedDispatchObjectAsMethodWithoutSenderNode(final int argumentCount, final Object object, final CompiledCodeObject method) {
            super(argumentCount, object, method);
        }

        @Override
        public Object execute(final VirtualFrame frame, final NativeObject cachedSelector, final FrameSlotReadNode[] receiverAndArgumentsNodes, final UpdateStackPointerNode updateStackPointerNode) {
            try {
                method.getDoesNotNeedSenderAssumption().check();
            } catch (final InvalidAssumptionException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                return replace(new CachedDispatchObjectAsMethodWithSenderNode(argumentCount, object, method)).execute(frame, cachedSelector, receiverAndArgumentsNodes, updateStackPointerNode);
            }
            return callNode.call(
                            createFrameArgumentsForOAMNode.execute(frame, cachedSelector, object, method, getContextOrMarkerNode.execute(frame), receiverAndArgumentsNodes, updateStackPointerNode));
        }
    }

    protected static final class CachedDispatchObjectAsMethodWithSenderNode extends AbstractCachedDispatchObjectAsMethodNode {
        @Child private GetOrCreateContextNode getOrCreateContextNode = GetOrCreateContextNode.create(true);

        private CachedDispatchObjectAsMethodWithSenderNode(final int argumentCount, final Object object, final CompiledCodeObject method) {
            super(argumentCount, object, method);
        }

        @Override
        public Object execute(final VirtualFrame frame, final NativeObject cachedSelector, final FrameSlotReadNode[] receiverAndArgumentsNodes, final UpdateStackPointerNode updateStackPointerNode) {
            return callNode.call(
                            createFrameArgumentsForOAMNode.execute(frame, cachedSelector, object, method, getOrCreateContextNode.executeGet(frame), receiverAndArgumentsNodes, updateStackPointerNode));
        }
    }
}
