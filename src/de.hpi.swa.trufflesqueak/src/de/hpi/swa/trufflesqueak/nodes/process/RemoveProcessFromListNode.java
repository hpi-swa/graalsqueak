/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.process;

import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.trufflesqueak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.trufflesqueak.model.AbstractSqueakObject;
import de.hpi.swa.trufflesqueak.model.NilObject;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.LINKED_LIST;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.PROCESS;
import de.hpi.swa.trufflesqueak.nodes.AbstractNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectReadNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;

public abstract class RemoveProcessFromListNode extends AbstractNode {
    @Child private AbstractPointersObjectReadNode readNode = AbstractPointersObjectReadNode.create();
    @Child private AbstractPointersObjectWriteNode writeNode = AbstractPointersObjectWriteNode.create();

    public final void executeRemove(final PointersObject process, final PointersObject list) {
        final Object first = readNode.execute(list, LINKED_LIST.FIRST_LINK);
        final Object last = readNode.execute(list, LINKED_LIST.LAST_LINK);
        executeRemove(process, list, first, last);
        writeNode.executeNil(process, PROCESS.NEXT_LINK);
    }

    protected abstract void executeRemove(PointersObject process, PointersObject list, Object first, Object last);

    @Specialization(guards = "process == first")
    protected final void doRemoveEqual(final PointersObject process, final PointersObject list, @SuppressWarnings("unused") final PointersObject first, final AbstractSqueakObject last) {
        final Object next = readNode.execute(process, PROCESS.NEXT_LINK);
        writeNode.execute(list, LINKED_LIST.FIRST_LINK, next);
        if (process == last) {
            writeNode.executeNil(list, LINKED_LIST.LAST_LINK);
        }
    }

    @Specialization(guards = "process != first")
    protected final void doRemoveNotEqual(final PointersObject process, final PointersObject list, final PointersObject first, final AbstractSqueakObject last) {
        PointersObject temp = first;
        Object next;
        while (true) {
            next = readNode.execute(temp, PROCESS.NEXT_LINK);
            if (next == process) {
                break;
            } else if (next == NilObject.SINGLETON) {
                throw PrimitiveFailed.GENERIC_ERROR; // TODO: make this better.
            } else {
                temp = (PointersObject) next;
            }
        }
        next = readNode.execute(process, PROCESS.NEXT_LINK);
        writeNode.execute(temp, PROCESS.NEXT_LINK, next);
        if (process == last) {
            writeNode.execute(list, LINKED_LIST.LAST_LINK, temp);
        }
    }

    @SuppressWarnings("unused")
    @Specialization
    protected static final void doRemoveNotEqual(final PointersObject process, final PointersObject list, final NilObject first, final AbstractSqueakObject last) {
        throw PrimitiveFailed.GENERIC_ERROR; // TODO: make sure this is needed (and make it better).
    }
}
