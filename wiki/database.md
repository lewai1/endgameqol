---
title: Database
description: Optional SQL database for cross-server player data sync
order: 17
published: true
---

# <gradient data="cyan:purple">Database Setup</gradient>

EndgameQoL stores all per-player data in <#55ff55>ECS components</#55ff55> that Hytale auto-saves to each player's <#aaaaaa>universe/players/&lt;UUID&gt;.bson</#aaaaaa> file — no database needed. The optional database feature syncs player data across sessions or multiple server instances.

! The database is a <#55ffff>secondary backup</#55ffff>. ECS component files remain the primary source of truth. If the database goes down, nothing breaks.

---

## <#55ffff>What gets synced?</#55ffff>

<#aaaaaa>Bounty progress</#aaaaaa> — Daily/weekly bounty state, streak count, total completed

<#aaaaaa>Achievements</#aaaaaa> — Completion, progress, and claimed rewards per player

<#aaaaaa>Bestiary</#aaaaaa> — NPC kill counts, discoveries, and milestones

<#aaaaaa>Accessory Pouch</#aaaaaa> — Trinket Pouch equipped accessories

<#aaaaaa>Combo Personal Best</#aaaaaa> — Highest kill streak record (never decreases)

<#aaaaaa>Gauntlet Best Wave</#aaaaaa> — Highest gauntlet wave reached (never decreases)

!! Not synced: Server config, difficulty settings, recipe overrides, and player language — these stay in local JSON files.

---

## <#55ffff>When does sync happen?</#55ffff>

Data is saved to the database <#55ffff>when a player disconnects</#55ffff>. There is no periodic auto-save — Hytale's ECS auto-save handles per-player persistence. The database acts as a secondary persistence layer for cross-server sync.

On <#55ffff>server shutdown</#55ffff>, the plugin waits up to 5 seconds for any pending writes to complete before stopping.

!v Personal bests are protected: Combo and Gauntlet best scores use a "max" comparison in SQL — a lower value will <#ff5555>never</#ff5555> overwrite a higher one, even if the player disconnects mid-run.

---

## <#d16eff>Supported Databases</#d16eff>

| Database | Best For |
|:---------|:---------|
| <#55ffff>SQLite</#55ffff> | Single server, zero setup, local file storage |
| <#ff6600>MySQL 8.0</#ff6600> | Dedicated servers, multi-instance, most common |
| <#ff6600>MariaDB 3.3</#ff6600> | Drop-in MySQL replacement (OVH, Hetzner, etc.) |
| <#d16eff>PostgreSQL 42.7</#d16eff> | Alternative for Pterodactyl and similar panels |

!! No JDBC drivers are bundled. You must download the driver JAR for your chosen database and place it in your server's <#55ffff>Mods/</#55ffff> folder. The driver is <#55ff55>auto-detected</#55ff55> from the JDBC URL prefix.

### Driver Downloads

All drivers are available on <#55ffff>Maven Central</#55ffff> (<#aaaaaa>repo1.maven.org/maven2/</#aaaaaa>). Search for the artifact name below, download the <#aaaaaa>.jar</#aaaaaa> file, and drop it in your <#55ffff>Mods/</#55ffff> folder.

| Database | Artifact |
|:---------|:---------|
| <#55ffff>SQLite</#55ffff> | sqlite-jdbc |
| <#ff6600>MySQL</#ff6600> | mysql-connector-j |
| <#ff6600>MariaDB</#ff6600> | mariadb-java-client |
| <#d16eff>PostgreSQL</#d16eff> | postgresql |

All drivers are available on <#55ffff>Maven Central</#55ffff>. Download the JAR and place it in your <#55ffff>Mods/</#55ffff> folder.

---

## <#55ffff>Configuration</#55ffff>

The config file is created automatically on first boot at:
<#aaaaaa>Saves/save/mods/Config_Endgame&QoL/DatabaseConfig.json</#aaaaaa>

### All Fields

<#55ffff>Enabled</#55ffff> — Toggle database sync (default: <#ff5555>false</#ff5555>)

<#55ffff>JdbcUrl</#55ffff> — JDBC connection URL (default: <#aaaaaa>jdbc:sqlite:./data/endgame_sync.db</#aaaaaa>)

<#55ffff>Username</#55ffff> — Database username (leave empty for SQLite)

<#55ffff>Password</#55ffff> — Database password (leave empty for SQLite)

<#55ffff>MaxPoolSize</#55ffff> — Maximum connections in pool (default: <#aaaaaa>10</#aaaaaa>)

### Validation Rules

The plugin validates your config at startup and will log an error if:
- <#aaaaaa>JdbcUrl</#aaaaaa> does not start with <#55ffff>jdbc:</#55ffff>

- Remote databases (MySQL/MariaDB/PostgreSQL) are missing a <#aaaaaa>Username</#aaaaaa>

- PostgreSQL URL contains embedded credentials (use the Username/Password fields instead)

- <#aaaaaa>MaxPoolSize</#aaaaaa> is less than 1

---

## <#55ffff>Quick Start — SQLite</#55ffff>

SQLite requires no external database server — just a driver JAR and one toggle.

**1.** Download <#55ffff>sqlite-jdbc</#55ffff> from Maven Central (see Driver Downloads above) and place the JAR in your server's <#55ffff>Mods/</#55ffff> folder

**2.** Open <#aaaaaa>Config_Endgame&QoL/DatabaseConfig.json</#aaaaaa>

**3.** Set <#55ff55>"Enabled": true</#55ff55>

**4.** Restart the server

The plugin creates <#aaaaaa>data/endgame_sync.db</#aaaaaa> automatically. No username or password needed.

> <#55ffff>Best for:</#55ffff> Single servers that want a backup or easier data migration.

---

## <#ff6600>MySQL / MariaDB Setup</#ff6600>

**1.** Download the JDBC driver from Maven Central — <#ff6600>mysql-connector-j</#ff6600> for MySQL or <#ff6600>mariadb-java-client</#ff6600> for MariaDB (see Driver Downloads above) — and place the JAR in your server's <#55ffff>Mods/</#55ffff> folder.

**2.** Create the database on your MySQL/MariaDB server:

```
CREATE DATABASE endgame;
```

**3.** Create a user with access (or use an existing one):

```
GRANT ALL PRIVILEGES ON endgame.* TO 'hytaleuser'@'localhost';
```

**4.** Edit <#aaaaaa>DatabaseConfig.json</#aaaaaa>:

```
{
  "Enabled": true,
  "JdbcUrl": "jdbc:mysql://localhost:3306/endgame",
  "Username": "hytaleuser",
  "Password": "yourpassword",
  "MaxPoolSize": 10
}
```

For <#ff6600>MariaDB</#ff6600>, replace <#aaaaaa>jdbc:mysql://</#aaaaaa> with <#aaaaaa>jdbc:mariadb://</#aaaaaa>.

**5.** Restart the server. Tables are created automatically.

> <#55ffff>Best for:</#55ffff> Multi-instance servers or networks that need shared player data.

---

## <#d16eff>PostgreSQL Setup</#d16eff>

**1.** Download the <#d16eff>postgresql</#d16eff> driver from Maven Central (see Driver Downloads above) and place the JAR in your server's <#55ffff>Mods/</#55ffff> folder.

**2.** Create the database:

```
CREATE DATABASE endgame;
```

**3.** Edit <#aaaaaa>DatabaseConfig.json</#aaaaaa>:

```
{
  "Enabled": true,
  "JdbcUrl": "jdbc:postgresql://localhost:5432/endgame",
  "Username": "hytaleuser",
  "Password": "yourpassword",
  "MaxPoolSize": 10
}
```

> <#ff5555>Important:</#ff5555> Do NOT put credentials in the URL. Always use the Username and Password fields.

**4.** Restart the server. Tables are created automatically.

---

## <#55ffff>Connection Pool (HikariCP)</#55ffff>

The plugin uses <#55ffff>HikariCP</#55ffff> for connection management (bundled in the JAR).

<#aaaaaa>Pool name</#aaaaaa> — EndgameQoL-DB

<#aaaaaa>Min idle connections</#aaaaaa> — 1

<#aaaaaa>Max pool size</#aaaaaa> — Configurable (default 10)

<#aaaaaa>Connection timeout</#aaaaaa> — 10 seconds

<#aaaaaa>Idle timeout</#aaaaaa> — 5 minutes

<#aaaaaa>Max connection lifetime</#aaaaaa> — 10 minutes

> For most servers, the default pool size of <#55ffff>10</#55ffff> is more than enough. Only increase it if you have 50+ concurrent players and see connection wait times in logs.

---

## <#55ffff>Database Tables</#55ffff>

The plugin creates two tables automatically (no manual SQL needed).

### Endgame_PlayerData

One row per player. Stores all synced features as JSON blobs.

<#55ffff>uuid</#55ffff> — Player UUID (primary key)

<#55ffff>username</#55ffff> — Last seen player name

<#aaaaaa>bounty_state</#aaaaaa> — Bounties JSON (progress, streak, daily/weekly)

<#aaaaaa>achievement_data</#aaaaaa> — Achievements JSON (progress, claimed)

<#aaaaaa>bestiary_data</#aaaaaa> — Bestiary JSON (kills, discoveries, milestones)

<#aaaaaa>accessory_pouch</#aaaaaa> — Trinket Pouch JSON

<#aaaaaa>gauntlet_best_wave</#aaaaaa> — Best gauntlet wave (integer)

<#aaaaaa>combo_personal_best</#aaaaaa> — Best combo streak (integer)

<#55ffff>last_updated</#55ffff> — Timestamp of last save

### Endgame_Leaderboard

Gauntlet wave leaderboard. One row per player.

<#55ffff>player_uuid</#55ffff> — Player UUID (primary key)

<#55ffff>player_name</#55ffff> — Display name at time of record

<#55ffff>best_wave</#55ffff> — Best wave reached (indexed for fast sorting)

<#55ffff>timestamp</#55ffff> — When the record was set

> Both tables use <#55ff55>CREATE TABLE IF NOT EXISTS</#55ff55> — safe to run every startup. Columns added in v4.0.0 are automatically migrated via ALTER TABLE.

---

## <#55ff55>Verifying it works</#55ff55>

After restarting, check the server logs for:

<#55ff55>[EndgameQoL.Database] Connection validated</#55ff55>

<#55ff55>[EndgameQoL.Database] Tables and indexes verified/created</#55ff55>

<#55ff55>[EndgameQoL.Database] Sync service initialized</#55ff55>

You can also run <#55ffff>/eg status</#55ffff> — the database health indicator shows <#55ff55>Healthy</#55ff55> or <#ff5555>Degraded</#ff5555>.

---

## <#ff5555>Graceful Degradation</#ff5555>

If the database connection fails:

**1.** The plugin <#55ff55>keeps working</#55ff55> with JSON files — no data is lost, no crash

**2.** After <#ff5555>3 consecutive failures</#ff5555>, the service enters <#ff5555>DEGRADED</#ff5555> mode (visible in <#55ffff>/eg status</#55ffff>)

**3.** Writes <#55ffff>continue to retry</#55ffff> on every player disconnect — there is no permanent cutoff

**4.** When the connection is restored, the service <#55ff55>self-heals automatically</#55ff55> and logs a recovery message

> If the database fails at <#ff5555>startup</#ff5555>, the plugin starts normally with JSON-only persistence. A warning is logged but it is non-fatal.

---

## <#ff6600>Troubleshooting</#ff6600>

<#ff5555>Driver not found / ClassNotFoundException</#ff5555>

The JDBC driver JAR is missing. Download the correct driver for your database type from Maven Central (see Supported Databases section) and place it in your server's <#55ffff>Mods/</#55ffff> folder, then restart the server.

<#ff5555>Connection refused</#ff5555>

Check that your database server is running and the URL/port are correct. Verify firewall rules if using a remote host.

<#ff5555>Access denied</#ff5555>

Verify username/password and that the user has permissions on the database. For MySQL: <#aaaaaa>GRANT ALL PRIVILEGES ON endgame.* TO 'user'@'host';</#aaaaaa>

<#ff5555>Tables not created</#ff5555>

Tables are created automatically. Check server logs for SQL errors — the most common cause is insufficient permissions (<#aaaaaa>CREATE TABLE</#aaaaaa> privilege required).

<#ff5555>No sync happening</#ff5555>

Make sure <#aaaaaa>Enabled</#aaaaaa> is <#55ff55>true</#55ff55> in DatabaseConfig.json and <#ff5555>restart</#ff5555> the server. A config reload is not enough — the database initializes at startup only.

<#ff5555>Degraded in /eg status</#ff5555>

The database had 3+ consecutive failures. Check your database server is online. The plugin will auto-recover once the connection is restored.

<#ff5555>Data not appearing on another server</#ff5555>

Both servers must point to the <#55ffff>same database</#55ffff> (same JdbcUrl, same database name). SQLite files are local-only — use MySQL/MariaDB/PostgreSQL for multi-server sync.

<#ff5555>Slow player disconnects</#ff5555>

Database writes are async and should not block disconnects. If you see delays, check your database server latency and increase <#aaaaaa>MaxPoolSize</#aaaaaa> if all connections are busy.

---

## <#55ffff>Recommended Pool Sizes</#55ffff>

| Players | MaxPoolSize |
|--------:|:-----------:|
| 1-20 | 5 |
| 20-50 | 10 |
| 50-100 | 15 |
| 100+ | 20 |

! SQLite does not benefit from pool sizes above <#55ffff>3</#55ffff> — it uses file-level locking.

---

<buttons>
    <button topic="index">Home</button>
    <button topic="configuration">Config Reference</button>
    <button topic="commands">Commands</button>
</buttons>
