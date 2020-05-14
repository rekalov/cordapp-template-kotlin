package com.test

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountCriteria
import net.corda.core.internal.sumByLong
import net.corda.core.node.services.queryBy
import net.corda.finance.DOLLARS
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.warrant.TestDefaults
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.math.pow
import kotlin.test.assertEquals

class TCashIssueFlowTests {
    private val network = MockNetwork(MockNetworkParameters(
            cordappsForAllNodes = TestDefaults.allCordapps,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)))
    private val a = network.createNode()
    private val b = network.createNode()

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(TCashIssueFlow.Responder::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun testIssueToCounterpart() {
        val future = a.startFlow(TCashIssueFlow(20.DOLLARS, b.info.singleIdentity()))
        network.runNetwork()
        future.get()
        val tokens = b.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(USD)).states
        assertEquals(20 * (10.0.pow(USD.fractionDigits)).toLong(), tokens.sumByLong { it.state.data.amount.quantity })
    }

    @Test
    fun testIssueToYourself() {
        val future = a.startFlow(TCashIssueFlow(20.DOLLARS, a.info.singleIdentity()))
        network.runNetwork()
        future.get()
        val tokens = a.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(USD)).states
        assertEquals(20 * (10.0.pow(USD.fractionDigits)).toLong(), tokens.sumByLong { it.state.data.amount.quantity })
    }
}