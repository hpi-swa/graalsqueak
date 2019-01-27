package de.hpi.swa.graal.squeak.nodes.accessing;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

import de.hpi.swa.graal.squeak.exceptions.SqueakExceptions.SqueakException;
import de.hpi.swa.graal.squeak.model.AbstractSqueakObject;
import de.hpi.swa.graal.squeak.model.ArrayObject;
import de.hpi.swa.graal.squeak.model.ClassObject;
import de.hpi.swa.graal.squeak.model.NilObject;
import de.hpi.swa.graal.squeak.model.PointersObject;
import de.hpi.swa.graal.squeak.nodes.accessing.ClassObjectNodesFactory.ClassObjectReadNodeGen;
import de.hpi.swa.graal.squeak.nodes.accessing.ClassObjectNodesFactory.ClassObjectWriteNodeGen;

public final class ClassObjectNodes {

    @ImportStatic(ClassObject.class)
    public abstract static class ClassObjectReadNode extends Node {
        protected static final int CACHE_LIMIT = 3;

        public static ClassObjectReadNode create() {
            return ClassObjectReadNodeGen.create();
        }

        public abstract Object execute(ClassObject obj, long index);

        @Specialization(guards = "isSuperclassIndex(index)")
        protected static final AbstractSqueakObject doClassSuperclass(final ClassObject obj, @SuppressWarnings("unused") final long index) {
            return obj.getSuperclass();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isMethodDictIndex(index)", "squeakClass == cachedSqueakClass"}, assumptions = {"cachedSqueakClass.getMethodDictStable()"}, limit = "CACHE_LIMIT")
        protected static final PointersObject doClassMethodDictConstant(final ClassObject squeakClass, final long index,
                        @Cached("squeakClass") final ClassObject cachedSqueakClass,
                        @Cached("squeakClass.getMethodDict()") final PointersObject cachedMethodDict) {
            return cachedMethodDict;
        }

        @Specialization(guards = {"isMethodDictIndex(index)"}, replaces = "doClassMethodDictConstant")
        protected static final PointersObject doClassMethodDict(final ClassObject obj, @SuppressWarnings("unused") final long index) {
            return obj.getMethodDict();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isFormatIndex(index)", "squeakClass == cachedSqueakClass"}, assumptions = {"cachedSqueakClass.getClassFormatStable()"}, limit = "CACHE_LIMIT")
        protected static final long doClassFormatConstant(final ClassObject squeakClass, final long index,
                        @Cached("squeakClass") final ClassObject cachedSqueakClass,
                        @Cached("squeakClass.getFormat()") final long cachedFormat) {
            return cachedFormat;
        }

        @Specialization(guards = {"isFormatIndex(index)"}, replaces = "doClassFormatConstant")
        protected static final long doClassFormat(final ClassObject obj, @SuppressWarnings("unused") final long index) {
            return obj.getFormat();
        }

        @Specialization(guards = "isInstanceVariablesIndex(index)")
        protected static final AbstractSqueakObject doClassInstanceVariables(final ClassObject obj, @SuppressWarnings("unused") final long index) {
            if (obj.getInstanceVariables() != null) {
                return obj.getInstanceVariables();
            } else {
                return obj.image.nilClass;
            }
        }

        @Specialization(guards = "isOrganizationIndex(index)")
        protected static final AbstractSqueakObject doClassOrganization(final ClassObject obj, @SuppressWarnings("unused") final long index) {
            if (obj.getOrganization() != null) {
                return obj.getOrganization();
            } else {
                return obj.image.nilClass;
            }
        }

        @Specialization(guards = "isOtherIndex(index)")
        protected static final Object doClass(final ClassObject obj, final long index) {
            return obj.getOtherPointer((int) index);
        }

        @Fallback
        protected static final void doFail(final ClassObject object, final long index) {
            throw new SqueakException("Unexpected value:", object, index);
        }
    }

    @ImportStatic(ClassObject.class)
    public abstract static class ClassObjectWriteNode extends Node {

        public static ClassObjectWriteNode create() {
            return ClassObjectWriteNodeGen.create();
        }

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
        protected static final void doClassMethodDict(final ClassObject obj, @SuppressWarnings("unused") final long index, final PointersObject value) {
            obj.setMethodDict(value);
        }

        @Specialization(guards = "isFormatIndex(index)")
        protected static final void doClassFormat(final ClassObject obj, @SuppressWarnings("unused") final long index, final Object value) {
            obj.setFormat((long) value);
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

        @Fallback
        protected static final void doFail(final ClassObject object, final long index, final Object value) {
            throw new SqueakException("Unexpected value:", object, index, value);
        }
    }
}
