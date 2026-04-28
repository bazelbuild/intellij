# Debugging C/C++ Bazel targets

> **TL;DR** — The plugin now warns when it cannot prove that a C/C++
> target was built with debug info. If `bazel run --compilation_mode=dbg
> //your:target` already gives you a working debug session on the
> command line, you are very likely in the "expected" path and can
> dismiss the warning. The warning exists to help users whose build
> setup silently drops debug info.

## Why this check exists

Starting with this plugin version, debugging a C/C++ run configuration
goes through an `aquery`-based inspection of the compile and link
actions Bazel will execute. If the recorded arguments do not contain
the flags a debugger needs, the plugin shows a **Debug Info Warning**
dialog before launching the session.

We assume command-line `bazel` debug works for most users. The check
helps the *opposite* group — users whose toolchain, `--config`, or
`copts` settings drop `-g`, force `-O2`, strip symbols, or omit
macOS-specific link options. Without this check those users would hit
a debugger that loads but never stops at breakpoints, with no clear
hint why.

## What the plugin inspects

For every compile action under the target the plugin runs (per
[DebugInfoCheck.kt](../../clwb/src/com/google/idea/blaze/clwb/run/DebugInfoCheck.kt)):

| Compiler | Required argument | Considered missing if |
|----------|-------------------|------------------------|
| GCC / Clang | `-g`, `-g1`/`-g2`/`-g3`, `-ggdb`, `-glldb`, `-gdwarf-*` | last `-g*` is `-g0`, or none present |
| MSVC | `/Z7`, `/Zi`, `/ZI` | no `/Z*` flag present |
| clang-cl | any of the above (`-g*`, `/clang:-g*`, `/Z*`) | none present |

For link actions only one case matters today:

| Platform + compiler | Required link option | Why |
|---------------------|----------------------|-----|
| **macOS + Clang** | `-Wl,-oso_prefix,.` | macOS uses OSO stabs that record absolute paths to `.o` files. Without `-oso_prefix,.` LLDB looks for `.o` files at the (sandboxed) absolute path Bazel used during linking and silently fails to resolve symbols. Adding `-oso_prefix,.` makes the recorded paths workspace-relative so LLDB can find them. |

Other platforms and compilers do not need a link-time check today.

## What the dialog offers

| Choice | Effect |
|--------|--------|
| **Continue** | Launch the debugger anyway. Useful if you know the binary is debuggable and the heuristic just missed it. |
| **Cancel** | Abort the launch. |
| **Dismiss for Target** | Suppress the warning for this target only. |
| **Dismiss for Project** | Suppress all debug-info warnings for this project. |
| **inject debug flags** (link in body) | Set `inject_debug_flags: true` in your local `.bazelproject` and re-run. The plugin will inject the flags below for any future debug build. |

## What `inject_debug_flags: true` injects

When enabled, debug builds add (see
[BazelDebugFlagsBuilder.kt](../../clwb/src/com/google/idea/blaze/clwb/run/BazelDebugFlagsBuilder.kt)):

- `--compilation_mode=dbg`
- `--strip=never`
- `--dynamic_mode=off`
- `--copt=-g2` (or `--copt=/Z7` for MSVC) and `--copt=-O0`
- `--fission=yes` for GDB targets
- macOS + LLDB only: `--linkopt=-Wl,-oso_prefix,.`,
  `--linkopt=-Wl,-reproducible`, and a remote-download regex to keep
  `.o` files locally available for the debugger

> **Note:** Toggling this flag invalidates Bazel's analysis cache for
> the next build, since the build configuration changes.

## I had working debug before — what should I do?

1. Verify command-line debug still works:
   `bazel run -c dbg //your:target` and attach a debugger, or build
   with the same flags you normally use and inspect the output.
2. If command-line works but the IDE warns: pick **Continue** or
   **Dismiss for Target/Project**. The check is conservative —
   toolchain wrappers can append `-g` in ways `aquery` does not show.
3. If you prefer the previous behavior (no check, no injection),
   leave `inject_debug_flags` unset (it defaults to `false`) and
   dismiss for project once.

## My debug was broken before — what should I do?

1. Click **inject debug flags** in the dialog. The plugin writes
   `inject_debug_flags: true` to your local `.bazelproject` and
   re-runs the configuration with the injected flags.
2. If that still does not yield breakpoints, run
   `bazel aquery --output=text 'deps(//your:target)'` and confirm the
   compile actions contain `-g*` and (on macOS) the link action
   contains `-Wl,-oso_prefix,.`. If they do not, your toolchain is
   stripping them — the fix belongs in the toolchain or `--copt`s,
   not the plugin.

## Reverting / power-user knobs

- `.bazelproject` → `inject_debug_flags: false` — the default. Disables
  flag injection.
- Registry key `bazel.clwb.debug.fission.disabled` — set in
  *Help → Find Action → Registry…* to suppress `--fission=yes`
  injection for GDB targets.
- Dismissals are stored per-project in IDE state; clear them via
  *File → Invalidate Caches…* or by editing the project's IDE config.
