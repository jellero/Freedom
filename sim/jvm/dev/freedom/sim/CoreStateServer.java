package dev.freedom.sim;

import dev.freedom.core.FreedomCore;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny line-protocol bridge used by sim/simctl.py and L3 differential harnesses.
 * Python owns orchestration/virtual time; security transition state lives in FreedomCore.
 */
public final class CoreStateServer {
    private final FreedomCore.Model model = new FreedomCore.Model();
    private final Map<String, Long> natGeneration = new LinkedHashMap<>();

    public static void main(String[] args) throws Exception {
        new CoreStateServer().run();
    }

    private void run() throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) continue;
            try {
                String response = handle(line.split("\\t", -1));
                out.println("OK\t" + response);
            } catch (FreedomCore.ProtocolViolation ex) {
                out.println("ERR\t" + ex.code() + "\t" + sanitize(ex.getMessage()));
            } catch (RuntimeException ex) {
                out.println("ERR\tMALFORMED\t" + sanitize(ex.getMessage()));
            }
        }
    }

    private String handle(String[] p) {
        String op = p[0];
        switch (op) {
            case "NODE" -> {
                require(p, 2);
                natGeneration.putIfAbsent(p[1], 0L);
                if (p[1].startsWith("relay_")) model.route.registerRelay(p[1]);
                return "node=" + p[1];
            }
            case "CONNECT" -> {
                require(p, 4);
                model.route.connect(p[1], p[2], p[3]);
                return snapshot();
            }
            case "BLOCK" -> { require(p, 2); model.route.block(p[1]); return snapshot(); }
            case "UNBLOCK" -> { require(p, 2); model.route.unblock(p[1]); return snapshot(); }
            case "NAT_REBIND" -> {
                require(p, 2);
                long next = natGeneration.getOrDefault(p[1], 0L) + 1;
                natGeneration.put(p[1], next);
                return "nat_generation=" + next + "\troute_blocked=" + model.route.routeBlocked();
            }
            case "RECOVER_ROUTE" -> {
                boolean recovered = model.route.recoverRoute();
                return "recovered=" + recovered + "\t" + snapshot();
            }
            case "RESTORE_ROOT" -> { model.recovery.restoreRootIdentity(); return snapshot(); }
            case "SET_RECOVERY_ANCHOR" -> {
                require(p, 2);
                model.recovery.setVerifiedAnchor(Long.parseLong(p[1]));
                return snapshot();
            }
            case "EVALUATE_BACKUP" -> {
                require(p, 5);
                boolean accepted = model.recovery.evaluateBackup(
                        Long.parseLong(p[1]), bool(p[2]), bool(p[3]), bool(p[4]));
                return "accepted=" + accepted + "\t" + snapshot();
            }
            case "ROTATE_PAIRWISE" -> { model.recovery.rotateFutureState(); return snapshot(); }
            case "SET_BOOTSTRAP_FLOOR" -> {
                require(p, 2);
                model.controlPlane.setFloor(Long.parseLong(p[1]));
                return snapshot();
            }
            case "VERIFY_CHECKPOINT" -> {
                require(p, 3);
                boolean accepted = model.controlPlane.verifyCheckpoint(Long.parseLong(p[1]), bool(p[2]));
                return "accepted=" + accepted + "\t" + snapshot();
            }
            case "VERIFY_MUTATION" -> {
                require(p, 6);
                boolean accepted = model.mutation.verify(
                        bool(p[1]), bool(p[2]), bool(p[3]), bool(p[4]), Long.parseLong(p[5]));
                return "accepted=" + accepted + "\t" + snapshot();
            }
            case "BEGIN_REKEY" -> { require(p, 2); model.rekey.begin(Long.parseLong(p[1])); return snapshot(); }
            case "REKEY_COMMIT" -> { require(p, 2); model.rekey.receiveCommit(Long.parseLong(p[1])); return snapshot(); }
            case "REKEY_ACK" -> { require(p, 2); model.rekey.acknowledge(Long.parseLong(p[1])); return snapshot(); }
            case "SNAPSHOT" -> { return snapshot(); }
            default -> throw new IllegalArgumentException("unknown operation " + op);
        }
    }

    private String snapshot() {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("route", nullString(model.route.route()));
        s.put("route_recovered", Boolean.toString(model.route.recovered()));
        s.put("peer_identity_unchanged", Boolean.toString(model.route.peerIdentityUnchanged()));
        s.put("mailbox_writes", Long.toString(model.route.mailboxWrites()));
        s.put("root_restored", Boolean.toString(model.recovery.rootRestored()));
        s.put("anchor_generation", nullString(model.recovery.anchorGeneration()));
        s.put("accepted_generation", nullString(model.recovery.acceptedGeneration()));
        s.put("backup_last_rejected", Boolean.toString(model.recovery.lastRejected()));
        s.put("backup_last_reason", nullString(model.recovery.lastReason()));
        s.put("future_rotated", Boolean.toString(model.recovery.futureRotated()));
        s.put("old_backup_future_authority", Boolean.toString(model.recovery.oldBackupFutureAuthority()));
        s.put("bootstrap_floor", nullString(model.controlPlane.minimumHeight()));
        s.put("verified_height", nullString(model.controlPlane.verifiedHeight()));
        s.put("control_last_rejected", Boolean.toString(model.controlPlane.lastRejected()));
        s.put("control_last_reason", nullString(model.controlPlane.lastReason()));
        s.put("mutation_committed", Boolean.toString(model.mutation.committed()));
        s.put("mutation_last_reason", nullString(model.mutation.lastReason()));
        s.put("mutation_committed_version", Long.toString(model.mutation.committedVersion()));
        s.put("key_epoch", Long.toString(model.rekey.keyEpoch()));
        s.put("pending_key_epoch", nullString(model.rekey.pendingEpoch()));
        s.put("rekey_phase", model.rekey.phase().name());
        s.put("old_send_key_erased", Boolean.toString(model.rekey.oldSendKeyErased()));
        s.put("no_split_brain", Boolean.toString(model.rekey.splitBrainFree()));
        return join(s);
    }

    private static String join(Map<String, String> fields) {
        StringBuilder b = new StringBuilder();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (b.length() > 0) b.append('\t');
            b.append(e.getKey()).append('=').append(sanitize(e.getValue()));
        }
        return b.toString();
    }

    private static boolean bool(String value) { return "true".equalsIgnoreCase(value); }
    private static String nullString(Object value) { return value == null ? "null" : value.toString(); }
    private static String sanitize(String value) { return value == null ? "null" : value.replace('\t', ' ').replace('\n', ' '); }
    private static void require(String[] parts, int n) {
        if (parts.length < n) throw new IllegalArgumentException("missing command arguments");
    }
}
