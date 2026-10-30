# M2 configuration simplification — current plan and ownership

The reviewed first design was too elaborate. User-requested revision:

1. Slot click opens the same full searchable catalogue, supporting any number of
   selected items. Show every selected icon/count; mark selected catalogue icons.
   Quantity edits stay inline. Explicit final deselection becomes EMPTY. Remove
   single-item proposal/confirmation pages and exchange-position UI/state.
2. ProfileEditorSession alone owns actual allowed items and raw count/SNBT. Search
   and active icon are view state. Opening/additive edits preserve all legacy
   OPTIONAL/FLEXIBLE/custom NBT. Confirm only destructive custom/special removal.
   Main Save alone uses existing repository Edit/atomic/conflict contracts.
3. Main page is name/hotbar/offhand/Reset/Save/Cancel. REQUIRED with1 or N allowed
   items has one consistent selected-count presentation. Advanced rules remain a
   secondary picker footer entry, not a separate main-slot navigation hierarchy.
4. O uses one Training task count field and explicit0=unlimited hint. Blank/bad/
   overflow text cannot start or overwrite the accepted option. Hide only routine
   IDLE/Ready catalogue feedback, retaining actual errors/other feedback.
5. Update focused tests/current docs; run requested offline test/build/boundaries
   and isolated remapped production-client multi-selection/save/cancel/NBT/count/
   resize/restart checks. Keep programmatic evidence distinct from human acceptance.

[Current acceptance](m2-multiselect-acceptance.md). Prior design/evidence remains
historical in [m2-usability-acceptance.md](m2-usability-acceptance.md).

## Copy and quantity-row follow-up

Use the training explanation itself above the input: “Number of successful
training tasks (0=unlimited)”; remove the duplicate title/manual-stop clause.
The picker omits catalogue instructions/count and uses “Hotbar N · count”. A
left-aligned input sits below “Item quantity: name”, with a labelled Delete button
beside it; remove the decorative multiplication sign. Keep Advanced as a secondary
entry because OPTIONAL/FLEXIBLE and full NBT/matching remain editable contracts;
its extra entry is the small cost of retaining that capability. No draft, domain,
persistence or protocol changes are needed. Verify existing tests and actual
rendered small-window/locale controls rather than adding copy-mirroring unit tests.
