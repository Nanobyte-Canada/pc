package com.portfolio.marketdata.ibkr

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class SubscriptionManager(
    private val ibkrClient: IbkrClient,
    @Value("\${ibkr.max-subscriptions:100}") private val maxSubscriptions: Int
) {

    private val logger = LoggerFactory.getLogger(SubscriptionManager::class.java)
    private val activeSubscriptions = LinkedHashMap<Int, Subscription>(16, 0.75f, true)
    private val subscriptionLock = Any()
    private val pinnedConIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    fun subscribe(conId: Int, callback: (tickType: Int, value: Double) -> Unit) {
        synchronized(subscriptionLock) {
            if (activeSubscriptions.containsKey(conId)) {
                activeSubscriptions[conId] = Subscription(conId, callback, pinnedConIds.contains(conId))
                return
            }
            if (activeSubscriptions.size >= maxSubscriptions) evictLRU()
            activeSubscriptions[conId] = Subscription(conId, callback, pinnedConIds.contains(conId))
            try {
                ibkrClient.requestMarketData(conId, callback)
            } catch (e: Exception) {
                logger.error("Failed to request market data for conId={}", conId, e)
                activeSubscriptions.remove(conId)
                throw e
            }
        }
    }

    fun unsubscribe(conId: Int) {
        synchronized(subscriptionLock) {
            val subscription = activeSubscriptions.remove(conId)
            if (subscription != null) {
                try { ibkrClient.cancelMarketData(conId) } catch (e: Exception) {
                    logger.error("Failed to cancel market data for conId={}", conId, e)
                }
            }
        }
    }

    fun pin(conId: Int) {
        pinnedConIds.add(conId)
        synchronized(subscriptionLock) {
            activeSubscriptions[conId]?.let { activeSubscriptions[conId] = it.copy(pinned = true) }
        }
    }

    fun unpin(conId: Int) {
        pinnedConIds.remove(conId)
        synchronized(subscriptionLock) {
            activeSubscriptions[conId]?.let { activeSubscriptions[conId] = it.copy(pinned = false) }
        }
    }

    fun getActiveCount(): Int = activeSubscriptions.size
    fun getPinnedCount(): Int = pinnedConIds.size
    fun isSubscribed(conId: Int): Boolean = activeSubscriptions.containsKey(conId)

    private fun evictLRU() {
        val toEvict = activeSubscriptions.entries.firstOrNull { !it.value.pinned }
        if (toEvict != null) {
            logger.info("SubscriptionManager: Evicting LRU conId={}", toEvict.key)
            unsubscribe(toEvict.key)
        }
    }

    fun unsubscribeAll() {
        synchronized(subscriptionLock) {
            activeSubscriptions.keys.toList().forEach { conId ->
                try { ibkrClient.cancelMarketData(conId) } catch (e: Exception) {
                    logger.error("Error canceling subscription for conId={}", conId, e)
                }
            }
            activeSubscriptions.clear()
        }
    }

    private data class Subscription(val conId: Int, val callback: (tickType: Int, value: Double) -> Unit, val pinned: Boolean = false)
}
