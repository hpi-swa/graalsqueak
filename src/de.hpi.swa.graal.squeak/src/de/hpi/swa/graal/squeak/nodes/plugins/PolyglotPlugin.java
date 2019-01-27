package de.hpi.swa.graal.squeak.nodes.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.Message;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.LanguageInfo;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.source.Source.LiteralBuilder;
import com.oracle.truffle.api.source.Source.SourceBuilder;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.nodes.accessing.ArrayObjectNodes.GetObjectArrayNode;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;

public final class PolyglotPlugin extends AbstractPrimitiveFactoryHolder {
    private static final String EVAL_SOURCE_NAME = "<eval>";
    private static final Boolean PRINT_ERRORS = true;

    @Override
    public boolean isEnabled(final SqueakImageContext image) {
        return image.supportsTruffleObject();
    }

    @Override
    public List<? extends NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return PolyglotPluginFactory.getFactories();
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveEvalString")
    protected abstract static class PrimEvalStringNode extends AbstractPrimitiveNode implements TernaryPrimitive {

        protected PrimEvalStringNode(final CompiledMethodObject method) {
            super(method);
        }

        @TruffleBoundary
        @Specialization(guards = {"languageIdOrMimeTypeObj.isByteType()", "sourceObject.isByteType()"})
        protected final Object doParseAndCall(@SuppressWarnings("unused") final Object receiver, final NativeObject languageIdOrMimeTypeObj, final NativeObject sourceObject) {
            PrimGetLastErrorNode.unsetLastError();
            final String languageIdOrMimeType = languageIdOrMimeTypeObj.asString();
            final String sourceText = sourceObject.asString();
            final Env env = code.image.env;
            try {
                final boolean mimeType = isMimeType(languageIdOrMimeType);
                final String lang = mimeType ? findLanguageByMimeType(env, languageIdOrMimeType) : languageIdOrMimeType;
                LiteralBuilder newBuilder = Source.newBuilder(lang, sourceText, EVAL_SOURCE_NAME);
                if (mimeType) {
                    newBuilder = newBuilder.mimeType(languageIdOrMimeType);
                }
                final Source source = newBuilder.build();
                final boolean wasActive = code.image.interrupt.isActive();
                code.image.interrupt.deactivate();
                try {
                    return code.image.wrap(env.parse(source).call());
                } finally {
                    if (wasActive) {
                        code.image.interrupt.activate();
                    }
                }
            } catch (RuntimeException e) {
                CompilerDirectives.transferToInterpreter();
                PrimGetLastErrorNode.setLastError(code, e);
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveEvalFile")
    protected abstract static class PrimEvalFileNode extends AbstractPrimitiveNode implements TernaryPrimitive {

        protected PrimEvalFileNode(final CompiledMethodObject method) {
            super(method);
        }

        @TruffleBoundary
        @Specialization(guards = {"languageIdOrMimeTypeObj.isByteType()", "path.isByteType()"})
        protected final Object doParseAndCall(@SuppressWarnings("unused") final Object receiver, final NativeObject languageIdOrMimeTypeObj, final NativeObject path) {
            PrimGetLastErrorNode.unsetLastError();
            final String languageIdOrMimeType = languageIdOrMimeTypeObj.asString();
            final String pathString = path.asString();
            final Env env = code.image.env;
            try {
                final boolean mimeType = isMimeType(languageIdOrMimeType);
                final String lang = mimeType ? findLanguageByMimeType(env, languageIdOrMimeType) : languageIdOrMimeType;
                SourceBuilder newBuilder = Source.newBuilder(lang, env.getTruffleFile(pathString));
                if (mimeType) {
                    newBuilder = newBuilder.mimeType(languageIdOrMimeType);
                }
                return env.parse(newBuilder.name(pathString).build()).call();
            } catch (IOException | RuntimeException e) {
                CompilerDirectives.transferToInterpreter();
                PrimGetLastErrorNode.setLastError(code, e);
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveEvalC")
    protected abstract static class PrimEvalCNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        private static final String C_FILENAME = "temp.c";
        private static final String LLVM_FILENAME = "temp.bc";
        @Child private Node readNode = Message.READ.createNode();
        @Child private Node executeNode = Message.EXECUTE.createNode();

        protected PrimEvalCNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"receiver.isByteType()", "memberToCall.isByteType()"})
        @TruffleBoundary
        protected final Object doEvaluate(final NativeObject receiver, final NativeObject memberToCall) {
            PrimGetLastErrorNode.unsetLastError();
            final String foreignCode = receiver.asString();
            final String cFile = code.image.imageRelativeFilePathFor(C_FILENAME);
            final String llvmFile = code.image.imageRelativeFilePathFor(LLVM_FILENAME);
            try {
                Files.write(Paths.get(cFile), foreignCode.getBytes());
                final Process p = Runtime.getRuntime().exec("clang -O1 -c -emit-llvm -o " + llvmFile + " " + cFile);
                p.waitFor();
                final Source source = Source.newBuilder("llvm", code.image.env.getTruffleFile(llvmFile)).build();
                final CallTarget foreignCallTarget = code.image.env.parse(source);
                final TruffleObject library = (TruffleObject) foreignCallTarget.call();
                final TruffleObject cFunction;
                try {
                    cFunction = (TruffleObject) ForeignAccess.sendRead(readNode, library, memberToCall.asString());
                } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                    PrimGetLastErrorNode.setLastError(code, e);
                    throw new PrimitiveFailed();
                }
                final Object result;
                try {
                    result = ForeignAccess.sendExecute(executeNode, cFunction);
                } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                    PrimGetLastErrorNode.setLastError(code, e);
                    throw new PrimitiveFailed();
                }
                return code.image.wrap(result);
            } catch (IOException | RuntimeException | InterruptedException e) {
                PrimGetLastErrorNode.setLastError(code, e);
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveAsPointer")
    protected abstract static class PrimAsPointerNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        @Child private Node asPointerNode = Message.AS_POINTER.createNode();

        protected PrimAsPointerNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"!isAbstractSqueakObject(receiver)"})
        protected final Object doExecute(final TruffleObject receiver) {
            PrimGetLastErrorNode.unsetLastError();
            try {
                return code.image.wrap(ForeignAccess.sendAsPointer(asPointerNode, receiver));
            } catch (UnsupportedMessageException e) {
                PrimGetLastErrorNode.setLastError(code, e);
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveExecute")
    protected abstract static class PrimExecuteNode extends AbstractPrimitiveNode implements TernaryPrimitive {
        @Child private Node executeNode = Message.EXECUTE.createNode();
        @Child private GetObjectArrayNode getObjectArrayNode = GetObjectArrayNode.create();

        protected PrimExecuteNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"!isAbstractSqueakObject(receiver)", "identifier.isByteType()"})
        protected final Object doExecute(final TruffleObject receiver, final NativeObject identifier, final ArrayObject argumentArray) {
            PrimGetLastErrorNode.unsetLastError();
            final Object[] arguments = getObjectArrayNode.execute(argumentArray);
            try {
                return code.image.wrap(ForeignAccess.sendExecute(executeNode, receiver, identifier.asString(), arguments));
            } catch (UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                PrimGetLastErrorNode.setLastError(code, e);
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveExport")
    protected abstract static class PrimExportNode extends AbstractPrimitiveNode implements TernaryPrimitive {
        protected PrimExportNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "name.isByteType()")
        @TruffleBoundary
        public final Object exportSymbol(@SuppressWarnings("unused") final ClassObject receiver, final NativeObject name, final Object value) {
            code.image.env.exportSymbol(name.asString(), value);
            return value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetKeys")
    protected abstract static class PrimGetKeysNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        @Child private Node keysNode = Message.KEYS.createNode();
        @Child private Node getSizeNode = Message.GET_SIZE.createNode();
        @Child private Node readNode = Message.READ.createNode();

        protected PrimGetKeysNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "!isAbstractSqueakObject(receiver)")
        protected final Object doKeys(final TruffleObject receiver) {
            PrimGetLastErrorNode.unsetLastError();
            try {
                final TruffleObject sendKeysResult = ForeignAccess.sendKeys(keysNode, receiver);
                final int size = (int) ForeignAccess.sendGetSize(getSizeNode, sendKeysResult);
                final Object[] keys = new Object[size];
                for (int index = 0; index < size; index++) {
                    final Object value = ForeignAccess.sendRead(readNode, sendKeysResult, index);
                    keys[index] = value == null ? "null" : value.toString();
                }
                return code.image.wrap(keys);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                PrimGetLastErrorNode.setLastError(code, e);
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetInstSize")
    protected abstract static class PrimGetInstSizeNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        @Child private Node hasKeysNode = Message.HAS_KEYS.createNode();
        @Child private Node getKeysNode = Message.KEYS.createNode();
        @Child private Node getSizeNode = Message.GET_SIZE.createNode();

        protected PrimGetInstSizeNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "!isAbstractSqueakObject(receiver)")
        protected final long doInstSize(final TruffleObject receiver) {
            PrimGetLastErrorNode.unsetLastError();
            try {
                if (ForeignAccess.sendHasKeys(hasKeysNode, receiver)) {
                    final TruffleObject keys = ForeignAccess.sendKeys(getKeysNode, receiver);
                    return (int) ForeignAccess.sendGetSize(getSizeNode, keys);
                } else {
                    return 0L;
                }
            } catch (UnsupportedMessageException e) {
                PrimGetLastErrorNode.setLastError(code, e);
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetSize")
    protected abstract static class PrimGetSizeNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        @Child private Node hasSizeNode = Message.HAS_SIZE.createNode();
        @Child private Node getSizeNode = Message.GET_SIZE.createNode();

        protected PrimGetSizeNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "!isAbstractSqueakObject(receiver)")
        protected final long doSize(final TruffleObject receiver) {
            PrimGetLastErrorNode.unsetLastError();
            try {
                if (ForeignAccess.sendHasSize(hasSizeNode, receiver)) {
                    return (int) ForeignAccess.sendGetSize(getSizeNode, receiver);
                } else {
                    return 0L;
                }
            } catch (UnsupportedMessageException e) {
                PrimGetLastErrorNode.setLastError(code, e);
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetLanguageInfo")
    protected abstract static class PrimGetLanguageInfoNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        protected PrimGetLanguageInfoNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "languageID.isByteType()")
        @TruffleBoundary
        protected final Object doGet(@SuppressWarnings("unused") final ClassObject receiver, final NativeObject languageID) {
            final Collection<LanguageInfo> languages = code.image.env.getLanguages().values();
            return code.image.wrap(languages.stream().//
                            filter(l -> !l.isInternal() && l.getId().equals(languageID.asString())).//
                            map(l -> new Object[]{l.getId(), l.getName(), l.getVersion(), l.getDefaultMimeType(), l.getMimeTypes().toArray()}).//
                            findFirst().orElseThrow(PrimitiveFailed::new));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetLastError")
    protected abstract static class PrimGetLastErrorNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        protected static Exception lastError = null;

        protected PrimGetLastErrorNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "lastError != null")
        @TruffleBoundary
        protected final Object doError(@SuppressWarnings("unused") final Object receiver) {
            return code.image.wrap(lastError.toString());
        }

        @Specialization(guards = "lastError == null")
        protected final Object doNil(@SuppressWarnings("unused") final Object receiver) {
            return code.image.nil;
        }

        protected static final void setLastError(final CompiledCodeObject code, final Exception e) {
            if (PRINT_ERRORS) {
                code.image.printToStdErr(e);
            }
            lastError = e;
        }

        protected static final void unsetLastError() {
            lastError = null;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetPolyglotBindings")
    protected abstract static class PrimGetPolyglotBindingsNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        protected PrimGetPolyglotBindingsNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        @TruffleBoundary
        protected final TruffleObject doGet(@SuppressWarnings("unused") final AbstractSqueakObject receiver) {
            return (TruffleObject) code.image.env.getPolyglotBindings();
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveImport")
    protected abstract static class PrimImportNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        protected PrimImportNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "name.isByteType()")
        @TruffleBoundary
        public final Object importSymbol(@SuppressWarnings("unused") final ClassObject receiver, final NativeObject name) {
            final Object object = code.image.env.importSymbol(name.asString());
            if (object == null) {
                return code.image.nil;
            }
            return object;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveListAvailableLanguageIDs")
    protected abstract static class PrimListAvailableLanguageIDsNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        protected PrimListAvailableLanguageIDsNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        @TruffleBoundary
        protected final Object doList(@SuppressWarnings("unused") final ClassObject receiver) {
            final Collection<LanguageInfo> languages = code.image.env.getLanguages().values();
            final Object[] result = languages.stream().filter(l -> !l.isInternal()).map(l -> l.getId()).toArray();
            return code.image.wrap(result);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveInvoke")
    protected abstract static class PrimInvokeNode extends AbstractPrimitiveNode implements TernaryPrimitive {
        @Child private Node invokeNode = Message.INVOKE.createNode();
        @Child private GetObjectArrayNode getObjectArrayNode = GetObjectArrayNode.create();

        protected PrimInvokeNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"!isAbstractSqueakObject(receiver)", "identifier.isByteType()"})
        protected final Object doInvoke(final TruffleObject receiver, final NativeObject identifier, final ArrayObject argumentArray) {
            PrimGetLastErrorNode.unsetLastError();
            final Object[] arguments = getObjectArrayNode.execute(argumentArray);
            try {
                return code.image.wrap(ForeignAccess.sendInvoke(invokeNode, receiver, identifier.asString(), arguments));
            } catch (UnsupportedTypeException | ArityException | UnknownIdentifierException | UnsupportedMessageException e) {
                PrimGetLastErrorNode.setLastError(code, e);
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveIsBoxed")
    protected abstract static class PrimIsBoxedNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        @Child private Node isExecutableNode = Message.IS_BOXED.createNode();

        protected PrimIsBoxedNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"!isAbstractSqueakObject(receiver)"})
        protected final Object doIsBoxed(final TruffleObject receiver) {
            return code.image.wrap(ForeignAccess.sendIsBoxed(isExecutableNode, receiver));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveIsExecutable")
    protected abstract static class PrimIsExecutableNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        @Child private Node isExecutableNode = Message.IS_EXECUTABLE.createNode();

        protected PrimIsExecutableNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"!isAbstractSqueakObject(receiver)"})
        protected final Object doIsExecutable(final TruffleObject receiver) {
            return code.image.wrap(ForeignAccess.sendIsExecutable(isExecutableNode, receiver));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveIsInstantiable")
    protected abstract static class PrimIsInstantiableNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        @Child private Node isIntantiable = Message.IS_INSTANTIABLE.createNode();

        protected PrimIsInstantiableNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"!isAbstractSqueakObject(receiver)"})
        protected final Object doIsInstantiable(final TruffleObject receiver) {
            return code.image.wrap(ForeignAccess.sendIsInstantiable(isIntantiable, receiver));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveIsNull")
    protected abstract static class PrimIsNullNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        @Child private Node isNullNode = Message.IS_NULL.createNode();

        protected PrimIsNullNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"!isAbstractSqueakObject(receiver)"})
        protected final Object doIsNull(final TruffleObject receiver) {
            return code.image.wrap(ForeignAccess.sendIsNull(isNullNode, receiver));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveIsPointer")
    protected abstract static class PrimIsPointerNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        @Child private Node isPointerNode = Message.IS_POINTER.createNode();

        protected PrimIsPointerNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"!isAbstractSqueakObject(receiver)"})
        protected final Object doIsPointer(final TruffleObject receiver) {
            return code.image.wrap(ForeignAccess.sendIsPointer(isPointerNode, receiver));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveRead")
    protected abstract static class PrimReadNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        @Child private Node readNode = Message.READ.createNode();

        protected PrimReadNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"!isAbstractSqueakObject(receiver)", "selector.isByteType()"})
        protected final Object doRead(final TruffleObject receiver, final NativeObject selector) {
            PrimGetLastErrorNode.unsetLastError();
            try {
                return code.image.wrap(ForeignAccess.sendRead(readNode, receiver, selector.asString()));
            } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                PrimGetLastErrorNode.setLastError(code, e);
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveStringRepresentation")
    protected abstract static class PrimStringRepresentationNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        @Child private Node readNode = Message.READ.createNode();

        protected PrimStringRepresentationNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"!isAbstractSqueakObject(receiver)"})
        @TruffleBoundary
        protected final Object doRead(final TruffleObject receiver) {
            return code.image.wrap(receiver.toString());
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveUnbox")
    protected abstract static class PrimUnboxNode extends AbstractPrimitiveNode implements UnaryPrimitive {
        @Child private Node unboxNode = Message.UNBOX.createNode();

        protected PrimUnboxNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"!isAbstractSqueakObject(receiver)"})
        @TruffleBoundary
        protected final Object doRead(final TruffleObject receiver) {
            PrimGetLastErrorNode.unsetLastError();
            try {
                return code.image.wrap(ForeignAccess.sendUnbox(unboxNode, receiver));
            } catch (UnsupportedMessageException e) {
                PrimGetLastErrorNode.setLastError(code, e);
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveWrite")
    protected abstract static class PrimWriteNode extends AbstractPrimitiveNode implements TernaryPrimitive {
        @Child private Node writeNode = Message.WRITE.createNode();

        protected PrimWriteNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"!isAbstractSqueakObject(receiver)", "selector.isByteType()"})
        @TruffleBoundary
        protected final Object doWrite(final TruffleObject receiver, final NativeObject selector, final Object value) {
            PrimGetLastErrorNode.unsetLastError();
            try {
                return code.image.wrap(ForeignAccess.sendWrite(writeNode, receiver, selector.asString(), value));
            } catch (UnknownIdentifierException | UnsupportedMessageException | UnsupportedTypeException e) {
                PrimGetLastErrorNode.setLastError(code, e);
                throw new PrimitiveFailed();
            }
        }
    }

    /*
     * Helper functions.
     */

    @TruffleBoundary(transferToInterpreterOnException = false)
    private static String findLanguageByMimeType(final Env env, final String mimeType) {
        final Map<String, LanguageInfo> languages = env.getLanguages();
        for (String registeredMimeType : languages.keySet()) {
            if (mimeType.equals(registeredMimeType)) {
                return languages.get(registeredMimeType).getId();
            }
        }
        return null;
    }

    private static boolean isMimeType(final String lang) {
        return lang.contains("/");
    }
}
