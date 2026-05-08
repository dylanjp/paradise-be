package com.dylanjohnpratt.paradise.be.health.model.converter;

import com.dylanjohnpratt.paradise.be.health.model.Dataset;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trip tests for the three Jackson-backed {@code AttributeConverter}
 * implementations used on {@code HealthMetric}.
 */
class JsonListConvertersTest {

    // --- StringListJsonConverter ---

    @Test
    void stringList_roundTrip_preservesOrderAndValues() {
        StringListJsonConverter conv = new StringListJsonConverter();
        List<String> input = List.of("a", "b", "c with, comma", "");
        String db = conv.convertToDatabaseColumn(input);
        assertThat(db).isNotBlank();
        List<String> out = conv.convertToEntityAttribute(db);
        assertThat(out).containsExactlyElementsOf(input);
    }

    @Test
    void stringList_nullAttribute_returnsNullDb() {
        StringListJsonConverter conv = new StringListJsonConverter();
        assertThat(conv.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void stringList_emptyList_roundTrips() {
        StringListJsonConverter conv = new StringListJsonConverter();
        String db = conv.convertToDatabaseColumn(List.of());
        assertThat(conv.convertToEntityAttribute(db)).isEmpty();
    }

    @Test
    void stringList_blankDb_returnsNull() {
        StringListJsonConverter conv = new StringListJsonConverter();
        assertThat(conv.convertToEntityAttribute(null)).isNull();
        assertThat(conv.convertToEntityAttribute("")).isNull();
        assertThat(conv.convertToEntityAttribute("   ")).isNull();
    }

    @Test
    void stringList_malformedDb_throws() {
        StringListJsonConverter conv = new StringListJsonConverter();
        assertThatThrownBy(() -> conv.convertToEntityAttribute("{not json"))
                .isInstanceOf(RuntimeException.class);
    }

    // --- BigDecimalListJsonConverter ---

    @Test
    void bigDecimalList_roundTrip_preservesScale() {
        BigDecimalListJsonConverter conv = new BigDecimalListJsonConverter();
        List<BigDecimal> input = List.of(
                new BigDecimal("120.50"),
                new BigDecimal("99"),
                new BigDecimal("0.001")
        );
        String db = conv.convertToDatabaseColumn(input);
        List<BigDecimal> out = conv.convertToEntityAttribute(db);
        assertThat(out).hasSize(3);
        // Values compare-equal even if Jackson strips trailing zeros
        assertThat(out.get(0)).isEqualByComparingTo(new BigDecimal("120.50"));
        assertThat(out.get(1)).isEqualByComparingTo(new BigDecimal("99"));
        assertThat(out.get(2)).isEqualByComparingTo(new BigDecimal("0.001"));
    }

    @Test
    void bigDecimalList_null_returnsNull() {
        BigDecimalListJsonConverter conv = new BigDecimalListJsonConverter();
        assertThat(conv.convertToDatabaseColumn(null)).isNull();
        assertThat(conv.convertToEntityAttribute(null)).isNull();
    }

    // --- DatasetListJsonConverter ---

    @Test
    void datasetList_roundTrip_preservesLabelsAndData() {
        DatasetListJsonConverter conv = new DatasetListJsonConverter();
        List<Dataset> input = List.of(
                new Dataset("Systolic", List.of(new BigDecimal("120"), new BigDecimal("125"))),
                new Dataset("Diastolic", List.of(new BigDecimal("80"), new BigDecimal("82")))
        );
        String db = conv.convertToDatabaseColumn(input);
        List<Dataset> out = conv.convertToEntityAttribute(db);
        assertThat(out).hasSize(2);
        assertThat(out.get(0).label()).isEqualTo("Systolic");
        assertThat(out.get(0).data()).hasSize(2);
        assertThat(out.get(0).data().get(0)).isEqualByComparingTo("120");
        assertThat(out.get(1).label()).isEqualTo("Diastolic");
    }

    @Test
    void datasetList_emptyList_roundTrips() {
        DatasetListJsonConverter conv = new DatasetListJsonConverter();
        String db = conv.convertToDatabaseColumn(List.of());
        assertThat(conv.convertToEntityAttribute(db)).isEmpty();
    }

    @Test
    void datasetList_null_returnsNull() {
        DatasetListJsonConverter conv = new DatasetListJsonConverter();
        assertThat(conv.convertToDatabaseColumn(null)).isNull();
        assertThat(conv.convertToEntityAttribute(null)).isNull();
        assertThat(conv.convertToEntityAttribute("  ")).isNull();
    }

    @Test
    void datasetList_malformed_throws() {
        DatasetListJsonConverter conv = new DatasetListJsonConverter();
        assertThatThrownBy(() -> conv.convertToEntityAttribute("]["))
                .isInstanceOf(RuntimeException.class);
    }
}
