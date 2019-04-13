package de.hpi.swa.graal.squeak.model;

import java.math.BigInteger;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.util.ArrayUtils;

@ExportLibrary(InteropLibrary.class)
public final class LargeIntegerObject extends AbstractSqueakObject {
    public static final long SMALLINTEGER32_MIN = -0x40000000;
    public static final long SMALLINTEGER32_MAX = 0x3fffffff;
    public static final long SMALLINTEGER64_MIN = -0x1000000000000000L;
    public static final long SMALLINTEGER64_MAX = 0xfffffffffffffffL;
    public static final int MASK_32BIT = 0xffffffff;
    public static final long MASK_64BIT = 0xffffffffffffffffL;
    private static final BigInteger ONE_SHIFTED_BY_64 = BigInteger.ONE.shiftLeft(64);
    private static final BigInteger ONE_HUNDRED_TWENTY_EIGHT = BigInteger.valueOf(128);
    private static final BigInteger LONG_MIN_OVERFLOW_RESULT = BigInteger.valueOf(Long.MIN_VALUE).abs();

    private byte[] bytes;
    private BigInteger integer;
    private boolean integerDirty = false;

    public LargeIntegerObject(final SqueakImageContext image, final BigInteger integer) {
        super(image, integer.compareTo(BigInteger.ZERO) >= 0 ? image.largePositiveIntegerClass : image.largeNegativeIntegerClass);
        bytes = bigIntegerToBytes(integer);
        this.integer = integer;
    }

    public LargeIntegerObject(final SqueakImageContext image, final long hash, final ClassObject klass, final byte[] bytes) {
        super(image, hash, klass);
        this.bytes = bytes;
        integerDirty = true;
    }

    public LargeIntegerObject(final SqueakImageContext image, final ClassObject klass, final byte[] bytes) {
        super(image, klass);
        this.bytes = bytes;
        integerDirty = true;
    }

    public LargeIntegerObject(final SqueakImageContext image, final ClassObject klass, final int size) {
        super(image, klass);
        bytes = new byte[size];
        integer = BigInteger.ZERO;
    }

    public LargeIntegerObject(final LargeIntegerObject original) {
        super(original.image, original.getSqueakClass());
        bytes = original.bytes.clone();
        integer = original.integer;
    }

    public static LargeIntegerObject createLongMinOverflowResult(final SqueakImageContext image) {
        return new LargeIntegerObject(image, LONG_MIN_OVERFLOW_RESULT);
    }

    public static byte[] getLongMinOverflowResultBytes() {
        return bigIntegerToBytes(LONG_MIN_OVERFLOW_RESULT);
    }

    public long getNativeAt0(final long index) {
        return Byte.toUnsignedLong(bytes[(int) index]);
    }

    public void setNativeAt0(final long index, final long value) {
        if (value < 0 || value > NativeObject.BYTE_MAX) { // check for overflow
            throw new IllegalArgumentException("Illegal value for LargeIntegerObject: " + value);
        }
        bytes[(int) index] = (byte) value;
        integerDirty = true;
    }

    public void markDirty() {
        integerDirty = true;
    }

    public void setBytes(final byte[] bytes) {
        this.bytes = bytes;
        integerDirty = true;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public BigInteger getBigInteger() {
        if (integerDirty) {
            integer = derivedBigIntegerFromBytes(bytes, isNegative());
            integerDirty = false;
        }
        return integer;
    }

    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public int instsize() {
        return 0;
    }

    @Override
    public int size() {
        return bytes.length;
    }

    private static BigInteger derivedBigIntegerFromBytes(final byte[] bytes, final boolean isNegative) {
        final byte[] bigEndianBytes = ArrayUtils.swapOrderCopy(bytes);
        if (bigEndianBytes.length == 0) {
            return BigInteger.ZERO;
        } else {
            if (isNegative) {
                return bigIntegerFromBigEndianBytes(bigEndianBytes).negate();
            } else {
                return bigIntegerFromBigEndianBytes(bigEndianBytes);
            }
        }
    }

    private static BigInteger bigIntegerFromBigEndianBytes(final byte[] bigEndianBytes) {
        return new BigInteger(bigEndianBytes).and(BigInteger.valueOf(1).shiftLeft(bigEndianBytes.length * 8).subtract(BigInteger.valueOf(1)));
    }

    public static byte[] bigIntegerToBytes(final BigInteger bigInteger) {
        final byte[] bytes = bigInteger.abs().toByteArray();
        if (bytes[0] == 0) {
            return ArrayUtils.swapOrderInPlace(Arrays.copyOfRange(bytes, 1, bytes.length));
        } else {
            return ArrayUtils.swapOrderInPlace(bytes);
        }
    }

    public boolean isNegative() {
        return getSqueakClass() == image.largeNegativeIntegerClass;
    }

    @Override
    @TruffleBoundary(transferToInterpreterOnException = false)
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        return getBigInteger().toString();
    }

    public boolean hasSameValueAs(final LargeIntegerObject other) {
        return Arrays.equals(bytes, other.bytes);
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (other instanceof LargeIntegerObject) {
            return hasSameValueAs((LargeIntegerObject) other);
        } else {
            return super.equals(other);
        }
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    public AbstractSqueakObject shallowCopy() {
        return new LargeIntegerObject(this);
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    private Object reduceIfPossible(final BigInteger value) {
        if (value.bitLength() > Long.SIZE - 1) {
            return newFromBigInteger(value);
        } else {
            return value.longValue() & MASK_64BIT;
        }
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object reduceIfPossible() {
        return reduceIfPossible(getBigInteger());
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public long longValue() {
        return getBigInteger().longValue();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public long longValueExact() throws ArithmeticException {
        return getBigInteger().longValueExact();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public byte byteValueExact() throws ArithmeticException {
        return getBigInteger().byteValueExact();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public short shortValueExact() throws ArithmeticException {
        return getBigInteger().shortValueExact();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public int intValueExact() throws ArithmeticException {
        return getBigInteger().intValueExact();
    }

    public boolean fitsIntoLong() {
        return bitLength() <= 63;
    }

    public boolean fitsIntoInt() {
        return bitLength() <= 31;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public int bitLength() {
        return getBigInteger().bitLength();
    }

    private LargeIntegerObject newFromBigInteger(final BigInteger value) {
        return newFromBigInteger(image, value);
    }

    private static LargeIntegerObject newFromBigInteger(final SqueakImageContext image, final BigInteger value) {
        return new LargeIntegerObject(image, value);
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public static LargeIntegerObject valueOf(final SqueakImageContext image, final long a) {
        return newFromBigInteger(image, BigInteger.valueOf(a));
    }

    public boolean isPositive() {
        return getSqueakClass() == image.largePositiveIntegerClass;
    }

    public boolean sizeLessThanWordSize() {
        /**
         * See `InterpreterPrimitives>>#positiveMachineIntegerValueOf:`.
         */
        return size() <= image.flags.wordSize();
    }

    /*
     * Arithmetic Operations
     */

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object add(final LargeIntegerObject b) {
        return reduceIfPossible(getBigInteger().add(b.getBigInteger()));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object subtract(final LargeIntegerObject b) {
        return reduceIfPossible(getBigInteger().subtract(b.getBigInteger()));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object multiply(final LargeIntegerObject b) {
        return reduceIfPossible(getBigInteger().multiply(b.getBigInteger()));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object divide(final LargeIntegerObject b) {
        return reduceIfPossible(getBigInteger().divide(b.getBigInteger()));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object floorDivide(final LargeIntegerObject b) {
        return reduceIfPossible(floorDivide(getBigInteger(), b.getBigInteger()));
    }

    private static BigInteger floorDivide(final BigInteger x, final BigInteger y) {
        BigInteger r = x.divide(y);
        // if the signs are different and modulo not zero, round down
        if (x.signum() != y.signum() && !r.multiply(y).equals(x)) {
            r = r.subtract(BigInteger.ONE);
        }
        return r;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object floorMod(final LargeIntegerObject b) {
        return reduceIfPossible(getBigInteger().subtract(floorDivide(getBigInteger(), b.getBigInteger()).multiply(b.getBigInteger())));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public LargeIntegerObject divideNoReduce(final LargeIntegerObject b) {
        return newFromBigInteger(getBigInteger().divide(b.getBigInteger()));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object remainder(final LargeIntegerObject b) {
        return reduceIfPossible(getBigInteger().remainder(b.getBigInteger()));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public LargeIntegerObject negateNoReduce() {
        return newFromBigInteger(getBigInteger().negate());
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public int compareTo(final LargeIntegerObject b) {
        return getBigInteger().compareTo(b.getBigInteger());
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public double doubleValue() {
        return getBigInteger().doubleValue();
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public boolean isZero() {
        return getBigInteger().compareTo(BigInteger.ZERO) == 0;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public boolean isZeroOrPositive() {
        return getBigInteger().compareTo(BigInteger.ZERO) >= 0;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public boolean lessThanOrEqualTo(final long value) {
        return fitsIntoLong() && getBigInteger().longValueExact() <= value;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public boolean lessThanOneShiftedBy64() {
        return getBigInteger().compareTo(ONE_SHIFTED_BY_64) < 0;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public boolean inRange(final long minValue, final long maxValue) {
        final long longValueExact = getBigInteger().longValueExact();
        return minValue <= longValueExact && longValueExact <= maxValue;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public boolean isIntegralWhenDividedBy(final LargeIntegerObject other) {
        return getBigInteger().mod(other.getBigInteger().abs()).compareTo(BigInteger.ZERO) == 0;
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public LargeIntegerObject toSigned() {
        if (getBigInteger().shiftRight(56).compareTo(ONE_HUNDRED_TWENTY_EIGHT) >= 0) {
            return newFromBigInteger(getBigInteger().subtract(ONE_SHIFTED_BY_64));
        } else {
            return this;
        }
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public LargeIntegerObject toUnsigned() {
        if (getBigInteger().compareTo(BigInteger.ZERO) < 0) {
            return newFromBigInteger(getBigInteger().add(ONE_SHIFTED_BY_64));
        } else {
            return this;
        }
    }

    /*
     * Bit Operations
     */

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object and(final LargeIntegerObject b) {
        return reduceIfPossible(getBigInteger().and(b.getBigInteger()));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object or(final LargeIntegerObject b) {
        return reduceIfPossible(getBigInteger().or(b.getBigInteger()));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object xor(final LargeIntegerObject b) {
        return reduceIfPossible(getBigInteger().xor(b.getBigInteger()));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object shiftLeft(final int b) {
        return reduceIfPossible(getBigInteger().shiftLeft(b));
    }

    @TruffleBoundary(transferToInterpreterOnException = false)
    public Object shiftRight(final int b) {
        return reduceIfPossible(getBigInteger().shiftRight(b));
    }

    /*
     * INTEROPERABILITY
     */

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean isNumber() {
        return true;
    }

    @ExportMessage
    public boolean fitsInByte() {
        return bitLength() < Byte.SIZE;
    }

    @ExportMessage
    public boolean fitsInShort() {
        return bitLength() < Short.SIZE;
    }

    @ExportMessage
    public boolean fitsInInt() {
        return bitLength() < Integer.SIZE;
    }

    @ExportMessage
    public boolean fitsInLong() {
        return bitLength() < Long.SIZE;
    }

    @ExportMessage
    public boolean fitsInFloat() {
        return bitLength() < Float.SIZE;
    }

    @ExportMessage
    public boolean fitsInDouble() {
        return bitLength() < Double.SIZE;
    }

    @ExportMessage
    public byte asByte() throws UnsupportedMessageException {
        try {
            return byteValueExact();
        } catch (final ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public short asShort() throws UnsupportedMessageException {
        try {
            return shortValueExact();
        } catch (final ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public int asInt() throws UnsupportedMessageException {
        try {
            return intValueExact();
        } catch (final ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public long asLong() throws UnsupportedMessageException {
        try {
            return longValueExact();
        } catch (final ArithmeticException e) {
            throw UnsupportedMessageException.create();
        }
    }

    @ExportMessage
    public float asFloat() throws UnsupportedMessageException {
        return asInt();
    }

    @ExportMessage
    public double asDouble() throws UnsupportedMessageException {
        return asLong();
    }
}
