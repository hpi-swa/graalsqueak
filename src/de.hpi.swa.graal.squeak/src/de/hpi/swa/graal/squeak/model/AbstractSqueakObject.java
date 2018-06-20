package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.interop.ForeignAccess;
import com.oracle.truffle.api.interop.TruffleObject;

import de.hpi.swa.graal.squeak.image.AbstractImageChunk;
import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.instrumentation.SqueakObjectMessageResolutionForeign;
import de.hpi.swa.graal.squeak.model.ObjectLayouts.SPECIAL_OBJECT_INDEX;

public abstract class AbstractSqueakObject implements TruffleObject {
    @CompilationFinal private static final int IDENTITY_HASH_MASK = 0x400000 - 1;
    @CompilationFinal private static final byte PINNED_BIT_SHIFT = 30;
    @CompilationFinal public final SqueakImageContext image;
    @CompilationFinal private long hash;
    @CompilationFinal private ClassObject sqClass;

    protected AbstractSqueakObject(final SqueakImageContext image) {
        this(image, null);
    }

    protected AbstractSqueakObject(final SqueakImageContext image, final ClassObject klass) {
        this.image = image;
        this.hash = hashCode() & IDENTITY_HASH_MASK;
        this.sqClass = klass;
    }

    public static final boolean isInstance(final TruffleObject obj) {
        return obj instanceof AbstractSqueakObject;
    }

    public void fillin(final AbstractImageChunk chunk) {
        hash = chunk.getHash();
        sqClass = chunk.getSqClass();
    }

    @Override
    public String toString() {
        return "a " + getSqClassName();
    }

    public final long squeakHash() {
        return hash;
    }

    public final void setSqueakHash(final long hash) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.hash = hash;
    }

    public final ClassObject getSqClass() {
        return sqClass;
    }

    public final void setSqClass(final ClassObject newCls) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.sqClass = newCls;
    }

    public String nameAsClass() {
        return "???NotAClass";
    }

    public final String getSqClassName() {
        if (isClass()) {
            return nameAsClass() + " class";
        } else {
            return getSqClass().nameAsClass();
        }
    }

    public final boolean isClass() {
        assert !(this instanceof ClassObject) || (image.metaclass == getSqClass() || image.metaclass == getSqClass().getSqClass());
        return this instanceof ClassObject;
    }

    public final boolean isNil() {
        return this instanceof NilObject;
    }

    public final boolean isSpecialKindAt(final long index) {
        return getSqClass() == image.specialObjectsArray.at0(index);
    }

    public final boolean isSpecialClassAt(final long index) {
        return this == image.specialObjectsArray.at0(index);
    }

    public final boolean isSemaphore() {
        return isSpecialKindAt(SPECIAL_OBJECT_INDEX.ClassSemaphore);
    }

    public boolean become(final AbstractSqueakObject other) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        final ClassObject otherSqClass = other.sqClass;
        other.sqClass = this.sqClass;
        this.sqClass = otherSqClass;
        return true;
    }

    public void pointersBecomeOneWay(final Object[] from, final Object[] to, final boolean copyHash) {
        final ClassObject oldClass = getSqClass();
        for (int i = 0; i < from.length; i++) {
            if (from[i] == oldClass) {
                final ClassObject newClass = (ClassObject) to[i]; // must be a ClassObject
                setSqClass(newClass);
                if (copyHash) {
                    newClass.setSqueakHash(oldClass.squeakHash());
                }
            }
        }
    }

    @Override
    public final ForeignAccess getForeignAccess() {
        return SqueakObjectMessageResolutionForeign.ACCESS;
    }

    public final boolean isPinned() {
        return ((hash >> PINNED_BIT_SHIFT) & 1) == 1;
    }

    public final void setPinned() {
        setSqueakHash(hash | (1 << PINNED_BIT_SHIFT));
    }

    public final void unsetPinned() {
        setSqueakHash(hash & ~(1 << PINNED_BIT_SHIFT));
    }
}
