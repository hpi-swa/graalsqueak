/*
 * Copyright (c) 2017-2021 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.bytecodes;

import com.oracle.truffle.api.frame.VirtualFrame;

import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;

public abstract class AbstractSqueakBytecodeDecoder {

    public abstract AbstractBytecodeNode decodeBytecode(VirtualFrame frame, CompiledCodeObject code, int index);

    public abstract String decodeToString(CompiledCodeObject code);

    public abstract int findLineNumber(CompiledCodeObject code, int targetIndex);

    public abstract int trailerPosition(CompiledCodeObject code);

    public abstract boolean hasStoreIntoTemp1AfterCallPrimitive(CompiledCodeObject code);
}
