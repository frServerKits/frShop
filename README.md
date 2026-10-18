# LimitShop

A small, fast Paper **26.2** shop plugin where everything is managed in game.
No shop config to hand-edit: create a shop with a command, hold an item, set a
price, done.

- **Paper 26.2** (`api-version: '26.2'`), Java 25
- **Vault-compatible custom currency** (works as a Vault economy provider)
- **Instant buying**: click an item to buy one
- **Shift + click**: buy a full stack (limited by stock and inventory space)
- **Limited stock** per item, with manual restocking
- **Offer of the day** per shop, persisted and rotated daily
- **Gradual debt repossession** instead of wiping the inventory at once
- **Simple 6-row chest menu** with balance, debt, offer, pages and close button
- Shops, settings and player data are all managed in game; `data.json` is just
  automatic storage

## Build

Requirements:

- Paper 26.2 server
- Java 25
- Vault (optional, but needed if other plugins should see the currency)

### Build for a specific Paper version

```bash
./scripts/build-paper.sh            # newest stable 26.2 build (default in pom.xml)
./scripts/build-paper.sh 26.2       # newest stable Paper 26.2 build
./scripts/build-paper.sh 26.3       # newest stable Paper 26.3 build
./scripts/build-paper.sh latest     # newest Paper release
```

The script asks PaperMC's fill API for the newest stable build, injects
`paper.version` and `paper.api.version` into Maven, and writes:

```text
target/LimitShop-1.0.0-paper26.2.jar
```

It does not edit the project, so building another version is one command.

A plain Maven build uses the defaults from `pom.xml`:

```bash
mvn package
```

The `plugin.yml` in the jar is filtered, so its `api-version` always matches the
Paper API the jar was compiled against. The included GitHub Actions workflow
builds on pushes and pull requests, and can also build `latest` on a schedule
or manually from the Actions tab.

## Install

1. Put `LimitShop-1.0.0-paper26.2.jar` in `plugins/`.
2. Optional: put `Vault.jar` in `plugins/` as well.
3. Start the server. `plugins/LimitShop/data.json` is created automatically.

There is intentionally no `config.yml`. Every setting has an in-game command.

## Quick start (adding a shop)

```text
/limitshop create blocks &6Blocks
```

Hold the item you want to sell, then:

```text
/limitshop add blocks 10 100 500
```

That adds the held item to `blocks` for **10 Shards**, with **100 stock** and a
**500 max stock** for restocking. Omit `stock` for unlimited stock:

```text
/limitshop add blocks 10
```

Set a daily offer discount and open the shop:

```text
/limitshop offer blocks 30
/limitshop blocks
```

Or open it for another player:

```text
/limitshop summon blocks Steve
```

The in-game shop list is also available with `/limitshop`, `/shop` or `/ls`.

## Buying

Open a shop and:

| Action | Result |
| --- | --- |
| Click an item | Buy 1 instantly |
| Shift + click an item | Buy a stack (up to 64, stock and space permitting) |
| Click the offer item at the top | Buy at today's discount |
| Click the balance icon | Shows your wallet |
| Click the debt icon | Shows debt and the next repossession |
| Arrow buttons | Change page |

There is no confirmation menu. A successful purchase is shown in the action bar
and the menu updates immediately.

## Offer of the day

Each shop has its own offer:

- One item is chosen deterministically and saved for the current day.
- The chosen item is displayed in the top-center slot with a discount.
- The discount is per shop (`/limitshop offer <shop> <percent>`) or the server
  default (`/limitshop settings offer-discount <percent>`).
- The offer uses the same stock as the item; when it is sold out, it shows
  `SOLD OUT`.
- The offer rotates at local midnight. Admins can force a new pick with
  `/limitshop offer <shop> reroll`.

While an item is today's offer it is only sold through the offer slot, so the
discounted deal is the only way to buy it that day.

## Stock

```text
/limitshop add <shop> <price> [stock] [maxStock]
/limitshop restock <shop> [amount]
/limitshop stock <shop> <item#> <stock>
/limitshop maxstock <shop> <item#> <max>
```

- `stock = -1` means unlimited.
- `restock` with no amount fills each finite item up to its `maxStock`.
- `restock <amount>` adds that amount, capped at `maxStock`.
- `stock`/`maxStock` are per shop item and are shared by all players.
- `/limitshop items <shop>` shows the item numbers used by these commands.

## Currency and Vault

By default LimitShop runs its own integer currency called **Shards** and
registers it with Vault. Other Vault plugins can then read/write the same
balances.

Economy modes:

```text
/limitshop economy internal   # LimitShop's currency, exposed through Vault
/limitshop economy external   # use another Vault economy (e.g. EssentialsX)
/limitshop economy auto       # external when available, internal otherwise
```

Switch at runtime with `/limitshop economy <mode>`, or
`/limitshop settings vault-mode <mode>`.

Important: internal and external wallets are separate. Switching modes does not
transfer balances, so pick one mode for your server.

Currency appearance:

```text
/limitshop settings currency-name Credits
/limitshop settings currency-singular Credit
/limitshop settings currency-symbol $
```

Players can sell items back at a percentage of the shop price:

```text
/limitshop sell        # sell the held stack
/limitshop sell all    # sell every matching item in the inventory
```

The default buy-back rate is 50% (`/limitshop settings sell-percent 50`). Sale
income pays off debt first.

## Debt

Debt is enabled by default. When a player cannot afford a purchase, LimitShop
can let them buy on credit up to a limit:

```text
/limitshop settings debt-enabled true
/limitshop settings debt-limit 500
/limitshop settings debt-grace-seconds 300
/limitshop settings debt-collect-seconds 60
/limitshop settings debt-min-item-value 2
```

How it works:

1. A purchase first uses the player's wallet, then adds the remainder as debt
   up to `debt-limit`. Purchases that would exceed the limit are denied.
2. The player has `debt-grace-seconds` to pay before collection starts.
3. Once due, the collector runs every `debt-collect-seconds`:
   - It first takes money from the player's wallet.
   - If money is not enough, it takes **one valuable item stack at a time**
     from the inventory.
   - If the inventory has no valuable items, it falls back to equipped armour
     and the offhand.
   - It never takes everything at once.
4. Debt is persisted. If it came due while the player was offline, one
   collection happens when they next join.
5. Pay directly with `/limitshop pay <amount>`, or sell items with
   `/limitshop sell` (income pays debt first).

Debt can be inspected with `/limitshop debt [player]`. The debt icon in the shop
menu shows the remaining debt and when the next collection is due.

## Commands

### Players

| Command | Description |
| --- | --- |
| `/limitshop` | Open the shop list |
| `/limitshop <shop>` | Open a shop directly |
| `/limitshop sell [all]` | Sell the held stack, or all matching items |
| `/limitshop balance [player]` | Show a balance |
| `/limitshop debt [player]` | Show debt and repossession timing |
| `/limitshop pay <amount>` | Pay off debt |
| `/limitshop list` | List shops |

### Admins (`limitshop.admin`)

| Command | Description |
| --- | --- |
| `/limitshop create <id> [title...]` | Create a shop |
| `/limitshop delete <shop> confirm` | Delete a shop and its stock |
| `/limitshop add <shop> <price> [stock] [maxStock]` | Add the held item |
| `/limitshop remove <shop> <item#>` | Remove an item |
| `/limitshop items <shop>` | List items with numbers and stock |
| `/limitshop price <shop> <item#> <price>` | Change a price |
| `/limitshop stock <shop> <item#> <amount>` | Set stock (`-1` = unlimited) |
| `/limitshop maxstock <shop> <item#> <amount>` | Set restock maximum |
| `/limitshop restock <shop> [amount]` | Restock finite items |
| `/limitshop title <shop> <title...>` | Change the shop title (`&` colours) |
| `/limitshop icon <shop>` | Use the held item as the shop icon |
| `/limitshop offer <shop> <percent\|reroll\|reset>` | Configure today's offer |
| `/limitshop summon <shop> [player]` | Open a shop for someone |
| `/limitshop settings` | Show all settings |
| `/limitshop settings <key> <value>` | Change a setting |
| `/limitshop economy <auto\|internal\|external>` | Choose the Vault mode |
| `/limitshop credit <player> <amount>` | Give currency |
| `/limitshop setbalance <player> <amount>` | Set a wallet balance |
| `/limitshop setdebt <player> <amount>` | Set debt |
| `/limitshop collect <player>` | Trigger one repossession now |
| `/limitshop reload` | Reload `data.json` and the economy hook |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `limitshop.use` | true | Open shops, buy, sell, check balance/debt |
| `limitshop.admin` | op | Manage shops, stock, offers, settings and wallets |

## Data

`plugins/LimitShop/data.json` stores shops, stock, offers, balances and debt. It
is written automatically and atomically. Do not edit it by hand; use the
commands. A corrupt file is backed up as `data.json.corrupt-<timestamp>`.
