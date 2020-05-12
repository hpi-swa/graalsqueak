/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.plugins;

import java.util.List;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.trufflesqueak.SqueakLanguage;
import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.model.AbstractSqueakObject;
import de.hpi.swa.trufflesqueak.model.ClassObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.nodes.accessing.AbstractPointersObjectNodes.AbstractPointersObjectWriteNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveFactoryHolder;
import de.hpi.swa.trufflesqueak.nodes.primitives.AbstractPrimitiveNode;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.BinaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.OctonaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.QuinaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.SenaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.SeptenaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.TernaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.PrimitiveInterfaces.UnaryPrimitive;
import de.hpi.swa.trufflesqueak.nodes.primitives.SqueakPrimitive;

public final class B2DPlugin extends AbstractPrimitiveFactoryHolder {

    @Override
    public List<? extends NodeFactory<? extends AbstractPrimitiveNode>> getFactories() {
        return B2DPluginFactory.getFactories();
    }

    // primitiveAbortProcessing omitted because it does not seem to be used.

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveAddActiveEdgeEntry")
    protected abstract static class PrimAddActiveEdgeEntryNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doAdd(final PointersObject receiver, final PointersObject edgeEntry,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveAddActiveEdgeEntry(receiver, edgeEntry);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveAddBezier")
    protected abstract static class PrimAddBezierNode extends AbstractPrimitiveNode implements SenaryPrimitive {

        @Specialization(guards = {"start.isPoint()", "stop.isPoint()", "via.isPoint()"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doAdd(final PointersObject receiver, final PointersObject start, final PointersObject stop, final PointersObject via, final long leftFillIndex,
                        final long rightFillIndex,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveAddBezier(receiver, start, stop, via, leftFillIndex, rightFillIndex);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveAddBezierShape")
    protected abstract static class PrimAddBezierShapeNode extends AbstractPrimitiveNode implements SenaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doAdd(final PointersObject receiver, final AbstractSqueakObject points, final long nSegments, final long fillStyle, final long lineWidth,
                        final long lineFill,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveAddBezierShape(receiver, points, nSegments, fillStyle, lineWidth, lineFill);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveAddBitmapFill")
    protected abstract static class PrimAddBitmapFillNode extends AbstractPrimitiveNode implements OctonaryPrimitive {

        @Specialization(guards = {"xIndex > 0", "origin.isPoint()", "direction.isPoint()", "normal.isPoint()"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doAdd(final PointersObject receiver, final PointersObject form, final AbstractSqueakObject cmap, final boolean tileFlag, final PointersObject origin,
                        final PointersObject direction, final PointersObject normal, final long xIndex,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveAddBitmapFill(receiver, form, cmap, tileFlag, origin, direction, normal, xIndex);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveAddCompressedShape")
    protected abstract static class PrimAddCompressedShapeNode extends AbstractPrimitiveNode implements OctonaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doAdd(final PointersObject receiver, final NativeObject points, final long nSegments, final NativeObject leftFills, final NativeObject rightFills,
                        final NativeObject lineWidths, final NativeObject lineFills, final NativeObject fillIndexList,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveAddCompressedShape(receiver, points, nSegments, leftFills, rightFills, lineWidths, lineFills, fillIndexList);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveAddGradientFill")
    protected abstract static class PrimAddGradientFillNode extends AbstractPrimitiveNode implements SenaryPrimitive {

        @Specialization(guards = {"colorRamp.getSqueakClass().isBitmapClass()", "origin.isPoint()", "direction.isPoint()", "normal.isPoint()"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doAdd(final PointersObject receiver, final NativeObject colorRamp, final PointersObject origin, final PointersObject direction, final PointersObject normal,
                        final boolean isRadial,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveAddGradientFill(receiver, colorRamp, origin, direction, normal, isRadial);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveAddLine")
    protected abstract static class PrimAddLineNode extends AbstractPrimitiveNode implements QuinaryPrimitive {

        @Specialization(guards = {"start.isPoint()", "end.isPoint()"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doAdd(final PointersObject receiver, final PointersObject start, final PointersObject end, final long leftFill, final long rightFill,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveAddLine(receiver, start, end, leftFill, rightFill);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveAddOval")
    protected abstract static class PrimAddOvalNode extends AbstractPrimitiveNode implements SenaryPrimitive {

        @Specialization(guards = {"start.isPoint()", "end.isPoint()"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doAdd(final PointersObject receiver, final PointersObject start, final PointersObject end, final long fillIndex, final long width,
                        final long pixelValue32,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveAddOval(receiver, start, end, fillIndex, width, pixelValue32);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveAddPolygon")
    protected abstract static class PrimAddPolygonNode extends AbstractPrimitiveNode implements SenaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doAdd(final PointersObject receiver, final AbstractSqueakObject points, final long nSegments, final long fillStyle, final long lineWidth,
                        final long lineFill,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveAddPolygon(receiver, points, nSegments, fillStyle, lineWidth, lineFill);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveAddRect")
    protected abstract static class PrimAddRectNode extends AbstractPrimitiveNode implements SenaryPrimitive {

        @Specialization(guards = {"start.isPoint()", "end.isPoint()"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doAdd(final PointersObject receiver, final PointersObject start, final PointersObject end, final long fillIndex, final long width,
                        final long pixelValue32,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveAddRect(receiver, start, end, fillIndex, width, pixelValue32);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveChangedActiveEdgeEntry")
    protected abstract static class PrimChangedActiveEdgeEntryNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doChange(final PointersObject receiver, final PointersObject edgeEntry,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveChangedActiveEdgeEntry(receiver, edgeEntry);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveCopyBuffer")
    protected abstract static class PrimCopyBufferNode extends AbstractPrimitiveNode implements TernaryPrimitive {

        @Specialization(guards = {"oldBuffer.isIntType()", "newBuffer.isIntType()"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doCopy(final PointersObject receiver, final NativeObject oldBuffer, final NativeObject newBuffer,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveCopyBuffer(receiver, oldBuffer, newBuffer);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveDisplaySpanBuffer")
    protected abstract static class PrimDisplaySpanBufferNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doDisplay(final PointersObject receiver,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveDisplaySpanBuffer(receiver);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveDoProfileStats")
    protected abstract static class PrimDoProfileStatsNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doProfile(final Object receiver, final boolean aBoolean,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveDoProfileStats(receiver, aBoolean);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveFinishedProcessing")
    protected abstract static class PrimFinishedProcessingNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doCopy(final PointersObject receiver,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveFinishedProcessing(receiver);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetAALevel")
    protected abstract static class PrimGetAALevelNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doGet(final PointersObject receiver,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveGetAALevel(receiver);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetBezierStats")
    protected abstract static class PrimGetBezierStatsNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization(guards = {"statsArray.isIntType()", "statsArray.getIntLength() >= 4"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doGet(final PointersObject receiver, final NativeObject statsArray,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveGetBezierStats(receiver, statsArray);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetClipRect")
    protected abstract static class PrimGetClipRectNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization(guards = {"rect.size() >= 2"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doGet(final PointersObject receiver, final PointersObject rect,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            return image.b2d.primitiveGetClipRect(writeNode, receiver, rect);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetCounts")
    protected abstract static class PrimGetCountsNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization(guards = {"statsArray.isIntType()", "statsArray.getIntLength() >= 9"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doGet(final PointersObject receiver, final NativeObject statsArray,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveGetCounts(receiver, statsArray);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetDepth")
    protected abstract static class PrimGetDepthNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doGet(final PointersObject receiver,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveGetDepth(receiver);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetFailureReason")
    protected abstract static class PrimGetFailureReasonNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doGet(final PointersObject receiver,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveGetFailureReason(receiver);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetOffset")
    protected abstract static class PrimGetOffsetNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doGet(final PointersObject receiver,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image,
                        @Cached final AbstractPointersObjectWriteNode writeNode) {
            return image.b2d.primitiveGetOffset(writeNode, receiver);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveGetTimes")
    protected abstract static class PrimGetTimesNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization(guards = {"statsArray.isIntType()", "statsArray.getIntLength() >= 9"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doGet(final PointersObject receiver, final NativeObject statsArray,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveGetTimes(receiver, statsArray);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveInitializeBuffer")
    protected abstract static class PrimInitializeBufferNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization(guards = {"buffer.isIntType()", "hasMinimalSize(buffer)"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doInit(final Object receiver, final NativeObject buffer,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveInitializeBuffer(receiver, buffer);
        }

        protected static final boolean hasMinimalSize(final NativeObject buffer) {
            return buffer.getIntLength() >= B2D.GW_MINIMAL_SIZE;
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveInitializeProcessing")
    protected abstract static class PrimInitializeProcessingNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doCopy(final PointersObject receiver,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveInitializeProcessing(receiver);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveMergeFillFrom")
    protected abstract static class PrimMergeFillFromNode extends AbstractPrimitiveNode implements TernaryPrimitive {

        @Specialization(guards = {"fillBitmap.getSqueakClass().isBitmapClass()"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doCopy(final PointersObject receiver, final NativeObject fillBitmap, final PointersObject fill,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveMergeFillFrom(receiver, fillBitmap, fill);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveNeedsFlush")
    protected abstract static class PrimNeedsFlushNode extends AbstractPrimitiveNode implements UnaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doNeed(final PointersObject receiver,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveNeedsFlush(receiver);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveNeedsFlushPut")
    protected abstract static class PrimNeedsFlushPutNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doNeed(final PointersObject receiver, final boolean aBoolean,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveNeedsFlushPut(receiver, aBoolean);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveNextActiveEdgeEntry")
    protected abstract static class PrimNextActiveEdgeEntryNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doNext(final PointersObject receiver, final PointersObject edgeEntry,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveNextActiveEdgeEntry(receiver, edgeEntry);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveNextFillEntry")
    protected abstract static class PrimNextFillEntryNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doNext(final PointersObject receiver, final PointersObject fillEntry,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveNextFillEntry(receiver, fillEntry);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveNextGlobalEdgeEntry")
    protected abstract static class PrimNextGlobalEdgeEntryNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doNext(final PointersObject receiver, final PointersObject edgeEntry,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveNextGlobalEdgeEntry(receiver, edgeEntry);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveRegisterExternalEdge")
    protected abstract static class PrimRegisterExternalEdgeNode extends AbstractPrimitiveNode implements SeptenaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doRegister(final PointersObject receiver, final long index, final long initialX, final long initialY, final long initialZ, final long leftFillIndex,
                        final long rightFillIndex,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveRegisterExternalEdge(receiver, index, initialX, initialY, initialZ, leftFillIndex, rightFillIndex);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveRegisterExternalFill")
    protected abstract static class PrimRegisterExternalFillNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doRegister(final PointersObject receiver, final long index,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveRegisterExternalFill(receiver, index);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveRenderImage")
    protected abstract static class PrimRenderImageNode extends AbstractPrimitiveNode implements TernaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doRender(final PointersObject receiver, final PointersObject edge, final PointersObject fill,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveRenderImage(receiver, edge, fill);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveRenderScanline")
    protected abstract static class PrimRenderScanlineNode extends AbstractPrimitiveNode implements TernaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doRender(final PointersObject receiver, final PointersObject edge, final PointersObject fill,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveRenderScanline(receiver, edge, fill);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSetAALevel")
    protected abstract static class PrimSetAALevelNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doSet(final PointersObject receiver, final long level,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveSetAALevel(receiver, level);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSetBitBltPlugin")
    protected abstract static class PrimSetBitBltPluginNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization(guards = {"pluginName.isByteType()"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doSet(final ClassObject receiver, final NativeObject pluginName) {
            return B2D.primitiveSetBitBltPlugin(receiver, pluginName);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSetClipRect")
    protected abstract static class PrimSetClipRectNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization(guards = {"rect.size() >= 2"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doSet(final PointersObject receiver, final PointersObject rect,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveSetClipRect(receiver, rect);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSetColorTransform")
    protected abstract static class PrimSetColorTransformNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doSet(final PointersObject receiver, final AbstractSqueakObject transform,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveSetColorTransform(receiver, transform);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSetDepth")
    protected abstract static class PrimSetDepthNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doSet(final PointersObject receiver, final long depth,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveSetDepth(receiver, depth);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSetEdgeTransform")
    protected abstract static class PrimSetEdgeTransformNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doSet(final PointersObject receiver, final AbstractSqueakObject transform,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveSetEdgeTransform(receiver, transform);
        }
    }

    @GenerateNodeFactory
    @SqueakPrimitive(names = "primitiveSetOffset")
    protected abstract static class PrimSetOffsetNode extends AbstractPrimitiveNode implements BinaryPrimitive {

        @Specialization(guards = {"point.isPoint()"})
        @TruffleBoundary(transferToInterpreterOnException = false)
        protected static final Object doSet(final PointersObject receiver, final PointersObject point,
                        @CachedContext(SqueakLanguage.class) final SqueakImageContext image) {
            return image.b2d.primitiveSetOffset(receiver, point);
        }
    }
}
