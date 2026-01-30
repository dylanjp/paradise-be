package com.dylanjohnpratt.paradise.be.service;

import com.dylanjohnpratt.paradise.be.model.RecurrenceRule;
import com.dylanjohnpratt.paradise.be.model.RecurrenceRule.RecurrenceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for edge cases in RecurrenceService.
 * Tests leap year handling, month boundary handling, and validation error messages.
 * 
 * Validates: Requirements 1.3, 1.4, 2.6, 5.1-5.5
 */
class RecurrenceServiceEdgeCaseTest {

    private RecurrenceService service;
    private static final ZoneId UTC = ZoneId.of("UTC");

    @BeforeEach
    void setUp() {
        service = new RecurrenceService(new Random(42));
    }

    /**
     * Tests for leap year handling (Requirement 1.3)
     */
    @Nested
    @DisplayName("Leap Year Handling Tests")
    class LeapYearHandlingTests {

        @Test
        @DisplayName("Feb 29 YEARLY rule delivers only in leap years")
        void feb29YearlyRuleDeliversOnlyInLeapYears() {
            // Create YEARLY rule for Feb 29
            RecurrenceRule feb29Rule = new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 29, false,
                    2, null, null, null, null, null, null);

            // 2024 is a leap year - should deliver
            LocalDate leapYearFeb29 = LocalDate.of(2024, 2, 29);
            assertThat(service.shouldDeliverOn(feb29Rule, leapYearFeb29, UTC))
                    .as("Should deliver on Feb 29 in leap year 2024")
                    .isTrue();

            // 2023 is not a leap year - Feb 29 doesn't exist, test Feb 28
            LocalDate nonLeapYearFeb28 = LocalDate.of(2023, 2, 28);
            assertThat(service.shouldDeliverOn(feb29Rule, nonLeapYearFeb28, UTC))
                    .as("Should NOT deliver on Feb 28 in non-leap year 2023")
                    .isFalse();

            // 2020 is a leap year - should deliver
            LocalDate leapYear2020Feb29 = LocalDate.of(2020, 2, 29);
            assertThat(service.shouldDeliverOn(feb29Rule, leapYear2020Feb29, UTC))
                    .as("Should deliver on Feb 29 in leap year 2020")
                    .isTrue();
        }

        @Test
        @DisplayName("getNextDeliveryDate for Feb 29 finds next leap year")
        void getNextDeliveryDateForFeb29FindsNextLeapYear() {
            // Create YEARLY rule for Feb 29
            RecurrenceRule feb29Rule = new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 29, false,
                    2, null, null, null, null, null, null);

            // Starting from March 2024 (after Feb 29 2024), should find Feb 29 2028
            LocalDate afterFeb29_2024 = LocalDate.of(2024, 3, 1);
            Optional<LocalDate> nextDelivery = service.getNextDeliveryDate(feb29Rule, afterFeb29_2024, UTC);
            
            assertThat(nextDelivery)
                    .isPresent()
                    .hasValue(LocalDate.of(2028, 2, 29));

            // Starting from Jan 2024 (before Feb 29 2024), should find Feb 29 2024
            LocalDate beforeFeb29_2024 = LocalDate.of(2024, 1, 15);
            nextDelivery = service.getNextDeliveryDate(feb29Rule, beforeFeb29_2024, UTC);
            
            assertThat(nextDelivery)
                    .isPresent()
                    .hasValue(LocalDate.of(2024, 2, 29));

            // Starting from 2023 (non-leap year), should find Feb 29 2024
            LocalDate inNonLeapYear = LocalDate.of(2023, 6, 15);
            nextDelivery = service.getNextDeliveryDate(feb29Rule, inNonLeapYear, UTC);
            
            assertThat(nextDelivery)
                    .isPresent()
                    .hasValue(LocalDate.of(2024, 2, 29));
        }

        @Test
        @DisplayName("Feb 29 rule is valid and can be created")
        void feb29RuleIsValidAndCanBeCreated() {
            // Should not throw - Feb 29 is valid for leap years
            assertThatCode(() -> new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 29, false,
                    2, null, null, null, null, null, null))
                    .doesNotThrowAnyException();
        }
    }


    /**
     * Tests for month boundary handling (Requirements 1.4, 2.6)
     */
    @Nested
    @DisplayName("Month Boundary Handling Tests")
    class MonthBoundaryHandlingTests {

        @Test
        @DisplayName("Day 31 in months with 30 days does not deliver")
        void day31InMonthsWith30DaysDoesNotDeliver() {
            // Create YEARLY rule for April 31 - should fail validation
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 31, false,
                    4, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Day 31 does not exist in month 4");
        }

        @Test
        @DisplayName("Day 30 in February is rejected")
        void day30InFebruaryIsRejected() {
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 30, false,
                    2, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Day 30 does not exist in month 2");
        }

        @Test
        @DisplayName("Day 31 in months with 31 days is valid")
        void day31InMonthsWith31DaysIsValid() {
            // January has 31 days
            assertThatCode(() -> new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 31, false,
                    1, null, null, null, null, null, null))
                    .doesNotThrowAnyException();

            // March has 31 days
            assertThatCode(() -> new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 31, false,
                    3, null, null, null, null, null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Day 30 in months with 30 days is valid")
        void day30InMonthsWith30DaysIsValid() {
            // April has 30 days
            assertThatCode(() -> new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 30, false,
                    4, null, null, null, null, null, null))
                    .doesNotThrowAnyException();

            // June has 30 days
            assertThatCode(() -> new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 30, false,
                    6, null, null, null, null, null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("RANDOM_DATE_RANGE with invalid start date is rejected")
        void randomDateRangeWithInvalidStartDateIsRejected() {
            // Feb 30 as start date
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.RANDOM_DATE_RANGE, null, null, false,
                    null, 2, 30, 5, 15, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Day 30 does not exist in month 2");
        }

        @Test
        @DisplayName("RANDOM_DATE_RANGE with invalid end date is rejected")
        void randomDateRangeWithInvalidEndDateIsRejected() {
            // April 31 as end date
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.RANDOM_DATE_RANGE, null, null, false,
                    null, 1, 15, 4, 31, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Day 31 does not exist in month 4");
        }
    }


    /**
     * Tests for validation error messages (Requirements 5.1-5.5)
     */
    @Nested
    @DisplayName("Validation Error Message Tests")
    class ValidationErrorMessageTests {

        @Test
        @DisplayName("YEARLY without month throws with correct message")
        void yearlyWithoutMonthThrowsWithCorrectMessage() {
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 15, false,
                    null, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Month is required for yearly recurrence");
        }

        @Test
        @DisplayName("YEARLY without dayOfMonth throws with correct message")
        void yearlyWithoutDayOfMonthThrowsWithCorrectMessage() {
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.YEARLY, null, null, false,
                    5, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Day of month is required for yearly recurrence");
        }

        @Test
        @DisplayName("YEARLY with month outside 1-12 throws with correct message")
        void yearlyWithMonthOutsideRangeThrowsWithCorrectMessage() {
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 15, false,
                    13, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Month must be between 1 and 12");

            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 15, false,
                    0, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Month must be between 1 and 12");
        }

        @Test
        @DisplayName("RANDOM_DATE_RANGE missing startMonth throws with correct message")
        void randomDateRangeMissingStartMonthThrowsWithCorrectMessage() {
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.RANDOM_DATE_RANGE, null, null, false,
                    null, null, 15, 5, 20, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Start month is required for date range recurrence");
        }

        @Test
        @DisplayName("RANDOM_DATE_RANGE missing startDay throws with correct message")
        void randomDateRangeMissingStartDayThrowsWithCorrectMessage() {
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.RANDOM_DATE_RANGE, null, null, false,
                    null, 1, null, 5, 20, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Start day is required for date range recurrence");
        }

        @Test
        @DisplayName("RANDOM_DATE_RANGE missing endMonth throws with correct message")
        void randomDateRangeMissingEndMonthThrowsWithCorrectMessage() {
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.RANDOM_DATE_RANGE, null, null, false,
                    null, 1, 15, null, 20, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("End month is required for date range recurrence");
        }

        @Test
        @DisplayName("RANDOM_DATE_RANGE missing endDay throws with correct message")
        void randomDateRangeMissingEndDayThrowsWithCorrectMessage() {
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.RANDOM_DATE_RANGE, null, null, false,
                    null, 1, 15, 5, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("End day is required for date range recurrence");
        }

        @Test
        @DisplayName("RANDOM_DATE_RANGE with month outside 1-12 throws with correct message")
        void randomDateRangeWithMonthOutsideRangeThrowsWithCorrectMessage() {
            // Invalid start month
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.RANDOM_DATE_RANGE, null, null, false,
                    null, 13, 15, 5, 20, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Month must be between 1 and 12");

            // Invalid end month
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.RANDOM_DATE_RANGE, null, null, false,
                    null, 1, 15, 0, 20, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Month must be between 1 and 12");
        }

        @Test
        @DisplayName("RANDOM_DATE_RANGE with day outside 1-31 throws with correct message")
        void randomDateRangeWithDayOutsideRangeThrowsWithCorrectMessage() {
            // Invalid start day
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.RANDOM_DATE_RANGE, null, null, false,
                    null, 1, 32, 5, 20, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Day must be between 1 and 31");

            // Invalid end day
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.RANDOM_DATE_RANGE, null, null, false,
                    null, 1, 15, 5, 0, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Day must be between 1 and 31");
        }

        @Test
        @DisplayName("Invalid day for month throws with correct message format")
        void invalidDayForMonthThrowsWithCorrectMessageFormat() {
            // April 31 (April has 30 days)
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 31, false,
                    4, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Day 31 does not exist in month 4");

            // June 31 (June has 30 days)
            assertThatThrownBy(() -> new RecurrenceRule(
                    RecurrenceType.YEARLY, null, 31, false,
                    6, null, null, null, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Day 31 does not exist in month 6");
        }
    }
}
