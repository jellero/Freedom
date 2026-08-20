package dev.freedom.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure Java 17 state machines shared by host simulation and future Android integration.
 *
 * This module deliberately contains no Android, RPC, socket or cryptographic provider code.
 * It implements security-relevant transition rules/oracles from the canonical specification.
 */
public final class FreedomCore {
    private FreedomCore() {}

    public static final class ProtocolViolation extends RuntimeException {
        private final String code;
        public ProtocolViolation(String code, String message) {
            super(message);
            this.code = code;
        }
        public String code() { return code; }
    }

    public static final class RouteState {
        private final Set<String> knownRelays = new HashSet<>();
        private final Set<String> blocked = new HashSet<>();
        private String from;
        private String to;
        private String route;
        private String originalFrom;
        private String originalTo;
        private boolean active;
        private boolean recovered;
        private long mailboxWrites;

        public void registerRelay(String relay) { knownRelays.add(relay); }

        public void connect(String from, String to, String via) {
            this.from = from;
            this.to = to;
            this.originalFrom = from;
            this.originalTo = to;
            this.route = via;
            this.active = true;
            this.recovered = false;
        }

        public void block(String target) { blocked.add(target); }
        public void unblock(String target) { blocked.remove(target); }
        public boolean routeBlocked() { return active && blocked.contains(route); }

        public boolean recoverRoute() {
            if (!active) return false;
            List<String> candidates = new ArrayList<>();
            for (String relay : knownRelays) {
                if (!blocked.contains(relay) && !relay.equals(route)) candidates.add(relay);
            }
            Collections.sort(candidates);
            if (candidates.isEmpty()) return false;
            route = candidates.get(0);
            recovered = true;
            return true;
        }

        public boolean peerIdentityUnchanged() {
            return active && eq(from, originalFrom) && eq(to, originalTo);
        }

        public boolean recovered() { return recovered; }
        public long mailboxWrites() { return mailboxWrites; }
        public String route() { return route; }
    }

    public static final class PairwiseRecoveryState {
        private boolean rootRestored;
        private Long anchorGeneration;
        private Long acceptedGeneration;
        private boolean lastRejected;
        private String lastReason;
        private boolean futureRotated;
        private boolean oldBackupFutureAuthority;

        public void restoreRootIdentity() { rootRestored = true; }
        public boolean rootRestored() { return rootRestored; }

        public void setVerifiedAnchor(long generation) {
            if (generation < 0) throw new ProtocolViolation("MALFORMED", "negative backup generation");
            if (anchorGeneration != null && generation < anchorGeneration) {
                throw new ProtocolViolation("CONTROL_PLANE_ROLLBACK", "pairwise recovery anchor rollback");
            }
            anchorGeneration = generation;
        }

        public boolean evaluateBackup(long generation, boolean integrityOk, boolean hashMatches, boolean stateMatches) {
            boolean accepted = integrityOk
                    && anchorGeneration != null
                    && generation == anchorGeneration
                    && hashMatches
                    && stateMatches;
            lastRejected = !accepted;
            lastReason = accepted ? null : "PAIRWISE_BACKUP_ROLLBACK_OR_MISMATCH";
            if (accepted) {
                acceptedGeneration = generation;
                oldBackupFutureAuthority = true;
            }
            return accepted;
        }

        public void rotateFutureState() {
            if (acceptedGeneration == null) {
                throw new ProtocolViolation("AUTHENTICATION_FAILED", "pairwise state not accepted");
            }
            futureRotated = true;
            oldBackupFutureAuthority = false;
        }

        public Long anchorGeneration() { return anchorGeneration; }
        public Long acceptedGeneration() { return acceptedGeneration; }
        public boolean lastRejected() { return lastRejected; }
        public String lastReason() { return lastReason; }
        public boolean futureRotated() { return futureRotated; }
        public boolean oldBackupFutureAuthority() { return oldBackupFutureAuthority; }
    }

    public static final class BootstrapFreshnessState {
        private Long minimumHeight;
        private Long verifiedHeight;
        private boolean lastRejected;
        private String lastReason;

        public void setFloor(long minimumHeight) {
            if (minimumHeight < 0) throw new ProtocolViolation("MALFORMED", "negative freshness floor");
            if (this.minimumHeight != null && minimumHeight < this.minimumHeight) {
                throw new ProtocolViolation("CONTROL_PLANE_ROLLBACK", "bootstrap floor rollback");
            }
            this.minimumHeight = minimumHeight;
        }

        public boolean verifyCheckpoint(long height, boolean proofValid) {
            if (!proofValid) {
                lastRejected = true;
                lastReason = "CONTROL_PLANE_PROOF_INVALID";
                return false;
            }
            if (minimumHeight != null && height < minimumHeight) {
                lastRejected = true;
                lastReason = "BOOTSTRAP_STATE_TOO_OLD";
                return false;
            }
            if (verifiedHeight != null && height < verifiedHeight) {
                lastRejected = true;
                lastReason = "CONTROL_PLANE_ROLLBACK";
                return false;
            }
            verifiedHeight = height;
            lastRejected = false;
            lastReason = null;
            return true;
        }

        public Long minimumHeight() { return minimumHeight; }
        public Long verifiedHeight() { return verifiedHeight; }
        public boolean lastRejected() { return lastRejected; }
        public String lastReason() { return lastReason; }
    }

    /**
     * Canonical client-side acceptance rule for a control-plane write.
     * Submission/tx hash alone never transitions local state to success.
     */
    public static final class MutationVerificationState {
        private boolean lastAccepted;
        private boolean hasCommittedState;
        private String lastReason;
        private long committedVersion;

        public boolean verify(
                boolean finalityProofValid,
                boolean executionSucceeded,
                boolean resultingStateProofValid,
                boolean exactTransitionMatched,
                long resultingVersion) {
            if (!finalityProofValid) return reject("CONTROL_PLANE_PROOF_INVALID");
            if (!executionSucceeded) return reject("CONTROL_PLANE_EXECUTION_FAILED");
            if (!resultingStateProofValid) return reject("CONTROL_PLANE_PROOF_INVALID");
            if (!exactTransitionMatched) return reject("CONTROL_PLANE_STATE_MISMATCH");
            if (hasCommittedState && resultingVersion < committedVersion) {
                return reject("CONTROL_PLANE_ROLLBACK");
            }
            lastAccepted = true;
            hasCommittedState = true;
            committedVersion = resultingVersion;
            lastReason = null;
            return true;
        }

        private boolean reject(String reason) {
            lastAccepted = false;
            lastReason = reason;
            return false;
        }

        public boolean lastAccepted() { return lastAccepted; }
        public boolean hasCommittedState() { return hasCommittedState; }
        public String lastReason() { return lastReason; }
        public long committedVersion() { return committedVersion; }
    }

    /**
     * Chain-agnostic NetworkAnchor acceptance state machine.
     *
     * Cryptographic signature/payload/consensus checks are performed by adapters and supplied as
     * verified booleans. This class owns the canonical bootstrap pin, context binding, monotonic
     * lineage, governance-transition and no-quorum-as-consensus rules.
     */
    public static final class NetworkAnchorState {
        private boolean configured;
        private String expectedNetworkId;
        private String expectedChainAdapterId;
        private String expectedChainNetworkId;
        private String expectedVerifierProfile;
        private long expectedVerifierPolicyVersion;
        private String pinnedBootstrapCommitment;
        private long minimumCheckpointHeight;

        private boolean initialized;
        private String currentCommitment;
        private long anchorEpoch;
        private long checkpointHeight;
        private long signerSetEpoch;
        private boolean lastAccepted;
        private String lastReason;

        public void configure(
                String networkId,
                String chainAdapterId,
                String chainNetworkId,
                String verifierProfile,
                long verifierPolicyVersion,
                String pinnedBootstrapCommitment,
                long minimumCheckpointHeight) {
            if (blank(networkId) || blank(chainAdapterId) || blank(chainNetworkId)
                    || blank(verifierProfile) || blank(pinnedBootstrapCommitment)
                    || verifierPolicyVersion < 0 || minimumCheckpointHeight < 0) {
                throw new ProtocolViolation("MALFORMED", "invalid NetworkAnchor verifier policy");
            }
            if (configured) {
                boolean same = eq(expectedNetworkId, networkId)
                        && eq(expectedChainAdapterId, chainAdapterId)
                        && eq(expectedChainNetworkId, chainNetworkId)
                        && eq(expectedVerifierProfile, verifierProfile)
                        && expectedVerifierPolicyVersion == verifierPolicyVersion
                        && eq(this.pinnedBootstrapCommitment, pinnedBootstrapCommitment)
                        && this.minimumCheckpointHeight == minimumCheckpointHeight;
                if (!same) {
                    throw new ProtocolViolation("NETWORK_ANCHOR_INVALID", "NetworkAnchor verifier policy cannot be silently replaced");
                }
                return;
            }
            configured = true;
            expectedNetworkId = networkId;
            expectedChainAdapterId = chainAdapterId;
            expectedChainNetworkId = chainNetworkId;
            expectedVerifierProfile = verifierProfile;
            expectedVerifierPolicyVersion = verifierPolicyVersion;
            this.pinnedBootstrapCommitment = pinnedBootstrapCommitment;
            this.minimumCheckpointHeight = minimumCheckpointHeight;
        }

        public boolean acceptCandidate(
                String networkId,
                String chainAdapterId,
                String chainNetworkId,
                String verifierProfile,
                long verifierPolicyVersion,
                String commitment,
                String previousCommitment,
                long candidateAnchorEpoch,
                long trustedCheckpointHeight,
                long candidateSignerSetEpoch,
                long issuedAtHeight,
                long activationHeight,
                boolean payloadBindingValid,
                boolean thresholdSignaturesValid,
                boolean signerSetTransitionValid,
                boolean consensusContinuityValid) {
            if (!configured) {
                throw new ProtocolViolation("NETWORK_ANCHOR_INVALID", "NetworkAnchor verifier policy is not configured");
            }
            if (blank(commitment) || candidateAnchorEpoch <= 0 || candidateSignerSetEpoch <= 0
                    || trustedCheckpointHeight < 0 || issuedAtHeight < 0 || activationHeight < 0) {
                return reject("NETWORK_ANCHOR_INVALID");
            }
            if (!eq(expectedNetworkId, networkId)
                    || !eq(expectedChainAdapterId, chainAdapterId)
                    || !eq(expectedChainNetworkId, chainNetworkId)
                    || !eq(expectedVerifierProfile, verifierProfile)
                    || expectedVerifierPolicyVersion != verifierPolicyVersion) {
                return reject("NETWORK_ANCHOR_INVALID");
            }
            if (!payloadBindingValid || !thresholdSignaturesValid) {
                return reject("NETWORK_ANCHOR_INVALID");
            }
            if (trustedCheckpointHeight < minimumCheckpointHeight) {
                return reject("BOOTSTRAP_STATE_TOO_OLD");
            }
            if (issuedAtHeight > activationHeight || activationHeight > trustedCheckpointHeight) {
                return reject("NETWORK_ANCHOR_NOT_ACTIVE");
            }

            if (!initialized) {
                if (previousCommitment != null || !eq(commitment, pinnedBootstrapCommitment)) {
                    return reject("NETWORK_ANCHOR_INVALID");
                }
                commit(commitment, candidateAnchorEpoch, trustedCheckpointHeight, candidateSignerSetEpoch);
                return true;
            }

            if (!eq(previousCommitment, currentCommitment)
                    || candidateAnchorEpoch != anchorEpoch + 1
                    || trustedCheckpointHeight < checkpointHeight
                    || candidateSignerSetEpoch < signerSetEpoch) {
                return reject("CONTROL_PLANE_ROLLBACK");
            }
            if (eq(commitment, currentCommitment)) {
                return reject("CONTROL_PLANE_ROLLBACK");
            }
            if (candidateSignerSetEpoch > signerSetEpoch + 1) {
                return reject("GOVERNANCE_TRANSITION_INVALID");
            }
            if (candidateSignerSetEpoch == signerSetEpoch + 1 && !signerSetTransitionValid) {
                return reject("GOVERNANCE_TRANSITION_INVALID");
            }

            // Threshold authorization is deliberately insufficient after bootstrap. The adapter
            // must prove that the candidate checkpoint is a consensus-valid continuation of state
            // the client already trusts; otherwise the Freedom quorum would become chain consensus.
            if (!consensusContinuityValid) {
                return reject("CONTROL_PLANE_PROOF_INVALID");
            }

            commit(commitment, candidateAnchorEpoch, trustedCheckpointHeight, candidateSignerSetEpoch);
            return true;
        }

        private void commit(String commitment, long epoch, long height, long signerEpoch) {
            initialized = true;
            currentCommitment = commitment;
            anchorEpoch = epoch;
            checkpointHeight = height;
            signerSetEpoch = signerEpoch;
            lastAccepted = true;
            lastReason = null;
        }

        private boolean reject(String reason) {
            lastAccepted = false;
            lastReason = reason;
            return false;
        }

        public boolean configured() { return configured; }
        public boolean initialized() { return initialized; }
        public String currentCommitment() { return currentCommitment; }
        public long anchorEpoch() { return anchorEpoch; }
        public long checkpointHeight() { return checkpointHeight; }
        public long signerSetEpoch() { return signerSetEpoch; }
        public boolean lastAccepted() { return lastAccepted; }
        public String lastReason() { return lastReason; }
    }

    public static final class RekeyState {
        public enum Phase { STABLE, INIT_SENT, NEW_KEY_PENDING_ACK }

        private long keyEpoch = 1;
        private Long pendingEpoch;
        private Phase phase = Phase.STABLE;
        private boolean oldSendKeyErased;

        public void begin(long nextEpoch) {
            if (phase != Phase.STABLE) {
                throw new ProtocolViolation("SESSION_REKEY_FAILED", "rekey already in progress");
            }
            if (nextEpoch != keyEpoch + 1) {
                throw new ProtocolViolation("KEY_EPOCH_MISMATCH", "rekey epoch must increment exactly by one");
            }
            pendingEpoch = nextEpoch;
            phase = Phase.INIT_SENT;
            oldSendKeyErased = false;
        }

        public void receiveCommit(long nextEpoch) {
            if (phase != Phase.INIT_SENT || pendingEpoch == null || nextEpoch != pendingEpoch) {
                throw new ProtocolViolation("SESSION_REKEY_FAILED", "unexpected rekey commit");
            }
            phase = Phase.NEW_KEY_PENDING_ACK;
        }

        public void acknowledge(long nextEpoch) {
            if (phase != Phase.NEW_KEY_PENDING_ACK || pendingEpoch == null || nextEpoch != pendingEpoch) {
                throw new ProtocolViolation("SESSION_REKEY_FAILED", "unexpected rekey ack");
            }
            keyEpoch = nextEpoch;
            pendingEpoch = null;
            phase = Phase.STABLE;
            oldSendKeyErased = true;
        }

        public long keyEpoch() { return keyEpoch; }
        public Long pendingEpoch() { return pendingEpoch; }
        public Phase phase() { return phase; }
        public boolean oldSendKeyErased() { return oldSendKeyErased; }
        public boolean splitBrainFree() { return phase == Phase.STABLE && pendingEpoch == null; }
    }

    public static final class Model {
        public final RouteState route = new RouteState();
        public final PairwiseRecoveryState recovery = new PairwiseRecoveryState();
        public final BootstrapFreshnessState controlPlane = new BootstrapFreshnessState();
        public final MutationVerificationState mutation = new MutationVerificationState();
        public final NetworkAnchorState networkAnchor = new NetworkAnchorState();
        public final RekeyState rekey = new RekeyState();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
