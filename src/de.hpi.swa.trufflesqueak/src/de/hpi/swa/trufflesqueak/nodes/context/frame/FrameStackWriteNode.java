/*
 * Copyright (c) 2017-2021 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.context.frame;

import com.oracle.truffle.api.dsl.NodeField;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;

import de.hpi.swa.trufflesqueak.nodes.AbstractNode;
import de.hpi.swa.trufflesqueak.nodes.context.frame.FrameStackWriteNodeFactory.FrameSlotWriteNodeGen;
import de.hpi.swa.trufflesqueak.util.FrameAccess;

public abstract class FrameStackWriteNode extends AbstractNode {
    public static FrameStackWriteNode create(final Frame frame, final int index) {
        final int numArgs = FrameAccess.getNumArguments(frame);
        if (index < numArgs) {
            return new FrameArgumentWriteNode(index);
        } else {
            return FrameSlotWriteNodeGen.create(FrameAccess.findOrAddStackSlot(frame, index));
        }
    }

    public abstract void executeWrite(Frame frame, Object value);

    @NodeField(name = "slot", type = FrameSlot.class)
    public abstract static class FrameSlotWriteNode extends FrameStackWriteNode {

        protected abstract FrameSlot getSlot();

        @Specialization(guards = "isBooleanOrIllegal(frame)")
        protected final void writeBool(final Frame frame, final boolean value) {
            /* Initialize type on first write. No-op if kind is already Boolean. */
            frame.getFrameDescriptor().setFrameSlotKind(getSlot(), FrameSlotKind.Boolean);

            frame.setBoolean(getSlot(), value);
        }

        @Specialization(guards = "isLongOrIllegal(frame)")
        protected final void writeLong(final Frame frame, final long value) {
            /* Initialize type on first write. No-op if kind is already Long. */
            frame.getFrameDescriptor().setFrameSlotKind(getSlot(), FrameSlotKind.Long);

            frame.setLong(getSlot(), value);
        }

        @Specialization(guards = "isDoubleOrIllegal(frame)")
        protected final void writeDouble(final Frame frame, final double value) {
            /* Initialize type on first write. No-op if kind is already Double. */
            frame.getFrameDescriptor().setFrameSlotKind(getSlot(), FrameSlotKind.Double);

            frame.setDouble(getSlot(), value);
        }

        @Specialization(replaces = {"writeBool", "writeLong", "writeDouble"})
        protected final void writeObject(final Frame frame, final Object value) {
            /* Initialize type on first write. No-op if kind is already Object. */
            frame.getFrameDescriptor().setFrameSlotKind(getSlot(), FrameSlotKind.Object);

            frame.setObject(getSlot(), value);
        }

        protected final boolean isBooleanOrIllegal(final Frame frame) {
            final FrameSlotKind kind = frame.getFrameDescriptor().getFrameSlotKind(getSlot());
            return kind == FrameSlotKind.Boolean || kind == FrameSlotKind.Illegal;
        }

        protected final boolean isLongOrIllegal(final Frame frame) {
            final FrameSlotKind kind = frame.getFrameDescriptor().getFrameSlotKind(getSlot());
            return kind == FrameSlotKind.Long || kind == FrameSlotKind.Illegal;
        }

        protected final boolean isDoubleOrIllegal(final Frame frame) {
            final FrameSlotKind kind = frame.getFrameDescriptor().getFrameSlotKind(getSlot());
            return kind == FrameSlotKind.Double || kind == FrameSlotKind.Illegal;
        }
    }

    private static final class FrameArgumentWriteNode extends FrameStackWriteNode {
        private final int index;

        private FrameArgumentWriteNode(final int index) {
            this.index = FrameAccess.getArgumentStartIndex() + index;
        }

        @Override
        public void executeWrite(final Frame frame, final Object value) {
            frame.getArguments()[index] = value;
        }
    }
}
