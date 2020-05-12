/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.model;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.trufflesqueak.image.SqueakImageChunk;
import de.hpi.swa.trufflesqueak.image.SqueakImageConstants;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.image.SqueakImageWriter;
import de.hpi.swa.trufflesqueak.interop.WrapToSqueakNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.ArrayObjectNodes.ArrayObjectReadNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.ArrayObjectNodes.ArrayObjectSizeNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.ArrayObjectNodes.ArrayObjectWriteNode;
import de.hpi.swa.trufflesqueak.shared.SqueakLanguageConfig;
import de.hpi.swa.trufflesqueak.util.ArrayUtils;
import de.hpi.swa.trufflesqueak.util.ObjectGraphUtils.ObjectTracer;
import de.hpi.swa.trufflesqueak.util.UnsafeUtils;

@ExportLibrary(InteropLibrary.class)
public final class ArrayObject extends AbstractSqueakObjectWithClassAndHash {
    public static final byte BOOLEAN_NIL_TAG = 0;
    public static final byte BOOLEAN_TRUE_TAG = 1;
    public static final byte BOOLEAN_FALSE_TAG = -1;
    public static final char CHAR_NIL_TAG = Character.MAX_VALUE - 1; // Rather unlikely char.
    public static final long LONG_NIL_TAG = Long.MIN_VALUE + 42; // Rather unlikely long.
    public static final double DOUBLE_NIL_TAG = Double.longBitsToDouble(0x7ff8000000000001L); // NaN+1.
    public static final long DOUBLE_NIL_TAG_LONG = Double.doubleToRawLongBits(DOUBLE_NIL_TAG);
    private static final TruffleLogger LOG = TruffleLogger.getLogger(SqueakLanguageConfig.ID, ArrayObject.class);

    private Object storage;

    public ArrayObject(final SqueakImageContext image) {
        super(image); // for special ArrayObjects only
    }

    private ArrayObject(final SqueakImageContext image, final ClassObject classObject, final Object storage) {
        super(image, classObject);
        this.storage = storage;
    }

    public ArrayObject(final SqueakImageContext image, final long hash, final ClassObject squeakClass) {
        super(image, hash, squeakClass);
    }

    private ArrayObject(final ArrayObject original, final Object storageCopy) {
        super(original);
        storage = storageCopy;
    }

    public static ArrayObject createEmptyStrategy(final SqueakImageContext image, final ClassObject classObject, final int size) {
        return new ArrayObject(image, classObject, size);
    }

    public static ArrayObject createObjectStrategy(final SqueakImageContext image, final ClassObject classObject, final int size) {
        final Object[] objects = new Object[size];
        Arrays.fill(objects, NilObject.SINGLETON);
        return new ArrayObject(image, classObject, objects);
    }

    public static ArrayObject createWithStorage(final SqueakImageContext image, final ClassObject classObject, final Object storage) {
        return new ArrayObject(image, classObject, storage);
    }

    public static boolean isCharNilTag(final char value) {
        return value == CHAR_NIL_TAG;
    }

    public static boolean isDoubleNilTag(final double value) {
        return Double.doubleToRawLongBits(value) == DOUBLE_NIL_TAG_LONG;
    }

    public static boolean isLongNilTag(final long value) {
        return value == LONG_NIL_TAG;
    }

    @Override
    public void fillin(final SqueakImageChunk chunk) {
        final Object[] pointers = chunk.getPointers();
        if (!chunk.getImage().options.enableStorageStrategies) {
            storage = pointers;
            return;
        }
        final int valuesLength = pointers.length;
        storage = valuesLength;
        if (valuesLength > 0) {
            final ArrayObjectWriteNode writeNode = ArrayObjectWriteNode.getUncached();
            for (int i = 0; i < pointers.length; i++) {
                writeNode.execute(this, i, pointers[i]);
            }
        }
    }

    public void become(final ArrayObject other) {
        becomeOtherClass(other);
        final Object otherStorage = other.storage;
        other.setStorage(storage);
        setStorage(otherStorage);
    }

    public byte getByte(final long index) {
        assert isBooleanType();
        return UnsafeUtils.getByte((byte[]) storage, index);
    }

    public void setByte(final long index, final byte value) {
        assert isBooleanType();
        UnsafeUtils.putByte((byte[]) storage, index, value);
    }

    public int getBooleanLength() {
        return getBooleanStorage().length;
    }

    public byte[] getBooleanStorage() {
        assert isBooleanType();
        return (byte[]) storage;
    }

    public char getChar(final long index) {
        assert isCharType();
        return UnsafeUtils.getChar((char[]) storage, index);
    }

    public void setChar(final long index, final char value) {
        assert isCharType();
        UnsafeUtils.putChar((char[]) storage, index, value);
    }

    public int getCharLength() {
        return getCharStorage().length;
    }

    public char[] getCharStorage() {
        assert isCharType();
        return (char[]) storage;
    }

    public double getDouble(final long index) {
        assert isDoubleType();
        return UnsafeUtils.getDouble((double[]) storage, index);
    }

    public void setDouble(final long index, final double value) {
        assert isDoubleType();
        UnsafeUtils.putDouble((double[]) storage, index, value);
    }

    public int getDoubleLength() {
        return getDoubleStorage().length;
    }

    public double[] getDoubleStorage() {
        assert isDoubleType();
        return (double[]) storage;
    }

    public int getEmptyLength() {
        return getEmptyStorage();
    }

    public int getEmptyStorage() {
        assert isEmptyType();
        return (int) storage;
    }

    public long getLong(final long index) {
        assert isLongType();
        return UnsafeUtils.getLong((long[]) storage, index);
    }

    public void setLong(final long index, final long value) {
        assert isLongType();
        UnsafeUtils.putLong((long[]) storage, index, value);
    }

    public int getLongLength() {
        return getLongStorage().length;
    }

    public long[] getLongStorage() {
        assert isLongType();
        return (long[]) storage;
    }

    public Object getObject(final long index) {
        assert isObjectType();
        return UnsafeUtils.getObject((Object[]) storage, index);
    }

    public void setObject(final long index, final Object value) {
        assert isObjectType();
        UnsafeUtils.putObject((Object[]) storage, index, value);
    }

    public int getObjectLength() {
        return getObjectStorage().length;
    }

    public Object[] getObjectStorage() {
        assert isObjectType();
        return (Object[]) storage;
    }

    public Class<? extends Object> getStorageType() {
        return storage.getClass();
    }

    @Override
    public int instsize() {
        return 0;
    }

    @Override
    public int size() {
        CompilerAsserts.neverPartOfCompilation();
        return ArrayObjectSizeNode.getUncached().execute(this);
    }

    public ArrayObject shallowCopy(final Object storageCopy) {
        return new ArrayObject(this, storageCopy);
    }

    public boolean isBooleanType() {
        return storage instanceof byte[];
    }

    public boolean isCharType() {
        return storage instanceof char[];
    }

    public boolean isDoubleType() {
        return storage instanceof double[];
    }

    public boolean isEmptyType() {
        return storage instanceof Integer;
    }

    public boolean isLongType() {
        return storage instanceof long[];
    }

    public boolean isObjectType() {
        return storage instanceof Object[];
    }

    public boolean isTraceable() {
        return isObjectType();
    }

    public boolean hasSameStorageType(final ArrayObject other) {
        return storage.getClass() == other.storage.getClass();
    }

    public void setStorage(final Object newStorage) {
        storage = newStorage;
    }

    public static Object toObjectFromBoolean(final byte value, final BranchProfile isNilTagProfile) {
        if (value == BOOLEAN_FALSE_TAG) {
            return BooleanObject.FALSE;
        } else if (value == BOOLEAN_TRUE_TAG) {
            return BooleanObject.TRUE;
        } else {
            isNilTagProfile.enter();
            assert value == BOOLEAN_NIL_TAG;
            return NilObject.SINGLETON;
        }
    }

    public static Object toObjectFromChar(final char value, final ConditionProfile isNilTagProfile) {
        return isNilTagProfile.profile(isCharNilTag(value)) ? NilObject.SINGLETON : value;
    }

    public static Object toObjectFromLong(final long value, final ConditionProfile isNilTagProfile) {
        return isNilTagProfile.profile(isLongNilTag(value)) ? NilObject.SINGLETON : value;
    }

    public static Object toObjectFromDouble(final double value, final ConditionProfile isNilTagProfile) {
        return isNilTagProfile.profile(isDoubleNilTag(value)) ? NilObject.SINGLETON : value;
    }

    public void transitionFromBooleansToObjects(final BranchProfile isNilTagProfile) {
        LOG.finer("transition from Booleans to Objects");
        final byte[] booleans = getBooleanStorage();
        final Object[] objects = new Object[booleans.length];
        for (int i = 0; i < booleans.length; i++) {
            objects[i] = toObjectFromBoolean(booleans[i], isNilTagProfile);
        }
        storage = objects;
    }

    public void transitionFromCharsToObjects(final ConditionProfile isNilTagProfile) {
        LOG.finer("transition from Chars to Objects");
        final char[] chars = getCharStorage();
        final Object[] objects = new Object[chars.length];
        for (int i = 0; i < chars.length; i++) {
            objects[i] = toObjectFromChar(chars[i], isNilTagProfile);
        }
        storage = objects;
    }

    public void transitionFromDoublesToObjects(final ConditionProfile isNilTagProfile) {
        LOG.finer("transition from Doubles to Objects");
        final double[] doubles = getDoubleStorage();
        final Object[] objects = new Object[doubles.length];
        for (int i = 0; i < doubles.length; i++) {
            objects[i] = toObjectFromDouble(doubles[i], isNilTagProfile);
        }
        storage = objects;
    }

    public void transitionFromEmptyToBooleans() {
        // Zero-initialized, no need to fill with BOOLEAN_NIL_TAG.
        storage = new byte[getEmptyStorage()];
    }

    public void transitionFromEmptyToChars() {
        final char[] chars = new char[getEmptyStorage()];
        Arrays.fill(chars, CHAR_NIL_TAG);
        storage = chars;
    }

    public void transitionFromEmptyToDoubles() {
        final double[] doubles = new double[getEmptyStorage()];
        Arrays.fill(doubles, DOUBLE_NIL_TAG);
        storage = doubles;
    }

    public void transitionFromEmptyToLongs() {
        final long[] longs = new long[getEmptyStorage()];
        Arrays.fill(longs, LONG_NIL_TAG);
        storage = longs;
    }

    public void transitionFromEmptyToObjects() {
        storage = ArrayUtils.withAll(getEmptyLength(), NilObject.SINGLETON);
    }

    public void transitionFromLongsToObjects(final ConditionProfile isNilTagProfile) {
        LOG.finer("transition from Longs to Objects");
        final long[] longs = getLongStorage();
        final Object[] objects = new Object[longs.length];
        for (int i = 0; i < longs.length; i++) {
            objects[i] = toObjectFromLong(longs[i], isNilTagProfile);
        }
        storage = objects;
    }

    @Override
    public void pointersBecomeOneWay(final Object[] from, final Object[] to, final boolean copyHash) {
        if (isObjectType()) {
            pointersBecomeOneWay(getObjectStorage(), from, to, copyHash);
        }
    }

    @Override
    public void tracePointers(final ObjectTracer tracer) {
        if (isObjectType()) {
            for (final Object value : getObjectStorage()) {
                tracer.addIfUnmarked(value);
            }
        }
    }

    @Override
    public void trace(final SqueakImageWriter writer) {
        super.trace(writer);
        if (isObjectType()) {
            writer.traceAllIfNecessary(getObjectStorage());
        }
    }

    @Override
    public void write(final SqueakImageWriter writer) {
        if (!writeHeader(writer)) {
            return;
        }
        if (isEmptyType()) {
            for (int i = 0; i < getEmptyLength(); i++) {
                writer.writeNil();
            }
        } else if (isBooleanType()) {
            for (final byte item : getBooleanStorage()) {
                if (item == BOOLEAN_FALSE_TAG) {
                    writer.writeFalse();
                } else if (item == BOOLEAN_TRUE_TAG) {
                    writer.writeTrue();
                } else {
                    assert item == BOOLEAN_NIL_TAG;
                    writer.writeNil();
                }
            }
        } else if (isCharType()) {
            for (final char item : getCharStorage()) {
                if (isCharNilTag(item)) {
                    writer.writeNil();
                } else {
                    writer.writeChar(item);
                }
            }
        } else if (isDoubleType()) {
            for (final double item : getDoubleStorage()) {
                if (isDoubleNilTag(item)) {
                    writer.writeNil();
                } else {
                    writer.writeSmallFloat(item);
                }
            }
        } else if (isLongType()) {
            for (final long item : getLongStorage()) {
                if (isLongNilTag(item)) {
                    writer.writeNil();
                } else {
                    writer.writeSmallInteger(item);
                }
            }
        } else if (isObjectType()) {
            writer.writeObjects(getObjectStorage());
        }
    }

    public void writeAsHiddenRoots(final SqueakImageWriter writer) {
        assert isObjectType();
        /* Write header. */
        final long numSlots = getObjectLength();
        assert numSlots >= SqueakImageConstants.OVERFLOW_SLOTS;
        writer.writeLong(numSlots | SqueakImageConstants.SLOTS_MASK);
        writer.writeObjectHeader(SqueakImageConstants.OVERFLOW_SLOTS, 0, getSqueakClass().getInstanceSpecification(), 0, SqueakImageConstants.ARRAY_CLASS_INDEX_PUN);
        /* Write content. */
        for (final Object item : getObjectStorage()) {
            writer.writeObject(item);
        }
    }

    /*
     * INTEROPERABILITY
     */

    @SuppressWarnings("static-method")
    @ExportMessage
    protected boolean hasArrayElements() {
        return true;
    }

    @ExportMessage
    protected long getArraySize(@Shared("sizeNode") @Cached final ArrayObjectSizeNode sizeNode) {
        return sizeNode.execute(this);
    }

    @SuppressWarnings("static-method")
    @ExportMessage(name = "isArrayElementReadable")
    @ExportMessage(name = "isArrayElementModifiable")
    @ExportMessage(name = "isArrayElementInsertable")
    protected boolean isArrayElementReadable(final long index, @Shared("sizeNode") @Cached final ArrayObjectSizeNode sizeNode) {
        return 0 <= index && index < sizeNode.execute(this);
    }

    @ExportMessage
    protected Object readArrayElement(final long index, @Cached final ArrayObjectReadNode readNode) {
        return readNode.execute(this, index);
    }

    @ExportMessage
    protected void writeArrayElement(final long index, final Object value,
                    @Exclusive @Cached final WrapToSqueakNode wrapNode,
                    @Cached final ArrayObjectWriteNode writeNode) throws InvalidArrayIndexException {
        try {
            writeNode.execute(this, index, wrapNode.executeWrap(value));
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw InvalidArrayIndexException.create(index);
        }
    }
}
