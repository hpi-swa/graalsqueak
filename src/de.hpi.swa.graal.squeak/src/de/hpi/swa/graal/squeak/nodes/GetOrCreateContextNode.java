/*
 * Copyright (c) 2017-2019 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.graal.squeak.nodes;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.ContextObject;

public abstract class GetOrCreateContextNode extends AbstractNodeWithCode {

    protected GetOrCreateContextNode(final CompiledCodeObject code) {
        super(code);
    }

    public static GetOrCreateContextNode create(final CompiledCodeObject code) {
        return GetOrCreateContextNodeGen.create(code);
    }

    public abstract ContextObject executeGet(Frame frame);

    @Specialization(guards = {"isVirtualized(frame)"})
    protected final ContextObject doCreate(final VirtualFrame frame) {
        return ContextObject.create(frame.materialize(), code);
    }

    @Fallback
    protected final ContextObject doGet(final VirtualFrame frame) {
        return getContext(frame);
    }
}
