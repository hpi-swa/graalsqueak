/*
 * Copyright (c) 2021 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.primitives;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class AbstractSingletonPrimitiveNode extends AbstractPrimitiveNode {

    protected abstract AbstractPrimitiveNode getSingleton();

    @Override
    public final Object executeWithArguments(final VirtualFrame frame, final Object... receiverAndArguments) {
        return execute();
    }

    @Override
    public final Object executeWithReceiverAndArguments(final VirtualFrame frame, final Object receiver, final Object... arguments) {
        return execute();
    }

    @Override
    public Object execute(final VirtualFrame frame) {
        return execute();
    }

    protected abstract Object execute();

    @Override
    public final boolean isAdoptable() {
        return false;
    }

    @Override
    public final Node copy() {
        return getSingleton();
    }

    @Override
    public final Node deepCopy() {
        return getSingleton();
    }
}
