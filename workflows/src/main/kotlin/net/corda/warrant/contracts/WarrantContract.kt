package net.corda.warrant.contracts

import com.r3.corda.lib.tokens.contracts.EvolvableTokenContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class WarrantContract : EvolvableTokenContract(), Contract {
    companion object {
        // Used to identify our contract when building a transaction.
        const val ID = "net.corda.warrant.contracts.WarrantContract"
    }

    override fun additionalCreateChecks(tx: LedgerTransaction) {
        // TODO implement
    }

    override fun additionalUpdateChecks(tx: LedgerTransaction) {
        // TODO implement
    }
}