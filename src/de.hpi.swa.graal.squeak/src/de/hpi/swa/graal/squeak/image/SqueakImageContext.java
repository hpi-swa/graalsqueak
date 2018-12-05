package de.hpi.swa.graal.squeak.image;

import java.io.File;
import java.io.PrintWriter;
import java.math.BigInteger;
import java.nio.file.Paths;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.frame.FrameInstanceVisitor;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.interop.TruffleObject;

import de.hpi.swa.graal.squeak.SqueakLanguage;
import de.hpi.swa.graal.squeak.SqueakOptions;
import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakAbortException;
import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.image.reading.SqueakImageReaderNode;
import de.hpi.swa.graal.squeak.io.DisplayPoint;
import de.hpi.swa.graal.squeak.io.SqueakDisplay;
import de.hpi.swa.graal.squeak.io.SqueakDisplayInterface;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.FloatObject;
import de.hpi.swa.graal.squeak.model.LargeIntegerObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.ASSOCIATION;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.PROCESS;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.ExecuteTopLevelContextNode;
import de.hpi.swa.graal.squeak.nodes.process.GetActiveProcessNode;
import de.hpi.swa.graal.squeak.util.ArrayUtils;
import de.hpi.swa.graal.squeak.util.FrameAccess;
import de.hpi.swa.graal.squeak.util.InterruptHandlerState;
import de.hpi.swa.graal.squeak.util.MiscUtils;
import de.hpi.swa.graal.squeak.util.OSDetector;

public final class SqueakImageContext {
    public final boolean sqFalse = false;
    public final boolean sqTrue = true;
    // Special objects
    public final NilObject nil = new NilObject(this);
    public final ClassObject trueClass = new ClassObject(this);
    public final ClassObject falseClass = new ClassObject(this);
    public final PointersObject schedulerAssociation = new PointersObject(this);
    public final ClassObject bitmapClass = new ClassObject(this);
    public final ClassObject smallIntegerClass = new ClassObject(this);
    public final ClassObject stringClass = new ClassObject(this);
    public final ClassObject arrayClass = new ClassObject(this);
    public final PointersObject smalltalk = new PointersObject(this);
    public final ClassObject floatClass = new ClassObject(this);
    public final ClassObject methodContextClass = new ClassObject(this);
    public final ClassObject pointClass = new ClassObject(this);
    public final ClassObject largePositiveIntegerClass = new ClassObject(this);
    public final ClassObject messageClass = new ClassObject(this);
    public final ClassObject compiledMethodClass = new ClassObject(this);
    public final ClassObject semaphoreClass = new ClassObject(this);
    public final ClassObject characterClass = new ClassObject(this);
    public final NativeObject doesNotUnderstand = new NativeObject(this);
    public final NativeObject mustBeBooleanSelector = new NativeObject(this);
    public final ClassObject byteArrayClass = new ClassObject(this);
    public final ClassObject processClass = new ClassObject(this);
    public final ClassObject blockClosureClass = new ClassObject(this);
    public final ArrayObject externalObjectsArray = new ArrayObject(this);
    public final ClassObject largeNegativeIntegerClass = new ClassObject(this);
    public final NativeObject aboutToReturnSelector = new NativeObject(this);
    public final NativeObject runWithInSelector = new NativeObject(this);
    public final ArrayObject primitiveErrorTable = new ArrayObject(this);
    public final ArrayObject specialSelectors = new ArrayObject(this);
    @CompilationFinal public ClassObject truffleObjectClass = null;

    public final ArrayObject specialObjectsArray = new ArrayObject(this);
    public final ClassObject metaclass = new ClassObject(this);
    public final ClassObject nilClass = new ClassObject(this);

    private final SqueakLanguage language;
    @CompilationFinal private PrintWriter output;
    @CompilationFinal private PrintWriter error;
    @CompilationFinal public SqueakLanguage.Env env;

    // Special selectors
    public final NativeObject plus = new NativeObject(this);
    public final NativeObject minus = new NativeObject(this);
    public final NativeObject lt = new NativeObject(this);
    public final NativeObject gt = new NativeObject(this);
    public final NativeObject le = new NativeObject(this);
    public final NativeObject ge = new NativeObject(this);
    public final NativeObject eq = new NativeObject(this);
    public final NativeObject ne = new NativeObject(this);
    public final NativeObject times = new NativeObject(this);
    public final NativeObject divide = new NativeObject(this);
    public final NativeObject modulo = new NativeObject(this);
    public final NativeObject pointAt = new NativeObject(this);
    public final NativeObject bitShift = new NativeObject(this);
    public final NativeObject floorDivide = new NativeObject(this);
    public final NativeObject bitAnd = new NativeObject(this);
    public final NativeObject bitOr = new NativeObject(this);
    public final NativeObject at = new NativeObject(this);
    public final NativeObject atput = new NativeObject(this);
    public final NativeObject sqSize = new NativeObject(this);
    public final NativeObject next = new NativeObject(this);
    public final NativeObject nextPut = new NativeObject(this);
    public final NativeObject atEnd = new NativeObject(this);
    public final NativeObject equivalent = new NativeObject(this);
    public final NativeObject klass = new NativeObject(this);
    public final NativeObject blockCopy = new NativeObject(this);
    public final NativeObject sqValue = new NativeObject(this);
    public final NativeObject valueWithArg = new NativeObject(this);
    public final NativeObject sqDo = new NativeObject(this);
    public final NativeObject sqNew = new NativeObject(this);
    public final NativeObject newWithArg = new NativeObject(this);
    public final NativeObject x = new NativeObject(this);
    public final NativeObject y = new NativeObject(this);

    @CompilationFinal(dimensions = 1) public final NativeObject[] specialSelectorsArray = new NativeObject[]{
                    plus, minus, lt, gt, le, ge, eq, ne, times, divide, modulo, pointAt, bitShift,
                    floorDivide, bitAnd, bitOr, at, atput, sqSize, next, nextPut, atEnd, equivalent,
                    klass, blockCopy, sqValue, valueWithArg, sqDo, sqNew, newWithArg, x, y
    };

    @CompilationFinal(dimensions = 1) public final int[] specialSelectorsNumArgs = new int[]{
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 0, 0, 1, 0, 1, 0, 1, 0, 1, 1, 0, 1, 0, 0
    };

    @CompilationFinal private String imagePath;
    @CompilationFinal(dimensions = 1) private String[] imageArguments = new String[0];
    @CompilationFinal private boolean isHeadless = true;
    public final SqueakImageFlags flags = new SqueakImageFlags();
    public final OSDetector os = new OSDetector();
    public final InterruptHandlerState interrupt;
    public final long startUpMillis = System.currentTimeMillis();
    private final AllocationReporter allocationReporter;

    @CompilationFinal private SqueakDisplayInterface display;

    @CompilationFinal private ClassObject compilerClass = null;
    @CompilationFinal private CompiledMethodObject evaluateMethod;
    @CompilationFinal private NativeObject simulatePrimitiveArgsSelector = null;
    @CompilationFinal private PointersObject scheduler = null;

    public static final byte[] AS_SYMBOL_SELECTOR_NAME = "asSymbol".getBytes(); // for testing
    @CompilationFinal private NativeObject asSymbolSelector = null; // for testing
    public static final byte[] DEBUG_ERROR_SELECTOR_NAME = "debugError:".getBytes(); // for testing
    @CompilationFinal private NativeObject debugErrorSelector = null; // for testing
    public static final byte[] DEBUG_SYNTAX_ERROR_SELECTOR_NAME = "debugSyntaxError:".getBytes(); // for
                                                                                                  // testing
    @CompilationFinal private NativeObject debugSyntaxErrorSelector = null; // for testing

    public SqueakImageContext(final SqueakLanguage squeakLanguage, final SqueakLanguage.Env environment) {
        language = squeakLanguage;
        patch(environment);
        interrupt = InterruptHandlerState.create(this);
        allocationReporter = env.lookup(AllocationReporter.class);
    }

    public void load() {
        Truffle.getRuntime().createCallTarget(new SqueakImageReaderNode(this)).call();
    }

    public boolean patch(final SqueakLanguage.Env newEnv) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        env = newEnv;
        output = new PrintWriter(env.out(), true);
        error = new PrintWriter(env.err(), true);
        asSymbolSelector = null;
        simulatePrimitiveArgsSelector = null;
        return true;
    }

    public ExecuteTopLevelContextNode getActiveContext() {
        final PointersObject activeProcess = GetActiveProcessNode.create(this).executeGet();
        final ContextObject activeContext = (ContextObject) activeProcess.at0(PROCESS.SUSPENDED_CONTEXT);
        activeProcess.atput0(PROCESS.SUSPENDED_CONTEXT, nil);
        return ExecuteTopLevelContextNode.create(getLanguage(), activeContext, true);
    }

    public ExecuteTopLevelContextNode getCompilerEvaluateContext(final String code) {
        assert compilerClass != null;
        if (evaluateMethod == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            evaluateMethod = (CompiledMethodObject) compilerClass.lookup("evaluate:");
            if (evaluateMethod.getCompiledInSelector() == doesNotUnderstand) {
                throw new SqueakAbortException("Compiler>>#evaluate: could not be found!");
            }
        }
        final ContextObject customContext = ContextObject.create(this, evaluateMethod.sqContextSize());
        customContext.atput0(CONTEXT.METHOD, evaluateMethod);
        customContext.atput0(CONTEXT.INSTRUCTION_POINTER, (long) evaluateMethod.getInitialPC());
        customContext.atput0(CONTEXT.RECEIVER, compilerClass);
        customContext.atput0(CONTEXT.STACKPOINTER, 0L);
        customContext.atput0(CONTEXT.CLOSURE_OR_NIL, nil);
        customContext.setSender(nil);
        customContext.push(wrap(code));
        return ExecuteTopLevelContextNode.create(getLanguage(), customContext, false);
    }

    public PrintWriter getOutput() {
        return output;
    }

    public PrintWriter getError() {
        return error;
    }

    public SqueakLanguage getLanguage() {
        return language;
    }

    public NativeObject getAsSymbolSelector() {
        return asSymbolSelector;
    }

    public void setAsSymbolSelector(final NativeObject asSymbolSelector) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.asSymbolSelector = asSymbolSelector;
    }

    public NativeObject getDebugErrorSelector() {
        return debugErrorSelector;
    }

    public void setDebugErrorSelector(final NativeObject debugErrorSelector) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.debugErrorSelector = debugErrorSelector;
    }

    public NativeObject getDebugSyntaxErrorSelector() {
        return debugSyntaxErrorSelector;
    }

    public void setDebugSyntaxErrorSelector(final NativeObject debugSyntaxErrorSelector) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.debugSyntaxErrorSelector = debugSyntaxErrorSelector;
    }

    public ClassObject getCompilerClass() {
        return compilerClass;
    }

    public void setCompilerClass(final ClassObject compilerClass) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.compilerClass = compilerClass;
    }

    public NativeObject getSimulatePrimitiveArgsSelector() {
        return simulatePrimitiveArgsSelector;
    }

    public void setSimulatePrimitiveArgsSelector(final NativeObject simulatePrimitiveArgsSelector) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.simulatePrimitiveArgsSelector = simulatePrimitiveArgsSelector;
    }

    public ClassObject initializeTruffleObject() {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        truffleObjectClass = new ClassObject(this);
        return truffleObjectClass;
    }

    public boolean supportsTruffleObject() {
        return truffleObjectClass != null;
    }

    public PointersObject getScheduler() {
        if (scheduler == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            scheduler = (PointersObject) schedulerAssociation.at0(ASSOCIATION.VALUE);
        }
        return scheduler;
    }

    public Object wrap(final Object obj) {
        if (obj == null) {
            return nil;
        } else if (obj instanceof Boolean) {
            return wrap((boolean) obj);
        } else if (obj instanceof Integer) {
            return wrap((long) Long.valueOf((Integer) obj));
        } else if (obj instanceof Long) {
            return wrap((long) obj);
        } else if (obj instanceof Double) {
            return wrap((double) obj);
        } else if (obj instanceof BigInteger) {
            return wrap((BigInteger) obj);
        } else if (obj instanceof String) {
            return wrap((String) obj);
        } else if (obj instanceof Character) {
            return wrap((char) obj);
        } else if (obj instanceof byte[]) {
            return wrap((byte[]) obj);
        } else if (obj instanceof Object[]) {
            return wrap((Object[]) obj);
        } else if (obj instanceof DisplayPoint) {
            return wrap((DisplayPoint) obj);
        } else if (obj instanceof TruffleObject) {
            return obj; // never wrap TruffleObjects
        }
        throw new SqueakException("Unsupported value to wrap:", obj);
    }

    public boolean wrap(final boolean value) {
        return value ? sqTrue : sqFalse;
    }

    @SuppressWarnings("static-method")
    public long wrap(final long l) {
        return l;
    }

    public AbstractSqueakObject wrap(final BigInteger i) {
        return new LargeIntegerObject(this, i);
    }

    public FloatObject wrap(final double value) {
        return new FloatObject(this, value);
    }

    @TruffleBoundary
    public NativeObject wrap(final String s) {
        return NativeObject.newNativeBytes(this, stringClass, s.getBytes());
    }

    public NativeObject wrap(final byte[] bytes) {
        return NativeObject.newNativeBytes(this, byteArrayClass, bytes);
    }

    public static char wrap(final char character) {
        return character;
    }

    @TruffleBoundary
    public ArrayObject wrap(final Object... elements) {
        final Object[] wrappedElements = new Object[elements.length];
        for (int i = 0; i < elements.length; i++) {
            wrappedElements[i] = wrap(elements[i]);
        }
        return newList(wrappedElements);
    }

    public PointersObject wrap(final DisplayPoint point) {
        return newPoint((long) point.getWidth(), (long) point.getHeight());
    }

    public ArrayObject newList(final Object elements) {
        return ArrayObject.createWithStorage(this, arrayClass, elements);
    }

    public ArrayObject newListWith(final Object... elements) {
        return newList(elements);
    }

    public PointersObject newPoint(final Object xPos, final Object yPos) {
        return new PointersObject(this, pointClass, new Object[]{xPos, yPos});
    }

    public NativeObject newSymbol(final String value) {
        return NativeObject.newNativeBytes(this, doesNotUnderstand.getSqueakClass(), value.getBytes());
    }

    public void setSemaphore(final long index, final AbstractSqueakObject semaphore) {
        assert semaphore.isSemaphore() || semaphore == nil;
        specialObjectsArray.atput0Object(index, semaphore);
    }

    public boolean hasDisplay() {
        return display != null;
    }

    public SqueakDisplayInterface getDisplay() {
        return display;
    }

    public static boolean isAOT() {
        return TruffleOptions.AOT;
    }

    public String imageRelativeFilePathFor(final String fileName) {
        return getImageDirectory() + File.separator + fileName;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(final String path) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        imagePath = path;
    }

    public String getImageDirectory() {
        return Paths.get(getImagePath()).getParent().getFileName().toString();
    }

    public String[] getImageArguments() {
        return imageArguments;
    }

    public void setImageArguments(final String[] args) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.imageArguments = args;
    }

    public boolean interruptHandlerDisabled() {
        return SqueakOptions.getOption(env, SqueakOptions.DisableInterruptHandler);
    }

    public boolean isHeadless() {
        return isHeadless || SqueakOptions.getOption(env, SqueakOptions.Headless);
    }

    public void disableHeadless() {
        if (isHeadless) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            display = new SqueakDisplay(this);
            isHeadless = false;
        }
    }

    public boolean isTesting() {
        return SqueakOptions.getOption(env, SqueakOptions.Testing);
    }

    public boolean isVerbose() {
        return SqueakOptions.getOption(env, SqueakOptions.Verbose);
    }

    public void printVerbose(final Object... arguments) {
        if (isVerbose()) {
            printToStdOut(arguments);
        }
    }

    @TruffleBoundary
    public void printToStdOut(final Object... arguments) {
        getOutput().println(MiscUtils.format("[graalsqueak] %s", ArrayUtils.toJoinedString(" ", arguments)));
    }

    @TruffleBoundary
    public void printToStdErr(final Object... arguments) {
        getError().println(MiscUtils.format("[graalsqueak] %s", ArrayUtils.toJoinedString(" ", arguments)));
    }

    public void traceProcessSwitches(final Object... arguments) {
        if (SqueakOptions.getOption(env, SqueakOptions.TraceProcessSwitches)) {
            printToStdOut(arguments);
        }
    }

    /*
     * Helper function for debugging purposes.
     */
    @TruffleBoundary
    public void printSqStackTrace() {
        CompilerDirectives.transferToInterpreter();
        final boolean isTravisBuild = System.getenv().containsKey("TRAVIS");
        final int[] depth = new int[1];
        final Object[] lastSender = new Object[]{null};
        getError().println("== Squeak stack trace ===========================================================");
        Truffle.getRuntime().iterateFrames(new FrameInstanceVisitor<Object>() {

            @Override
            public Object visitFrame(final FrameInstance frameInstance) {
                if (depth[0]++ > 50 && isTravisBuild) {
                    return null;
                }
                final Frame current = frameInstance.getFrame(FrameInstance.FrameAccess.READ_ONLY);
                if (!FrameAccess.isGraalSqueakFrame(current)) {
                    return null;
                }
                final Object method = FrameAccess.getMethod(current);
                lastSender[0] = FrameAccess.getSender(current);
                final Object contextOrMarker = FrameAccess.getContextOrMarker(current);
                final Object[] arguments = FrameAccess.getReceiverAndArguments(current);
                final String[] argumentStrings = new String[arguments.length];
                for (int i = 0; i < arguments.length; i++) {
                    argumentStrings[i] = arguments[i].toString();
                }
                final String prefix = FrameAccess.getClosure(current) == null ? "" : "[] in ";
                getError().println(MiscUtils.format("%s%s #(%s) [this: %s, sender: %s]", prefix, method, String.join(", ", argumentStrings), contextOrMarker, lastSender[0]));
                return null;
            }
        });
        getError().println("== " + depth[0] + " Truffle frames ================================================================");
        if (lastSender[0] instanceof ContextObject) {
            ((ContextObject) lastSender[0]).printSqStackTrace();
        }
    }

    public Object getSmalltalkDictionary() {
        return smalltalk; // TODO: turn into TruffleObject with support for keys etc
    }

    public void reportNewAllocationRequest() {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
    }

    public Object reportNewAllocationResult(final Object value) {
        allocationReporter.onReturnValue(value, 0, AllocationReporter.SIZE_UNKNOWN);
        return value;
    }
}