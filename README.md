# ServerUtil

ServerUtil is a Paper administration plugin for Minecraft `1.21.11` using Java 21, Adventure components, and SQLite persistence.

## Installation

Build the jar and place `target/ServerUtil-1.0-SNAPSHOT.jar` in your Paper server `plugins` folder. Start the server once to generate `config.yml`, `messages.yml`, and `serverutil.db`.

## Commands

- `/util` opens the administration GUI.
- `/util reload` reloads configuration and message files.
- `/report <player> <reason>` submits a private staff report.
- `/reports` opens the reports GUI.
- `/staffmode` toggles Staff Mode.
- `/staffchat [message]` and `/sc [message]` send staff chat or toggle staff-chat mode.

## Permissions

Permissions are declared in `plugin.yml` with operator defaults for staff actions. Public reporting uses `serverutil.report` by default. Sensitive IP display requires `serverutil.players.sensitive`.

## Configuration

`config.yml` controls durations, sounds, Staff Mode behavior, snapshot retention, restart milestones, chat filter rules, and performance cleanup safeguards. `messages.yml` contains MiniMessage text used by GUI workflows and broadcasts.

## Database

SQLite data is stored at `plugins/ServerUtil/serverutil.db`. The schema is initialized on startup and keeps player identities, punishments, reports, history, notes, death snapshots, queued rollbacks, grace period state, vanish state, freezes, Staff Mode backups, lockdown, chat controls, restart countdowns, audit logs, and Key All transactions.

## Inventory Rollbacks

Death snapshots preserve inventory, armor, ender chest data, XP, location, death cause, timestamp, and keep-inventory state. Applying a rollback to an online player first creates a safety snapshot. Offline rollbacks are queued and marked applied so they cannot run twice.

## Staff Mode Recovery

Staff Mode writes the player inventory, armor, offhand, XP, gamemode, flight state, health, and food to SQLite before replacing tools. If the server stops while Staff Mode is active, the player is told to use `/staffmode` after reconnecting to restore the saved state.

## Report Workflow

Reports start as Open. Staff can assign, resolve, or reject them from the GUI. Notes and report details are shown only to staff with report permissions.

## Lockdown

Lockdown persists across restarts. When enabled, non-bypassed players can be kicked and new logins receive the configured rejection message. Operators and `serverutil.lockdown.bypass` are intended bypass holders.

## Restart Behavior

The restart manager stores the scheduled deadline and scheduler name. It announces configured milestones and runs the configured console command when the countdown expires.

## Performance Cleanup Safeguards

Metrics are sampled on a timer. Cleanup is disabled by default, requires confirmation, runs in batches, and skips players, villagers, tamed animals, armor stands, bosses, named entities, persistent-data entities, and configured protected types.

## Build

Use Maven with Java 21:

```bash
mvn clean package
```

This repository was validated with the IntelliJ-bundled Maven on this machine because `mvn` was not on PATH.

## Known Limitations

The plugin does not depend on external Minecraft plugins. Inventory editors open Bukkit inventories and log access, but deeper slot-by-slot diff summaries should be expanded if you need forensic inventory audits. Lockdown bypass checks for players joining cold are limited by what Paper exposes before the player object exists, so permission-plugin specific pre-login bypasses may require server policy or operator handling.
