package com.carle7.energytracker.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// Octopus usage is refreshed every hour; the heavier Octopus account/tariff data runs once a day
// at 02:00 and the Growatt solar load once a day at midnight. Reads the real @Scheduled
// annotations, so a change to any of the cron expressions fails here.
class DataLoadSchedulerScheduleTest {

    private static final ZoneId LONDON = ZoneId.of("Europe/London");
    // A winter Monday, so no DST transition falls in the windows checked below.
    private static final ZonedDateTime START = ZonedDateTime.of(2026, 1, 5, 0, 0, 0, 0, LONDON);

    private static Scheduled scheduled(String method) throws NoSuchMethodException {
        return DataLoadScheduler.class.getMethod(method).getAnnotation(Scheduled.class);
    }

    @Test
    void octopusUsageRunsEveryHour() throws Exception {
        Scheduled schedule = scheduled("loadLatestUsageHourly");
        assertThat(schedule.zone()).isEqualTo("Europe/London");

        CronExpression cron = CronExpression.parse(schedule.cron());
        ZonedDateTime run = cron.next(START);
        assertThat(run).isEqualTo(START.withMinute(15));
        for (int i = 0; i < 48; i++) {
            ZonedDateTime next = cron.next(run);
            assertThat(Duration.between(run, next)).isEqualTo(Duration.ofHours(1));
            run = next;
        }
    }

    @Test
    void octopusAccountDataRunsOnceADayAt0200() throws Exception {
        assertOncePerDayAt("loadLatestAccountDataDaily", 2);
    }

    @Test
    void growattSolarRunsOnceADayAtMidnight() throws Exception {
        assertOncePerDayAt("loadLatestSolarDataDaily", 0);
    }

    private static void assertOncePerDayAt(String method, int hour) throws Exception {
        Scheduled schedule = scheduled(method);
        assertThat(schedule.zone()).isEqualTo("Europe/London");

        // From just before START's midnight, so a midnight job's first run is START itself.
        CronExpression cron = CronExpression.parse(schedule.cron());
        ZonedDateTime first = cron.next(START.minusSeconds(1));
        assertThat(first).isEqualTo(START.withHour(hour));
        ZonedDateTime second = cron.next(first);
        assertThat(second).isEqualTo(START.plusDays(1).withHour(hour));
        assertThat(Duration.between(first, second)).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void hourlyUsageJobDoesNotStartWithTheDailyJobs() throws Exception {
        // Kept off the hour so it never starts together with the midnight and 02:00 jobs.
        CronExpression usage = CronExpression.parse(scheduled("loadLatestUsageHourly").cron());

        for (int hour : new int[] {0, 2}) {
            ZonedDateTime justBefore = START.withHour(hour).minusMinutes(1);
            assertThat(usage.next(justBefore)).isNotEqualTo(START.withHour(hour));
        }
    }
}
