/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.exceptions.SqueakExceptions.SqueakError;
import de.hpi.swa.trufflesqueak.exceptions.SqueakExceptions.SqueakSyntaxError;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.ClassObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.CompiledMethodObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.nodes.DispatchSendNodeFactory.DispatchSendSelectorNodeGen;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.SqueakObjectClassNode;
import de.hpi.swa.trufflesqueak.util.ArrayUtils;
import de.hpi.swa.trufflesqueak.util.MiscUtils;

@NodeInfo(cost = NodeCost.NONE)
public abstract class DispatchSendNode extends AbstractNode {

    public static DispatchSendNode create(final NativeObject selector, final CompiledCodeObject code) {
        if (code.image.isHeadless()) {
            if (selector.isDebugErrorSelector()) {
                return new DispatchSendHeadlessErrorNode();
            } else if (selector.isDebugSyntaxErrorSelector()) {
                return new DispatchSendSyntaxErrorNode();
            }
        }
        return DispatchSendSelectorNodeGen.create();
    }

    public abstract Object executeSend(VirtualFrame frame, NativeObject selector, Object lookupResult, ClassObject rcvrClass, Object[] receiverAndArguments);

    public abstract static class DispatchSendSelectorNode extends DispatchSendNode {
        @Child protected DispatchEagerlyNode dispatchNode = DispatchEagerlyNode.create();

        public static DispatchSendSelectorNode create() {
            return DispatchSendSelectorNodeGen.create();
        }

        @Specialization(guards = {"lookupResult != null"})
        protected final Object doDispatch(final VirtualFrame frame, @SuppressWarnings("unused") final NativeObject selector, final CompiledMethodObject lookupResult,
                        @SuppressWarnings("unused") final ClassObject rcvrClass, final Object[] rcvrAndArgs) {
            return dispatchNode.executeDispatch(frame, lookupResult, rcvrAndArgs);
        }

        @Specialization(guards = {"lookupResult == null"})
        protected final Object doDoesNotUnderstand(final VirtualFrame frame, final NativeObject selector, @SuppressWarnings("unused") final Object lookupResult, final ClassObject rcvrClass,
                        final Object[] rcvrAndArgs,
                        @Shared("writeNode") @Cached final AbstractPointersObjectWriteNode writeNode,
                        @Cached final LookupMethodNode lookupNode,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            final CompiledMethodObject doesNotUnderstandMethod = (CompiledMethodObject) lookupNode.executeLookup(rcvrClass, image.doesNotUnderstand);
            final PointersObject message = image.newMessage(writeNode, selector, rcvrClass, ArrayUtils.allButFirst(rcvrAndArgs));
            return dispatchNode.executeDispatch(frame, doesNotUnderstandMethod, new Object[]{rcvrAndArgs[0], message});
        }

        @Specialization(guards = {"!isCompiledMethodObject(targetObject)"})
        protected final Object doObjectAsMethod(final VirtualFrame frame, final NativeObject selector, final Object targetObject, @SuppressWarnings("unused") final ClassObject rcvrClass,
                        final Object[] rcvrAndArgs,
                        @Cached final SqueakObjectClassNode classNode,
                        @Shared("writeNode") @Cached final AbstractPointersObjectWriteNode writeNode,
                        @Cached final LookupMethodNode lookupNode,
                        @Cached("createBinaryProfile()") final ConditionProfile isDoesNotUnderstandProfile,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            final Object[] arguments = ArrayUtils.allButFirst(rcvrAndArgs);
            final ClassObject targetClass = classNode.executeLookup(targetObject);
            final Object newLookupResult = lookupNode.executeLookup(targetClass, image.runWithInSelector);
            if (isDoesNotUnderstandProfile.profile(newLookupResult == null)) {
                final Object doesNotUnderstandMethod = lookupNode.executeLookup(targetClass, image.doesNotUnderstand);
                return dispatchNode.executeDispatch(frame, (CompiledMethodObject) doesNotUnderstandMethod,
                                new Object[]{targetObject, image.newMessage(writeNode, selector, targetClass, arguments)});
            } else {
                return dispatchNode.executeDispatch(frame, (CompiledMethodObject) newLookupResult, new Object[]{targetObject, selector, image.asArrayOfObjects(arguments), rcvrAndArgs[0]});
            }
        }
    }

    private static final class DispatchSendHeadlessErrorNode extends DispatchSendNode {
        @Override
        public Object executeSend(final VirtualFrame frame, final NativeObject selector, final Object lookupResult, final ClassObject rcvrClass, final Object[] receiverAndArguments) {
            CompilerDirectives.transferToInterpreter();
            throw new SqueakError(this, MiscUtils.format("%s>>#%s detected in headless mode. Aborting...", rcvrClass.getSqueakClassName(), selector.asStringUnsafe()));
        }
    }

    private static final class DispatchSendSyntaxErrorNode extends DispatchSendNode {
        @Override
        public Object executeSend(final VirtualFrame frame, final NativeObject selector, final Object lookupResult, final ClassObject rcvrClass, final Object[] receiverAndArguments) {
            CompilerDirectives.transferToInterpreter();
            throw new SqueakSyntaxError((PointersObject) receiverAndArguments[1]);
        }
    }
}
