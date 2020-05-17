package com.test

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.redeem.ConfidentialRedeemFungibleTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.redeem.ConfidentialRedeemFungibleTokensFlowHandler
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import java.util.*

@InitiatingFlow
@StartableByRPC
class TCashRedeemTokenFlow(private val amount: Amount<Currency>, private val issuer: Party) : FlowLogic<SignedTransaction>() {

    override val progressTracker = ProgressTracker(INITIALIZING, REDEEMING)

    private fun Amount<Currency>.tokenize(): Amount<TokenType> =
            Amount(quantity, displayTokenSize, TokenType(token.currencyCode, token.defaultFractionDigits))

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("${javaClass.name} start")
        progressTracker.currentStep = INITIALIZING
        val session = initiateFlow(issuer)

        progressTracker.currentStep = REDEEMING
        val ret = subFlow(ConfidentialRedeemFungibleTokensFlow(amount.tokenize(), session))
        logger.info("${javaClass.name} finish")
        return ret
    }

    @InitiatedBy(TCashRedeemTokenFlow::class)
    class Responder(private val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("${javaClass.name} start")
            subFlow(ConfidentialRedeemFungibleTokensFlowHandler(counterpartySession))
            logger.info("${javaClass.name} finish")
        }
    }

    companion object {
        object INITIALIZING : ProgressTracker.Step("Initializing")
        object REDEEMING : ProgressTracker.Step("Redeeming cash token")
    }
}
