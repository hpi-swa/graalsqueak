/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.util;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.SPECIAL_OBJECT;
import de.hpi.swa.trufflesqueak.nodes.process.SignalSemaphoreNode;

public final class InterruptHandlerNode extends Node {
    @Child private SignalSemaphoreNode signalSemaporeNode;

    private final Object[] specialObjects;
    private final InterruptHandlerState istate;
    private final boolean enableTimerInterrupts;

    private final BranchProfile isActiveProfile = BranchProfile.create();
    private final BranchProfile nextWakeupTickProfile;
    private final BranchProfile pendingFinalizationSignalsProfile = BranchProfile.create();
    private final BranchProfile hasSemaphoresToSignalProfile = BranchProfile.create();

    protected InterruptHandlerNode(final SqueakImageContext image, final boolean enableTimerInterrupts) {
        specialObjects = image.specialObjectsArray.getObjectStorage();
        istate = image.interrupt;
        signalSemaporeNode = SignalSemaphoreNode.create();
        this.enableTimerInterrupts = enableTimerInterrupts;
        nextWakeupTickProfile = enableTimerInterrupts ? BranchProfile.create() : null;
    }

    public static InterruptHandlerNode createOrNull(final boolean enableTimerInterrupts) {
        final SqueakImageContext image = SqueakLanguage.getContext();
        if (image.interruptHandlerDisabled()) {
            return null;
        } else {
            return new InterruptHandlerNode(image, enableTimerInterrupts);
        }
    }

    public void executeTrigger(final VirtualFrame frame) {
        if (CompilerDirectives.inCompiledCode() && !CompilerDirectives.inCompilationRoot() || !istate.isActive()) {
            return;
        }
        isActiveProfile.enter();
        if (istate.interruptPending()) {
            /* Exclude user interrupt case from compilation. */
            CompilerDirectives.transferToInterpreter();
            LogUtils.INTERRUPTS.fine("User interrupt");
            istate.interruptPending = false; // reset interrupt flag
            signalSemaporeNode.executeSignal(frame, istate.getInterruptSemaphore());
        }
        if (enableTimerInterrupts && istate.nextWakeUpTickTrigger()) {
            nextWakeupTickProfile.enter();
            LogUtils.INTERRUPTS.fine("Timer interrupt");
            istate.nextWakeupTick = 0; // reset timer interrupt
            signalSemaporeNode.executeSignal(frame, istate.getTimerSemaphore());
        }
        if (istate.pendingFinalizationSignals()) { // signal any pending finalizations
            pendingFinalizationSignalsProfile.enter();
            LogUtils.INTERRUPTS.fine("Finalization interrupt");
            istate.setPendingFinalizations(false);
            signalSemaporeNode.executeSignal(frame, specialObjects[SPECIAL_OBJECT.THE_FINALIZATION_SEMAPHORE]);
        }
        if (istate.hasSemaphoresToSignal()) {
            hasSemaphoresToSignalProfile.enter();
            LogUtils.INTERRUPTS.fine("Semaphore interrupt");
            final ArrayObject externalObjects = (ArrayObject) specialObjects[SPECIAL_OBJECT.EXTERNAL_OBJECTS_ARRAY];
            if (!externalObjects.isEmptyType()) { // signal external semaphores
                final Object[] semaphores = externalObjects.getObjectStorage();
                Integer semaIndex;
                while ((semaIndex = istate.nextSemaphoreToSignal()) != null) {
                    signalSemaporeNode.executeSignal(frame, semaphores[semaIndex - 1]);
                }
            }
        }
    }
}
