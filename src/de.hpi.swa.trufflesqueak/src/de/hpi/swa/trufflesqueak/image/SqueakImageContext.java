/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.image;

import java.io.File;
import java.io.PrintWriter;
import java.lang.ref.ReferenceQueue;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.instrumentation.AllocationReporter;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;

import de.hpi.swa.trufflesqueak.SqueakImage;
import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.SqueakOptions.SqueakContextOptions;
import de.hpi.swa.trufflesqueak.exceptions.ProcessSwitch;
import de.hpi.swa.trufflesqueak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.trufflesqueak.exceptions.SqueakExceptions.SqueakSyntaxError;
import de.hpi.swa.trufflesqueak.interop.InteropMap;
import de.hpi.swa.trufflesqueak.interop.LookupMethodByStringNode;
import de.hpi.swa.trufflesqueak.io.DisplayPoint;
import de.hpi.swa.trufflesqueak.io.SqueakDisplay;
import de.hpi.swa.trufflesqueak.io.SqueakDisplayInterface;
import de.hpi.swa.trufflesqueak.model.AbstractSqueakObject;
import de.hpi.swa.trufflesqueak.model.AbstractSqueakObjectWithClassAndHash;
import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.BlockClosureObject;
import de.hpi.swa.trufflesqueak.model.BooleanObject;
import de.hpi.swa.trufflesqueak.model.ClassObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.CompiledMethodObject;
import de.hpi.swa.trufflesqueak.model.ContextObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.model.NilObject;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.ASSOCIATION;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.ENVIRONMENT;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.MESSAGE;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.PROCESS;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.PROCESS_SCHEDULER;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.SMALLTALK_IMAGE;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts.SPECIAL_OBJECT;
import de.hpi.swa.trufflesqueak.model.layout.SlotLocation;
import de.hpi.swa.trufflesqueak.nodes.ExecuteTopLevelContextNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectReadNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;
import de.hpi.swa.trufflesqueak.nodes.plugins.B2D;
import de.hpi.swa.trufflesqueak.nodes.plugins.BitBlt;
import de.hpi.swa.trufflesqueak.nodes.plugins.JPEGReader;
import de.hpi.swa.trufflesqueak.nodes.plugins.SqueakSSL.SqSSL;
import de.hpi.swa.trufflesqueak.nodes.plugins.Zip;
import de.hpi.swa.trufflesqueak.nodes.plugins.network.SqueakSocket;
import de.hpi.swa.trufflesqueak.shared.SqueakImageLocator;
import de.hpi.swa.trufflesqueak.shared.SqueakLanguageConfig;
import de.hpi.swa.trufflesqueak.tools.SqueakMessageInterceptor;
import de.hpi.swa.trufflesqueak.util.ArrayUtils;
import de.hpi.swa.trufflesqueak.util.InterruptHandlerState;
import de.hpi.swa.trufflesqueak.util.MiscUtils;

public final class SqueakImageContext {
    /* Special objects */
    public final ClassObject trueClass = new ClassObject(this);
    public final ClassObject falseClass = new ClassObject(this);
    public final PointersObject schedulerAssociation = new PointersObject(this);
    public final ClassObject bitmapClass = new ClassObject(this);
    public final ClassObject smallIntegerClass = new ClassObject(this);
    public final ClassObject byteStringClass = new ClassObject(this);
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
    public final NativeObject cannotReturn = new NativeObject(this);
    public final NativeObject mustBeBooleanSelector = new NativeObject(this);
    public final ClassObject byteArrayClass = new ClassObject(this);
    public final ClassObject processClass = new ClassObject(this);
    public final ClassObject blockClosureClass = new ClassObject(this);
    public final ClassObject largeNegativeIntegerClass = new ClassObject(this);
    public final NativeObject aboutToReturnSelector = new NativeObject(this);
    public final NativeObject runWithInSelector = new NativeObject(this);
    public final ArrayObject primitiveErrorTable = new ArrayObject(this);
    public final ArrayObject specialSelectors = new ArrayObject(this);
    @CompilationFinal private ClassObject smallFloatClass = null;
    @CompilationFinal private ClassObject byteSymbolClass = null;
    @CompilationFinal private ClassObject foreignObjectClass = null;

    public final ArrayObject specialObjectsArray = new ArrayObject(this);
    public final ClassObject metaClass = new ClassObject(this);
    public final ClassObject nilClass = new ClassObject(this);

    public final CompiledMethodObject dummyMethod = new CompiledMethodObject(this, null, new Object[]{CompiledCodeObject.makeHeader(1, 0, 0, false, true)});

    /* System Information */
    public final SqueakImageFlags flags = new SqueakImageFlags();
    private String imagePath;
    private final TruffleFile homePath;
    @CompilationFinal private boolean isHeadless;
    public final SqueakContextOptions options;

    /* System */
    public NativeObject clipboardTextHeadless = asByteString("");
    private boolean currentMarkingFlag;
    private ArrayObject hiddenRoots;
    private long globalClassCounter = -1;
    @CompilationFinal private SqueakDisplayInterface display;
    public final InterruptHandlerState interrupt;
    public final long startUpMillis = System.currentTimeMillis();
    public final ReferenceQueue<Object> weakPointersQueue = new ReferenceQueue<>();

    /* Truffle */
    private final AllocationReporter allocationReporter;
    @CompilationFinal public SqueakLanguage.Env env;
    private final SqueakLanguage language;
    private Source lastParseRequestSource;
    @CompilationFinal private PrintWriter output;
    @CompilationFinal private PrintWriter error;

    @CompilationFinal private SqueakImage squeakImage;

    /* Stack Management */
    public int stackDepth = 0;
    public ContextObject lastSeenContext;

    @CompilationFinal private ClassObject compilerClass = null;
    @CompilationFinal private ClassObject parserClass = null;
    private PointersObject parserSharedInstance = null;
    @CompilationFinal private PointersObject scheduler = null;
    @CompilationFinal private ClassObject wideStringClass = null;

    /* Plugins */
    public final B2D b2d = new B2D(this);
    public final BitBlt bitblt = new BitBlt(this);
    public String[] dropPluginFileList = new String[0];
    public final EconomicMap<Long, SeekableByteChannel> filePluginHandles = EconomicMap.create();
    public final JPEGReader jpegReader = new JPEGReader();
    public final EconomicMap<Long, SqueakSocket> socketPluginHandles = EconomicMap.create();
    public final EconomicMap<Long, SqSSL> squeakSSLHandles = EconomicMap.create();
    public final Zip zip = new Zip();

    /* Error detection for headless execution */
    @CompilationFinal(dimensions = 1) public static final byte[] DEBUG_ERROR_SELECTOR_NAME = "debugError:".getBytes();
    @CompilationFinal private NativeObject debugErrorSelector = null;
    @CompilationFinal(dimensions = 1) public static final byte[] DEBUG_SYNTAX_ERROR_SELECTOR_NAME = "debugSyntaxError:".getBytes();
    @CompilationFinal private NativeObject debugSyntaxErrorSelector = null;

    public SqueakImageContext(final SqueakLanguage squeakLanguage, final SqueakLanguage.Env environment) {
        language = squeakLanguage;
        patch(environment);
        options = new SqueakContextOptions(env);
        isHeadless = options.isHeadless;
        interrupt = InterruptHandlerState.create(this);
        allocationReporter = env.lookup(AllocationReporter.class);
        SqueakMessageInterceptor.enableIfRequested(environment);
        homePath = env.getInternalTruffleFile(language.getTruffleLanguageHome());
        assert homePath.exists() : "Home directory does not exist: " + homePath;
    }

    public void ensureLoaded() {
        if (!loaded()) {
            // Load image.
            SqueakImageReader.load(this);
            if (options.disableStartup) {
                printToStdOut("Skipping startup routine...");
                return;
            }
            printToStdOut("Preparing image for headless execution...");
            // Remove active context.
            getActiveProcessSlow().instVarAtPut0Slow(PROCESS.SUSPENDED_CONTEXT, NilObject.SINGLETON);
            // Modify StartUpList for headless execution.
            evaluate("{EventSensor. Project} do: [:ea | Smalltalk removeFromStartUpList: ea]");
            try {
                /** See SmalltalkImage>>#snapshot:andQuit:withExitCode:embedded:. */
                evaluate("[Smalltalk clearExternalObjects. Smalltalk processStartUpList: true. Smalltalk setPlatformPreferences] value");
            } catch (final Exception e) {
                printToStdErr("startUpList failed:");
                e.printStackTrace();
            }
            // Set author information.
            evaluate("Utilities authorName: 'TruffleSqueak'");
            evaluate("Utilities setAuthorInitials: 'TruffleSqueak'");
            // Initialize fresh MorphicUIManager.
            evaluate("Project current instVarNamed: #uiManager put: MorphicUIManager new");
        }
    }

    /**
     * Returns `true` if image has been loaded. {@link SqueakImageReader} calls
     * {@link #getSqueakImage()} and initializes `squeakImage`.
     */
    public boolean loaded() {
        return squeakImage != null;
    }

    public SqueakImage getSqueakImage() {
        if (squeakImage == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            squeakImage = new SqueakImage(this);
        }
        return squeakImage;
    }

    @TruffleBoundary
    public Object evaluate(final String sourceCode) {
        final Source source = Source.newBuilder(SqueakLanguageConfig.NAME, sourceCode, "<image#evaluate>").build();
        return Truffle.getRuntime().createCallTarget(getDoItContextNode(source)).call();
    }

    public Object lookup(final String member) {
        final Object symbol = getCompilerClass().send("evaluate:", asByteString("'" + member + "' asSymbol"));
        return smalltalk.send("at:ifAbsent:", symbol, NilObject.SINGLETON);
    }

    public boolean patch(final SqueakLanguage.Env newEnv) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        env = newEnv;
        output = new PrintWriter(env.out(), true);
        error = new PrintWriter(env.err(), true);
        return true;
    }

    @TruffleBoundary
    public ExecuteTopLevelContextNode getActiveContextNode() {
        final PointersObject activeProcess = getActiveProcessSlow();
        final ContextObject activeContext = (ContextObject) activeProcess.instVarAt0Slow(PROCESS.SUSPENDED_CONTEXT);
        activeContext.setProcess(activeProcess);
        activeProcess.instVarAtPut0Slow(PROCESS.SUSPENDED_CONTEXT, NilObject.SINGLETON);
        return ExecuteTopLevelContextNode.create(getLanguage(), activeContext, true);
    }

    @TruffleBoundary
    public ExecuteTopLevelContextNode getDoItContextNode(final Source source) {
        lastParseRequestSource = source;
        return getDoItContextNode(source.getCharacters().toString());
    }

    @TruffleBoundary
    public ExecuteTopLevelContextNode getDoItContextNode(final String source) {
        /*
         * (Parser new parse: '1 + 2 * 3' class: UndefinedObject noPattern: true notifying: nil
         * ifFail: [^nil]) generate
         */
        assert parserClass != null;
        assert compilerClass != null;

        if (parserSharedInstance == null) {
            parserSharedInstance = (PointersObject) parserClass.send("new");
        }
        final PointersObject methodNode;
        try {
            methodNode = (PointersObject) parserSharedInstance.send("parse:class:noPattern:notifying:ifFail:",
                            asByteString(source), nilClass, BooleanObject.TRUE, NilObject.SINGLETON, BlockClosureObject.create(this, 0));
        } catch (final ProcessSwitch e) {
            /*
             * A ProcessSwitch exception is thrown in case of a syntax error to open the
             * corresponding window. Fail with an appropriate exception here. This way, it is clear
             * why code execution failed (e.g. when requested through the Polyglot API).
             */
            CompilerDirectives.transferToInterpreter();
            throw new SqueakSyntaxError("Syntax Error in \"" + source + "\"");
        }
        final CompiledMethodObject doItMethod = (CompiledMethodObject) methodNode.send("generate");

        final ContextObject doItContext = ContextObject.create(this, doItMethod.getSqueakContextSize());
        doItContext.setReceiver(NilObject.SINGLETON);
        doItContext.setMethod(doItMethod);
        doItContext.setInstructionPointer(doItMethod.getInitialPC());
        doItContext.setStackPointer(doItMethod.getNumTemps());
        doItContext.removeSender();
        doItContext.setProcess(getActiveProcessSlow());
        return ExecuteTopLevelContextNode.create(getLanguage(), doItContext, false);
    }

    /*
     * ACCESSING
     */

    public PrintWriter getOutput() {
        return output;
    }

    public PrintWriter getError() {
        return error;
    }

    public SqueakLanguage getLanguage() {
        return language;
    }

    public boolean getCurrentMarkingFlag() {
        return currentMarkingFlag;
    }

    public boolean toggleCurrentMarkingFlag() {
        return currentMarkingFlag = !currentMarkingFlag;
    }

    public ArrayObject getHiddenRoots() {
        return hiddenRoots;
    }

    public TruffleFile getHomePath() {
        return homePath;
    }

    public long getGlobalClassCounter() {
        return globalClassCounter;
    }

    public void setGlobalClassCounter(final long newValue) {
        assert globalClassCounter < 0 : "globalClassCounter should only be set once";
        globalClassCounter = newValue;
    }

    public long getNextClassHash() {
        return ++globalClassCounter;
    }

    public NativeObject getDebugErrorSelector() {
        return debugErrorSelector;
    }

    public void setDebugErrorSelector(final NativeObject nativeObject) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert debugErrorSelector == null;
        debugErrorSelector = nativeObject;
    }

    public NativeObject getDebugSyntaxErrorSelector() {
        return debugSyntaxErrorSelector;
    }

    public void setDebugSyntaxErrorSelector(final NativeObject nativeObject) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert debugSyntaxErrorSelector == null;
        debugSyntaxErrorSelector = nativeObject;
    }

    public ClassObject getCompilerClass() {
        return compilerClass;
    }

    public void setCompilerClass(final ClassObject classObject) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert compilerClass == null;
        compilerClass = classObject;
    }

    public ClassObject getParserClass() {
        return parserClass;
    }

    public void setParserClass(final ClassObject classObject) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert parserClass == null;
        parserClass = classObject;
    }

    public ClassObject getSmallFloatClass() {
        return smallFloatClass;
    }

    public void setSmallFloatClass(final ClassObject classObject) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert smallFloatClass == null;
        smallFloatClass = classObject;
    }

    public ClassObject getByteSymbolClass() {
        return byteSymbolClass;
    }

    public void setByteSymbolClass(final ClassObject classObject) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        assert byteSymbolClass == null;
        byteSymbolClass = classObject;
    }

    public ClassObject getWideStringClass() {
        if (wideStringClass == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            // TODO: find a better way to find wideStringClass or do this on image side instead?
            final CompiledMethodObject method = (CompiledMethodObject) LookupMethodByStringNode.getUncached().executeLookup(byteArrayClass, "asWideString");
            if (method != null) {
                final PointersObject assoc = (PointersObject) method.getLiteral(1);
                wideStringClass = (ClassObject) assoc.instVarAt0Slow(ASSOCIATION.VALUE);
            } else {
                /* Image only uses a single String class (e.g. Cuis 5.0). */
                wideStringClass = byteStringClass;
            }
        }
        return wideStringClass;
    }

    public static void initializeBeforeLoadingImage() {
        SlotLocation.initialize();
    }

    public void initializeAfterLoadingImage(final ArrayObject theHiddenRoots) {
        assert hiddenRoots == null;
        hiddenRoots = theHiddenRoots;
    }

    public ClassObject getForeignObjectClass() {
        assert foreignObjectClass != null;
        return foreignObjectClass;
    }

    public boolean setForeignObjectClass(final ClassObject classObject) {
        if (foreignObjectClass == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            foreignObjectClass = classObject;
            return true;
        } else {
            return false;
        }
    }

    public boolean supportsNFI() {
        CompilerAsserts.neverPartOfCompilation();
        return env.getInternalLanguages().containsKey("nfi");
    }

    public PointersObject getScheduler() {
        if (scheduler == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            scheduler = (PointersObject) schedulerAssociation.instVarAt0Slow(ASSOCIATION.VALUE);
        }
        return scheduler;
    }

    public PointersObject getActiveProcessSlow() {
        return getActiveProcess(AbstractPointersObjectReadNode.getUncached());
    }

    public PointersObject getActiveProcess(final AbstractPointersObjectReadNode pointersReadNode) {
        return pointersReadNode.executePointers(getScheduler(), PROCESS_SCHEDULER.ACTIVE_PROCESS);
    }

    public Object getSpecialObject(final int index) {
        return specialObjectsArray.getObjectStorage()[index];
    }

    public void setSpecialObject(final int index, final Object value) {
        specialObjectsArray.getObjectStorage()[index] = value;
    }

    private ArrayObject getSpecialSelectors() {
        return (ArrayObject) getSpecialObject(SPECIAL_OBJECT.SPECIAL_SELECTORS);
    }

    public NativeObject getSpecialSelector(final int index) {
        return (NativeObject) getSpecialSelectors().getObjectStorage()[index * 2];
    }

    public int getSpecialSelectorNumArgs(final int index) {
        return (int) (long) getSpecialSelectors().getObjectStorage()[index * 2 + 1];
    }

    public void setSemaphore(final int index, final AbstractSqueakObject semaphore) {
        assert semaphore == NilObject.SINGLETON || ((AbstractSqueakObjectWithClassAndHash) semaphore).getSqueakClass().isSemaphoreClass();
        setSpecialObject(index, semaphore);
    }

    public boolean hasDisplay() {
        return display != null;
    }

    public SqueakDisplayInterface getDisplay() {
        return display;
    }

    public String imageRelativeFilePathFor(final String fileName) {
        return getImageDirectory() + File.separator + fileName;
    }

    public String getImagePath() {
        if (imagePath == null) {
            CompilerDirectives.transferToInterpreter();
            setImagePath(options.imagePath.isEmpty() ? SqueakImageLocator.findImage() : options.imagePath);
        }
        return imagePath;
    }

    public void setImagePath(final String path) {
        imagePath = path;
    }

    @TruffleBoundary
    public String getImageDirectory() {
        final Path parent = Paths.get(getImagePath()).getParent();
        if (parent != null) {
            return "" + parent.getFileName(); // Avoids NullPointerExceptions.
        } else {
            throw SqueakException.create("`parent` should not be `null`.");
        }
    }

    public String[] getImageArguments() {
        if (options.imageArguments.length > 0) {
            return options.imageArguments;
        } else {
            return env.getApplicationArguments();
        }
    }

    public Source getLastParseRequestSource() {
        return lastParseRequestSource;
    }

    public boolean interruptHandlerDisabled() {
        return options.disableInterruptHandler;
    }

    public boolean isHeadless() {
        return isHeadless;
    }

    public void attachDisplayIfNecessary() {
        if (!isHeadless) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            display = new SqueakDisplay(this);
        }
    }

    public boolean isTesting() {
        return options.isTesting;
    }

    public Object getGlobals() {
        final PointersObject environment = (PointersObject) smalltalk.instVarAt0Slow(SMALLTALK_IMAGE.GLOBALS);
        final PointersObject bindings = (PointersObject) environment.instVarAt0Slow(ENVIRONMENT.BINDINGS);
        return new InteropMap(bindings);
    }

    /*
     * INSTANCE CREATION
     */

    public ArrayObject asArrayOfLongs(final long... elements) {
        return ArrayObject.createWithStorage(this, arrayClass, elements);
    }

    public ArrayObject asArrayOfObjects(final Object... elements) {
        return ArrayObject.createWithStorage(this, arrayClass, elements);
    }

    public NativeObject asByteArray(final byte[] bytes) {
        return NativeObject.newNativeBytes(this, byteArrayClass, bytes);
    }

    public NativeObject asByteString(final String value) {
        return NativeObject.newNativeBytes(this, byteStringClass, MiscUtils.stringToBytes(value));
    }

    public NativeObject asWideString(final String value) {
        return NativeObject.newNativeInts(this, getWideStringClass(), MiscUtils.stringToCodePointsArray(value));
    }

    public NativeObject asString(final String value, final ConditionProfile wideStringProfile) {
        return wideStringProfile.profile(NativeObject.needsWideString(value)) ? asWideString(value) : asByteString(value);
    }

    public PointersObject asPoint(final AbstractPointersObjectWriteNode writeNode, final Object xPos, final Object yPos) {
        return PointersObject.create(writeNode, pointClass, xPos, yPos);
    }

    public PointersObject asPoint(final AbstractPointersObjectWriteNode writeNode, final DisplayPoint point) {
        return asPoint(writeNode, (long) point.getWidth(), (long) point.getHeight());
    }

    public ArrayObject newEmptyArray() {
        return ArrayObject.createWithStorage(this, arrayClass, ArrayUtils.EMPTY_ARRAY);
    }

    public PointersObject newMessage(final AbstractPointersObjectWriteNode writeNode, final NativeObject selector, final ClassObject rcvrClass, final Object[] arguments) {
        final PointersObject message = new PointersObject(this, messageClass);
        writeNode.execute(message, MESSAGE.SELECTOR, selector);
        writeNode.execute(message, MESSAGE.ARGUMENTS, asArrayOfObjects(arguments));
        assert message.instsize() > MESSAGE.LOOKUP_CLASS : "Early versions do not have lookupClass";
        writeNode.execute(message, MESSAGE.LOOKUP_CLASS, rcvrClass);
        return message;
    }

    /*
     * PRINTING
     */

    @TruffleBoundary
    public void printToStdOut(final String string) {
        if (!options.isQuiet) {
            getOutput().println("[trufflesqueak] " + string);
        }
    }

    @TruffleBoundary
    public void printToStdOut(final Object... arguments) {
        printToStdOut(ArrayUtils.toJoinedString(" ", arguments));
    }

    @TruffleBoundary
    public void printToStdErr(final String string) {
        getError().println("[trufflesqueak] " + string);
    }

    @TruffleBoundary
    public void printToStdErr(final Object... arguments) {
        printToStdErr(ArrayUtils.toJoinedString(" ", arguments));
    }

    /*
     * INSTRUMENTATION
     */

    public void reportNewAllocationRequest() {
        allocationReporter.onEnter(null, 0, AllocationReporter.SIZE_UNKNOWN);
    }

    public <T extends Object> T reportNewAllocationResult(final T value) {
        allocationReporter.onReturnValue(value, 0, AllocationReporter.SIZE_UNKNOWN);
        return value;
    }
}
