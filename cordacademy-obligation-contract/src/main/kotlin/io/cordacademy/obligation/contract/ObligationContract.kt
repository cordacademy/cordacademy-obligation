package io.cordacademy.obligation.contract

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

/**
 * Represents the obligation contract which governs how obligation states may evolve.
 */
class ObligationContract : Contract {

    companion object {

        /**
         * Gets the identity of the obligation contract.
         */
        @JvmStatic
        val ID: ContractClassName = this::class.java.enclosingClass.canonicalName
    }

    /**
     * Verifies the transaction.
     *
     * @param tx The ledger transaction to be verified.
     */
    override fun verify(tx: LedgerTransaction) {
        val command = tx.commands.requireSingleCommand<ObligationContractCommand>()
        when (command.value) {
            is Issue,
            is Transfer,
            is Settle,
            is Exit -> command.value.verify(tx, command.signers.toSet())
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    /**
     * Defines the interface for obligation command implementations.
     */
    interface ObligationContractCommand : CommandData {

        /**
         * Verifies the transaction.
         *
         * @param tx The transaction to be verified.
         * @param signers A set of public keys that have been used to sign the transaction.
         */
        fun verify(tx: LedgerTransaction, signers: Set<PublicKey>)
    }

    /**
     * Represents the obligation issuance command.
     */
    class Issue : ObligationContractCommand {

        companion object {

            internal const val CONTRACT_RULE_INPUTS =
                "When issuing an obligation, zero input states must be consumed."

            internal const val CONTRACT_RULE_OUTPUTS =
                "When issuing an obligation, only one output state must be created."

            internal const val CONTRACT_RULE_AMOUNT_IS_POSITIVE =
                "When issuing an obligation, the output state must be issued with a positive amount."

            internal const val CONTRACT_RULE_OBLIGEE_ISNT_OBLIGOR =
                "When issuing an obligation, the obligor and obligee must not be the same identity."

            internal const val CONTRACT_RULE_SIGNERS =
                "When issuing an obligation, all participants must sign the transaction."
        }

        /**
         * Verifies the transaction.
         *
         * @param tx The transaction to be verified.
         * @param signers A set of public keys that have been used to sign the transaction.
         */
        override fun verify(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {

            // Transaction verification
            CONTRACT_RULE_INPUTS using (tx.inputs.isEmpty())
            CONTRACT_RULE_OUTPUTS using (tx.outputs.size == 1)

            // State verification
            val output = tx.outputsOfType<ObligationState>().single()
            CONTRACT_RULE_AMOUNT_IS_POSITIVE using (output.borrowed.quantity > 0)
            CONTRACT_RULE_OBLIGEE_ISNT_OBLIGOR using (output.obligor != output.obligee)
            CONTRACT_RULE_SIGNERS using (signers == output.participantKeys)
        }
    }

    /**
     * Represents the obligation transfer command.
     */
    class Transfer : ObligationContractCommand {

        companion object {

            internal const val CONTRACT_RULE_INPUTS =
                "When transferring an obligation, only one input state must be consumed."

            internal const val CONTRACT_RULE_OUTPUTS =
                "When transferring an obligation, only one output state must be created."

            internal const val CONTRACT_RULE_OBLIGEE_CHANGED =
                "When transferring an obligation, the obligee must change."

            internal const val CONTRACT_RULE_ONLY_OBLIGEE_CHANGED =
                "When transferring an obligation, only the obligee must change."

            internal const val CONTRACT_RULE_OBLIGEE_ISNT_OBLIGOR =
                "When transferring an obligation, the obligor and obligee must not be the same identity."

            internal const val CONTRACT_RULE_SIGNERS =
                "When transferring an obligation, the obligor, old obligee and new obligee must sign the transaction."
        }

        /**
         * Verifies the transaction.
         *
         * @param tx The transaction to be verified.
         * @param signers A set of public keys that have been used to sign the transaction.
         */
        override fun verify(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {

            // Transaction verification
            CONTRACT_RULE_INPUTS using (tx.inputs.size == 1)
            CONTRACT_RULE_OUTPUTS using (tx.outputs.size == 1)

            // State verification
            val input = tx.inputsOfType<ObligationState>().single()
            val output = tx.outputsOfType<ObligationState>().single()
            CONTRACT_RULE_OBLIGEE_CHANGED using (input.obligee != output.obligee)
            CONTRACT_RULE_ONLY_OBLIGEE_CHANGED using (input.hashWithoutObligee() == output.hashWithoutObligee())
            CONTRACT_RULE_OBLIGEE_ISNT_OBLIGOR using (output.obligee != output.obligor)
            CONTRACT_RULE_SIGNERS using (signers == input.participantKeys union output.participantKeys)
        }
    }

    /**
     * Represents the obligation settlement command.
     */
    class Settle : ObligationContractCommand {

        companion object {

            internal const val CONTRACT_RULE_INPUTS =
                "When settling an obligation, only one input state must be consumed."

            internal const val CONTRACT_RULE_OUTPUTS =
                "When settling an obligation, only one output state must be created."

            internal const val CONTRACT_RULE_OUTPUTS_AMOUNT =
                "When settling an obligation, the settled amount must not be greater than the borrowed amount."

            internal const val CONTRACT_RULE_ONLY_SETTLED_CHANGED =
                "When settling an obligation, only the settled amount must change."

            internal const val CONTRACT_RULE_SIGNERS =
                "When settling an obligation, all participants must sign the transaction."
        }

        /**
         * Verifies the transaction.
         *
         * @param tx The transaction to be verified.
         * @param signers A set of public keys that have been used to sign the transaction.
         */
        override fun verify(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {

            // Transaction verification
            CONTRACT_RULE_INPUTS using (tx.inputs.size == 1)
            CONTRACT_RULE_OUTPUTS using (tx.outputs.size == 1)

            // State verification
            val input = tx.inputsOfType<ObligationState>().single()
            val output = tx.outputsOfType<ObligationState>().single()
            CONTRACT_RULE_OUTPUTS_AMOUNT using (output.borrowed >= output.settled)
            CONTRACT_RULE_ONLY_SETTLED_CHANGED using (input.hashWithoutSettled() == output.hashWithoutSettled())
            CONTRACT_RULE_SIGNERS using (signers == output.participantKeys)
        }
    }

    /**
     * Represents the obligation exit command.
     */
    class Exit : ObligationContractCommand {

        companion object {

            internal const val CONTRACT_RULE_INPUTS =
                "When exiting an obligation, only one input state must be consumed."

            internal const val CONTRACT_RULE_OUTPUTS =
                "When exiting an obligation, zero output states must be created."

            internal const val CONTRACT_RULE_INPUT_SETTLED =
                "When exiting an obligation, the input state must be fully settled."

            internal const val CONTRACT_RULE_SIGNERS =
                "When exiting an obligation, all participants must sign the transaction."
        }

        /**
         * Verifies the transaction.
         *
         * @param tx The transaction to be verified.
         * @param signers A set of public keys that have been used to sign the transaction.
         */
        override fun verify(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {

            // Transaction verification
            CONTRACT_RULE_INPUTS using (tx.inputs.size == 1)
            CONTRACT_RULE_OUTPUTS using (tx.outputs.isEmpty())

            // State verification
            val input = tx.inputsOfType<ObligationState>().single()
            CONTRACT_RULE_INPUT_SETTLED using (input.borrowed == input.settled)
            CONTRACT_RULE_SIGNERS using (signers == input.participantKeys)
        }
    }
}