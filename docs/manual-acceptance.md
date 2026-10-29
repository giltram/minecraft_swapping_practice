# M1 physical input and visual acceptance

Status: pending. Automated lifecycle/fault checks do not replace these observations.
Use Minecraft 1.16.1 + Fabric/API and the production trainer jar, without the
automated smoke mod. Create a new disposable world with cheats enabled, stand on
dry solid ground, and run `/swaptrainer enable-test-world`.

1. Press O, choose Single Item / 10 tasks, then start. Use your normal number keys,
   scroll wheel and Swap Hands binding. Complete ten successes. Check that each
   input feels consistent and the original inventory/selected slot returns.
2. Repeat with Full Inventory. Confirm the target icons and allowed alternatives
   are understandable; the displayed target is one legal arrangement.
3. Rebind Swap Hands in vanilla Controls and repeat several tasks. Check that the
   old binding no longer swaps and the new binding does. Restore your preferred binding.
4. Hold Swap Hands while entering preparation/countdown, release it, then press
   after GO. Check that preparation can proceed after release and an early press
   does not perform a training swap or leave a queued surprise swap after GO.
5. During training, open inventory, press Esc, and switch to another application.
   Check interruption feedback and that preparation waits for a closed screen,
   focused game and released Swap Hands. Try opening inventory during success
   feedback and keeping it open into the next task.
6. Try your normal and smallest usable window sizes and GUI scales. Check target
   icons, hotbar/offhand labels, counters and alternative/NBT hints for clipping
   or overlap. Record the resolution, GUI scale and profile if text is unreadable.
7. Use O while training, change mode/profile/length, and start again. Check that
   restoration finishes before another training session can start.

Record pass/fail, key bindings, resolution/GUI scale and any reproducible problem.
Do not mark this checklist complete based on compilation or automated screenshots.
