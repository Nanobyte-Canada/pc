package com.portfolio.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(VersionController::class)
class VersionControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `version endpoint returns version info`() {
        mockMvc.perform(get("/api/v1/version"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").exists())
            .andExpect(jsonPath("$.environment").exists())
    }

    @Test
    fun `version endpoint returns valid JSON structure`() {
        mockMvc.perform(get("/api/v1/version"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.version").isString)
            .andExpect(jsonPath("$.environment").isString)
    }
}
