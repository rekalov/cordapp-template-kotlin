package com.test

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.ConfidentialMoveFungibleTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.move.ConfidentialMoveTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.types.PartyAndAmount
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import java.util.*

@InitiatingFlow
@StartableByRPC
class TCashPaymentTokenFlow(private val amount: Amount<Currency>, private val recipient: Party) : FlowLogic<SignedTransaction>() {
    override val progressTracker = ProgressTracker(INITIALIZING, PAYING)

    private fun Amount<Currency>.tokenize(): Amount<TokenType> =
            Amount(quantity, displayTokenSize, TokenType(token.currencyCode, token.defaultFractionDigits))

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("${javaClass.name} start")
        progressTracker.currentStep = INITIALIZING
        val session = initiateFlow(recipient)
        val partyAndAmount = PartyAndAmount(recipient, amount.tokenize())

        progressTracker.currentStep = PAYING
        val ret = subFlow(ConfidentialMoveFungibleTokensFlow(partyAndAmount, listOf(session), ourIdentity))
        logger.info("${javaClass.name} finish")
        return ret
    }

    @InitiatedBy(TCashPaymentTokenFlow::class)
    class Responder(private val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("${javaClass.name} start")
            subFlow(ConfidentialMoveTokensFlowHandler(counterpartySession))
            logger.info("${javaClass.name} finish")
        }
    }

    companion object {
        object INITIALIZING : ProgressTracker.Step("Initializing")
        object PAYING : ProgressTracker.Step("Paying cash token")
    }
}
