package de.hpi.swa.graal.squeak.exceptions;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.ControlFlowException;

import de.hpi.swa.graal.squeak.model.ContextObject;

public final class ProcessSwitch extends ControlFlowException {
    private static final long serialVersionUID = 1L;
    private final ContextObject newContext;

    public ProcessSwitch(final ContextObject newContext) {
        this.newContext = newContext;
    }

    public ContextObject getNewContext() {
        return newContext;
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return "Process switch to " + newContext;
    }
}
