package com.portfolio.ingestion.tracking

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.ingestion.persistence.entity.*
import com.portfolio.ingestion.persistence.repository.IngestionErrorRepository
import com.portfolio.ingestion.persistence.repository.IngestionRunRepository
import com.portfolio.ingestion.persistence.repository.IngestionStepRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class IngestionTrackingService(
    private val runRepo: IngestionRunRepository,
    private val stepRepo: IngestionStepRepository,
    private val errorRepo: IngestionErrorRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun startRun(runType: RunType, triggerSource: String): IngestionRun {
        val run = IngestionRun(runType = runType, triggerSource = triggerSource)
        return runRepo.save(run)
    }

    fun completeRun(run: IngestionRun, status: RunStatus) {
        run.status = status
        run.completedAt = OffsetDateTime.now()
        runRepo.save(run)
    }

    fun startStep(run: IngestionRun, stepName: StepName): IngestionStep {
        val step = IngestionStep(run = run, stepName = stepName)
        return stepRepo.save(step)
    }

    fun completeStep(step: IngestionStep, status: StepStatus, processed: Int, created: Int, updated: Int, failed: Int, metadata: Map<String, Any>? = null) {
        step.status = status
        step.completedAt = OffsetDateTime.now()
        step.recordsProcessed = processed
        step.recordsCreated = created
        step.recordsUpdated = updated
        step.recordsFailed = failed
        step.metadata = metadata?.let { objectMapper.valueToTree(it) }
        stepRepo.save(step)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logError(step: IngestionStep, errorType: ErrorType, errorCode: String? = null, errorMessage: String? = null, context: Map<String, Any>? = null) {
        val contextNode: JsonNode? = context?.let { objectMapper.valueToTree(it) }
        val error = IngestionError(
            step = step,
            errorType = errorType,
            errorCode = errorCode,
            errorMessage = errorMessage,
            context = contextNode
        )
        errorRepo.save(error)
        log.warn("Ingestion error [{}] {}: {}", errorType, errorCode ?: "", errorMessage ?: "")
    }
}
