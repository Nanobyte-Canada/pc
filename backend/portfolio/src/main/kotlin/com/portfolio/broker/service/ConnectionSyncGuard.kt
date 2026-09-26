package com.portfolio.broker.service

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/** In-process single-flight guard per connection. Single-replica deployment;
 *  multi-replica would require ShedLock (out of scope). */
@Component
class ConnectionSyncGuard {
    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    fun tryAcquire(connectionId: Long): Boolean {
        val lock = locks.computeIfAbsent(connectionId) { ReentrantLock() }
        // tryLock() alone would succeed for the thread that already holds the lock
        // (ReentrantLock is reentrant); single-flight means exactly one holder no matter
        // which thread asks, so re-entry is rejected explicitly.
        if (lock.isHeldByCurrentThread) return false
        return lock.tryLock()
    }

    fun release(connectionId: Long) {
        locks[connectionId]?.let { if (it.isHeldByCurrentThread) it.unlock() }
    }
}
