package com.test

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.contracts.withoutIssuer
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashExitFlow
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import org.junit.Test
import java.util.*

class FinanceTest {
    private val amount = Amount.parseCurrency("1000 RUB")
    private val issuerRef = OpaqueBytes("ref".toByteArray())

    private fun connect(address: String) = CordaRPCClient(NetworkHostAndPort.parse(address)).start("test", "test")
            .proxy.also { println("Connected to ${it.nodeInfo()}") }

    private fun CordaRPCOps.printStates() {
        val states = vaultQuery(Cash.State::class.java).states
        println("==============================================")
        states.forEach { println(it) }
        println("==============================================")
    }

    private fun CordaRPCOps.printAmount(): Amount<Currency>? {
        val result = vaultQuery(Cash.State::class.java).states.map { it.state.data.amount.withoutIssuer() }
                .takeIf { it.isNotEmpty() }?.reduce() { a, b -> a + b }
        println("Node [${nodeInfo().legalIdentities.first()}] has $result.")
        return result
    }

    @Test
    fun check() {
        connect("localhost:10006").printStates()
        connect("localhost:10009").printStates()
    }

    @Test
    fun step1() {
        val node = connect("localhost:10006")
        val notary = node.notaryIdentities().first()
        println("Using notary: $notary")

        val party1 = node.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Denis Test Node, L=London, C=GB"))
        val party2 = node.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Denis Test Node 2, L=London, C=GB"))

        node.printAmount()
        node.startFlow(::CashIssueFlow, amount, issuerRef, notary).returnValue.get().also { println(it) }
        node.startFlowDynamic(CashPaymentFlow::class.java, amount, party1, false).returnValue.get().also { println(it) }
        node.startFlowDynamic(CashPaymentFlow::class.java, amount, party2, false).returnValue.get().also { println(it) }

        node.startFlow(::CashIssueFlow, amount, issuerRef, notary).returnValue.get().also { println(it) }
        node.startFlowDynamic(CashPaymentFlow::class.java, amount, party1, false).returnValue.get().also { println(it) }

        node.startFlow(::CashIssueFlow, amount, issuerRef, notary).returnValue.get().also { println(it) }
        node.printAmount()
    }

    @Test
    fun step2() {
        val node = connect("localhost:10006")
        val notary = node.notaryIdentities().first()
        println("Using notary: $notary")

        val party1 = node.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Denis Test Node, L=London, C=GB"))
        val party2 = node.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Denis Test Node 2, L=London, C=GB"))

        node.printAmount()
        node.startFlow(::CashIssueFlow, amount, issuerRef, notary).returnValue.get().also { println(it) }
        node.startFlowDynamic(CashPaymentFlow::class.java, amount, party1, false).returnValue.get().also { println(it) }

        node.startFlow(::CashIssueFlow, amount, issuerRef, notary).returnValue.get().also { println(it) }

        node.startFlowDynamic(CashPaymentFlow::class.java, amount.times(4), party2, false).returnValue.get().also { println(it) }
        node.printAmount()
    }

    @Test
    fun move() {
        val node = connect("localhost:10009")
        val party1 = node.wellKnownPartyFromX500Name(CordaX500Name.parse("O=Denis Test Node, L=London, C=GB"))
        val totalAmount = node.printAmount()
        if (totalAmount != null) {
            node.startFlowDynamic(CashPaymentFlow::class.java, totalAmount, party1, false).returnValue.get().also { println(it) }
            node.printAmount()
        }
    }

    @Test
    fun exit() {
        val node = connect("localhost:10006")
        val totalAmount = node.printAmount()
        if (totalAmount != null) {
            node.startFlow(::CashExitFlow, totalAmount, issuerRef).returnValue.get().also { println(it) }
            node.printAmount()
        }
    }
}
