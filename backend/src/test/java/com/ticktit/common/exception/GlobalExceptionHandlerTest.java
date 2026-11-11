package com.ticktit.common.exception;

import com.ticktit.common.enums.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    record Body(@Email String email, @Size(min = 8) String password) {}

    @RestController
    static class TestController {
        @GetMapping("/api-error") void apiError() { throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS); }
        @GetMapping("/boom") void boom() { throw new IllegalStateException("db password=secret"); }
        @PostMapping("/valid") void valid(@Valid @RequestBody Body body) { }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void apiExceptionReturnsItsCode() throws Exception {
        mvc.perform(get("/api-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.detail").value(ErrorCode.EMAIL_ALREADY_EXISTS.getMessage()));
    }

    @Test
    void unexpectedErrorHidesInternals() throws Exception {
        mvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.detail").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

    @Test
    void validationReturnsErrorsPerField() throws Exception {
        mvc.perform(post("/valid").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }
}
