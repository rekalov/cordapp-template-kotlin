package com.test

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractState
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.LedgerTransaction
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

class DummyContract : Contract {
    companion object {
        const val ID = "com.test.DummyContract"
    }

    override fun verify(tx: LedgerTransaction) {
    }

    interface Commands : CommandData {
        class Action : Commands
    }
}

@BelongsToContract(DummyContract::class)
data class DummyState(override val participants: List<AbstractParty>) : ContractState

@StartableByRPC
class Generator : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {
        val anonymousIdentity = serviceHub.keyManagementService.freshKeyAndCert(ourIdentityAndCert, false)
        val transactionBuilder = TransactionBuilder()
        transactionBuilder.notary = serviceHub.networkMapCache.notaryIdentities[0]
        transactionBuilder.addOutputState(DummyState(listOf(anonymousIdentity.party)), DummyContract.ID)
        transactionBuilder.addCommand(DummyContract.Commands.Action(), anonymousIdentity.owningKey)
        transactionBuilder.verify(serviceHub)
        val tx = serviceHub.signInitialTransaction(transactionBuilder, anonymousIdentity.owningKey)
        logger.info("Signed transaction with generated wrapped key")
        return subFlow(FinalityFlow(tx, listOf()))
    }
}

@StartableByRPC
class Signer : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {
        val state = serviceHub.vaultService.queryBy(DummyState::class.java).states.first()
        logger.info("Found state $state")
        val transactionBuilder = TransactionBuilder()
        transactionBuilder.notary = serviceHub.networkMapCache.notaryIdentities[0]
        transactionBuilder.addInputState(state)
        transactionBuilder.addCommand(DummyContract.Commands.Action(), state.state.data.participants[0].owningKey)
        transactionBuilder.verify(serviceHub)
        val tx = serviceHub.signInitialTransaction(transactionBuilder, state.state.data.participants[0].owningKey)
        logger.info("Signed transaction with stored wrapped key")
        return subFlow(FinalityFlow(tx, listOf()))
    }
}

