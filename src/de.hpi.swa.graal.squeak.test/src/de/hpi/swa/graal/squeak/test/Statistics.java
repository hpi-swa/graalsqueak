package de.hpi.swa.graal.squeak.test;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import de.hpi.swa.graal.squeak.test.SqueakTests.SqueakTest;
import de.hpi.swa.graal.squeak.test.SqueakTests.TestType;
import java.util.Collection;
import java.util.Map;
import de.hpi.swa.graal.squeak.test.Travis.AnsiCodes;

final class Statistics {

    private Statistics() {
    }

    static void print(final Collection<SqueakTest> tests) {
        final Map<TestType, Long> counts = countByType(tests);

        print(TestType.PASSING, counts, AnsiCodes.GREEN);
        print(TestType.SLOW_PASSING, counts, AnsiCodes.GREEN);
        print(TestType.FLAKY, counts, AnsiCodes.YELLOW);
        print(TestType.SLOW_FAILING, counts, AnsiCodes.RED);
        print(TestType.FAILING, counts, AnsiCodes.RED);
        print(TestType.NOT_TERMINATING, counts, AnsiCodes.RED);
        print(TestType.BROKEN_IN_SQUEAK, counts, AnsiCodes.BLUE);
        print(TestType.IGNORED, counts, AnsiCodes.BOLD);
    }

    private static Map<TestType, Long> countByType(final Collection<SqueakTest> tests) {
        return tests.stream().collect(groupingBy(t -> t.type, counting()));
    }

    private static void print(final TestType type, final Map<TestType, Long> counts, final String color) {
        // Checkstyle: stop
        System.out.printf("%s%5d %s tests%s\n",
                        color,
                        counts.getOrDefault(type, 0L),
                        type.getMessage(),
                        AnsiCodes.RESET);
        // Checkstyle: resume
    }
}
