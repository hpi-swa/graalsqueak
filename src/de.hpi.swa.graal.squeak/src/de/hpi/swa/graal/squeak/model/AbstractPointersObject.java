package de.hpi.swa.graal.squeak.model;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;

public abstract class AbstractPointersObject extends AbstractSqueakObject {
    @CompilationFinal(dimensions = 0) private Object[] pointers;

    protected AbstractPointersObject(final SqueakImageContext image) {
        super(image);
    }

    protected AbstractPointersObject(final SqueakImageContext image, final int hash) {
        super(image, hash);
    }

    protected AbstractPointersObject(final SqueakImageContext image, final ClassObject sqClass) {
        super(image, sqClass);
    }

    protected AbstractPointersObject(final SqueakImageContext image, final long hash, final ClassObject sqClass) {
        super(image, hash, sqClass);
    }

    public final Object getPointer(final int index) {
        return pointers[index];
    }

    public final Object[] getPointers() {
        return pointers;
    }

    public final void setPointer(final int index, final Object value) {
        this.pointers[index] = value;
    }

    public final void setPointersUnsafe(final Object[] pointers) {
        this.pointers = pointers;
    }

    public final void setPointers(final Object[] pointers) {
        // assert this.pointers == null || this.pointers.length == pointers.length : "Pointers
        // lengths do not match (wrong assumption?)";
        // TODO: find out if invalidation should be avoided by copying values if pointers != null
        CompilerDirectives.transferToInterpreterAndInvalidate();
        this.pointers = pointers;
    }

    public final int size() {
        return pointers.length;
    }
}
