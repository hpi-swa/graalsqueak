package de.hpi.swa.graal.squeak.util;

import java.io.PrintWriter;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.BlockClosureObject;
import de.hpi.swa.graal.squeak.model.CompiledBlockObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.FrameMarker;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.model.layout.ObjectLayouts.SPECIAL_OBJECT;

public class DebugUtils {

    /*
     * Helper functions for debugging purposes.
     */

    public static void printSqMaterializedStackTraceOn(final StringBuilder b, final ContextObject context) {
        ContextObject current = context;
        while (current != null) {
            final Object[] rcvrAndArgs = current.getReceiverAndNArguments(current.getBlockOrMethod().getNumArgsAndCopied());
            b.append(MiscUtils.format("%s #(%s) [%s]", current, ArrayUtils.toJoinedString(", ", rcvrAndArgs), current.getFrameMarker()));
            b.append('\n');
            final Object sender = current.getFrameSender();
            if (sender == NilObject.SINGLETON) {
                break;
            } else if (sender instanceof FrameMarker) {
                b.append(sender);
                b.append('\n');
                break;
            } else {
                current = (ContextObject) sender;
            }
        }
    }

    @TruffleBoundary
    public static void printSqStackTrace(final ContextObject context) {
        final StringBuilder b = new StringBuilder();
        printSqMaterializedStackTraceOn(b, context);
        context.image.getOutput().println(b.toString());
    }

    public static String currentState(final SqueakImageContext image) {
        final StringBuilder b = new StringBuilder();
        b.append("\nImage processes state\n");
        final PointersObject activeProcess = image.getActiveProcess();
        final long activePriority = image.getPriority(activeProcess);
        b.append("*Active process @");
        b.append(Integer.toHexString(activeProcess.hashCode()));
        b.append(" priority ");
        b.append(activePriority);
        b.append('\n');
        final Object interruptSema = image.getSpecialObject(SPECIAL_OBJECT.THE_INTERRUPT_SEMAPHORE);
        printSemaphoreOrNil(image, b, "*Interrupt semaphore @", interruptSema, true);
        final Object timerSema = image.getSpecialObject(SPECIAL_OBJECT.THE_TIMER_SEMAPHORE);
        printSemaphoreOrNil(image, b, "*Timer semaphore @", timerSema, true);
        final Object finalizationSema = image.getSpecialObject(SPECIAL_OBJECT.THE_FINALIZATION_SEMAPHORE);
        printSemaphoreOrNil(image, b, "*Finalization semaphore @", finalizationSema, true);
        final Object lowSpaceSema = image.getSpecialObject(SPECIAL_OBJECT.THE_LOW_SPACE_SEMAPHORE);
        printSemaphoreOrNil(image, b, "*Low space semaphore @", lowSpaceSema, true);
        final ArrayObject externalObjects = (ArrayObject) image.getSpecialObject(SPECIAL_OBJECT.EXTERNAL_OBJECTS_ARRAY);
        if (!externalObjects.isEmptyType()) {
            final Object[] semaphores = externalObjects.getObjectStorage();
            for (int i = 0; i < semaphores.length; i++) {
                printSemaphoreOrNil(image, b, "*External semaphore at index " + (i + 1) + " @", semaphores[i], false);
            }
        }
        final Object[] lists = image.getProcessLists().getObjectStorage();
        for (int i = 0; i < lists.length; i++) {
            printLinkedList(image, b, "*Quiescent processes list at priority " + (i + 1), (PointersObject) lists[i]);
        }
        return b.toString();
    }

    private static boolean printLinkedList(final SqueakImageContext image, final StringBuilder b, final String label, final PointersObject linkedList) {
        Object temp = image.getFirstLink(linkedList);
        if (temp instanceof PointersObject) {
            b.append(label);
            b.append(" and process");
            if (temp != image.getLastLink(linkedList)) {
                b.append("es:\n");
            } else {
                b.append(":\n");
            }
            while (temp instanceof PointersObject) {
                final PointersObject aProcess = (PointersObject) temp;
                final Object aContext = image.getSuspendedContext(aProcess);
                if (aContext instanceof ContextObject) {
                    assert ((ContextObject) aContext).getProcess() == null || ((ContextObject) aContext).getProcess() == aProcess;
                    b.append("\tprocess @");
                    b.append(Integer.toHexString(aProcess.hashCode()));
                    b.append(" with suspended context ");
                    b.append(aContext);
                    b.append(" and stack trace:\n");
                    DebugUtils.printSqMaterializedStackTraceOn(b, (ContextObject) aContext);
                } else {
                    b.append("\tprocess @");
                    b.append(Integer.toHexString(aProcess.hashCode()));
                    b.append(" with suspended context nil\n");
                }
                temp = image.getNextLink(aProcess);
            }
            return true;
        } else {
            return false;
        }
    }

    private static void printSemaphoreOrNil(final SqueakImageContext image, final StringBuilder b, final String label, final Object semaphoreOrNil, final boolean printIfNil) {
        if (semaphoreOrNil instanceof PointersObject) {
            b.append(label);
            b.append(Integer.toHexString(semaphoreOrNil.hashCode()));
            b.append(" with ");
            b.append(image.getExcessSignals((PointersObject) semaphoreOrNil));
            b.append(" excess signals");
            if (!printLinkedList(image, b, "", (PointersObject) semaphoreOrNil)) {
                b.append(" and no processes\n");
            }
        } else {
            if (printIfNil) {
                b.append(label);
                b.append(" is nil\n");
            }
        }
    }

    @TruffleBoundary
    public static void printSqStackTrace() {
        CompilerDirectives.transferToInterpreter();
        final boolean isTravisBuild = System.getenv().containsKey("TRAVIS");
        final int[] depth = new int[1];
        final boolean[] truffleFrames = new boolean[1];

        new FramesAndContextsIterator(
                        (frame, code) -> {
                            PrintWriter err = null;
                            if (depth[0]++ > 50 && isTravisBuild) {
                                return;
                            } else if (!truffleFrames[0]) {
                                truffleFrames[0] = true;
                                err = FrameAccess.getMethod(frame).image.getError();
                                err.println("== Truffle stack trace ===========================================================");
                            }
                            final Object sender = FrameAccess.getSender(frame);
                            final Object marker = FrameAccess.getMarker(frame, code);
                            final Object context = FrameAccess.getContext(frame, code);
                            final String argumentsString = ArrayUtils.toJoinedString(", ", FrameAccess.getReceiverAndArguments(frame));
                            err.println(MiscUtils.format("%s #(%s) [marker: %s, sender: %s]", context, argumentsString, marker, sender));
                        },
                        (context) -> {
                            PrintWriter err = null;
                            if (depth[0]++ > 50 && isTravisBuild) {
                                return;
                            } else if (truffleFrames[0]) {
                                truffleFrames[0] = false;
                                err = context.image.getError();
                                err.println("== Squeak frames ================================================================");
                            }
                            final Object[] rcvrAndArgs = context.getReceiverAndNArguments(context.getBlockOrMethod().getNumArgsAndCopied());
                            err.println(MiscUtils.format("%s #(%s) [%s]", context, ArrayUtils.toJoinedString(", ", rcvrAndArgs), context.getFrameMarker()));
                        }).scanFor((FrameMarker) null, NilObject.SINGLETON, NilObject.SINGLETON);
    }

    public static String logSwitch(final PointersObject newProcess, final int newPriority, final PointersObject currentProcess, final ContextObject thisContext, final ContextObject newContext) {
        final StringBuilder b = new StringBuilder();
        b.append("Switching from process @");
        b.append(Integer.toHexString(currentProcess.hashCode()));
        b.append(" with priority ");
        b.append(currentProcess.getPriority());
        b.append(" and stack\n");
        printSqMaterializedStackTraceOn(b, thisContext);
        b.append("\n...to process @");
        b.append(Integer.toHexString(newProcess.hashCode()));
        b.append(" with priority ");
        b.append(newPriority);
        b.append(" and stack\n");
        printSqMaterializedStackTraceOn(b, newContext);
        return b.toString();
    }

    public static String logNoSwitch(final PointersObject newProcess) {
        final StringBuilder b = new StringBuilder();
        b.append("\nCannot resume process @");
        b.append(Integer.toHexString(newProcess.hashCode()));
        b.append(" with priority ");
        b.append(newProcess.getPriority());
        final AbstractSqueakObject newContext = newProcess.getSuspendedContext();
        if (newContext == NilObject.SINGLETON) {
            b.append(" and nil suspendedContext\n");
        } else {
            b.append(" and stack\n");
            printSqMaterializedStackTraceOn(b, (ContextObject) newContext);
        }
        b.append("\n...because it hs a lower priority than the currently active process @");
        final PointersObject currentProcess = newProcess.image.getActiveProcess();
        b.append(Integer.toHexString(currentProcess.hashCode()));
        b.append(" with priority ");
        b.append(currentProcess.getPriority());
        return b.toString();
    }

    public static String stackFor(final VirtualFrame frame, final CompiledCodeObject code) {
        final Object[] frameArguments = frame.getArguments();
        final Object receiver = frameArguments[3];
        final StringBuilder b = new StringBuilder("\n\t\t- Receiver:                         ");
        b.append(receiver);
        if (receiver instanceof ContextObject) {
            final ContextObject context = (ContextObject) receiver;
            if (context.hasTruffleFrame()) {
                final MaterializedFrame receiverFrame = context.getTruffleFrame();
                final Object[] receiverFrameArguments = receiverFrame.getArguments();
                b.append("\n\t\t\t\t- Receiver:                         ");
                b.append(receiverFrameArguments[3]);
                final CompiledCodeObject receiverCode = receiverFrameArguments[2] != null ? ((BlockClosureObject) receiverFrameArguments[2]).getCompiledBlock()
                                : (CompiledMethodObject) receiverFrameArguments[0];
                if (receiverCode != null) {
                    b.append("\n\t\t\t\t- Stack (including args and temps)\n");
                    final int zeroBasedStackp = FrameAccess.getStackPointer(receiverFrame, receiverCode) - 1;
                    final int numArgs = receiverCode.getNumArgs();
                    for (int i = 0; i < numArgs; i++) {
                        final Object value = receiverFrameArguments[i + 4];
                        b.append(zeroBasedStackp == i ? "\t\t\t\t\t\t-> a" : "\t\t\t\t\t\t\ta");
                        b.append(i);
                        b.append("\t");
                        b.append(value);
                        b.append("\n");
                    }
                    final FrameSlot[] stackSlots = receiverCode.getStackSlotsUnsafe();
                    boolean addedSeparator = false;
                    final FrameDescriptor frameDescriptor = receiverCode.getFrameDescriptor();
                    final int initialStackp;
                    if (receiverCode instanceof CompiledBlockObject) {
                        assert ((BlockClosureObject) receiverFrameArguments[2]).getCopied().length == receiverCode.getNumArgsAndCopied() - receiverCode.getNumArgs();
                        initialStackp = receiverCode.getNumArgsAndCopied();
                        for (int i = numArgs; i < initialStackp; i++) {
                            final Object value = receiverFrameArguments[i + 4];
                            b.append(zeroBasedStackp == i ? "\t\t\t\t\t\t-> c" : "\t\t\t\t\t\t\tc");
                            b.append(i);
                            b.append("\t");
                            b.append(value);
                            b.append("\n");
                        }
                    } else {
                        initialStackp = receiverCode.getNumTemps();
                        for (int i = numArgs; i < initialStackp; i++) {
                            final FrameSlot slot = stackSlots[i];
                            Object value = null;
                            if (slot != null && (value = receiverFrame.getValue(slot)) != null) {
                                b.append(zeroBasedStackp == i ? "\t\t\t\t\t\t-> t" : "\t\t\t\t\t\t\tt");
                                b.append(i);
                                b.append("\t");
                                b.append(value);
                                b.append("\n");
                            }
                        }
                    }
                    int j = initialStackp;
                    for (int i = initialStackp; i < stackSlots.length; i++) {
                        final FrameSlot slot = stackSlots[i];
                        Object value = null;
                        if (slot != null && frameDescriptor.getFrameSlotKind(slot) != FrameSlotKind.Illegal && (value = receiverFrame.getValue(slot)) != null) {
                            if (!addedSeparator) {
                                addedSeparator = true;
                                b.append("\t\t\t\t\t\t\t------------------------------------------------\n");
                            }
                            b.append(zeroBasedStackp == i ? "\t\t\t\t\t\t\t->\t" : "\t\t\t\t\t\t\t\t\t");
                            b.append(value);
                            b.append("\n");
                        } else {
                            j = i;
                            if (zeroBasedStackp == i) {
                                if (!addedSeparator) {
                                    addedSeparator = true;
                                    b.append("\t\t\t\t\t\t\t------------------------------------------------\n");
                                }
                                b.append("\t\t\t\t\t\t\t->\tnull\n");
                            }
                            break; // This and all following slots are not in use.
                        }
                    }
                    if (j == 0 && !addedSeparator) {
                        b.deleteCharAt(b.length() - 1);
                        b.append(" is empty\n");
                    } else if (!addedSeparator) {
                        b.append("\t\t\t\t\t\t\t------------------------------------------------\n");
                    }
                }
            }
        }
        b.append("\n\t\t- Stack (including args and temps)\n");
        final int zeroBasedStackp = FrameAccess.getStackPointer(frame, code) - 1;
        final int numArgs = code.getNumArgs();
        for (int i = 0; i < numArgs; i++) {
            final Object value = frameArguments[i + 4];
            b.append(zeroBasedStackp == i ? "\t\t\t\t-> a" : "\t\t\t\t\ta");
            b.append(i);
            b.append("\t");
            b.append(value);
            b.append("\n");
        }
        final FrameSlot[] stackSlots = code.getStackSlotsUnsafe();
        boolean addedSeparator = false;
        final FrameDescriptor frameDescriptor = code.getFrameDescriptor();
        final int initialStackp;
        if (code instanceof CompiledBlockObject) {
            initialStackp = code.getNumArgsAndCopied();
            for (int i = numArgs; i < initialStackp; i++) {
                final Object value = frameArguments[i + 4];
                b.append(zeroBasedStackp == i ? "\t\t\t\t-> c" : "\t\t\t\t\tc");
                b.append(i);
                b.append("\t");
                b.append(value);
                b.append("\n");
            }
        } else {
            initialStackp = code.getNumTemps();
            for (int i = numArgs; i < initialStackp; i++) {
                final FrameSlot slot = stackSlots[i];
                final Object value = frame.getValue(slot);
                b.append(zeroBasedStackp == i ? "\t\t\t\t-> t" : "\t\t\t\t\tt");
                b.append(i);
                b.append("\t");
                b.append(value);
                b.append("\n");
            }
        }
        int j = initialStackp;
        for (int i = initialStackp; i < stackSlots.length; i++) {
            final FrameSlot slot = stackSlots[i];
            Object value = null;
            if (slot != null && frameDescriptor.getFrameSlotKind(slot) != FrameSlotKind.Illegal && (value = frame.getValue(slot)) != null) {
                if (!addedSeparator) {
                    addedSeparator = true;
                    b.append("\t\t\t\t\t------------------------------------------------\n");
                }
                b.append(zeroBasedStackp == i ? "\t\t\t\t\t->\t" : "\t\t\t\t\t\t\t");
                b.append(value);
                b.append("\n");
            } else {
                j = i;
                if (zeroBasedStackp == i) {
                    if (!addedSeparator) {
                        addedSeparator = true;
                        b.append("\t\t\t\t\t------------------------------------------------\n");
                    }
                    b.append("\t\t\t\t\t->\tnull\n");
                }
                break; // This and all following slots are not in use.
            }
        }
        if (j == 0 && !addedSeparator) {
            b.deleteCharAt(b.length() - 1);
            b.append(" is empty\n");
        } else if (!addedSeparator) {
            b.append("\t\t\t\t\t------------------------------------------------\n");
        }
        return b.toString();
    }

}