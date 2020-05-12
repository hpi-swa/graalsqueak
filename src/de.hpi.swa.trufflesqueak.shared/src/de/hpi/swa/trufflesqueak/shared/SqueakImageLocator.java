/*
 * Copyright (c) 2017-2020 Software Architecture Group, Hasso Plattner Institute
 *
 * Licensed under the MIT License.
 */
package de.hpi.swa.trufflesqueak.shared;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;

import org.graalvm.home.HomeFinder;

public final class SqueakImageLocator {

    /* Returns path to image file in TruffleSqueak's resources directory. */
    public static String findImage() {
        final Path resourcesDirectory = HomeFinder.getInstance().getLanguageHomes().get(SqueakLanguageConfig.ID).resolve("resources");
        final File resourcesDirectoryFile = resourcesDirectory.toFile();
        final String[] imageFiles = resourcesDirectoryFile.list((dir, name) -> dir == resourcesDirectoryFile && name.endsWith(".image"));
        if (imageFiles != null && imageFiles.length > 0) {
            /* Sort imageFiles alphabetically and return the last. */
            Arrays.sort(imageFiles);
            return resourcesDirectory.resolve(imageFiles[imageFiles.length - 1]).toString();
        } else {
            throw new RuntimeException("Unable to locate an image file in TruffleSqueak's resources directory.");
        }
    }
}
