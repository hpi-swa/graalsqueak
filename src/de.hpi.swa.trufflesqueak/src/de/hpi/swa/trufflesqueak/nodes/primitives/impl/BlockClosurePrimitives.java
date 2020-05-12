/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.primitives.impl;

import java.util.List;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.BlockClosureObject;
import de.hpi.swa.trufflesqueak.model.CompiledBlockObject;
import de.hpi.swa.trufflesqueak.nodes.accessing.ArrayObjectNodes.ArrayObjectCopyIntoObjectArrayNode;
import de.hpi.swa.trufflesqueak.nodes.context.frame.GetContextOrMarkerNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.SqueakObjectSizeNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.QuaternaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.SenaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.trufflesqueak.util.FrameAccess;
import de.hpi.swa.trufflesqueak.util.NotProvided;

public final class BlockClosurePrimitives extends AbstractPrimitiveFactoryHolder {
    protected static final int INLINE_CACHE_SIZE = 3;

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 201)
    @ImportStatic(BlockClosurePrimitives.class)
    public abstract static class PrimClosureValue0Node extends AbstractPrimitiveNode implements UnaryPrimitive {
        @SuppressWarnings("unused")
        @Specialization(guards = {"closure.getCompiledBlock() == cachedBlock", "cachedBlock.getNumArgs() == 0"}, assumptions = {"cachedBlock.getCallTargetStable()"}, limit = "INLINE_CACHE_SIZE")
        protected static final Object doValueDirect(final VirtualFrame frame, final BlockClosureObject closure,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached("create(cachedBlock.getCallTarget())") final DirectCallNode directCallNode) {
            return directCallNode.call(FrameAccess.newClosureArgumentsTemplate(closure, cachedBlock, getContextOrMarkerNode.execute(frame), 0));
        }

        @Specialization(guards = {"closure.getNumArgs() == 0"}, replaces = "doValueDirect")
        protected static final Object doValueIndirect(final VirtualFrame frame, final BlockClosureObject closure,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached final IndirectCallNode indirectCallNode) {
            return indirectCallNode.call(closure.getCompiledBlock().getCallTarget(), FrameAccess.newClosureArgumentsTemplate(closure, getContextOrMarkerNode.execute(frame), 0));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 202)
    @ImportStatic(BlockClosurePrimitives.class)
    protected abstract static class PrimClosureValue1Node extends AbstractPrimitiveNode implements BinaryPrimitive {
        @SuppressWarnings("unused")
        @Specialization(guards = {"closure.getCompiledBlock() == cachedBlock", "cachedBlock.getNumArgs() == 1"}, assumptions = {"cachedBlock.getCallTargetStable()"}, limit = "INLINE_CACHE_SIZE")
        protected static final Object doValueDirect(final VirtualFrame frame, final BlockClosureObject closure, final Object arg,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached("create(cachedBlock.getCallTarget())") final DirectCallNode directCallNode) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, cachedBlock, getContextOrMarkerNode.execute(frame), 1);
            frameArguments[FrameAccess.getArgumentStartIndex()] = arg;
            return directCallNode.call(frameArguments);
        }

        @Specialization(guards = {"closure.getNumArgs() == 1"}, replaces = "doValueDirect")
        protected static final Object doValueIndirect(final VirtualFrame frame, final BlockClosureObject closure, final Object arg,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached final IndirectCallNode indirectCallNode) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, getContextOrMarkerNode.execute(frame), 1);
            frameArguments[FrameAccess.getArgumentStartIndex()] = arg;
            return indirectCallNode.call(closure.getCompiledBlock().getCallTarget(), frameArguments);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 203)
    @ImportStatic(BlockClosurePrimitives.class)
    protected abstract static class PrimClosureValue2Node extends AbstractPrimitiveNode implements TernaryPrimitive {
        @SuppressWarnings("unused")
        @Specialization(guards = {"closure.getCompiledBlock() == cachedBlock", "cachedBlock.getNumArgs() == 2"}, assumptions = {"cachedBlock.getCallTargetStable()"}, limit = "INLINE_CACHE_SIZE")
        protected static final Object doValueDirect(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached("create(cachedBlock.getCallTarget())") final DirectCallNode directCallNode) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, cachedBlock, getContextOrMarkerNode.execute(frame), 2);
            frameArguments[FrameAccess.getArgumentStartIndex()] = arg1;
            frameArguments[FrameAccess.getArgumentStartIndex() + 1] = arg2;
            return directCallNode.call(frameArguments);
        }

        @Specialization(guards = {"closure.getNumArgs() == 2"}, replaces = "doValueDirect")
        protected static final Object doValueIndirect(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached final IndirectCallNode indirectCallNode) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, getContextOrMarkerNode.execute(frame), 2);
            frameArguments[FrameAccess.getArgumentStartIndex()] = arg1;
            frameArguments[FrameAccess.getArgumentStartIndex() + 1] = arg2;
            return indirectCallNode.call(closure.getCompiledBlock().getCallTarget(), frameArguments);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 204)
    @ImportStatic(BlockClosurePrimitives.class)
    protected abstract static class PrimClosureValue3Node extends AbstractPrimitiveNode implements QuaternaryPrimitive {
        @SuppressWarnings("unused")
        @Specialization(guards = {"closure.getCompiledBlock() == cachedBlock", "cachedBlock.getNumArgs() == 3"}, assumptions = {"cachedBlock.getCallTargetStable()"}, limit = "INLINE_CACHE_SIZE")
        protected static final Object doValueDirect(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2, final Object arg3,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached("create(cachedBlock.getCallTarget())") final DirectCallNode directCallNode) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, cachedBlock, getContextOrMarkerNode.execute(frame), 3);
            frameArguments[FrameAccess.getArgumentStartIndex()] = arg1;
            frameArguments[FrameAccess.getArgumentStartIndex() + 1] = arg2;
            frameArguments[FrameAccess.getArgumentStartIndex() + 2] = arg3;
            return directCallNode.call(frameArguments);
        }

        @Specialization(guards = {"closure.getNumArgs() == 3"}, replaces = "doValueDirect")
        protected static final Object doValueIndirect(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2, final Object arg3,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached final IndirectCallNode indirectCallNode) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, getContextOrMarkerNode.execute(frame), 3);
            frameArguments[FrameAccess.getArgumentStartIndex()] = arg1;
            frameArguments[FrameAccess.getArgumentStartIndex() + 1] = arg2;
            frameArguments[FrameAccess.getArgumentStartIndex() + 2] = arg3;
            return indirectCallNode.call(closure.getCompiledBlock().getCallTarget(), frameArguments);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 205)
    @ImportStatic(BlockClosurePrimitives.class)
    protected abstract static class PrimClosureValueNode extends AbstractPrimitiveNode implements SenaryPrimitive {
        @SuppressWarnings("unused")
        @Specialization(guards = {"closure.getCompiledBlock() == cachedBlock", "cachedBlock.getNumArgs() == 4"}, assumptions = {"cachedBlock.getCallTargetStable()"}, limit = "INLINE_CACHE_SIZE")
        protected static final Object doValue4Direct(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2, final Object arg3, final Object arg4,
                        final NotProvided arg5,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached("create(cachedBlock.getCallTarget())") final DirectCallNode directCallNode) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, cachedBlock, getContextOrMarkerNode.execute(frame), 4);
            frameArguments[FrameAccess.getArgumentStartIndex()] = arg1;
            frameArguments[FrameAccess.getArgumentStartIndex() + 1] = arg2;
            frameArguments[FrameAccess.getArgumentStartIndex() + 2] = arg3;
            frameArguments[FrameAccess.getArgumentStartIndex() + 3] = arg4;
            return directCallNode.call(frameArguments);
        }

        @Specialization(guards = {"closure.getNumArgs() == 4"}, replaces = "doValue4Direct")
        protected static final Object doValue4Indirect(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2, final Object arg3, final Object arg4,
                        @SuppressWarnings("unused") final NotProvided arg5,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached final IndirectCallNode indirectCallNode) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, getContextOrMarkerNode.execute(frame), 4);
            frameArguments[FrameAccess.getArgumentStartIndex()] = arg1;
            frameArguments[FrameAccess.getArgumentStartIndex() + 1] = arg2;
            frameArguments[FrameAccess.getArgumentStartIndex() + 2] = arg3;
            frameArguments[FrameAccess.getArgumentStartIndex() + 3] = arg4;
            return indirectCallNode.call(closure.getCompiledBlock().getCallTarget(), frameArguments);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isNotProvided(arg5)", "closure.getCompiledBlock() == cachedBlock", "cachedBlock.getNumArgs() == 5"}, assumptions = {
                        "cachedBlock.getCallTargetStable()"}, limit = "INLINE_CACHE_SIZE")
        protected static final Object doValue5Direct(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2, final Object arg3, final Object arg4,
                        final Object arg5,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached("create(cachedBlock.getCallTarget())") final DirectCallNode directCallNode) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, cachedBlock, getContextOrMarkerNode.execute(frame), 5);
            frameArguments[FrameAccess.getArgumentStartIndex()] = arg1;
            frameArguments[FrameAccess.getArgumentStartIndex() + 1] = arg2;
            frameArguments[FrameAccess.getArgumentStartIndex() + 2] = arg3;
            frameArguments[FrameAccess.getArgumentStartIndex() + 3] = arg4;
            frameArguments[FrameAccess.getArgumentStartIndex() + 4] = arg5;
            return directCallNode.call(frameArguments);
        }

        @Specialization(guards = {"!isNotProvided(arg5)", "closure.getNumArgs() == 5"}, replaces = "doValue5Direct")
        protected static final Object doValue5Indirect(final VirtualFrame frame, final BlockClosureObject closure, final Object arg1, final Object arg2, final Object arg3, final Object arg4,
                        final Object arg5,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached final IndirectCallNode indirectCallNode) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, getContextOrMarkerNode.execute(frame), 4);
            frameArguments[FrameAccess.getArgumentStartIndex()] = arg1;
            frameArguments[FrameAccess.getArgumentStartIndex() + 1] = arg2;
            frameArguments[FrameAccess.getArgumentStartIndex() + 2] = arg3;
            frameArguments[FrameAccess.getArgumentStartIndex() + 3] = arg4;
            frameArguments[FrameAccess.getArgumentStartIndex() + 4] = arg5;
            return indirectCallNode.call(closure.getCompiledBlock().getCallTarget(), frameArguments);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 206)
    @ImportStatic(BlockClosurePrimitives.class)
    protected abstract static class PrimClosureValueAryNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        @SuppressWarnings("unused")
        @Specialization(guards = {"closure.getCompiledBlock() == cachedBlock", "cachedBlock.getNumArgs() == sizeNode.execute(argArray)"}, assumptions = {
                        "cachedBlock.getCallTargetStable()"}, limit = "INLINE_CACHE_SIZE")
        protected static final Object doValueDirect(final VirtualFrame frame, final BlockClosureObject closure, final ArrayObject argArray,
                        @SuppressWarnings("unused") @Cached final SqueakObjectSizeNode sizeNode,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached("createForFrameArguments()") final ArrayObjectCopyIntoObjectArrayNode copyIntoNode,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached("create(cachedBlock.getCallTarget())") final DirectCallNode directCallNode) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, getContextOrMarkerNode.execute(frame), sizeNode.execute(argArray));
            copyIntoNode.execute(frameArguments, argArray);
            return directCallNode.call(frameArguments);
        }

        @Specialization(guards = {"closure.getNumArgs() == sizeNode.execute(argArray)"}, replaces = "doValueDirect", limit = "1")
        protected static final Object doValueIndirect(final VirtualFrame frame, final BlockClosureObject closure, final ArrayObject argArray,
                        @SuppressWarnings("unused") @Cached final SqueakObjectSizeNode sizeNode,
                        @Cached("createForFrameArguments()") final ArrayObjectCopyIntoObjectArrayNode copyIntoNode,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached final IndirectCallNode indirectCallNode) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, getContextOrMarkerNode.execute(frame), sizeNode.execute(argArray));
            copyIntoNode.execute(frameArguments, argArray);
            return indirectCallNode.call(closure.getCompiledBlock().getCallTarget(), frameArguments);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 221)
    @ImportStatic(BlockClosurePrimitives.class)
    public abstract static class PrimClosureValueNoContextSwitchNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        @SuppressWarnings("unused")
        @Specialization(guards = {"closure.getCompiledBlock() == cachedBlock", "cachedBlock.getNumArgs() == 0"}, assumptions = {"cachedBlock.getCallTargetStable()"}, limit = "INLINE_CACHE_SIZE")
        protected static final Object doValueDirect(final VirtualFrame frame, final BlockClosureObject closure,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached("create(cachedBlock.getCallTarget())") final DirectCallNode directCallNode,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            final boolean wasActive = image.interrupt.isActive();
            image.interrupt.deactivate();
            try {
                return directCallNode.call(FrameAccess.newClosureArgumentsTemplate(closure, cachedBlock, getContextOrMarkerNode.execute(frame), 0));
            } finally {
                if (wasActive) {
                    image.interrupt.activate();
                }
            }
        }

        @Specialization(guards = {"closure.getNumArgs() == 0"}, replaces = "doValueDirect")
        protected static final Object doValueIndirect(final VirtualFrame frame, final BlockClosureObject closure,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached final IndirectCallNode indirectCallNode,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            final boolean wasActive = image.interrupt.isActive();
            image.interrupt.deactivate();
            try {
                return indirectCallNode.call(closure.getCompiledBlock().getCallTarget(), FrameAccess.newClosureArgumentsTemplate(closure, getContextOrMarkerNode.execute(frame), 0));
            } finally {
                if (wasActive) {
                    image.interrupt.activate();
                }
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 222)
    @ImportStatic(BlockClosurePrimitives.class)
    protected abstract static class PrimClosureValueAryNoContextSwitchNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        @SuppressWarnings("unused")
        @Specialization(guards = {"closure.getCompiledBlock() == cachedBlock", "cachedBlock.getNumArgs() == sizeNode.execute(argArray)"}, //
                        assumptions = {"cachedBlock.getCallTargetStable()"}, limit = "INLINE_CACHE_SIZE")
        protected static final Object doValueDirect(final VirtualFrame frame, final BlockClosureObject closure, final ArrayObject argArray,
                        @SuppressWarnings("unused") @Cached final SqueakObjectSizeNode sizeNode,
                        @Cached("closure.getCompiledBlock()") final CompiledBlockObject cachedBlock,
                        @Cached("createForFrameArguments()") final ArrayObjectCopyIntoObjectArrayNode copyIntoNode,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached("create(cachedBlock.getCallTarget())") final DirectCallNode directCallNode,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, getContextOrMarkerNode.execute(frame), sizeNode.execute(argArray));
            copyIntoNode.execute(frameArguments, argArray);
            final boolean wasActive = image.interrupt.isActive();
            image.interrupt.deactivate();
            try {
                return directCallNode.call(frameArguments);
            } finally {
                if (wasActive) {
                    image.interrupt.activate();
                }
            }
        }

        @Specialization(guards = {"closure.getNumArgs() == sizeNode.execute(argArray)"}, replaces = "doValueDirect", limit = "1")
        protected static final Object doValueIndirect(final VirtualFrame frame, final BlockClosureObject closure, final ArrayObject argArray,
                        @SuppressWarnings("unused") @Cached final SqueakObjectSizeNode sizeNode,
                        @Cached("createForFrameArguments()") final ArrayObjectCopyIntoObjectArrayNode copyIntoNode,
                        @Cached final GetContextOrMarkerNode getContextOrMarkerNode,
                        @Cached final IndirectCallNode indirectCallNode,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            final Object[] frameArguments = FrameAccess.newClosureArgumentsTemplate(closure, getContextOrMarkerNode.execute(frame), sizeNode.execute(argArray));
            copyIntoNode.execute(frameArguments, argArray);
            final boolean wasActive = image.interrupt.isActive();
            image.interrupt.deactivate();
            try {
                return indirectCallNode.call(closure.getCompiledBlock().getCallTarget(), frameArguments);
            } finally {
                if (wasActive) {
                    image.interrupt.activate();
                }
            }
        }
    }

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return BlockClosurePrimitivesFactory.getFactories();
    }
}
