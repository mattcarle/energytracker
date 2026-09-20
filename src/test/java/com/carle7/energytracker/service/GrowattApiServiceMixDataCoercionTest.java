package com.carle7.energytracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// Growatt occasionally responds to a mix_data call with "data":"" (an empty string) instead of
// an object or null - observed live, logged as "Cannot coerce empty String ... to MixDataData"
// (an unhandled InvalidFormatException) before GrowattApiService.configureGrowattObjectMapper
// was added. This asserts that fix directly against the real DTO classes, without needing a
// Spring context or a live API call.
class GrowattApiServiceMixDataCoercionTest {

    private final ObjectMapper mapper = GrowattApiService.configureGrowattObjectMapper(new ObjectMapper());

    @Test
    void emptyStringDataFieldIsTreatedAsNoData() {
        String json = "{\"error_msg\":\"\",\"data\":\"\",\"error_code\":0}";

        assertThatCode(() -> mapper.readValue(json, GrowattApiService.MixDataResponse.class)).doesNotThrowAnyException();
    }

    @Test
    void emptyStringDataFieldParsesToNullData() throws Exception {
        String json = "{\"error_msg\":\"\",\"data\":\"\",\"error_code\":0}";

        GrowattApiService.MixDataResponse response = mapper.readValue(json, GrowattApiService.MixDataResponse.class);

        assertThat(response.data).isNull();
    }

    @Test
    void realObjectDataFieldStillParsesNormally() throws Exception {
        String json = "{\"error_msg\":\"\",\"data\":{\"datas\":[],\"count\":0},\"error_code\":0}";

        GrowattApiService.MixDataResponse response = mapper.readValue(json, GrowattApiService.MixDataResponse.class);

        assertThat(response.data).isNotNull();
        assertThat(response.data.count).isEqualTo(0);
    }
}
