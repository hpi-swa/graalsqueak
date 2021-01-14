/*
 * Copyright (c) 2017-2021 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.primitives;

import java.util.Collections;
import java.util.List;

import com.oracle.truffle.api.dsl.NodeFactory;

public abstract class AbstractPrimitiveFactoryHolder {
    public abstract List<? extends NodeFactory<? extends AbstractPrimitiveNode>> getFactories();

    public List<? extends AbstractPrimitiveNode> getSingletonPrimitives() {
        return Collections.emptyList();
    }
}
