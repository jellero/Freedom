# Freedom — Shield Circuit Architecture

Status: **canonical design target / pre-implementation normative boundary**

Freedom Shield è la modalità di path protection/multi-hop di Freedom. Finché le proprietà sotto non sono implementate e testate, `SHIELDED` è un design target e non una garanzia production.

Normative baseline: [`SECURITY_INVARIANTS.md`](SECURITY_INVARIANTS.md).

## 1. Obiettivo

```text
Alice -> Hop A -> Hop B -> Bob
```

Target:

- Hop A vede il lato origine adiacente ma non la destinazione finale in forma direttamente utilizzabile;
- Hop B vede il lato destinazione adiacente ma non direttamente l'IP originale;
- nessun hop riceve session keys E2EE Alice/Bob;
- compromissione di un singolo hop non consente impersonation o plaintext;
- route identity resta separata dalla pairwise contact identity.

Non promettere anonimato contro collusione completa o osservatore globale.

## 2. Circuit setup

Un circuito usa materiale per-hop separato:

```text
ShieldCircuit {
    circuit_id
    circuit_epoch
    hop_descriptors[]
    hop_key_epochs[]
    expires_at
    policy
}
```

`circuit_id` è temporaneo e non è un global identity.

La costruzione concreta deve usare un protocollo standard/reviewato o una composizione formalmente reviewata; concatenare semplici proxy TCP non è sufficiente.

## 3. Hop keys

Per ogni hop il client deriva/negozia una chiave distinta:

```text
K_A
K_B
...
```

Le chiavi per-hop sono indipendenti da:

- RootIdentity;
- DeviceKey;
- pairwise conversation keys;
- Gateway application keys.

Un relay compromesso non ottiene le chiavi degli altri hop.

## 4. Layered forwarding

Concettualmente:

```text
payload_to_Bob
 -> wrap for Hop B
 -> wrap for Hop A
 -> send to Hop A
```

Ogni hop rimuove soltanto il proprio layer e apprende il next-hop token necessario.

Header per-hop non contiene RootIdentity, DeviceRecordCommitment o pairwise alias quando un capability token basta.

## 5. ShieldHopDescriptor

```text
ShieldHopDescriptor {
    relay_public_key
    relay_descriptor_hash
    transport
    endpoint
    provenance_class
    observed_network_metadata?
    capabilities
    expires_at
    signature
}
```

Self-declared operator/geography/provider metadata non è una prova di indipendenza.

## 6. Diversity

Il selector distingue:

```text
SELF_DECLARED
OBSERVED
VERIFIED_PROVENANCE
```

Per un circuito multi-hop non deve considerare automaticamente due relay IDs come due operatori indipendenti.

Preferire, quando disponibile:

- provider/ASN differenti;
- provenance differente;
- operatori verificabilmente differenti;
- regioni differenti coerenti con policy;
- transport families differenti quando utile.

## 7. Sybil / eclipse

Un attacker può creare molti relay IDs.

Mitigazioni:

- relay keys persistenti ma non identity user-facing;
- provenance/source diversity;
- observed ASN/provider metadata;
- reputation/capability bounded senza social graph;
- evitare selezione interamente da una singola directory/provenance;
- randomizzazione controllata;
- circuit rebuild su anomalie.

Nessuna metrica singola dimostra indipendenza reale.

## 8. Circuit lifecycle

```text
BUILDING
ACTIVE
ROTATING
DEGRADED
CLOSING
CLOSED
```

Circuiti hanno TTL/key lifetime bounded.

Rotation può avvenire per:

- expiry;
- relay failure;
- policy change;
- filtering evidence;
- provenance/diversity improvement;
- rekey requirement.

## 9. Failure

Se un hop fallisce:

```text
current circuit fails
 -> authenticated endpoint session remains the identity context
 -> build alternate circuit/path
 -> resume/re-establish according to session protocol
```

Il path non ridefinisce chi è il peer.

## 10. Gateway Shield

Per Gateway:

```text
Client -> Hop A -> Egress B -> Internet
```

l'egress resta una trust boundary distinta. Shield separa osservazioni tra hop ma non trasforma traffico Internet plaintext in Freedom E2EE.

## 11. Claim gate

La UI può mostrare `SHIELDED` solo quando:

- il circuit setup definito è completato;
- hop keys separate sono attive;
- layered forwarding è verificato;
- il path soddisfa la policy Shield richiesta;
- non è avvenuto silent fallback direct.

Prima di questo gate usare label di sviluppo/concept, non claim production.

## 12. Test gate

Prima della release Shield:

- single-hop compromise test;
- two-hop collusion model;
- replay/reorder per circuit frame;
- circuit rebuild;
- per-hop key rotation;
- relay Sybil/eclipsing simulation;
- provenance spoofing;
- packet-size/timing metadata review;
- route failure during active Communication session;
- Gateway egress separation tests;
- external security review del circuit protocol.
