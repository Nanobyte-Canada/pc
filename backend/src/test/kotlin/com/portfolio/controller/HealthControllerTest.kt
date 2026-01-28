package com.portfolio.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(HealthController::class)
class HealthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `health endpoint returns UP status`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.timestamp").exists())
    }

    @Test
    fun `health endpoint returns valid JSON`() {
        mockMvc.perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").isString)
            .andExpect(jsonPath("$.timestamp").isString)
    }
}
