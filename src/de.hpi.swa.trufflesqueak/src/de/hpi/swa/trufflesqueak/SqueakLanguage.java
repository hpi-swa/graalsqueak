/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak;

import java.util.Arrays;
import java.util.Collections;

import org.graalvm.options.OptionDescriptors;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.Scope;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.debug.DebuggerTags;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.instrumentation.ProvidedTags;
import com.oracle.truffle.api.instrumentation.StandardTags;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.source.Source;

import de.hpi.swa.trufflesqueak.image.SqueakImageContext;
import de.hpi.swa.trufflesqueak.interop.ContextObjectInfo;
import de.hpi.swa.trufflesqueak.interop.InteropArray;
import de.hpi.swa.trufflesqueak.interop.SqueakFileDetector;
import de.hpi.swa.trufflesqueak.model.CompiledCodeObject;
import de.hpi.swa.trufflesqueak.shared.SqueakLanguageConfig;
import de.hpi.swa.trufflesqueak.util.FrameAccess;
import de.hpi.swa.trufflesqueak.util.MiscUtils;

@TruffleLanguage.Registration(//
                byteMimeTypes = SqueakLanguageConfig.MIME_TYPE, //
                characterMimeTypes = SqueakLanguageConfig.ST_MIME_TYPE, //
                defaultMimeType = SqueakLanguageConfig.ST_MIME_TYPE, //
                dependentLanguages = {"nfi"}, //
                fileTypeDetectors = SqueakFileDetector.class, //
                id = SqueakLanguageConfig.ID, //
                implementationName = SqueakLanguageConfig.IMPLEMENTATION_NAME, //
                interactive = true, //
                internal = false, //
                name = SqueakLanguageConfig.NAME, //
                version = SqueakLanguageConfig.VERSION)
@ProvidedTags({StandardTags.StatementTag.class, StandardTags.CallTag.class, StandardTags.RootTag.class, DebuggerTags.AlwaysHalt.class})
public final class SqueakLanguage extends TruffleLanguage<SqueakImageContext> {

    @Override
    protected SqueakImageContext createContext(final Env env) {
        return new SqueakImageContext(this, env);
    }

    @Override
    protected CallTarget parse(final ParsingRequest request) throws Exception {
        final SqueakImageContext image = getContext();
        final Source source = request.getSource();
        if (source.hasBytes()) {
            image.setImagePath(source.getPath());
            return image.getSqueakImage().asCallTarget();
        } else {
            image.ensureLoaded();
            if (source.isInternal()) {
                image.printToStdOut(MiscUtils.format("Evaluating '%s'...", source.getCharacters().toString()));
            }
            return Truffle.getRuntime().createCallTarget(image.getDoItContextNode(source));
        }
    }

    @Override
    protected boolean isThreadAccessAllowed(final Thread thread, final boolean singleThreaded) {
        return true; // TODO: Experimental, make TruffleSqueak work in multiple threads.
    }

    @Override
    protected Iterable<Scope> findTopScopes(final SqueakImageContext context) {
        context.ensureLoaded();
        return Arrays.asList(Scope.newBuilder("Smalltalk", context.getGlobals()).build());
    }

    @Override
    protected Iterable<Scope> findLocalScopes(final SqueakImageContext context, final Node node, final Frame frame) {
        // TODO: support access at parse time (frame == null).
        if (!FrameAccess.isTruffleSqueakFrame(frame)) {
            return super.findLocalScopes(context, node, frame);
        }
        final CompiledCodeObject blockOrMethod = FrameAccess.getBlockOrMethod(frame);
        final String name = blockOrMethod.toString();
        final Object receiver = FrameAccess.getReceiver(frame);
        final ContextObjectInfo variables = new ContextObjectInfo(frame);
        final InteropArray arguments = new InteropArray(frame.getArguments());
        return Collections.singletonList(Scope.newBuilder(name, variables).node(node).receiver(receiver.toString(), receiver).arguments(arguments).build());
    }

    public static SqueakImageContext getContext() {
        CompilerAsserts.neverPartOfCompilation();
        return getCurrentContext(SqueakLanguage.class);
    }

    public String getTruffleLanguageHome() {
        return getLanguageHome();
    }

    @Override
    protected OptionDescriptors getOptionDescriptors() {
        return SqueakOptions.createDescriptors();
    }

    @Override
    protected boolean patchContext(final SqueakImageContext context, final Env newEnv) {
        return context.patch(newEnv);
    }
}
