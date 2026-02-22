# Core TUI State Machine

`CoreTuiController` submit/generation flow uses one explicit state machine:

- `IDLE`
- `VALIDATING`
- `LOADING`
- `SUCCESS`
- `ERROR`
- `CANCELLED`

## Transition Table

| Current | Event | Guard | Next | Effect |
|---|---|---|---|---|
| `IDLE` | `Enter` | always | `VALIDATING` | Start submit attempt. |
| `VALIDATING` | validation passes + generation configured | always | `LOADING` | Start async generation flow. |
| `VALIDATING` | validation fails | always | `ERROR` | Show submit-blocked validation error. |
| `VALIDATING` | validation passes + generation not configured | always | `IDLE` | Keep shell interactive with status feedback. |
| `LOADING` | async completion success | matching generation token | `SUCCESS` | Show generated path and next-step hint. |
| `LOADING` | async completion failure | matching generation token | `ERROR` | Show user-friendly error. |
| `LOADING` | async cancellation completion | matching generation token | `CANCELLED` | Show cancellation guidance. |
| `SUCCESS` | `Enter` | always | `IDLE` then `VALIDATING` | New submit starts from clean state. |
| `ERROR` | `Enter` | always | `IDLE` then `VALIDATING` | Retry path starts from clean state. |
| `CANCELLED` | `Enter` | always | `IDLE` then `VALIDATING` | Retry path starts from clean state. |

## Guards

- Duplicate submit is blocked while `LOADING` (state remains `LOADING`).
- `Esc` while `LOADING` requests cancellation and keeps app open.
- Async progress/completion callbacks are ignored when token/state checks fail.
- All async callbacks marshal through the render-thread scheduler before mutating UI state.
