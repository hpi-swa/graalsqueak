package de.hpi.swa.graal.squeak.nodes.primitives.impl;

import java.util.List;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;

import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.FrameMarker;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.accessing.ContextObjectNodes.ContextObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ContextObjectNodes.ContextObjectWriteNode;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.graal.squeak.util.FrameAccess;

public class ContextPrimitives extends AbstractPrimitiveFactoryHolder {

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 76)
    protected abstract static class PrimStoreStackPointerNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        protected PrimStoreStackPointerNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected static final ContextObject store(final ContextObject receiver, final long value) {
            receiver.setStackPointer((int) value);
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 195)
    protected abstract static class PrimFindNextUnwindContextUpToNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        public PrimFindNextUnwindContextUpToNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "receiver.hasMaterializedSender()")
        protected final Object doFindNext(final ContextObject receiver, final AbstractSqueakObject previousContextOrNil) {
            ContextObject current = receiver;
            while (current != previousContextOrNil) {
                final Object sender = current.getSender();
                if (sender == code.image.nil || sender == previousContextOrNil) {
                    break;
                } else {
                    current = (ContextObject) sender;
                    if (current.getClosure() == null && current.getMethod().isUnwindMarked()) {
                        return current;
                    }
                }
            }
            return code.image.nil;
        }

        @Specialization(guards = "!receiver.hasMaterializedSender()")
        protected final Object doFindNextAvoidingMaterialization(final ContextObject receiver, final ContextObject previousContext) {
            // Sender is not materialized, so avoid materialization by walking Truffle frames.
            final boolean[] foundMyself = {false};
            final Object result = Truffle.getRuntime().iterateFrames((frameInstance) -> {
                final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                if (!FrameAccess.isGraalSqueakFrame(current)) {
                    return null; // Foreign frame cannot be unwind marked.
                }
                final ContextObject context = FrameAccess.getContext(current);
                if (!foundMyself[0]) {
                    if (receiver == context) {
                        foundMyself[0] = true;
                    }
                } else {
                    if (previousContext == context) {
                        return code.image.nil;
                    }
                    if (FrameAccess.getClosure(current) == null && FrameAccess.getMethod(current).isUnwindMarked()) {
                        if (context != null) {
                            return context;
                        } else {
                            return ContextObject.create(frameInstance);
                        }
                    }
                }
                return null;
            });
            assert foundMyself[0] : "Did not find receiver with virtual sender on Truffle stack";
            return result != null ? result : code.image.nil;
        }

        @Specialization(guards = "!receiver.hasMaterializedSender()")
        protected final Object doFindNextAvoidingMaterializationNil(final ContextObject receiver, @SuppressWarnings("unused") final NilObject nil) {
            return doFindNext(receiver, nil);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 196)
    protected abstract static class PrimTerminateToNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        public PrimTerminateToNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected final Object doUnwindAndTerminate(final ContextObject receiver, final ContextObject previousContext) {
            /*
             * Terminate all the Contexts between me and previousContext, if previousContext is on
             * my Context stack. Make previousContext my sender.
             */
            terminateBetween(receiver, previousContext);
            receiver.setSender(previousContext);
            return receiver;
        }

        @Specialization
        protected static final Object doTerminate(final ContextObject receiver, @SuppressWarnings("unused") final NilObject nil) {
            receiver.removeSender();
            return receiver;
        }

        private void terminateBetween(final ContextObject start, final ContextObject end) {
            ContextObject current = start;
            while (current.hasMaterializedSender()) {
                final AbstractSqueakObject sender = start.getSender();
                current.terminate();
                if (sender == code.image.nil || sender == end) {
                    return;
                } else {
                    current = (ContextObject) sender;
                }
            }
            terminateBetween(current.getFrameMarker(), end);
        }

        private void terminateBetween(final FrameMarker start, final ContextObject end) {
            assert start != null;
            final ContextObject[] bottomContextOnTruffleStack = new ContextObject[1];
            final ContextObject result = Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<ContextObject>() {
                boolean foundMyself = false;

                @Override
                public ContextObject visitFrame(final FrameInstance frameInstance) {
                    final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                    if (!FrameAccess.isGraalSqueakFrame(current)) {
                        return null;
                    }
                    final CompiledCodeObject currentCode = FrameAccess.getMethod(current);
                    if (!foundMyself) {
                        if (start == FrameAccess.getMarker(current)) {
                            foundMyself = true;
                        }
                    } else {
                        final ContextObject context = FrameAccess.getContext(current);
                        if (context == end) {
                            return end;
                        }
                        bottomContextOnTruffleStack[0] = context;
                        final Frame currentWritable = frameInstance.getFrame(FrameInstance.FrameAccess.READ_WRITE);
                        // Terminate frame
                        FrameAccess.setInstructionPointer(currentWritable, currentCode, -1);
                        FrameAccess.setSender(currentWritable, code.image.nil);
                    }
                    return null;
                }
            });
            if (result == null && bottomContextOnTruffleStack[0] != null) {
                terminateBetween(bottomContextOnTruffleStack[0], end);
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 197)
    protected abstract static class PrimNextHandlerContextNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        protected PrimNextHandlerContextNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"receiver.hasMaterializedSender()"})
        protected final AbstractSqueakObject findNext(final ContextObject receiver) {
            ContextObject context = receiver;
            while (true) {
                if (context.getMethod().isExceptionHandlerMarked()) {
                    assert context.getClosure() == null;
                    return context;
                }
                final AbstractSqueakObject sender = context.getSender();
                if (sender instanceof ContextObject) {
                    context = (ContextObject) sender;
                } else {
                    assert sender == code.image.nil;
                    return code.image.nil;
                }
            }
        }

        @Specialization(guards = {"!receiver.hasMaterializedSender()"})
        protected final AbstractSqueakObject findNextAvoidingMaterialization(final ContextObject receiver) {
            final boolean[] foundMyself = new boolean[1];
            final Object[] lastSender = new Object[1];
            final ContextObject result = Truffle.getRuntime().iterateFrames(frameInstance -> {
                final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                if (!FrameAccess.isGraalSqueakFrame(current)) {
                    return null; // Foreign frame cannot be handler.
                }
                final ContextObject context = FrameAccess.getContext(current);
                if (!foundMyself[0]) {
                    if (context == receiver) {
                        foundMyself[0] = true;
                    }
                } else {
                    if (FrameAccess.getMethod(current).isExceptionHandlerMarked()) {
                        if (context != null) {
                            return context;
                        } else {
                            return ContextObject.create(frameInstance);
                        }
                    } else {
                        lastSender[0] = FrameAccess.getSender(current);
                    }
                }
                return null;
            });
            if (result == null) {
                if (!foundMyself[0]) {
                    return findNext(receiver); // Fallback to other version.
                }
                if (lastSender[0] instanceof ContextObject) {
                    return findNext((ContextObject) lastSender[0]);
                } else {
                    return code.image.nil;
                }
            } else {
                return result;
            }
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @ImportStatic(FrameAccess.class)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 210)
    protected abstract static class PrimContextAtNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        @Child private ContextObjectReadNode readNode = ContextObjectReadNode.create();

        protected PrimContextAtNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"index < receiver.getStackSize()"})
        protected final Object doContextObject(final ContextObject receiver, final long index) {
            return readNode.execute(receiver, CONTEXT.TEMP_FRAME_START + index - 1);
        }
    }

    @NodeInfo(cost = NodeCost.NONE)
    @ImportStatic(FrameAccess.class)
    @GenerateNodeFactory
    @SqueakPrimitive(indices = 211)
    protected abstract static class PrimContextAtPutNode extends AbstractPrimitiveNode implements TernaryPrimitive {
        @Child private ContextObjectWriteNode writeNode = ContextObjectWriteNode.create();

        protected PrimContextAtPutNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "index < receiver.getStackSize()")
        protected final Object doContextObject(final ContextObject receiver, final long index, final Object value) {
            writeNode.execute(receiver, CONTEXT.TEMP_FRAME_START + index - 1, value);
            return value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 212)
    protected abstract static class PrimContextSizeNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        protected PrimContextSizeNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "receiver.hasTruffleFrame()")
        protected static final long doSize(final ContextObject receiver) {
            return FrameAccess.getStackPointer(receiver.getTruffleFrame());
        }

        @Specialization(guards = "!receiver.hasTruffleFrame()")
        protected static final long doSizeWithoutFrame(final ContextObject receiver) {
            return receiver.size() - receiver.instsize();
        }
    }

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return ContextPrimitivesFactory.getFactories();
    }
}
