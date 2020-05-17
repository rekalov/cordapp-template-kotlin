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

class TCashTokenFlowTests {
    private val network = MockNetwork(MockNetworkParameters(
            cordappsForAllNodes = TestDefaults.allCordapps,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)))
    private val a = network.createNode()
    private val b = network.createNode()

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(TCashIssueTokenFlow.Responder::class.java)
            it.registerInitiatedFlow(TCashPaymentTokenFlow.Responder::class.java)
            it.registerInitiatedFlow(TCashRedeemTokenFlow.Responder::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun testIssueToCounterpart() {
        val future = a.startFlow(TCashIssueTokenFlow(20.DOLLARS, b.info.singleIdentity()))
        network.runNetwork()
        future.get()
        val tokens = b.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(USD)).states
        assertEquals(20 * (10.0.pow(USD.fractionDigits)).toLong(), tokens.sumByLong { it.state.data.amount.quantity })
    }

    @Test
    fun testIssueToYourself() {
        val future = a.startFlow(TCashIssueTokenFlow(20.DOLLARS, a.info.singleIdentity()))
        network.runNetwork()
        future.get()
        val tokens = a.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(USD)).states
        assertEquals(20 * (10.0.pow(USD.fractionDigits)).toLong(), tokens.sumByLong { it.state.data.amount.quantity })
    }

    @Test
    fun testRedeemTokensIssuedByYourself() {
        val future = a.startFlow(TCashIssueTokenFlow(20.DOLLARS, a.info.singleIdentity()))
        val future2 = a.startFlow(TCashIssueTokenFlow(10.DOLLARS, a.info.singleIdentity()))
        network.runNetwork()
        future.get()
        future2.get()
        val future3 = a.startFlow(TCashRedeemTokenFlow(30.DOLLARS, a.info.singleIdentity()))
        network.runNetwork()
        future3.get()
        val tokens = a.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(USD)).states
        assertEquals(0, tokens.sumByLong { it.state.data.amount.quantity })
    }

    @Test
    fun testRedeemTokensIssuedByCounterpart() {
        val future = b.startFlow(TCashIssueTokenFlow(20.DOLLARS, a.info.singleIdentity()))
        val future2 = b.startFlow(TCashIssueTokenFlow(10.DOLLARS, a.info.singleIdentity()))
        network.runNetwork()
        future.get()
        future2.get()
        val future3 = a.startFlow(TCashRedeemTokenFlow(30.DOLLARS, b.info.singleIdentity()))
        network.runNetwork()
        future3.get()
        val tokens = a.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(USD)).states
        assertEquals(0, tokens.sumByLong { it.state.data.amount.quantity })
        val tokens2 = b.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(USD)).states
        assertEquals(0, tokens2.sumByLong { it.state.data.amount.quantity })
    }

    @Test
    fun testMoveTokensIssuedByYourself() {
        val future = a.startFlow(TCashIssueTokenFlow(20.DOLLARS, a.info.singleIdentity()))
        val future2 = a.startFlow(TCashIssueTokenFlow(10.DOLLARS, a.info.singleIdentity()))
        network.runNetwork()
        future.get()
        future2.get()
        val future3 = a.startFlow(TCashPaymentTokenFlow(30.DOLLARS, b.info.singleIdentity()))
        network.runNetwork()
        future3.get()
        val tokens = a.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(USD)).states
        assertEquals(0, tokens.sumByLong { it.state.data.amount.quantity })
        val tokens2 = b.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(USD)).states
        assertEquals(30 * (10.0.pow(USD.fractionDigits)).toLong(), tokens2.sumByLong { it.state.data.amount.quantity })
    }

    @Test
    fun testMoveTokensIssuedByCounterpart() {
        val future = b.startFlow(TCashIssueTokenFlow(20.DOLLARS, a.info.singleIdentity()))
        val future2 = b.startFlow(TCashIssueTokenFlow(10.DOLLARS, a.info.singleIdentity()))
        network.runNetwork()
        future.get()
        future2.get()
        val future3 = a.startFlow(TCashPaymentTokenFlow(30.DOLLARS, b.info.singleIdentity()))
        network.runNetwork()
        future3.get()
        val tokens = a.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(USD)).states
        assertEquals(0, tokens.sumByLong { it.state.data.amount.quantity })
        val tokens2 = b.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(USD)).states
        assertEquals(30 * (10.0.pow(USD.fractionDigits)).toLong(), tokens2.sumByLong { it.state.data.amount.quantity })
    }
}