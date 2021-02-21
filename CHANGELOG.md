# CHANGELOG.md

## 1.0.2

### Added

* `add-report`, which returns a map with keys `:db`, containing the updated map,
  and `:entities`, which contains the set of lookup refs modified

* `pull-report`, which returns a map with keys `:data`, containing the result of
  the EQL query, and `:entities`, which contains the set of lookup refs
  queried in `:data`
