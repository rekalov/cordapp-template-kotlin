package net.corda.warrant.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.selection.database.selector.DatabaseTokenSelection
import com.r3.corda.lib.tokens.workflows.flows.confidential.ConfidentialTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.confidential.ConfidentialTokensFlowHandler
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlowHandler
import net.corda.core.contracts.*
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import java.util.UUID

@InitiatingFlow
@StartableByRPC
class SettlementFlow(private val basket: List<AmountTransfer<TokenType, Party>>) : FlowLogic<SignedTransaction>() {

    override val progressTracker: ProgressTracker

    private val sessionSteps: List<PerSessionSteps>
    private val participants: List<Party>

    init {
        val sources = basket.map { it.source }.toSet()
        val destinations = basket.map { it.destination }.toSet()
        participants = (sources + destinations).toList()
        sessionSteps = participants.mapIndexed { index, _ ->
            PerSessionSteps(
                    requestingTransfers = ProgressTracker.Step("Requesting token transfers $index"),
                    collectingInputs = ProgressTracker.Step("Collecting input tokens $index"),
                    collectingOutputs = ProgressTracker.Step("Collecting output tokens $index")
            )
        }
        progressTracker = ProgressTracker(
                //QUERYING_WARRANTS,
                *sessionSteps.map { listOf(it.requestingTransfers, it.collectingInputs, it.collectingOutputs) }.flatten().toTypedArray(),
                ANONYMIZING, SIGNING, SIGN_INITIAL_TRANSACTION, COLLECTING_SIGNATURES, RECORDING, DISTRIBUTING)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        logger.info("zZA1-SettlementFlow start")
        val inputs = mutableListOf<StateAndRef<FungibleToken>>()
        val outputs = mutableListOf<FungibleToken>()

//    progressTracker.currentStep = QUERYING_WARRANTS

        val participantSessions = participants.map { initiateFlow(it) }

        val lockId = UUID.randomUUID()

        participantSessions.forEachIndexed { sessionIndex, session ->

    progressTracker.currentStep = sessionSteps[sessionIndex].requestingTransfers

            val transferRequests = basket.filter { it.source == session.counterparty }.map {
                Pair(it.destination, Amount(it.quantityDelta, it.token))
            }
            val transferRequest = TokenTransferRequest(lockId, transferRequests)
            session.send(transferRequest)

            if (transferRequest.transfers.isNotEmpty()) {
    progressTracker.currentStep = sessionSteps[sessionIndex].collectingInputs
                inputs += subFlow(ReceiveStateAndRefFlow(session))

    progressTracker.currentStep = sessionSteps[sessionIndex].collectingOutputs
                outputs += session.receive<List<FungibleToken>>().unwrap { it }
            }

        }

    progressTracker.currentStep = ANONYMIZING

        val confidentialOutputs = subFlow(
                ConfidentialTokensFlow(outputs, participantSessions))

    progressTracker.currentStep = SIGNING

        val transactionBuilder = addMoveTokens(
                TransactionBuilder(
                        notary = serviceHub.networkParameters.notaries.first().identity,
                        lockId = lockId),
                inputs,
                confidentialOutputs)

    progressTracker.currentStep = SIGN_INITIAL_TRANSACTION

        // TODO ObserverAwareFinalityFlow from token sdk gets initial signature differently and fails, need to investigate
        var signedTransaction = serviceHub.signInitialTransaction(transactionBuilder)

        progressTracker.currentStep = COLLECTING_SIGNATURES

        signedTransaction = subFlow(CollectSignaturesFlow(
                signedTransaction, participantSessions, COLLECTING_SIGNATURES.childProgressTracker()))

    progressTracker.currentStep = RECORDING

        // Create new participantSessions if this is started as a top level flow.
        signedTransaction = subFlow(
                ObserverAwareFinalityFlow(
                        signedTransaction = signedTransaction,
                        allSessions = participantSessions // TODO + observerSessions
                )
        )

        // Update the distribution list.
        logger.info("zZA1-SettlementFlow-UpdateDistributionListFlow start disabled")
     // progressTracker.currentStep = DISTRIBUTING
     // subFlow(UpdateDistributionListFlow(signedTransaction))
     // logger.info("zZA2-PurchaseWarrantFlow-UpdateDistributionListFlow finish")

        logger.info("zZA1-SettlementFlow finish")
        // Return the newly created transaction.
        return signedTransaction
    }

    private fun <T : ContractState> getStateReference(clazz: Class<T>, id: UniqueIdentifier): StateAndRef<T> {
        val vaultPage = serviceHub.vaultService.queryBy(clazz,
                QueryCriteria.LinearStateQueryCriteria(linearId = listOf(id)))

        requireThat {
            "State not found" using (vaultPage.states.size == 1)
        }

        return vaultPage.states.first()
    }

    @CordaSerializable
    data class TokenTransferRequest(val lockId: UUID, val transfers: List<Pair<Party, Amount<TokenType>>>)

    @InitiatedBy(SettlementFlow::class)
    class SettlementResponseFlow (private val flowSession: FlowSession): FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            logger.info("zZA1-SettlementResponseFlow start")
            val (lockId, transferRequests) = flowSession.receive<TokenTransferRequest>().unwrap { it }

            if (transferRequests.isNotEmpty()) {
                val transferGroups = transferRequests.groupBy { it.second.token }.values.toList()
                val allInputs = mutableListOf<StateAndRef<FungibleToken>>()
                val allOutputs = mutableListOf<FungibleToken>()
                transferGroups.forEach {
                    val (inputs, outputs)
                            = DatabaseTokenSelection(this.serviceHub).generateMove(it, ourIdentity, lockId = lockId)
                    allInputs += inputs
                    allOutputs += outputs
                }

                logger.info("zZA2-SettlementResponseFlow-SendStateAndRefFlow start")
                subFlow(SendStateAndRefFlow(flowSession, allInputs))
                logger.info("zZA2-SettlementResponseFlow-SendStateAndRefFlow finish")

                logger.info("zZA2-SettlementResponseFlow-send(allOutputs) start")
                flowSession.send(allOutputs)
                logger.info("zZA2-SettlementResponseFlow-send(allOutputs) finish")
            }

            logger.info("zZA2-SettlementResponseFlow-subFlow(ConfidentialTokensFlowHandler) start")
            subFlow(ConfidentialTokensFlowHandler(flowSession))
            logger.info("zZA2-SettlementResponseFlow-subFlow(ConfidentialTokensFlowHandler) finish")

            val signTransactionFlow = object : SignTransactionFlow(flowSession) {
                override fun checkTransaction(stx: SignedTransaction) {
                }
            }
            logger.info("zZA2-SettlementResponseFlow-subFlow(signTransactionFlow) start")
            subFlow(signTransactionFlow)
            logger.info("zZA2-SettlementResponseFlow-subFlow(signTransactionFlow) finish")

            // TODO raise token sdk issue - inability to pass transactionId
            logger.info("zZA2-SettlementResponseFlow-ObserverAwareFinalityFlowHandler start")
            subFlow((ObserverAwareFinalityFlowHandler(flowSession)))
            logger.info("zZA2-SettlementResponseFlow-ObserverAwareFinalityFlowHandler finish")

            logger.info("zZA2-SettlementResponseFlow-UpdateDistributionListFlowHandler start disabled")
         // subFlow(UpdateDistributionListFlowHandler(flowSession))
         // logger.info("zZA2-PurchaseWarrantFlowResponse-UpdateDistributionListFlowHandler finish")
            logger.info("zZA1-SettlementResponseFlow finish")
        }
    }

    private class PerSessionSteps(
            val requestingTransfers: ProgressTracker.Step,
            val collectingInputs: ProgressTracker.Step,
            val collectingOutputs: ProgressTracker.Step)

    companion object {
        //object QUERYING_WARRANTS : ProgressTracker.Step("Querying vault for warrant reference states")
        object ANONYMIZING : ProgressTracker.Step("Anonymizing token holders")
        object SIGNING : ProgressTracker.Step("Generating and signing transaction proposal")
        object SIGN_INITIAL_TRANSACTION : ProgressTracker.Step("Sign initial transaction")
        object COLLECTING_SIGNATURES : ProgressTracker.Step("Collecting signatures from other parties") {
            override fun childProgressTracker() = CollectSignaturesFlow.tracker()
        }
        object RECORDING : ProgressTracker.Step("Recording completed transaction")
        object DISTRIBUTING : ProgressTracker.Step("Updating warrant distribution list") {
            override fun childProgressTracker() = UpdateDistributionListFlow.tracker()
        }
    }
}
