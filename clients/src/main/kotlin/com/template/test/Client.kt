package com.template.test

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.flows.NotaryChangeFlow
import net.corda.core.utilities.NetworkHostAndPort.Companion.parse
import net.corda.core.utilities.getOrThrow
import net.corda.finance.contracts.asset.Cash
import kotlin.system.exitProcess

/**
 * Connects to a Corda node via RPC and performs RPC operations on the node.
 */
fun main(args: Array<String>) {
    // Create an RPC connection to the node.
    val proxy = CordaRPCClient(parse("localhost:10006")).start("test", "test").proxy

    // Interact with the node.
    // For example, here we print the nodes on the network.
    val nodes = proxy.networkMapSnapshot()
    println(nodes)

    val state = proxy.vaultQuery(Cash.State::class.java).states.first()
    println(state)

    val firstNotary = proxy.notaryIdentities().first()
    println(firstNotary)
    val lastNotary = proxy.notaryIdentities().last()
    println(lastNotary)

    if (state.state.notary == firstNotary) {
        println("Changing notary ...")
        val result = proxy.startFlowDynamic(NotaryChangeFlow::class.java, state, lastNotary).returnValue.getOrThrow()
        println("Changed: $result")
    }
    exitProcess(0)
}
