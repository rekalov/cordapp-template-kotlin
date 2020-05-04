package net.corda.warrant.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.issue.ConfidentialIssueTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.issue.ConfidentialIssueTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class IssueCashFlow(private val issuances: List<PartyAndAmount<TokenType>>) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker(INITIALIZING, ISSUING_CASH_TOKEN)

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("zZA1-IssueCashFlow start")
        progressTracker.currentStep = INITIALIZING
        val tokens = issuances.map { it.amount issuedBy ourIdentity heldBy it.party }
        val participants = tokens.map { it.participants }.flatten().toSet()
        val sessions = participants.map { party -> initiateFlow(party as Party) }

        progressTracker.currentStep = ISSUING_CASH_TOKEN
        val issueFlow = ConfidentialIssueTokensFlow(tokens, sessions)
        val ret = subFlow(issueFlow)
        logger.info("zZA1-IssueCashFlow finish")
        return ret
    }

    @InitiatedBy(IssueCashFlow::class)
    class Responder(private val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("zZA1-IssueCashFlowResponse start")
            subFlow(ConfidentialIssueTokensFlowHandler(counterpartySession))
            logger.info("zZA1-IssueCashFlowResponse finish")
        }
    }

    companion object {
        object INITIALIZING : ProgressTracker.Step("Initializing")
        object ISSUING_CASH_TOKEN : ProgressTracker.Step("Issuing cash token")
    }
}
