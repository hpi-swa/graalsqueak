/*
 * Copyright (c) 2017-2019 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.nodes.process;

import java.util.logging.Level;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNodeWithImage;
import de.hpi.swa.graal.squeak.nodes.GetOrCreateContextNode;
import de.hpi.swa.graal.squeak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.ArrayObjectReadNode;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.ArrayObjectSizeNode;
import de.hpi.swa.graal.squeak.nodes.primitives.impl.ControlPrimitives;
import de.hpi.swa.graal.squeak.shared.SqueakLanguageConfig;

public final class WakeHighestPriorityNode extends AbstractNodeWithImage {
    private static final TruffleLogger LOG = TruffleLogger.getLogger(SqueakLanguageConfig.ID, ControlPrimitives.class);
    private static final boolean isLoggingEnabled = LOG.isLoggable(Level.FINE);

    @Child private ArrayObjectReadNode arrayReadNode = ArrayObjectReadNode.create();
    @Child private ArrayObjectSizeNode arraySizeNode = ArrayObjectSizeNode.create();
    @Child private AbstractPointersObjectWriteNode pointersWriteNode = AbstractPointersObjectWriteNode.create();
    private final BranchProfile errorProfile = BranchProfile.create();
    @Child private GetOrCreateContextNode contextNode;

    private WakeHighestPriorityNode(final CompiledCodeObject code) {
        super(code.image);
        contextNode = GetOrCreateContextNode.create(code);
    }

    public static WakeHighestPriorityNode create(final CompiledCodeObject code) {
        return new WakeHighestPriorityNode(code);
    }

    public void executeWake(final VirtualFrame frame) {
        // Return the highest priority process that is ready to run.
        // Note: It is a fatal VM error if there is no runnable process.
        final ArrayObject schedLists = image.getProcessLists();
        for (long p = arraySizeNode.execute(schedLists) - 1; p >= 0; p--) {
            final PointersObject processList = (PointersObject) arrayReadNode.execute(schedLists, p);
            while (!processList.isEmptyList()) {
                final PointersObject newProcess = processList.removeFirstLinkOfList(pointersWriteNode);
                final Object newContext = newProcess.getSuspendedContext();
                final int priority = (int) (p + 1);
                if (newContext instanceof ContextObject) {
                    final ContextObject thisContext = contextNode.executeGet(frame, NilObject.SINGLETON);
                    if (isLoggingEnabled) {
                        LOG.fine(() -> logSwitch(newProcess, priority, thisContext, (ContextObject) newContext));
                    }
                    thisContext.transferTo(pointersWriteNode, newProcess);
                    throw SqueakException.create("Should not be reached");
                } else {
                    if (isLoggingEnabled) {
                        LOG.severe(() -> "evicted zombie process from run queue " + priority);
                    }
                }
            }
        }
        errorProfile.enter();
        throw SqueakException.create("scheduler could not find a runnable process");
    }

    private String logSwitch(final PointersObject newProcess, final int priority, final ContextObject thisContext, final ContextObject newContext) {
        final StringBuilder b = new StringBuilder();
        b.append("Switching from process @");
        final PointersObject currentProcess = image.getActiveProcess();
        b.append(Integer.toHexString(currentProcess.hashCode()));
        b.append(" with priority ");
        b.append(currentProcess.getPriority());
        b.append(" and stack\n");
        thisContext.printSqMaterializedStackTraceOn(b);
        b.append("\n...to process @");
        b.append(Integer.toHexString(newProcess.hashCode()));
        b.append(" with priority ");
        b.append(priority);
        b.append(" and stack\n");
        newContext.printSqMaterializedStackTraceOn(b);
        return b.toString();
    }

}
