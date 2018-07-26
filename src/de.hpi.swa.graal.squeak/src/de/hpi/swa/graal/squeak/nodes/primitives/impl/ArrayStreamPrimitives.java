package de.hpi.swa.graal.squeak.nodes.primitives.impl;

import java.util.List;

import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.ContextObject;
import de.hpi.swa.graal.squeak.model.EmptyObject;
import de.hpi.swa.graal.squeak.model.FloatObject;
import de.hpi.swa.graal.squeak.model.LargeIntegerObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.model.NotProvided;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectAt0Node;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectAtPut0Node;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectInstSizeNode;
import de.hpi.swa.graal.squeak.nodes.accessing.SqueakObjectSizeNode;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.graal.squeak.nodes.primitives.impl.ArithmeticPrimitives.AbstractArithmeticPrimitiveNode;

public final class ArrayStreamPrimitives extends AbstractPrimitiveFactoryHolder {

    @Override
    public List<NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return ArrayStreamPrimitivesFactory.getFactories();
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 60)
    protected abstract static class PrimBasicAtNode extends AbstractPrimitiveNode {
        @Child private SqueakObjectInstSizeNode instSizeNode = SqueakObjectInstSizeNode.create();
        @Child private SqueakObjectAt0Node at0Node = SqueakObjectAt0Node.create();

        protected PrimBasicAtNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Override
        public final Object executeWithArguments(final VirtualFrame frame, final Object... arguments) {
            try {
                return executeWithArgumentsSpecialized(frame, arguments);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        @Override
        public final Object executePrimitive(final VirtualFrame frame) {
            try {
                return executeAt(frame);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        public abstract Object executeAt(VirtualFrame frame);

        @Specialization
        protected static final long doCharacter(final char receiver, final long index, @SuppressWarnings("unused") final NotProvided notProvided) {
            if (index == 1) {
                return receiver;
            } else {
                throw new PrimitiveFailed();
            }
        }

        @Specialization(guards = "!isSmallInteger(receiver)")
        protected final long doLong(final long receiver, final long index, @SuppressWarnings("unused") final NotProvided notProvided) {
            try {
                return asLargeInteger(receiver).getNativeAt0(index - 1);
            } catch (IndexOutOfBoundsException e) {
                return 0L; // inline fallback code
            }
        }

        @Specialization
        protected final Object doNative(final NativeObject receiver, final long index, @SuppressWarnings("unused") final NotProvided notProvided) {
            return at0Node.execute(receiver, index - 1);
        }

        @Specialization
        protected static final long doLargeInteger(final LargeIntegerObject receiver, final long index, @SuppressWarnings("unused") final NotProvided notProvided) {
            return receiver.getNativeAt0(index - 1);
        }

        @Specialization
        protected static final long doFloat(final FloatObject receiver, final long index, @SuppressWarnings("unused") final NotProvided notProvided) {
            return receiver.getNativeAt0(index - 1);
        }

        @Specialization(guards = {"!isNativeObject(receiver)", "!isLargeInteger(receiver)", "!isFloat(receiver)"})
        protected final Object doSqueakObject(final AbstractSqueakObject receiver, final long index, @SuppressWarnings("unused") final NotProvided notProvided) {
            return at0Node.execute(receiver, index - 1 + instSizeNode.execute(receiver));
        }

        /*
         * Context>>#object:basicAt:
         */

        @Specialization
        protected static final long doCharacter(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final char target, final long index) {
            if (index == 1) {
                return target;
            } else {
                throw new PrimitiveFailed();
            }
        }

        @Specialization(guards = "!isSmallInteger(target)")
        protected final Object doLong(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final long target, final long index) {
            return asLargeInteger(target).getNativeAt0(index - 1);
        }

        @Specialization
        protected final Object doNative(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final NativeObject target, final long index) {
            return at0Node.execute(target, index - 1);
        }

        @Specialization
        protected static final long doLargeInteger(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final LargeIntegerObject target, final long index) {
            return target.getNativeAt0(index - 1);
        }

        @Specialization
        protected static final long doFloat(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final FloatObject target, final long index) {
            return target.getNativeAt0(index - 1);
        }

        @Specialization(guards = {"!isNativeObject(receiver)", "!isLargeInteger(receiver)", "!isFloat(receiver)"})
        protected final Object doSqueakObject(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final AbstractSqueakObject target, final long index) {
            return at0Node.execute(target, index - 1 + instSizeNode.execute(target));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 61)
    protected abstract static class PrimBasicAtPutNode extends AbstractPrimitiveNode {
        @Child private SqueakObjectInstSizeNode instSizeNode = SqueakObjectInstSizeNode.create();
        @Child private SqueakObjectAtPut0Node atPut0Node = SqueakObjectAtPut0Node.create();

        protected PrimBasicAtPutNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Override
        public final Object executeWithArguments(final VirtualFrame frame, final Object... arguments) {
            try {
                return executeWithArgumentsSpecialized(frame, arguments);
            } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
        }

        @Override
        public final Object executePrimitive(final VirtualFrame frame) {
            try {
                return executeAtPut(frame);
            } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
        }

        public abstract Object executeAtPut(VirtualFrame frame);

        @Specialization
        protected char doNativeChar(final NativeObject receiver, final long index, final char value, @SuppressWarnings("unused") final NotProvided notProvided) {
            try {
                atPut0Node.execute(receiver, index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected long doNativeLong(final NativeObject receiver, final long index, final long value, @SuppressWarnings("unused") final NotProvided notProvided) {
            try {
                atPut0Node.execute(receiver, index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected Object doNativeLargeInteger(final NativeObject receiver, final long index, final LargeIntegerObject value, @SuppressWarnings("unused") final NotProvided notProvided) {
            try {
                atPut0Node.execute(receiver, index - 1, value.longValueExact());
            } catch (IllegalArgumentException | ArithmeticException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected char doLargeIntegerChar(final LargeIntegerObject receiver, final long index, final char value, @SuppressWarnings("unused") final NotProvided notProvided) {
            try {
                receiver.setNativeAt0(index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected long doLargeIntegerLong(final LargeIntegerObject receiver, final long index, final long value, @SuppressWarnings("unused") final NotProvided notProvided) {
            try {
                receiver.setNativeAt0(index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected Object doLargeInteger(final LargeIntegerObject receiver, final long index, final LargeIntegerObject value, @SuppressWarnings("unused") final NotProvided notProvided) {
            try {
                receiver.setNativeAt0(index - 1, value.longValueExact());
            } catch (IllegalArgumentException | ArithmeticException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected char doFloatChar(final FloatObject receiver, final long index, final char value, @SuppressWarnings("unused") final NotProvided notProvided) {
            try {
                receiver.setNativeAt0(index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected long doFloatLong(final FloatObject receiver, final long index, final long value, @SuppressWarnings("unused") final NotProvided notProvided) {
            try {
                receiver.setNativeAt0(index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected Object doFloatLargeInteger(final FloatObject receiver, final long index, final LargeIntegerObject value, @SuppressWarnings("unused") final NotProvided notProvided) {
            try {
                receiver.setNativeAt0(index - 1, value.longValueExact());
            } catch (IllegalArgumentException | ArithmeticException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected Object doEmptyObject(final EmptyObject receiver, final long idx, final Object value, final NotProvided notProvided) {
            throw new PrimitiveFailed();
        }

        @Specialization(guards = "!isSmallInteger(receiver)")
        protected Object doSqueakObject(final long receiver, final long index, final long value, @SuppressWarnings("unused") final NotProvided notProvided) {
            try {
                asLargeInteger(receiver).setNativeAt0(index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization(guards = {"!isNativeObject(receiver)", "!isEmptyObject(receiver)"})
        protected Object doSqueakObject(final AbstractSqueakObject receiver, final long index, final Object value, @SuppressWarnings("unused") final NotProvided notProvided) {
            atPut0Node.execute(receiver, index - 1 + instSizeNode.execute(receiver), value);
            return value;
        }

        /*
         * Context>>#object:basicAt:put:
         */

        @Specialization
        protected char doNativeChar(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final NativeObject target, final long index, final char value) {
            try {
                atPut0Node.execute(target, index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected long doNativeLong(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final NativeObject target, final long index, final long value) {
            try {
                atPut0Node.execute(target, index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected Object doNativeLargeInteger(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final NativeObject target, final long index, final LargeIntegerObject value) {
            try {
                atPut0Node.execute(target, index - 1, value.longValueExact());
            } catch (IllegalArgumentException | ArithmeticException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected char doLargeIntegerChar(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final LargeIntegerObject target, final long index, final char value) {
            try {
                target.setNativeAt0(index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected long doLargeIntegerLong(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final LargeIntegerObject target, final long index, final long value) {
            try {
                target.setNativeAt0(index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected Object doLargeInteger(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final LargeIntegerObject target, final long index, final LargeIntegerObject value) {
            try {
                target.setNativeAt0(index - 1, value.longValueExact());
            } catch (IllegalArgumentException | ArithmeticException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected char doFloatChar(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final FloatObject target, final long index, final char value) {
            try {
                target.setNativeAt0(index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected long doFloatLong(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final FloatObject target, final long index, final long value) {
            try {
                target.setNativeAt0(index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization
        protected Object doFloatLargeInteger(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final FloatObject target, final long index, final LargeIntegerObject value) {
            try {
                target.setNativeAt0(index - 1, value.longValueExact());
            } catch (IllegalArgumentException | ArithmeticException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected Object doEmptyObject(final AbstractSqueakObject receiver, final EmptyObject target, final long idx, final Object value) {
            throw new PrimitiveFailed();
        }

        @Specialization(guards = "!isSmallInteger(target)")
        protected Object doSqueakObject(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final long target, final long index, final long value) {
            try {
                asLargeInteger(target).setNativeAt0(index - 1, value);
            } catch (IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }

        @Specialization(guards = {"!isNativeObject(receiver)", "!isEmptyObject(receiver)"})
        protected Object doSqueakObject(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final AbstractSqueakObject target, final long index, final Object value) {
            atPut0Node.execute(target, index - 1 + instSizeNode.execute(target), value);
            return value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 62)
    protected abstract static class PrimSizeNode extends AbstractArithmeticPrimitiveNode {
        @Child private SqueakObjectSizeNode sizeNode = SqueakObjectSizeNode.create();
        @Child private SqueakObjectInstSizeNode instSizeNode = SqueakObjectInstSizeNode.create();

        protected PrimSizeNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        protected static final long size(@SuppressWarnings("unused") final char obj, @SuppressWarnings("unused") final NotProvided notProvided) {
            return 0;
        }

        @Specialization
        protected static final long size(@SuppressWarnings("unused") final boolean o, @SuppressWarnings("unused") final NotProvided notProvided) {
            return 0;
        }

        @Specialization(guards = "!isSmallInteger(value)")
        protected final long doLong(final long value, @SuppressWarnings("unused") final NotProvided notProvided) {
            return asLargeInteger(value).size();
        }

        @Specialization
        protected static final long doString(final String s, @SuppressWarnings("unused") final NotProvided notProvided) {
            return s.getBytes().length;
        }

        @Specialization
        protected final long doNative(final NativeObject obj, @SuppressWarnings("unused") final NotProvided notProvided) {
            return sizeNode.execute(obj);
        }

        @Specialization
        protected static final long doLargeInteger(final LargeIntegerObject obj, @SuppressWarnings("unused") final NotProvided notProvided) {
            return obj.size();
        }

        @Specialization
        protected static final long doFloat(@SuppressWarnings("unused") final FloatObject obj, @SuppressWarnings("unused") final NotProvided notProvided) {
            return FloatObject.size();
        }

        @Specialization
        protected static final long size(@SuppressWarnings("unused") final double o, @SuppressWarnings("unused") final NotProvided notProvided) {
            return 2; // Float in words
        }

        @Specialization(guards = {"!isNil(obj)", "hasVariableClass(obj)"})
        protected final long size(final AbstractSqueakObject obj, @SuppressWarnings("unused") final NotProvided notProvided) {
            return sizeNode.execute(obj) - instSizeNode.execute(obj);
        }

        /*
         * Context>>#objectSize:
         */

        @SuppressWarnings("unused")
        @Specialization
        protected static final long doChar(final AbstractSqueakObject receiver, final char obj) {
            return 0;
        }

        @SuppressWarnings("unused")
        @Specialization
        protected static final long doBoolean(final AbstractSqueakObject receiver, final boolean o) {
            return 0;
        }

        @Specialization(guards = "!isSmallInteger(value)")
        protected final long doLong(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final long value) {
            return asLargeInteger(value).size();
        }

        @Specialization
        protected static final long doString(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final String s) {
            return s.getBytes().length;
        }

        @Specialization
        protected final long doNative(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final NativeObject obj) {
            return sizeNode.execute(obj);
        }

        @Specialization
        protected static final long doLargeInteger(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final LargeIntegerObject obj) {
            return obj.size();
        }

        @Specialization
        protected static final long doFloat(@SuppressWarnings("unused") final AbstractSqueakObject receiver, @SuppressWarnings("unused") final FloatObject obj) {
            return FloatObject.size();
        }

        @Specialization
        protected static final long doDouble(@SuppressWarnings("unused") final AbstractSqueakObject receiver, @SuppressWarnings("unused") final double o) {
            return 2; // Float in words
        }

        @Specialization(guards = {"!isNil(obj)", "hasVariableClass(obj)"})
        protected final long doSqueakObject(@SuppressWarnings("unused") final AbstractSqueakObject receiver, final AbstractSqueakObject obj) {
            return sizeNode.execute(obj) - instSizeNode.execute(obj);
        }

        /*
         * Quick return 0 to allow eager primitive calls.
         * "The number of indexable fields of fixed-length objects is 0" (see Object>>basicSize).
         */
        @SuppressWarnings("unused")
        @Fallback
        protected static final long doObject(final Object receiver, final Object anything) {
            return 0;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 63)
    protected abstract static class PrimStringAtNode extends AbstractPrimitiveNode {
        @Child private SqueakObjectAt0Node at0Node = SqueakObjectAt0Node.create();

        protected PrimStringAtNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Override
        public final Object executeWithArguments(final VirtualFrame frame, final Object... arguments) {
            try {
                return executeWithArgumentsSpecialized(frame, arguments);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        @Override
        public final Object executePrimitive(final VirtualFrame frame) {
            try {
                return executeStringAt(frame);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        public abstract Object executeStringAt(VirtualFrame frame);

        @Specialization
        protected final char doNativeObject(final NativeObject obj, final long index) {
            return (char) ((long) at0Node.execute(obj, index - 1));
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 64)
    protected abstract static class PrimStringAtPutNode extends AbstractPrimitiveNode {
        @Child private SqueakObjectAtPut0Node atPut0Node = SqueakObjectAtPut0Node.create();

        protected PrimStringAtPutNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Override
        public final Object executeWithArguments(final VirtualFrame frame, final Object... arguments) {
            try {
                return executeWithArgumentsSpecialized(frame, arguments);
            } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
        }

        @Override
        public final Object executePrimitive(final VirtualFrame frame) {
            try {
                return executeStringAtPut(frame);
            } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
                throw new PrimitiveFailed();
            }
        }

        public abstract Object executeStringAtPut(VirtualFrame frame);

        @Specialization
        protected final char doNativeObject(final NativeObject obj, final long index, final char value) {
            atPut0Node.execute(obj, index - 1, value);
            return value;
        }

        @Specialization
        protected final char doNativeObject(final NativeObject obj, final long index, final long value) {
            assert value >= 0;
            atPut0Node.execute(obj, index - 1, value);
            return (char) value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 143)
    protected abstract static class PrimShortAtNode extends AbstractPrimitiveNode {

        protected PrimShortAtNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = "receiver.isByteType()")
        protected static final long doNativeBytes(final NativeObject receiver, final long index) {
            final int offset = (int) ((index - 1) * 2);
            final byte[] bytes = receiver.getByteStorage();
            try {
                final int byte0 = (byte) Byte.toUnsignedLong(bytes[offset]);
                int byte1 = (int) Byte.toUnsignedLong(bytes[offset + 1]) << 8;
                if ((byte1 & 0x8000) != 0) {
                    byte1 = 0xffff0000 | byte1;
                }
                return byte1 | byte0;
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        @Specialization(guards = "receiver.isShortType()")
        protected static final long doNativeShorts(final NativeObject receiver, final long index) {
            try {
                return Short.toUnsignedLong(receiver.getShortStorage()[(int) index]);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        @Specialization(guards = "receiver.isIntType()")
        protected static final long doNativeInts(final NativeObject receiver, final long index) {
            try {
                final int word = receiver.getIntStorage()[((int) index - 1) / 2];
                int shortValue;
                if ((index - 1) % 2 == 0) {
                    shortValue = word & 0xffff;
                } else {
                    shortValue = (word >> 16) & 0xffff;
                }
                if ((shortValue & 0x8000) != 0) {
                    shortValue = 0xffff0000 | shortValue;
                }
                return shortValue;
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "receiver.isLongType()")
        protected static final long doNativeLongs(final NativeObject receiver, final long index) {
            throw new SqueakException("Not yet implemented: shortAtPut0"); // TODO: implement
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 144)
    protected abstract static class PrimShortAtPutNode extends AbstractPrimitiveNode {

        protected PrimShortAtPutNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = {"inShortRange(value)", "receiver.isByteType()"})
        protected static final long doNativeBytes(final NativeObject receiver, final long index, final long value) {
            final int offset = (int) ((index - 1) * 2);
            final byte[] bytes = receiver.getByteStorage();
            bytes[offset] = (byte) value;
            bytes[offset + 1] = (byte) (value >> 8);
            return value;
        }

        @Specialization(guards = {"inShortRange(value)", "receiver.isShortType()"})
        protected static final long doNativeShorts(final NativeObject receiver, final long index, final long value) {
            receiver.getShortStorage()[(int) index] = (short) value;
            return value;
        }

        @Specialization(guards = {"inShortRange(value)", "receiver.isIntType()", "isEven(index)"})
        protected static final long doNativeIntsEven(final NativeObject receiver, final long index, final long value) {
            final int wordIndex = (int) ((index - 1) / 2);
            final int[] ints = receiver.getIntStorage();
            final int word = (int) Integer.toUnsignedLong(ints[wordIndex]);
            ints[wordIndex] = (word & 0xffff0000) | ((int) value & 0xffff);
            return value;
        }

        @Specialization(guards = {"inShortRange(value)", "receiver.isIntType()", "!isEven(index)"})
        protected static final long doNativeIntsOdd(final NativeObject receiver, final long index, final long value) {
            final int wordIndex = (int) ((index - 1) / 2);
            final int[] ints = receiver.getIntStorage();
            final int word = (int) Integer.toUnsignedLong(ints[wordIndex]);
            ints[wordIndex] = ((int) value << 16) | (word & 0xffff);
            return value;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"inShortRange(value)", "receiver.isLongType()"})
        protected static final long doNativeLongs(final NativeObject receiver, final long index, final long value) {
            throw new SqueakException("Not yet implemented: shortAtPut0"); // TODO: implement
        }

        protected static final boolean inShortRange(final long value) {
            return -0x8000 <= value && value <= 0x8000;
        }

        protected static final boolean isEven(final long index) {
            return (index - 1) % 2 == 0;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 165)
    protected abstract static class PrimIntegerAtNode extends AbstractPrimitiveNode {
        private final BranchProfile errorProfile = BranchProfile.create();

        protected PrimIntegerAtNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = {"receiver.isIntType()"})
        protected final long doNativeInt(final NativeObject receiver, final long index) {
            try {
                return receiver.getIntStorage()[(int) index - 1];
            } catch (ArrayIndexOutOfBoundsException e) {
                errorProfile.enter();
                throw new PrimitiveFailed();
            }
        }
    }

    @ImportStatic(Integer.class)
    @GenerateNodeFactory
    @SqueakPrimitive(index = 166)
    protected abstract static class PrimIntegerAtPutNode extends AbstractPrimitiveNode {

        protected PrimIntegerAtPutNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = {"receiver.isIntType()", "value >= MIN_VALUE", "value <= MAX_VALUE"})
        protected static final long doNativeInt(final NativeObject receiver, final long index, final long value) {
            try {
                receiver.getIntStorage()[(int) index - 1] = (int) value;
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 210)
    protected abstract static class PrimContextAtNode extends AbstractPrimitiveNode {
        protected PrimContextAtNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        protected static final Object doContextObject(final ContextObject receiver, final long index) {
            try {
                return receiver.atTemp(index - 1);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(indices = 211)
    protected abstract static class PrimContextAtPutNode extends AbstractPrimitiveNode {
        protected PrimContextAtPutNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        protected static final Object doContextObject(final ContextObject receiver, final long index, final Object value) {
            try {
                receiver.atTempPut(index - 1, value);
            } catch (IndexOutOfBoundsException e) {
                throw new PrimitiveFailed();
            }
            return value;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(index = 212)
    protected abstract static class PrimContextSizeNode extends AbstractPrimitiveNode {

        protected PrimContextSizeNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        protected static final long doSize(final ContextObject receiver) {
            return receiver.size() - receiver.instsize();
        }
    }
}
