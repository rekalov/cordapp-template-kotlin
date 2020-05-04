package net.corda.warrant.states

import com.r3.corda.lib.tokens.contracts.states.EvolvableTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.warrant.contracts.WarrantContract
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.time.Instant

@BelongsToContract(WarrantContract::class)
data class Warrant(
        val stockName: String,
        val price: Amount<TokenType>,
        val expirationDate: Instant,
        override val maintainers: List<Party>,
        override val participants: List<AbstractParty> = maintainers,
        override val linearId: UniqueIdentifier = UniqueIdentifier()) : EvolvableTokenType() {
    override val fractionDigits: Int = 0
}
