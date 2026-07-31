# FermataX Package Identity

FermataX intentionally has three different identifiers. They are not interchangeable and must not
be renamed as cleanup.

| Identifier | Current value | Responsibility |
|---|---|---|
| Default application ID | `me.app.fermataX` | Mobile install identity before flavor suffixes |
| Auto application ID | `me.app.fermataX.auto` | Existing Android Auto install/update identity |
| Android namespace | `me.aap.fermata` | Legacy `R`, `BuildConfig`, manifest, and resource namespace |
| Core Java package | `me.aap.fermata` | Existing core API and persisted/reflected class names |
| Auto Java package | `me.app.fermatax.auto` | Projection/activity implementation package |

The uppercase `X` in the application ID is part of the distributed identity. The lowercase `x` in
the Auto Java package does not select the installed package. Source paths under
`fermata/src/auto/java/me/app/fermatax/auto` already match their Java declarations.

## Rules

1. Do not change `applicationId` or `.auto` suffix during refactoring.
2. Do not migrate the core namespace without a separate compatibility project covering resources,
   manifests, reflection, preferences, providers, Play signing, and update installation.
3. New core code belongs under `me.aap.fermata` until such a migration is explicitly approved.
4. New Auto host code belongs under `me.app.fermatax.auto`.
5. Dynamic features expose behavior through interfaces in `me.aap.fermata.addon` and must not be
   imported by the base application through their implementation packages.

`ArchitectureBoundaryTest` enforces the runtime identity and addon import boundaries.
