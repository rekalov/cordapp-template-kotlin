package net.corda.warrant

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import com.r3.corda.lib.tokens.workflows.utilities.tokenAmountCriteria
import net.corda.warrant.flows.CreateWarrantFlow
import net.corda.warrant.flows.IssueCashFlow
import net.corda.warrant.flows.IssueWarrantFlow
import net.corda.warrant.states.Warrant
import net.corda.core.contracts.Amount
import net.corda.core.contracts.AmountTransfer
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.sumByLong
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.getOrThrow
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverDSL
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.warrant.flows.PurchaseWarrantFlow
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Future
import kotlin.math.pow
import kotlin.test.assertEquals

class DriverBasedTest {
    private val ccp = TestIdentity(CordaX500Name("CCP", "", "GB"))
    private val bankA = TestIdentity(CordaX500Name("BankA", "", "GB"))
    private val bankB = TestIdentity(CordaX500Name("BankB", "", "US"))

    @Test
    fun `issuance test`() = withDriver {
        // Start a pair of nodes and wait for them both to be ready.
        val (ccpHandle, partyAHandle, partyBHandle) = startNodes(ccp, bankA, bankB)

        val issuances = listOf(
                PartyAndAmount(partyAHandle.nodeInfo.singleIdentity(), 50.GBP),
                PartyAndAmount(partyBHandle.nodeInfo.singleIdentity(), 150.GBP))
        ccpHandle.rpc.startFlow({ IssueCashFlow(it) }, issuances).returnValue.get()
    }

    @Test
    fun `purchase test`() = withDriver {
        // Start a pair of nodes and wait for them both to be ready.
        val (ccpHandle, partyAHandle, partyBHandle) = startNodes(ccp, bankA, bankB)

        val aParty = partyAHandle.nodeInfo.singleIdentity()
        val bParty = partyBHandle.nodeInfo.singleIdentity()

        val (createFuture, createProgress) = ccpHandle.rpc.startTrackedFlow(::CreateWarrantFlow,
                "Nestle", 2.0.GBP,
                Instant.now().plus(3, ChronoUnit.DAYS))
                .let { Pair(it.returnValue, it.progress) }
        createProgress.subscribe { println("PROGRESS (${CreateWarrantFlow::class.simpleName}) $it")}
        val warrant = createFuture.get().coreTransaction.outputs.first().data as Warrant

        val warrantToken = warrant.toPointer<Warrant>()
        val warrantIssuances = listOf(
                PartyAndAmount(aParty, Amount.fromDecimal(BigDecimal.valueOf(10L), warrantToken)),
                PartyAndAmount(bParty, Amount.fromDecimal(BigDecimal.valueOf(15L), warrantToken)))
        val (warrantIssueFuture, warrantIssueProgress) = ccpHandle.rpc.startTrackedFlow(::IssueWarrantFlow, warrantIssuances)
                .let { Pair(it.returnValue, it.progress)}
        warrantIssueProgress.subscribe { println("PROGRESS (${IssueWarrantFlow::class.simpleName} $it")}
        warrantIssueFuture.get()

        val cashIssuances = listOf(PartyAndAmount(aParty, 50.GBP), PartyAndAmount(bParty, 150.GBP))
        val (cashIssueFuture, cashIssueProgress) = ccpHandle.rpc.startTrackedFlow(::IssueCashFlow, cashIssuances)
                .let { Pair(it.returnValue, it.progress) }
        cashIssueProgress.subscribe { println("PROGRESS (${IssueCashFlow::class.simpleName}) $it")}
        cashIssueFuture.get()

        val basket = listOf(
                AmountTransfer(2L, warrantToken, aParty, bParty),
                AmountTransfer(4L, warrantToken, bParty, aParty)
        )
        val (purchaseFuture, purchaseProgress) = ccpHandle.rpc.startTrackedFlow(::PurchaseWarrantFlow, basket)
                .let { Pair(it.returnValue, it.progress) }
        purchaseProgress.subscribe { println("PROGRESS (${PurchaseWarrantFlow::class.simpleName}) $it") }
        purchaseFuture.get()

        val aWarrantTokens = partyAHandle.rpc.vaultQueryByCriteria(
                tokenAmountCriteria(warrantToken), FungibleToken::class.java).states
        assertEquals(12 * (10.0.pow(warrantToken.fractionDigits)).toLong(),
                aWarrantTokens.sumByLong { it.state.data.amount.quantity })
        val bWarrantTokens = partyBHandle.rpc.vaultQueryByCriteria(
                tokenAmountCriteria(warrantToken), FungibleToken::class.java).states
        assertEquals(13 * (10.0.pow(warrantToken.fractionDigits)).toLong(),
                bWarrantTokens.sumByLong { it.state.data.amount.quantity })
        val ccpWarrantTokens = ccpHandle.rpc.vaultQueryByCriteria(
                tokenAmountCriteria(warrantToken), FungibleToken::class.java).states
        assertEquals(0,
                ccpWarrantTokens.sumByLong { it.state.data.amount.quantity })

        val aGbpTokens = partyAHandle.rpc.vaultQueryByCriteria(
                tokenAmountCriteria(GBP), FungibleToken::class.java).states
        assertEquals(46 * (10.0.pow(GBP.fractionDigits)).toLong(),
                aGbpTokens.sumByLong { it.state.data.amount.quantity })
        val bGbpTokens = partyBHandle.rpc.vaultQueryByCriteria(
                tokenAmountCriteria(GBP), FungibleToken::class.java).states
        assertEquals(154 * (10.0.pow(GBP.fractionDigits)).toLong(),
                bGbpTokens.sumByLong { it.state.data.amount.quantity })
        val ccpGbpTokens = ccpHandle.rpc.vaultQueryByCriteria(
                tokenAmountCriteria(GBP), FungibleToken::class.java).states
        assertEquals(0,
                ccpGbpTokens.sumByLong { it.state.data.amount.quantity })
    }

    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
        DriverParameters(
                isDebug = true,
                startNodesInProcess = true,
                cordappsForAllNodes = TestDefaults.allCordapps,
                networkParameters = testNetworkParameters(minimumPlatformVersion = 4)
    )) { test() }

    // Makes an RPC call to retrieve another node's name from the network map.
    private fun NodeHandle.resolveName(name: CordaX500Name) = rpc.wellKnownPartyFromX500Name(name)!!.name

    // Resolves a list of futures to a list of the promised values.
    private fun <T> List<Future<T>>.waitForAll(): List<T> = map { it.getOrThrow() }

    // Starts multiple nodes simultaneously, then waits for them all to be ready.
    private fun DriverDSL.startNodes(vararg identities: TestIdentity) = identities
        .map { startNode(providedName = it.name) }
        .waitForAll()
}