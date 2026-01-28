package com.portfolio.ingestion.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.ingestion.entity.*
import com.portfolio.ingestion.repository.IngestionErrorRepository
import com.portfolio.ingestion.repository.IngestionRunRepository
import com.portfolio.ingestion.repository.IngestionStepRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

data class StepResult(
    val processed: Int = 0,
    val created: Int = 0,
    val updated: Int = 0,
    val failed: Int = 0,
    val metadata: Map<String, Any>? = null
)

/**
 * Result of processing a single batch with REQUIRES_NEW transaction.
 * Used by batch processors to return counts for aggregation.
 */
data class BatchResult(
    val updated: Int = 0,
    val failed: Int = 0,
    val processed: Int = 0
)

@Service
class IngestionTrackingService(
    private val runRepository: IngestionRunRepository,
    private val stepRepository: IngestionStepRepository,
    private val errorRepository: IngestionErrorRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun startRun(runType: RunType, triggerSource: String): IngestionRun {
        log.info("Starting ingestion run: type=$runType, source=$triggerSource")

        val run = IngestionRun(
            runType = runType,
            triggerSource = triggerSource
        )
        return runRepository.save(run)
    }

    @Transactional
    fun startStep(run: IngestionRun, stepName: StepName): IngestionStep {
        log.info("Starting step: ${stepName.name} for run ${run.id}")

        val step = IngestionStep(
            run = run,
            stepName = stepName
        )
        run.steps.add(step)
        return stepRepository.save(step)
    }

    @Transactional
    fun completeStep(step: IngestionStep, result: StepResult, status: StepStatus = StepStatus.COMPLETED) {
        log.info("Completing step ${step.stepName}: processed=${result.processed}, created=${result.created}, updated=${result.updated}, failed=${result.failed}")

        step.updateCounts(result.processed, result.created, result.updated, result.failed)
        step.metadata = result.metadata?.let { objectMapper.writeValueAsString(it) }
        step.complete(status)
        stepRepository.save(step)
    }

    @Transactional
    fun failStep(step: IngestionStep, error: Exception) {
        log.error("Step ${step.stepName} failed: ${error.message}")

        step.complete(StepStatus.FAILED)
        stepRepository.save(step)

        logError(step.id, ErrorType.DB_ERROR, error.message, mapOf("exception" to error.javaClass.simpleName))
    }

    @Transactional
    fun completeRun(run: IngestionRun, status: RunStatus) {
        log.info("Completing run ${run.id} with status $status")

        run.complete(status)
        runRepository.save(run)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logError(
        stepId: Long,
        errorType: ErrorType,
        message: String?,
        context: Map<String, Any>? = null,
        errorCode: String? = null
    ): IngestionError {
        val step = stepRepository.findById(stepId).orElseThrow {
            IllegalArgumentException("IngestionStep not found with id: $stepId")
        }
        log.warn("Logging error for step ${step.stepName}: type=$errorType, message=$message")

        val error = IngestionError(
            step = step,
            errorType = errorType,
            errorCode = errorCode,
            errorMessage = message,
            context = context?.let { objectMapper.writeValueAsString(it) }
        )
        step.errors.add(error)
        step.recordsFailed++
        stepRepository.save(step)
        return errorRepository.save(error)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logApiError(stepId: Long, identifier: String, statusCode: Int?, responseBody: String?) {
        logError(
            stepId = stepId,
            errorType = ErrorType.API_ERROR,
            message = "API call failed for $identifier",
            context = mapOf(
                "identifier" to identifier,
                "statusCode" to (statusCode ?: "unknown"),
                "response" to (responseBody?.take(500) ?: "")
            ),
            errorCode = statusCode?.toString()
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logParseError(stepId: Long, identifier: String, error: Exception) {
        logError(
            stepId = stepId,
            errorType = ErrorType.PARSE_ERROR,
            message = "Failed to parse data for $identifier: ${error.message}",
            context = mapOf(
                "identifier" to identifier,
                "exception" to error.javaClass.simpleName
            )
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logAmbiguousMatch(stepId: Long, identifier: String, matchCount: Int) {
        logError(
            stepId = stepId,
            errorType = ErrorType.AMBIGUOUS_MATCH,
            message = "Found $matchCount matches for $identifier",
            context = mapOf(
                "identifier" to identifier,
                "matchCount" to matchCount
            )
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logNotFound(stepId: Long, identifier: String) {
        logError(
            stepId = stepId,
            errorType = ErrorType.NOT_FOUND,
            message = "No match found for $identifier",
            context = mapOf("identifier" to identifier)
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logDuplicateIsin(
        stepId: Long,
        newIdentifier: String,
        existingIdentifier: String,
        isin: String
    ) {
        logError(
            stepId = stepId,
            errorType = ErrorType.DUPLICATE_ISIN,
            message = "ISIN $isin already exists on $existingIdentifier, cannot assign to $newIdentifier",
            context = mapOf(
                "newIdentifier" to newIdentifier,
                "existingIdentifier" to existingIdentifier,
                "isin" to isin
            )
        )
    }

    fun getRunWithDetails(runId: Long): IngestionRun? {
        return runRepository.findByIdWithStepsAndErrors(runId)
    }

    fun getRecentRuns(limit: Int): List<IngestionRun> {
        return runRepository.findRecentRuns(PageRequest.of(0, limit))
    }
}
