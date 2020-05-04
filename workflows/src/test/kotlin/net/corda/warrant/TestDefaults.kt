package net.corda.warrant

import net.corda.testing.node.TestCordapp

class TestDefaults {
    companion object {
        private val cordappPackages = listOf(
                "net.corda.behave.tokensdk.contracts",
                "net.corda.behave.tokensdk.flows",

                "com.r3.corda.lib.tokens.contracts",
                "com.r3.corda.lib.tokens.workflows.flows",
                "com.r3.corda.lib.tokens.money")
        val allCordapps by lazy { cordappPackages.map(TestCordapp.Companion::findCordapp) }
    }
}