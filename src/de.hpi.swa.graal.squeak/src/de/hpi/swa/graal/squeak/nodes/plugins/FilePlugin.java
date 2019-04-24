package de.hpi.swa.graal.squeak.nodes.plugins;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.FloatObject;
import de.hpi.swa.graal.squeak.model.LargeIntegerObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.QuaternaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.QuinaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.graal.squeak.shared.SqueakLanguageConfig;
import de.hpi.swa.graal.squeak.util.ArrayConversionUtils;

public final class FilePlugin extends AbstractPrimitiveFactoryHolder {
    private static final TruffleLogger LOG = TruffleLogger.getLogger(SqueakLanguageConfig.ID, FilePlugin.class);

    public static final class STDIO_HANDLES {
        public static final long IN = 0;
        public static final long OUT = 1;
        public static final long ERROR = 2;
        public static final long[] ALL = new long[]{STDIO_HANDLES.IN, STDIO_HANDLES.OUT, STDIO_HANDLES.ERROR};
    }

    @Override
    public List<? extends NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return FilePluginFactory.getFactories();
    }

    protected abstract static class AbstractFilePluginPrimitiveNode extends AbstractPrimitiveNode {
        protected AbstractFilePluginPrimitiveNode(final CompiledMethodObject method) {
            super(method);
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        protected SeekableByteChannel getFileOrPrimFail(final long fileDescriptor) {
            final SeekableByteChannel handle = method.image.filePluginHandles.get(fileDescriptor);
            if (handle == null) {
                throw new PrimitiveFailed();
            }
            return handle;
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final String asString(final NativeObject obj) {
            return new String(obj.getByteStorage());
        }

        protected final TruffleFile asTruffleFile(final NativeObject obj) {
            return method.image.env.getTruffleFile(asString(obj));
        }

        protected static final boolean inBounds(final long startIndex, final long count, final int slotSize) {
            return startIndex >= 1 && startIndex + count - 1 <= slotSize;
        }
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    protected static Object createFileHandleOrPrimFail(final SqueakImageContext image, final TruffleFile truffleFile, final Boolean writableFlag) {
        try {
            final EnumSet<StandardOpenOption> options;
            if (writableFlag) {
                options = EnumSet.<StandardOpenOption> of(StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.CREATE);
            } else {
                options = EnumSet.<StandardOpenOption> of(StandardOpenOption.READ);
            }
            final SeekableByteChannel file = truffleFile.newByteChannel(options);
            final long fileId = file.hashCode();
            image.filePluginHandles.put(fileId, file);
            LOG.fine(() -> "File Handle Creation SUCCEEDED: " + truffleFile.getPath() + " (fileID: " + fileId + ", " + ", writable: " + writableFlag + ")");
            return fileId;
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            LOG.fine(() -> "File Handle Creation FAILED: " + truffleFile.getPath() + " (writable: " + writableFlag + ")");
            throw new PrimitiveFailed();
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveDirectoryCreate")
    protected abstract static class PrimDirectoryCreateNode extends AbstractFilePluginPrimitiveNode implements BinaryPrimitive {

        protected PrimDirectoryCreateNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "fullPath.isByteType()")
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final Object doCreate(final PointersObject receiver, final NativeObject fullPath) {
            try {
                asTruffleFile(fullPath).createDirectory();
                return receiver;
            } catch (IOException | UnsupportedOperationException | SecurityException e) {
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveDirectoryDelete")
    protected abstract static class PrimDirectoryDeleteNode extends AbstractFilePluginPrimitiveNode implements BinaryPrimitive {

        protected PrimDirectoryDeleteNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "fullPath.isByteType()")
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final Object doDelete(final PointersObject receiver, final NativeObject fullPath) {
            try {
                asTruffleFile(fullPath).delete();
                return receiver;
            } catch (IOException | UnsupportedOperationException | SecurityException e) {
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveDirectoryDelimitor")
    protected abstract static class PrimDirectoryDelimitorNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        protected PrimDirectoryDelimitorNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected static final char doDelimitor(@SuppressWarnings("unused") final AbstractSqueakObject receiver) {
            return File.separatorChar;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveDirectoryEntry")
    protected abstract static class PrimDirectoryEntryNode extends AbstractFilePluginPrimitiveNode implements TernaryPrimitive {

        protected PrimDirectoryEntryNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"fullPath.isByteType()", "fName.isByteType()"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final Object doEntry(@SuppressWarnings("unused") final PointersObject receiver, final NativeObject fullPath, final NativeObject fName) {
            final String pathName = asString(fullPath);
            final String fileName = asString(fName);
            final File path;
            if (".".equals(fileName)) {
                path = new File(pathName);
            } else {
                path = new File(pathName + File.separator + fileName);
            }
            if (path.exists()) {
                final NativeObject pathNameNative = method.image.asByteString(path.getName());
                final long pathLastModified = path.lastModified();
                final boolean pathIsDirectory = method.image.asBoolean(path.isDirectory());
                final long pathLength = path.length();
                return method.image.asArrayOfObjects(pathNameNative, pathLastModified, pathLastModified, pathIsDirectory, pathLength);
            }
            return method.image.nil;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveDirectoryLookup")
    protected abstract static class PrimDirectoryLookupNode extends AbstractFilePluginPrimitiveNode implements TernaryPrimitive {

        protected PrimDirectoryLookupNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"longIndex > 0", "nativePathName.isByteType()", "nativePathName.getByteLength() == 0"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final Object doLookupEmptyString(@SuppressWarnings("unused") final PointersObject receiver, @SuppressWarnings("unused") final NativeObject nativePathName, final long longIndex) {
            assert method.image.os.isWindows() : "Unexpected empty path on a non-Windows system.";
            final ArrayList<File> fileList = new ArrayList<>();
            for (final Path path : FileSystems.getDefault().getRootDirectories()) {
                fileList.add(path.toFile());
            }
            final File[] files = fileList.toArray(new File[fileList.size()]);
            final int index = (int) longIndex - 1;
            if (index < files.length) {
                final File file = files[index];
                // Use getPath here, getName returns empty string on root path.
                // Squeak strips the trailing backslash from C:\ on Windows.
                final NativeObject pathNameNative = method.image.asByteString(file.getPath().replace("\\", ""));
                final long pathLastModified = file.lastModified();
                final boolean pathIsDirectory = method.image.asBoolean(file.isDirectory());
                final long pathLength = file.length();
                return method.image.asArrayOfObjects(pathNameNative, pathLastModified, pathLastModified, pathIsDirectory, pathLength);
            } else {
                return method.image.nil;
            }
        }

        @Specialization(guards = {"longIndex > 0", "nativePathName.isByteType()", "nativePathName.getByteLength() > 0"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final Object doLookup(@SuppressWarnings("unused") final PointersObject receiver, final NativeObject nativePathName, final long longIndex) {
            String pathName = asString(nativePathName);
            if (method.image.os.isWindows() && !pathName.endsWith("\\")) {
                pathName += "\\"; // new File("C:") will fail, we need to add a trailing backslash.
            }
            final File directory = new File(pathName);
            if (!directory.isDirectory()) {
                PrimitiveFailed.andTransferToInterpreter();
            }
            final File[] files = directory.listFiles();
            final int index = (int) longIndex - 1;
            if (files != null && index < files.length) {
                final File file = files[index];
                final NativeObject pathNameNative = method.image.asByteString(file.getName());
                final long pathLastModified = file.lastModified();
                final boolean pathIsDirectory = method.image.asBoolean(file.isDirectory());
                final long pathLength = file.length();
                return method.image.asArrayOfObjects(pathNameNative, pathLastModified, pathLastModified, pathIsDirectory, pathLength);
            } else {
                return method.image.nil;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"longIndex <= 0"})
        protected final Object doNil(final PointersObject receiver, final NativeObject nativePathName, final long longIndex) {
            return method.image.nil;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveDirectoryGetMacTypeAndCreator")
    protected abstract static class PrimDirectoryGetMacTypeAndCreatorNode extends AbstractPrimitiveNode implements QuaternaryPrimitive {
        protected PrimDirectoryGetMacTypeAndCreatorNode(final CompiledMethodObject method) {
            super(method);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected static final Object doNothing(final PointersObject receiver, final NativeObject fileName, final NativeObject typeString, final NativeObject creatorString) {
            /*
             * Get the Macintosh file type and creator info for the file with the given name. Fails
             * if the file does not exist or if the type and creator type arguments are not strings
             * of length 4. This primitive is Mac specific; it is a noop on other platforms.
             */
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveDirectorySetMacTypeAndCreator")
    protected abstract static class PrimDirectorySetMacTypeAndCreatorNode extends AbstractPrimitiveNode implements QuaternaryPrimitive {
        protected PrimDirectorySetMacTypeAndCreatorNode(final CompiledMethodObject method) {
            super(method);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected static final Object doNothing(final PointersObject receiver, final NativeObject fileName, final NativeObject typeString, final NativeObject creatorString) {
            /*
             * Set the Macintosh file type and creator info for the file with the given name. Fails
             * if the file does not exist or if the type and creator type arguments are not strings
             * of length 4. Does nothing on other platforms (where the underlying primitive is a
             * noop).
             */
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileAtEnd")
    protected abstract static class PrimFileAtEndNode extends AbstractFilePluginPrimitiveNode implements BinaryPrimitive {

        protected PrimFileAtEndNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final Object doAtEnd(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor) {
            try {
                final SeekableByteChannel file = getFileOrPrimFail(fileDescriptor);
                return method.image.asBoolean(file.position() >= file.size() - 1);
            } catch (final IOException e) {
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileClose")
    protected abstract static class PrimFileCloseNode extends AbstractFilePluginPrimitiveNode implements BinaryPrimitive {

        protected PrimFileCloseNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final Object doClose(final PointersObject receiver, final long fileDescriptor) {
            try {
                getFileOrPrimFail(fileDescriptor).close();
                LOG.fine(() -> "File Closed SUCCEEDED: " + fileDescriptor);
            } catch (final IOException e) {
                LOG.fine(() -> "File Closed FAILED: " + fileDescriptor);
                throw new PrimitiveFailed();
            }
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileDelete")
    protected abstract static class PrimFileDeleteNode extends AbstractFilePluginPrimitiveNode implements BinaryPrimitive {

        protected PrimFileDeleteNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "nativeFileName.isByteType()")
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doDelete(final PointersObject receiver, final NativeObject nativeFileName) {
            final File file = new File(asString(nativeFileName));
            if (!file.delete()) {
                throw new PrimitiveFailed();
            }
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileFlush")
    protected abstract static class PrimFileFlushNode extends AbstractFilePluginPrimitiveNode implements BinaryPrimitive {

        protected PrimFileFlushNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected static final Object doFlush(final PointersObject receiver, @SuppressWarnings("unused") final long fileDescriptor) {
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileGetPosition")
    protected abstract static class PrimFileGetPositionNode extends AbstractFilePluginPrimitiveNode implements BinaryPrimitive {

        protected PrimFileGetPositionNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final long doGet(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor) {
            try {
                return getFileOrPrimFail(fileDescriptor).position();
            } catch (final IOException e) {
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileOpen")
    protected abstract static class PrimFileOpenNode extends AbstractFilePluginPrimitiveNode implements TernaryPrimitive {

        protected PrimFileOpenNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = "nativeFileName.isByteType()")
        protected final Object doOpen(@SuppressWarnings("unused") final PointersObject receiver, final NativeObject nativeFileName, final Boolean writableFlag) {
            return createFileHandleOrPrimFail(method.image, asTruffleFile(nativeFileName), writableFlag);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileRead")
    protected abstract static class PrimFileReadNode extends AbstractFilePluginPrimitiveNode implements QuinaryPrimitive {

        protected PrimFileReadNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"target.isByteType()", "inBounds(startIndex, longCount, target.getByteLength())"})
        protected final Object doReadBytes(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor, final NativeObject target,
                        final long startIndex, final long longCount) {
            final int count = (int) longCount;
            final ByteBuffer dst = allocate(count);
            try {
                final long read = readFrom(fileDescriptor, dst);
                for (int index = 0; index < read; index++) {
                    target.getByteStorage()[(int) (startIndex - 1 + index)] = getFrom(dst, index);
                }
                return Math.max(read, 0L); // `read` can be `-1`, Squeak expects zero.
            } catch (final IOException e) {
                throw new PrimitiveFailed();
            }
        }

        @Specialization(guards = {"target.isIntType()", "inBounds(startIndex, longCount, target.getIntLength())"})
        protected final Object doReadInts(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor, final NativeObject target,
                        final long startIndex, final long longCount) {
            final int count = (int) longCount;
            final ByteBuffer dst = allocate(count * ArrayConversionUtils.INTEGER_BYTE_SIZE);
            try {
                final long readBytes = readFrom(fileDescriptor, dst);
                assert readBytes % ArrayConversionUtils.INTEGER_BYTE_SIZE == 0;
                final long readInts = readBytes / ArrayConversionUtils.INTEGER_BYTE_SIZE;
                for (int index = 0; index < readInts; index++) {
                    final int offset = index * ArrayConversionUtils.INTEGER_BYTE_SIZE;
                    target.getIntStorage()[(int) (startIndex - 1 + index)] = getFromUnsigned(dst, offset + 3) << 24 |
                                    getFromUnsigned(dst, offset + 2) << 16 |
                                    getFromUnsigned(dst, offset + 1) << 8 |
                                    getFromUnsigned(dst, offset);
                }
                return Math.max(readInts, 0L); // `read` can be `-1`, Squeak expects zero.
            } catch (final IOException e) {
                throw new PrimitiveFailed();
            }
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static ByteBuffer allocate(final int count) {
            return ByteBuffer.allocate(count);
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private int readFrom(final long fileDescriptor, final ByteBuffer dst) throws IOException {
            return getFileOrPrimFail(fileDescriptor).read(dst);
        }

        @TruffleBoundary(transferToInterpreterOnException = false)
        private static byte getFrom(final ByteBuffer dst, final int index) {
            return dst.get(index);
        }

        private static int getFromUnsigned(final ByteBuffer dst, final int index) {
            return Byte.toUnsignedInt(getFrom(dst, index));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileRename")
    protected abstract static class PrimFileRenameNode extends AbstractFilePluginPrimitiveNode implements TernaryPrimitive {

        protected PrimFileRenameNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"oldName.isByteType()", "newName.isByteType()"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final Object doRename(final PointersObject receiver, final NativeObject oldName, final NativeObject newName) {
            try {
                asTruffleFile(oldName).move(asTruffleFile(newName));
            } catch (final IOException e) {
                throw new PrimitiveFailed();
            }
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileSetPosition")
    protected abstract static class PrimFileSetPositionNode extends AbstractFilePluginPrimitiveNode implements TernaryPrimitive {

        protected PrimFileSetPositionNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final Object doSet(final PointersObject receiver, final long fileDescriptor, final long position) {
            try {
                getFileOrPrimFail(fileDescriptor).position(position);
            } catch (IllegalArgumentException | IOException e) {
                throw new PrimitiveFailed();
            }
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileSize")
    protected abstract static class PrimFileSizeNode extends AbstractFilePluginPrimitiveNode implements BinaryPrimitive {

        protected PrimFileSizeNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final long doSize(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor) {
            try {
                return getFileOrPrimFail(fileDescriptor).size();
            } catch (final IOException e) {
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileStdioHandles")
    protected abstract static class PrimFileStdioHandlesNode extends AbstractFilePluginPrimitiveNode implements UnaryPrimitive {
        protected PrimFileStdioHandlesNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        protected final Object getHandles(@SuppressWarnings("unused") final ClassObject receiver) {
            return method.image.asArrayOfLongs(STDIO_HANDLES.ALL);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileTruncate")
    protected abstract static class PrimFileTruncateNode extends AbstractFilePluginPrimitiveNode implements TernaryPrimitive {
        protected PrimFileTruncateNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final Object doTruncate(final PointersObject receiver, final long fileDescriptor, final long to) {
            try {
                getFileOrPrimFail(fileDescriptor).truncate(to);
            } catch (IllegalArgumentException | IOException e) {
                throw new PrimitiveFailed();
            }
            return receiver;
        }
    }

    @ImportStatic({STDIO_HANDLES.class, FloatObject.class})
    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFileWrite")
    protected abstract static class PrimFileWriteNode extends AbstractFilePluginPrimitiveNode implements QuinaryPrimitive {

        protected PrimFileWriteNode(final CompiledMethodObject method) {
            super(method);
        }

        @Specialization(guards = {"!isStdioFileDescriptor(fileDescriptor)", "content.isByteType()", "inBounds(startIndex, count, content.getByteLength())"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final long doWriteByte(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor, final NativeObject content, final long startIndex,
                        final long count) {
            return fileWriteFromAt(fileDescriptor, count, content.getByteStorage(), startIndex, 1);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"fileDescriptor == OUT", "content.isByteType()", "inBounds(startIndex, count, content.getByteLength())"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected long doWriteByteToStdout(final PointersObject receiver, final long fileDescriptor, final NativeObject content, final long startIndex, final long count) {
            return fileWriteToPrintWriter(method.image.getOutput(), content, startIndex, count);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"fileDescriptor == ERROR", "content.isByteType()", "inBounds(startIndex, count, content.getByteLength())"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected long doWriteByteToStderr(final PointersObject receiver, final long fileDescriptor, final NativeObject content, final long startIndex, final long count) {
            return fileWriteToPrintWriter(method.image.getError(), content, startIndex, count);
        }

        @Specialization(guards = {"!isStdioFileDescriptor(fileDescriptor)", "content.isIntType()", "inBounds(startIndex, count, content.getIntLength())"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final long doWriteInt(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor, final NativeObject content, final long startIndex,
                        final long count) {
            return fileWriteFromAt(fileDescriptor, count, ArrayConversionUtils.bytesFromInts(content.getIntStorage()), startIndex, 4);
        }

        @Specialization(guards = {"!isStdioFileDescriptor(fileDescriptor)", "inBounds(startIndex, count, content.size())"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final long doWriteLargeInteger(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor, final LargeIntegerObject content, final long startIndex,
                        final long count) {
            return fileWriteFromAt(fileDescriptor, count, content.getBytes(), startIndex, 1);
        }

        @Specialization(guards = {"!isStdioFileDescriptor(fileDescriptor)", "inBounds(startIndex, count, WORD_LENGTH)"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final long doWriteDouble(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor, final double content, final long startIndex, final long count) {
            return fileWriteFromAt(fileDescriptor, count, FloatObject.getBytes(content), startIndex, 8);
        }

        @Specialization(guards = {"!isStdioFileDescriptor(fileDescriptor)", "inBounds(startIndex, count, WORD_LENGTH)"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected final long doWriteFloatObject(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor, final FloatObject content, final long startIndex,
                        final long count) {
            return fileWriteFromAt(fileDescriptor, count, content.getBytes(), startIndex, 8);
        }

        protected static final boolean isStdioFileDescriptor(final long fileDescriptor) {
            return fileDescriptor == STDIO_HANDLES.IN || fileDescriptor == STDIO_HANDLES.OUT || fileDescriptor == STDIO_HANDLES.ERROR;
        }

        private long fileWriteFromAt(final long fileDescriptor, final long count, final byte[] bytes, final long startIndex, final int elementSize) {
            final int byteStart = (int) (startIndex - 1) * elementSize;
            final int byteEnd = Math.min(byteStart + (int) count, bytes.length) * elementSize;
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.position(byteStart);
            buffer.limit(byteEnd);
            final int written;
            try {
                written = getFileOrPrimFail(fileDescriptor).write(buffer);
            } catch (final IOException e) {
                throw new PrimitiveFailed();
            }
            return written / elementSize;
        }

        private static long fileWriteToPrintWriter(final PrintWriter printWriter, final NativeObject content, final long startIndex, final long count) {
            final String string = asString(content);
            final int byteStart = (int) (startIndex - 1);
            final int byteEnd = Math.min(byteStart + (int) count, string.length());
            printWriter.write(string, byteStart, Math.max(byteEnd - byteStart, 0));
            printWriter.flush();
            return byteEnd - byteStart;
        }
    }
}
