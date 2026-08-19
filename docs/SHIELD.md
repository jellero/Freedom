# Freedom — Shield Circuit Architecture

Status: **canonical design target / pre-implementation normative boundary**.

Normative baseline: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).
Schema: [`../spec/freedom.cddl`](../spec/freedom.cddl).

Freedom Shield è la modalità multi-hop/path-protection. Finché i gate sotto non sono implementati/testati, `SHIELDED` è design target e non garanzia production.

## 1. Obiettivo

```text
Alice -> Hop A -> Hop B -> Bob
```

Target:

- Hop A vede origine adiacente ma non destinazione finale direttamente utilizzabile;
- Hop B vede destinazione adiacente ma non direttamente l'IP originale;
- nessun hop riceve le session keys Alice/Bob;
- single-hop compromise non concede impersonation/plaintext;
- route identity separata dalla pairwise contact identity.

Non promettere anonimato contro collusione completa/global observer.

## 2. Circuit protocol

Un path `SHIELDED` richiede:

```text
circuit setup autenticato
per-hop independent keys
layered forwarding
temporary circuit identity
bounded lifetime/rekey
explicit teardown/rebuild
no silent direct fallback
```

Concatenare proxy TCP non basta.

La costruzione concreta deve essere standard/reviewata o formalmente reviewata prima del claim production.

## 3. Per-hop keys

Ogni hop ha materiale distinto e indipendente da RootIdentity, DeviceKey, pairwise conversation keys e Gateway application keys.

Un hop compromesso non ottiene automaticamente le keys degli altri hop.

## 4. Layered forwarding

```text
payload
 -> wrap for last hop
 -> wrap for previous hop
 -> ...
 -> first hop
```

Ogni hop rimuove soltanto il proprio layer e apprende soltanto il next-hop token necessario.

Header non contiene RootIdentity/DeviceRecordCommitment/pairwise alias quando capability temporanee bastano.

## 5. RelayDescriptor / provenance

Schema canonico in CDDL:

```text
relay-descriptor
provenance-attestation
```

Self-declared operator/geography/provider metadata **non è** prova di indipendenza.

## 6. Provenance classes

Il selector distingue:

```text
SELF_DECLARED
OBSERVED
ATTESTED_PROVENANCE
```

`ATTESTED_PROVENANCE` significa soltanto che uno o più issuer hanno firmato uno specifico claim bounded/expiring sul relay.

Una singola attestation **non dimostra** che due relay abbiano operatori realmente indipendenti.

## 7. Provenance issuers

Gli issuer devono essere identificabili per chiave/issuer class e avere scope limitato.

Possibili classi future:

```text
NETWORK_OBSERVER
MANAGED_PROVIDER
COMMUNITY_DIRECTORY
ORGANIZATION_ADMIN
INDEPENDENT_AUDITOR
```

Una attestation include almeno subject relay, claim type/value, observation height, expiry e firma domain-separated.

Il selector considera anche **issuer diversity**: tre attestazioni provenienti dallo stesso operator/custody domain non equivalgono automaticamente a tre osservatori indipendenti.

## 8. Diversity

Preferire quando possibile:

- ASN/provider differenti osservati;
- endpoint/netblock differenti;
- directory/source differenti;
- provenance issuer differenti;
- managed/community/private class mix coerente con policy;
- transport families differenti quando utile.

`N relay IDs != N independent operators` rimane un'invariante.

Operator independence è un trust signal probabilistico, non una proprietà matematica derivabile dal solo descriptor.

## 9. Sybil/eclipse

Attacker può creare relay IDs/endpoint/capacity hint differenti.

Mitigazioni:

- relay keys persistenti;
- multi-source discovery;
- observed network metadata;
- issuer-diverse provenance attestations;
- controlled randomization;
- evitare circuiti interamente da una singola directory/provenance quando alternative esistono;
- rebuild su anomalie;
- dedicated Sybil/eclipse simulation.

## 10. Circuit lifecycle

```text
BUILDING
ACTIVE
ROTATING
DEGRADED
CLOSING
CLOSED
```

Circuit/key lifetime bounded. Failure di un hop provoca rebuild/route change senza ridefinire peer identity.

## 11. Gateway Shield

```text
Client -> Hop A -> Egress B -> Internet
```

Egress resta trust boundary separata. Shield non trasforma traffico Internet plaintext in Freedom E2EE.

## 12. Claim gate

UI può mostrare `SHIELDED` soltanto se:

- circuit setup completato;
- per-hop keys attive;
- layered forwarding verificato;
- requested Shield policy soddisfatta;
- no silent fallback direct;
- path state corrente/non expired.

## 13. Test gate

Prima della release Shield:

- circuit setup vectors;
- per-hop key separation/rotation;
- layered forwarding;
- single-hop compromise;
- all-hop collusion threat test/model;
- replay/reorder;
- circuit rebuild;
- provenance spoofing;
- issuer-collusion/issuer-duplication cases;
- relay Sybil/eclipsing;
- packet-size/timing metadata review;
- route failure during Communication session;
- Gateway egress separation;
- external security review.
