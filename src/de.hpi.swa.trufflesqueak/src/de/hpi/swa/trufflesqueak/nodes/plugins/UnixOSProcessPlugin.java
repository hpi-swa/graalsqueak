/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.plugins;

import java.io.IOException;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.BooleanObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.model.NilObject;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitiveWithoutFallback;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitiveWithoutFallback;
import de.hpi.swa.trufflesqueak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.trufflesqueak.util.MiscUtils;

public final class UnixOSProcessPlugin extends AbstractOSProcessPlugin {
    protected abstract static class AbstractFilePrimitiveNode extends AbstractPrimitiveNode {

        @TruffleBoundary
        private static long decodePermissions(final Set<PosixFilePermission> permissions, final PosixFilePermission read, final PosixFilePermission write, final PosixFilePermission execute) {
            return (permissions.contains(read) ? 4 : 0) | (permissions.contains(write) ? 2 : 0) | (permissions.contains(execute) ? 1 : 0);
        }

        protected static final ArrayObject getProtectionMask(final SqueakImageContext image, final Set<PosixFilePermission> permissions) {
            final long owner = decodePermissions(permissions, PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
            final long group = decodePermissions(permissions, PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.GROUP_EXECUTE);
            final long others = decodePermissions(permissions, PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_WRITE, PosixFilePermission.OTHERS_EXECUTE);
            return image.asArrayOfLongs(0L, owner, group, others);
        }
    }

    protected abstract static class AbstractKillPrimitiveNode extends AbstractSysCallPrimitiveNode {
        @Override
        protected final String getFunctionName() {
            return "kill";
        }

        @Override
        protected final String getFunctionSignature() {
            return "(SINT32,SINT32):SINT32";
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveArgumentAt")
    protected abstract static class PrimArgumentAtNode extends AbstractPrimitiveNode implements BinaryPrimitiveWithoutFallback {
        @Specialization
        protected static final Object doAt(@SuppressWarnings("unused") final Object receiver, final long index,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            if (index == 1) {
                return image.asByteString(MiscUtils.getVMPath());
            } else if (1 < index && index < image.getImageArguments().length) {
                return image.asByteString(image.getImageArguments()[(int) index - 2]);
            } else {
                return NilObject.SINGLETON;
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveCanReceiveSignals")
    protected abstract static class PrimCanReceiveSignalsNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @SuppressWarnings("unused")
        @Specialization(guards = "!isLong(pid)")
        protected static final boolean doCanReceiveSignals(final Object receiver, final Object pid) {
            return BooleanObject.FALSE;
        }

        @Specialization(guards = "supportsNFI")
        protected final boolean doCanReceiveSignals(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return BooleanObject.wrap(setValue(lib, pid, SIGNALS.SIG_DFL) == 0);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveEnvironmentAt")
    protected abstract static class PrimEnvironmentAtNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        private static Object[] environmentKeys;

        protected static final Object[] getEnvironmentKeys() {
            if (environmentKeys == null) {
                environmentKeys = systemGetEnvKeyArray();
            }
            return environmentKeys;
        }

        @TruffleBoundary
        private static Object[] systemGetEnvKeyArray() {
            return System.getenv().keySet().toArray();
        }

        @Specialization(guards = "inBounds1(index, getEnvironmentKeys().length)")
        protected static final NativeObject doAt(@SuppressWarnings("unused") final Object receiver, final long index,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            final String key = getEnvironmentKeys()[(int) index - 1].toString();
            assert key != null : "key should not be null";
            final String value = systemGetEnv(image.env, key);
            assert value != null : "value should not be null";
            return image.asByteString(key + "=" + value);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveEnvironmentAtSymbol")
    protected abstract static class PrimEnvironmentAtSymbolNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "aSymbol.isByteType()")
        protected static final NativeObject doAt(@SuppressWarnings("unused") final Object receiver, final NativeObject aSymbol,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            final String key = aSymbol.asStringUnsafe();
            final String value = systemGetEnv(image.env, key);
            if (value == null) {
                throw PrimitiveFailed.GENERIC_ERROR;
            } else {
                return image.asByteString(value);
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveErrorMessageAt")
    protected abstract static class PrimErrorMessageAtNode extends AbstractSysCallPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final NativeObject doErrorMessageAt(@SuppressWarnings("unused") final Object receiver, final long index,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib,
                        @CachedLibrary(limit = "1") final InteropLibrary resultLib,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            try {
                return image.asByteString(resultLib.asString(lib.execute(sysCallObject, (int) index)));
            } catch (final UnsupportedMessageException | UnsupportedTypeException | ArityException e) {
                throw PrimitiveFailed.andTransferToInterpreterWithError(e);
            }
        }

        @Override
        protected final String getFunctionName() {
            return "strerror";
        }

        @Override
        protected final String getFunctionSignature() {
            return "(SINT32):STRING";
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileProtectionMask")
    protected abstract static class PrimFileProtectionMaskNode extends AbstractFilePrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "pathString.isByteType()")
        protected static final ArrayObject doFileProtectionMask(@SuppressWarnings("unused") final Object receiver, final NativeObject pathString,
                        @Cached final BranchProfile errorProfile,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            try {
                final TruffleFile file = image.env.getPublicTruffleFile(pathString.asStringUnsafe());
                return getProtectionMask(image, file.getPosixPermissions());
            } catch (final IOException | UnsupportedOperationException | SecurityException e) {
                errorProfile.enter();
                throw PrimitiveFailed.GENERIC_ERROR;
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileStat")
    protected abstract static class PrimFileStatNode extends AbstractFilePrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "pathString.isByteType()")
        protected static final ArrayObject doFileStat(@SuppressWarnings("unused") final Object receiver, final NativeObject pathString,
                        @Cached final BranchProfile errorProfile,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            try {
                final TruffleFile file = image.env.getPublicTruffleFile(pathString.asStringUnsafe());
                final long uid = file.getOwner().hashCode();
                final long gid = file.getGroup().hashCode();
                final ArrayObject mask = getProtectionMask(image, file.getPosixPermissions());
                return image.asArrayOfObjects(uid, gid, mask);
            } catch (final IOException | UnsupportedOperationException | SecurityException e) {
                errorProfile.enter();
                throw PrimitiveFailed.GENERIC_ERROR;
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveForwardSignalToSemaphore")
    protected abstract static class PrimForwardSignalToSemaphoreNode extends AbstractPrimitiveNode implements TernaryPrimitive {
        @SuppressWarnings("unused")
        @Specialization
        protected static final boolean doForward(final Object receiver, final long signalNumber, final long semaphoreIndex) {
            return BooleanObject.TRUE; // TODO: implement
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetEGid")
    protected abstract static class PrimGetEGidNode extends AbstractSysCallPrimitiveNode implements UnaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doGetEGid(@SuppressWarnings("unused") final Object receiver,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return getValue(lib);
        }

        @Override
        protected final String getFunctionName() {
            return "getegid";
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetEUid")
    protected abstract static class PrimGetEUidNode extends AbstractSysCallPrimitiveNode implements UnaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doGetEUid(@SuppressWarnings("unused") final Object receiver,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return getValue(lib);
        }

        @Override
        protected final String getFunctionName() {
            return "geteuid";
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetGid")
    protected abstract static class PrimGetGidNode extends AbstractSysCallPrimitiveNode implements UnaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doGetGid(@SuppressWarnings("unused") final Object receiver,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return getValue(lib);
        }

        @Override
        protected final String getFunctionName() {
            return "getgid";
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetPGid")
    protected abstract static class PrimGetPGidNode extends AbstractSysCallPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doGetPGid(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib,
                        @Cached final BranchProfile errorProfile) {
            return failIfMinusOne(getValue(lib, pid), errorProfile);
        }

        @Override
        protected final String getFunctionName() {
            return "getpgid";
        }

        @Override
        protected final String getFunctionSignature() {
            return "(SINT32):SINT32";
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetPGrp")
    protected abstract static class PrimGetPGrpNode extends AbstractSysCallPrimitiveNode implements UnaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doGetPGrp(@SuppressWarnings("unused") final Object receiver,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib,
                        @Cached final BranchProfile errorProfile) {
            return failIfMinusOne(getValue(lib), errorProfile);
        }

        @Override
        protected final String getFunctionName() {
            return "getpgrp";
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetPPid")
    protected abstract static class PrimGetPPidNode extends AbstractSysCallPrimitiveNode implements UnaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doGetPPid(@SuppressWarnings("unused") final Object receiver,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return getValue(lib);
        }

        @Override
        protected final String getFunctionName() {
            return "getppid";
        }
    }

    @GenerateNodeFactory
    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(names = "primitiveGetStdErrHandle")
    protected abstract static class PrimGetStdErrHandleNode extends AbstractPrimitiveNode implements UnaryPrimitiveWithoutFallback {
        @Specialization
        protected static final long doGet(@SuppressWarnings("unused") final Object receiver) {
            return FilePlugin.STDIO_HANDLES.ERROR;
        }
    }

    @GenerateNodeFactory
    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(names = "primitiveGetStdInHandle")
    protected abstract static class PrimGetStdInHandleNode extends AbstractPrimitiveNode implements UnaryPrimitiveWithoutFallback {
        @Specialization
        protected static final long doGet(@SuppressWarnings("unused") final Object receiver) {
            return FilePlugin.STDIO_HANDLES.IN;
        }
    }

    @GenerateNodeFactory
    @NodeInfo(cost = NodeCost.NONE)
    @SqueakPrimitive(names = "primitiveGetStdOutHandle")
    protected abstract static class PrimGetStdOutHandleNode extends AbstractPrimitiveNode implements UnaryPrimitiveWithoutFallback {
        @Specialization
        protected static final long doGet(@SuppressWarnings("unused") final Object receiver) {
            return FilePlugin.STDIO_HANDLES.OUT;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetUid")
    protected abstract static class PrimGetUidNode extends AbstractSysCallPrimitiveNode implements UnaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doGetUid(@SuppressWarnings("unused") final Object receiver,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return getValue(lib);
        }

        @Override
        protected final String getFunctionName() {
            return "getuid";
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveRealpath")
    protected abstract static class PrimRealpathNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "pathString.isByteType()")
        protected static final NativeObject doRealpath(@SuppressWarnings("unused") final Object receiver, final NativeObject pathString,
                        @Cached final BranchProfile errorProfile,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            try {
                return image.asByteString(image.env.getPublicTruffleFile(pathString.asStringUnsafe()).getCanonicalFile().getPath());
            } catch (final IOException e) {
                errorProfile.enter();
                throw PrimitiveFailed.GENERIC_ERROR;
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSendSigabrtTo")
    protected abstract static class PrimSendSigabrtToNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doSendSigabrtTo(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGABRT);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSendSigalrmTo")
    protected abstract static class PrimSendSigalrmToNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doSendSigalrmTo(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGALRM);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSendSigchldTo")
    protected abstract static class PrimSendSigchldToNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = {"supportsNFI", "isMacOS()"})
        protected final long doSendSigchldToMacOS(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGCHLD_MACOS);
        }

        @Specialization(guards = {"supportsNFI", "isLinux()"})
        protected final long doSendSigchldToUnix(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGCHLD_UNIX);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSendSigcontTo")
    protected abstract static class PrimSendSigcontToNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doSendSigcontTo(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGCONT);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSendSighupTo")
    protected abstract static class PrimSendSighupToNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doSendSighupTo(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGHUP);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSendSigintTo")
    protected abstract static class PrimSendSigintToNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doSendSigintTo(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGINT);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSendSigkillTo")
    protected abstract static class PrimSendSigkillToNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doSendSigkillTo(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGKILL);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSendSigpipeTo")
    protected abstract static class PrimSendSigpipeToNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doSendSigpipeTo(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGPIPE);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSendSigquitTo")
    protected abstract static class PrimSendSigquitToNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doSendSigquitTo(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGQUIT);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSendSigstopTo")
    protected abstract static class PrimSendSigstopToNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doSendSigstopTo(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGSTOP);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSendSigtermTo")
    protected abstract static class PrimSendSigtermToNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doSendSigtermTo(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGTERM);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSendSigusr1To")
    protected abstract static class PrimSendSigusr1ToNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = {"supportsNFI", "isMacOS()"})
        protected final long doSendSigusr1ToMacOS(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGUSR1_MACOS);
        }

        @Specialization(guards = {"supportsNFI", "isLinux()"})
        protected final long doSendSigusr1ToUnix(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGUSR1_UNIX);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSendSigusr2To")
    protected abstract static class PrimSendSigusr2ToNode extends AbstractKillPrimitiveNode implements BinaryPrimitive {
        @Specialization(guards = {"supportsNFI", "isMacOS()"})
        protected final long doSendSigusr2ToMacOS(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGUSR2_MACOS);
        }

        @Specialization(guards = {"supportsNFI", "isLinux()"})
        protected final long doSendSigusr2ToUnix(@SuppressWarnings("unused") final Object receiver, final long pid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, SIGNALS.SIGUSR2_UNIX);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSetPGid")
    protected abstract static class PrimSetPGidNode extends AbstractSysCallPrimitiveNode implements TernaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doSetPGid(@SuppressWarnings("unused") final Object receiver, final long pid, final long pgid,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, pid, pgid);
        }

        @Override
        protected final String getFunctionName() {
            return "setpgid";
        }

        @Override
        protected String getFunctionSignature() {
            return "(SINT32,SINT32):SINT32";
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSetPGrp")
    protected abstract static class PrimSetPGrpNode extends AbstractSysCallPrimitiveNode implements UnaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doSetPGid(@SuppressWarnings("unused") final Object receiver,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib) {
            return setValue(lib, 0, 0);
        }

        @Override
        protected final String getFunctionName() {
            return "setpgid";
        }

        @Override
        protected String getFunctionSignature() {
            return "(SINT32,SINT32):SINT32";
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSetSid")
    protected abstract static class PrimSetSidNode extends AbstractSysCallPrimitiveNode implements UnaryPrimitive {
        @Specialization(guards = "supportsNFI")
        protected final long doSetSid(@SuppressWarnings("unused") final Object receiver,
                        @CachedLibrary("getSysCallObject()") final InteropLibrary lib,
                        @Cached final BranchProfile errorProfile) {
            return failIfMinusOne(getValue(lib), errorProfile);
        }

        @Override
        protected final String getFunctionName() {
            return "setsid";
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSigChldNumber")
    protected abstract static class PrimSigChldNumberNode extends AbstractPrimitiveNode implements UnaryPrimitiveWithoutFallback {
        @Specialization(guards = "isMacOS()")
        protected static final long doSigChldNumberMacOS(@SuppressWarnings("unused") final Object receiver) {
            return SIGNALS.SIGCHLD_MACOS;
        }

        @Specialization(guards = "isLinux()")
        protected static final long doSigChldNumberUnix(@SuppressWarnings("unused") final Object receiver) {
            return SIGNALS.SIGCHLD_UNIX;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSigHupNumber")
    protected abstract static class PrimSigHupNumberNode extends AbstractPrimitiveNode implements UnaryPrimitiveWithoutFallback {
        @Specialization
        protected static final long doSigHupNumber(@SuppressWarnings("unused") final Object receiver) {
            return SIGNALS.SIGHUP;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSigIntNumber")
    protected abstract static class PrimSigIntNumberNode extends AbstractPrimitiveNode implements UnaryPrimitiveWithoutFallback {
        @Specialization
        protected static final long doSigIntNumber(@SuppressWarnings("unused") final Object receiver) {
            return SIGNALS.SIGINT;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSigKillNumber")
    protected abstract static class PrimSigKillNumberNode extends AbstractPrimitiveNode implements UnaryPrimitiveWithoutFallback {
        @Specialization
        protected static final long doSigKillNumber(@SuppressWarnings("unused") final Object receiver) {
            return SIGNALS.SIGKILL;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSigPipeNumber")
    protected abstract static class PrimSigPipeNumberNode extends AbstractPrimitiveNode implements UnaryPrimitiveWithoutFallback {
        @Specialization
        protected static final long doSigPipeNumber(@SuppressWarnings("unused") final Object receiver) {
            return SIGNALS.SIGPIPE;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSigQuitNumber")
    protected abstract static class PrimSigQuitNumberNode extends AbstractPrimitiveNode implements UnaryPrimitiveWithoutFallback {
        @Specialization
        protected static final long doSigQuitNumber(@SuppressWarnings("unused") final Object receiver) {
            return SIGNALS.SIGQUIT;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSigTermNumber")
    protected abstract static class PrimSigTermNumberNode extends AbstractPrimitiveNode implements UnaryPrimitiveWithoutFallback {
        @Specialization
        protected static final long doSigTermNumber(@SuppressWarnings("unused") final Object receiver) {
            return SIGNALS.SIGTERM;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSigUsr1Number")
    protected abstract static class PrimSigUsr1NumberNode extends AbstractPrimitiveNode implements UnaryPrimitiveWithoutFallback {
        @Specialization(guards = "isMacOS()")
        protected static final long doSigUsr1NumberMacOS(@SuppressWarnings("unused") final Object receiver) {
            return SIGNALS.SIGUSR1_MACOS;
        }

        @Specialization(guards = "isLinux()")
        protected static final long doSigUsr1NumberUnix(@SuppressWarnings("unused") final Object receiver) {
            return SIGNALS.SIGUSR1_UNIX;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSigUsr2Number")
    protected abstract static class PrimSigUsr2NumberNode extends AbstractPrimitiveNode implements UnaryPrimitiveWithoutFallback {
        @Specialization(guards = "isLinux()")
        protected static final long doSigUsr2NumberLinux(@SuppressWarnings("unused") final Object receiver) {
            return SIGNALS.SIGUSR2_UNIX;
        }

        @Specialization(guards = "isMacOS()")
        protected static final long doSigUsr2NumberMacOS(@SuppressWarnings("unused") final Object receiver) {
            return SIGNALS.SIGUSR2_MACOS;
        }
    }

    private static final class SIGNALS {
        private static final int SIG_DFL = 0;
        private static final int SIGHUP = 1;
        private static final int SIGINT = 2;
        private static final int SIGQUIT = 3;
        private static final int SIGABRT = 6;
        private static final int SIGKILL = 9;
        private static final int SIGUSR1_UNIX = 10;
        private static final int SIGUSR1_MACOS = 30;
        private static final int SIGUSR2_UNIX = 12;
        private static final int SIGUSR2_MACOS = 31;
        private static final int SIGPIPE = 13;
        private static final int SIGALRM = 14;
        private static final int SIGTERM = 15;
        private static final int SIGCHLD_UNIX = 17;
        private static final int SIGCHLD_MACOS = 20;
        private static final int SIGCONT = 18;
        private static final int SIGSTOP = 19;
    }

    @TruffleBoundary
    private static String systemGetEnv(final Env env, final String key) {
        return env.getEnvironment().get(key);
    }

    @Override
    public List<? extends NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        final List<NodeFactory<? extends AbstractPrimitiveNode>> factories = new ArrayList<>();
        factories.addAll(UnixOSProcessPluginFactory.getFactories());
        factories.addAll(AbstractOSProcessPluginFactory.getFactories());
        return factories;
    }
}
