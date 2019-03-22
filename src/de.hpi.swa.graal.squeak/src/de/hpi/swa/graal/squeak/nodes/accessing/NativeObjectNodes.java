package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.CharacterObject;
import de.hpi.swa.graal.squeak.model.LargeIntegerObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.nodes.AbstractNode;
import de.hpi.swa.graal.squeak.nodes.accessing.NativeObjectNodesFactory.NativeAcceptsValueNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.NativeObjectNodesFactory.NativeGetBytesNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.NativeObjectNodesFactory.NativeObjectReadNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.NativeObjectNodesFactory.NativeObjectSizeNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.NativeObjectNodesFactory.NativeObjectWriteNodeGen;
import de.hpi.swa.graal.squeak.util.ArrayConversionUtils;

public final class NativeObjectNodes {

    @GenerateUncached
    public abstract static class NativeAcceptsValueNode extends AbstractNode {

        public static NativeAcceptsValueNode create() {
            return NativeAcceptsValueNodeGen.create();
        }

        public abstract boolean execute(NativeObject obj, Object value);

        @Specialization(guards = "obj.isByteType()")
        protected static final boolean doNativeBytes(@SuppressWarnings("unused") final NativeObject obj, final char value) {
            return value <= NativeObject.BYTE_MAX;
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final boolean doNativeShorts(@SuppressWarnings("unused") final NativeObject obj, final char value) {
            return value <= NativeObject.SHORT_MAX;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "obj.isIntType() || obj.isLongType()")
        protected static final boolean doNativeInts(final NativeObject obj, final char value) {
            return true;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "obj.isByteType()")
        protected static final boolean doNativeBytes(final NativeObject obj, final CharacterObject value) {
            return false; // Value of CharacterObjects is always larger than `Character.MAX_VALUE`.
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final boolean doNativeShorts(@SuppressWarnings("unused") final NativeObject obj, final CharacterObject value) {
            return value.getValue() <= NativeObject.SHORT_MAX;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "obj.isIntType() || obj.isLongType()")
        protected static final boolean doNativeInts(final NativeObject obj, final CharacterObject value) {
            return true;
        }

        @Specialization(guards = "obj.isByteType()")
        protected static final boolean doNativeBytes(@SuppressWarnings("unused") final NativeObject obj, final long value) {
            return 0 <= value && value <= NativeObject.BYTE_MAX;
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final boolean doNativeShorts(@SuppressWarnings("unused") final NativeObject obj, final long value) {
            return 0 <= value && value <= NativeObject.SHORT_MAX;
        }

        @Specialization(guards = "obj.isIntType()")
        protected static final boolean doNativeInts(@SuppressWarnings("unused") final NativeObject obj, final long value) {
            return 0 <= value && value <= NativeObject.INTEGER_MAX;
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final boolean doNativeLongs(@SuppressWarnings("unused") final NativeObject obj, final long value) {
            return 0 <= value;
        }

        @Specialization(guards = {"obj.isByteType()"})
        protected static final boolean doNativeBytesLargeInteger(@SuppressWarnings("unused") final NativeObject obj, final LargeIntegerObject value) {
            return value.inRange(0, NativeObject.BYTE_MAX);
        }

        @Specialization(guards = {"obj.isShortType()"})
        protected static final boolean doNativeShortsLargeInteger(@SuppressWarnings("unused") final NativeObject obj, final LargeIntegerObject value) {
            return value.inRange(0, NativeObject.SHORT_MAX);
        }

        @Specialization(guards = {"obj.isIntType()"})
        protected static final boolean doNativeIntsLargeInteger(@SuppressWarnings("unused") final NativeObject obj, final LargeIntegerObject value) {
            return value.inRange(0, NativeObject.INTEGER_MAX);
        }

        @Specialization(guards = {"obj.isLongType()"})
        protected static final boolean doNativeLongsLargeInteger(@SuppressWarnings("unused") final NativeObject obj, final LargeIntegerObject value) {
            return value.isZeroOrPositive() && value.lessThanOneShiftedBy64();
        }

        @Fallback
        protected static final boolean doFail(final NativeObject object, final Object value) {
            throw SqueakException.create("Unexpected values:", object, value);
        }
    }

    @GenerateUncached
    public abstract static class NativeObjectReadNode extends AbstractNode {

        public static NativeObjectReadNode create() {
            return NativeObjectReadNodeGen.create();
        }

        public abstract Object execute(NativeObject obj, long index);

        @Specialization(guards = "obj.isByteType()")
        protected static final long doNativeBytes(final NativeObject obj, final long index) {
            return Byte.toUnsignedLong(obj.getByteStorage()[(int) index]);
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final long doNativeShorts(final NativeObject obj, final long index) {
            return Short.toUnsignedLong(obj.getShortStorage()[(int) index]);
        }

        @Specialization(guards = "obj.isIntType()")
        protected static final long doNativeInts(final NativeObject obj, final long index) {
            return Integer.toUnsignedLong(obj.getIntStorage()[(int) index]);
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final Object doNativeLongs(final NativeObject obj, final long index) {
            final long value = obj.getLongStorage()[(int) index];
            if (value >= 0) {
                return value;
            } else {
                return LargeIntegerObject.valueOf(obj.image, value).toUnsigned();
            }
        }

        @Fallback
        protected static final long doFail(final NativeObject obj, final long index) {
            throw SqueakException.create("Unexpected values:", obj, index);
        }
    }

    @GenerateUncached
    @ImportStatic(NativeObject.class)
    public abstract static class NativeObjectWriteNode extends AbstractNode {

        public static NativeObjectWriteNode create() {
            return NativeObjectWriteNodeGen.create();
        }

        public abstract void execute(NativeObject obj, long index, Object value);

        @Specialization(guards = {"obj.isByteType()", "value >= 0", "value <= BYTE_MAX"})
        protected static final void doNativeBytes(final NativeObject obj, final long index, final long value) {
            obj.getByteStorage()[(int) index] = (byte) value;
        }

        @Specialization(guards = {"obj.isShortType()", "value >= 0", "value <= SHORT_MAX"})
        protected static final void doNativeShorts(final NativeObject obj, final long index, final long value) {
            obj.getShortStorage()[(int) index] = (short) value;
        }

        @Specialization(guards = {"obj.isIntType()", "value >= 0", "value <= INTEGER_MAX"})
        protected static final void doNativeInts(final NativeObject obj, final long index, final long value) {
            obj.getIntStorage()[(int) index] = (int) value;
        }

        @Specialization(guards = {"obj.isLongType()", "value >= 0"})
        protected static final void doNativeLongs(final NativeObject obj, final long index, final long value) {
            obj.getLongStorage()[(int) index] = value;
        }

        protected static final boolean inByteRange(final char value) {
            return value <= NativeObject.BYTE_MAX;
        }

        @Specialization(guards = {"obj.isByteType()", "inByteRange(value)"})
        protected static final void doNativeBytesChar(final NativeObject obj, final long index, final char value) {
            doNativeBytes(obj, index, value);
        }

        @Specialization(guards = "obj.isShortType()") // char values fit into short
        protected static final void doNativeShortsChar(final NativeObject obj, final long index, final char value) {
            doNativeShorts(obj, index, value);
        }

        @Specialization(guards = "obj.isIntType()")
        protected static final void doNativeIntsChar(final NativeObject obj, final long index, final char value) {
            doNativeInts(obj, index, value);
        }

        @Specialization(guards = "obj.isIntType()")
        protected static final void doNativeIntsChar(final NativeObject obj, final long index, final CharacterObject value) {
            doNativeInts(obj, index, value.getValue());
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final void doNativeLongsChar(final NativeObject obj, final long index, final char value) {
            doNativeLongs(obj, index, value);
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final void doNativeLongsChar(final NativeObject obj, final long index, final CharacterObject value) {
            doNativeLongs(obj, index, value.getValue());
        }

        @Specialization(guards = {"obj.isByteType()", "value.inRange(0, BYTE_MAX)"})
        protected static final void doNativeBytesLargeInteger(final NativeObject obj, final long index, final LargeIntegerObject value) {
            doNativeBytes(obj, index, value.longValueExact());
        }

        @Specialization(guards = {"obj.isShortType()", "value.inRange(0, SHORT_MAX)"})
        protected static final void doNativeShortsLargeInteger(final NativeObject obj, final long index, final LargeIntegerObject value) {
            doNativeShorts(obj, index, value.longValueExact());
        }

        @Specialization(guards = {"obj.isIntType()", "value.inRange(0, INTEGER_MAX)"})
        protected static final void doNativeIntsLargeInteger(final NativeObject obj, final long index, final LargeIntegerObject value) {
            doNativeInts(obj, index, value.longValueExact());
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"obj.isIntType()", "!value.inRange(0, INTEGER_MAX)"})
        protected static final void doNativeIntsLargeIntegerIllegal(final NativeObject obj, final long index, final LargeIntegerObject value) {
            throw SqueakException.create("Illegal value for int array: " + value);
        }

        @Specialization(guards = {"obj.isLongType()", "value.isZeroOrPositive()", "value.fitsIntoLong()"})
        protected static final void doNativeLongsLargeInteger(final NativeObject obj, final long index, final LargeIntegerObject value) {
            doNativeLongs(obj, index, value.longValueExact());
        }

        @Specialization(guards = {"obj.isLongType()", "value.isZeroOrPositive()", "!value.fitsIntoLong()", "value.lessThanOneShiftedBy64()"})
        protected static final void doNativeLongsLargeIntegerSigned(final NativeObject obj, final long index, final LargeIntegerObject value) {
            doNativeLongs(obj, index, value.toSigned().longValueExact());
        }

        @Fallback
        protected static final void doFail(final NativeObject obj, final long index, final Object value) {
            throw SqueakException.create("Unexpected values:", obj, index, value);
        }
    }

    @GenerateUncached
    public abstract static class NativeObjectSizeNode extends AbstractNode {

        public static NativeObjectSizeNode create() {
            return NativeObjectSizeNodeGen.create();
        }

        public abstract int execute(NativeObject obj);

        @Specialization(guards = "obj.isByteType()")
        protected static final int doNativeBytes(final NativeObject obj) {
            return obj.getByteLength();
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final int doNativeShorts(final NativeObject obj) {
            return obj.getShortLength();
        }

        @Specialization(guards = "obj.isIntType()")
        protected static final int doNativeInts(final NativeObject obj) {
            return obj.getIntLength();
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final int doNativeLongs(final NativeObject obj) {
            return obj.getLongLength();
        }

        @Fallback
        protected static final int doFail(final NativeObject object) {
            throw SqueakException.create("Unexpected value:", object);
        }
    }

    @GenerateUncached
    public abstract static class NativeGetBytesNode extends AbstractNode {

        public static NativeGetBytesNode create() {
            return NativeGetBytesNodeGen.create();
        }

        @TruffleBoundary
        public final String executeAsString(final NativeObject obj) {
            return new String(execute(obj));
        }

        public abstract byte[] execute(NativeObject obj);

        @Specialization(guards = "obj.isByteType()")
        protected static final byte[] doNativeBytes(final NativeObject obj) {
            return obj.getByteStorage();
        }

        @Specialization(guards = "obj.isShortType()")
        protected static final byte[] doNativeShorts(final NativeObject obj) {
            return ArrayConversionUtils.bytesFromShortsReversed(obj.getShortStorage());
        }

        @Specialization(guards = "obj.isIntType()")
        protected static final byte[] doNativeInts(final NativeObject obj) {
            return ArrayConversionUtils.bytesFromIntsReversed(obj.getIntStorage());
        }

        @Specialization(guards = "obj.isLongType()")
        protected static final byte[] doNativeLongs(final NativeObject obj) {
            return ArrayConversionUtils.bytesFromLongsReversed(obj.getLongStorage());
        }

        @Fallback
        protected static final byte[] doFail(final NativeObject object) {
            throw SqueakException.create("Unexpected value:", object);
        }
    }
}
