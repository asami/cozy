# Hygiene follow-up — 2026-10-28

## HYG-COZY-PUBLICATION-E3-CASE-COUNT

- Status: OPEN.
- Discovery: focused review of selected-artifact publication, 2026-10-28.
- Location: `src/test/scala/cozy/archive/Phase56NamespaceQualifiedCarPublicationSpec.scala`, existing E3 scenario.
- Evidence: Given says "six distinct ways"; the existing failures vector contains ten cases. This wording predates the selected-publication change.
- Category: specification prose; priority P3. Risk is misleading case-count wording; exercised cases and assertions are unaffected.
- Disposition: outside the selected registration fix. Later remove the fixed count or align the sentence with the existing cases; no product change or additional tests required.
