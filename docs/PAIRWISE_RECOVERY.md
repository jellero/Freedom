# Freedom — Pairwise Recovery

Status: **canonical / normative design rules**.

Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).
Normative baseline: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Identity model: [`IDENTITY_MODEL.md`](IDENTITY_MODEL.md).

## 1. Problem

Recovering the user's RootIdentity does not reconstruct pairwise secrets automatically.

A backup source may be untrusted and may intentionally serve an **older but cryptographically valid** `PairwiseRecoveryBundle`.

After total loss of all devices, local highest-seen state is gone. Therefore rollback detection needs either:

```text
A. a surviving authorized device
or
B. an independently recoverable monotonic recovery anchor
```

Without either one, Freedom MUST NOT claim that a restored pairwise backup is known to be the latest state.

## 2. PairwiseRecoveryBundle

Canonical object: `pairwise-recovery-bundle`.

The bundle is ciphertext and contains no public social graph.

Normative fields include:

```text
bundle_id
state_epoch
backup_generation
previous_bundle_hash?
created_at_height
recovery_key_epoch
contacts_metadata_ciphertext
pairwise_state_ciphertext
state_commitment
integrity
```

`backup_generation` is monotonic for one recovery lineage.

`previous_bundle_hash` creates an authenticated backup chain when a predecessor exists.

`bundle_id` and `state_commitment` use fixed domains from `spec/crypto-domains.txt`.

## 3. PairwiseRecoveryAnchor

Users/deployments that want rollback-detectable recovery after loss of **all** devices publish/retain a small monotonic anchor independent from the untrusted bundle source.

Canonical object: `pairwise-recovery-anchor`.

```text
PairwiseRecoveryAnchor {
    root_control_commitment
    anchor_epoch
    latest_backup_generation
    latest_bundle_hash
    latest_state_commitment
    recovery_key_epoch
    updated_at_height
    authorization_proof
}
```

The anchor contains no contacts, peer identities, routes or plaintext pairwise state.

It does reveal that the same opaque recovery lineage updated its backup state at particular control-plane times. This correlation trade-off is explicit.

## 4. Authorization

Updating the anchor is security-sensitive.

An update MUST be authorized by a recovery-state authority bound to the current verified user recovery lineage and MUST satisfy:

```text
anchor_epoch          = previous.anchor_epoch + 1
backup_generation     > previous.latest_backup_generation
root_control_commitment unchanged for the lineage
recovery_key_epoch    >= previous.recovery_key_epoch
```

The concrete authorization proof is versioned and domain-separated.

A raw RPC response or transaction hash is not proof that the anchor update succeeded.

## 5. Restore with surviving device

Preferred path when another authorized device survives:

```text
new device
 -> authenticate surviving device
 -> transfer latest pairwise state directly
 -> compare/advance recovery anchor when enabled
 -> establish fresh pairwise/session state
```

The surviving device is a stronger freshness oracle than an arbitrary backup mirror.

## 6. Restore with backup + anchor

After total device loss:

```text
recover RootIdentity / recovery state
 -> obtain verified PairwiseRecoveryAnchor
 -> obtain candidate encrypted bundle from any source
 -> hash candidate canonical bytes
 -> require bundle.generation == anchor.latest_backup_generation
 -> require bundle hash == anchor.latest_bundle_hash
 -> require state commitment == anchor.latest_state_commitment
 -> decrypt/validate bundle
 -> re-authenticate peers
 -> rotate future rendezvous/recovery/session state
```

Mismatch is `PAIRWISE_BACKUP_ROLLBACK_OR_MISMATCH` and fails closed.

## 7. Restore without anchor

An anchor is optional because publishing backup-update timing is a privacy trade-off.

If all devices are lost and the user has only an encrypted bundle but no independently recoverable freshness anchor:

- cryptographic integrity of that bundle can be verified;
- its freshness relative to unknown later backups cannot be proven;
- UI/diagnostics MUST NOT call it `LATEST_VERIFIED_BACKUP`;
- after restore, peers are re-authenticated and future secrets are rotated;
- high-assurance deployments SHOULD require an anchor or a surviving-device transfer.

## 8. Post-restore rotation

A restored historical pairwise secret MUST NOT remain indefinite future authority.

For each recovered contact, after successful peer re-authentication:

```text
old recovered PairSecret / PairRendezvousSecret
 -> authenticate continuity
 -> derive fresh pairwise recovery/rendezvous generation
 -> establish fresh session keys
 -> retire old future-write authority
```

Old backup material may remain sufficient to decrypt the historical backup itself; it must not authorize future rendezvous generations indefinitely.

## 9. RecoveryStateKey rotation

`RecoveryStateKey` has its own epoch.

Rotate it when:

- compromise is suspected;
- a root-compromise recovery completes;
- policy requires periodic rotation;
- backup storage exposure warrants replacement.

A new recovery key epoch does not silently make an older bundle current.

## 10. Storage / availability

Bundle bytes may live on user-chosen storage, private mirror, removable media, encrypted cloud storage or other untrusted sources.

The source is availability infrastructure, not trust.

The protocol does not require the Freedom control-plane to store the full pairwise bundle.

Only the small optional anchor is control-plane state.

## 11. Tests

Required scenarios:

- latest bundle + latest anchor -> accept;
- old valid bundle + newer anchor -> reject;
- tampered bundle -> reject;
- rollback anchor -> reject by verified highest-seen/checkpoint rules;
- missing anchor after total device loss -> integrity-only recovery state, no freshness claim;
- surviving-device transfer newer than mirror bundle -> surviving state wins and anchor advances;
- root-compromise recovery -> RecoveryStateKey rotation;
- restored pairwise state -> peer re-authentication + future rendezvous rotation;
- malicious mirror with correct old ciphertext -> cannot claim latest verified state.

## 12. Invariants

- pairwise state is never reconstructed from public social-graph data;
- backup source is untrusted;
- integrity != freshness;
- total-device-loss freshness requires surviving trusted state or independent monotonic anchor;
- anchor contains no contact list/plaintext;
- rollback mismatch fails closed when anchor profile is enabled;
- recovered historical secrets are not indefinite future authority.
