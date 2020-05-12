/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.plugins;

import java.time.ZonedDateTime;
import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.ArrayObjectNodes.ArrayObjectSizeNode;
import de.hpi.swa.trufflesqueak.nodes.accessing.ArrayObjectNodes.ArrayObjectWriteNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.SqueakPrimitive;
import de.hpi.swa.trufflesqueak.util.NotProvided;

public final class NullPlugin extends AbstractPrimitiveFactoryHolder {
    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveUtcWithOffset")
    protected abstract static class PrimUtcWithOffsetNode extends AbstractPrimitiveNode implements BinaryPrimitive {
        @Specialization
        @SuppressWarnings("unused")
        protected static final ArrayObject doUTC(final Object receiver, final NotProvided np,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.asArrayOfLongs(getUTCMicroseconds(), getOffsetFromGTMInSeconds());
        }

        @Specialization(guards = "objectWithTwoSlots.size() == 2")
        protected static final PointersObject doUTC(@SuppressWarnings("unused") final Object receiver, final PointersObject objectWithTwoSlots,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            writeNode.execute(objectWithTwoSlots, 0, getUTCMicroseconds());
            writeNode.execute(objectWithTwoSlots, 1, getOffsetFromGTMInSeconds());
            return objectWithTwoSlots;
        }

        @Specialization(guards = "sizeNode.execute(arrayWithTwoSlots) == 2", limit = "1")
        protected static final ArrayObject doUTC(@SuppressWarnings("unused") final Object receiver, final ArrayObject arrayWithTwoSlots,
                        @SuppressWarnings("unused") @Cached final ArrayObjectSizeNode sizeNode,
                        @Cached final ArrayObjectWriteNode writeNode) {
            writeNode.execute(arrayWithTwoSlots, 0, getUTCMicroseconds());
            writeNode.execute(arrayWithTwoSlots, 1, getOffsetFromGTMInSeconds());
            return arrayWithTwoSlots;
        }

        @TruffleBoundary
        private static long getUTCMicroseconds() {
            return System.currentTimeMillis() * 1000L;
        }

        @TruffleBoundary
        private static long getOffsetFromGTMInSeconds() {
            return ZonedDateTime.now().getOffset().getTotalSeconds();
        }
    }

    @Override
    public List<? extends NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return NullPluginFactory.getFactories();
    }
}
