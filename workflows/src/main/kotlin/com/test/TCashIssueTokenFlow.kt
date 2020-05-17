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
class TCashIssueTokenFlow(private val amount: Amount<Currency>, private val recipient: Party) : FlowLogic<SignedTransaction>() {
    @Suppress("UNUSED", "UNUSED_PARAMETER")
    constructor(amount: Amount<Currency>, issueRef: OpaqueBytes, recipient: Party, anonymous: Boolean, notary: Party)
            : this(amount, recipient)

    override val progressTracker = ProgressTracker(INITIALIZING, ISSUING)

    private fun Amount<Currency>.tokenize(): Amount<TokenType> =
            Amount(quantity, displayTokenSize, TokenType(token.currencyCode, token.defaultFractionDigits))

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("${javaClass.name} start")
        progressTracker.currentStep = INITIALIZING
        val token = amount.tokenize() issuedBy ourIdentity heldBy recipient
        val session = initiateFlow(recipient)

        progressTracker.currentStep = ISSUING
        val ret = subFlow(ConfidentialIssueTokensFlow(listOf(token), listOf(session)))
        logger.info("${javaClass.name} finish")
        return ret
    }

    @InitiatedBy(TCashIssueTokenFlow::class)
    class Responder(private val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("${javaClass.name} start")
            subFlow(ConfidentialIssueTokensFlowHandler(counterpartySession))
            logger.info("${javaClass.name} finish")
        }
    }

    companion object {
        object INITIALIZING : ProgressTracker.Step("Initializing")
        object ISSUING : ProgressTracker.Step("Issuing cash token")
    }
}
