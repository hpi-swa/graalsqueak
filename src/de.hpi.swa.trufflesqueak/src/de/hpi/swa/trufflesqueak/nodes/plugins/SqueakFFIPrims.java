/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.plugins;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.source.Source;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.interop.WrapToSqueakNode;
import de.hpi.swa.trufflesqueak.model.AbstractSqueakObject;
import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.ClassObject;
import de.hpi.swa.trufflesqueak.model.LargeIntegerObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.model.NilObject;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.model.layout.ObjectLayouts;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectReadNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.ArrayObjectNodes.ArrayObjectToObjectArrayCopyNode;
import de.hpi.swa.trufflesqueak.nodes.plugins.SqueakFFIPrimsFactory.ArgTypeConversionNodeGen;
import de.hpi.swa.trufflesqueak.nodes.plugins.ffi.FFIConstants.FFI_ERROR;
import de.hpi.swa.trufflesqueak.nodes.plugins.ffi.FFIConstants.FFI_TYPES;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.QuaternaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.QuinaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.MiscellaneousPrimitives.PrimCalloutToFFINode;
import de.hpi.swa.trufflesqueak.util.OSDetector;
import de.hpi.swa.trufflesqueak.util.UnsafeUtils;

public final class SqueakFFIPrims extends AbstractPrimitiveFactoryHolder {

    /** "primitiveCallout" implemented as {@link PrimCalloutToFFINode}. */

    @ImportStatic(FFI_TYPES.class)
    protected abstract static class ArgTypeConversionNode extends Node {

        protected static ArgTypeConversionNode create() {
            return ArgTypeConversionNodeGen.create();
        }

        public abstract Object execute(int headerWord, Object value);

        @Specialization(guards = {"getAtomicType(headerWord) == 10", "!isPointerType(headerWord)"})
        protected static final char doChar(@SuppressWarnings("unused") final int headerWord, final boolean value) {
            return (char) (value ? 0 : 1);
        }

        @Specialization(guards = {"getAtomicType(headerWord) == 10", "!isPointerType(headerWord)"})
        protected static final char doChar(@SuppressWarnings("unused") final int headerWord, final char value) {
            return value;
        }

        @Specialization(guards = {"getAtomicType(headerWord) == 10", "!isPointerType(headerWord)"})
        protected static final char doChar(@SuppressWarnings("unused") final int headerWord, final long value) {
            return (char) value;
        }

        @Specialization(guards = {"getAtomicType(headerWord) == 10", "!isPointerType(headerWord)"})
        protected static final char doChar(@SuppressWarnings("unused") final int headerWord, final double value) {
            return (char) value;
        }

        @Specialization(guards = {"getAtomicType(headerWord) == 10", "isPointerType(headerWord)"}, limit = "1")
        protected static final String doString(@SuppressWarnings("unused") final int headerWord, final Object value,
                        @CachedLibrary("value") final InteropLibrary lib) {
            try {
                return lib.asString(value);
            } catch (final UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                e.printStackTrace();
                return "unknown";
            }
        }

        @Specialization(guards = "getAtomicType(headerWord) == 12")
        protected static final float doFloat(@SuppressWarnings("unused") final int headerWord, final double value) {
            return (float) value;
        }

        @Fallback
        protected static final Object doFallback(@SuppressWarnings("unused") final int headerWord, final Object value) {
            return value;
        }
    }

    public abstract static class AbstractFFIPrimitiveNode extends AbstractPrimitiveNode {
        @Child private ArgTypeConversionNode conversionNode = ArgTypeConversionNode.create();
        @Child private WrapToSqueakNode wrapNode = WrapToSqueakNode.create();
        @Child private AbstractPointersObjectReadNode readExternalLibNode = AbstractPointersObjectReadNode.create();
        @Child private AbstractPointersObjectReadNode readArgumentTypeNode = AbstractPointersObjectReadNode.create();

        protected static final PointersObject asExternalFunctionOrFail(final SqueakImageContext image, final Object object) {
            if (!(object instanceof PointersObject && ((PointersObject) object).getSqueakClass().includesExternalFunctionBehavior(image))) {
                throw PrimitiveFailed.andTransferToInterpreter(FFI_ERROR.NOT_FUNCTION);
            }
            return (PointersObject) object;
        }

        @TruffleBoundary
        protected final Object doCallout(final SqueakImageContext image, final PointersObject externalLibraryFunction, final AbstractSqueakObject receiver, final Object... arguments) {
            final List<Integer> headerWordList = new ArrayList<>();

            final ArrayObject argTypes = readExternalLibNode.executeArray(externalLibraryFunction, ObjectLayouts.EXTERNAL_LIBRARY_FUNCTION.ARG_TYPES);

            if (argTypes != null && argTypes.getObjectStorage().length == arguments.length + 1) {
                final Object[] argTypesValues = argTypes.getObjectStorage();

                for (final Object argumentType : argTypesValues) {
                    if (argumentType instanceof PointersObject) {
                        final NativeObject compiledSpec = readArgumentTypeNode.executeNative((PointersObject) argumentType, ObjectLayouts.EXTERNAL_TYPE.COMPILED_SPEC);
                        headerWordList.add(compiledSpec.getInt(0));
                    }
                }
            }

            final Object[] argumentsConverted = getConvertedArgumentsFromHeaderWords(headerWordList, arguments);
            final List<String> nfiArgTypeList = getArgTypeListFromHeaderWords(headerWordList);

            final String name = readExternalLibNode.executeNative(externalLibraryFunction, ObjectLayouts.EXTERNAL_LIBRARY_FUNCTION.NAME).asStringUnsafe();
            final String moduleName = getModuleName(receiver, externalLibraryFunction);
            final String nfiCodeParams = generateNfiCodeParamsString(nfiArgTypeList);
            final String nfiCode = String.format("load \"%s\" {%s%s}", getPathOrFail(image, moduleName), name, nfiCodeParams);
            try {
                final Object value = calloutToLib(image, name, argumentsConverted, nfiCode);
                assert value != null;
                return wrapNode.executeWrap(conversionNode.execute(headerWordList.get(0), value));
            } catch (UnsupportedMessageException | ArityException | UnknownIdentifierException | UnsupportedTypeException e) {
                e.printStackTrace();
                // TODO: return correct error code.
                throw PrimitiveFailed.GENERIC_ERROR;
            } catch (final Exception e) {
                e.printStackTrace();
                // TODO: handle exception
                throw PrimitiveFailed.GENERIC_ERROR;
            }
        }

        private Object[] getConvertedArgumentsFromHeaderWords(final List<Integer> headerWordList, final Object[] arguments) {
            final Object[] argumentsConverted = new Object[arguments.length];

            for (int j = 1; j < headerWordList.size(); j++) {
                argumentsConverted[j - 1] = conversionNode.execute(headerWordList.get(j), arguments[j - 1]);
            }
            return argumentsConverted;
        }

        private static List<String> getArgTypeListFromHeaderWords(final List<Integer> headerWordList) {
            final List<String> nfiArgTypeList = new ArrayList<>();

            for (final int headerWord : headerWordList) {
                final String atomicName = FFI_TYPES.getTruffleTypeFromInt(headerWord);
                nfiArgTypeList.add(atomicName);
            }
            return nfiArgTypeList;
        }

        private static Object calloutToLib(final SqueakImageContext image, final String name, final Object[] argumentsConverted, final String nfiCode)
                        throws UnsupportedMessageException, ArityException, UnknownIdentifierException, UnsupportedTypeException {
            final Source source = Source.newBuilder("nfi", nfiCode, "native").build();
            final Object ffiTest = image.env.parseInternal(source).call();
            final InteropLibrary interopLib = InteropLibrary.getFactory().getUncached(ffiTest);
            return interopLib.invokeMember(ffiTest, name, argumentsConverted);
        }

        private String getModuleName(final AbstractSqueakObject receiver, final PointersObject externalLibraryFunction) {
            final Object moduleObject = readExternalLibNode.execute(externalLibraryFunction, ObjectLayouts.EXTERNAL_LIBRARY_FUNCTION.MODULE);
            if (moduleObject != NilObject.SINGLETON) {
                return ((NativeObject) moduleObject).asStringUnsafe();
            } else {
                return ((NativeObject) ((PointersObject) receiver).instVarAt0Slow(ObjectLayouts.CLASS.NAME)).asStringUnsafe();
            }
        }

        protected static final String getPathOrFail(final SqueakImageContext image, final String moduleName) {
            final String ffiExtension = OSDetector.SINGLETON.getFFIExtension();
            final TruffleFile home = image.getHomePath();
            TruffleFile libPath = home.resolve("lib" + File.separatorChar + moduleName + ffiExtension);
            if (!libPath.exists()) {
                libPath = home.resolve("lib" + File.separatorChar + "lib" + moduleName + ffiExtension);
                if (!libPath.exists()) {
                    throw PrimitiveFailed.GENERIC_ERROR;
                }
            }
            return libPath.getAbsoluteFile().getPath();
        }

        private static String generateNfiCodeParamsString(final List<String> argumentList) {
            String nfiCodeParams = "";
            if (!argumentList.isEmpty()) {
                final String returnType = argumentList.get(0);
                argumentList.remove(0);
                if (!argumentList.isEmpty()) {
                    nfiCodeParams = "(" + String.join(",", argumentList) + ")";
                } else {
                    nfiCodeParams = "()";
                }
                nfiCodeParams += ":" + returnType + ";";
            }
            return nfiCodeParams;
        }

    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveCalloutWithArgs")
    protected abstract static class PrimCalloutWithArgsNode extends AbstractFFIPrimitiveNode implements BinaryPrimitive {
        @Child private ArrayObjectToObjectArrayCopyNode getObjectArrayNode = ArrayObjectToObjectArrayCopyNode.create();

        @SuppressWarnings("unused")
        @Specialization
        protected final Object doCalloutWithArgs(final PointersObject receiver, final ArrayObject argArray,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return doCallout(image, asExternalFunctionOrFail(image, receiver), receiver, getObjectArrayNode.execute(argArray));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveLoadSymbolFromModule")
    protected abstract static class PrimLoadSymbolFromModuleNode extends AbstractFFIPrimitiveNode implements TernaryPrimitive {
        @Specialization(guards = {"moduleSymbol.isByteType()", "module.isByteType()"})
        protected static final Object doLoadSymbol(final ClassObject receiver, final NativeObject moduleSymbol, final NativeObject module,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image,
                        @CachedLibrary(limit = "2") final InteropLibrary lib) {
            final String moduleSymbolName = moduleSymbol.asStringUnsafe();
            final String moduleName = module.asStringUnsafe();
            final CallTarget target = image.env.parseInternal(generateNFILoadSource(image, moduleName));
            final Object library;
            try {
                library = target.call();
            } catch (final Throwable e) {
                throw PrimitiveFailed.andTransferToInterpreter();
            }
            final Object symbol;
            try {
                symbol = lib.readMember(library, moduleSymbolName);
            } catch (UnsupportedMessageException | UnknownIdentifierException e) {
                throw PrimitiveFailed.andTransferToInterpreter();
            }
            final long pointer;
            try {
                pointer = lib.asPointer(symbol);
            } catch (final UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                e.printStackTrace();
                return newExternalAddress(image, receiver, 0L);
            }
            return newExternalAddress(image, receiver, pointer);
        }

        @TruffleBoundary
        private static Source generateNFILoadSource(final SqueakImageContext image, final String moduleName) {
            return Source.newBuilder("nfi", String.format("load \"%s\"", getPathOrFail(image, moduleName)), "native").build();
        }

        private static NativeObject newExternalAddress(final SqueakImageContext image, final ClassObject externalAddressClass, final long pointer) {
            return NativeObject.newNativeBytes(image, externalAddressClass,
                            new byte[]{(byte) pointer, (byte) (pointer >> 8), (byte) (pointer >> 16), (byte) (pointer >> 24), (byte) (pointer >> 32), (byte) (pointer >> 40),
                                            (byte) (pointer >> 48), (byte) (pointer >> 56)});
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFFIIntegerAt")
    protected abstract static class PrimFFIIntegerAtNode extends AbstractPrimitiveNode implements QuaternaryPrimitive {
        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 2", "isSigned"})
        protected static final long doAt2Signed(final NativeObject byteArray, final long byteOffsetLong, final long byteSize, final boolean isSigned) {
            return (int) doAt2Unsigned(byteArray, byteOffsetLong, byteSize, false);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 2", "!isSigned"})
        protected static final long doAt2Unsigned(final NativeObject byteArray, final long byteOffsetLong, final long byteSize, final boolean isSigned) {
            return Short.toUnsignedLong(UnsafeUtils.getShortFromBytes(byteArray.getByteStorage(), byteOffsetLong - 1));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 4", "isSigned"})
        protected static final long doAt4Signed(final NativeObject byteArray, final long byteOffsetLong, final long byteSize, final boolean isSigned) {
            return (int) doAt4Unsigned(byteArray, byteOffsetLong, byteSize, false);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 4", "!isSigned"})
        protected static final long doAt4Unsigned(final NativeObject byteArray, final long byteOffsetLong, final long byteSize, final boolean isSigned) {
            return Integer.toUnsignedLong(UnsafeUtils.getIntFromBytes(byteArray.getByteStorage(), byteOffsetLong - 1));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 8", "isSigned"})
        protected static final long doAt8Signed(final NativeObject byteArray, final long byteOffsetLong, final long byteSize, final boolean isSigned) {
            return UnsafeUtils.getLongAtByteIndex(byteArray.getByteStorage(), (int) byteOffsetLong - 1);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 8", "!isSigned"})
        protected static final Object doAt8Unsigned(final NativeObject byteArray, final long byteOffsetLong, final long byteSize, final boolean isSigned,
                        @Cached("createBinaryProfile()") final ConditionProfile positiveProfile,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            final long signedLong = doAt8Signed(byteArray, byteOffsetLong, byteSize, isSigned);
            if (positiveProfile.profile(signedLong >= 0)) {
                return signedLong;
            } else {
                return LargeIntegerObject.toUnsigned(image, signedLong);
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFFIIntegerAtPut")
    protected abstract static class PrimFFIIntegerAtPutNode extends AbstractPrimitiveNode implements QuinaryPrimitive {
        protected static final long MAX_VALUE_SIGNED_1 = 1L << 8 * 1 - 1;
        protected static final long MAX_VALUE_SIGNED_2 = 1L << 8 * 2 - 1;
        protected static final long MAX_VALUE_SIGNED_4 = 1L << 8 * 4 - 1;
        protected static final BigInteger MAX_VALUE_SIGNED_8 = BigInteger.ONE.shiftLeft(8 * 8 - 1);
        protected static final long MAX_VALUE_UNSIGNED_1 = 1L << 8 * 1;
        protected static final long MAX_VALUE_UNSIGNED_2 = 1L << 8 * 2;
        protected static final long MAX_VALUE_UNSIGNED_4 = 1L << 8 * 4;

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 1", "isSigned", "inSignedBounds(value, MAX_VALUE_SIGNED_1)"})
        protected static final Object doAtPut1Signed(final NativeObject byteArray, final long byteOffsetLong, final long value, final long byteSize, final boolean isSigned) {
            return doAtPut1Unsigned(byteArray, byteOffsetLong, value, byteSize, isSigned);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 1", "!isSigned", "inUnsignedBounds(value, MAX_VALUE_UNSIGNED_1)"})
        protected static final Object doAtPut1Unsigned(final NativeObject byteArray, final long byteOffsetLong, final long value, final long byteSize, final boolean isSigned) {
            byteArray.getByteStorage()[(int) byteOffsetLong - 1] = (byte) value;
            return value;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 2", "isSigned", "inSignedBounds(value, MAX_VALUE_SIGNED_2)"})
        protected static final Object doAtPut2Signed(final NativeObject byteArray, final long byteOffsetLong, final long value, final long byteSize, final boolean isSigned) {
            return doAtPut2Unsigned(byteArray, byteOffsetLong, value, byteSize, isSigned);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 2", "!isSigned", "inUnsignedBounds(value, MAX_VALUE_UNSIGNED_2)"})
        protected static final Object doAtPut2Unsigned(final NativeObject byteArray, final long byteOffsetLong, final long value, final long byteSize, final boolean isSigned) {
            UnsafeUtils.putShortIntoBytes(byteArray.getByteStorage(), byteOffsetLong - 1, (short) value);
            return value;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 4", "isSigned", "inSignedBounds(value, MAX_VALUE_SIGNED_4)"})
        protected static final Object doAtPut4Signed(final NativeObject byteArray, final long byteOffsetLong, final long value, final long byteSize, final boolean isSigned) {
            return doAtPut4Unsigned(byteArray, byteOffsetLong, value, byteSize, isSigned);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 4", "!isSigned", "inUnsignedBounds(value, MAX_VALUE_UNSIGNED_4)"})
        protected static final Object doAtPut4Unsigned(final NativeObject byteArray, final long byteOffsetLong, final long value, final long byteSize, final boolean isSigned) {
            UnsafeUtils.putIntIntoBytes(byteArray.getByteStorage(), byteOffsetLong - 1, (int) value);
            return value;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 4", "isSigned", "value.fitsIntoLong()", "inSignedBounds(value.longValueExact(), MAX_VALUE_SIGNED_4)"})
        protected static final Object doAtPut4SignedLarge(final NativeObject byteArray, final long byteOffsetLong, final LargeIntegerObject value, final long byteSize, final boolean isSigned) {
            return doAtPut4UnsignedLarge(byteArray, byteOffsetLong, value, byteSize, isSigned);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 4", "!isSigned", "value.fitsIntoLong()",
                        "inUnsignedBounds(value.longValueExact(), MAX_VALUE_UNSIGNED_4)"})
        @ExplodeLoop
        protected static final Object doAtPut4UnsignedLarge(final NativeObject byteArray, final long byteOffsetLong, final LargeIntegerObject value, final long byteSize, final boolean isSigned) {
            final int byteOffset = (int) byteOffsetLong - 1;
            final byte[] targetBytes = byteArray.getByteStorage();
            final byte[] sourceBytes = value.getBytes();
            final int numSourceBytes = sourceBytes.length;
            for (int i = 0; i < 4; i++) {
                targetBytes[byteOffset + i] = i < numSourceBytes ? sourceBytes[i] : 0;
            }
            return value;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 8", "isSigned"})
        protected static final Object doAtPut8Signed(final NativeObject byteArray, final long byteOffsetLong, final long value, final long byteSize, final boolean isSigned) {
            return doAtPut8Unsigned(byteArray, byteOffsetLong, value, byteSize, isSigned);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 8", "!isSigned", "value >= 0"})
        protected static final Object doAtPut8Unsigned(final NativeObject byteArray, final long byteOffsetLong, final long value, final long byteSize, final boolean isSigned) {
            UnsafeUtils.putLongIntoBytes(byteArray.getByteStorage(), byteOffsetLong - 1, value);
            return value;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 8", "isSigned", "inSignedBounds(value, MAX_VALUE_SIGNED_8)"})
        protected static final Object doAtPut8SignedLarge(final NativeObject byteArray, final long byteOffsetLong, final LargeIntegerObject value, final long byteSize, final boolean isSigned) {
            return doAtPut8UnsignedLarge(byteArray, byteOffsetLong, value, byteSize, isSigned);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"byteArray.isByteType()", "byteOffsetLong > 0", "byteSize == 8", "!isSigned", "inUnsignedBounds(value)"})
        @ExplodeLoop
        protected static final Object doAtPut8UnsignedLarge(final NativeObject byteArray, final long byteOffsetLong, final LargeIntegerObject value, final long byteSize, final boolean isSigned) {
            final int byteOffset = (int) byteOffsetLong - 1;
            final byte[] targetBytes = byteArray.getByteStorage();
            final byte[] sourceBytes = value.getBytes();
            final int numSourceBytes = sourceBytes.length;
            for (int i = 0; i < 8; i++) {
                targetBytes[byteOffset + i] = i < numSourceBytes ? sourceBytes[i] : 0;
            }
            return value;
        }

        protected static final boolean inSignedBounds(final long value, final long max) {
            return value >= 0 - max && value < max;
        }

        protected static final boolean inUnsignedBounds(final long value, final long max) {
            return 0 <= value && value < max;
        }

        @TruffleBoundary
        protected static final boolean inSignedBounds(final LargeIntegerObject value, final BigInteger max) {
            return value.getBigInteger().compareTo(BigInteger.ZERO.subtract(max)) >= 0 && value.getBigInteger().compareTo(max) < 0;
        }

        @TruffleBoundary
        protected static final boolean inUnsignedBounds(final LargeIntegerObject value) {
            return value.isZeroOrPositive() && value.lessThanOneShiftedBy64();
        }
    }

    @Override
    public List<? extends NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return SqueakFFIPrimsFactory.getFactories();
    }
}
