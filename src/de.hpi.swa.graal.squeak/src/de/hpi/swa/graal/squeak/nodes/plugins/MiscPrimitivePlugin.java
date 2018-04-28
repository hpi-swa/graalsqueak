package de.hpi.swa.graal.squeak.nodes.plugins;

import java.util.List;

import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.graal.squeak.exceptions.PrimitiveExceptions.PrimitiveFailed;
import de.hpi.swa.graal.squeak.model.BaseSqueakObject;
import de.hpi.swa.graal.squeak.model.CompiledMethodObject;
import de.hpi.swa.graal.squeak.model.NativeObject;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.graal.squeak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.graal.squeak.nodes.primitives.SqueakPrimitive;

public class MiscPrimitivePlugin extends AbstractPrimitiveFactoryHolder {

    @Override
    public List<? extends NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return MiscPrimitivePluginFactory.getFactories();
    }

    public abstract static class AbstractMiscPrimitiveNode extends AbstractPrimitiveNode {

        public AbstractMiscPrimitiveNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        protected static boolean isASCIIOrder(final NativeObject order) {
            final byte[] bytes = order.getBytes();
            for (int i = 0; i < 256; i++) {
                if (bytes[i] != (byte) i) {
                    return false;
                }
            }
            return true;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveCompareString")
    public abstract static class PrimCompareStringNode extends AbstractMiscPrimitiveNode {

        public PrimCompareStringNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = "isASCIIOrder(order)")
        protected static final long doAsciiOrder(@SuppressWarnings("unused") final BaseSqueakObject receiver, final NativeObject string1, final NativeObject string2,
                        @SuppressWarnings("unused") final NativeObject order) {
            final byte[] bytes1 = string1.getBytes();
            final byte[] bytes2 = string2.getBytes();
            final int length1 = bytes1.length;
            final int length2 = bytes2.length;
            final int minLength = Math.min(bytes1.length, length2);
            for (int i = 0; i < minLength; i++) {
                final byte c1 = bytes1[i];
                final byte c2 = bytes2[i];
                if (c1 != c2) {
                    if (c1 < c2) {
                        return 1;
                    } else {
                        return 3;
                    }
                }
            }
            if (length1 == length2) {
                return 2;
            } else if (length1 < length2) {
                return 1;
            } else {
                return 3;
            }
        }

        @Specialization(guards = "!isASCIIOrder(order)")
        protected static final long doCollated(@SuppressWarnings("unused") final BaseSqueakObject receiver, final NativeObject string1, final NativeObject string2, final NativeObject order) {
            final byte[] bytes1 = string1.getBytes();
            final byte[] bytes2 = string2.getBytes();
            final byte[] orderBytes = order.getBytes();
            final int length1 = bytes1.length;
            final int length2 = bytes2.length;
            final int minLength = Math.min(length1, length2);
            for (int i = 0; i < minLength; i++) {
                final byte c1 = orderBytes[bytes1[i]];
                final byte c2 = orderBytes[bytes2[i]];
                if (c1 != c2) {
                    if (c1 < c2) {
                        return 1;
                    } else {
                        return 3;
                    }
                }
            }
            if (length1 == length2) {
                return 2;
            } else if (length1 < length2) {
                return 1;
            } else {
                return 3;
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveCompressToByteArray")
    public abstract static class PrimCompressToByteArrayNode extends AbstractMiscPrimitiveNode {

        public PrimCompressToByteArrayNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected static final Object compress(final BaseSqueakObject bitmap, final Object bm, final Object from) {
            // TODO: implement primitive
            throw new PrimitiveFailed();
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveDecompressFromByteArray")
    public abstract static class PrimDecompressFromByteArrayNode extends AbstractMiscPrimitiveNode {

        public PrimDecompressFromByteArrayNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @SuppressWarnings("unused")
        @Specialization
        protected static final Object decompress(final BaseSqueakObject bitmap, final Object bm, final Object from, final long index) {
            // TODO: implement primitive
            throw new PrimitiveFailed();
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFindFirstInString")
    public abstract static class PrimFindFirstInStringNode extends AbstractMiscPrimitiveNode {

        public PrimFindFirstInStringNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        protected long doFind(@SuppressWarnings("unused") final BaseSqueakObject receiver, final NativeObject string, final NativeObject inclusionMap, final long start) {
            if (inclusionMap.size() != 256) {
                return 0;
            }
            final byte[] stringBytes = string.getBytes();
            final int stringSize = stringBytes.length;
            int index = (int) start;
            for (; index <= stringSize; index++) {
                if (inclusionMap.getNativeAt0((int) string.getNativeAt0(index - 1)) != 0) {
                    break;
                }
            }
            if (index > stringSize) {
                return 0;
            } else {
                return index;
            }
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveFindSubstring")
    public abstract static class PrimFindSubstringNode extends AbstractMiscPrimitiveNode {

        public PrimFindSubstringNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization(guards = "isASCIIOrder(matchTable)")
        protected long doFindAscii(@SuppressWarnings("unused") final BaseSqueakObject receiver, final NativeObject key, final NativeObject body, final long start,
                        @SuppressWarnings("unused") final NativeObject matchTable) {
            return body.toString().indexOf(key.toString(), (int) start - 1) + 1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isASCIIOrder(matchTable)")
        protected long doFindWithMatchTable(final BaseSqueakObject receiver, final NativeObject key, final NativeObject body, final long start, final NativeObject matchTable) {
            throw new PrimitiveFailed(); // TODO: implement primitive
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveIndexOfAsciiInString")
    public abstract static class PrimIndexOfAsciiInStringNode extends AbstractMiscPrimitiveNode {

        public PrimIndexOfAsciiInStringNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        protected long doNativeObject(@SuppressWarnings("unused") final BaseSqueakObject receiver, final long value, final NativeObject string, final long start) {
            if (start < 0) {
                throw new PrimitiveFailed();
            }
            final byte[] bytes = string.getBytes();
            for (int i = (int) (start - 1); i < bytes.length; i++) {
                if (bytes[i] == value) {
                    return i + 1;
                }
            }
            return 0;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveStringHash")
    public abstract static class PrimStringHashNode extends AbstractMiscPrimitiveNode {

        public PrimStringHashNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        protected long doNativeObject(@SuppressWarnings("unused") final BaseSqueakObject receiver, final NativeObject string, final long initialHash) {
            long hash = initialHash & 0xfffffff;
            long low;
            for (byte value : string.getBytes()) {
                hash += value & 0xff;
                low = hash & 16383;
                hash = (0x260D * low + (((0x260d * (hash >> 14) + (0x0065 * low)) & 16383) * 16384)) & 0x0fffffff;
            }
            return hash;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(name = "primitiveTranslateStringWithTable")
    public abstract static class PrimTranslateStringWithTableNode extends AbstractMiscPrimitiveNode {

        public PrimTranslateStringWithTableNode(final CompiledMethodObject method, final int numArguments) {
            super(method, numArguments);
        }

        @Specialization
        protected NativeObject doNativeObject(@SuppressWarnings("unused") final BaseSqueakObject receiver, final NativeObject string, final long start, final long stop, final NativeObject table) {
            for (int i = (int) start - 1; i < stop; i++) {
                final Long tableValue = (long) table.at0(((Long) string.getNativeAt0(i)).intValue());
                string.setByte(i, tableValue.byteValue());
            }
            return string;
        }
    }
}
