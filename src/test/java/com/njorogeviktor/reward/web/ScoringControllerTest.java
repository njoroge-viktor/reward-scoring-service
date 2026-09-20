package com.njorogeviktor.reward.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.njorogeviktor.reward.scoring.ScoringService;

@WebMvcTest(ScoringController.class)
@Import(ScoringService.class)
class ScoringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String VALID_RUBRIC =
            """
            {"name":"code-quality","criteria":[
              {"name":"correctness","weight":3.0,"min":1,"max":5},
              {"name":"clarity","weight":1.0,"min":1,"max":5}]}""";

    @Test
    void scoresAValidRequest() throws Exception {
        String body =
                """
                {"itemId":"item-1","rubric":%s,"scores":{"correctness":4.0,"clarity":2.0}}"""
                        .formatted(VALID_RUBRIC);

        mockMvc.perform(post("/v1/scores").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value("item-1"))
                .andExpect(jsonPath("$.normalized").value(0.625));
    }

    @Test
    void aScoringFailureIsUnprocessableWithDetails() throws Exception {
        String body =
                """
                {"itemId":"item-1","rubric":%s,"scores":{"correctness":9.0,"clarity":2.0}}"""
                        .formatted(VALID_RUBRIC);

        mockMvc.perform(post("/v1/scores").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("scoring_failed"))
                .andExpect(jsonPath("$.details[0]").value(org.hamcrest.Matchers.containsString("outside [1, 5]")));
    }

    @Test
    void aMissingItemIdFailsValidation() throws Exception {
        String body =
                """
                {"rubric":%s,"scores":{"correctness":4.0,"clarity":2.0}}""".formatted(VALID_RUBRIC);

        mockMvc.perform(post("/v1/scores").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void anInvalidRubricSurfacesTheRealReason() throws Exception {
        // max <= min trips the record's compact constructor; the caller must
        // see "max must exceed min", not a generic deserialisation error.
        String body =
                """
                {"itemId":"i","rubric":{"name":"bad","criteria":[
                  {"name":"x","weight":1.0,"min":5,"max":1}]},"scores":{"x":3.0}}""";

        mockMvc.perform(post("/v1/scores").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("malformed_request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("must exceed")));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/v1/scores").contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("malformed_request"));
    }

    @Test
    void scoresABatchPreservingOrder() throws Exception {
        String body =
                """
                [{"itemId":"a","rubric":%s,"scores":{"correctness":5.0,"clarity":5.0}},
                 {"itemId":"b","rubric":%s,"scores":{"correctness":1.0,"clarity":1.0}}]"""
                        .formatted(VALID_RUBRIC, VALID_RUBRIC);

        mockMvc.perform(post("/v1/scores/batch").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].itemId").value("a"))
                .andExpect(jsonPath("$[0].normalized").value(1.0))
                .andExpect(jsonPath("$[1].itemId").value("b"))
                .andExpect(jsonPath("$[1].normalized").value(0.0));
    }

    @Test
    void anOversizedBatchIsRejected() throws Exception {
        StringBuilder body = new StringBuilder("[");
        for (int i = 0; i < 1001; i++) {
            if (i > 0) {
                body.append(",");
            }
            body.append(
                    """
                    {"itemId":"i%d","rubric":%s,"scores":{"correctness":3.0,"clarity":3.0}}"""
                            .formatted(i, VALID_RUBRIC));
        }
        body.append("]");

        mockMvc.perform(post("/v1/scores/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("batch_too_large"));
    }
}
