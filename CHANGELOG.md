# CHANGELOG.md

## UNRELEASED

### Added

* `delete`, which dissocs an entity from the db and removes all references to it

### Fixed

* Adding and creating databases with entities that have non-entities inside collections

## 1.0.2

### Added

* `add-report`, which returns a map with keys `:db`, containing the updated map,
  and `:entities`, which contains the set of lookup refs modified

* `pull-report`, which returns a map with keys `:data`, containing the result of
  the EQL query, and `:entities`, which contains the set of lookup refs
  queried in `:data`
