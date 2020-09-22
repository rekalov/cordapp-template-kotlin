package com.test

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import org.junit.Before
import org.junit.Test

class FinanceTest {
    private lateinit var node1: CordaRPCOps
    private lateinit var node2: CordaRPCOps

    @Before
    fun before() {
        node1 = connect("localhost:10006")
        node2 = connect("localhost:10009")
    }

    private fun connect(address: String) = CordaRPCClient(NetworkHostAndPort.parse(address)).start("test", "test")
            .proxy.also { println("Connected to ${it.nodeInfo()}") }

    private fun CordaRPCOps.printStates() {
        val states = vaultQuery(Cash.State::class.java).states
        println("==============================================")
        states.forEach { println(it) }
        println("==============================================")
    }

    private fun CordaRPCOps.printStatesCount() {
        val states = vaultQuery(Cash.State::class.java).states
        println("Node [${nodeInfo().legalIdentities.first()}] has ${states.size} states.")
    }


    @Test
    fun check() {
        node1.printStates()
        node2.printStates()
    }

    @Test
    fun init() {
        val notary = node1.notaryIdentities().first()
        println("Using notary: $notary")

        node1.printStatesCount()

        val amount = Amount.parseCurrency("1000 RUB")
        val issuerRef = OpaqueBytes("ref".toByteArray())

        node1.startFlow(::CashIssueFlow, amount, issuerRef, notary).returnValue.get().also { println(it) }

        node1.printStatesCount()
    }

}
