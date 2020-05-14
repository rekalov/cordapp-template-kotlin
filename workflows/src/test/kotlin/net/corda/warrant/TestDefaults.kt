package net.corda.warrant

import net.corda.testing.node.TestCordapp

class TestDefaults {
    companion object {
        private val cordappPackages = listOf(
                "net.corda.warrant.contracts",
                "net.corda.warrant.flows",
                "com.test",
                "com.template.contracts",
                "com.template.flows",

                "com.r3.corda.lib.tokens.contracts",
                "com.r3.corda.lib.tokens.workflows.flows",
                "com.r3.corda.lib.tokens.money")
        val allCordapps by lazy { cordappPackages.map(TestCordapp.Companion::findCordapp) }
    }
}