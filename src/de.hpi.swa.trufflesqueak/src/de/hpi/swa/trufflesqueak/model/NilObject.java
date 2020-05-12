/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.model;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.profiles.ConditionProfile;

import de.hpi.swa.trufflesqueak.image.SqueakImageConstants;
import de.hpi.swa.trufflesqueak.image.SqueakImageWriter;

@ExportLibrary(InteropLibrary.class)
public final class NilObject extends AbstractSqueakObject {
    public static final NilObject SINGLETON = new NilObject();

    private NilObject() {
    }

    public static AbstractSqueakObject nullToNil(final AbstractSqueakObject object) {
        return object == null ? SINGLETON : object;
    }

    public static AbstractSqueakObject nullToNil(final AbstractSqueakObject object, final ConditionProfile profile) {
        return profile.profile(object == null) ? SINGLETON : object;
    }

    public static Object nullToNil(final Object object) {
        return object == null ? SINGLETON : object;
    }

    public static Object nullToNil(final Object object, final ConditionProfile profile) {
        return profile.profile(object == null) ? SINGLETON : object;
    }

    public static long getSqueakHash() {
        return 1L;
    }

    @Override
    public int getNumSlots() {
        return 0;
    }

    @Override
    public int instsize() {
        return 0;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public String toString() {
        return "nil";
    }

    @SuppressWarnings("static-method")
    @ExportMessage
    public boolean isNull() {
        return true;
    }

    public void write(final SqueakImageWriter writer) {
        writer.writeObjectHeader(instsize() + size(), getSqueakHash(), writer.getImage().nilClass, 0);
        writer.writePadding(SqueakImageConstants.WORD_SIZE); /* Write alignment word. */
    }
}
