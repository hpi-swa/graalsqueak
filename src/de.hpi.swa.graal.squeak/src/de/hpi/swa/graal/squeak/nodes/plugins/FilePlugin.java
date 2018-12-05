package de.hpi.swa.graal.squeak.nodes.plugins;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.FloatObject;
import de.hpi.swa.graal.squeak.model.LargeIntegerObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectAtPut0Node;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.graal.squeak.util.ArrayConversionUtils;

public final class FilePlugin extends AbstractPrimitiveFactoryHolder {
    private static final Map<Long, SeekableByteChannel> files = new HashMap<>();

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

    @TruffleBoundary
    private static SeekableByteChannel getFileOrPrimFail(final long fileDescriptor) {
        final SeekableByteChannel handle = files.get(fileDescriptor);
        if (handle == null) {
            throw new PrimitiveFailed();
        }
        return handle;
    }

    protected abstract static class AbstractFilePluginPrimitiveNode extends AbstractPrimitiveNode {
        protected AbstractFilePluginPrimitiveNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @TruffleBoundary
        protected static final String asString(final NativeObject obj) {
            return new String(obj.getByteStorage());
        }

        protected final TruffleFile asTruffleFile(final NativeObject obj) {
            return code.image.env.getTruffleFile(asString(obj));
        }
    }

    @TruffleBoundary
    protected static Object createFileHandleOrPrimFail(final TruffleFile truffleFile, final Boolean writableFlag) {
        try {
            final EnumSet<StandardOpenOption> options;
            if (writableFlag) {
                options = EnumSet.<StandardOpenOption> of(StandardOpenOption.WRITE, StandardOpenOption.READ, StandardOpenOption.CREATE);
            } else {
                options = EnumSet.<StandardOpenOption> of(StandardOpenOption.READ);
            }
            final SeekableByteChannel file = truffleFile.newByteChannel(options);
            final long fileId = file.hashCode();
            files.put(fileId, file);
            return fileId;
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            throw new PrimitiveFailed();
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveDirectoryCreate")
    protected abstract static class PrimDirectoryCreateNode extends AbstractFilePluginPrimitiveNode {

        protected PrimDirectoryCreateNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = "fullPath.isByteType()")
        @TruffleBoundary
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
    @SqueakPrimitive(name = "primitiveDirectoryDelete")
    protected abstract static class PrimDirectoryDeleteNode extends AbstractFilePluginPrimitiveNode {

        protected PrimDirectoryDeleteNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = "fullPath.isByteType()")
        @TruffleBoundary
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
    @SqueakPrimitive(name = "primitiveDirectoryDelimitor")
    protected abstract static class PrimDirectoryDelimitorNode extends AbstractPrimitiveNode {

        protected PrimDirectoryDelimitorNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        protected static final char doDelimitor(@SuppressWarnings("unused") final Object receiver) {
            return File.separatorChar;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveDirectoryEntry")
    protected abstract static class PrimDirectoryEntryNode extends AbstractFilePluginPrimitiveNode {

        protected PrimDirectoryEntryNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = {"fullPath.isByteType()", "fName.isByteType()"})
        @TruffleBoundary
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
                final Object[] result = new Object[]{path.getName(), path.lastModified(), path.lastModified(), path.isDirectory(), path.length()};
                return code.image.wrap(result);
            }
            return code.image.nil;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveDirectoryLookup")
    protected abstract static class PrimDirectoryLookupNode extends AbstractFilePluginPrimitiveNode {

        protected PrimDirectoryLookupNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = {"nativePathName.isByteType()", "longIndex >= 0"})
        @TruffleBoundary
        protected final Object doLookup(@SuppressWarnings("unused") final PointersObject receiver, final NativeObject nativePathName, final long longIndex) {
            final int index = (int) longIndex;
            String pathName = asString(nativePathName);
            if (pathName.length() == 0) {
                pathName = "/";
            }
            final File directory = new File(pathName);
            if (!directory.isDirectory()) {
                throw new PrimitiveFailed();
            }
            final File[] paths = directory.listFiles();
            if (paths != null && index < paths.length) {
                final File path = paths[index];
                final Object[] result = new Object[]{path.getName(), path.lastModified(), path.lastModified(), path.isDirectory(), path.length()};
                return code.image.wrap(result);
            }
            return code.image.nil;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveDirectoryGetMacTypeAndCreator")
    protected abstract static class PrimDirectoryGetMacTypeAndCreatorNode extends AbstractPrimitiveNode {
        protected PrimDirectoryGetMacTypeAndCreatorNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
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
    @SqueakPrimitive(name = "primitiveDirectorySetMacTypeAndCreator")
    protected abstract static class PrimDirectorySetMacTypeAndCreatorNode extends AbstractPrimitiveNode {
        protected PrimDirectorySetMacTypeAndCreatorNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
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
    @SqueakPrimitive(name = "primitiveFileAtEnd")
    protected abstract static class PrimFileAtEndNode extends AbstractPrimitiveNode {

        protected PrimFileAtEndNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        @TruffleBoundary
        protected final Object doAtEnd(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor) {
            try {
                final SeekableByteChannel file = getFileOrPrimFail(fileDescriptor);
                return code.image.wrap(file.position() >= file.size() - 1);
            } catch (IOException e) {
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFileClose")
    protected abstract static class PrimFileCloseNode extends AbstractPrimitiveNode {

        protected PrimFileCloseNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        @TruffleBoundary
        protected static final Object doClose(final PointersObject receiver, final long fileDescriptor) {
            try {
                getFileOrPrimFail(fileDescriptor).close();
            } catch (IOException e) {
                throw new PrimitiveFailed();
            }
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFileDelete")
    protected abstract static class PrimFileDeleteNode extends AbstractFilePluginPrimitiveNode {

        protected PrimFileDeleteNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = "nativeFileName.isByteType()")
        @TruffleBoundary
        protected static final Object doDelete(final PointersObject receiver, final NativeObject nativeFileName) {
            final File file = new File(asString(nativeFileName));
            if (!file.delete()) {
                throw new PrimitiveFailed();
            }
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFileFlush")
    protected abstract static class PrimFileFlushNode extends AbstractPrimitiveNode {

        protected PrimFileFlushNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        protected static final Object doFlush(final PointersObject receiver, @SuppressWarnings("unused") final long fileDescriptor) {
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFileGetPosition")
    protected abstract static class PrimFileGetPositionNode extends AbstractPrimitiveNode {

        protected PrimFileGetPositionNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        @TruffleBoundary
        protected final Object doGet(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor) {
            try {
                return code.image.wrap(getFileOrPrimFail(fileDescriptor).position());
            } catch (IOException e) {
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFileOpen")
    protected abstract static class PrimFileOpenNode extends AbstractFilePluginPrimitiveNode {

        protected PrimFileOpenNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = "nativeFileName.isByteType()")
        protected final Object doOpen(@SuppressWarnings("unused") final PointersObject receiver, final NativeObject nativeFileName, final Boolean writableFlag) {
            return createFileHandleOrPrimFail(asTruffleFile(nativeFileName), writableFlag);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFileRead")
    protected abstract static class PrimFileReadNode extends AbstractPrimitiveNode {
        @Child private SqueakObjectAtPut0Node atPut0Node = SqueakObjectAtPut0Node.create();

        protected PrimFileReadNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        @TruffleBoundary
        protected final Object doRead(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor, final AbstractSqueakObject target, final long startIndex,
                        final long longCount) {
            final int count = (int) longCount;
            final ByteBuffer dst = ByteBuffer.allocate(count);
            try {
                final long read = getFileOrPrimFail(fileDescriptor).read(dst);
                for (int index = 0; index < read; index++) {
                    atPut0Node.execute(target, startIndex - 1 + index, dst.get(index) & 0xFFL);
                }
                return read;
            } catch (IOException e) {
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFileRename")
    protected abstract static class PrimFileRenameNode extends AbstractFilePluginPrimitiveNode {

        protected PrimFileRenameNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = {"oldName.isByteType()", "newName.isByteType()"})
        @TruffleBoundary
        protected final Object doRename(final PointersObject receiver, final NativeObject oldName, final NativeObject newName) {
            try {
                asTruffleFile(oldName).move(asTruffleFile(newName));
            } catch (IOException e) {
                throw new PrimitiveFailed();
            }
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFileSetPosition")
    protected abstract static class PrimFileSetPositionNode extends AbstractPrimitiveNode {

        protected PrimFileSetPositionNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        @TruffleBoundary
        protected static final Object doSet(final PointersObject receiver, final long fileDescriptor, final long position) {
            try {
                getFileOrPrimFail(fileDescriptor).position(position);
            } catch (IllegalArgumentException | IOException e) {
                throw new PrimitiveFailed();
            }
            return receiver;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFileSize")
    protected abstract static class PrimFileSizeNode extends AbstractPrimitiveNode {

        protected PrimFileSizeNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        @TruffleBoundary
        protected final Object doSize(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor) {
            try {
                return code.image.wrap(getFileOrPrimFail(fileDescriptor).size());
            } catch (IOException e) {
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFileStdioHandles")
    protected abstract static class PrimFileStdioHandlesNode extends AbstractPrimitiveNode {
        protected PrimFileStdioHandlesNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        protected final Object getHandles(@SuppressWarnings("unused") final ClassObject receiver) {
            return code.image.newList(STDIO_HANDLES.ALL);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFileTruncate")
    protected abstract static class PrimFileTruncateNode extends AbstractPrimitiveNode {
        protected PrimFileTruncateNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        @TruffleBoundary
        protected static final Object doTruncate(final PointersObject receiver, final long fileDescriptor, final long to) {
            try {
                getFileOrPrimFail(fileDescriptor).truncate(to);
            } catch (IllegalArgumentException | IOException e) {
                throw new PrimitiveFailed();
            }
            return receiver;
        }
    }

    @ImportStatic(STDIO_HANDLES.class)
    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFileWrite")
    protected abstract static class PrimFileWriteNode extends AbstractFilePluginPrimitiveNode {

        protected PrimFileWriteNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = {"content.isByteType()", "!isStdioFileDescriptor(fileDescriptor)"})
        @TruffleBoundary
        protected static final long doWriteByte(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor, final NativeObject content, final long startIndex,
                        final long count) {
            return fileWriteFromAt(fileDescriptor, count, content.getByteStorage(), startIndex, 1);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"content.isByteType()", "fileDescriptor == OUT"})
        @TruffleBoundary
        protected final long doWriteByteToStdout(final PointersObject receiver, final long fileDescriptor, final NativeObject content, final long startIndex, final long count) {
            return fileWriteToPrintWriter(code.image.getOutput(), content, startIndex, count);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"content.isByteType()", "fileDescriptor == ERROR"})
        @TruffleBoundary
        protected final long doWriteByteToStderr(final PointersObject receiver, final long fileDescriptor, final NativeObject content, final long startIndex, final long count) {
            return fileWriteToPrintWriter(code.image.getError(), content, startIndex, count);
        }

        @Specialization(guards = {"content.isIntType()", "!isStdioFileDescriptor(fileDescriptor)"})
        @TruffleBoundary
        protected static final long doWriteInt(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor, final NativeObject content, final long startIndex,
                        final long count) {
            return fileWriteFromAt(fileDescriptor, count, ArrayConversionUtils.bytesFromInts(content.getIntStorage()), startIndex, 4);
        }

        @Specialization(guards = {"!isStdioFileDescriptor(fileDescriptor)"})
        @TruffleBoundary
        protected static final long doWriteLargeInteger(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor, final LargeIntegerObject content, final long startIndex,
                        final long count) {
            return fileWriteFromAt(fileDescriptor, count, content.getBytes(), startIndex, 1);
        }

        @Specialization(guards = {"!isStdioFileDescriptor(fileDescriptor)"})
        @TruffleBoundary
        protected static final long doWriteDouble(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor, final double content, final long startIndex, final long count) {
            return fileWriteFromAt(fileDescriptor, count, FloatObject.getBytes(content), startIndex, 8);
        }

        @Specialization(guards = {"!isStdioFileDescriptor(fileDescriptor)"})
        @TruffleBoundary
        protected static final long doWriteFloatObject(@SuppressWarnings("unused") final PointersObject receiver, final long fileDescriptor, final FloatObject content, final long startIndex,
                        final long count) {
            return fileWriteFromAt(fileDescriptor, count, content.getBytes(), startIndex, 8);
        }

        protected static final boolean isStdioFileDescriptor(final long fileDescriptor) {
            return fileDescriptor == STDIO_HANDLES.IN || fileDescriptor == STDIO_HANDLES.OUT || fileDescriptor == STDIO_HANDLES.ERROR;
        }

        private static long fileWriteFromAt(final long fileDescriptor, final long count, final byte[] bytes, final long startIndex, final int elementSize) {
            final int byteStart = (int) (startIndex - 1) * elementSize;
            final int byteEnd = Math.min(byteStart + (int) count, bytes.length) * elementSize;
            final ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.position(byteStart);
            buffer.limit(byteEnd);
            final int written;
            try {
                written = getFileOrPrimFail(fileDescriptor).write(buffer);
            } catch (IOException e) {
                throw new PrimitiveFailed();
            }
            return written / elementSize;
        }

        private static long fileWriteToPrintWriter(final PrintWriter printWriter, final NativeObject content, final long startIndex, final long count) {
            final byte[] bytes = content.getByteStorage();
            final int byteStart = (int) (startIndex - 1);
            final int byteEnd = Math.min(byteStart + (int) count, bytes.length);
            printWriter.append(asString(content), byteStart, byteEnd);
            printWriter.flush();
            return byteEnd - byteStart;
        }
    }
}