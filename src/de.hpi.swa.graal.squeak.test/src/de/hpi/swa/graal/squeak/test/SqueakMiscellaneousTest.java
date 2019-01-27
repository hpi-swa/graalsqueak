package de.hpi.swa.graal.squeak.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import de.hpi.swa.graal.squeak.image.reading.SqueakImageChunk;
import de.hpi.swa.graal.squeak.model.CompiledCodeObject;
import de.hpi.swa.graal.squeak.model.FloatObject;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.CONTEXT;
import de.hpi.swa.graal.squeak.nodes.bytecodes.AbstractBytecodeNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.JumpBytecodes.ConditionalJumpNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.MiscellaneousBytecodes.DupNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.MiscellaneousBytecodes.PopNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.PushBytecodes.PushConstantNode;
import de.hpi.swa.graal.squeak.nodes.bytecodes.ReturnBytecodes.ReturnReceiverNode;
import de.hpi.swa.graal.squeak.util.ArrayConversionUtils;
import de.hpi.swa.graal.squeak.util.CompiledCodeObjectPrinter;
import de.hpi.swa.graal.squeak.util.SqueakBytecodeDecoder;

public class SqueakMiscellaneousTest extends AbstractSqueakTestCaseWithDummyImage {
    private static final String ALL_BYTECODES_EXPECTED_RESULT = String.join("\n", "1 <0F> pushRcvr: 15",
                    "2 <1F> pushTemp: 15",
                    "3 <20> pushConstant: someSelector",
                    "4 <5F> pushLit: 31",
                    "5 <60> popIntoRcvr: 0",
                    "6 <61> popIntoRcvr: 1",
                    "7 <62> popIntoRcvr: 2",
                    "8 <63> popIntoRcvr: 3",
                    "9 <67> popIntoRcvr: 7",
                    "10 <6F> popIntoTemp: 7",
                    "11 <70> self",
                    "12 <71> pushConstant: true",
                    "13 <72> pushConstant: false",
                    "14 <73> pushConstant: nil",
                    "15 <74> pushConstant: -1",
                    "16 <75> pushConstant: 0",
                    "17 <76> pushConstant: 1",
                    "18 <77> pushConstant: 2",
                    "19 <78> returnSelf",
                    "20 <79> return: true",
                    "21 <7A> return: false",
                    "22 <7B> return: nil",
                    "23 <7C> returnTop",
                    "24 <7E> unknown: 126",
                    "25 <7F> unknown: 127",
                    "26 <80 1F> pushRcvr: 31",
                    "27 <81 1F> storeIntoRcvr: 31",
                    "28 <82 1F> popIntoRcvr: 31",
                    "29 <83 20> send: someSelector",
                    "30 <84 1F 01> send: someOtherSelector",
                    "31 <85 20> sendSuper: someSelector",
                    "32 <86 01> send: someOtherSelector",
                    "33 <87> pop",
                    "34 <88> dup",
                    "35 <89> pushThisContext:",
                    "36 <8A 1F> push: (Array new: 31)",
                    "37 <8B 1F 00> callPrimitive: 31",
                    "38 <8C 1F 38> pushTemp: 31 inVectorAt: 56",
                    "39 <8D 1F 38> storeIntoTemp: 31 inVectorAt: 56",
                    "40 <8E 1F 38> popIntoTemp: 31 inVectorAt: 56",
                    "41 <8F 1F 3F 7F> closureNumCopied: 1 numArgs: 15 bytes 61 to 16316",
                    "42  <97> jumpTo: 8",
                    "43  <9F> jumpFalse: 8",
                    "44  <A7 1F> jumpTo: 799",
                    "45  <AB 1F> jumpTrue: 799",
                    "46  <AF 1F> jumpFalse: 799",
                    "47  <7D> blockReturn",
                    "48 <B0> send: plus",
                    "49 <B1> send: minus",
                    "50 <B2> send: lt",
                    "51 <B3> send: gt",
                    "52 <B4> send: le",
                    "53 <B5> send: ge",
                    "54 <B6> send: eq",
                    "55 <B7> send: ne",
                    "56 <B8> send: times",
                    "57 <B9> send: divide",
                    "58 <BA> send: modulo",
                    "59 <BB> send: pointAt",
                    "60 <BC> send: bitShift",
                    "61 <BD> send: floorDivide",
                    "62 <BE> send: bitAnd",
                    "63 <BF> send: bitOr",
                    "64 <C0> send: at",
                    "65 <C1> send: atput",
                    "66 <C2> send: size",
                    "67 <C3> send: next",
                    "68 <C4> send: nextPut",
                    "69 <C5> send: atEnd",
                    "70 <C6> send: equivalent",
                    "71 <C7> send: klass",
                    "72 <C8> send: blockCopy",
                    "73 <C9> send: value",
                    "74 <CA> send: valueWithArg",
                    "75 <CB> send: do",
                    "76 <CC> send: new",
                    "77 <CD> send: newWithArg",
                    "78 <CE> send: x",
                    "79 <CF> send: y",
                    "80 <D0> send: someSelector",
                    "81 <E1> send: someOtherSelector",
                    "82 <F0> send: someSelector");

    @Test
    public void testIfNil() {
        // (1 ifNil: [true]) class
        // pushConstant: 1, dup, pushConstant: nil, send: ==, jumpFalse: 24, pop,
        // pushConstant: true, send: class, pop, returnSelf
        final int[] bytes = {0x76, 0x88, 0x73, 0xc6, 0x99, 0x87, 0x71, 0xc7, 0x87, 0x78};
        final CompiledCodeObject code = makeMethod(bytes);
        final AbstractBytecodeNode[] bytecodeNodes = SqueakBytecodeDecoder.decode(code);
        assertEquals(bytes.length, bytecodeNodes.length);
        assertSame(PushConstantNode.class, bytecodeNodes[0].getClass());
        assertSame(DupNode.class, bytecodeNodes[1].getClass());
        assertSame(PushConstantNode.class, bytecodeNodes[2].getClass());

        assertEquals("send: " + image.equivalent.asString(), bytecodeNodes[3].toString());

        assertSame(ConditionalJumpNode.class, bytecodeNodes[4].getClass());
        assertSame(PopNode.class, bytecodeNodes[5].getClass());
        assertSame(PushConstantNode.class, bytecodeNodes[6].getClass());

        assertEquals("send: " + image.klass.asString(), bytecodeNodes[7].toString());

        assertSame(PopNode.class, bytecodeNodes[8].getClass());
        assertTrue(ReturnReceiverNode.class.isAssignableFrom(bytecodeNodes[9].getClass()));
    }

    @Test
    public void testSource() {
        final Object[] literals = new Object[]{14548994L, image.nil, image.nil}; // header with
                                                                                 // numTemp=55
        final CompiledCodeObject code = makeMethod(literals, 0x70, 0x68, 0x10, 0x8F, 0x10, 0x00, 0x02, 0x10, 0x7D, 0xC9, 0x7C);
        final CharSequence source = CompiledCodeObjectPrinter.getString(code);
        assertEquals(String.join("\n",
                        "1 <70> self",
                        "2 <68> popIntoTemp: 0",
                        "3 <10> pushTemp: 0",
                        "4 <8F 10 00 02> closureNumCopied: 1 numArgs: 0 bytes 7 to 9",
                        "5  <10> pushTemp: 0",
                        "6  <7D> blockReturn",
                        "7 <C9> send: value",
                        "8 <7C> returnTop"), source);
    }

    @Test
    public void testSourceAllBytecodes() {
        final Object[] literals = new Object[]{17235971L, image.wrap("someSelector"), image.wrap("someOtherSelector"), 63};
        final CompiledCodeObject code = makeMethod(literals,
                        15, 31, 32, 95, 96, 97, 98, 99, 103, 111, 112, 113, 114, 115, 116,
                        117, 118, 119, 120, 121, 122, 123, 124, 126, 127,
                        128, 31,
                        129, 31,
                        130, 31,
                        131, 32,
                        132, 31, 1,
                        133, 32,
                        134, 1,
                        135,
                        136,
                        137,
                        138, 31,
                        139, 31, 0,
                        140, 31, CONTEXT.LARGE_FRAMESIZE,
                        141, 31, CONTEXT.LARGE_FRAMESIZE,
                        142, 31, CONTEXT.LARGE_FRAMESIZE,
                        143, 31, 63, 127,
                        151,
                        159,
                        167, 31,
                        171, 31,
                        175, 31,
                        125,
                        176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188,
                        189, 190, 191, 192, 193, 194, 195, 196, 197, 198, 199, 200, 201,
                        202, 203, 204, 205, 206, 207, 208, 225, 240);
        final CharSequence source = CompiledCodeObjectPrinter.getString(code);
        assertEquals(ALL_BYTECODES_EXPECTED_RESULT, source);
    }

    @Test
    public void testFloatDecoding() {
        SqueakImageChunk chunk = newFloatChunk(ArrayConversionUtils.bytesFromIntsReversed(new int[]{0, 1072693248}));
        assertEquals(1.0, getDouble(chunk), 0);

        chunk = newFloatChunk(ArrayConversionUtils.bytesFromIntsReversed(new int[]{(int) 2482401462L, 1065322751}));
        assertEquals(0.007699011184197404, getDouble(chunk), 0);

        chunk = newFloatChunk(ArrayConversionUtils.bytesFromIntsReversed(new int[]{876402988, 1075010976}));
        assertEquals(4.841431442464721, getDouble(chunk), 0);
    }

    private static double getDouble(final SqueakImageChunk chunk) {
        return ((FloatObject) chunk.asObject()).getValue();
    }

    private static SqueakImageChunk newFloatChunk(final byte[] data) {
        final SqueakImageChunk chunk = new SqueakImageChunk(
                        null,
                        image,
                        data, // 2 words
                        10, // float format, 32-bit words without padding word
                        34, // classid of BoxedFloat64
                        3833906, // identityHash for 1.0
                        0 // position
        );
        chunk.setSqClass(image.floatClass);
        return chunk;
    }
}
