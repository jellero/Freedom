package dev.freedom.core;

public final class CoreSelfTest {
    public static void main(String[] args) {
        testRouteRecoveryKeepsIdentity();
        testPairwiseRollback();
        testBootstrapFreshness();
        testVerifiedMutation();
        testNetworkAnchorLifecycle();
        testRekeyLostAck();
        System.out.println("Freedom core self-tests passed.");
    }

    private static void testRouteRecoveryKeepsIdentity() {
        FreedomCore.RouteState route = new FreedomCore.RouteState();
        route.registerRelay("relay_a");
        route.registerRelay("relay_b");
        route.connect("alice", "bob", "relay_a");
        route.block("relay_a");
        check(route.routeBlocked(), "blocked active route not detected");
        check(route.recoverRoute(), "alternate relay not selected");
        check("relay_b".equals(route.route()), "wrong alternate relay");
        check(route.peerIdentityUnchanged(), "route change modified peer identity");
        check(route.mailboxWrites() == 0, "route recovery wrote mailbox state");
    }

    private static void testPairwiseRollback() {
        FreedomCore.PairwiseRecoveryState recovery = new FreedomCore.PairwiseRecoveryState();
        recovery.restoreRootIdentity();
        recovery.setVerifiedAnchor(9);
        check(!recovery.evaluateBackup(7, true, false, false), "stale bundle accepted");
        check("PAIRWISE_BACKUP_ROLLBACK_OR_MISMATCH".equals(recovery.lastReason()), "wrong rollback error");
        check(recovery.evaluateBackup(9, true, true, true), "latest bundle rejected");
        recovery.rotateFutureState();
        check(recovery.futureRotated(), "future pairwise state not rotated");
        check(!recovery.oldBackupFutureAuthority(), "old backup retained future authority");
    }

    private static void testBootstrapFreshness() {
        FreedomCore.BootstrapFreshnessState cp = new FreedomCore.BootstrapFreshnessState();
        cp.setFloor(100);
        check(!cp.verifyCheckpoint(99, true), "stale checkpoint accepted");
        check("BOOTSTRAP_STATE_TOO_OLD".equals(cp.lastReason()), "wrong stale checkpoint error");
        check(cp.verifyCheckpoint(101, true), "fresh checkpoint rejected");
        check(cp.verifiedHeight() == 101, "verified height not retained");
        check(!cp.verifyCheckpoint(100, true), "highest-seen rollback accepted");
        check("CONTROL_PLANE_ROLLBACK".equals(cp.lastReason()), "wrong rollback error");
    }

    private static void testVerifiedMutation() {
        FreedomCore.MutationVerificationState mutation = new FreedomCore.MutationVerificationState();
        check(!mutation.verify(true, false, true, true, 1), "failed execution accepted");
        check("CONTROL_PLANE_EXECUTION_FAILED".equals(mutation.lastReason()), "wrong execution failure class");
        check(!mutation.verify(true, true, true, false, 1), "state mismatch accepted");
        check("CONTROL_PLANE_STATE_MISMATCH".equals(mutation.lastReason()), "wrong state mismatch class");
        check(mutation.verify(true, true, true, true, 2), "verified mutation rejected");
        check(mutation.hasCommittedState(), "verified mutation did not create committed state");
        check(mutation.committedVersion() == 2, "committed state version not retained");
        check(!mutation.verify(true, true, true, true, 1), "resulting-state rollback accepted");
        check("CONTROL_PLANE_ROLLBACK".equals(mutation.lastReason()), "wrong mutation rollback class");
        check(mutation.hasCommittedState(), "failed follow-up mutation erased prior committed state");
        check(mutation.committedVersion() == 2, "failed follow-up mutation rolled back committed version");
    }

    private static void testNetworkAnchorLifecycle() {
        FreedomCore.NetworkAnchorState anchor = new FreedomCore.NetworkAnchorState();
        anchor.configure(
                "freedom-testnet",
                "NEAR",
                "testnet",
                "NEAR-NEP25-PRE-SPICE-BORSH-V1",
                1,
                "anchor-1",
                100);

        check(!anchor.acceptCandidate(
                "freedom-testnet", "NEAR", "testnet", "NEAR-NEP25-PRE-SPICE-BORSH-V1", 1,
                "attacker-anchor", null, 1, 120, 1, 100, 110,
                true, true, true, false), "un-pinned bootstrap anchor accepted");
        check("NETWORK_ANCHOR_INVALID".equals(anchor.lastReason()), "wrong bootstrap pin error");
        check(!anchor.initialized(), "rejected bootstrap mutated anchor state");

        check(anchor.acceptCandidate(
                "freedom-testnet", "NEAR", "testnet", "NEAR-NEP25-PRE-SPICE-BORSH-V1", 1,
                "anchor-1", null, 1, 120, 1, 100, 110,
                true, true, true, false), "pinned bootstrap anchor rejected");
        check(anchor.initialized(), "bootstrap anchor did not initialize state");
        check("anchor-1".equals(anchor.currentCommitment()), "wrong initial anchor commitment");

        check(!anchor.acceptCandidate(
                "freedom-testnet", "NEAR", "testnet", "NEAR-NEP25-PRE-SPICE-BORSH-V1", 1,
                "anchor-2", "anchor-1", 2, 140, 1, 120, 130,
                true, true, true, false), "threshold-authorized rotation bypassed chain consensus");
        check("CONTROL_PLANE_PROOF_INVALID".equals(anchor.lastReason()), "wrong continuity failure class");
        check("anchor-1".equals(anchor.currentCommitment()), "failed continuity check replaced trusted anchor");
        check(anchor.checkpointHeight() == 120, "failed continuity check changed checkpoint height");

        check(!anchor.acceptCandidate(
                "freedom-testnet", "NEAR", "testnet", "NEAR-NEP25-PRE-SPICE-BORSH-V1", 1,
                "anchor-2", "anchor-1", 2, 140, 2, 120, 130,
                true, true, false, true), "signer-set change without transition accepted");
        check("GOVERNANCE_TRANSITION_INVALID".equals(anchor.lastReason()), "wrong signer transition error");
        check(anchor.signerSetEpoch() == 1, "rejected signer transition mutated signer epoch");

        check(anchor.acceptCandidate(
                "freedom-testnet", "NEAR", "testnet", "NEAR-NEP25-PRE-SPICE-BORSH-V1", 1,
                "anchor-2", "anchor-1", 2, 140, 2, 120, 130,
                true, true, true, true), "valid NetworkAnchor rotation rejected");
        check("anchor-2".equals(anchor.currentCommitment()), "valid rotation did not update commitment");
        check(anchor.anchorEpoch() == 2, "valid rotation did not update anchor epoch");
        check(anchor.checkpointHeight() == 140, "valid rotation did not update checkpoint height");
        check(anchor.signerSetEpoch() == 2, "valid rotation did not update signer epoch");

        check(!anchor.acceptCandidate(
                "freedom-testnet", "NEAR", "testnet", "NEAR-NEP25-PRE-SPICE-BORSH-V1", 1,
                "anchor-1", null, 1, 120, 1, 100, 110,
                true, true, true, true), "old NetworkAnchor replay accepted");
        check("CONTROL_PLANE_ROLLBACK".equals(anchor.lastReason()), "wrong anchor rollback error");
        check("anchor-2".equals(anchor.currentCommitment()), "rollback attempt changed trusted anchor");
    }

    private static void testRekeyLostAck() {
        FreedomCore.RekeyState rekey = new FreedomCore.RekeyState();
        rekey.begin(2);
        rekey.receiveCommit(2);
        check(rekey.phase() == FreedomCore.RekeyState.Phase.NEW_KEY_PENDING_ACK, "commit did not enter pending ack");
        // Lost Ack leaves the state pending; retransmitted/confirmed Ack then completes the same epoch.
        rekey.acknowledge(2);
        check(rekey.keyEpoch() == 2, "rekey epoch not advanced");
        check(rekey.splitBrainFree(), "rekey left split-brain state");
        check(rekey.oldSendKeyErased(), "old send key not erased after confirmation");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
