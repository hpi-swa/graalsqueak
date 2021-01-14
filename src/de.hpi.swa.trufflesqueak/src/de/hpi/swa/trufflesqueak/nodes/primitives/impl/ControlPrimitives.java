/*
 * Copyright (c) 2017-2021 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.primitives.impl;

import java.lang.management.ManagementFactory;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.trufflesqueak.exceptions.ProcessSwitch;
import de.hpi.swa.trufflesqueak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.trufflesqueak.exceptions.SqueakExceptions.SqueakQuit;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.AbstractSqueakObject;
import de.hpi.swa.trufflesqueak.model.AbstractSqueakObjectWithClassAndHash;
import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.BooleanObject;
import de.hpi.swa.trufflesqueak.model.ClassObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.ContextObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.model.NilObject;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.MUTEX;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.PROCESS;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.PROCESS_SCHEDULER;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.SEMAPHORE;
import de.hpi.swa.trufflesqueak.nodes.InheritsFromNode;
import de.hpi.swa.trufflesqueak.nodes.LookupMethodNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectReadNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.ArrayObjectNodes.ArrayObjectReadNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.ArrayObjectNodes.ArrayObjectSizeNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.ArrayObjectNodes.ArrayObjectToObjectArrayCopyNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.ArrayObjectNodes.ArrayObjectToObjectArrayWithFirstNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.SqueakObjectAt0Node;
import de.hpi.swa.trufflesqueak.nodes.accessing.SqueakObjectChangeClassOfToNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.SqueakObjectClassNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.SqueakObjectIdentityNode;
import de.hpi.swa.trufflesqueak.nodes.context.ArgumentNodes.AbstractArgumentNode;
import de.hpi.swa.trufflesqueak.nodes.context.frame.FrameStackPointerIncrementNode;
import de.hpi.swa.trufflesqueak.nodes.context.frame.FrameStackPushNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.DispatchEagerlyNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.DispatchSendNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.DispatchSendNode.DispatchSendHeadlessErrorNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.DispatchSendNode.DispatchSendSelectorNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.DispatchSendNode.DispatchSendSyntaxErrorNode;
import de.hpi.swa.trufflesqueak.nodes.dispatch.DispatchSendNodeFactory.DispatchSendSelectorNodeGen;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractSingletonPrimitiveNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveFallbacks.BinaryPrimitiveFallback;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveFallbacks.QuaternaryPrimitiveFallback;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveFallbacks.QuinaryPrimitiveFallback;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveFallbacks.SenaryPrimitiveFallback;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveFallbacks.SeptenaryPrimitiveFallback;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveFallbacks.TernaryPrimitiveFallback;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveFallbacks.UnaryPrimitiveFallback;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveNodeFactory;
import de.hpi.swa.trufflesqueak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.ControlPrimitivesFactory.PrimLoadInstVarNodeGen;
import de.hpi.swa.trufflesqueak.nodes.process.GetActiveProcessNode;
import de.hpi.swa.trufflesqueak.nodes.process.LinkProcessToListNode;
import de.hpi.swa.trufflesqueak.nodes.process.RemoveProcessFromListNode;
import de.hpi.swa.trufflesqueak.nodes.process.ResumeProcessNode;
import de.hpi.swa.trufflesqueak.nodes.process.SignalSemaphoreNode;
import de.hpi.swa.trufflesqueak.nodes.process.WakeHighestPriorityNode;
import de.hpi.swa.trufflesqueak.util.InterruptHandlerNode;
import de.hpi.swa.trufflesqueak.util.LogUtils;
import de.hpi.swa.trufflesqueak.util.MiscUtils;

public final class ControlPrimitives extends AbstractPrimitiveFactoryHolder {

    protected abstract static class AbstractPrimitiveStackIncrementNode extends AbstractPrimitiveNode {
        @Child protected FrameStackPointerIncrementNode frameStackPointerIncrementNode;

        protected final FrameStackPointerIncrementNode getFrameStackPointerIncrementNode() {
            if (frameStackPointerIncrementNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                frameStackPointerIncrementNode = insert(FrameStackPointerIncrementNode.create());
            }
            return frameStackPointerIncrementNode;
        }
    }

    protected abstract static class AbstractPrimitiveStackPushNode extends AbstractPrimitiveNode {
        @Child protected FrameStackPushNode frameStackPushNode;

        protected final FrameStackPushNode getFrameStackPushNode() {
            if (frameStackPushNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                frameStackPushNode = insert(FrameStackPushNode.create());
            }
            return frameStackPushNode;
        }
    }

    protected abstract static class AbstractPerformPrimitiveNode extends AbstractPrimitiveNode {
        protected static final int CACHE_LIMIT = 2;

        protected final DispatchSendNode createDispatchSendNode(final NativeObject selector) {
            final SqueakImageContext image = lookupContext();
            if (image.isHeadless()) {
                if (selector.isDebugErrorSelector(image)) {
                    return new DispatchSendHeadlessErrorNode();
                } else if (selector.isDebugSyntaxErrorSelector(image)) {
                    return new DispatchSendSyntaxErrorNode();
                }
            }
            return DispatchSendSelectorNodeGen.create();
        }
    }

    /* primitiveFail (#19) handled specially. */

    // primitiveBlockCopy / primitiveBlockValue: (#80, #81, #82) no longer needed.

    public abstract static class AbstractPrimPerformNode extends AbstractPerformPrimitiveNode {
        protected static final Object dispatchCached(final VirtualFrame frame, final NativeObject selector, final Object[] receiverAndArguments, final SqueakObjectClassNode lookupClassNode,
                        final LookupMethodNode lookupMethodNode, final DispatchSendNode dispatchNode) {
            final ClassObject rcvrClass = lookupClassNode.executeLookup(receiverAndArguments[0]);
            final Object lookupResult = lookupMethodNode.executeLookup(rcvrClass, selector);
            return dispatchNode.executeSend(frame, selector, lookupResult, rcvrClass, receiverAndArguments);
        }

        protected static final Object dispatchUncached(final VirtualFrame frame, final NativeObject selector, final Object[] receiverAndArguments, final SqueakObjectClassNode lookupClassNode,
                        final LookupMethodNode lookupMethodNode, final DispatchSendSelectorNode dispatchNode) {
            final ClassObject rcvrClass = lookupClassNode.executeLookup(receiverAndArguments[0]);
            final Object lookupResult = lookupMethodNode.executeLookup(rcvrClass, selector);
            return dispatchNode.executeSend(frame, selector, lookupResult, rcvrClass, receiverAndArguments);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 83)
    protected abstract static class PrimPerform1Node extends AbstractPrimPerformNode implements BinaryPrimitiveFallback {
        @SuppressWarnings("unused")
        @Specialization(guards = "selector == cachedSelector", limit = "CACHE_LIMIT")
        protected static final Object perform0Cached(final VirtualFrame frame, final Object receiver, final NativeObject selector,
                        @Cached("selector") final NativeObject cachedSelector,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached("createDispatchSendNode(cachedSelector)") final DispatchSendNode dispatchNode) {
            return dispatchCached(frame, cachedSelector, new Object[]{receiver}, lookupClassNode, lookupMethodNode, dispatchNode);
        }

        @SuppressWarnings("unused")
        @Specialization(replaces = "perform0Cached")
        protected static final Object perform0(final VirtualFrame frame, final Object receiver, final NativeObject selector,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached final DispatchSendSelectorNode dispatchNode) {
            return dispatchUncached(frame, selector, new Object[]{receiver}, lookupClassNode, lookupMethodNode, dispatchNode);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 83)
    protected abstract static class PrimPerform2Node extends AbstractPrimPerformNode implements TernaryPrimitiveFallback {
        @SuppressWarnings("unused")
        @Specialization(guards = {"selector == cachedSelector"}, limit = "CACHE_LIMIT")
        protected static final Object perform1Cached(final VirtualFrame frame, final Object receiver, final NativeObject selector, final Object object1,
                        @Cached("selector") final NativeObject cachedSelector,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached("createDispatchSendNode(cachedSelector)") final DispatchSendNode dispatchNode) {
            return dispatchCached(frame, cachedSelector, new Object[]{receiver, object1}, lookupClassNode, lookupMethodNode, dispatchNode);
        }

        @SuppressWarnings("unused")
        @Specialization(replaces = "perform1Cached")
        protected static final Object perform1(final VirtualFrame frame, final Object receiver, final NativeObject selector, final Object object1,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached final DispatchSendSelectorNode dispatchNode) {
            return dispatchUncached(frame, selector, new Object[]{receiver, object1}, lookupClassNode, lookupMethodNode, dispatchNode);
        }

    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 83)
    protected abstract static class PrimPerform3Node extends AbstractPrimPerformNode implements QuaternaryPrimitiveFallback {
        @SuppressWarnings("unused")
        @Specialization(guards = {"selector == cachedSelector"}, limit = "CACHE_LIMIT")
        protected static final Object perform2Cached(final VirtualFrame frame, final Object receiver, final NativeObject selector, final Object object1, final Object object2,
                        @Cached("selector") final NativeObject cachedSelector,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached("createDispatchSendNode(cachedSelector)") final DispatchSendNode dispatchNode) {
            return dispatchCached(frame, cachedSelector, new Object[]{receiver, object1, object2}, lookupClassNode, lookupMethodNode, dispatchNode);
        }

        @SuppressWarnings("unused")
        @Specialization(replaces = "perform2Cached")
        protected static final Object perform2(final VirtualFrame frame, final Object receiver, final NativeObject selector, final Object object1, final Object object2,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached final DispatchSendSelectorNode dispatchNode) {
            return dispatchUncached(frame, selector, new Object[]{receiver, object1, object2}, lookupClassNode, lookupMethodNode, dispatchNode);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 83)
    protected abstract static class PrimPerform4Node extends AbstractPrimPerformNode implements QuinaryPrimitiveFallback {
        @SuppressWarnings("unused")
        @Specialization(guards = {"selector == cachedSelector"}, limit = "CACHE_LIMIT")
        protected static final Object perform3Cached(final VirtualFrame frame, final Object receiver, final NativeObject selector, final Object object1, final Object object2,
                        final Object object3,
                        @Cached("selector") final NativeObject cachedSelector,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached("createDispatchSendNode(cachedSelector)") final DispatchSendNode dispatchNode) {
            return dispatchCached(frame, cachedSelector, new Object[]{receiver, object1, object2, object3}, lookupClassNode, lookupMethodNode, dispatchNode);
        }

        @SuppressWarnings("unused")
        @Specialization(replaces = "perform3Cached")
        protected static final Object perform3(final VirtualFrame frame, final Object receiver, final NativeObject selector, final Object object1, final Object object2,
                        final Object object3,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached final DispatchSendSelectorNode dispatchNode) {
            return dispatchUncached(frame, selector, new Object[]{receiver, object1, object2, object3}, lookupClassNode, lookupMethodNode, dispatchNode);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 83)
    protected abstract static class PrimPerform5Node extends AbstractPrimPerformNode implements SenaryPrimitiveFallback {
        @SuppressWarnings("unused")
        @Specialization(guards = {"selector == cachedSelector"}, limit = "CACHE_LIMIT")
        protected static final Object perform4Cached(final VirtualFrame frame, final Object receiver, final NativeObject selector, final Object object1, final Object object2, final Object object3,
                        final Object object4,
                        @Cached("selector") final NativeObject cachedSelector,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached("createDispatchSendNode(cachedSelector)") final DispatchSendNode dispatchNode) {
            return dispatchCached(frame, cachedSelector, new Object[]{receiver, object1, object2, object3, object4}, lookupClassNode, lookupMethodNode, dispatchNode);
        }

        @SuppressWarnings("unused")
        @Specialization(replaces = "perform4Cached")
        protected static final Object perform4(final VirtualFrame frame, final Object receiver, final NativeObject selector, final Object object1, final Object object2, final Object object3,
                        final Object object4,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached final DispatchSendSelectorNode dispatchNode) {
            return dispatchUncached(frame, selector, new Object[]{receiver, object1, object2, object3, object4}, lookupClassNode, lookupMethodNode, dispatchNode);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 83)
    protected abstract static class PrimPerform6Node extends AbstractPrimPerformNode implements SeptenaryPrimitiveFallback {
        @SuppressWarnings("unused")
        @Specialization(guards = {"selector == cachedSelector"}, limit = "CACHE_LIMIT")
        protected static final Object perform5Cached(final VirtualFrame frame, final Object receiver, final NativeObject selector, final Object object1, final Object object2,
                        final Object object3, final Object object4, final Object object5,
                        @Cached("selector") final NativeObject cachedSelector,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached("createDispatchSendNode(cachedSelector)") final DispatchSendNode dispatchNode) {
            return dispatchCached(frame, cachedSelector, new Object[]{receiver, object1, object2, object3, object4, object5}, lookupClassNode, lookupMethodNode, dispatchNode);
        }

        @SuppressWarnings("unused")
        @Specialization(replaces = "perform5Cached")
        protected static final Object perform5(final VirtualFrame frame, final Object receiver, final NativeObject selector, final Object object1, final Object object2,
                        final Object object3, final Object object4, final Object object5,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached final DispatchSendSelectorNode dispatchNode) {
            return dispatchUncached(frame, selector, new Object[]{receiver, object1, object2, object3, object4, object5}, lookupClassNode, lookupMethodNode, dispatchNode);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 84)
    protected abstract static class PrimPerformWithArgumentsNode extends AbstractPerformPrimitiveNode implements TernaryPrimitiveFallback {
        @Child private ArrayObjectToObjectArrayWithFirstNode getObjectArrayNode = ArrayObjectToObjectArrayWithFirstNode.create();

        @Specialization(guards = "selector == cachedSelector", limit = "CACHE_LIMIT")
        protected final Object performCached(final VirtualFrame frame, final Object receiver, @SuppressWarnings("unused") final NativeObject selector, final ArrayObject arguments,
                        @Cached("selector") final NativeObject cachedSelector,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached("createDispatchSendNode(cachedSelector)") final DispatchSendNode dispatchNode) {
            final ClassObject rcvrClass = lookupClassNode.executeLookup(receiver);
            final Object lookupResult = lookupMethodNode.executeLookup(rcvrClass, cachedSelector);
            return dispatchNode.executeSend(frame, cachedSelector, lookupResult, rcvrClass, getObjectArrayNode.execute(receiver, arguments));
        }

        @Specialization(replaces = "performCached")
        protected final Object perform(final VirtualFrame frame, final Object receiver, final NativeObject selector, final ArrayObject arguments,
                        @Cached final SqueakObjectClassNode lookupClassNode,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached final DispatchSendSelectorNode dispatchNode) {
            final ClassObject rcvrClass = lookupClassNode.executeLookup(receiver);
            final Object lookupResult = lookupMethodNode.executeLookup(rcvrClass, selector);
            return dispatchNode.executeSend(frame, selector, lookupResult, rcvrClass, getObjectArrayNode.execute(receiver, arguments));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 85)
    protected abstract static class PrimSignalNode extends AbstractPrimitiveStackIncrementNode implements UnaryPrimitiveFallback {
        @Specialization(guards = "receiver.getSqueakClass().isSemaphoreClass()")
        protected final Object doSignal(final VirtualFrame frame, final PointersObject receiver,
                        @Cached final SignalSemaphoreNode signalSemaphoreNode) {
            try {
                signalSemaphoreNode.executeSignal(frame, receiver);
            } catch (final ProcessSwitch ps) {
                /*
                 * Leave receiver on stack. It has not been removed from the stack yet, so it is
                 * enough to increment the stack pointer.
                 */
                getFrameStackPointerIncrementNode().execute(frame);
                throw ps;
            }
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 86)
    protected abstract static class PrimWaitNode extends AbstractPrimitiveStackIncrementNode implements UnaryPrimitiveFallback {
        @Child private AbstractPointersObjectReadNode pointersReadNode = AbstractPointersObjectReadNode.create();

        @Specialization(guards = {"receiver.getSqueakClass().isSemaphoreClass()", "hasExcessSignals(receiver)"})
        protected final Object doWaitExcessSignals(final PointersObject receiver,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            final long excessSignals = pointersReadNode.executeLong(receiver, SEMAPHORE.EXCESS_SIGNALS);
            writeNode.execute(receiver, SEMAPHORE.EXCESS_SIGNALS, excessSignals - 1);
            return receiver;
        }

        @Specialization(guards = {"receiver.getSqueakClass().isSemaphoreClass()", "!hasExcessSignals(receiver)"})
        protected final Object doWait(final VirtualFrame frame, final PointersObject receiver,
                        @Cached final LinkProcessToListNode linkProcessToListNode,
                        @Cached final WakeHighestPriorityNode wakeHighestPriorityNode,
                        @Cached final GetActiveProcessNode getActiveProcessNode) {
            linkProcessToListNode.executeLink(getActiveProcessNode.execute(), receiver);
            try {
                wakeHighestPriorityNode.executeWake(frame);
            } catch (final ProcessSwitch ps) {
                /*
                 * Leave receiver on stack. It has not been removed from the stack yet, so it is
                 * enough to increment the stack pointer.
                 */
                getFrameStackPointerIncrementNode().execute(frame);
                throw ps;
            }
            return receiver;
        }

        protected final boolean hasExcessSignals(final PointersObject semaphore) {
            return pointersReadNode.executeLong(semaphore, SEMAPHORE.EXCESS_SIGNALS) > 0;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 87)
    protected abstract static class PrimResumeNode extends AbstractPrimitiveStackIncrementNode implements UnaryPrimitiveFallback {

        @Specialization
        protected final Object doResume(final VirtualFrame frame, final PointersObject receiver,
                        @Cached final AbstractPointersObjectReadNode readNode,
                        @Cached final ResumeProcessNode resumeProcessNode) {
            if (!(readNode.execute(receiver, PROCESS.SUSPENDED_CONTEXT) instanceof ContextObject)) {
                CompilerDirectives.transferToInterpreter();
                throw PrimitiveFailed.GENERIC_ERROR;
            }
            try {
                resumeProcessNode.executeResume(frame, receiver);
            } catch (final ProcessSwitch ps) {
                /*
                 * Leave receiver on stack. It has not been removed from the stack yet, so it is
                 * enough to increment the stack pointer.
                 */
                getFrameStackPointerIncrementNode().execute(frame);
                throw ps;
            }
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 88)
    protected abstract static class PrimSuspendNode extends AbstractPrimitiveStackPushNode implements UnaryPrimitiveFallback {

        @Specialization(guards = "receiver == getActiveProcessNode.execute()", limit = "1")
        protected final Object doSuspendActiveProcess(final VirtualFrame frame, @SuppressWarnings("unused") final PointersObject receiver,
                        @SuppressWarnings("unused") @Shared("getActiveProcessNode") @Cached final GetActiveProcessNode getActiveProcessNode,
                        @Cached final WakeHighestPriorityNode wakeHighestPriorityNode) {
            try {
                wakeHighestPriorityNode.executeWake(frame);
            } catch (final ProcessSwitch ps) {
                /* Leave `nil` as result on stack. */
                getFrameStackPushNode().execute(frame, NilObject.SINGLETON);
                throw ps;
            }
            return NilObject.SINGLETON;
        }

        @Specialization(guards = {"receiver != getActiveProcessNode.execute()"}, limit = "1")
        protected static final PointersObject doSuspendOtherProcess(final PointersObject receiver,
                        @SuppressWarnings("unused") @Shared("getActiveProcessNode") @Cached final GetActiveProcessNode getActiveProcessNode,
                        @Cached final RemoveProcessFromListNode removeProcessNode,
                        @Cached final AbstractPointersObjectReadNode readNode,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            if (readNode.execute(receiver, PROCESS.LIST) == NilObject.SINGLETON) {
                CompilerDirectives.transferToInterpreter();
                throw PrimitiveFailed.BAD_RECEIVER;
            } else {
                final PointersObject oldList = readNode.executePointers(receiver, PROCESS.LIST);
                removeProcessNode.executeRemove(receiver, oldList);
                writeNode.execute(receiver, PROCESS.LIST, NilObject.SINGLETON);
                return oldList;
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 89)
    protected abstract static class PrimFlushCacheNode extends AbstractPrimitiveNode {

        @Specialization
        protected static final Object doFlush(final Object receiver,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            image.flushMethodCache();
            return receiver;
        }
    }

    protected abstract static class AbstractPrimPerformWithArgumentsInSuperclassNode extends AbstractPerformPrimitiveNode {
        @Child private ArrayObjectToObjectArrayWithFirstNode getObjectArrayNode = ArrayObjectToObjectArrayWithFirstNode.create();
        @Child private InheritsFromNode inheritsFromNode = InheritsFromNode.create();

        protected final Object performCached(final VirtualFrame frame, final Object receiver, final ArrayObject arguments, final ClassObject superClass, final NativeObject cachedSelector,
                        final LookupMethodNode lookupMethodNode, final DispatchSendNode dispatchNode) {
            if (inheritsFromNode.execute(receiver, superClass)) {
                final Object lookupResult = lookupMethodNode.executeLookup(superClass, cachedSelector);
                return dispatchNode.executeSend(frame, cachedSelector, lookupResult, superClass, getObjectArrayNode.execute(receiver, arguments));
            } else {
                CompilerDirectives.transferToInterpreter();
                throw PrimitiveFailed.BAD_RECEIVER;
            }
        }

        protected final Object performGeneric(final VirtualFrame frame, final Object receiver, final NativeObject selector, final ArrayObject arguments, final ClassObject superClass,
                        final LookupMethodNode lookupMethodNode, final DispatchSendSelectorNode dispatchNode) {
            if (inheritsFromNode.execute(receiver, superClass)) {
                final Object lookupResult = lookupMethodNode.executeLookup(superClass, selector);
                return dispatchNode.executeSend(frame, selector, lookupResult, superClass, getObjectArrayNode.execute(receiver, arguments));
            } else {
                CompilerDirectives.transferToInterpreter();
                throw PrimitiveFailed.BAD_RECEIVER;
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 100)
    /* Object>>#perform:withArguments:inSuperclass: */
    protected abstract static class PrimPerformWithArgumentsInSuperclass4Node extends AbstractPrimPerformWithArgumentsInSuperclassNode implements QuaternaryPrimitiveFallback {
        @Specialization(guards = "selector == cachedSelector", limit = "CACHE_LIMIT")
        protected final Object performCached(final VirtualFrame frame, final Object receiver, @SuppressWarnings("unused") final NativeObject selector, final ArrayObject arguments,
                        final ClassObject superClass,
                        @Cached("selector") final NativeObject cachedSelector,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached("createDispatchSendNode(cachedSelector)") final DispatchSendNode dispatchNode) {
            return performCached(frame, receiver, arguments, superClass, cachedSelector, lookupMethodNode, dispatchNode);
        }

        @Specialization(replaces = "performCached")
        protected final Object perform(final VirtualFrame frame, final Object receiver, final NativeObject selector, final ArrayObject arguments, final ClassObject superClass,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached final DispatchSendSelectorNode dispatchNode) {
            return performGeneric(frame, receiver, selector, arguments, superClass, lookupMethodNode, dispatchNode);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 100)
    /* Context>>#object:perform:withArguments:inClass: */
    protected abstract static class PrimPerformWithArgumentsInSuperclass5Node extends AbstractPrimPerformWithArgumentsInSuperclassNode implements QuinaryPrimitiveFallback {
        @Specialization(guards = "selector == cachedSelector", limit = "CACHE_LIMIT")
        protected final Object performContextCached(final VirtualFrame frame, @SuppressWarnings("unused") final Object receiver, final Object target,
                        @SuppressWarnings("unused") final NativeObject selector, final ArrayObject arguments, final ClassObject superClass,
                        @Cached("selector") final NativeObject cachedSelector,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached("createDispatchSendNode(cachedSelector)") final DispatchSendNode dispatchNode) {
            return performCached(frame, target, arguments, superClass, cachedSelector, lookupMethodNode, dispatchNode);
        }

        @Specialization(replaces = "performContextCached")
        protected final Object performContext(final VirtualFrame frame, @SuppressWarnings("unused") final Object receiver, final Object target, final NativeObject selector,
                        final ArrayObject arguments, final ClassObject superClass,
                        @Cached final LookupMethodNode lookupMethodNode,
                        @Cached final DispatchSendSelectorNode dispatchNode) {
            return performGeneric(frame, target, selector, arguments, superClass, lookupMethodNode, dispatchNode);
        }
    }

    @GenerateNodeFactory
    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(indices = 110)
    public abstract static class PrimIdentical2Node extends AbstractPrimitiveNode {
        @Specialization
        protected static final boolean doObject(final Object a, final Object b,
                        @Cached final SqueakObjectIdentityNode identityNode) {
            return identityNode.execute(a, b);
        }
    }

    @GenerateNodeFactory
    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(indices = 110)
    protected abstract static class PrimIdentical3Node extends AbstractPrimitiveNode {
        @Specialization
        public static final boolean doObject(@SuppressWarnings("unused") final Object context, final Object a, final Object b,
                        @Cached final SqueakObjectIdentityNode identityNode) {
            return identityNode.execute(a, b);
        }
    }

    /*
     * primitiveClass (see Object>>class and Context>>objectClass:).
     */
    @NodeInfo(cost = NodeCost.NONE)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 111)
    public abstract static class PrimClass1Node extends AbstractPrimitiveNode {
        @Specialization
        protected static final ClassObject doClass(final Object receiver,
                        @Cached final SqueakObjectClassNode classNode) {
            return classNode.executeLookup(receiver);
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 111)
    protected abstract static class PrimClass2Node extends AbstractPrimitiveNode {
        @Specialization
        protected static final ClassObject doClass(@SuppressWarnings("unused") final Object receiver, final Object object,
                        @Cached final SqueakObjectClassNode classNode) {
            return classNode.executeLookup(object);
        }
    }

    @SqueakPrimitive(indices = 112)
    protected static final class PrimBytesLeftNode extends AbstractSingletonPrimitiveNode {
        private static final PrimBytesLeftNode SINGLETON = new PrimBytesLeftNode();

        @Override
        public Object execute() {
            return MiscUtils.runtimeFreeMemory();
        }

        @Override
        protected AbstractPrimitiveNode getSingleton() {
            return SINGLETON;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 113)
    protected abstract static class PrimQuit1Node extends AbstractPrimitiveNode {
        @SuppressWarnings("unused")
        @Specialization
        protected final Object doQuit(final Object receiver) {
            throw new SqueakQuit(this, 0);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 113)
    protected abstract static class PrimQuit2Node extends AbstractPrimitiveNode implements BinaryPrimitiveFallback {
        @Specialization
        protected final Object doQuit(@SuppressWarnings("unused") final Object receiver, final long exitStatus) {
            throw new SqueakQuit(this, (int) exitStatus);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 114)
    public abstract static class PrimExitToDebuggerNode extends AbstractPrimitiveNode {
        public static final String SELECTOR_NAME = "exitToDebugger";

        @Specialization
        protected static final Object doDebugger(final Object receiver) {
            return receiver;
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 115)
    protected abstract static class PrimChangeClassNode extends AbstractPrimitiveNode implements BinaryPrimitiveFallback {

        @Specialization
        protected static final AbstractSqueakObject doPrimChangeClass(final AbstractSqueakObjectWithClassAndHash receiver, final AbstractSqueakObjectWithClassAndHash argument,
                        @Cached final SqueakObjectChangeClassOfToNode changeClassOfToNode) {
            changeClassOfToNode.execute(receiver, argument.getSqueakClass());
            return receiver;
        }

    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 116)
    protected abstract static class PrimFlushCacheByMethodNode extends AbstractPrimitiveNode implements UnaryPrimitiveFallback {
        @Child protected AbstractPointersObjectReadNode readNode = AbstractPointersObjectReadNode.create();

        @Specialization(guards = "receiver.hasMethodClass(readNode)")
        protected final CompiledCodeObject doFlush(final CompiledCodeObject receiver,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            image.flushMethodCacheForMethod(receiver);
            /*
             * TODO: maybe the method's callTarget could be invalidated to remove it from any PIC
             * and to avoid invalidating the entire methodDict assumption.
             */
            receiver.getMethodClass(readNode).invalidateMethodDictStableAssumption();
            return receiver;
        }
    }

    /** primitiveExternalCall (#117) handled specially in {@link PrimitiveNodeFactory}. */

    protected abstract static class AbstractPrimDoPrimitiveWithArgsNode extends AbstractPrimitiveNode {
        protected static final AbstractPrimitiveNode createPrimitiveNode(final long primitiveIndex, final int arraySize) {
            return PrimitiveNodeFactory.forIndex((int) primitiveIndex, arraySize, true);
        }

        protected static final Object primitiveWithArgs(final VirtualFrame frame, final Object receiver, final ArrayObject argumentArray,
                        final AbstractPrimitiveNode primitiveNode, final ArrayObjectToObjectArrayCopyNode toObjectArrayNode) {
            return primitiveNode.executeWithReceiverAndArguments(frame, receiver, toObjectArrayNode.execute(argumentArray));
        }

        protected final Object primitiveWithArgs(final VirtualFrame frame, final Object receiver, final long primitiveIndex, final ArrayObject argumentArray) {
            /* Deopt might be acceptable because primitive is mostly used for debugging anyway. */
            CompilerDirectives.transferToInterpreter();
            final int arraySize = ArrayObjectSizeNode.getUncached().execute(argumentArray);
            final AbstractPrimitiveNode primitiveNode = insert(createPrimitiveNode(primitiveIndex, arraySize));
            if (primitiveNode == null) {
                throw PrimitiveFailed.GENERIC_ERROR;
            } else {
                final Object[] arguments = ArrayObjectToObjectArrayCopyNode.getUncached().execute(argumentArray);
                return primitiveNode.executeWithReceiverAndArguments(frame, receiver, arguments);
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 118)
    protected abstract static class PrimDoPrimitiveWithArgs3Node extends AbstractPrimDoPrimitiveWithArgsNode implements TernaryPrimitiveFallback {
        @Specialization(guards = {"primitiveIndex == cachedPrimitiveIndex", "primitiveNode != null", "sizeNode.execute(argumentArray) == cachedArraySize"}, limit = "2")
        protected static final Object doPrimitiveWithArgsCached(final VirtualFrame frame, final Object receiver, @SuppressWarnings("unused") final long primitiveIndex, final ArrayObject argumentArray,
                        @SuppressWarnings("unused") @Cached("primitiveIndex") final long cachedPrimitiveIndex,
                        @SuppressWarnings("unused") @Cached final ArrayObjectSizeNode sizeNode,
                        @SuppressWarnings("unused") @Cached("sizeNode.execute(argumentArray)") final int cachedArraySize,
                        @Cached("createPrimitiveNode(cachedPrimitiveIndex, cachedArraySize)") final AbstractPrimitiveNode primitiveNode,
                        @Cached final ArrayObjectToObjectArrayCopyNode toObjectArrayNode) {
            return primitiveWithArgs(frame, receiver, argumentArray, primitiveNode, toObjectArrayNode);
        }

        @Specialization(replaces = "doPrimitiveWithArgsCached")
        protected final Object doPrimitiveWithArgs(final VirtualFrame frame, final Object receiver, final long primitiveIndex, final ArrayObject argumentArray) {
            return primitiveWithArgs(frame, receiver, primitiveIndex, argumentArray);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 118)
    protected abstract static class PrimDoPrimitiveWithArgs4Node extends AbstractPrimDoPrimitiveWithArgsNode implements QuaternaryPrimitiveFallback {
        @Specialization(guards = {"primitiveIndex == cachedPrimitiveIndex", "primitiveNode != null", "sizeNode.execute(argumentArray) == cachedArraySize"}, limit = "2")
        protected static final Object doPrimitiveWithArgsContextCached(final VirtualFrame frame, @SuppressWarnings("unused") final Object context, final Object receiver,
                        @SuppressWarnings("unused") final long primitiveIndex, final ArrayObject argumentArray,
                        @SuppressWarnings("unused") @Cached("primitiveIndex") final long cachedPrimitiveIndex,
                        @SuppressWarnings("unused") @Cached final ArrayObjectSizeNode sizeNode,
                        @SuppressWarnings("unused") @Cached("sizeNode.execute(argumentArray)") final int cachedArraySize,
                        @Cached("createPrimitiveNode(cachedPrimitiveIndex, cachedArraySize)") final AbstractPrimitiveNode primitiveNode,
                        @Cached final ArrayObjectToObjectArrayCopyNode toObjectArrayNode) {
            return primitiveWithArgs(frame, receiver, argumentArray, primitiveNode, toObjectArrayNode);
        }

        @Specialization(replaces = "doPrimitiveWithArgsContextCached")
        protected final Object doPrimitiveWithArgsContext(final VirtualFrame frame, @SuppressWarnings("unused") final Object context, final Object receiver,
                        final long primitiveIndex, final ArrayObject argumentArray) {
            return primitiveWithArgs(frame, receiver, primitiveIndex, argumentArray);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 119)
    protected abstract static class PrimFlushCacheSelectiveNode extends AbstractPrimitiveNode {

        @Specialization
        protected static final NativeObject doFlush(final NativeObject receiver,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            image.flushMethodCacheForSelector(receiver);
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 130)
    protected abstract static class PrimFullGCNode extends AbstractPrimitiveNode {
        private static final MBeanServer SERVER = TruffleOptions.AOT ? null : ManagementFactory.getPlatformMBeanServer();
        private static final String OPERATION_NAME = "gcRun";
        private static final Object[] PARAMS = new Object[]{null};
        private static final String[] SIGNATURE = new String[]{String[].class.getName()};
        private static final ObjectName OBJECT_NAME;

        static {
            if (TruffleOptions.AOT) {
                OBJECT_NAME = null;
            } else {
                try {
                    OBJECT_NAME = new ObjectName("com.sun.management:type=DiagnosticCommand");
                } catch (final MalformedObjectNameException e) {
                    throw SqueakException.create(e);
                }
            }
        }

        @Specialization
        protected static final long doGC(@SuppressWarnings("unused") final Object receiver,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            if (TruffleOptions.AOT) {
                /* System.gc() triggers full GC by default in SVM (see https://git.io/JvY7g). */
                MiscUtils.systemGC();
            } else {
                forceFullGC();
            }
            final boolean hasPendingFinalizations = LogUtils.GC_IS_LOGGABLE_FINE ? hasPendingFinalizationsWithLogging(image) : hasPendingFinalizations(image);
            if (hasPendingFinalizations) {
                image.interrupt.setPendingFinalizations(true);
            }
            return MiscUtils.runtimeFreeMemory();
        }

        /**
         * {@link System#gc()} does not force a GC, but the DiagnosticCommand "gcRun" does.
         */
        @TruffleBoundary
        private static void forceFullGC() {
            try {
                SERVER.invoke(OBJECT_NAME, OPERATION_NAME, PARAMS, SIGNATURE);
            } catch (InstanceNotFoundException | ReflectionException | MBeanException e) {
                e.printStackTrace();
            }
        }

        @TruffleBoundary
        private static boolean hasPendingFinalizations(final SqueakImageContext image) {
            return image.weakPointersQueue.poll() != null;
        }

        @TruffleBoundary
        private static boolean hasPendingFinalizationsWithLogging(final SqueakImageContext image) {
            final ReferenceQueue<Object> queue = image.weakPointersQueue;
            Reference<?> element = queue.poll();
            int count = 0;
            while (element != null) {
                count++;
                element = queue.poll();
            }
            LogUtils.GC.log(Level.FINE, "Number of garbage collected WeakPointersObjects: {0}", count);
            return count > 0;
        }
    }

    @SqueakPrimitive(indices = 131)
    protected static final class PrimIncrementalGCNode extends AbstractSingletonPrimitiveNode {
        private static final PrimIncrementalGCNode SINGLETON = new PrimIncrementalGCNode();

        @Override
        public Object execute() {
            /* Cannot force incremental GC in Java, suggesting a normal GC instead. */
            MiscUtils.systemGC();
            return MiscUtils.runtimeFreeMemory();
        }

        @Override
        protected AbstractPrimitiveNode getSingleton() {
            return SINGLETON;
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 160)
    protected abstract static class PrimAdoptInstanceNode extends AbstractPrimitiveNode implements BinaryPrimitiveFallback {

        @Specialization
        protected static final ClassObject doPrimAdoptInstance(final ClassObject receiver, final AbstractSqueakObjectWithClassAndHash argument,
                        @Cached final SqueakObjectChangeClassOfToNode changeClassOfToNode) {
            changeClassOfToNode.execute(argument, receiver);
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 167)
    protected abstract static class PrimYieldNode extends AbstractPrimitiveStackIncrementNode implements UnaryPrimitiveFallback {
        @Child private LinkProcessToListNode linkProcessToListNode;
        @Child private WakeHighestPriorityNode wakeHighestPriorityNode;

        @Specialization
        protected final Object doYield(final VirtualFrame frame, final PointersObject scheduler,
                        @Cached final ArrayObjectReadNode arrayReadNode,
                        @Cached final GetActiveProcessNode getActiveProcessNode,
                        @Cached final AbstractPointersObjectReadNode pointersReadNode) {
            final PointersObject activeProcess = getActiveProcessNode.execute();
            final long priority = pointersReadNode.executeLong(activeProcess, PROCESS.PRIORITY);
            final ArrayObject processLists = pointersReadNode.executeArray(scheduler, PROCESS_SCHEDULER.PROCESS_LISTS);
            final PointersObject processList = (PointersObject) arrayReadNode.execute(processLists, priority - 1);
            if (!processList.isEmptyList(pointersReadNode)) {
                getLinkProcessToListNode().executeLink(activeProcess, processList);
                try {
                    getWakeHighestPriorityNode().executeWake(frame);
                } catch (final ProcessSwitch ps) {
                    /*
                     * Leave receiver on stack. It has not been removed from the stack yet, so it is
                     * enough to increment the stack pointer.
                     */
                    getFrameStackPointerIncrementNode().execute(frame);
                    throw ps;
                }
            }
            return scheduler;
        }

        private LinkProcessToListNode getLinkProcessToListNode() {
            if (linkProcessToListNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                linkProcessToListNode = insert(LinkProcessToListNode.create());
                wakeHighestPriorityNode = insert(WakeHighestPriorityNode.create());
            }
            return linkProcessToListNode;
        }

        private WakeHighestPriorityNode getWakeHighestPriorityNode() {
            if (wakeHighestPriorityNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            return wakeHighestPriorityNode;
        }
    }

    @GenerateNodeFactory
    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(indices = 169)
    public abstract static class PrimNotIdenticalNode extends AbstractPrimitiveNode {
        @Specialization
        public static final boolean doObject(final Object a, final Object b,
                        @Cached final SqueakObjectIdentityNode identityNode) {
            return !identityNode.execute(a, b);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 185)
    protected abstract static class PrimExitCriticalSectionNode extends AbstractPrimitiveStackIncrementNode implements UnaryPrimitiveFallback {
        @Child protected AbstractPointersObjectReadNode readNode = AbstractPointersObjectReadNode.create();
        @Child private AbstractPointersObjectWriteNode writeNode = AbstractPointersObjectWriteNode.create();

        @Specialization(guards = "mutex.isEmptyList(readNode)")
        protected final PointersObject doExitEmpty(final PointersObject mutex) {
            writeNode.executeNil(mutex, MUTEX.OWNER);
            return mutex;
        }

        @Specialization(guards = "!mutex.isEmptyList(readNode)")
        protected final Object doExitNonEmpty(final VirtualFrame frame, final PointersObject mutex,
                        @Cached("create()") final ResumeProcessNode resumeProcessNode) {
            final PointersObject owningProcess = mutex.removeFirstLinkOfList(readNode, writeNode);
            writeNode.execute(mutex, MUTEX.OWNER, owningProcess);
            try {
                resumeProcessNode.executeResume(frame, owningProcess);
            } catch (final ProcessSwitch ps) {
                /*
                 * Leave receiver on stack. It has not been removed from the stack yet, so it is
                 * enough to increment the stack pointer.
                 */
                getFrameStackPointerIncrementNode().execute(frame);
                throw ps;
            }
            return mutex;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 186)
    protected abstract static class AbstractPrimEnterCriticalSectionNode extends AbstractPrimitiveStackPushNode {
        @Child private AbstractPointersObjectReadNode readNode = AbstractPointersObjectReadNode.create();

        protected final boolean ownerIsNil(final PointersObject mutex) {
            return readNode.execute(mutex, MUTEX.OWNER) == NilObject.SINGLETON;
        }

        protected final boolean activeProcessMutexOwner(final PointersObject mutex, final GetActiveProcessNode getActiveProcessNode) {
            return readNode.execute(mutex, MUTEX.OWNER) == getActiveProcessNode.execute();
        }

        protected final boolean isMutexOwner(final PointersObject mutex, final PointersObject effectiveProcess) {
            return readNode.execute(mutex, MUTEX.OWNER) == effectiveProcess;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 186)
    protected abstract static class PrimEnterCriticalSection1Node extends AbstractPrimEnterCriticalSectionNode implements UnaryPrimitiveFallback {
        @Specialization(guards = "ownerIsNil(mutex)")
        protected static final boolean doEnterNilOwner(final PointersObject mutex,
                        @Cached final AbstractPointersObjectWriteNode writeNode,
                        @Shared("getActiveProcessNode") @Cached final GetActiveProcessNode getActiveProcessNode) {
            writeNode.execute(mutex, MUTEX.OWNER, getActiveProcessNode.execute());
            return BooleanObject.FALSE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "activeProcessMutexOwner(mutex, getActiveProcessNode)", limit = "1")
        protected static final boolean doEnterActiveProcessOwner(final PointersObject mutex,
                        @Shared("getActiveProcessNode") @Cached final GetActiveProcessNode getActiveProcessNode) {
            return BooleanObject.TRUE;
        }

        @Specialization(guards = {"!ownerIsNil(mutex)", "!activeProcessMutexOwner(mutex, getActiveProcessNode)"}, limit = "1")
        protected final Object doEnter(final VirtualFrame frame, final PointersObject mutex,
                        @Cached final LinkProcessToListNode linkProcessToListNode,
                        @Cached final WakeHighestPriorityNode wakeHighestPriorityNode,
                        @Shared("getActiveProcessNode") @Cached final GetActiveProcessNode getActiveProcessNode) {
            linkProcessToListNode.executeLink(getActiveProcessNode.execute(), mutex);
            try {
                wakeHighestPriorityNode.executeWake(frame);
            } catch (final ProcessSwitch ps) {
                /* Leave `false` as result on stack. */
                getFrameStackPushNode().execute(frame, BooleanObject.FALSE);
                throw ps;
            }
            return BooleanObject.FALSE;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 186)
    protected abstract static class PrimEnterCriticalSection2Node extends AbstractPrimEnterCriticalSectionNode implements BinaryPrimitiveFallback {
        @Specialization(guards = "ownerIsNil(mutex)")
        protected static final boolean doEnterNilOwner(final PointersObject mutex, @SuppressWarnings("unused") final PointersObject effectiveProcess,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            writeNode.execute(mutex, MUTEX.OWNER, effectiveProcess);
            return BooleanObject.FALSE;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isMutexOwner(mutex, effectiveProcess)")
        protected static final boolean doEnterActiveProcessOwner(final PointersObject mutex, final PointersObject effectiveProcess) {
            return BooleanObject.TRUE;
        }

        @Specialization(guards = {"!ownerIsNil(mutex)", "!isMutexOwner(mutex, effectiveProcess)"})
        protected final Object doEnter(final VirtualFrame frame, final PointersObject mutex, @SuppressWarnings("unused") final PointersObject effectiveProcess,
                        @Cached final LinkProcessToListNode linkProcessToListNode,
                        @Cached final WakeHighestPriorityNode wakeHighestPriorityNode) {
            linkProcessToListNode.executeLink(effectiveProcess, mutex);
            try {
                wakeHighestPriorityNode.executeWake(frame);
            } catch (final ProcessSwitch ps) {
                /* Leave `false` as result on stack. */
                getFrameStackPushNode().execute(frame, BooleanObject.FALSE);
                throw ps;
            }
            return BooleanObject.FALSE;
        }
    }

    @ImportStatic(MUTEX.class)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 187)
    protected abstract static class PrimTestAndSetOwnershipOfCriticalSectionNode extends AbstractPrimitiveNode implements UnaryPrimitiveFallback {
        @Child private GetActiveProcessNode getActiveProcessNode = GetActiveProcessNode.create();
        @Child private AbstractPointersObjectReadNode readNode = AbstractPointersObjectReadNode.create();

        @Specialization(guards = {"ownerIsNil(rcvrMutex)"})
        protected final boolean doNilOwner(final PointersObject rcvrMutex,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            writeNode.execute(rcvrMutex, MUTEX.OWNER, getActiveProcessNode.execute());
            return BooleanObject.FALSE;
        }

        @Specialization(guards = {"ownerIsActiveProcess(rcvrMutex)"})
        protected static final boolean doOwnerIsActiveProcess(@SuppressWarnings("unused") final PointersObject rcvrMutex) {
            return BooleanObject.TRUE;
        }

        @Specialization(guards = {"!ownerIsNil(rcvrMutex)", "!ownerIsActiveProcess(rcvrMutex)"})
        protected static final Object doFallback(@SuppressWarnings("unused") final PointersObject rcvrMutex) {
            return NilObject.SINGLETON;
        }

        protected final boolean ownerIsNil(final PointersObject mutex) {
            return readNode.execute(mutex, MUTEX.OWNER) == NilObject.SINGLETON;
        }

        protected final boolean ownerIsActiveProcess(final PointersObject mutex) {
            return readNode.execute(mutex, MUTEX.OWNER) == getActiveProcessNode.execute();
        }
    }

    protected abstract static class AbstractPrimExecuteMethodArgsArrayNode extends AbstractPrimitiveNode {
        @Child private ArrayObjectSizeNode sizeNode = ArrayObjectSizeNode.create();
        @Child protected ArrayObjectReadNode readNode = ArrayObjectReadNode.create();

        protected final Object doExecuteMethod(final VirtualFrame frame, final Object receiver, final ArrayObject argArray, final CompiledCodeObject methodObject,
                        final DispatchEagerlyNode dispatchNode) {
            final int numArgs = sizeNode.execute(argArray);
            final Object[] dispatchRcvrAndArgs = new Object[1 + numArgs];
            dispatchRcvrAndArgs[0] = receiver;
            for (int i = 0; i < numArgs; i++) {
                dispatchRcvrAndArgs[1 + i] = readNode.execute(argArray, i);
            }
            return dispatchNode.executeDispatch(frame, methodObject, dispatchRcvrAndArgs);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 188)
    protected abstract static class PrimExecuteMethodArgsArray3Node extends AbstractPrimExecuteMethodArgsArrayNode implements TernaryPrimitiveFallback {
        /** Deprecated since Kernel-eem.1204. Kept for backward compatibility. */
        @Specialization
        protected final Object doExecute(final VirtualFrame frame, final Object receiver, final ArrayObject argArray, final CompiledCodeObject methodObject,
                        @Cached final DispatchEagerlyNode dispatchNode) {
            return doExecuteMethod(frame, receiver, argArray, methodObject, dispatchNode);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 188)
    protected abstract static class PrimExecuteMethodArgsArray4Node extends AbstractPrimExecuteMethodArgsArrayNode implements QuaternaryPrimitiveFallback {
        @Specialization
        protected final Object doExecute(final VirtualFrame frame, @SuppressWarnings("unused") final ClassObject compiledMethodClass, final Object receiver, final ArrayObject argArray,
                        final CompiledCodeObject methodObject,
                        @Cached final DispatchEagerlyNode dispatchNode) {
            return doExecuteMethod(frame, receiver, argArray, methodObject, dispatchNode);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 189)
    protected abstract static class PrimExecuteMethod2Node extends AbstractPrimitiveNode implements BinaryPrimitiveFallback {
        @Specialization
        protected static final Object doExecute(final VirtualFrame frame, final Object receiver, final CompiledCodeObject methodObject,
                        @Cached final DispatchEagerlyNode dispatchNode) {
            return dispatchNode.executeDispatch(frame, methodObject, new Object[]{receiver});
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 189)
    protected abstract static class PrimExecuteMethod3Node extends AbstractPrimitiveNode implements TernaryPrimitiveFallback {
        @Specialization
        protected static final Object doExecute(final VirtualFrame frame, final Object receiver, final Object arg1, final CompiledCodeObject methodObject,
                        @Cached final DispatchEagerlyNode dispatchNode) {
            return dispatchNode.executeDispatch(frame, methodObject, new Object[]{receiver, arg1});
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 189)
    protected abstract static class PrimExecuteMethod4Node extends AbstractPrimitiveNode implements QuaternaryPrimitiveFallback {
        @Specialization
        protected static final Object doExecute(final VirtualFrame frame, final Object receiver, final Object arg1, final Object arg2, final CompiledCodeObject methodObject,
                        @Cached final DispatchEagerlyNode dispatchNode) {
            return dispatchNode.executeDispatch(frame, methodObject, new Object[]{receiver, arg1, arg2});
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 189)
    protected abstract static class PrimExecuteMethod5Node extends AbstractPrimitiveNode implements QuinaryPrimitiveFallback {
        @Specialization
        protected static final Object doExecute(final VirtualFrame frame, final Object receiver, final Object arg1, final Object arg2, final Object arg3, final CompiledCodeObject methodObject,
                        @Cached final DispatchEagerlyNode dispatchNode) {
            return dispatchNode.executeDispatch(frame, methodObject, new Object[]{receiver, arg1, arg2, arg3});
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 189)
    protected abstract static class PrimExecuteMethod6Node extends AbstractPrimitiveNode implements SenaryPrimitiveFallback {
        @Specialization
        protected static final Object doExecute(final VirtualFrame frame, final Object receiver, final Object arg1, final Object arg2, final Object arg3, final Object arg4,
                        final CompiledCodeObject methodObject,
                        @Cached final DispatchEagerlyNode dispatchNode) {
            return dispatchNode.executeDispatch(frame, methodObject, new Object[]{receiver, arg1, arg2, arg3, arg4});
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 218)
    protected abstract static class PrimDoNamedPrimitiveWithArgsNode extends AbstractPrimitiveNode implements QuaternaryPrimitiveFallback {

        @Specialization(guards = {"methodObject == cachedMethodObject", "primitiveNode != null", "sizeNode.execute(argumentArray) == cachedArraySize",
                        "cachedArraySize == cachedMethodObject.getNumArgs()"}, limit = "2")
        protected static final Object doNamedPrimitiveWithArgsContextCached(final VirtualFrame frame, @SuppressWarnings("unused") final Object context,
                        @SuppressWarnings("unused") final CompiledCodeObject methodObject, final Object target, final ArrayObject argumentArray,
                        @SuppressWarnings("unused") @Cached("methodObject") final CompiledCodeObject cachedMethodObject,
                        @SuppressWarnings("unused") @Cached final ArrayObjectSizeNode sizeNode,
                        @SuppressWarnings("unused") @Cached("sizeNode.execute(argumentArray)") final int cachedArraySize,
                        @Cached("createPrimitiveNode(methodObject)") final AbstractPrimitiveNode primitiveNode,
                        @Cached final ArrayObjectToObjectArrayCopyNode toObjectArrayNode) {
            return primitiveNode.executeWithReceiverAndArguments(frame, target, toObjectArrayNode.execute(argumentArray));
        }

        @Specialization(replaces = "doNamedPrimitiveWithArgsContextCached")
        protected final Object doNamedPrimitiveWithArgsContext(final VirtualFrame frame, @SuppressWarnings("unused") final Object context, final CompiledCodeObject methodObject,
                        final Object target, final ArrayObject argumentArray) {
            /* Deopt might be acceptable because primitive is mostly used for debugging anyway. */
            CompilerDirectives.transferToInterpreter();
            final int arraySize = ArrayObjectSizeNode.getUncached().execute(argumentArray);
            assert arraySize == methodObject.getNumArgs();
            final AbstractPrimitiveNode primitiveNode = insert(createPrimitiveNode(methodObject));
            if (primitiveNode == null) {
                throw PrimitiveFailed.GENERIC_ERROR;
            } else {
                final Object[] arguments = ArrayObjectToObjectArrayCopyNode.getUncached().execute(argumentArray);
                return primitiveNode.executeWithReceiverAndArguments(frame, target, arguments);
            }
        }

        protected static final AbstractPrimitiveNode createPrimitiveNode(final CompiledCodeObject methodObject) {
            return PrimitiveNodeFactory.namedFor(methodObject, false, true);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 230)
    protected abstract static class PrimRelinquishProcessorNode extends AbstractPrimitiveStackIncrementNode implements BinaryPrimitiveFallback {
        @Specialization
        protected final Object doRelinquish(final VirtualFrame frame, final Object receiver, final long timeMicroseconds,
                        @Cached("createOrNull(true)") final InterruptHandlerNode interruptNode) {
            MiscUtils.sleep(timeMicroseconds / 1000);
            /*
             * Perform interrupt check (even if interrupt handler is not active), otherwise
             * idleProcess gets stuck. Checking whether the interrupt handler `shouldTrigger()`
             * decreases performance for some reason, forcing interrupt check instead.
             */
            if (interruptNode != null) {
                try {
                    interruptNode.executeTrigger(frame);
                } catch (final ProcessSwitch ps) {
                    /*
                     * Leave receiver on stack. It has not been removed from the stack yet, so it is
                     * enough to increment the stack pointer.
                     */
                    getFrameStackPointerIncrementNode().execute(frame);
                    throw ps;
                }
            }
            return receiver;
        }
    }

    @GenerateNodeFactory
    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(indices = 231)
    protected abstract static class PrimForceDisplayUpdateNode extends AbstractPrimitiveNode {
        @Specialization
        protected static final Object doForceUpdate(final Object receiver) {
            return receiver; // Do nothing.
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 233)
    protected abstract static class PrimSetFullScreenNode extends AbstractPrimitiveNode implements BinaryPrimitiveFallback {
        @Specialization
        protected static final Object doFullScreen(final Object receiver, final boolean enable,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            if (image.hasDisplay()) {
                image.getDisplay().setFullscreen(enable);
            }
            return receiver;
        }
    }

    @GenerateNodeFactory
    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(indices = 256)
    protected abstract static class PrimQuickReturnSelfNode extends AbstractPrimitiveNode {
        @Specialization
        protected static final Object returnValue(final Object receiver) {
            return receiver;
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(indices = 257)
    protected static final class PrimQuickReturnTrueNode extends AbstractSingletonPrimitiveNode {
        private static final PrimQuickReturnTrueNode SINGLETON = new PrimQuickReturnTrueNode();

        @Override
        public Object execute() {
            return BooleanObject.TRUE;
        }

        @Override
        protected AbstractPrimitiveNode getSingleton() {
            return SINGLETON;
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(indices = 258)
    protected static final class PrimQuickReturnFalseNode extends AbstractSingletonPrimitiveNode {
        private static final PrimQuickReturnFalseNode SINGLETON = new PrimQuickReturnFalseNode();

        @Override
        public Object execute() {
            return BooleanObject.FALSE;
        }

        @Override
        protected AbstractPrimitiveNode getSingleton() {
            return SINGLETON;
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(indices = 259)
    protected static final class PrimQuickReturnNilNode extends AbstractSingletonPrimitiveNode {
        private static final PrimQuickReturnNilNode SINGLETON = new PrimQuickReturnNilNode();

        @Override
        public Object execute() {
            return NilObject.SINGLETON;
        }

        @Override
        protected AbstractPrimitiveNode getSingleton() {
            return SINGLETON;
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(indices = 260)
    protected static final class PrimQuickReturnMinusOneNode extends AbstractSingletonPrimitiveNode {
        private static final PrimQuickReturnMinusOneNode SINGLETON = new PrimQuickReturnMinusOneNode();

        @Override
        public Object execute() {
            return -1L;
        }

        @Override
        protected AbstractPrimitiveNode getSingleton() {
            return SINGLETON;
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(indices = 261)
    protected static final class PrimQuickReturnZeroNode extends AbstractSingletonPrimitiveNode {
        private static final PrimQuickReturnZeroNode SINGLETON = new PrimQuickReturnZeroNode();

        @Override
        public Object execute() {
            return 0L;
        }

        @Override
        protected AbstractPrimitiveNode getSingleton() {
            return SINGLETON;
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(indices = 262)
    protected static final class PrimQuickReturnOneNode extends AbstractSingletonPrimitiveNode {
        private static final PrimQuickReturnOneNode SINGLETON = new PrimQuickReturnOneNode();

        @Override
        public Object execute() {
            return 1L;
        }

        @Override
        protected AbstractPrimitiveNode getSingleton() {
            return SINGLETON;
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(indices = 263)
    protected static final class PrimQuickReturnTwoNode extends AbstractSingletonPrimitiveNode {
        private static final PrimQuickReturnTwoNode SINGLETON = new PrimQuickReturnTwoNode();

        @Override
        public Object execute() {
            return 2L;
        }

        @Override
        protected AbstractPrimitiveNode getSingleton() {
            return SINGLETON;
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    public abstract static class PrimLoadInstVarNode extends AbstractPrimitiveNode {
        @Child private SqueakObjectAt0Node at0Node = SqueakObjectAt0Node.create();
        private final long variableIndex;

        protected PrimLoadInstVarNode(final long variableIndex) {
            this.variableIndex = variableIndex;
        }

        public static PrimLoadInstVarNode create(final long variableIndex, final AbstractArgumentNode[] arguments) {
            return PrimLoadInstVarNodeGen.create(variableIndex, arguments);
        }

        @Specialization
        protected final Object doReceiverVariable(final Object receiver) {
            return at0Node.execute(receiver, variableIndex);
        }
    }

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return ControlPrimitivesFactory.getFactories();
    }

    @Override
    public List<? extends AbstractPrimitiveNode> getSingletonPrimitives() {
        return Arrays.asList(
                        PrimQuickReturnTrueNode.SINGLETON,
                        PrimQuickReturnFalseNode.SINGLETON,
                        PrimQuickReturnNilNode.SINGLETON,
                        PrimQuickReturnMinusOneNode.SINGLETON,
                        PrimQuickReturnZeroNode.SINGLETON,
                        PrimQuickReturnOneNode.SINGLETON,
                        PrimQuickReturnTwoNode.SINGLETON,
                        PrimBytesLeftNode.SINGLETON,
                        PrimIncrementalGCNode.SINGLETON);
    }
}
