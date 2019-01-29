package de.hpi.swa.graal.squeak.test;

import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SqueakTests {

    protected static final Pattern TEST_CASE = Pattern.compile("(\\w+)>>(\\w+)");
    private static final Pattern TEST_CASE_LINE = Pattern.compile("^" + TEST_CASE.pattern());
    private static final String FILENAME = "tests.properties";

    public enum TestType {
        PASSING("Passing"),
        FAILING("Failing"),
        FLAKY("Flaky"),
        NOT_TERMINATING("Not Terminating"),
        BROKEN_IN_SQUEAK("Broken in Squeak"),
        IGNORED("Ignored"), // unable to run (e.g., OOM, ...)
        SLOWLY_PASSING("Passing, but slow"),
        SLOWLY_FAILING("Failing and slow");

        private final String message;

        TestType(final String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    protected static final class SqueakTest {

        protected final TestType type;
        protected final String className;
        protected final String selector;

        protected SqueakTest(final TestType type, final String className, final String selector) {
            this.type = type;
            this.className = className;
            this.selector = selector;
        }

        protected String qualifiedName() {
            return className + ">>#" + selector;
        }

        @Override
        public String toString() {
            return type.getMessage() + ": " + className + ">>" + selector;
        }

        protected boolean nameEquals(final SqueakTest test) {
            return className.equals(test.className) && selector.equals(test.selector);
        }
    }

    private SqueakTests() {
    }

    protected static Stream<SqueakTest> getTestsToRun(final String testClass) {
        final List<SqueakTest> tests = allTests().filter(t -> t.className.equals(testClass)).collect(toList());
        if (tests.isEmpty()) {
            throw new IllegalArgumentException("No test cases found for filter expression '" + testClass + "'");
        }
        return tests.stream();
    }

    /**
     * Test names in the order they appear in the file - useful for testing properties such as
     * sorting, duplication.
     */
    protected static List<String> rawTestNames() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(SqueakTests.class.getResourceAsStream(FILENAME)))) {
            return reader.lines().map(TEST_CASE_LINE::matcher).filter(Matcher::find).map(Matcher::group).collect(toList());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Checkstyle: stop
    protected static Stream<SqueakTest> allTests() {
        final Properties properties = loadProperties();
        return properties.stringPropertyNames().stream() //
                        .map(test -> parseTest(test, properties.getProperty(test))) //
                        .sorted(Comparator.comparing(t -> t.qualifiedName().toLowerCase()));
    }
    // Checkstyle: resume

    private static SqueakTest parseTest(final String test, final String type) {
        final Matcher matcher = TEST_CASE_LINE.matcher(test);
        if (matcher.matches()) {
            return new SqueakTest(parseType(type), matcher.group(1), matcher.group(2));
        }

        throw new IllegalArgumentException(test);
    }

    private static TestType parseType(final String type) {
        return TestType.valueOf(type.toUpperCase());
    }

    private static Properties loadProperties() {
        try (InputStream in = SqueakTests.class.getResourceAsStream(FILENAME)) {
            final Properties properties = new Properties();
            properties.load(in);
            return properties;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
