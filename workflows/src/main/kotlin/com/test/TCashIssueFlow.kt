package com.test

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.workflows.flows.issue.ConfidentialIssueTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.issue.ConfidentialIssueTokensFlowHandler
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import java.util.*


@InitiatingFlow
@StartableByRPC
class TCashIssueFlow(private val amount: Amount<Currency>, val recipient: Party) : FlowLogic<SignedTransaction>() {
    constructor(amount: Amount<Currency>, issueRef: OpaqueBytes, recipient: Party, anonymous: Boolean, notary: Party)
            : this(amount, recipient)

    override val progressTracker = ProgressTracker(INITIALIZING, ISSUING_CASH_TOKEN)

    private fun Amount<Currency>.tokenize(): Amount<TokenType> =
            Amount(quantity, displayTokenSize, TokenType(token.currencyCode, token.defaultFractionDigits))

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("TCashIssueFlow start")
        progressTracker.currentStep = INITIALIZING
        val token = amount.tokenize() issuedBy ourIdentity heldBy recipient
        val session = initiateFlow(recipient)

        progressTracker.currentStep = ISSUING_CASH_TOKEN
        val ret = subFlow(ConfidentialIssueTokensFlow(listOf(token), listOf(session)))
        logger.info("TCashIssueFlow finish")
        return ret
    }

    @InitiatedBy(TCashIssueFlow::class)
    class Responder(private val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("TCashIssueFlowResponse start")
            subFlow(ConfidentialIssueTokensFlowHandler(counterpartySession))
            logger.info("TCashIssueFlowResponse finish")
        }
    }

    companion object {
        object INITIALIZING : ProgressTracker.Step("Initializing")
        object ISSUING_CASH_TOKEN : ProgressTracker.Step("Issuing cash token")
    }
}
