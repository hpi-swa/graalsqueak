/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.context.frame;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.trufflesqueak.model.CompiledMethodObject;
import de.hpi.swa.trufflesqueak.nodes.AbstractNode;
import de.hpi.swa.trufflesqueak.util.FrameAccess;

public final class CreateFrameArgumentsNode extends AbstractNode {
    @CompilationFinal private FrameSlot stackPointerSlot;
    @CompilationFinal private int stackPointer;
    @Children private FrameSlotReadNode[] receiverAndArgumentsNodes;

    private CreateFrameArgumentsNode(final int argumentCount) {
        receiverAndArgumentsNodes = new FrameSlotReadNode[1 + argumentCount];
    }

    public static CreateFrameArgumentsNode create(final int argumentCount) {
        return new CreateFrameArgumentsNode(argumentCount);
    }

    public Object[] execute(final VirtualFrame frame, final CompiledMethodObject method, final Object sender) {
        if (stackPointerSlot == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            stackPointerSlot = FrameAccess.getStackPointerSlot(frame);
            stackPointer = FrameAccess.getStackPointer(frame, stackPointerSlot) - receiverAndArgumentsNodes.length;
            assert stackPointer >= 0 : "Bad stack pointer";
            for (int i = 0; i < receiverAndArgumentsNodes.length; i++) {
                receiverAndArgumentsNodes[i] = insert(FrameSlotReadNode.create(frame, stackPointer + i));
            }
        }
        FrameAccess.setStackPointer(frame, stackPointerSlot, stackPointer);
        return FrameAccess.newWith(frame, method, sender, null, receiverAndArgumentsNodes);
    }
}
