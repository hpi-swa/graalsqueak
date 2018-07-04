package de.hpi.swa.graal.squeak.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.SPECIAL_OBJECT_INDEX;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.GetOrCreateContextNode;
import de.hpi.swa.graal.squeak.nodes.context.frame.FrameSlotWriteNode;
import de.hpi.swa.graal.squeak.nodes.process.SignalSemaphoreNode;

public final class InterruptHandlerNode extends Node {
    private static final int INTERRUPT_CHECKS_EVERY_N_MILLISECONDS = 3;
    private final SqueakImageContext image;
    private final boolean disabled;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final ConditionProfile countingProfile = ConditionProfile.createCountingProfile();
    private final Deque<Integer> semaphoresToSignal = new ArrayDeque<>();

    @Child private GetOrCreateContextNode contextNode = GetOrCreateContextNode.create();
    @Child private FrameSlotWriteNode contextWriteNode = FrameSlotWriteNode.createForContextOrMarker();
    @Child private SignalSemaphoreNode signalSemaporeNode;

    private long nextWakeupTick = 0;
    private boolean interruptPending = false;
    private boolean disabledTemporarily = false;
    private boolean pendingFinalizationSignals = false;
    private volatile boolean shouldTrigger = false;

    public static InterruptHandlerNode create(final SqueakImageContext image, final SqueakConfig config) {
        return new InterruptHandlerNode(image, config);
    }

    protected InterruptHandlerNode(final SqueakImageContext image, final SqueakConfig config) {
        this.image = image;

        disabled = config.disableInterruptHandler();
        if (disabled) {
            image.printToStdOut("Interrupt handler disabled...");
        }
    }

    public void initializeSignalSemaphoreNode(final CompiledCodeObject method) {
        signalSemaporeNode = SignalSemaphoreNode.create(method);
    }

    @TruffleBoundary
    public void start() {
        if (disabled) {
            return;
        }
        executor.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                shouldTrigger = true;
            }
        }, INTERRUPT_CHECKS_EVERY_N_MILLISECONDS, INTERRUPT_CHECKS_EVERY_N_MILLISECONDS, TimeUnit.MILLISECONDS);
    }

    @TruffleBoundary
    public void shutdown() {
        executor.shutdown();
    }

    public void setInterruptPending() {
        interruptPending = true;
    }

    public void setNextWakeupTick(final long msTime) {
        nextWakeupTick = msTime;
    }

    public long getNextWakeupTick() {
        return nextWakeupTick;
    }

    public boolean disabled() {
        return disabledTemporarily;
    }

    public void disable() {
        disabledTemporarily = true;
    }

    public void enable() {
        disabledTemporarily = false;
    }

    public void setPendingFinalizations() {
        pendingFinalizationSignals = true;
    }

    public void sendOrBackwardJumpTrigger(final VirtualFrame frame) {
        if (disabled) {
            return; //
        }
        if (CompilerDirectives.inCompiledCode() && !CompilerDirectives.inCompilationRoot()) {
            return; // do not trigger in inlined code
        }
        if (disabledTemporarily) {
            return;
        }
        if (countingProfile.profile(!shouldTrigger)) {
            return;
        }
        trigger(frame);
    }

    public Object trigger(final VirtualFrame frame) {
        shouldTrigger = false;
        if (interruptPending) {
            interruptPending = false; // reset interrupt flag
            signalSemaporeIfNotNil(frame, SPECIAL_OBJECT_INDEX.TheInterruptSemaphore);
        }
        if ((nextWakeupTick != 0) && (System.currentTimeMillis() >= nextWakeupTick)) {
            nextWakeupTick = 0; // reset timer interrupt
            signalSemaporeIfNotNil(frame, SPECIAL_OBJECT_INDEX.TheTimerSemaphore);
        }
        if (pendingFinalizationSignals) { // signal any pending finalizations
            pendingFinalizationSignals = false;
            signalSemaporeIfNotNil(frame, SPECIAL_OBJECT_INDEX.TheFinalizationSemaphore);
        }
        if (!hasSemaphoresToSignal()) {
            final Object[] semaphores = ((PointersObject) image.specialObjectsArray.at0(SPECIAL_OBJECT_INDEX.ExternalObjectsArray)).getPointers();
            while (!hasSemaphoresToSignal()) {
                final int semaIndex = nextSemaphoreToSignal();
                final Object semaphore = semaphores[semaIndex - 1];
                signalSemaporeIfNotNil(frame, semaphore);
            }
        }
        return null;
    }

    @TruffleBoundary
    private boolean hasSemaphoresToSignal() {
        return semaphoresToSignal.isEmpty();
    }

    @TruffleBoundary
    private Integer nextSemaphoreToSignal() {
        return semaphoresToSignal.removeFirst();
    }

    private void signalSemaporeIfNotNil(final VirtualFrame frame, final int semaphoreIndex) {
        signalSemaporeIfNotNil(frame, image.specialObjectsArray.at0(semaphoreIndex));
    }

    private void signalSemaporeIfNotNil(final VirtualFrame frame, final Object semaphore) {
        signalSemaporeNode.executeSignal(frame, semaphore);
    }

    public static int getInterruptChecksEveryNms() {
        return INTERRUPT_CHECKS_EVERY_N_MILLISECONDS;
    }

    @TruffleBoundary
    public void signalSemaphoreWithIndex(final int index) {
        semaphoresToSignal.addLast(index);
    }

    /*
     * TESTING
     */

    public void reset() {
        CompilerAsserts.neverPartOfCompilation("Resetting interrupt handler only supported for testing purposes");
        nextWakeupTick = 0;
        interruptPending = false;
        disabledTemporarily = false;
        pendingFinalizationSignals = false;
    }
}
