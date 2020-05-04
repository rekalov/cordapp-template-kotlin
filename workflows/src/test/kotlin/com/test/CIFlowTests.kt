package com.test

import com.template.flows.Responder
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test

class CIFlowTests {
    private val network = MockNetwork(MockNetworkParameters(cordappsForAllNodes = listOf(
        TestCordapp.findCordapp("com.test")
    )))
    private val a = network.createNode()
    private val b = network.createNode()

    init {
        listOf(a, b).forEach {
            it.registerInitiatedFlow(Responder::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `dummy test`() {
        a.startFlow(Generator())
        a.startFlow(Signer())
        a.startFlow(Signer())
    }
}