package de.hpi.swa.graal.squeak;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import de.hpi.swa.graal.squeak.image.SqueakImageContext;
import de.hpi.swa.graal.squeak.interop.InteropArray;

@ExportLibrary(InteropLibrary.class)
public final class SqueakImage implements TruffleObject {
    private final SqueakImageContext image;

    public SqueakImage(final SqueakImageContext image) {
        this.image = image;
    }

    public String getName() {
        return image.getImagePath();
    }

    protected static boolean isInstance(final TruffleObject object) {
        return object instanceof SqueakImage;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean hasMembers() {
        return true;
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean isMemberReadable(@SuppressWarnings("unused") final String member) {
        return true;
    }

    @ExportMessage
    public Object getMembers(final boolean includeInternal) {
        final Object[] members;
        if (includeInternal) {
            members = new Object[]{image.getGlobals(), image.getCompilerClass()};
        } else {
            members = new Object[]{image.getGlobals()};
        }
        return new InteropArray(members);
    }

    @ExportMessage
    public Object readMember(final String key) {
        if ("Compiler".equals(key)) {
            return image.getCompilerClass();
        } else {
            // TODO:
            return image.getGlobals();
        }
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean isExecutable() {
        return true;
    }

    @ExportMessage
    public Object execute(final Object... arguments) {
        assert arguments.length == 0;
        image.interrupt.start();
        image.disableHeadless();
        return Truffle.getRuntime().createCallTarget(image.getActiveContextNode()).call();
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public static boolean accepts(@SuppressWarnings("unused") final SqueakImage receiver) {
        return true;
    }
}
