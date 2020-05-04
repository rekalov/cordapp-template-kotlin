package net.corda.warrant.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.withNotary
import com.r3.corda.lib.tokens.workflows.flows.rpc.CreateEvolvableTokens
import net.corda.warrant.states.Warrant
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import java.time.Instant

@InitiatingFlow
@StartableByRPC
class CreateWarrantFlow(
        private val stockName: String,
        private val price: Amount<TokenType>,
        private val expirationDate: Instant) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker(INITIALIZING, CREATING_WARRANT_TOKEN)

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("zZA1-CreateWarrantFlow start")
        progressTracker.currentStep = INITIALIZING
        val warrant = Warrant(stockName, price, expirationDate, listOf(ourIdentity))
        val notary = serviceHub.networkMapCache.notaryIdentities.first()
        val transactionState = warrant withNotary notary

        progressTracker.currentStep = CREATING_WARRANT_TOKEN
        val ret = subFlow(CreateEvolvableTokens(transactionState))
        logger.info("zZA1-CreateWarrantFlow finish")
        return ret
    }

    companion object {
        object INITIALIZING : ProgressTracker.Step("Initializing")
        object CREATING_WARRANT_TOKEN : ProgressTracker.Step("Creating warrant token")
    }
}
