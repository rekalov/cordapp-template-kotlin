package net.corda.warrant

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.EUR
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountCriteria
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import org.junit.After
import org.junit.Before
import org.junit.Test

import net.corda.warrant.states.Warrant
import net.corda.core.contracts.Amount
import net.corda.core.contracts.AmountTransfer
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.sumByLong
import net.corda.core.node.services.queryBy
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.warrant.flows.*
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.test.assertEquals

class FlowTests {
    private val network = MockNetwork(MockNetworkParameters(
            cordappsForAllNodes = TestDefaults.allCordapps,
            networkParameters = testNetworkParameters(minimumPlatformVersion = 4)))
    private val ccpNode = network.createNode(legalName = CordaX500Name(
            commonName = "Settler", organisation = "RR3", locality = "London", country = "GB"))
    private val aNode = network.createNode(legalName = CordaX500Name(
            commonName = "Bank A", organisation = "RR3", locality = "London", country = "GB"))
    private val bNode = network.createNode(legalName = CordaX500Name(
            commonName = "Bank B", organisation = "RR3", locality = "London", country = "GB"))
    private val ccpParty = ccpNode.info.singleIdentity()
    private val aParty = aNode.info.singleIdentity()
    private val bParty = bNode.info.singleIdentity()

    init {
        listOf(ccpNode, aNode, bNode).forEach {
            it.registerInitiatedFlow(IssueCashFlow.Responder::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `issuance cash test`() {
        val issuances = listOf(PartyAndAmount(aParty, 50.GBP), PartyAndAmount(bParty, 150.GBP))
        val future = ccpNode.startFlow(IssueCashFlow(issuances))
        network.runNetwork()
        future.get()
        val aGbpTokens = aNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(GBP)).states
        assertEquals(50 * (10.0.pow(GBP.fractionDigits)).toLong(),
                aGbpTokens.sumByLong { it.state.data.amount.quantity })
        val bGbpTokens = bNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(GBP)).states
        assertEquals(150 * (10.0.pow(GBP.fractionDigits)).toLong(),
                bGbpTokens.sumByLong { it.state.data.amount.quantity })
        val ccpGbpTokens = ccpNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(GBP)).states
        assertEquals(0,
                ccpGbpTokens.sumByLong { it.state.data.amount.quantity })
    }

    @Test
    fun `create warrant test`() {
        val future = ccpNode.startFlow(CreateWarrantFlow(
                "Nestle", 2.0.GBP,
                Instant.now().plus(3, ChronoUnit.DAYS)))
        network.runNetwork()
        future.get()
    }

    @Test
    fun `issue warrant test`() {
        val createFuture = ccpNode.startFlow(CreateWarrantFlow(
                "Nestle", 2.0.GBP,
                Instant.now().plus(3, ChronoUnit.DAYS)))
        network.runNetwork()
        val warrant = createFuture.get().coreTransaction.outputs.first().data as Warrant
        val warrantToken = warrant.toPointer<Warrant>()
        val issuances = listOf(
                PartyAndAmount(aParty, Amount.fromDecimal(BigDecimal.valueOf(10L), warrantToken)),
                PartyAndAmount(bParty, Amount.fromDecimal(BigDecimal.valueOf(15L), warrantToken)))
        val issueFuture = ccpNode.startFlow(IssueWarrantFlow(issuances))
        network.runNetwork()
        issueFuture.get()
        val aWarrantTokens = aNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(warrantToken)).states
        assertEquals(10 * (10.0.pow(warrantToken.fractionDigits)).toLong(),
                aWarrantTokens.sumByLong { it.state.data.amount.quantity })
        val bWarrantTokens = bNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(warrantToken)).states
        assertEquals(15 * (10.0.pow(warrantToken.fractionDigits)).toLong(),
                bWarrantTokens.sumByLong { it.state.data.amount.quantity })
        val ccpWarrantTokens = ccpNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(warrantToken)).states
        assertEquals(0,
                ccpWarrantTokens.sumByLong { it.state.data.amount.quantity })
    }

    @Test
    fun `purchase warrant test`() {
        val createFuture = ccpNode.startFlow(CreateWarrantFlow(
                "Nestle", 2.0.GBP,
                Instant.now().plus(3, ChronoUnit.DAYS)))
        network.runNetwork()
        val warrant = createFuture.get().coreTransaction.outputs.first().data as Warrant

        val warrantToken = warrant.toPointer<Warrant>()
        val warrantIssuances = listOf(
                PartyAndAmount(aParty, Amount.fromDecimal(BigDecimal.valueOf(10L), warrantToken)),
                PartyAndAmount(bParty, Amount.fromDecimal(BigDecimal.valueOf(15L), warrantToken)))
        val warrantIssueFuture = ccpNode.startFlow(IssueWarrantFlow(warrantIssuances))
        network.runNetwork()
        warrantIssueFuture.get()

        val cashIssuances = listOf(PartyAndAmount(aParty, 50.GBP), PartyAndAmount(bParty, 150.GBP))
        val cashIssueFuture = ccpNode.startFlow(IssueCashFlow(cashIssuances))
        network.runNetwork()
        cashIssueFuture.get()

        val basket = listOf(
                AmountTransfer(2L, warrantToken, aParty, bParty),
                AmountTransfer(4L, warrantToken, bParty, aParty)
        )
        val purchaseFuture = ccpNode.startFlow(PurchaseWarrantFlow(basket))
        network.runNetwork()
        purchaseFuture.get()

        val aWarrantTokens = aNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(warrantToken)).states
        assertEquals(12 * (10.0.pow(warrantToken.fractionDigits)).toLong(),
                aWarrantTokens.sumByLong { it.state.data.amount.quantity })
        val bWarrantTokens = bNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(warrantToken)).states
        assertEquals(13 * (10.0.pow(warrantToken.fractionDigits)).toLong(),
                bWarrantTokens.sumByLong { it.state.data.amount.quantity })
        val ccpWarrantTokens = ccpNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(warrantToken)).states
        assertEquals(0,
                ccpWarrantTokens.sumByLong { it.state.data.amount.quantity })

        val aGbpTokens = aNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(GBP)).states
        assertEquals(46 * (10.0.pow(GBP.fractionDigits)).toLong(),
                aGbpTokens.sumByLong { it.state.data.amount.quantity })
        val bGbpTokens = bNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(GBP)).states
        assertEquals(154 * (10.0.pow(GBP.fractionDigits)).toLong(),
                bGbpTokens.sumByLong { it.state.data.amount.quantity })
        val ccpGbpTokens = ccpNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(GBP)).states
        assertEquals(0,
                ccpGbpTokens.sumByLong { it.state.data.amount.quantity })
    }

    @Test
    fun `settlement test`() {
        val cashIssuances = listOf(
                PartyAndAmount(aParty, 50.GBP), PartyAndAmount(aParty, 150.EUR),
                PartyAndAmount(bParty, 50.EUR), PartyAndAmount(bParty, 150.GBP))
        val cashIssueFuture = ccpNode.startFlow(IssueCashFlow(cashIssuances))
        network.runNetwork()
        cashIssueFuture.get()

        val basket = listOf(
                AmountTransfer(10 * (10.0.pow(GBP.fractionDigits)).toLong(), GBP, aParty, bParty),
                AmountTransfer(20 * (10.0.pow(EUR.fractionDigits)).toLong(), EUR, bParty, aParty)
        )
        val purchaseFuture = ccpNode.startFlow(SettlementFlow(basket))
        network.runNetwork()
        purchaseFuture.get()

        val aWarrantTokens = aNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(EUR)).states
        assertEquals(170 * (10.0.pow(EUR.fractionDigits)).toLong(),
                aWarrantTokens.sumByLong { it.state.data.amount.quantity })
        val bWarrantTokens = bNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(EUR)).states
        assertEquals(30 * (10.0.pow(EUR.fractionDigits)).toLong(),
                bWarrantTokens.sumByLong { it.state.data.amount.quantity })
        val ccpWarrantTokens = ccpNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(EUR)).states
        assertEquals(0,
                ccpWarrantTokens.sumByLong { it.state.data.amount.quantity })

        val aGbpTokens = aNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(GBP)).states
        assertEquals(40 * (10.0.pow(GBP.fractionDigits)).toLong(),
                aGbpTokens.sumByLong { it.state.data.amount.quantity })
        val bGbpTokens = bNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(GBP)).states
        assertEquals(160 * (10.0.pow(GBP.fractionDigits)).toLong(),
                bGbpTokens.sumByLong { it.state.data.amount.quantity })
        val ccpGbpTokens = ccpNode.services.vaultService.queryBy<FungibleToken>(tokenAmountCriteria(GBP)).states
        assertEquals(0,
                ccpGbpTokens.sumByLong { it.state.data.amount.quantity })
    }
}