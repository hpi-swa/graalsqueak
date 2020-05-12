/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;

import org.junit.Test;

import de.hpi.swa.trufflesqueak.interop.WrapToSqueakNode;
import de.hpi.swa.trufflesqueak.model.AbstractSqueakObject;
import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.BooleanObject;
import de.hpi.swa.trufflesqueak.model.LargeIntegerObject;
import de.hpi.swa.trufflesqueak.model.NilObject;

public class SqueakPrimitiveTest extends AbstractSqueakTestCaseWithDummyImage {
    @Test
    public void testPrimEquivalent() {
        final AbstractSqueakObject rcvr = image.specialObjectsArray;
        assertTrue((boolean) runBinaryPrimitive(110, rcvr, rcvr));
        assertFalse((boolean) runBinaryPrimitive(110, rcvr, NilObject.SINGLETON));
    }

    @Test
    public void testPrimReplaceFromTo() {
        final ArrayObject rcvr = image.asArrayOfObjects(NilObject.SINGLETON, BooleanObject.FALSE, BooleanObject.TRUE, image.characterClass, image.metaClass,
                        image.schedulerAssociation, image.smallIntegerClass, image.smalltalk,
                        image.specialObjectsArray);
        assertSame(NilObject.SINGLETON, rcvr.getObject(0));
        for (int i = 1; i < 8; i++) {
            assertNotSame(NilObject.SINGLETON, rcvr.getObject(i));
        }
        final Object result = runQuinaryPrimitive(105, rcvr, 1L, 6L, ArrayObject.createEmptyStrategy(image, image.arrayClass, 10), 1L);
        assertSame(result, rcvr);
        for (int i = 0; i < 6; i++) {
            assertSame(NilObject.SINGLETON, rcvr.getObject(i));
        }
        for (int i = 7; i < 8; i++) {
            assertNotSame(NilObject.SINGLETON, rcvr.getObject(i));
        }
    }

    @Test
    public void testAdd() {
        final Object[][] testValues = new Object[][]{
                        {(long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE, 2 * (long) Integer.MAX_VALUE},
                        {Long.MAX_VALUE, Long.MAX_VALUE, new LargeIntegerObject(image, BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.valueOf(2)))},
                        {Long.MAX_VALUE, Long.MIN_VALUE, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.valueOf(Long.MIN_VALUE)).longValue()}};
        final WrapToSqueakNode wrapNode = WrapToSqueakNode.getUncached();
        for (int i = 0; i < testValues.length; i++) {
            final Object[] values = testValues[i];
            assertEquals(wrapNode.executeWrap(values[2]), runBinaryPrimitive(1, wrapNode.executeWrap(values[0]), wrapNode.executeWrap(values[1])));
        }
    }

    @Test
    public void testSub() {
        final Object[][] testValues = new Object[][]{
                        {(long) Integer.MAX_VALUE, (long) Integer.MAX_VALUE, 0L},
                        {Long.MAX_VALUE, Long.MAX_VALUE, 0L},
                        {Long.MAX_VALUE, Long.MAX_VALUE - 1, 1L},
                        {Long.MAX_VALUE, Long.MAX_VALUE - Integer.MAX_VALUE, (long) Integer.MAX_VALUE},
                        {Long.MIN_VALUE, 1L, new LargeIntegerObject(image, BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE))},
                        {Long.MAX_VALUE, Long.MAX_VALUE - Integer.MAX_VALUE, (long) Integer.MAX_VALUE}};
        final WrapToSqueakNode wrapNode = WrapToSqueakNode.getUncached();
        for (int i = 0; i < testValues.length; i++) {
            final Object[] values = testValues[i];
            assertEquals(wrapNode.executeWrap(values[2]), runBinaryPrimitive(2, wrapNode.executeWrap(values[0]), wrapNode.executeWrap(values[1])));
        }
        assertEquals(wrapNode.executeWrap(Long.MAX_VALUE),
                        runBinaryPrimitive(22, wrapNode.executeWrap(new LargeIntegerObject(image, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE))), wrapNode.executeWrap(1L)));
    }
}
