package dev.freedom.core;

public final class CoreSelfTest {
    public static void main(String[] args) {
        testRouteRecoveryKeepsIdentity();
        testPairwiseRollback();
        testBootstrapFreshness();
        testVerifiedMutation();
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
        check(mutation.committedVersion() == 2, "committed state version not retained");
        check(!mutation.verify(true, true, true, true, 1), "resulting-state rollback accepted");
        check("CONTROL_PLANE_ROLLBACK".equals(mutation.lastReason()), "wrong mutation rollback class");
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
