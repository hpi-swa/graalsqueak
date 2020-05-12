/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.util;

import java.io.PrintWriter;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameSlot;
import com.oracle.truffle.api.frame.FrameSlotKind;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.AbstractPointersObject;
import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.BlockClosureObject;
import de.hpi.swa.trufflesqueak.model.CompiledBlockObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.CompiledMethodObject;
import de.hpi.swa.trufflesqueak.model.ContextObject;
import de.hpi.swa.trufflesqueak.model.FrameMarker;
import de.hpi.swa.trufflesqueak.model.NilObject;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.LINKED_LIST;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.PROCESS;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.PROCESS_SCHEDULER;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.SEMAPHORE;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.SPECIAL_OBJECT;

/**
 * Helper functions for debugging purposes.
 */
public final class DebugUtils {
    public static final boolean UNDER_DEBUG = ManagementFactory.getRuntimeMXBean().getInputArguments().toString().indexOf("-agentlib:jdwp") > 0;

    public static void dumpState() {
        CompilerAsserts.neverPartOfCompilation("For debugging purposes only");
        MiscUtils.systemGC();
        final StringBuilder sb = new StringBuilder("Thread dump");
        dumpThreads(sb);
        println(sb.toString());
        println(currentState());
    }

    public static void dumpThreads(final StringBuilder sb) {
        CompilerAsserts.neverPartOfCompilation("For debugging purposes only");
        sb.append("\r\n\r\n\r\n");
        sb.append("Total number of threads started: ");
        sb.append(ManagementFactory.getThreadMXBean().getTotalStartedThreadCount());
        sb.append("\r\n\r\n");

        final Runtime r = Runtime.getRuntime();
        sb.append("Total Memory : ");
        sb.append(r.totalMemory());
        sb.append("\r\n");
        sb.append("Max Memory   : ");
        sb.append(r.maxMemory());
        sb.append("\r\n");
        sb.append("Free Memory  : ");
        sb.append(r.freeMemory());
        sb.append("\r\n\r\n");

        final ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
        for (final ThreadInfo info : threads) {
            sb.append("\"" + info.getThreadName() + "\" Id=" + info.getThreadId() + " " + info.getThreadState());
            if (info.getLockName() != null) {
                sb.append(" on " + info.getLockName());
            }
            if (info.getLockOwnerName() != null) {
                sb.append(" owned by \"" + info.getLockOwnerName() + "\" Id=" + info.getLockOwnerId());
            }
            if (info.isSuspended()) {
                sb.append(" (suspended)");
            }
            if (info.isInNative()) {
                sb.append(" (in native)");
            }
            sb.append("\r\n");
            int i = 0;
            for (; i < info.getStackTrace().length; i++) {
                final StackTraceElement ste = info.getStackTrace()[i];
                sb.append("\tat " + ste.toString());
                sb.append("\r\n");
                if (i == 0 && info.getLockInfo() != null) {
                    final Thread.State ts = info.getThreadState();
                    switch (ts) {
                        case BLOCKED:
                            sb.append("\t-  blocked on " + info.getLockInfo());
                            sb.append("\r\n");
                            break;
                        case WAITING:
                            sb.append("\t-  waiting on " + info.getLockInfo());
                            sb.append("\r\n");
                            break;
                        case TIMED_WAITING:
                            sb.append("\t-  waiting on " + info.getLockInfo());
                            sb.append("\r\n");
                            break;
                        default:
                    }
                }

                for (final MonitorInfo mi : info.getLockedMonitors()) {
                    if (mi.getLockedStackDepth() == i) {
                        sb.append("\t-  locked " + mi);
                        sb.append("\r\n");
                    }
                }
            }
            if (i < info.getStackTrace().length) {
                sb.append("\t...");
                sb.append("\r\n");
            }

            final LockInfo[] locks = info.getLockedSynchronizers();
            if (locks.length > 0) {
                sb.append("\r\n\tNumber of locked synchronizers = " + locks.length);
                sb.append("\r\n");
                for (final LockInfo li : locks) {
                    sb.append("\t- " + li);
                    sb.append("\r\n");
                }
            }

            sb.append("\r\n\r\n");
        }
    }

    public static String currentState() {
        CompilerAsserts.neverPartOfCompilation("For debugging purposes only");
        final SqueakImageContext image = SqueakLanguage.getContext();
        final StringBuilder b = new StringBuilder();
        b.append("\nImage processes state\n");
        final PointersObject activeProcess = image.getActiveProcessSlow();
        final long activePriority = (long) activeProcess.instVarAt0Slow(PROCESS.PRIORITY);
        b.append("*Active process @");
        b.append(Integer.toHexString(activeProcess.hashCode()));
        b.append(" priority ");
        b.append(activePriority);
        b.append('\n');
        final Object interruptSema = image.getSpecialObject(SPECIAL_OBJECT.THE_INTERRUPT_SEMAPHORE);
        printSemaphoreOrNil(b, "*Interrupt semaphore @", interruptSema, true);
        final Object timerSema = image.getSpecialObject(SPECIAL_OBJECT.THE_TIMER_SEMAPHORE);
        printSemaphoreOrNil(b, "*Timer semaphore @", timerSema, true);
        final Object finalizationSema = image.getSpecialObject(SPECIAL_OBJECT.THE_FINALIZATION_SEMAPHORE);
        printSemaphoreOrNil(b, "*Finalization semaphore @", finalizationSema, true);
        final Object lowSpaceSema = image.getSpecialObject(SPECIAL_OBJECT.THE_LOW_SPACE_SEMAPHORE);
        printSemaphoreOrNil(b, "*Low space semaphore @", lowSpaceSema, true);
        final ArrayObject externalObjects = (ArrayObject) image.getSpecialObject(SPECIAL_OBJECT.EXTERNAL_OBJECTS_ARRAY);
        if (!externalObjects.isEmptyType()) {
            final Object[] semaphores = externalObjects.getObjectStorage();
            for (int i = 0; i < semaphores.length; i++) {
                printSemaphoreOrNil(b, "*External semaphore at index " + (i + 1) + " @", semaphores[i], false);
            }
        }
        final Object[] lists = ((ArrayObject) image.getScheduler().instVarAt0Slow(PROCESS_SCHEDULER.PROCESS_LISTS)).getObjectStorage();
        for (int i = 0; i < lists.length; i++) {
            printLinkedList(b, "*Quiescent processes list at priority " + (i + 1), (PointersObject) lists[i]);
        }
        return b.toString();
    }

    public static void printSqStackTrace() {
        CompilerAsserts.neverPartOfCompilation("For debugging purposes only");
        final boolean isCIBuild = System.getenv().containsKey("GITHUB_ACTIONS");
        final int[] depth = new int[1];
        final Object[] lastSender = new Object[]{null};
        final PrintWriter err = SqueakLanguage.getContext().getError();
        err.println("== Truffle stack trace ===========================================================");
        Truffle.getRuntime().iterateFrames(frameInstance -> {
            if (depth[0]++ > 50 && isCIBuild) {
                return null;
            }
            final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
            if (!FrameAccess.isTruffleSqueakFrame(current)) {
                return null;
            }
            final CompiledMethodObject method = FrameAccess.getMethod(current);
            lastSender[0] = FrameAccess.getSender(current);
            final Object marker = FrameAccess.getMarker(current, method);
            final Object context = FrameAccess.getContext(current, method);
            final String prefix = FrameAccess.getClosure(current) == null ? "" : "[] in ";
            final String argumentsString = ArrayUtils.toJoinedString(", ", FrameAccess.getReceiverAndArguments(current));
            err.println(MiscUtils.format("%s%s #(%s) [marker: %s, context: %s, sender: %s]", prefix, method, argumentsString, marker, context, lastSender[0]));
            return null;
        });
        if (lastSender[0] instanceof ContextObject) {
            err.println("== Squeak frames ================================================================");
            printSqStackTrace((ContextObject) lastSender[0]);
        }
    }

    public static void printSqStackTrace(final ContextObject context) {
        CompilerAsserts.neverPartOfCompilation("For debugging purposes only");
        final StringBuilder b = new StringBuilder();
        printSqMaterializedStackTraceOn(b, context);
        SqueakLanguage.getContext().getOutput().println(b.toString());
    }

    public static String stackFor(final VirtualFrame frame, final CompiledCodeObject code) {
        CompilerAsserts.neverPartOfCompilation("For debugging purposes only");
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

    private static void printSemaphoreOrNil(final StringBuilder b, final String label, final Object semaphoreOrNil, final boolean printIfNil) {
        if (semaphoreOrNil instanceof PointersObject) {
            b.append(label);
            b.append(Integer.toHexString(semaphoreOrNil.hashCode()));
            b.append(" with ");
            b.append(((AbstractPointersObject) semaphoreOrNil).instVarAt0Slow(SEMAPHORE.EXCESS_SIGNALS));
            b.append(" excess signals");
            if (!printLinkedList(b, "", (PointersObject) semaphoreOrNil)) {
                b.append(" and no processes\n");
            }
        } else {
            if (printIfNil) {
                b.append(label);
                b.append(" is nil\n");
            }
        }
    }

    private static boolean printLinkedList(final StringBuilder b, final String label, final PointersObject linkedList) {
        Object temp = linkedList.instVarAt0Slow(LINKED_LIST.FIRST_LINK);
        if (temp instanceof PointersObject) {
            b.append(label);
            b.append(" and process");
            if (temp != linkedList.instVarAt0Slow(LINKED_LIST.LAST_LINK)) {
                b.append("es:\n");
            } else {
                b.append(":\n");
            }
            while (temp instanceof PointersObject) {
                final PointersObject aProcess = (PointersObject) temp;
                final Object aContext = aProcess.instVarAt0Slow(PROCESS.SUSPENDED_CONTEXT);
                if (aContext instanceof ContextObject) {
                    assert ((ContextObject) aContext).getProcess() == null || ((ContextObject) aContext).getProcess() == aProcess;
                    b.append("\tprocess @");
                    b.append(Integer.toHexString(aProcess.hashCode()));
                    b.append(" with suspended context ");
                    b.append(aContext);
                    b.append(" and stack trace:\n");
                    printSqMaterializedStackTraceOn(b, (ContextObject) aContext);
                } else {
                    b.append("\tprocess @");
                    b.append(Integer.toHexString(aProcess.hashCode()));
                    b.append(" with suspended context nil\n");
                }
                temp = aProcess.instVarAt0Slow(PROCESS.NEXT_LINK);
            }
            return true;
        } else {
            return false;
        }
    }

    private static void printSqMaterializedStackTraceOn(final StringBuilder b, final ContextObject context) {
        ContextObject current = context;
        while (current != null && current.hasTruffleFrame()) {
            final Object[] rcvrAndArgs = current.getReceiverAndNArguments();
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

    private static void println(final Object object) {
        // Checkstyle: stop
        System.out.println(object);
        // Checkstyle: resume
    }
}
