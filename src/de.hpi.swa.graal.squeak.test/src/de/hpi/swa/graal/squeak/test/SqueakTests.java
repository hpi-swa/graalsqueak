package de.hpi.swa.graal.squeak.test;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class SqueakTests {

    static final Pattern TEST_CASE = Pattern.compile("(\\w+)>>(\\w+)");
    private static final Pattern TEST_CASE_LINE = Pattern.compile("^" + TEST_CASE.pattern());
    private static final String FILENAME = "tests.properties";

    public enum TestType {
        PASSING("Passing"),
        FAILING("Failing"),
        FLAKY("Flaky"),
        NOT_TERMINATING("Not Terminating"),
        BROKEN_IN_SQUEAK("Broken in Squeak"),
        IGNORED("Ignored"), // unable to run (e.g., OOM, ...)
        SLOW_PASSING("Passing, but slow"),
        SLOW_FAILING("Failing and slow");

        private final String message;

        TestType(final String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    static final class SqueakTest {

        final TestType type;
        final String className;
        final String selector;

        SqueakTest(final TestType type, final String className, final String selector) {
            this.type = type;
            this.className = className;
            this.selector = selector;
        }

        String qualifiedName() {
            return className + ">>#" + selector;
        }

        @Override
        public String toString() {
            return type.getMessage() + ": " + className + ">>" + selector;
        }

        boolean nameEquals(final SqueakTest test) {
            return className.equals(test.className) && selector.equals(test.selector);
        }
    }

    private SqueakTests() {
    }

    static Stream<SqueakTest> getTestsToRun() {
        final Set<TestType> types = runnableTestTypes();
        return tests().filter(t -> types.contains(t.type));
    }

    private static Set<TestType> runnableTestTypes() {
        return new HashSet<>(asList(TestType.PASSING, TestType.FAILING, TestType.FLAKY, TestType.NOT_TERMINATING));
    }

    static Stream<SqueakTest> getTestsToRun(final String testClass) {
        return tests().filter(t -> t.className.equals(testClass));
    }

    /**
     * Test names in the order they appear in the file - useful for testing properties such as
     * sorting, duplication.
     */
    static List<String> rawTestNames() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(SqueakTests.class.getResourceAsStream(FILENAME)))) {
            return reader.lines().map(TEST_CASE_LINE::matcher).filter(Matcher::find).map(Matcher::group).collect(toList());
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static Stream<SqueakTest> tests() {
        final Properties properties = loadProperties();
        return properties.stringPropertyNames().stream().map(test -> parseTest(test, properties.getProperty(test))).sorted(
                        Comparator.comparing(t -> t.qualifiedName().toLowerCase()));
    }

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
