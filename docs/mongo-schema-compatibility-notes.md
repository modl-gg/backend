# Mongo Schema Compatibility Notes

This refactor preserves all current Mongo collection names and persisted field names to avoid breaking existing data. The items below are the main schema inconsistencies and cleanup candidates that should be handled in a later dedicated migration.

## Field-name semantics preserved for compatibility

- `Server.customDomain`
  - Persisted field: `customDomain`
  - Current meaning in code: the server subdomain under the main app domain, not a true custom domain override.
  - Cleanup candidate: rename the Java property and/or persisted field to reflect subdomain semantics explicitly.

- `Server.customDomainOverride`
  - Persisted field: `customDomainOverride`
  - Current meaning: the real external custom domain.
  - Cleanup candidate: standardize naming so `customDomain` and override/subdomain concepts are not inverted.

## Collection naming inconsistencies preserved

- `staffs`
  - Used by `CollectionName.STAFF`
  - Cleanup candidate: migrate to `staff` or another singular/plural convention used consistently across the project.

- `staffroles`
  - Used by `CollectionName.STAFF_ROLES`
  - Cleanup candidate: migrate to a clearer collection name such as `staff_roles`.

- `knowledgebasecategories`
- `knowledgebasearticles`
- `homepagecards`
- `system_config`
- `system_logs`
- `systemprompts`
- `migrations`
  - Cleanup candidate: standardize collection naming to one convention (`snake_case` or another single policy).

## Implicit collection mappings worth making explicit later

The following entities currently rely on implicit `@Document` collection resolution in some places while other code passes explicit collection names externally:

- `Ticket`
- `Staff`
- `StaffRole`

Cleanup candidate: add explicit collection declarations on the entity types so collection mapping is defined in one place.

## Dynamic document sections preserved for compatibility

These areas still persist flexible map-shaped data and should be migrated carefully in a later schema pass if full type safety is desired:

- `Settings.data`
- `Player.data`
- `Ticket.formData`
- `Ticket.data`
- `Punishment.data`
- domain/status sub-maps inside some settings documents

Cleanup candidate: replace with typed embedded models, constrained value objects, or versioned schema migrations where the shape is stable.

## Embedded document naming/history to review later

- `Punishment.typeOrdinal`
  - Persisted as `typeOrdinal`
  - Cleanup candidate: consider a more explicit stable identifier if ordinal-based meaning becomes harder to evolve.

- 2FA/session-related fields on `Staff`
  - Stored as multiple top-level fields on the staff document.
  - Cleanup candidate: group into a typed embedded subdocument if future session semantics grow further.
