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
            return eq(from, originalFrom) && eq(to, originalTo);
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
        public final RekeyState rekey = new RekeyState();
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
