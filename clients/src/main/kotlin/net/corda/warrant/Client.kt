package net.corda.warrant

import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.money.GBP
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.warrant.flows.CreateWarrantFlow
import net.corda.warrant.flows.IssueCashFlow
import net.corda.warrant.flows.IssueWarrantFlow
import net.corda.warrant.states.Warrant
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.contracts.AmountTransfer
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startTrackedFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.warrant.flows.PurchaseWarrantFlow
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

fun main(args: Array<String>) {
    val command = args.elementAtOrElse(0) { "0" }
    val proxy = CordaRPCClient(NetworkHostAndPort.parse("localhost:10006"))
            .start("test", "test").proxy
    val aParty = proxy.nodeInfo().legalIdentities.first()
    val bParty = proxy.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Denis Test Node 2, L=London, C=GB"))!!
    println("Connected to node: [$aParty], Counterpart: [$bParty]")
    val num = 1
    val warrantTokens = proxy.vaultQuery(Warrant::class.java).states.map { it.state.data.toPointer<Warrant>() }
            .takeIf { it.size >= num } ?: MutableList(num) { warrantToken(proxy) }

    if (command != "2") {
        issue(proxy, aParty, bParty, warrantTokens)
    }
    if (command != "1") {
        transfer(proxy, aParty, bParty, warrantTokens)
    }
}

fun warrantToken(proxy: CordaRPCOps): TokenPointer<Warrant> {
    val (createFuture, createProgress) = proxy.startTrackedFlow(::CreateWarrantFlow,
            UUID.randomUUID().toString(), 2.0.GBP,
            Instant.now().plus(3, ChronoUnit.DAYS))
            .let { Pair(it.returnValue, it.progress) }
    createProgress.subscribe { println("PROGRESS (${CreateWarrantFlow::class.simpleName}) $it") }
    val warrant = createFuture.get().coreTransaction.outputs.first().data as Warrant
    println("Created warrant: $warrant")
    return warrant.toPointer()
}

fun issue(proxy: CordaRPCOps, aParty: Party, bParty: Party, warrantTokens: List<TokenPointer<Warrant>>) {
    val warrantIssuances = warrantTokens.flatMap {
        listOf(
                PartyAndAmount(aParty, Amount.fromDecimal(BigDecimal.valueOf(10L), it)),
                PartyAndAmount(bParty, Amount.fromDecimal(BigDecimal.valueOf(15L), it))
        )
    }
    val (warrantIssueFuture, warrantIssueProgress) = proxy.startTrackedFlow(::IssueWarrantFlow, warrantIssuances)
            .let { Pair(it.returnValue, it.progress) }
    warrantIssueProgress.subscribe { println("PROGRESS (${IssueWarrantFlow::class.simpleName}) $it") }
    warrantIssueFuture.get()

    val cashIssuances = listOf(listOf(PartyAndAmount(aParty, 50.GBP), PartyAndAmount(bParty, 150.GBP))).flatten()
    val (cashIssueFuture, cashIssueProgress) = proxy.startTrackedFlow(::IssueCashFlow, cashIssuances)
            .let { Pair(it.returnValue, it.progress) }
    cashIssueProgress.subscribe { println("PROGRESS (${IssueCashFlow::class.simpleName}) $it") }
    cashIssueFuture.get()

}

fun transfer(proxy: CordaRPCOps, aParty: Party, bParty: Party, warrantTokens: List<TokenPointer<Warrant>>) {
    val basket = warrantTokens.flatMap {
        listOf(
                AmountTransfer(2L, it, aParty, bParty),
                AmountTransfer(4L, it, bParty, aParty)
        )
    }
    val (purchaseFuture, purchaseProgress) = proxy.startTrackedFlow(::PurchaseWarrantFlow, basket)
            .let { Pair(it.returnValue, it.progress) }
    purchaseProgress.subscribe { println("PROGRESS (${PurchaseWarrantFlow::class.simpleName}) $it") }
    purchaseFuture.get()
}