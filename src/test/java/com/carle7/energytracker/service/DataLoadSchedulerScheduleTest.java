package com.carle7.energytracker.service;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// Octopus usage is refreshed every hour; the heavier Octopus account/tariff data and the Growatt
// solar load stay once a day at 02:00. Reads the real @Scheduled annotations, so a change to any
// of the cron expressions fails here.
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
        assertOncePerDayAt0200("loadLatestAccountDataDaily");
    }

    @Test
    void growattSolarStillRunsOnceADayAt0200() throws Exception {
        assertOncePerDayAt0200("loadLatestSolarDataDaily");
    }

    private static void assertOncePerDayAt0200(String method) throws Exception {
        Scheduled schedule = scheduled(method);
        assertThat(schedule.zone()).isEqualTo("Europe/London");

        CronExpression cron = CronExpression.parse(schedule.cron());
        ZonedDateTime first = cron.next(START);
        assertThat(first).isEqualTo(START.withHour(2));
        ZonedDateTime second = cron.next(first);
        assertThat(second).isEqualTo(START.plusDays(1).withHour(2));
        assertThat(Duration.between(first, second)).isEqualTo(Duration.ofDays(1));
    }

    @Test
    void hourlyUsageJobDoesNotStartWithTheTwoAmJobs() throws Exception {
        // Kept off the hour so it never starts together with the two 02:00 jobs.
        CronExpression usage = CronExpression.parse(scheduled("loadLatestUsageHourly").cron());
        ZonedDateTime beforeTwo = START.withHour(1).withMinute(59);

        assertThat(usage.next(beforeTwo)).isNotEqualTo(START.withHour(2));
    }
}
