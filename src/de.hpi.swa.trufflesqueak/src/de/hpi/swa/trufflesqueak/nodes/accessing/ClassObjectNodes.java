/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.nodes.accessing;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;

import de.hpi.swa.trufflesqueak.model.AbstractSqueakObject;
import de.hpi.swa.trufflesqueak.model.ArrayObject;
import de.hpi.swa.trufflesqueak.model.ClassObject;
import de.hpi.swa.trufflesqueak.model.NilObject;
import de.hpi.swa.trufflesqueak.model.PointersObject;
import de.hpi.swa.trufflesqueak.model.VariablePointersObject;
import de.hpi.swa.trufflesqueak.nodes.AbstractNode;

public final class ClassObjectNodes {
    @GenerateUncached
    @ImportStatic(ClassObject.class)
    public abstract static class ClassObjectReadNode extends AbstractNode {
        protected static final int CACHE_LIMIT = 3;

        public abstract Object execute(ClassObject obj, long index);

        @Specialization(guards = "isSuperclassIndex(index)")
        protected static final AbstractSqueakObject doClassSuperclass(final ClassObject obj, @SuppressWarnings("unused") final long index) {
            return obj.getSuperclass();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isMethodDictIndex(index)", "squeakClass == cachedSqueakClass"}, assumptions = {"cachedSqueakClass.getMethodDictStable()"}, limit = "CACHE_LIMIT")
        protected static final VariablePointersObject doClassMethodDictConstant(final ClassObject squeakClass, final long index,
                        @Cached("squeakClass") final ClassObject cachedSqueakClass,
                        @Cached("cachedSqueakClass.getMethodDict()") final VariablePointersObject cachedMethodDict) {
            return cachedMethodDict;
        }

        @Specialization(guards = {"isMethodDictIndex(index)"}, replaces = "doClassMethodDictConstant")
        protected static final VariablePointersObject doClassMethodDict(final ClassObject obj, @SuppressWarnings("unused") final long index) {
            return obj.getMethodDict();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isFormatIndex(index)", "squeakClass == cachedSqueakClass"}, assumptions = {"cachedSqueakClass.getClassFormatStable()"}, limit = "CACHE_LIMIT")
        protected static final long doClassFormatConstant(final ClassObject squeakClass, final long index,
                        @Cached("squeakClass") final ClassObject cachedSqueakClass,
                        @Cached("cachedSqueakClass.getFormat()") final long cachedFormat) {
            return cachedFormat;
        }

        @Specialization(guards = {"isFormatIndex(index)"}, replaces = "doClassFormatConstant")
        protected static final long doClassFormat(final ClassObject obj, @SuppressWarnings("unused") final long index) {
            return obj.getFormat();
        }

        @Specialization(guards = "isInstanceVariablesIndex(index)")
        protected static final AbstractSqueakObject doClassInstanceVariables(final ClassObject obj, @SuppressWarnings("unused") final long index) {
            return obj.getInstanceVariables();
        }

        @Specialization(guards = "isOrganizationIndex(index)")
        protected static final AbstractSqueakObject doClassOrganization(final ClassObject obj, @SuppressWarnings("unused") final long index) {
            return obj.getOrganization();
        }

        @Specialization(guards = "isOtherIndex(index)")
        protected static final Object doClass(final ClassObject obj, final long index) {
            return obj.getOtherPointer((int) index);
        }
    }

    @GenerateUncached
    @ImportStatic(ClassObject.class)
    public abstract static class ClassObjectWriteNode extends AbstractNode {

        public abstract void execute(ClassObject obj, long index, Object value);

        @Specialization(guards = "isSuperclassIndex(index)")
        protected static final void doClassSuperclass(final ClassObject obj, @SuppressWarnings("unused") final long index, final ClassObject value) {
            obj.setSuperclass(value);
        }

        @Specialization(guards = "isSuperclassIndex(index)")
        protected static final void doClassSuperclass(final ClassObject obj, @SuppressWarnings("unused") final long index, @SuppressWarnings("unused") final NilObject value) {
            obj.setSuperclass(null);
        }

        @Specialization(guards = "isMethodDictIndex(index)")
        protected static final void doClassMethodDict(final ClassObject obj, @SuppressWarnings("unused") final long index, final VariablePointersObject value) {
            obj.setMethodDict(value);
        }

        @Specialization(guards = "isFormatIndex(index)")
        protected static final void doClassFormat(final ClassObject obj, @SuppressWarnings("unused") final long index, final long value) {
            obj.setFormat(value);
        }

        @Specialization(guards = "isInstanceVariablesIndex(index)")
        protected static final void doClassInstanceVariables(final ClassObject obj, @SuppressWarnings("unused") final long index, final ArrayObject value) {
            obj.setInstanceVariables(value);
        }

        @Specialization(guards = "isInstanceVariablesIndex(index)")
        protected static final void doClassInstanceVariables(final ClassObject obj, @SuppressWarnings("unused") final long index, @SuppressWarnings("unused") final NilObject value) {
            obj.setInstanceVariables(null);
        }

        @Specialization(guards = "isOrganizationIndex(index)")
        protected static final void doClassOrganization(final ClassObject obj, @SuppressWarnings("unused") final long index, final PointersObject value) {
            obj.setOrganization(value);
        }

        @Specialization(guards = "isOrganizationIndex(index)")
        protected static final void doClassOrganization(final ClassObject obj, @SuppressWarnings("unused") final long index, @SuppressWarnings("unused") final NilObject value) {
            obj.setOrganization(null);
        }

        @Specialization(guards = "isOtherIndex(index)")
        protected static final void doClass(final ClassObject obj, final long index, final Object value) {
            obj.setOtherPointer((int) index, value);
        }
    }
}
