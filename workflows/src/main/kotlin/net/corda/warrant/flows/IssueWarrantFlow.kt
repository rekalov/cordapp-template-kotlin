package net.corda.warrant.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenPointer
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.workflows.flows.issue.ConfidentialIssueTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.issue.ConfidentialIssueTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.warrant.states.Warrant
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class IssueWarrantFlow(
        private val issuances: List<PartyAndAmount<TokenPointer<Warrant>>>) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker(INITIALIZING, ISSUING_WARRANT_TOKEN)

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("zZA1-IssueWarrantFlow start")
        progressTracker.currentStep = INITIALIZING
        val tokens = issuances.map {
            val amountToken = it.amount
            val issued = Amount(amountToken.quantity, amountToken.displayTokenSize, IssuedTokenType(ourIdentity, amountToken.token))
            issued heldBy it.party
        }
        val participants = tokens.map { it.participants }.flatten().toSet()
        val sessions = participants.map { party -> initiateFlow(party as Party) }

        progressTracker.currentStep = ISSUING_WARRANT_TOKEN
        val ret = subFlow(ConfidentialIssueTokensFlow(tokens, sessions))
        logger.info("zZA1-IssueWarrantFlow finish")
        return ret
    }

    @InitiatedBy(IssueWarrantFlow::class)
    class IssueWarrantFlowResponse (private val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("zZA1-IssueWarrantFlowResponse start")
            subFlow(ConfidentialIssueTokensFlowHandler(flowSession))
            logger.info("zZA1-IssueWarrantFlowResponse finish")
        }
    }

    companion object {
        object INITIALIZING : ProgressTracker.Step("Initializing")
        object ISSUING_WARRANT_TOKEN : ProgressTracker.Step("Issuing warrant token")
    }
}
