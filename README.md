# Donut Auction (Fabric 1.21.11, client-side)

Host your own auctions on DonutSMP.

## How to use
1. Press **K** in game (rebind it in Controls > Donut Auction).
2. Type in the search box and **click an item** (any item in Minecraft is listed; mouse wheel changes page).
3. Write the **name**, **time** (`5m`, `90s`, `1h30m`; a bare number = minutes), **min bid** (`1000`, `5k`, `2.5m`) and **quantity**.
4. Click **Start auction**.

What happens:
- A big title appears on your screen and the start message is sent to public chat.
- A panel at the top of the screen shows item, time left and the current top bidder.
- Players bid with `/pay <your name> <amount>`. The mod reads the "X paid you $Y" message; every time somebody takes the lead it announces it in chat.
- When the time ends it announces the winner and the amount in chat and on screen.

Bids are the **total** each player paid you during the auction. Money paid is not refunded automatically.

## Config
`.minecraft/config/donutauction.json` is created on first launch:
- `publicChat`: false = messages only for you.
- `paymentPatterns`: regex to read the /pay message (group 1 = name, group 2 = amount). If bids are not detected, set `debug: true`, copy the real line from chat and adjust the regex.
- all the chat messages and countdown seconds can be edited.

## Build
Needs Java 21.
```
./gradlew build        (Windows: gradlew.bat build)
```
The jar is in `build/libs/donut-auction-1.0.0.jar`. Put it in `.minecraft/mods` with Fabric API.
If Gradle says a version does not exist, update `gradle.properties` with the values from https://fabricmc.net/develop
