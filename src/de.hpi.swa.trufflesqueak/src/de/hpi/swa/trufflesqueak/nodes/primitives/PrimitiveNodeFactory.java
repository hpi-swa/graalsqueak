/*
 * Copyright (c) 2017-2021 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.primitives;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.collections.EconomicMap;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.NodeFactory;

import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.model.NativeObject;
import de.hpi.swa.trufflesqueak.model.NilObject;
import de.hpi.swa.trufflesqueak.nodes.context.ArgumentNodes.AbstractArgumentNode;
import de.hpi.swa.trufflesqueak.nodes.context.ArgumentNodes.ArgumentNode;
import de.hpi.swa.trufflesqueak.nodes.plugins.B2DPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.BMPReadWriterPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.BitBltPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.ClipboardExtendedPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.CroquetPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.DSAPrims;
import de.hpi.swa.trufflesqueak.nodes.plugins.DropPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.FilePlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.Float64ArrayPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.FloatArrayPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.HostWindowPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.JPEGReadWriter2Plugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.JPEGReaderPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.LargeIntegers;
import de.hpi.swa.trufflesqueak.nodes.plugins.LocalePlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.Matrix2x3Plugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.MiscPrimitivePlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.NullPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.PolyglotPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.SecurityPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.SoundCodecPrims;
import de.hpi.swa.trufflesqueak.nodes.plugins.SqueakFFIPrims;
import de.hpi.swa.trufflesqueak.nodes.plugins.SqueakSSL;
import de.hpi.swa.trufflesqueak.nodes.plugins.TruffleSqueakPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.UUIDPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.UnixOSProcessPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.Win32OSProcessPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.ZipPlugin;
import de.hpi.swa.trufflesqueak.nodes.plugins.network.SocketPlugin;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.ArithmeticPrimitives;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.ArrayStreamPrimitives;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.BlockClosurePrimitives;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.ContextPrimitives;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.ControlPrimitives;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.IOPrimitives;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.MiscellaneousPrimitives;
import de.hpi.swa.trufflesqueak.nodes.primitives.impl.StoragePrimitives;
import de.hpi.swa.trufflesqueak.util.OSDetector;

public final class PrimitiveNodeFactory {
    private static final int PRIMITIVE_EXTERNAL_CALL_INDEX = 117;
    public static final int PRIMITIVE_LOAD_INST_VAR_LOWER_INDEX = 264;
    public static final int PRIMITIVE_LOAD_INST_VAR_UPPER_INDEX = 520;
    private static final int MAX_PRIMITIVE_INDEX = 575;
    @CompilationFinal(dimensions = 1) private static final byte[] NULL_MODULE_NAME = NullPlugin.class.getSimpleName().getBytes();

    private static final EconomicMap<Integer, AbstractPrimitiveNode> SINGLETON_PRIMITIVE_TABLE = EconomicMap.create();
    private static final EconomicMap<Integer, EconomicMap<Integer, NodeFactory<? extends AbstractPrimitiveNode>>> PRIMITIVE_TABLE = EconomicMap.create(MAX_PRIMITIVE_INDEX);
    private static final EconomicMap<String, EconomicMap<String, EconomicMap<Integer, NodeFactory<? extends AbstractPrimitiveNode>>>> PLUGIN_MAP = EconomicMap.create();

    static {
        final AbstractPrimitiveFactoryHolder[] indexPrimitives = new AbstractPrimitiveFactoryHolder[]{
                        new ArithmeticPrimitives(),
                        new ArrayStreamPrimitives(),
                        new BlockClosurePrimitives(),
                        new ContextPrimitives(),
                        new ControlPrimitives(),
                        new IOPrimitives(),
                        new MiscellaneousPrimitives(),
                        new StoragePrimitives()};
        fillPrimitiveTable(indexPrimitives);

        final AbstractPrimitiveFactoryHolder[] plugins = new AbstractPrimitiveFactoryHolder[]{
                        new B2DPlugin(),
                        new BitBltPlugin(),
                        new BMPReadWriterPlugin(),
                        new ClipboardExtendedPlugin(),
                        new CroquetPlugin(),
                        new DropPlugin(),
                        new DSAPrims(),
                        new FilePlugin(),
                        new FloatArrayPlugin(),
                        new Float64ArrayPlugin(),
                        new TruffleSqueakPlugin(),
                        new HostWindowPlugin(),
                        new JPEGReaderPlugin(),
                        new JPEGReadWriter2Plugin(),
                        new LargeIntegers(),
                        new LocalePlugin(),
                        new Matrix2x3Plugin(),
                        new MiscPrimitivePlugin(),
                        new NullPlugin(),
                        new PolyglotPlugin(),
                        new SecurityPlugin(),
                        new SocketPlugin(),
                        new SoundCodecPrims(),
                        new SqueakFFIPrims(),
                        new SqueakSSL(),
                        new UUIDPlugin(),
                        new ZipPlugin(),
                        OSDetector.SINGLETON.isWindows() ? new Win32OSProcessPlugin() : new UnixOSProcessPlugin()};
        fillPrimitiveTable(plugins);
        fillPluginMap(plugins);
    }

    private PrimitiveNodeFactory() {
    }

    public static AbstractPrimitiveNode forIndex(final CompiledCodeObject method, final boolean useStack, final int primitiveIndex, final boolean argsProvided) {
        CompilerAsserts.neverPartOfCompilation("Primitive node instantiation should never happen on fast path");
        assert primitiveIndex >= 0 : "Unexpected negative primitiveIndex";
        if (primitiveIndex == PRIMITIVE_EXTERNAL_CALL_INDEX) {
            return namedFor(method, useStack, argsProvided);
        } else if (isLoadInstVarPrimitive(primitiveIndex)) {
            assert method.getNumArgs() == 0;
            return createPrimLoadInstVarNode(primitiveIndex, useStack);
        } else {
            assert primitiveIndex <= MAX_PRIMITIVE_INDEX;
            final AbstractPrimitiveNode primitiveNode = SINGLETON_PRIMITIVE_TABLE.get(primitiveIndex);
            if (primitiveNode != null) {
                assert method.getNumArgs() == 0;
                return primitiveNode;
            } else {
                return createInstance(method, useStack, argsProvided, PRIMITIVE_TABLE.get(primitiveIndex));
            }
        }
    }

    public static AbstractPrimitiveNode forIndex(final int primitiveIndex, final int numArgs, final boolean argsProvided) {
        assert primitiveIndex != PRIMITIVE_EXTERNAL_CALL_INDEX;
        if (isLoadInstVarPrimitive(primitiveIndex)) {
            assert numArgs == 0;
            return createPrimLoadInstVarNode(primitiveIndex, false);
        } else {
            assert primitiveIndex <= MAX_PRIMITIVE_INDEX;
            final AbstractPrimitiveNode primitiveNode = SINGLETON_PRIMITIVE_TABLE.get(primitiveIndex);
            if (primitiveNode != null) {
                assert numArgs == 0;
                return primitiveNode;
            } else {
                return createInstance(PRIMITIVE_TABLE.get(primitiveIndex), numArgs, argsProvided);
            }
        }
    }

    public static boolean isLoadInstVarPrimitive(final int primitiveIndex) {
        return PRIMITIVE_LOAD_INST_VAR_LOWER_INDEX <= primitiveIndex && primitiveIndex <= PRIMITIVE_LOAD_INST_VAR_UPPER_INDEX;
    }

    public static AbstractPrimitiveNode namedFor(final CompiledCodeObject method, final boolean useStack, final boolean argsProvided) {
        final Object[] values = ((ArrayObject) method.getLiteral(0)).getObjectStorage();
        if (values[1] == NilObject.SINGLETON) {
            return null;
        } else if (values[0] == NilObject.SINGLETON) {
            final NativeObject functionName = (NativeObject) values[1];
            return forName(method, useStack, argsProvided, NULL_MODULE_NAME, functionName.getByteStorage());
        } else {
            final NativeObject moduleName = (NativeObject) values[0];
            final NativeObject functionName = (NativeObject) values[1];
            return forName(method, useStack, argsProvided, moduleName.getByteStorage(), functionName.getByteStorage());
        }
    }

    private static AbstractPrimitiveNode forName(final CompiledCodeObject method, final boolean useStack, final boolean argsProvided, final byte[] moduleName, final byte[] functionName) {
        CompilerAsserts.neverPartOfCompilation("Primitive node instantiation should never happen on fast path");
        final EconomicMap<String, EconomicMap<Integer, NodeFactory<? extends AbstractPrimitiveNode>>> functionNameToNodeFactory = PLUGIN_MAP.get(new String(moduleName));
        if (functionNameToNodeFactory != null) {
            return createInstance(method, useStack, argsProvided, functionNameToNodeFactory.get(new String(functionName)));
        }
        return null;
    }

    private static AbstractPrimitiveNode createPrimLoadInstVarNode(final int primitiveIndex, final boolean useStack) {
        return ControlPrimitives.PrimLoadInstVarNode.create(primitiveIndex - PRIMITIVE_LOAD_INST_VAR_LOWER_INDEX,
                        new AbstractArgumentNode[]{ArgumentNode.create(0, useStack)});
    }

    public static NodeFactory<? extends AbstractPrimitiveNode> getNodeFactory(final int primitiveIndex, final int numArguments) {
        assert !isLoadInstVarPrimitive(primitiveIndex);
        final EconomicMap<Integer, NodeFactory<? extends AbstractPrimitiveNode>> map = PRIMITIVE_TABLE.get(primitiveIndex);
        if (map == null) {
            return null;
        }
        return map.get(numArguments);
    }

    public static NodeFactory<? extends AbstractPrimitiveNode> getNodeFactory(final CompiledCodeObject method, final int numArguments) {
        assert method.hasPrimitive() && method.primitiveIndex() == 117;
        final Object[] values = ((ArrayObject) method.getLiteral(0)).getObjectStorage();
        final String moduleName = ((NativeObject) values[0]).asStringUnsafe();
        final EconomicMap<String, EconomicMap<Integer, NodeFactory<? extends AbstractPrimitiveNode>>> pluginMap = PLUGIN_MAP.get(moduleName);
        if (pluginMap == null) {
            return null;
        }
        final String functionName = ((NativeObject) values[1]).asStringUnsafe();
        final EconomicMap<Integer, NodeFactory<? extends AbstractPrimitiveNode>> map = pluginMap.get(functionName);
        if (map == null) {
            return null;
        }
        return map.get(numArguments);
    }

    public static String[] getPluginNames() {
        final Set<String> target = new HashSet<>();
        for (final String key : PLUGIN_MAP.getKeys()) {
            target.add(key);
        }
        return target.toArray(new String[0]);
    }

    private static AbstractPrimitiveNode createInstance(final CompiledCodeObject method, final boolean useStack, final boolean argsProvided,
                    final EconomicMap<Integer, NodeFactory<? extends AbstractPrimitiveNode>> map) {
        if (map == null) {
            return null;
        }
        final int numReceiverAndArguments = 1 + method.getNumArgs();
        final NodeFactory<? extends AbstractPrimitiveNode> nodeFactory = map.get(numReceiverAndArguments);
        if (nodeFactory == null) {
            return null;
        }
        assert numReceiverAndArguments == nodeFactory.getExecutionSignature().size();
        AbstractArgumentNode[] argumentNodes = null;
        if (!argsProvided) {
            argumentNodes = new AbstractArgumentNode[numReceiverAndArguments];
            for (int i = 0; i < numReceiverAndArguments; i++) {
                argumentNodes[i] = AbstractArgumentNode.create(i, useStack);
            }
        }
        final AbstractPrimitiveNode primitiveNode = nodeFactory.createNode((Object) argumentNodes);
        if (primitiveNode.acceptsMethod(method)) {
            return primitiveNode;
        } else {
            return null;
        }
    }

    private static AbstractPrimitiveNode createInstance(final EconomicMap<Integer, NodeFactory<? extends AbstractPrimitiveNode>> map, final int numArgs, final boolean argsProvided) {
        if (map == null) {
            return null;
        }
        final int numReceiverAndArguments = 1 + numArgs;
        final NodeFactory<? extends AbstractPrimitiveNode> nodeFactory = map.get(numReceiverAndArguments);
        if (nodeFactory == null) {
            assert false : "Unable to find primitive with arity: " + numReceiverAndArguments;
            return null;
        }
        assert numReceiverAndArguments == nodeFactory.getExecutionSignature().size();
        AbstractArgumentNode[] argumentNodes = null;
        if (!argsProvided) {
            argumentNodes = new AbstractArgumentNode[numReceiverAndArguments];
            for (int i = 0; i < numReceiverAndArguments; i++) {
                argumentNodes[i] = AbstractArgumentNode.create(i, false);
            }
        }
        return nodeFactory.createNode((Object) argumentNodes);
    }

    private static void fillPrimitiveTable(final AbstractPrimitiveFactoryHolder[] primitiveFactories) {
        for (final AbstractPrimitiveFactoryHolder primitiveFactory : primitiveFactories) {
            for (final NodeFactory<? extends AbstractPrimitiveNode> nodeFactory : primitiveFactory.getFactories()) {
                final Class<? extends AbstractPrimitiveNode> primitiveClass = nodeFactory.getNodeClass();
                final SqueakPrimitive primitive = primitiveClass.getAnnotation(SqueakPrimitive.class);
                for (final int index : primitive.indices()) {
                    addEntryToPrimitiveTable(index, nodeFactory);
                }
            }
            for (final AbstractPrimitiveNode singleton : primitiveFactory.getSingletonPrimitives()) {
                final Class<? extends AbstractPrimitiveNode> primitiveClass = singleton.getClass();
                final SqueakPrimitive primitive = primitiveClass.getAnnotation(SqueakPrimitive.class);
                for (final int index : primitive.indices()) {
                    assert !SINGLETON_PRIMITIVE_TABLE.containsKey(index) && !PRIMITIVE_TABLE.containsKey(index);
                    SINGLETON_PRIMITIVE_TABLE.put(index, singleton);
                }
            }
        }
    }

    private static void fillPluginMap(final AbstractPrimitiveFactoryHolder[] plugins) {
        for (final AbstractPrimitiveFactoryHolder plugin : plugins) {
            final List<? extends NodeFactory<? extends AbstractPrimitiveNode>> nodeFactories = plugin.getFactories();
            final EconomicMap<String, EconomicMap<Integer, NodeFactory<? extends AbstractPrimitiveNode>>> functionNameToNodeFactory = EconomicMap.create(nodeFactories.size());
            for (final NodeFactory<? extends AbstractPrimitiveNode> nodeFactory : nodeFactories) {
                final Class<? extends AbstractPrimitiveNode> primitiveClass = nodeFactory.getNodeClass();
                final SqueakPrimitive primitive = primitiveClass.getAnnotation(SqueakPrimitive.class);
                for (final String name : primitive.names()) {
                    EconomicMap<Integer, NodeFactory<? extends AbstractPrimitiveNode>> map = functionNameToNodeFactory.get(name);
                    if (map == null) {
                        map = EconomicMap.create();
                        functionNameToNodeFactory.put(name, map);
                    }
                    final int numReceiverAndArguments = nodeFactory.getExecutionSignature().size();
                    assert !map.containsKey(numReceiverAndArguments) : "Primitive name is not unique: " + name;
                    map.put(numReceiverAndArguments, nodeFactory);
                }
            }
            final String pluginName = plugin.getClass().getSimpleName();
            PLUGIN_MAP.put(pluginName, EconomicMap.create(functionNameToNodeFactory));
        }
    }

    private static void addEntryToPrimitiveTable(final int index, final NodeFactory<? extends AbstractPrimitiveNode> nodeFactory) {
        assert index <= MAX_PRIMITIVE_INDEX : "primitive table array not large enough";
        assert !SINGLETON_PRIMITIVE_TABLE.containsKey(index);
        EconomicMap<Integer, NodeFactory<? extends AbstractPrimitiveNode>> map = PRIMITIVE_TABLE.get(index);
        if (map == null) {
            map = EconomicMap.create();
            PRIMITIVE_TABLE.put(index, map);
        }
        final int numReceiverAndArguments = nodeFactory.getExecutionSignature().size();
        assert !map.containsKey(numReceiverAndArguments) : "primitives are not allowed to override others (#" + index + ")";
        map.put(nodeFactory.getExecutionSignature().size(), nodeFactory);
    }
}
