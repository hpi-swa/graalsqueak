/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.tools;

import org.graalvm.polyglot.Context;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.instrumentation.EventContext;
import com.oracle.truffle.api.instrumentation.ExecutionEventListener;
import com.oracle.truffle.api.instrumentation.SourceSectionFilter;
import com.oracle.truffle.api.instrumentation.StandardTags.CallTag;
import com.oracle.truffle.api.instrumentation.TruffleInstrument;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.SqueakOptions;

@TruffleInstrument.Registration(id = SqueakMessageInterceptor.ID, services = SqueakMessageInterceptor.class)
public final class SqueakMessageInterceptor extends TruffleInstrument {
    public static final String ID = "squeak-message-interceptor";
    private static final String DEFAULTS = "TestCase>>runCase,TestCase>>logFailure:,TestCase>>signalFailure:,Object>>halt,Object>>inform:," + //
                    "SmalltalkImage>>logSqueakError:inContext:,UnhandledError>>defaultAction,SyntaxErrorNotification>>setClass:code:doitFlag:errorMessage:location:";

    private static String[] breakpoints = null;

    public static void enableIfRequested(final SqueakLanguage.Env env) {
        if (env.getOptions().hasBeenSet(SqueakOptions.InterceptMessages)) {
            /* Looking up instrument to activate it. */
            breakpoints = (DEFAULTS + "," + env.getOptions().get(SqueakOptions.InterceptMessages)).split(",");
            Context.getCurrent().getEngine().getInstruments().get(ID).lookup(SqueakMessageInterceptor.class);
        }
    }

    @Override
    protected void onCreate(final Env env) {
        assert breakpoints != null;
        env.registerService(this);
        env.getInstrumenter().attachExecutionEventListener(
                        SourceSectionFilter.newBuilder().tagIs(CallTag.class).rootNameIs(s -> {
                            if (s == null) {
                                return false;
                            }
                            for (final String breakpoint : breakpoints) {
                                if (s.contains(breakpoint)) {
                                    return true;
                                }
                            }
                            return false;
                        }).build(),
                        new ExecutionEventListener() {

                            /*
                             * This is the "halt" method - put a breakpoint in your IDE on the print
                             * statement within.
                             */
                            @Override
                            public void onEnter(final EventContext context, final VirtualFrame frame) {
                                printToStdOut(context);
                            }

                            @TruffleBoundary
                            private void printToStdOut(final EventContext context) {
                                // Checkstyle: stop
                                System.out.println("Entering " + context.getInstrumentedSourceSection().getSource().getName() + "...");
                                // Checkstyle: resume
                            }

                            @Override
                            public void onReturnValue(final EventContext context, final VirtualFrame frame, final Object result) {
                                /* Nothing to do. */
                            }

                            @Override
                            public void onReturnExceptional(final EventContext context, final VirtualFrame frame, final Throwable exception) {
                                /* Nothing to do. */
                            }
                        });
    }
}
